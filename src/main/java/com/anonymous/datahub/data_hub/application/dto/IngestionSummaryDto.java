package com.anonymous.datahub.data_hub.application.dto;

import java.time.Instant;
import java.util.List;

public record IngestionSummaryDto(
        Instant from,
        Instant to,
        long totalUniqueEvents,
        List<SourceVolumeDto> volumesBySource
) {
}
