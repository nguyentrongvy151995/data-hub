package com.anonymous.datahub.data_hub.infrastructure.kafka;

import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.service.EventApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=kafka-listener-it",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "spring.docker.compose.enabled=false",
        "app.kafka.listener.enabled=true",
        "app.kafka.producer.enabled=false",
        "app.kafka.retry.max-attempts=0",
        "app.kafka.retry.backoff-ms=0",
        "app.kafka.test.random-failure-enabled=false",
        "app.kafka.topic.raw-events=test.raw-events",
        "app.kafka.topic.raw-events-dlt=test.raw-events.DLT",
        "app.kafka.topic.raw-events-parking-lot=test.raw-events.parking-lot"
})
@EmbeddedKafka(partitions = 1, topics = {
        "test.raw-events",
        "test.raw-events.DLT",
        "test.raw-events.parking-lot"
})
class RawEventKafkaListenerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private EventApplicationService eventApplicationService;

    @Test
    void shouldConsumeMessageAndDelegateToIngestUseCase() throws Exception {
        when(eventApplicationService.ingest(any(KafkaEventDto.class))).thenReturn(EventIngestionResult.STORED);

        kafkaTemplate.send(
                "test.raw-events",
                "evt-it-001",
                "{" +
                        "\"eventId\":\"evt-it-001\"," +
                        "\"eventType\":\"BET\"," +
                        "\"createdAt\":\"2026-03-13T10:15:30Z\"," +
                        "\"source\":\"systemA\"," +
                        "\"payload\":{\"amount\":100}" +
                        "}"
        ).get(10, TimeUnit.SECONDS);

        ArgumentCaptor<KafkaEventDto> captor = ArgumentCaptor.forClass(KafkaEventDto.class);
        verify(eventApplicationService, timeout(10_000)).ingest(captor.capture());
        verify(eventApplicationService, never()).markFailedAfterRetries(any(KafkaEventDto.class));

        KafkaEventDto captured = captor.getValue();
        assertThat(captured.eventId()).isEqualTo("evt-it-001");
        assertThat(captured.eventType()).isEqualTo("BET");
        assertThat(captured.source()).isEqualTo("systemA");
    }

    @Test
    void shouldMarkFailedWhenIngestionThrowsException() throws Exception {
        when(eventApplicationService.ingest(any(KafkaEventDto.class))).thenThrow(new IllegalStateException("boom"));

        kafkaTemplate.send(
                "test.raw-events",
                "evt-it-002",
                "{" +
                        "\"eventId\":\"evt-it-002\"," +
                        "\"eventType\":\"BET\"," +
                        "\"createdAt\":\"2026-03-13T10:15:30Z\"," +
                        "\"source\":\"systemA\"," +
                        "\"payload\":{\"amount\":100}" +
                        "}"
        ).get(10, TimeUnit.SECONDS);

        verify(eventApplicationService, timeout(10_000)).markFailedAfterRetries(any(KafkaEventDto.class));
    }
}
