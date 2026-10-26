package com.propertyseacher.cache;

/**
 * Pluggable Deduplication Strategy Pattern.
 * Decouples caching implementation (In-Memory Circular Buffer vs. Distributed Redis)
 * from the tracking orchestration logic.
 */
public interface DeduplicationCacheStrategy {
    boolean contains(String targetKey, String url);
    void add(String targetKey, String url);
    int size(String targetKey);
    void clear(String targetKey);
}
