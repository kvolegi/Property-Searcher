package com.propertysearcher.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Production-Grade Redis Deduplication Cache Strategy.
 * Backed by native Redis Set (SADD, SISMEMBER) data structures with O(1) containment
 * and cluster-wide key TTL expiration for memory hygiene.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class RedisDeduplicationCacheStrategy implements DeduplicationCacheStrategy {

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    // Resilient local fallback keyspace for test isolation if Redis is offline
    private final Map<String, ConcurrentHashMap.KeySetView<String, Boolean>> localFallbackSets = new ConcurrentHashMap<>();

    public RedisDeduplicationCacheStrategy(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Value("${app.cache.redis.ttl-hours:48}") int ttlHours
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Override
    public boolean contains(String targetKey, String url) {
        if (targetKey == null || url == null) return false;
        String redisKey = "dedup:" + targetKey;

        if (redisTemplate != null) {
            try {
                Boolean isMember = redisTemplate.opsForSet().isMember(redisKey, url);
                return Boolean.TRUE.equals(isMember);
            } catch (Exception ex) {
                log.warn("[REDIS_CACHE] SISMEMBER failed: {}. Falling back to node cache.", ex.getMessage());
            }
        }

        var set = localFallbackSets.computeIfAbsent(targetKey, k -> ConcurrentHashMap.newKeySet());
        return set.contains(url);
    }

    @Override
    public void add(String targetKey, String url) {
        if (targetKey == null || url == null) return;
        String redisKey = "dedup:" + targetKey;

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForSet().add(redisKey, url);
                redisTemplate.expire(redisKey, ttl);
                log.debug("[REDIS_CLUSTER_CACHE] SADD key={} value={}", redisKey, url);
                return;
            } catch (Exception ex) {
                log.warn("[REDIS_CACHE] SADD failed: {}. Falling back to node cache.", ex.getMessage());
            }
        }

        var set = localFallbackSets.computeIfAbsent(targetKey, k -> ConcurrentHashMap.newKeySet());
        set.add(url);
    }

    @Override
    public int size(String targetKey) {
        if (targetKey == null) return 0;
        String redisKey = "dedup:" + targetKey;

        if (redisTemplate != null) {
            try {
                Long size = redisTemplate.opsForSet().size(redisKey);
                return size != null ? size.intValue() : 0;
            } catch (Exception ex) {
                log.warn("[REDIS_CACHE] SCARD failed: {}", ex.getMessage());
            }
        }

        var set = localFallbackSets.get(targetKey);
        return set != null ? set.size() : 0;
    }

    @Override
    public void clear(String targetKey) {
        if (targetKey == null) return;
        String redisKey = "dedup:" + targetKey;

        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey);
                log.debug("[REDIS_CLUSTER_CACHE] DEL key={}", redisKey);
            } catch (Exception ex) {
                log.warn("[REDIS_CACHE] DEL failed: {}", ex.getMessage());
            }
        }

        localFallbackSets.remove(targetKey);
    }
}
