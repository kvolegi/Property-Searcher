package com.propertyseacher.event;

import com.propertyseacher.scraper.PropertyDto;
import java.math.BigDecimal;

/**
 * Domain event emitted when a tracked listing experiences a price mutation (drop or hike).
 */
public record PriceChangedEvent(
        PropertyDto property,
        String changeSummary,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        long timestamp
) {
    public PriceChangedEvent(PropertyDto property, String changeSummary, BigDecimal oldPrice, BigDecimal newPrice) {
        this(property, changeSummary, oldPrice, newPrice, System.currentTimeMillis());
    }
}
