package com.propertysearcher.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe 50-slot in-memory circular buffer strategy with true O(1) containment checks
 * and O(1) ring-pointer eviction.
 */
@Component
@ConditionalOnProperty(name = "app.cache.type", havingValue = "in-memory", matchIfMissing = true)
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
        private final Set<String> lookupSet;
        private int head = 0;
        private int count = 0;

        public RingBuffer(int capacity) {
            this.elements = new String[capacity];
            this.lookupSet = new HashSet<>(capacity * 2);
        }

        public synchronized boolean contains(String item) {
            return item != null && lookupSet.contains(item);
        }

        public synchronized void add(String item) {
            if (item == null || item.isBlank()) return;
            if (lookupSet.contains(item)) {
                return;
            }
            if (count == elements.length) {
                String oldest = elements[head];
                if (oldest != null) {
                    lookupSet.remove(oldest);
                }
            } else {
                count++;
            }
            elements[head] = item;
            lookupSet.add(item);
            head = (head + 1) % elements.length;
        }

        public synchronized int size() {
            return count;
        }
    }
}
