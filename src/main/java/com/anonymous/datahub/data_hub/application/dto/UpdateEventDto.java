package com.anonymous.datahub.data_hub.application.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record UpdateEventDto(
        String sourceSystem,
        JsonNode payload,
        Instant createdAt
) {
}
