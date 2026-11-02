package com.propertyseacher.scraper;

import com.propertyseacher.scraper.strategy.ScraperStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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

    @CircuitBreaker(name = "scraperEngine", fallbackMethod = "scrapeFallback")
    public List<PropertyDto> executeScrape(String platform, String url, String category) {
        ScraperStrategy strategy = strategies.get(platform.toUpperCase());
        if (strategy == null) {
            strategy = strategies.get("OLX");
        }
        if (strategy == null) {
            return List.of();
        }
        return strategy.scrape(url, category);
    }

    public List<PropertyDto> scrapeFallback(String platform, String url, String category, Throwable t) {
        log.warn("Resilience4j CircuitBreaker [scraperEngine] active on platform {}. Fallback engaged. Cause: {}",
                platform, t.getMessage());
        return List.of();
    }
}
