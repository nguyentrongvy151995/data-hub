package com.anonymous.datahub.data_hub.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.kafka.producer.enabled", havingValue = "true")
public class RawEventKafkaProducerScheduler {

    private static final Logger log = LoggerFactory.getLogger(RawEventKafkaProducerScheduler.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int batchSize;
    private final String source;
    private final String eventType;
    private final String userName;
    private final long maxEvents;
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public RawEventKafkaProducerScheduler(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic.raw-events}") String topic,
            @Value("${app.kafka.producer.batch-size:3}") int batchSize,
            @Value("${app.kafka.producer.source:systemA}") String source,
            @Value("${app.kafka.producer.event-type:BET}") String eventType,
            @Value("${app.kafka.producer.user-name:Vy Nguyen}") String userName,
            @Value("${app.kafka.producer.max-events:300000}") long maxEvents
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.batchSize = batchSize;
        this.source = source;
        this.eventType = eventType;
        this.userName = userName;
        this.maxEvents = maxEvents;
    }

    @Scheduled(
            fixedRateString = "${app.kafka.producer.interval-ms:1000}",
            initialDelayString = "${app.kafka.producer.initial-delay-ms:3000}"
    )
    public void produceBatch() {
        if (sequence.get() >= maxEvents) {
            if (finished.compareAndSet(false, true)) {
                log.info("[KAFKA-PRODUCER] reached max-events={} and stopped producing", maxEvents);
            }
            return;
        }

        long firstSeq = sequence.get() + 1;
        int produced = 0;
        for (int i = 0; i < batchSize && sequence.get() < maxEvents; i++) {
            // publishOne(i);
            // produced++;
        }

        if (produced == 0) {
            return;
        }
        long lastSeq = sequence.get();
        log.info(
                "[KAFKA-PRODUCER] sent batch topic={} size={} eventIdRange={}..{}",
                topic,
                produced,
                firstSeq,
                lastSeq
        );
    }

    private void publishOne(int batchIndex) {
        try {
            long seq = sequence.incrementAndGet();
            Instant now = Instant.now();
            String eventId = String.valueOf(seq);

            ObjectNode message = objectMapper.createObjectNode();
            message.put("eventId", eventId);
            message.put("eventType", eventType);
            message.put("createdAt", now.toString());
            message.put("source", source);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("userId", seq);
            payload.put("name", userName);
            message.set("payload", payload);

            kafkaTemplate.send(topic, eventId, objectMapper.writeValueAsString(message));
        } catch (Exception ex) {
            log.error("[KAFKA-PRODUCER] failed to publish message batchIndex={}", batchIndex, ex);
        }
    }
}
