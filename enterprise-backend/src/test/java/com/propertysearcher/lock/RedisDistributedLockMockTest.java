package com.propertysearcher.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockMockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("Should acquire lock via Redis SET NX PX and release safely using atomic Lua script")
    void testRedisLockAcquireAndLuaRelease() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("lock:test"), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        RedisDistributedLockService lockService = new RedisDistributedLockService(redisTemplate);

        boolean acquired = lockService.tryAcquire("lock:test", Duration.ofSeconds(30));
        assertTrue(acquired, "Lock acquisition must succeed via Redis SETNX");

        lockService.release("lock:test");
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock:test")), anyString());
    }
}
