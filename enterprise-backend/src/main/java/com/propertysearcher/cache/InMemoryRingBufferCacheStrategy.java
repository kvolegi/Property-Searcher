package com.propertysearcher.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        private int head = 0;
        private int count = 0;

        public RingBuffer(int capacity) {
            this.elements = new String[capacity];
            this.lookupSet = new HashSet<>(capacity * 2);
        }

        public boolean contains(String item) {
            if (item == null) return false;
            rwLock.readLock().lock();
            try {
                return lookupSet.contains(item);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public void add(String item) {
            if (item == null || item.isBlank()) return;
            rwLock.writeLock().lock();
            try {
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
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public int size() {
            rwLock.readLock().lock();
            try {
                return count;
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }
}
