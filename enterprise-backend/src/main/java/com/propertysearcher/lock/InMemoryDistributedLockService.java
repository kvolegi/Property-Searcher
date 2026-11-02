package com.propertysearcher.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node in-memory implementation of {@link DistributedLockService}.
 * Suitable for standalone development, test environments, and non-clustered profiles.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.lock.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryDistributedLockService implements DistributedLockService {

    private record LockHolder(String ownerId, Instant expiresAt) {}

    private final ConcurrentHashMap<String, LockHolder> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String lockKey, Duration leaseDuration) {
        if (lockKey == null || leaseDuration == null) return false;

        Instant now = Instant.now();
        Instant deadline = now.plus(leaseDuration);
        String currentOwner = Thread.currentThread().getName() + "@" + System.identityHashCode(Thread.currentThread());

        LockHolder result = locks.compute(lockKey, (key, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new LockHolder(currentOwner, deadline);
            }
            return current;
        });

        boolean acquired = result != null && currentOwner.equals(result.ownerId());
        if (acquired) {
            log.debug("[IN_MEMORY_LOCK] Acquired lock: {} (Lease: {} ms)", lockKey, leaseDuration.toMillis());
        }
        return acquired;
    }

    @Override
    public void release(String lockKey) {
        if (lockKey == null) return;
        locks.remove(lockKey);
        log.debug("[IN_MEMORY_LOCK] Released lock: {}", lockKey);
    }
}
