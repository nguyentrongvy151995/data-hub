package com.anonymous.datahub.data_hub.interfaces.rest.dto;

import java.time.Instant;

public record EventResponse(
        String eventId,
        String sourceSystem,
        String payload,
        Instant occurredAt,
        Instant receivedAt
) {
}
