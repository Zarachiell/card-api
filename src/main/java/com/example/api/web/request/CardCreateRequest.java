package com.example.api.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
public record CardCreateRequest(
        @NotBlank String cardNumber,
        @NotBlank String holderName,
        @Min(1) @Max(12) int expiryMonth,
        @Min(2000) int expiryYear,
        String brand,
        Map<String, Object> metadata
) {}
