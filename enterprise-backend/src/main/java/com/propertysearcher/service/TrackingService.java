package com.propertysearcher.service;

import com.propertysearcher.analytics.PriceAnalyticsService;
import com.propertysearcher.cache.DeduplicationCacheStrategy;
import com.propertysearcher.domain.entity.SearchTarget;
import com.propertysearcher.domain.repository.PriceHistoryRepository;
import com.propertysearcher.domain.repository.SearchTargetRepository;
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
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final ScraperService scraperService;
    private final DeduplicationCacheStrategy deduplicationCacheStrategy;
    private final ApplicationEventPublisher eventPublisher;
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
