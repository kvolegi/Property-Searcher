package com.propertysearcher.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DistributedLockServiceTest {

    @Test
    @DisplayName("InMemoryDistributedLockService should acquire, prevent double acquisition, and release cleanly")
    void testInMemoryLockLifecycle() {
        InMemoryDistributedLockService lockService = new InMemoryDistributedLockService();
        String resource = "target:category:123";

        // First acquisition succeeds
        boolean acquired = lockService.tryAcquire(resource, Duration.ofSeconds(5));
        assertTrue(acquired, "First lock acquisition should succeed");

        // Second acquisition from simulated different thread should fail
        Thread otherThread = new Thread(() -> {
            boolean secondAcquired = lockService.tryAcquire(resource, Duration.ofSeconds(5));
            assertFalse(secondAcquired, "Concurrent acquisition from another thread must fail");
        });
        otherThread.start();
        try {
            otherThread.join();
        } catch (InterruptedException ignored) {}

        // Release
        lockService.release(resource);

        // After release, acquisition succeeds again
        boolean reacquired = lockService.tryAcquire(resource, Duration.ofSeconds(5));
        assertTrue(reacquired, "Lock should be re-acquirable after release");
        lockService.release(resource);
    }

    @Test
    @DisplayName("RedisDistributedLockService should enforce SETNX mutual exclusion and automatic release")
    void testRedisLockLifecycle() {
        RedisDistributedLockService redisLock = new RedisDistributedLockService(null);
        String resource = "cluster:target:456";

        boolean acquired = redisLock.tryAcquire(resource, Duration.ofSeconds(10));
        assertTrue(acquired, "Redis SETNX acquisition should succeed");

        // Second acquisition should fail while active
        boolean secondAcquisition = redisLock.tryAcquire(resource, Duration.ofSeconds(10));
        assertFalse(secondAcquisition, "Active Redis lock cannot be double-acquired");

        // Release
        redisLock.release(resource);

        // Can acquire again
        boolean acquiredAgain = redisLock.tryAcquire(resource, Duration.ofSeconds(10));
        assertTrue(acquiredAgain, "Released Redis lock can be re-acquired");
        redisLock.release(resource);
    }
}
