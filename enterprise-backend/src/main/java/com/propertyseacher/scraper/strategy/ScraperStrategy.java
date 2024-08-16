package com.propertyseacher.scraper.strategy;

import com.propertyseacher.scraper.PropertyDto;

import java.util.List;

public interface ScraperStrategy {
    String getSupportedPlatform();
    List<PropertyDto> scrape(String targetUrl, String category);
}
