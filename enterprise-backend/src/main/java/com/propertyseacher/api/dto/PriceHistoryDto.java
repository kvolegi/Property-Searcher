package com.propertyseacher.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Immutable DTO projection of a recorded historical price point")
public record PriceHistoryDto(
        @Schema(description = "Unique price record ID") Long id,
        @Schema(description = "Associated property ID") Long propertyId,
        @Schema(description = "Original raw price label") String rawPrice,
        @Schema(description = "Normalized numeric price") BigDecimal numericPrice,
        @Schema(description = "Currency ISO code") String currency,
        @Schema(description = "Timestamp when this price was observed") LocalDateTime recordedAt
) {}
