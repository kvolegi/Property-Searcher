package com.propertysearcher.scraper;

import com.propertysearcher.scraper.exception.AntiBotChallengeException;
import com.propertysearcher.scraper.strategy.HeadlessBrowserScraperStrategy;
import com.propertysearcher.scraper.strategy.ScraperStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ScraperService {

    private final Map<String, ScraperStrategy> strategies;

    public ScraperService(List<ScraperStrategy> strategyList) {
        this.strategies = new HashMap<>();
        for (ScraperStrategy s : strategyList) {
            String key = s.getSupportedPlatform().toUpperCase();
            this.strategies.put(key, s);
            this.strategies.put(key.replace("_", "."), s);
            this.strategies.put(key.replace(".", "_"), s);
        }
    }

    @CircuitBreaker(name = "scraperEngine", fallbackMethod = "scrapeFallback")
    public List<PropertyDto> executeScrape(String platform, String url, String category) {
        String key = platform != null ? platform.toUpperCase() : "OLX";
        ScraperStrategy strategy = strategies.get(key);
        if (strategy == null) {
            strategy = strategies.get(key.replace(".", "_"));
        }
        if (strategy == null) {
            strategy = strategies.get(key.replace("_", "."));
        }
        if (strategy == null) {
            strategy = strategies.get("OLX");
        }
        if (strategy == null) {
            return List.of();
        }

        try {
            return strategy.scrape(url, category);
        } catch (AntiBotChallengeException challengeException) {
            log.warn("[ANTI_BOT_ESCALATION] Primary scraper encountered barrier ({}: {}). Escalating to Headless Browser Automation...",
                    challengeException.getStatusCode(), challengeException.getDetectedChallenge());
            ScraperStrategy headless = strategies.get(HeadlessBrowserScraperStrategy.PLATFORM);
            if (headless != null) {
                return headless.scrape(url, category);
            }
            throw challengeException;
        }
    }

    public List<PropertyDto> scrapeFallback(String platform, String url, String category, Throwable t) {
        log.warn("Resilience4j CircuitBreaker [scraperEngine] active on platform {}. Fallback engaged. Cause: {}",
                platform, t.getMessage());
        return List.of();
    }
}
