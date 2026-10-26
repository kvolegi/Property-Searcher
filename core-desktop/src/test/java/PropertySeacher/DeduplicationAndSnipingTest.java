package PropertySeacher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Deduplication, Adaptive Sniping & URL Processing Tests")
class DeduplicationAndSnipingTest {

    @Test
    @DisplayName("Adaptive pacing: Single active category should use minimum baseline latency")
    void testAdaptiveDelaySingleCategory() {
        int delay0 = BotTG.calculateAdaptiveDelay(0);
        int delay1 = BotTG.calculateAdaptiveDelay(1);

        assertEquals(BotTG.MIN_THREAD_DELAY_MS, delay0);
        assertEquals(BotTG.MIN_THREAD_DELAY_MS, delay1);
        assertEquals(1800, delay1, "Minimum baseline sniping interval should be 1800ms");
    }

    @ParameterizedTest
    @CsvSource({
            "2, 1800",
            "5, 2273",
            "10, 4545",
            "20, 9091"
    })
    @DisplayName("Adaptive pacing: Staggered scaling across multiple categories to prevent CloudFront IP rate-limits")
    void testAdaptiveDelayScaling(int activeCategories, int expectedMinDelay) {
        int delay = BotTG.calculateAdaptiveDelay(activeCategories);
        assertTrue(delay >= expectedMinDelay - 50 && delay <= expectedMinDelay + 50,
                "Computed delay (" + delay + "ms) must throttle safely within target RPS for " + activeCategories + " categories");

        // Verify effective global request rate stays at or below safe 2.3 requests/second
        double effectiveRps = (activeCategories * 1000.0) / delay;
        assertTrue(effectiveRps <= 2.3, "Global RPS must never exceed CloudFront safe barrier (2.3 req/s)");
    }

    @Test
    @DisplayName("Sliding window buffer: Oldest entries should drop when capacity (50) is exceeded")
    void testCircularDeduplicationBuffer() {
        int capacity = 50;
        String[] buffer = new String[capacity];

        // Fill buffer with 50 unique items
        for (int i = 0; i < capacity; i++) {
            updateBuffer(buffer, "https://www.olx.ua/ad/item-" + i);
        }

        // Verify newest is at head and oldest is at tail
        assertEquals("https://www.olx.ua/ad/item-49", buffer[0]);
        assertEquals("https://www.olx.ua/ad/item-0", buffer[capacity - 1]);

        // Push 51st item -> item-0 should drop out
        updateBuffer(buffer, "https://www.olx.ua/ad/item-50");
        assertEquals("https://www.olx.ua/ad/item-50", buffer[0]);
        assertEquals("https://www.olx.ua/ad/item-1", buffer[capacity - 1]);

        // item-0 is no longer duplicate
        assertFalse(isDuplicate(buffer, "https://www.olx.ua/ad/item-0"));
        // item-50 and item-1 are recognized duplicates
        assertTrue(isDuplicate(buffer, "https://www.olx.ua/ad/item-50"));
        assertTrue(isDuplicate(buffer, "https://www.olx.ua/ad/item-1"));
    }

    private static void updateBuffer(String[] buffer, String item) {
        for (int i = buffer.length - 1; i > 0; i--) {
            buffer[i] = buffer[i - 1];
        }
        buffer[0] = item;
    }

    private static boolean isDuplicate(String[] buffer, String item) {
        for (String s : buffer) {
            if (item != null && item.equals(s)) return true;
        }
        return false;
    }
}
