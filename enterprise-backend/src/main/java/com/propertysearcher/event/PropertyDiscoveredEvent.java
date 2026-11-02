package com.propertysearcher.event;

import com.propertysearcher.analytics.PriceAnalyticsService.DealValuation;
import com.propertysearcher.scraper.PropertyDto;

/**
 * Domain event emitted when a new property listing is intercepted from CDN edge nodes.
 * Implements Event-Driven Architecture (EDA) pattern for asynchronous processing.
 */
public record PropertyDiscoveredEvent(
        PropertyDto property,
        DealValuation valuation,
        long timestamp
) {
    public PropertyDiscoveredEvent(PropertyDto property) {
        this(property, null, System.currentTimeMillis());
    }

    public PropertyDiscoveredEvent(PropertyDto property, DealValuation valuation) {
        this(property, valuation, System.currentTimeMillis());
    }
}
