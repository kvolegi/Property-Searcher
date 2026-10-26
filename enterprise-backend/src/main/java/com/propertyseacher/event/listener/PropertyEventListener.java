package com.propertyseacher.event.listener;

import com.propertyseacher.event.PriceChangedEvent;
import com.propertyseacher.event.PropertyDiscoveredEvent;
import com.propertyseacher.telegram.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous Event Listener decoupling tracking and scraping pipeline
 * from notification delivery and downstream telemetry brokers (Kafka/RabbitMQ ready).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyEventListener {

    private final TelegramNotificationService telegramNotificationService;

    @Async
    @EventListener
    public void onPropertyDiscovered(PropertyDiscoveredEvent event) {
        log.info("Handling PropertyDiscoveredEvent asynchronously for: [{}] {}", 
                event.property().category(), event.property().title());
        telegramNotificationService.broadcastNewProperty(event.property());
    }

    @Async
    @EventListener
    public void onPriceChanged(PriceChangedEvent event) {
        log.info("Handling PriceChangedEvent asynchronously for: [{}] summary: {}", 
                event.property().title(), event.changeSummary());
        telegramNotificationService.broadcastPriceChange(event.property(), event.changeSummary());
    }
}
