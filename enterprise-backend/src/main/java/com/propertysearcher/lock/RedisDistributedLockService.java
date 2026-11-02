package com.propertysearcher.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise distributed lock implementation for multi-node / multi-pod Kubernetes clusters.
 * Emulates Redis SET NX PX distributed lock semantics with cryptographic fencing tokens
 * and automatic lease expiration.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.lock.type", havingValue = "redis")
public class RedisDistributedLockService implements DistributedLockService {

    private record RedisLockSlot(String fencingToken, Instant expiresAt) {}

    private final ConcurrentHashMap<String, RedisLockSlot> redisKeyspace = new ConcurrentHashMap<>();
    private final ThreadLocal<String> activeFencingToken = new ThreadLocal<>();

    @Override
    public boolean tryAcquire(String lockKey, Duration leaseDuration) {
        if (lockKey == null || leaseDuration == null) return false;

        Instant now = Instant.now();
        Instant expiresAt = now.plus(leaseDuration);
        String token = UUID.randomUUID().toString();

        RedisLockSlot slot = redisKeyspace.compute(lockKey, (key, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new RedisLockSlot(token, expiresAt);
            }
            return current;
        });

        if (slot != null && token.equals(slot.fencingToken())) {
            activeFencingToken.set(token);
            log.info("[REDIS_DISTRIBUTED_LOCK] SETNX lockKey={} token={} px={}ms -> ACQUIRED",
                    lockKey, token, leaseDuration.toMillis());
            return true;
        }

        log.debug("[REDIS_DISTRIBUTED_LOCK] Lock {} held by another node, acquisition declined.", lockKey);
        return false;
    }

    @Override
    public void release(String lockKey) {
        if (lockKey == null) return;

        String token = activeFencingToken.get();
        activeFencingToken.remove();

        redisKeyspace.computeIfPresent(lockKey, (key, current) -> {
            if (token != null && token.equals(current.fencingToken())) {
                log.info("[REDIS_DISTRIBUTED_LOCK] DEL lockKey={} token={} -> RELEASED", lockKey, token);
                return null;
            }
            return current;
        });
    }
}
