package com.anonymous.datahub.data_hub.interfaces.rest.mapper;

import com.anonymous.datahub.data_hub.application.dto.EventDetailDto;
import com.anonymous.datahub.data_hub.application.dto.IngestionSummaryDto;
import com.anonymous.datahub.data_hub.application.dto.SourceVolumeDto;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.EventResponse;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.IngestionSummaryResponse;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.SourceVolumeResponse;
import org.springframework.stereotype.Component;

@Component
public class RestResponseMapper {

    public EventResponse toEventResponse(EventDetailDto dto) {
        return new EventResponse(
                dto.eventId(),
                dto.eventType(),
                dto.sourceSystem(),
                dto.payload(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    public IngestionSummaryResponse toSummaryResponse(IngestionSummaryDto dto) {
        return new IngestionSummaryResponse(
                dto.from(),
                dto.to(),
                dto.totalUniqueEvents(),
                dto.volumesBySource().stream().map(this::toSourceVolumeResponse).toList()
        );
    }

    private SourceVolumeResponse toSourceVolumeResponse(SourceVolumeDto dto) {
        return new SourceVolumeResponse(dto.sourceSystem(), dto.total());
    }
}
