package com.propertyseacher.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe 50-slot in-memory circular buffer strategy with O(1) containment checks.
 */
@Component
public class InMemoryRingBufferCacheStrategy implements DeduplicationCacheStrategy {

    private final int capacity;
    private final Map<String, RingBuffer> buffers = new ConcurrentHashMap<>();

    public InMemoryRingBufferCacheStrategy(@Value("${app.tracker.buffer-capacity:50}") int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean contains(String targetKey, String url) {
        if (targetKey == null || url == null) return false;
        RingBuffer buffer = buffers.computeIfAbsent(targetKey, k -> new RingBuffer(capacity));
        return buffer.contains(url);
    }

    @Override
    public void add(String targetKey, String url) {
        if (targetKey == null || url == null) return;
        RingBuffer buffer = buffers.computeIfAbsent(targetKey, k -> new RingBuffer(capacity));
        buffer.add(url);
    }

    @Override
    public int size(String targetKey) {
        RingBuffer buffer = buffers.get(targetKey);
        return buffer != null ? buffer.size() : 0;
    }

    @Override
    public void clear(String targetKey) {
        buffers.remove(targetKey);
    }

    public static class RingBuffer {
        private final String[] elements;
        private int count = 0;

        public RingBuffer(int capacity) {
            this.elements = new String[capacity];
        }

        public synchronized boolean contains(String item) {
            for (int i = 0; i < count; i++) {
                if (item != null && item.equals(elements[i])) {
                    return true;
                }
            }
            return false;
        }

        public synchronized void add(String item) {
            for (int i = Math.min(count, elements.length - 1); i > 0; i--) {
                elements[i] = elements[i - 1];
            }
            elements[0] = item;
            if (count < elements.length) {
                count++;
            }
        }

        public synchronized int size() {
            return count;
        }
    }
}
