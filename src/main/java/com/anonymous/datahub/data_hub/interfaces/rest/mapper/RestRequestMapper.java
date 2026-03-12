package com.anonymous.datahub.data_hub.interfaces.rest.mapper;

import com.anonymous.datahub.data_hub.application.dto.CreateEventDto;
import com.anonymous.datahub.data_hub.application.dto.UpdateEventDto;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.CreateEventRequest;
import com.anonymous.datahub.data_hub.interfaces.rest.dto.UpdateEventRequest;
import org.springframework.stereotype.Component;

@Component
public class RestRequestMapper {

    public CreateEventDto toCreateEventDto(CreateEventRequest request) {
        return new CreateEventDto(
                request.eventId(),
                request.sourceSystem(),
                request.payload(),
                request.occurredAt()
        );
    }

    public UpdateEventDto toUpdateEventDto(UpdateEventRequest request) {
        return new UpdateEventDto(
                request.sourceSystem(),
                request.payload(),
                request.occurredAt()
        );
    }
}
