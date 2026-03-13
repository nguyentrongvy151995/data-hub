package com.anonymous.datahub.data_hub.domain.model;

import java.time.Instant;

public record IncomingEvent(
        String eventId,
        String eventType,
        String sourceSystem,
        String payload,
        Instant createdAt,
        Instant updatedAt
) {
}
