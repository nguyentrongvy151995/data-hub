package com.anonymous.datahub.data_hub.application.usecase;

import com.anonymous.datahub.data_hub.application.dto.EventIngestionResult;
import com.anonymous.datahub.data_hub.application.dto.KafkaEventDto;

public interface IngestEventUseCase {

    EventIngestionResult claimForProcessing(KafkaEventDto eventDto);

    void processClaimedEvent(KafkaEventDto eventDto);

    default EventIngestionResult ingest(KafkaEventDto eventDto) {
        EventIngestionResult claimResult = claimForProcessing(eventDto);
        if (claimResult == EventIngestionResult.DUPLICATE) {
            return claimResult;
        }
        processClaimedEvent(eventDto);
        return EventIngestionResult.STORED;
    }

    void markFailedAfterRetries(KafkaEventDto eventDto);
}
