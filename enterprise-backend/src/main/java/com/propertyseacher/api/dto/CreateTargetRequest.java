package com.propertyseacher.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Request payload for provisioning a new real estate monitoring target")
public record CreateTargetRequest(
        @Schema(description = "Human-readable label for the target", example = "Квартири Одеса (OLX)")
        @NotBlank(message = "Target title must not be blank")
        String title,

        @Schema(description = "Source marketplace platform", example = "OLX", allowableValues = {"OLX", "DOM.RIA", "MOCK"})
        @NotBlank(message = "Platform is required")
        @Pattern(regexp = "^(?i)(OLX|DOM\\.RIA|MOCK)$", message = "Platform must be OLX, DOM.RIA, or MOCK")
        String platform,

        @Schema(description = "Target search URL with applied filters", example = "https://www.olx.ua/uk/nedvizhimost/kvartiry/od/?currency=UAH&search[order]=created_at:desc&view=list")
        @NotBlank(message = "URL must not be blank")
        @URL(message = "Target URL must be a valid HTTP/HTTPS URL")
        String url
) {}
