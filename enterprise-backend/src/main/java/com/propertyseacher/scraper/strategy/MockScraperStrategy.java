package com.propertyseacher.scraper.strategy;

import com.propertyseacher.scraper.PropertyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class MockScraperStrategy implements ScraperStrategy {

    private static final String PLATFORM = "MOCK";

    @Override
    public String getSupportedPlatform() {
        return PLATFORM;
    }

    @Override
    public List<PropertyDto> scrape(String targetUrl, String category) {
        log.info("Generating mock properties for: {}", category);
        List<PropertyDto> list = new ArrayList<>();
        String randId = UUID.randomUUID().toString().substring(0, 8);
        list.add(new PropertyDto(
                "mock-" + randId,
                PLATFORM,
                "Затишна 2-кімнатна квартира біля парку (" + category + ")",
                "15 000 грн",
                "https://example.com/property/" + randId,
                category
        ));
        return list;
    }
}
