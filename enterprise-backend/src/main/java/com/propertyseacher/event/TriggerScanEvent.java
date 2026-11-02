package com.propertyseacher.event;

/**
 * Event triggered when an operator or user requests an immediate on-demand scan.
 * Decouples Telegram presentation bot from tracking orchestration.
 */
public record TriggerScanEvent(
        Long requestedByChatId,
        long timestamp
) {
    public TriggerScanEvent(Long requestedByChatId) {
        this(requestedByChatId, System.currentTimeMillis());
    }
}
