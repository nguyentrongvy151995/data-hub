package com.anonymous.datahub.data_hub.application.service;

import com.anonymous.datahub.data_hub.application.dto.CreateEventDto;
import com.anonymous.datahub.data_hub.application.dto.EventDetailDto;
import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.IngestionSummaryDto;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.dto.SourceVolumeDto;
import com.anonymous.datahub.data_hub.application.dto.UpdateEventDto;
import com.anonymous.datahub.data_hub.application.mapper.IncomingEventMapper;
import com.anonymous.datahub.data_hub.application.usecase.IngestEventUseCase;
import com.anonymous.datahub.data_hub.application.usecase.ManageEventUseCase;
import com.anonymous.datahub.data_hub.application.usecase.QueryEventUseCase;
import com.anonymous.datahub.data_hub.domain.model.EventPersistenceOutcome;
import com.anonymous.datahub.data_hub.domain.model.EventProcessingStatus;
import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.model.SourceEventVolume;
import com.anonymous.datahub.data_hub.domain.port.EventBusinessProcessor;
import com.anonymous.datahub.data_hub.domain.port.EventStorePort;
import com.anonymous.datahub.data_hub.shared.exception.DuplicateResourceException;
import com.anonymous.datahub.data_hub.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class EventApplicationService implements IngestEventUseCase, QueryEventUseCase, ManageEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(EventApplicationService.class);

    private final EventStorePort eventStorePort;
    private final EventBusinessProcessor eventBusinessProcessor;
    private final IncomingEventMapper incomingEventMapper;
    private final Clock clock;
    private final long processingTimeoutMs;

    public EventApplicationService(
            EventStorePort eventStorePort,
            EventBusinessProcessor eventBusinessProcessor,
            IncomingEventMapper incomingEventMapper,
            Clock clock,
            @Value("${app.event.processing.timeout-ms:10000}") long processingTimeoutMs
    ) {
        this.eventStorePort = eventStorePort;
        this.eventBusinessProcessor = eventBusinessProcessor;
        this.incomingEventMapper = incomingEventMapper;
        this.clock = clock;
        this.processingTimeoutMs = processingTimeoutMs;
    }

    @Override
    public EventIngestionResult ingest(KafkaEventDto eventDto) {
        IncomingEvent event = incomingEventMapper.toDomain(eventDto, Instant.now(clock));
        EventPersistenceOutcome outcome = eventStorePort.saveIfAbsent(event);

        if (outcome == EventPersistenceOutcome.STORED) {
            try {
                processEventWithTimeout(event);
                eventStorePort.updateStatusByEventId(event.eventId(), EventProcessingStatus.SUCCESS);
                return EventIngestionResult.STORED;
            } catch (Exception ex) {
                // Remove temporary record so main consumer retry can execute the logic again.
                eventStorePort.deleteByEventId(event.eventId());
                // log.warn("Event processing failed and temporary record is removed. eventId={}, reason={}",
                //         event.eventId(), ex.getMessage());
                throw ex;
            }
        }

        return EventIngestionResult.DUPLICATE;
    }

    @Override
    public void markFailedAfterRetries(KafkaEventDto eventDto) {
        IncomingEvent event = incomingEventMapper.toDomain(eventDto, Instant.now(clock));
        EventPersistenceOutcome outcome = eventStorePort.saveIfAbsent(event);
        eventStorePort.updateStatusByEventId(event.eventId(), EventProcessingStatus.FAILED);
        log.error("Event marked as FAILED after retries exhausted. eventId={}, persistenceOutcome={}",
                event.eventId(), outcome);
    }

    @Override
    public EventDetailDto create(CreateEventDto createEventDto) {
        IncomingEvent event = incomingEventMapper.toDomain(createEventDto, Instant.now(clock));
        EventPersistenceOutcome outcome = eventStorePort.saveIfAbsent(event);
        if (outcome == EventPersistenceOutcome.DUPLICATE) {
            throw new DuplicateResourceException("Event already exists: " + createEventDto.eventId());
        }
        eventStorePort.updateStatusByEventId(event.eventId(), EventProcessingStatus.SUCCESS);
        return incomingEventMapper.toEventDetailDto(event);
    }

    @Override
    public EventDetailDto update(String eventId, UpdateEventDto updateEventDto) {
        IncomingEvent existingEvent = eventStorePort.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        IncomingEvent updateCandidate = incomingEventMapper.toDomain(
                eventId,
                existingEvent.eventType(),
                updateEventDto,
                existingEvent.updatedAt()
        );
        IncomingEvent updated = eventStorePort.updateByEventId(eventId, updateCandidate);
        return incomingEventMapper.toEventDetailDto(updated);
    }

    @Override
    public void delete(String eventId) {
        if (eventStorePort.findByEventId(eventId).isEmpty()) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        eventStorePort.deleteByEventId(eventId);
    }

    @Override
    public EventDetailDto getByEventId(String eventId) {
        IncomingEvent event = eventStorePort.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        return incomingEventMapper.toEventDetailDto(event);
    }

    @Override
    public List<EventDetailDto> getAll() {
        return eventStorePort.findAll().stream().map(incomingEventMapper::toEventDetailDto).toList();
    }

    @Override
    public IngestionSummaryDto getSummary(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before to");
        }

        long totalUnique = eventStorePort.countUpdatedBetween(from, to);
        List<SourceVolumeDto> bySource = eventStorePort.summarizeBySourceBetween(from, to)
                .stream()
                .map(this::toSourceVolumeDto)
                .toList();

        return new IngestionSummaryDto(from, to, totalUnique, bySource);
    }

    private SourceVolumeDto toSourceVolumeDto(SourceEventVolume sourceEventVolume) {
        return incomingEventMapper.toSourceVolumeDto(sourceEventVolume);
    }

    private void processEventWithTimeout(IncomingEvent event) {
        CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(
                () -> eventBusinessProcessor.process(event)
        );

        try {
            processingFuture.get(processingTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            processingFuture.cancel(true);
            throw new IllegalStateException(
                    "Event processing timed out after " + processingTimeoutMs + "ms for eventId=" + event.eventId(),
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Event processing interrupted for eventId=" + event.eventId(), ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause != null ? cause : ex);
        }
    }
}
