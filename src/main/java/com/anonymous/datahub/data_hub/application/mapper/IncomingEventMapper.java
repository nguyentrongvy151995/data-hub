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

    public IncomingEvent toDomain(KafkaEventDto dto, Instant receivedAt) {
        return new IncomingEvent(
                dto.eventId(),
                dto.sourceSystem(),
                dto.payload().toString(),
                dto.occurredAt(),
                receivedAt
        );
    }

    public IncomingEvent toDomain(CreateEventDto dto, Instant receivedAt) {
        return new IncomingEvent(
                dto.eventId(),
                dto.sourceSystem(),
                dto.payload().toString(),
                dto.occurredAt(),
                receivedAt
        );
    }

    public IncomingEvent toDomain(String eventId, UpdateEventDto dto, Instant receivedAt) {
        return new IncomingEvent(
                eventId,
                dto.sourceSystem(),
                dto.payload().toString(),
                dto.occurredAt(),
                receivedAt
        );
    }

    public EventDetailDto toEventDetailDto(IncomingEvent event) {
        return new EventDetailDto(
                event.eventId(),
                event.sourceSystem(),
                event.payload(),
                event.occurredAt(),
                event.receivedAt()
        );
    }

    public SourceVolumeDto toSourceVolumeDto(SourceEventVolume sourceEventVolume) {
        return new SourceVolumeDto(sourceEventVolume.sourceSystem(), sourceEventVolume.total());
    }
}
