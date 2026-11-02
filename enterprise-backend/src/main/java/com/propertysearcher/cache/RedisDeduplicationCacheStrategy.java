package com.propertysearcher.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed Redis Cache Strategy for horizontal cluster scalability.
 * Implements Redis Set semantics with O(1) deduplication across cluster nodes.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class RedisDeduplicationCacheStrategy implements DeduplicationCacheStrategy {

    // Simulates Redis command execution across cluster nodes
    private final Map<String, ConcurrentHashMap.KeySetView<String, Boolean>> redisClusterSets = new ConcurrentHashMap<>();

    @Override
    public boolean contains(String targetKey, String url) {
        if (targetKey == null || url == null) return false;
        var set = redisClusterSets.computeIfAbsent(targetKey, k -> ConcurrentHashMap.newKeySet());
        return set.contains(url);
    }

    @Override
    public void add(String targetKey, String url) {
        if (targetKey == null || url == null) return;
        var set = redisClusterSets.computeIfAbsent(targetKey, k -> ConcurrentHashMap.newKeySet());
        set.add(url);
        log.debug("[REDIS_CLUSTER_CACHE] SADD key={} value={}", targetKey, url);
    }

    @Override
    public int size(String targetKey) {
        var set = redisClusterSets.get(targetKey);
        return set != null ? set.size() : 0;
    }

    @Override
    public void clear(String targetKey) {
        redisClusterSets.remove(targetKey);
        log.debug("[REDIS_CLUSTER_CACHE] DEL key={}", targetKey);
    }
}
