package com.anonymous.datahub.data_hub.infrastructure.kafka;

import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.service.EventApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=kafka-listener-it",
        "spring.kafka.listener.concurrency=3",
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
        "app.kafka.topic.raw-events.partitions=3",
        "app.kafka.topic.raw-events.replicas=1"
})
@EmbeddedKafka(partitions = 3, topics = {
        "test.raw-events",
        "test.raw-events.DLT"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RawEventKafkaListenerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockitoBean
    private EventApplicationService eventApplicationService;

    @BeforeEach
    void waitForListenerAssignment() {
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }
    }

    @Test
    void shouldConsumeMessageAndDelegateToClaimThenProcess() throws Exception {
        when(eventApplicationService.claimForProcessing(any(KafkaEventDto.class))).thenReturn(EventIngestionResult.STORED);

        kafkaTemplate.send("test.raw-events", "evt-it-001", eventJson("evt-it-001")).get(10, TimeUnit.SECONDS);

        ArgumentCaptor<KafkaEventDto> captor = ArgumentCaptor.forClass(KafkaEventDto.class);
        verify(eventApplicationService, timeout(10_000)).claimForProcessing(captor.capture());
        verify(eventApplicationService, timeout(10_000)).processClaimedEvent(any(KafkaEventDto.class));
        verify(eventApplicationService, never()).markFailedAfterRetries(any(KafkaEventDto.class));

        KafkaEventDto captured = captor.getValue();
        assertThat(captured.eventId()).isEqualTo("evt-it-001");
        assertThat(captured.eventType()).isEqualTo("BET");
        assertThat(captured.source()).isEqualTo("systemA");
    }
    
    @Test
    void shouldSkipProcessingWhenClaimReturnsDuplicate() throws Exception {
        when(eventApplicationService.claimForProcessing(any(KafkaEventDto.class))).thenReturn(EventIngestionResult.DUPLICATE);

        kafkaTemplate.send("test.raw-events", "evt-it-dup", eventJson("evt-it-dup")).get(10, TimeUnit.SECONDS);

        verify(eventApplicationService, timeout(10_000)).claimForProcessing(any(KafkaEventDto.class));
        verify(eventApplicationService, never()).processClaimedEvent(any(KafkaEventDto.class));
        verify(eventApplicationService, never()).markFailedAfterRetries(any(KafkaEventDto.class));
    }
    
    @Test
    void shouldMarkFailedWhenProcessingClaimedEventThrowsException() throws Exception {
        when(eventApplicationService.claimForProcessing(any(KafkaEventDto.class))).thenReturn(EventIngestionResult.STORED);
        doThrow(new IllegalStateException("boom"))
                .when(eventApplicationService)
                .processClaimedEvent(any(KafkaEventDto.class));

        kafkaTemplate.send("test.raw-events", "evt-it-002", eventJson("evt-it-002")).get(10, TimeUnit.SECONDS);

        verify(eventApplicationService, timeout(10_000)).markFailedAfterRetries(any(KafkaEventDto.class));
    }

    @Test
    void shouldProcessMultipleMessagesInParallelAcrossPartitions() throws Exception {
        Set<String> workerThreads = ConcurrentHashMap.newKeySet();
        CountDownLatch atLeastTwoStarted = new CountDownLatch(2);
        CountDownLatch allStarted = new CountDownLatch(3);
        CountDownLatch releaseProcessing = new CountDownLatch(1);

        when(eventApplicationService.claimForProcessing(any(KafkaEventDto.class))).thenReturn(EventIngestionResult.STORED);
        doAnswer(invocation -> {
            workerThreads.add(Thread.currentThread().getName());
            atLeastTwoStarted.countDown();
            allStarted.countDown();
            releaseProcessing.await(5, TimeUnit.SECONDS);
            return null;
        }).when(eventApplicationService).processClaimedEvent(any(KafkaEventDto.class));

        kafkaTemplate.send("test.raw-events", 0, "evt-par-001", eventJson("evt-par-001")).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("test.raw-events", 1, "evt-par-002", eventJson("evt-par-002")).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("test.raw-events", 2, "evt-par-003", eventJson("evt-par-003")).get(10, TimeUnit.SECONDS);

        assertThat(atLeastTwoStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(allStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(workerThreads.size()).isGreaterThanOrEqualTo(2);

        releaseProcessing.countDown();
        verify(eventApplicationService, timeout(10_000).times(3)).claimForProcessing(any(KafkaEventDto.class));
        verify(eventApplicationService, timeout(10_000).times(3)).processClaimedEvent(any(KafkaEventDto.class));
        verify(eventApplicationService, never()).markFailedAfterRetries(any(KafkaEventDto.class));
    }

    private String eventJson(String eventId) {
        return "{" +
                "\"eventId\":\"" + eventId + "\"," +
                "\"eventType\":\"BET\"," +
                "\"createdAt\":\"2026-03-13T10:15:30Z\"," +
                "\"source\":\"systemA\"," +
                "\"payload\":{\"amount\":100}" +
                "}";
    }
}
