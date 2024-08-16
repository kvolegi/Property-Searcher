package com.propertyseacher.scraper;

import java.util.List;

public record PropertyDto(
    String externalId,
    String platform,
    String title,
    String price,
    String url,
    String category,
    String description,
    List<String> photoUrls,
    String location
) {
    // Backwards-compatible constructor
    public PropertyDto(String externalId, String platform, String title, String price, String url, String category) {
        this(externalId, platform, title, price, url, category, "", List.of(), "");
    }
}
