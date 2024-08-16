package com.propertyseacher;

import com.propertyseacher.analytics.PriceAnalyticsService;
import com.propertyseacher.domain.entity.DiscoveredProperty;
import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.repository.DiscoveredPropertyRepository;
import com.propertyseacher.domain.repository.PriceHistoryRepository;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.scraper.PropertyDto;
import com.propertyseacher.scraper.ScraperService;
import com.propertyseacher.service.TrackingService;
import com.propertyseacher.telegram.TelegramNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    private TelegramNotificationService telegramNotificationService;

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
    @DisplayName("Should detect and broadcast new property in Live Snipe mode")
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

        trackingService.processTarget(mockTarget);

        verify(telegramNotificationService, times(1)).broadcastNewProperty(newDto);

        // Process again with same URL -> should be filtered by 10-item memory ring buffer
        trackingService.processTarget(mockTarget);
        verify(telegramNotificationService, times(1)).broadcastNewProperty(newDto);
    }

    @Test
    @DisplayName("Should detect price drop on existing property in Database Analytics mode")
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
        when(priceAnalyticsService.parseNumericPrice("15 000 грн")).thenReturn(new java.math.BigDecimal("15000"));
        when(priceAnalyticsService.parseNumericPrice("13 500 грн")).thenReturn(new java.math.BigDecimal("13500"));
        when(priceAnalyticsService.detectCurrency("13 500 грн")).thenReturn("UAH");
        when(priceAnalyticsService.calculatePriceChangeSummary(any(), any(), any()))
                .thenReturn("📉 Ціна знизилася!");

        trackingService.processTarget(mockTarget);

        verify(discoveredPropertyRepository, times(1)).save(existingProperty);
        verify(telegramNotificationService, times(1)).broadcastPriceChange(eq(updatedDto), anyString());
    }
}
