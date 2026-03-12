package com.anonymous.datahub.data_hub.application.usecase;

import com.anonymous.datahub.data_hub.application.dto.CreateEventDto;
import com.anonymous.datahub.data_hub.application.dto.EventDetailDto;
import com.anonymous.datahub.data_hub.application.dto.UpdateEventDto;

public interface ManageEventUseCase {

    EventDetailDto create(CreateEventDto createEventDto);

    EventDetailDto update(String eventId, UpdateEventDto updateEventDto);

    void delete(String eventId);
}
