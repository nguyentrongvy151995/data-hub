package com.anonymous.datahub.data_hub.application.usecase;

import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;

public interface IngestEventUseCase {

    EventIngestionResult ingest(KafkaEventDto eventDto);

    void markFailedAfterRetries(KafkaEventDto eventDto);
}
