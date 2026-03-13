package com.anonymous.datahub.data_hub.application.mapper;

import com.anonymous.datahub.data_hub.application.dto.CreateEventDto;
import com.anonymous.datahub.data_hub.application.dto.EventDetailDto;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;
import com.anonymous.datahub.data_hub.application.dto.SourceVolumeDto;
import com.anonymous.datahub.data_hub.application.dto.UpdateEventDto;
import com.anonymous.datahub.data_hub.domain.model.IncomingEvent;
import com.anonymous.datahub.data_hub.domain.model.SourceEventVolume;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class IncomingEventMapper {

    private static final String MANUAL_EVENT_TYPE = "MANUAL";

    public IncomingEvent toDomain(KafkaEventDto dto, Instant updatedAt) {
        return new IncomingEvent(
                dto.eventId(),
                dto.eventType(),
                dto.source(),
                dto.payload().toString(),
                dto.createdAt(),
                updatedAt
        );
    }

    public IncomingEvent toDomain(CreateEventDto dto, Instant updatedAt) {
        return new IncomingEvent(
                dto.eventId(),
                MANUAL_EVENT_TYPE,
                dto.sourceSystem(),
                dto.payload().toString(),
                dto.createdAt(),
                updatedAt
        );
    }

    public IncomingEvent toDomain(String eventId, String eventType, UpdateEventDto dto, Instant updatedAt) {
        return new IncomingEvent(
                eventId,
                eventType,
                dto.sourceSystem(),
                dto.payload().toString(),
                dto.createdAt(),
                updatedAt
        );
    }

    public EventDetailDto toEventDetailDto(IncomingEvent event) {
        return new EventDetailDto(
                event.eventId(),
                event.eventType(),
                event.sourceSystem(),
                event.payload(),
                event.createdAt(),
                event.updatedAt()
        );
    }

    public SourceVolumeDto toSourceVolumeDto(SourceEventVolume sourceEventVolume) {
        return new SourceVolumeDto(sourceEventVolume.sourceSystem(), sourceEventVolume.total());
    }
}
