package com.propertyseacher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyseacher.analytics.PriceAnalyticsService;
import com.propertyseacher.domain.entity.DiscoveredProperty;
import com.propertyseacher.domain.entity.PriceHistory;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.PriceHistoryRepository;
import com.propertyseacher.event.PriceChangedEvent;
import com.propertyseacher.event.PropertyDiscoveredEvent;
import com.propertyseacher.scraper.PropertyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Dedicated Transactional Persistence Boundary.
 * Separates database write transactions from long-running network scraping,
 * preventing HikariCP pool starvation and eliminating Spring AOP self-invocation traps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingPersistenceService {

    private final DiscoveredPropertyRepository discoveredPropertyRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Executes entity mutations inside an active, proxy-managed Spring transaction.
     */
    @Transactional
    public void persistDatabaseAnalytics(SearchTarget target, List<PropertyDto> scraped,
                                         Function<PropertyDto, PriceAnalyticsService.DealValuation> valuationEvaluator) {
        for (PropertyDto dto : scraped) {
            Optional<DiscoveredProperty> existingOpt = discoveredPropertyRepository
                    .findByExternalIdAndPlatform(dto.externalId(), dto.platform());

            String currency = priceAnalyticsService.detectCurrency(dto.price());
            PriceAnalyticsService.DealValuation valuation = valuationEvaluator.apply(dto);

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
}
