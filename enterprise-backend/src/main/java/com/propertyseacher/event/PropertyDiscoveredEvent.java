package com.propertyseacher.event;

import com.propertyseacher.scraper.PropertyDto;

/**
 * Domain event emitted when a new property listing is intercepted from CDN edge nodes.
 * Implements Event-Driven Architecture (EDA) pattern for asynchronous processing.
 */
public record PropertyDiscoveredEvent(
        PropertyDto property,
        long timestamp
) {
    public PropertyDiscoveredEvent(PropertyDto property) {
        this(property, System.currentTimeMillis());
    }
}
