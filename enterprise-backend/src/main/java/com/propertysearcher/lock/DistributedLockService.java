package com.propertysearcher.lock;

import java.time.Duration;

/**
 * Cloud-native distributed lock abstraction for coordinating concurrency
 * across multi-pod Kubernetes deployments or horizontally scaled instances.
 */
public interface DistributedLockService {

    /**
     * Attempts to acquire an exclusive lock for the given key with an automatic lease timeout.
     *
     * @param lockKey unique resource identifier
     * @param leaseDuration maximum duration to hold the lock before automatic eviction
     * @return true if the lock was successfully acquired, false otherwise
     */
    boolean tryAcquire(String lockKey, Duration leaseDuration);

    /**
     * Releases the acquired lock.
     *
     * @param lockKey unique resource identifier
     */
    void release(String lockKey);
}
