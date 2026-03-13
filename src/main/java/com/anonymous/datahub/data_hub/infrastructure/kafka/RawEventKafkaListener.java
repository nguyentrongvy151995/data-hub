package com.anonymous.datahub.data_hub.infrastructure.kafka;

import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.usecase.IngestEventUseCase;
import com.anonymous.datahub.data_hub.shared.exception.InvalidEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.kafka.listener.enabled", havingValue = "true")
public class RawEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(RawEventKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final IngestEventUseCase ingestEventUseCase;

    public RawEventKafkaListener(
            ObjectMapper objectMapper,
            Validator validator,
            IngestEventUseCase ingestEventUseCase
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.ingestEventUseCase = ingestEventUseCase;
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
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String threadName = Thread.currentThread().getName();
        try {
            KafkaEventDto eventDto = objectMapper.readValue(message, KafkaEventDto.class);
            validate(eventDto);
            EventIngestionResult result = ingestEventUseCase.ingest(eventDto);

            acknowledgment.acknowledge();

            log.info(
                    "[KAFKA] thread={} eventId={} eventType={} source={} result={} partition={} offset={}",
                    threadName,
                    eventDto.eventId(),
                    eventDto.eventType(),
                    eventDto.source(),
                    result,
                    partition,
                    offset
            );
        } catch (InvalidEventException | JsonProcessingException ex) {
            log.warn(
                    "[KAFKA-CONSUMER][INVALID] thread={} topic={} partition={} offset={} reason={}",
                    threadName,
                    topic,
                    partition,
                    offset,
                    ex.getMessage()
            );
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error(
                    "[KAFKA-CONSUMER][FAILED] thread={} topic={} partition={} offset={}. Offset is not committed.",
                    threadName,
                    topic,
                    partition,
                    offset,
                    ex
            );
            throw new RuntimeException(ex);
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
