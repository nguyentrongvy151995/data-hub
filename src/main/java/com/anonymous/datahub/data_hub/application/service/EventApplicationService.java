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
import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.model.SourceEventVolume;
import com.anonymous.datahub.data_hub.domain.port.EventBusinessProcessor;
import com.anonymous.datahub.data_hub.domain.port.EventStorePort;
import com.anonymous.datahub.data_hub.shared.exception.DuplicateResourceException;
import com.anonymous.datahub.data_hub.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class EventApplicationService implements IngestEventUseCase, QueryEventUseCase, ManageEventUseCase {

    private final EventStorePort eventStorePort;
    private final EventBusinessProcessor eventBusinessProcessor;
    private final IncomingEventMapper incomingEventMapper;
    private final Clock clock;

    public EventApplicationService(
            EventStorePort eventStorePort,
            EventBusinessProcessor eventBusinessProcessor,
            IncomingEventMapper incomingEventMapper,
            Clock clock
    ) {
        this.eventStorePort = eventStorePort;
        this.eventBusinessProcessor = eventBusinessProcessor;
        this.incomingEventMapper = incomingEventMapper;
        this.clock = clock;
    }

    @Override
    public EventIngestionResult ingest(KafkaEventDto eventDto) {
        IncomingEvent event = incomingEventMapper.toDomain(eventDto, Instant.now(clock));
        EventPersistenceOutcome outcome = eventStorePort.saveIfAbsent(event);

        if (outcome == EventPersistenceOutcome.STORED) {
            eventBusinessProcessor.process(event);
            return EventIngestionResult.STORED;
        }

        return EventIngestionResult.DUPLICATE;
    }

    @Override
    public EventDetailDto create(CreateEventDto createEventDto) {
        IncomingEvent event = incomingEventMapper.toDomain(createEventDto, Instant.now(clock));
        EventPersistenceOutcome outcome = eventStorePort.saveIfAbsent(event);
        if (outcome == EventPersistenceOutcome.DUPLICATE) {
            throw new DuplicateResourceException("Event already exists: " + createEventDto.eventId());
        }
        return incomingEventMapper.toEventDetailDto(event);
    }

    @Override
    public EventDetailDto update(String eventId, UpdateEventDto updateEventDto) {
        IncomingEvent existingEvent = eventStorePort.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        IncomingEvent updateCandidate = incomingEventMapper.toDomain(
                eventId,
                updateEventDto,
                existingEvent.receivedAt()
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

        long totalUnique = eventStorePort.countReceivedBetween(from, to);
        List<SourceVolumeDto> bySource = eventStorePort.summarizeBySourceBetween(from, to)
                .stream()
                .map(this::toSourceVolumeDto)
                .toList();

        return new IngestionSummaryDto(from, to, totalUnique, bySource);
    }

    private SourceVolumeDto toSourceVolumeDto(SourceEventVolume sourceEventVolume) {
        return incomingEventMapper.toSourceVolumeDto(sourceEventVolume);
    }
}
