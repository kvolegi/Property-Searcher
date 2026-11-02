# Contributing to Property Searcher 3

Welcome! We are excited to collaborate with engineers who value clean architecture, automated testing, and production resilience.

This guide outlines our development workflow, coding conventions, and a step-by-step tutorial on extending the scraper engine with new real estate platforms.

---

## 1. Development Principles & Code Standards

- **Zero Breaking Changes**: The core domain engine must remain backwards-compatible.
- **Fail Gracefully**: External network calls must be wrapped in timeouts and fallback protections (see `docs/adr/0002-resilience-circuit-breaker-fallback.md`).
- **Tests Before Merge**: Every new feature or bug fix must include JUnit 5 tests.
- **Conventional Commits**: Write clear, descriptive commit messages:
  - `feat: add Otodom.pl scraper strategy`
  - `fix: resolve currency parsing for space-separated prices`
  - `docs: update deployment runbook for Docker Compose v2`
  - `test: add integration test for price alert dispatch`

---

## 2. Tutorial: Adding a New Real Estate Scraper Platform

The scraping engine is architected using the **Strategy Pattern** combined with Spring's dependency injection. New platforms can be plugged in without modifying any existing classes (complying strictly with the **Open-Closed Principle**).

### Step 1: Implement `ScraperStrategy`
Create a new strategy class under `enterprise-backend/src/main/java/com/propertyseacher/scraper/strategy/`:

```java
package com.propertyseacher.scraper.strategy;

import com.propertyseacher.scraper.PropertyDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class OtodomScraperStrategy implements ScraperStrategy {

    @Override
    public String getSupportedPlatform() {
        return "OTODOM";
    }

    @Override
    public List<PropertyDto> scrape(String url, String category) {
        List<PropertyDto> results = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();

            Elements listings = doc.select("article[data-cy='listing-item']");
            for (Element item : listings) {
                // Extract fields cleanly
                String title = item.select("p[data-cy='listing-item-title']").text();
                String link = item.select("a").attr("abs:href");
                String priceText = item.select("span[data-cy='listing-item-price']").text();

                results.add(PropertyDto.builder()
                        .externalId("otodom-" + item.attr("id"))
                        .title(title)
                        .url(link)
                        .price(parsePrice(priceText))
                        .currency("PLN")
                        .platform("OTODOM")
                        .category(category)
                        .build());
            }
        } catch (Exception ex) {
            // Log and return empty list to trigger graceful fallback
        }
        return results;
    }

    private BigDecimal parsePrice(String text) {
        String clean = text.replaceAll("[^0-9.]", "");
        return clean.isEmpty() ? BigDecimal.ZERO : new BigDecimal(clean);
    }
}
```

### Step 2: Automatic Spring Registration
Because the class is annotated with `@Component`, Spring Boot automatically discovers it at startup and injects it into `ScraperService` without touching a single line of orchestration code.

### Step 3: Add Unit & Integration Tests
Add a corresponding test under `src/test/java/com/propertyseacher/scraper/`:
- Test parsing against an offline HTML fixture to keep tests fast and deterministic.
- Assert price parsing, currency tagging, and error handling when elements are missing.

---

## 3. Pull Request Checklist

Before submitting a Pull Request, verify locally via the root `Makefile`:

```bash
# 1. Run the complete test suite (51 tests)
make test

# 2. Verify compilation of both modules
make build

# 3. Clean up temporary files
make clean
```

Ensure all tests pass and your branch is rebased onto the latest `main`.
