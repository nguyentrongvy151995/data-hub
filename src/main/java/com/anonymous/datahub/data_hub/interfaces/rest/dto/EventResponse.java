package com.anonymous.datahub.data_hub.interfaces.rest.dto;

import java.time.Instant;

public record EventResponse(
        String eventId,
        String eventType,
        String sourceSystem,
        String payload,
        Instant createdAt,
        Instant updatedAt
) {
}
