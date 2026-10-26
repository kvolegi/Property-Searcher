package com.propertyseacher.service;

import com.propertyseacher.analytics.PriceAnalyticsService;
import com.propertyseacher.cache.DeduplicationCacheStrategy;
import com.propertyseacher.domain.entity.DiscoveredProperty;
import com.propertyseacher.domain.entity.PriceHistory;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.PriceHistoryRepository;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.event.PriceChangedEvent;
import com.propertyseacher.event.PropertyDiscoveredEvent;
import com.propertyseacher.scraper.PropertyDto;
import com.propertyseacher.scraper.ScraperService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingService {

    public enum TrackingMode {
        LIVE_SNIPE,          // In-memory 50-slot circular buffer (sub-2s sniping, like core-desktop)
        DATABASE_ANALYTICS   // DB storage + price history + deduplication + bargain analysis
    }

    private final SearchTargetRepository searchTargetRepository;
    private final DiscoveredPropertyRepository discoveredPropertyRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final ScraperService scraperService;
    private final DeduplicationCacheStrategy deduplicationCacheStrategy;
    private final ApplicationEventPublisher eventPublisher;

    @Getter
    @Setter
    private TrackingMode trackingMode = TrackingMode.LIVE_SNIPE;

    public String getCurrentMode() {
        return trackingMode.name();
    }

    @Scheduled(fixedDelayString = "${app.tracker.delay-ms:10000}", initialDelay = 5000)
    public void trackAllTargets() {
        log.info("Starting property tracking cycle (Mode: {})...", trackingMode);
        List<SearchTarget> targets = searchTargetRepository.findAllByActiveTrue();

        for (SearchTarget target : targets) {
            try {
                processTarget(target);
            } catch (Exception e) {
                log.error("Error processing target [{}]: {}", target.getTitle(), e.getMessage());
            }
        }
        log.info("Completed tracking cycle.");
    }

    @Transactional
    public void processTarget(SearchTarget target) {
        List<PropertyDto> scraped = scraperService.executeScrape(
                target.getPlatform(),
                target.getUrl(),
                target.getTitle()
        );

        if (scraped == null || scraped.isEmpty()) {
            return;
        }

        if (trackingMode == TrackingMode.LIVE_SNIPE) {
            processLiveSnipe(target, scraped);
        } else {
            processDatabaseAnalytics(target, scraped);
        }
    }

    /**
     * Ultra-fast Live Snipe Mode (zero DB latency, in-memory 50-slot ring buffer check)
     * Matches the core desktop client circular memory buffer specification.
     * Emits PropertyDiscoveredEvent to decoupled event subscribers (Telegram, Kafka, etc.).
     */
    private void processLiveSnipe(SearchTarget target, List<PropertyDto> scraped) {
        for (PropertyDto dto : scraped) {
            if (dto.url() == null || dto.url().isBlank()) continue;

            if (!deduplicationCacheStrategy.contains(target.getUrl(), dto.url())) {
                log.info("[LIVE_SNIPE] Fresh organic property detected: [{}] {}", dto.category(), dto.title());
                deduplicationCacheStrategy.add(target.getUrl(), dto.url());
                eventPublisher.publishEvent(new PropertyDiscoveredEvent(dto));
            }
        }
    }

    /**
     * Database Analytics Mode (Full historical tracking, price drop calculation & Bargain Detection)
     */
    private void processDatabaseAnalytics(SearchTarget target, List<PropertyDto> scraped) {
        for (PropertyDto dto : scraped) {
            Optional<DiscoveredProperty> existingOpt = discoveredPropertyRepository
                    .findByExternalIdAndPlatform(dto.externalId(), dto.platform());

            if (existingOpt.isEmpty()) {
                log.info("[DB_ANALYTICS] New property detected: [{}] {}", dto.category(), dto.title());

                DiscoveredProperty entity = DiscoveredProperty.builder()
                        .externalId(dto.externalId())
                        .platform(dto.platform())
                        .title(dto.title())
                        .price(dto.price())
                        .url(dto.url())
                        .category(dto.category())
                        .build();

                DiscoveredProperty saved = discoveredPropertyRepository.save(entity);
                recordPrice(saved, dto.price());
                eventPublisher.publishEvent(new PropertyDiscoveredEvent(dto));
            } else {
                DiscoveredProperty existing = existingOpt.get();
                if (!dto.price().equalsIgnoreCase(existing.getPrice())) {
                    BigDecimal oldNumeric = priceAnalyticsService.parseNumericPrice(existing.getPrice());
                    BigDecimal newNumeric = priceAnalyticsService.parseNumericPrice(dto.price());
                    String currency = priceAnalyticsService.detectCurrency(dto.price());

                    String changeSummary = priceAnalyticsService.calculatePriceChangeSummary(oldNumeric, newNumeric, currency);

                    log.info("Price changed for [{}]: was {} -> now {}", dto.title(), existing.getPrice(), dto.price());
                    existing.setPrice(dto.price());
                    discoveredPropertyRepository.save(existing);
                    recordPrice(existing, dto.price());

                    if (!changeSummary.isBlank()) {
                        eventPublisher.publishEvent(new PriceChangedEvent(dto, changeSummary, oldNumeric, newNumeric));
                    }
                }
            }
        }
    }

    private void recordPrice(DiscoveredProperty property, String rawPrice) {
        BigDecimal numeric = priceAnalyticsService.parseNumericPrice(rawPrice);
        String currency = priceAnalyticsService.detectCurrency(rawPrice);

        PriceHistory history = PriceHistory.builder()
                .property(property)
                .rawPrice(rawPrice)
                .numericPrice(numeric)
                .currency(currency)
                .build();

        priceHistoryRepository.save(history);
    }
}
