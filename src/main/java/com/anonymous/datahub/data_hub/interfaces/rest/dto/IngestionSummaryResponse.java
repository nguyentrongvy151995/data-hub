package com.anonymous.datahub.data_hub.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;

public record IngestionSummaryResponse(
        Instant from,
        Instant to,
        long totalUniqueEvents,
        List<SourceVolumeResponse> volumesBySource
) {
}
