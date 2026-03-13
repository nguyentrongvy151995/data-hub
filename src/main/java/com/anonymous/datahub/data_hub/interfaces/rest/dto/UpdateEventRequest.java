package com.anonymous.datahub.data_hub.interfaces.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateEventRequest(
        @NotBlank String sourceSystem,
        @NotNull Instant createdAt,
        @NotNull JsonNode payload
) {
}
