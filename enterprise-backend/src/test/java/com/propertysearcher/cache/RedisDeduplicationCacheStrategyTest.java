package com.propertysearcher.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDeduplicationCacheStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Test
    @DisplayName("Should use native Redis SISMEMBER and SADD operations when RedisTemplate is present")
    void testRedisDeduplicationNativeCalls() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("dedup:search_kiev", "https://dom.ria.com/realty/123")).thenReturn(false);
        when(setOperations.size("dedup:search_kiev")).thenReturn(1L);

        RedisDeduplicationCacheStrategy strategy = new RedisDeduplicationCacheStrategy(redisTemplate, 48);

        // Contains check
        boolean contains = strategy.contains("search_kiev", "https://dom.ria.com/realty/123");
        assertFalse(contains);
        verify(setOperations).isMember("dedup:search_kiev", "https://dom.ria.com/realty/123");

        // Add
        strategy.add("search_kiev", "https://dom.ria.com/realty/123");
        verify(setOperations).add("dedup:search_kiev", "https://dom.ria.com/realty/123");
        verify(redisTemplate).expire(eq("dedup:search_kiev"), any(Duration.class));

        // Size
        assertEquals(1, strategy.size("search_kiev"));

        // Clear
        strategy.clear("search_kiev");
        verify(redisTemplate).delete("dedup:search_kiev");
    }

    @Test
    @DisplayName("Should fall back to node memory cache safely when Redis is offline or null")
    void testRedisFallbackWhenTemplateNull() {
        RedisDeduplicationCacheStrategy strategy = new RedisDeduplicationCacheStrategy(null, 48);

        assertFalse(strategy.contains("target1", "https://olx.ua/ad/1"));
        strategy.add("target1", "https://olx.ua/ad/1");
        assertTrue(strategy.contains("target1", "https://olx.ua/ad/1"));
        assertEquals(1, strategy.size("target1"));

        strategy.clear("target1");
        assertEquals(0, strategy.size("target1"));
    }
}
