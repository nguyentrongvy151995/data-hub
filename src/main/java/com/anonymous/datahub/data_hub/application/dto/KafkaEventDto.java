package com.anonymous.datahub.data_hub.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record KafkaEventDto(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @NotNull Instant createdAt,
        @NotBlank String source,
        @NotNull JsonNode payload
) {
}
