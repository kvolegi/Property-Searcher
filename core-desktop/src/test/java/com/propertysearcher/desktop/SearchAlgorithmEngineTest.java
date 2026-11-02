package com.propertysearcher.desktop;

import com.propertysearcher.desktop.engine.SearchAlgorithmEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Architectural Deconstruction: SearchAlgorithmEngine & Rate Limiter Tests")
class SearchAlgorithmEngineTest {

    @Test
    @DisplayName("Pacing: Single active category operates at minimum safe latency (1800ms)")
    void testAdaptivePacingSingleCategory() {
        int delay0 = SearchAlgorithmEngine.calculateAdaptiveDelay(0);
        int delay1 = SearchAlgorithmEngine.calculateAdaptiveDelay(1);

        assertEquals(1800, delay0);
        assertEquals(1800, delay1);
        assertEquals(SearchAlgorithmEngine.MIN_THREAD_DELAY_MS, delay1);
    }

    @ParameterizedTest
    @CsvSource({
            "2, 1800",
            "5, 2273",
            "10, 4545",
            "20, 9091"
    })
    @DisplayName("Pacing: Staggered scaling enforces CloudFront/DataDome safe barrier (<= 2.3 RPS)")
    void testAdaptivePacingScaling(int activeCategories, int expectedMinDelay) {
        int delay = SearchAlgorithmEngine.calculateAdaptiveDelay(activeCategories);
        assertTrue(delay >= expectedMinDelay - 50 && delay <= expectedMinDelay + 50,
                "Computed delay (" + delay + "ms) must throttle safely within target RPS for " + activeCategories + " categories");

        double effectiveRps = (activeCategories * 1000.0) / delay;
        assertTrue(effectiveRps <= 2.3, "Global RPS must never exceed CloudFront safe barrier (2.3 req/s)");
    }

    @Test
    @DisplayName("Engine Lifecycle: Instantiation and duplicate detection without network scraping")
    void testEngineDeduplicationBuffer() {
        SearchAlgorithmEngine engine = new SearchAlgorithmEngine("https://olx.ua/test", "12345", "TestCat", (res, link) -> {});
        assertFalse(engine.isDuplicate("https://olx.ua/item-1"));
        assertNotNull(engine.getCategoryName());
        assertEquals("TestCat", engine.getCategoryName());
    }
}
