package com.propertyseacher.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "High-level analytics and market telemetry summary")
public record AnalyticsSummaryDto(
        @Schema(description = "Total number of active search targets", example = "5")
        long totalActiveTargets,

        @Schema(description = "Total unique property listings discovered and indexed", example = "1420")
        long totalPropertiesTracked,

        @Schema(description = "Total historical price update events captured", example = "312")
        long totalPriceChangeEvents,

        @Schema(description = "Breakdown of tracked listings by platform")
        Map<String, Long> propertiesByPlatform,

        @Schema(description = "Current operational mode of the background tracking engine", example = "LIVE_SNIPE")
        String activeTrackingMode
) {}
