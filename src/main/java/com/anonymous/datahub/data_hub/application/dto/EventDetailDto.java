package com.anonymous.datahub.data_hub.application.dto;

import java.time.Instant;

public record EventDetailDto(
        String eventId,
        String eventType,
        String sourceSystem,
        String payload,
        Instant createdAt,
        Instant updatedAt
) {
}
