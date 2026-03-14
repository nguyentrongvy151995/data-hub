package com.anonymous.datahub.data_hub.application.service;

import com.anonymous.datahub.data_hub.application.dto.CreateEventDto;
import com.anonymous.datahub.data_hub.application.dto.EventDetailDto;
import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.mapper.IncomingEventMapper;
import com.anonymous.datahub.data_hub.domain.model.EventPersistenceOutcome;
import com.anonymous.datahub.data_hub.domain.model.EventProcessingStatus;
import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.port.EventBusinessProcessor;
import com.anonymous.datahub.data_hub.domain.port.EventStorePort;
import com.anonymous.datahub.data_hub.shared.exception.DuplicateResourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventApplicationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-13T00:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-03-12T10:00:00Z");

    @Mock
    private EventStorePort eventStorePort;

    @Mock
    private EventBusinessProcessor eventBusinessProcessor;

    @Mock
    private IncomingEventMapper incomingEventMapper;

    private EventApplicationService eventApplicationService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        eventApplicationService = new EventApplicationService(
                eventStorePort,
                eventBusinessProcessor,
                incomingEventMapper,
                fixedClock,
                2_000L
        );
    }

    @Test
    void createShouldPersistAndMarkSuccessWhenEventIsNew() {
        CreateEventDto createEventDto = new CreateEventDto("evt-001", "systemA", payload(), CREATED_AT);
        IncomingEvent domainEvent = new IncomingEvent("evt-001", "MANUAL", "systemA", "{\"amount\":100}", CREATED_AT, FIXED_NOW);
        EventDetailDto expectedResponse = new EventDetailDto(
                "evt-001",
                "MANUAL",
                "systemA",
                "{\"amount\":100}",
                CREATED_AT,
                FIXED_NOW
        );

        when(incomingEventMapper.toDomain(eq(createEventDto), any(Instant.class))).thenReturn(domainEvent);
        when(eventStorePort.saveIfAbsent(domainEvent)).thenReturn(EventPersistenceOutcome.STORED);
        when(incomingEventMapper.toEventDetailDto(domainEvent)).thenReturn(expectedResponse);

        EventDetailDto result = eventApplicationService.create(createEventDto);

        assertThat(result).isEqualTo(expectedResponse);
        verify(eventStorePort).updateStatusByEventId("evt-001", EventProcessingStatus.SUCCESS);
    }

    @Test
    void createShouldThrowConflictWhenDuplicate() {
        CreateEventDto createEventDto = new CreateEventDto("evt-001", "systemA", payload(), CREATED_AT);
        IncomingEvent domainEvent = new IncomingEvent("evt-001", "MANUAL", "systemA", "{\"amount\":100}", CREATED_AT, FIXED_NOW);

        when(incomingEventMapper.toDomain(eq(createEventDto), any(Instant.class))).thenReturn(domainEvent);
        when(eventStorePort.saveIfAbsent(domainEvent)).thenReturn(EventPersistenceOutcome.DUPLICATE);

        assertThatThrownBy(() -> eventApplicationService.create(createEventDto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Event already exists: evt-001");

        verify(eventStorePort, never()).updateStatusByEventId(any(), any());
    }

    @Test
    void ingestShouldReturnStoredAndMarkSuccessWhenNewEvent() {
        KafkaEventDto kafkaEventDto = new KafkaEventDto("evt-100", "BET", CREATED_AT, "systemA", payload());
        IncomingEvent domainEvent = new IncomingEvent("evt-100", "BET", "systemA", "{\"amount\":100}", CREATED_AT, FIXED_NOW);

        when(incomingEventMapper.toDomain(eq(kafkaEventDto), any(Instant.class))).thenReturn(domainEvent);
        when(eventStorePort.saveIfAbsent(domainEvent)).thenReturn(EventPersistenceOutcome.STORED);

        EventIngestionResult result = eventApplicationService.ingest(kafkaEventDto);

        assertThat(result).isEqualTo(EventIngestionResult.STORED);
        verify(eventBusinessProcessor).process(domainEvent);
        verify(eventStorePort).updateStatusByEventId("evt-100", EventProcessingStatus.SUCCESS);
        verify(eventStorePort, never()).deleteByEventId(any());
    }

    @Test
    void ingestShouldMarkRetryableWhenBusinessProcessingFails() {
        KafkaEventDto kafkaEventDto = new KafkaEventDto("evt-100", "BET", CREATED_AT, "systemA", payload());
        IncomingEvent domainEvent = new IncomingEvent("evt-100", "BET", "systemA", "{\"amount\":100}", CREATED_AT, FIXED_NOW);

        when(incomingEventMapper.toDomain(eq(kafkaEventDto), any(Instant.class))).thenReturn(domainEvent);
        when(eventStorePort.saveIfAbsent(domainEvent)).thenReturn(EventPersistenceOutcome.STORED);
        doThrow(new IllegalStateException("Business failure")).when(eventBusinessProcessor).process(domainEvent);

        assertThatThrownBy(() -> eventApplicationService.ingest(kafkaEventDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Business failure");

        verify(eventStorePort).updateStatusByEventId("evt-100", EventProcessingStatus.FAILED_RETRYABLE);
        verify(eventStorePort, never()).updateStatusByEventId(any(), eq(EventProcessingStatus.SUCCESS));
        verify(eventStorePort, never()).deleteByEventId(any());
    }

    @Test
    void ingestShouldReturnDuplicateWhenEventAlreadyExistsAndCannotReclaim() {
        KafkaEventDto kafkaEventDto = new KafkaEventDto("evt-100", "BET", CREATED_AT, "systemA", payload());
        IncomingEvent domainEvent = new IncomingEvent("evt-100", "BET", "systemA", "{\"amount\":100}", CREATED_AT, FIXED_NOW);

        when(incomingEventMapper.toDomain(eq(kafkaEventDto), any(Instant.class))).thenReturn(domainEvent);
        when(eventStorePort.saveIfAbsent(domainEvent)).thenReturn(EventPersistenceOutcome.DUPLICATE);
        when(eventStorePort.reclaimForProcessing("evt-100", FIXED_NOW)).thenReturn(false);

        EventIngestionResult result = eventApplicationService.ingest(kafkaEventDto);

        assertThat(result).isEqualTo(EventIngestionResult.DUPLICATE);
        verifyNoInteractions(eventBusinessProcessor);
        verify(eventStorePort, never()).updateStatusByEventId(any(), eq(EventProcessingStatus.SUCCESS));
    }

    @Test
    void ingestShouldReclaimAndProcessWhenExistingEventIsRetryable() {
        KafkaEventDto kafkaEventDto = new KafkaEventDto("evt-100", "BET", CREATED_AT, "systemA", payload());
        IncomingEvent domainEvent = new IncomingEvent("evt-100", "BET", "systemA", "{\"amount\":100}", CREATED_AT, FIXED_NOW);

        when(incomingEventMapper.toDomain(eq(kafkaEventDto), any(Instant.class))).thenReturn(domainEvent);
        when(eventStorePort.saveIfAbsent(domainEvent)).thenReturn(EventPersistenceOutcome.DUPLICATE);
        when(eventStorePort.reclaimForProcessing("evt-100", FIXED_NOW)).thenReturn(true);

        EventIngestionResult result = eventApplicationService.ingest(kafkaEventDto);

        assertThat(result).isEqualTo(EventIngestionResult.STORED);
        verify(eventBusinessProcessor).process(domainEvent);
        verify(eventStorePort).updateStatusByEventId("evt-100", EventProcessingStatus.SUCCESS);
    }

    private JsonNode payload() {
        return JsonNodeFactory.instance.objectNode().put("amount", 100);
    }
}
