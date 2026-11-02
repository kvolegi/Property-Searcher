package com.propertysearcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertysearcher.analytics.PriceAnalyticsService;
import com.propertysearcher.cache.DeduplicationCacheStrategy;
import com.propertysearcher.domain.entity.DiscoveredProperty;
import com.propertysearcher.domain.entity.SearchTarget;
import com.propertysearcher.domain.repository.DiscoveredPropertyRepository;
import com.propertysearcher.domain.repository.PriceHistoryRepository;
import com.propertysearcher.domain.repository.SearchTargetRepository;
import com.propertysearcher.event.PriceChangedEvent;
import com.propertysearcher.event.PropertyDiscoveredEvent;
import com.propertysearcher.scraper.PropertyDto;
import com.propertysearcher.scraper.ScraperService;
import com.propertysearcher.service.TrackingPersistenceService;
import com.propertysearcher.service.TrackingService;
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

    @Mock
    private TrackingPersistenceService trackingPersistenceService;

    @org.mockito.Spy
    private ObjectMapper objectMapper = new ObjectMapper();

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
    @DisplayName("Should delegate database persistence to TrackingPersistenceService in DATABASE_ANALYTICS mode")
    void shouldDetectPriceDropAndNotify() {
        trackingService.setTrackingMode(TrackingService.TrackingMode.DATABASE_ANALYTICS);

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

        trackingService.processTarget(mockTarget);

        verify(trackingPersistenceService, times(1)).persistDatabaseAnalytics(eq(mockTarget), any(), any());
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

    @Test
    @DisplayName("Should detect statistical anomaly hot deal via authentic Z-Score evaluation")
    void shouldDetectStatisticalAnomalyZScore() {
        PriceAnalyticsService realService = new PriceAnalyticsService();
        List<BigDecimal> historical = List.of(
                new BigDecimal("60000"),
                new BigDecimal("65000"),
                new BigDecimal("55000"),
                new BigDecimal("62000"),
                new BigDecimal("58000")
        );
        BigDecimal askingPrice = new BigDecimal("48000");

        PriceAnalyticsService.DealValuation valuation = realService.evaluateStatisticalBargain(
                askingPrice, historical, "USD", "DOM_RIA"
        );

        assertTrue(valuation.isHotDeal());
        assertTrue(valuation.zScore() >= 2.0, "Expected Z-Score >= 2.0 but was " + valuation.zScore());
        assertTrue(valuation.badge().contains("HOT DEAL"));
        assertTrue(valuation.analysisText().contains("standard deviations below"));
    }
}
