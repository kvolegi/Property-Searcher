package com.propertysearcher.scraper.strategy;

import com.propertysearcher.scraper.PropertyDto;

import java.util.List;

public interface ScraperStrategy {
    String getSupportedPlatform();
    List<PropertyDto> scrape(String targetUrl, String category);
}
