package com.propertyseacher.scraper;

import com.propertyseacher.scraper.strategy.ScraperStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScraperService {

    private final Map<String, ScraperStrategy> strategies;

    public ScraperService(List<ScraperStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        s -> s.getSupportedPlatform().toUpperCase(),
                        Function.identity()
                ));
    }

    public List<PropertyDto> executeScrape(String platform, String url, String category) {
        ScraperStrategy strategy = strategies.get(platform.toUpperCase());
        if (strategy == null) {
            // Default fallback
            strategy = strategies.get("OLX");
        }
        return strategy != null ? strategy.scrape(url, category) : List.of();
    }
}
