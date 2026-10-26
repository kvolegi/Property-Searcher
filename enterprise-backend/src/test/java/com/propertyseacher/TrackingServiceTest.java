package com.propertyseacher;

import com.propertyseacher.analytics.PriceAnalyticsService;
import com.propertyseacher.cache.DeduplicationCacheStrategy;
import com.propertyseacher.domain.entity.DiscoveredProperty;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.PriceHistoryRepository;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.event.PriceChangedEvent;
import com.propertyseacher.event.PropertyDiscoveredEvent;
import com.propertyseacher.scraper.PropertyDto;
import com.propertyseacher.scraper.ScraperService;
import com.propertyseacher.service.TrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private SearchTargetRepository searchTargetRepository;

    @Mock
    private DiscoveredPropertyRepository discoveredPropertyRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    @Mock
    private ScraperService scraperService;

    @Mock
    private DeduplicationCacheStrategy deduplicationCacheStrategy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TrackingService trackingService;

    private SearchTarget mockTarget;

    @BeforeEach
    void setUp() {
        mockTarget = SearchTarget.builder()
                .id(1L)
                .title("Одеса 1-к")
                .url("https://dom.ria.com/uk/arenda-kvartir/odessa/")
                .platform("DOM_RIA")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should detect and emit PropertyDiscoveredEvent in Live Snipe mode")
    void shouldNotifyInLiveSnipeMode() {
        trackingService.setTrackingMode(TrackingService.TrackingMode.LIVE_SNIPE);

        PropertyDto newDto = new PropertyDto(
                "ext-12345",
                "DOM_RIA",
                "Квартира в центрі",
                "12 000 грн",
                "https://dom.ria.com/realty/12345",
                "Одеса 1-к",
                "Гарна квартира",
                List.of("https://cdn.riastatic.com/photo/123.jpg"),
                "Одеса"
        );

        when(scraperService.executeScrape(mockTarget.getPlatform(), mockTarget.getUrl(), mockTarget.getTitle()))
                .thenReturn(List.of(newDto));
        when(deduplicationCacheStrategy.contains(mockTarget.getUrl(), newDto.url())).thenReturn(false);

        trackingService.processTarget(mockTarget);

        verify(deduplicationCacheStrategy, times(1)).add(mockTarget.getUrl(), newDto.url());
        verify(eventPublisher, times(1)).publishEvent(any(PropertyDiscoveredEvent.class));
    }

    @Test
    @DisplayName("Should detect price drop on existing property and emit PriceChangedEvent")
    void shouldDetectPriceDropAndNotify() {
        trackingService.setTrackingMode(TrackingService.TrackingMode.DATABASE_ANALYTICS);

        DiscoveredProperty existingProperty = DiscoveredProperty.builder()
                .id(1L)
                .externalId("ext-existing")
                .platform("DOM_RIA")
                .title("Квартира")
                .price("15 000 грн")
                .url("https://dom.ria.com/realty/existing")
                .category("Одеса 1-к")
                .build();

        PropertyDto updatedDto = new PropertyDto(
                "ext-existing",
                "DOM_RIA",
                "Квартира",
                "13 500 грн",
                "https://dom.ria.com/realty/existing",
                "Одеса 1-к"
        );

        when(scraperService.executeScrape(mockTarget.getPlatform(), mockTarget.getUrl(), mockTarget.getTitle()))
                .thenReturn(List.of(updatedDto));
        when(discoveredPropertyRepository.findByExternalIdAndPlatform("ext-existing", "DOM_RIA"))
                .thenReturn(java.util.Optional.of(existingProperty));
        when(priceAnalyticsService.parseNumericPrice("15 000 грн")).thenReturn(new BigDecimal("15000"));
        when(priceAnalyticsService.parseNumericPrice("13 500 грн")).thenReturn(new BigDecimal("13500"));
        when(priceAnalyticsService.detectCurrency("13 500 грн")).thenReturn("UAH");
        when(priceAnalyticsService.calculatePriceChangeSummary(any(), any(), any()))
                .thenReturn("📉 Ціна знизилася!");

        trackingService.processTarget(mockTarget);

        verify(discoveredPropertyRepository, times(1)).save(existingProperty);
        verify(eventPublisher, times(1)).publishEvent(any(PriceChangedEvent.class));
    }

    @Test
    @DisplayName("Should accurately evaluate Hot Deal bargain when price is 25% below market benchmark")
    void shouldEvaluateHotDealBargain() {
        PriceAnalyticsService realService = new PriceAnalyticsService();
        BigDecimal askingPrice = new BigDecimal("45000");
        BigDecimal benchmark = new BigDecimal("60000");

        PriceAnalyticsService.DealValuation valuation = realService.evaluateBargain(askingPrice, benchmark, "USD");

        assertTrue(valuation.isHotDeal());
        assertEquals(25.0, valuation.discountPercentage(), 0.1);
        assertTrue(valuation.badge().contains("HOT DEAL"));
    }
}
