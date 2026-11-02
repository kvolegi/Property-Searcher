package com.propertysearcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertysearcher.analytics.PriceAnalyticsService;
import com.propertysearcher.cache.DeduplicationCacheStrategy;
import com.propertysearcher.domain.entity.DiscoveredProperty;
import com.propertysearcher.domain.entity.PriceHistory;
import com.propertysearcher.domain.entity.SearchTarget;
import com.propertysearcher.domain.repository.DiscoveredPropertyRepository;
import com.propertysearcher.domain.repository.PriceHistoryRepository;
import com.propertysearcher.domain.repository.SearchTargetRepository;
import com.propertysearcher.event.PriceChangedEvent;
import com.propertysearcher.event.PropertyDiscoveredEvent;
import com.propertysearcher.event.TriggerScanEvent;
import com.propertysearcher.scraper.PropertyDto;
import com.propertysearcher.scraper.ScraperService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
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
    private final ObjectMapper objectMapper;
    private final TrackingPersistenceService trackingPersistenceService;

    private final java.util.concurrent.atomic.AtomicBoolean isScanInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Getter
    @Setter
    private volatile TrackingMode trackingMode = TrackingMode.LIVE_SNIPE;

    public String getCurrentMode() {
        return trackingMode.name();
    }

    @EventListener
    public void onTriggerScan(TriggerScanEvent event) {
        log.info("Received on-demand scan trigger from chatId: {}", event.requestedByChatId());
        trackAllTargets();
    }

    @Scheduled(fixedDelayString = "${app.tracker.delay-ms:10000}", initialDelay = 5000)
    public void trackAllTargets() {
        if (!isScanInProgress.compareAndSet(false, true)) {
            log.warn("[CONCURRENCY_GUARD] Tracking cycle already in progress, skipping overlapping execution.");
            return;
        }
        try {
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
        } finally {
            isScanInProgress.set(false);
        }
    }

    /**
     * Executes external scraping OUTSIDE any database transaction to prevent HikariCP connection starvation.
     * Transactional boundaries are strictly delegated to TrackingPersistenceService.
     */
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
            trackingPersistenceService.persistDatabaseAnalytics(target, scraped, dto -> evaluateDynamicBargain(target, dto, scraped));
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

                PriceAnalyticsService.DealValuation valuation = evaluateDynamicBargain(target, dto, scraped);
                eventPublisher.publishEvent(new PropertyDiscoveredEvent(dto, valuation));
            }
        }
    }

    /**
     * Database Analytics Mode (Full historical tracking, price drop calculation & Bargain Detection).
     * Protected by @Transactional strictly over the database persistence operations.
     */
    @Transactional
    public void persistDatabaseAnalytics(SearchTarget target, List<PropertyDto> scraped) {
        for (PropertyDto dto : scraped) {
            Optional<DiscoveredProperty> existingOpt = discoveredPropertyRepository
                    .findByExternalIdAndPlatform(dto.externalId(), dto.platform());

            BigDecimal numericPrice = priceAnalyticsService.parseNumericPrice(dto.price());
            String currency = priceAnalyticsService.detectCurrency(dto.price());
            PriceAnalyticsService.DealValuation valuation = evaluateDynamicBargain(target, dto, scraped);

            if (existingOpt.isEmpty()) {
                log.info("[DB_ANALYTICS] New property detected: [{}] {}", dto.category(), dto.title());

                String rawJson;
                try {
                    rawJson = objectMapper.writeValueAsString(Map.of(
                            "externalId", dto.externalId(),
                            "platform", dto.platform(),
                            "title", dto.title(),
                            "price", dto.price(),
                            "url", dto.url(),
                            "category", dto.category() != null ? dto.category() : ""
                    ));
                } catch (Exception e) {
                    rawJson = "{}";
                }

                DiscoveredProperty entity = DiscoveredProperty.builder()
                        .externalId(dto.externalId())
                        .platform(dto.platform())
                        .title(dto.title())
                        .price(dto.price())
                        .url(dto.url())
                        .category(dto.category())
                        .rawPayload(rawJson)
                        .build();

                DiscoveredProperty saved = discoveredPropertyRepository.save(entity);
                recordPrice(saved, dto.price());
                eventPublisher.publishEvent(new PropertyDiscoveredEvent(dto, valuation));
            } else {
                DiscoveredProperty existing = existingOpt.get();
                if (!dto.price().equalsIgnoreCase(existing.getPrice())) {
                    BigDecimal oldNumeric = priceAnalyticsService.parseNumericPrice(existing.getPrice());
                    BigDecimal newNumeric = priceAnalyticsService.parseNumericPrice(dto.price());

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

    private PriceAnalyticsService.DealValuation evaluateDynamicBargain(SearchTarget target, PropertyDto dto, List<PropertyDto> batch) {
        BigDecimal numericPrice = priceAnalyticsService.parseNumericPrice(dto.price());
        String currency = priceAnalyticsService.detectCurrency(dto.price());
        if (numericPrice == null) {
            return new PriceAnalyticsService.DealValuation(false, 0.0, 0.0, "📊 Market Standard", "Awaiting numeric price.");
        }

        List<BigDecimal> sample = new ArrayList<>();
        try {
            List<BigDecimal> history = priceHistoryRepository
                    .findRecentPricesByPlatformAndCurrency(target.getPlatform(), currency, org.springframework.data.domain.PageRequest.of(0, 50));
            if (history != null) {
                sample.addAll(history);
            }
        } catch (Exception e) {
            log.debug("No historical repository prices available: {}", e.getMessage());
        }

        if (batch != null) {
            for (PropertyDto item : batch) {
                BigDecimal p = priceAnalyticsService.parseNumericPrice(item.price());
                if (p != null && currency.equalsIgnoreCase(priceAnalyticsService.detectCurrency(item.price()))) {
                    sample.add(p);
                }
            }
        }

        return priceAnalyticsService.evaluateStatisticalBargain(numericPrice, sample, currency, target.getPlatform());
    }
}
