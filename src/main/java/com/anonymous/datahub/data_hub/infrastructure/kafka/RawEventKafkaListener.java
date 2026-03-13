package com.anonymous.datahub.data_hub.infrastructure.kafka;

import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.usecase.IngestEventUseCase;
import com.anonymous.datahub.data_hub.shared.exception.InvalidEventException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.kafka.listener.enabled", havingValue = "true")
public class RawEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(RawEventKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final IngestEventUseCase ingestEventUseCase;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String dltTopic;
    private final String parkingLotTopic;
    private final int maxRetryAttempts;
    private final long retryBackoffMs;
    private final long sendTimeoutMs;
    private final boolean randomFailureEnabled;
    private final int randomFailurePercent;

    public RawEventKafkaListener(
            ObjectMapper objectMapper,
            Validator validator,
            IngestEventUseCase ingestEventUseCase,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topic.raw-events-dlt:${app.kafka.topic.raw-events}.DLT}") String dltTopic,
            @Value("${app.kafka.topic.raw-events-parking-lot:${app.kafka.topic.raw-events}.parking-lot}") String parkingLotTopic,
            @Value("${app.kafka.retry.max-attempts:2}") int maxRetryAttempts,
            @Value("${app.kafka.retry.backoff-ms:1000}") long retryBackoffMs,
            @Value("${app.kafka.producer.send-timeout-ms:5000}") long sendTimeoutMs,
            @Value("${app.kafka.test.random-failure-enabled:false}") boolean randomFailureEnabled,
            @Value("${app.kafka.test.random-failure-percent:35}") int randomFailurePercent
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.ingestEventUseCase = ingestEventUseCase;
        this.kafkaTemplate = kafkaTemplate;
        this.dltTopic = dltTopic;
        this.parkingLotTopic = parkingLotTopic;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryBackoffMs = retryBackoffMs;
        this.sendTimeoutMs = sendTimeoutMs;
        this.randomFailureEnabled = randomFailureEnabled;
        this.randomFailurePercent = Math.max(0, Math.min(100, randomFailurePercent));
    }

    @KafkaListener(
            topics = "${app.kafka.topic.raw-events}",
            groupId = "${spring.kafka.consumer.group-id:data-hub-consumer}",
            autoStartup = "${app.kafka.listener.enabled:true}",
            containerFactory = "manualAckKafkaListenerContainerFactory"
    )
    public void consume(
            String message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        String threadName = Thread.currentThread().getName();
        KafkaEventDto parsedEvent = null;
        String extractedEventId = extractEventId(message);
        int totalAttempts = Math.max(0, maxRetryAttempts) + 1;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                parsedEvent = objectMapper.readValue(message, KafkaEventDto.class);
                validate(parsedEvent);
                simulateRandomFailureIfEnabled(parsedEvent, attempt, totalAttempts, partition, offset);
                EventIngestionResult result = ingestEventUseCase.ingest(parsedEvent);

                acknowledgment.acknowledge();
                log.info(
                        "[KAFKA][MAIN-SUCCESS] thread={} eventId={} result={} attempt={}/{} partition={} offset={}",
                        threadName,
                        parsedEvent.eventId(),
                        result,
                        attempt,
                        totalAttempts,
                        partition,
                        offset
                );
                return;
            } catch (Exception ex) {
                String eventId = resolveEventId(parsedEvent, extractedEventId, key);
                publishFailureEvent(
                        dltTopic,
                        eventId,
                        message,
                        topic,
                        partition,
                        offset,
                        attempt,
                        ex,
                        "MAIN_TO_DLT"
                );

                if (attempt < totalAttempts) {
                    log.warn(
                            "[KAFKA][RETRY] thread={} eventId={} attempt={}/{} partition={} offset={} reason={}",
                            threadName,
                            eventId,
                            attempt,
                            totalAttempts,
                            partition,
                            offset,
                            ex.getMessage()
                    );
                    sleepBeforeRetry();
                    continue;
                }

                if (parsedEvent != null) {
                    ingestEventUseCase.markFailedAfterRetries(parsedEvent);
                } else {
                    log.warn(
                            "[KAFKA][FAILED-PERSIST-SKIPPED] thread={} partition={} offset={} reason=event payload is not parseable",
                            threadName,
                            partition,
                            offset
                    );
                }

                publishFailureEvent(
                        parkingLotTopic,
                        eventId,
                        message,
                        topic,
                        partition,
                        offset,
                        attempt,
                        ex,
                        "DLT_TO_PARKING_LOT"
                );

                acknowledgment.acknowledge();
                log.error(
                        "[KAFKA][PARKING-LOT] thread={} eventId={} attempt={}/{} partition={} offset={} mainOffsetAcked=true",
                        threadName,
                        eventId,
                        attempt,
                        totalAttempts,
                        partition,
                        offset
                );
                return;
            }
        }
    }

    private void publishFailureEvent(
            String targetTopic,
            String eventId,
            String payload,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            int attempt,
            Exception exception,
            String stage
    ) {
        String safeEventId = eventId == null || eventId.isBlank() ? "unknown" : eventId;
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", safeEventId);
        envelope.put("stage", stage);
        envelope.put("sourceTopic", sourceTopic);
        envelope.put("sourcePartition", sourcePartition);
        envelope.put("sourceOffset", sourceOffset);
        envelope.put("retryAttempt", attempt);
        envelope.put("failedAt", Instant.now().toString());
        envelope.put("errorType", exception.getClass().getSimpleName());
        envelope.put("errorMessage", trimErrorMessage(exception.getMessage()));
        envelope.put("payload", payload);

        try {
            String outboundPayload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(targetTopic, safeEventId, outboundPayload)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.info(
                    "[KAFKA][PUBLISH] targetTopic={} eventId={} sourcePartition={} sourceOffset={} retryAttempt={}",
                    targetTopic,
                    safeEventId,
                    sourcePartition,
                    sourceOffset,
                    attempt
            );
        } catch (Exception publishEx) {
            throw new IllegalStateException(
                    "Failed to publish event to topic=" + targetTopic + " eventId=" + safeEventId,
                    publishEx
            );
        }
    }

    private String resolveEventId(KafkaEventDto parsedEvent, String extractedEventId, String key) {
        if (parsedEvent != null && parsedEvent.eventId() != null && !parsedEvent.eventId().isBlank()) {
            return parsedEvent.eventId();
        }
        if (extractedEventId != null && !extractedEventId.isBlank()) {
            return extractedEventId;
        }
        if (key != null && !key.isBlank()) {
            return key;
        }
        return "unknown";
    }

    private String extractEventId(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode eventIdNode = root.get("eventId");
            return eventIdNode == null ? null : eventIdNode.asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimErrorMessage(String message) {
        if (message == null) {
            return "unknown";
        }
        if (message.length() <= 500) {
            return message;
        }
        return message.substring(0, 500);
    }

    private void sleepBeforeRetry() {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry backoff interrupted", ex);
        }
    }

    private void simulateRandomFailureIfEnabled(
            KafkaEventDto eventDto,
            int attempt,
            int totalAttempts,
            int partition,
            long offset
    ) {
        if (!randomFailureEnabled || randomFailurePercent <= 0) {
            return;
        }
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < randomFailurePercent) {
            throw new IllegalStateException(
                    "Simulated random failure for testing DLT/Parking-Lot. eventId=" + eventDto.eventId()
                            + ", attempt=" + attempt + "/" + totalAttempts
                            + ", partition=" + partition
                            + ", offset=" + offset
                            + ", roll=" + roll
                            + ", threshold=" + randomFailurePercent
            );
        }
    }

    private void validate(KafkaEventDto dto) {
        Set<ConstraintViolation<KafkaEventDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Kafka payload validation failed: ");
            for (ConstraintViolation<KafkaEventDto> violation : violations) {
                sb.append(violation.getPropertyPath())
                        .append(' ')
                        .append(violation.getMessage())
                        .append(';');
            }
            throw new InvalidEventException(sb.toString());
        }
    }

}
