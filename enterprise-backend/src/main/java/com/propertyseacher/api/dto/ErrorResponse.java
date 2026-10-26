package com.propertyseacher.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Standard RFC 7807 compliant API error payload")
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Error title / reason phrase", example = "Bad Request")
        String error,

        @Schema(description = "Detailed human-readable error description", example = "Validation failed for one or more fields")
        String message,

        @Schema(description = "List of field-level validation errors, if applicable")
        List<String> details,

        @Schema(description = "Request URI that generated this error", example = "/api/v1/targets")
        String path,

        @Schema(description = "ISO-8601 timestamp of the error event")
        Instant timestamp
) {
    public static ErrorResponse of(int status, String error, String message, List<String> details, String path) {
        return new ErrorResponse(status, error, message, details, path, Instant.now());
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, List.of(), path, Instant.now());
    }
}
