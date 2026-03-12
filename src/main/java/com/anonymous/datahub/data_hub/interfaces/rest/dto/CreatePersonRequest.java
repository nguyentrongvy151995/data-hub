package com.anonymous.datahub.data_hub.interfaces.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreatePersonRequest(
        @NotBlank String name,
        @Min(0) int age
) {
        
}
