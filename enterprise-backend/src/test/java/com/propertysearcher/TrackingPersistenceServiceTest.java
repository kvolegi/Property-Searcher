package com.propertysearcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertysearcher.analytics.PriceAnalyticsService;
import com.propertysearcher.domain.entity.DiscoveredProperty;
import com.propertysearcher.domain.entity.SearchTarget;
import com.propertysearcher.domain.repository.DiscoveredPropertyRepository;
import com.propertysearcher.domain.repository.PriceHistoryRepository;
import com.propertysearcher.event.PriceChangedEvent;
import com.propertysearcher.event.PropertyDiscoveredEvent;
import com.propertysearcher.scraper.PropertyDto;
import com.propertysearcher.service.TrackingPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingPersistenceServiceTest {

    @Mock
    private DiscoveredPropertyRepository discoveredPropertyRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TrackingPersistenceService persistenceService;

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
    @DisplayName("Should save fresh property entity and dispatch PropertyDiscoveredEvent")
    void shouldPersistNewProperty() {
        PropertyDto newDto = new PropertyDto(
                "ext-new-1",
                "DOM_RIA",
                "Новобудова",
                "20 000 грн",
                "https://dom.ria.com/realty/new1",
                "Одеса 1-к"
        );

        when(discoveredPropertyRepository.findByExternalIdAndPlatform("ext-new-1", "DOM_RIA"))
                .thenReturn(Optional.empty());
        when(discoveredPropertyRepository.save(any(DiscoveredProperty.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        persistenceService.persistDatabaseAnalytics(
                mockTarget,
                List.of(newDto),
                dto -> new PriceAnalyticsService.DealValuation(true, 15.0, 1.8, "HOT", "Analysis")
        );

        verify(discoveredPropertyRepository, times(1)).save(any(DiscoveredProperty.class));
        verify(priceHistoryRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publishEvent(any(PropertyDiscoveredEvent.class));
    }

    @Test
    @DisplayName("Should update existing property and fire PriceChangedEvent on price mutation")
    void shouldUpdateExistingPropertyOnPriceDrop() {
        DiscoveredProperty existing = DiscoveredProperty.builder()
                .id(10L)
                .externalId("ext-existing-1")
                .platform("DOM_RIA")
                .title("Квартира")
                .price("18 000 грн")
                .build();

        PropertyDto updatedDto = new PropertyDto(
                "ext-existing-1",
                "DOM_RIA",
                "Квартира",
                "15 000 грн",
                "https://dom.ria.com/realty/ex1",
                "Одеса 1-к"
        );

        when(discoveredPropertyRepository.findByExternalIdAndPlatform("ext-existing-1", "DOM_RIA"))
                .thenReturn(Optional.of(existing));
        when(priceAnalyticsService.parseNumericPrice("18 000 грн")).thenReturn(new BigDecimal("18000"));
        when(priceAnalyticsService.parseNumericPrice("15 000 грн")).thenReturn(new BigDecimal("15000"));
        when(priceAnalyticsService.detectCurrency("15 000 грн")).thenReturn("UAH");
        when(priceAnalyticsService.calculatePriceChangeSummary(any(), any(), any())).thenReturn("📉 Знижка 3000 грн");

        persistenceService.persistDatabaseAnalytics(
                mockTarget,
                List.of(updatedDto),
                dto -> new PriceAnalyticsService.DealValuation(false, 0.0, 0.0, "Standard", "Text")
        );

        verify(discoveredPropertyRepository, times(1)).save(existing);
        verify(priceHistoryRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publishEvent(any(PriceChangedEvent.class));
    }
}
