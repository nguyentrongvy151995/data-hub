package com.anonymous.datahub.data_hub.application.usecase;

import com.anonymous.datahub.data_hub.application.dto.EventDetailDto;
import com.anonymous.datahub.data_hub.application.dto.IngestionSummaryDto;

import java.time.Instant;
import java.util.List;

public interface QueryEventUseCase {

    EventDetailDto getByEventId(String eventId);

    List<EventDetailDto> getAll();

    IngestionSummaryDto getSummary(Instant from, Instant to);
}
