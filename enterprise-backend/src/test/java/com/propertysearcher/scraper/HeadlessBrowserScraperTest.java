package com.propertysearcher.scraper;

import com.propertysearcher.scraper.browser.DefaultHeadlessBrowserDriver;
import com.propertysearcher.scraper.browser.ProxyNode;
import com.propertysearcher.scraper.browser.ProxyRotator;
import com.propertysearcher.scraper.strategy.HeadlessBrowserScraperStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeadlessBrowserScraperTest {

    @Test
    @DisplayName("ProxyRotator should rotate through registered proxy nodes in round-robin sequence")
    void testProxyRotatorRoundRobin() {
        ProxyRotator rotator = new ProxyRotator();
        rotator.registerProxy(new ProxyNode("10.0.0.1", 3128, Proxy.Type.HTTP, null, null));
        rotator.registerProxy(new ProxyNode("10.0.0.2", 3128, Proxy.Type.HTTP, null, null));

        assertTrue(rotator.getPoolSize() >= 2);

        ProxyNode first = rotator.getNextProxy();
        ProxyNode second = rotator.getNextProxy();
        assertNotNull(first);
        assertNotNull(second);
    }

    @Test
    @DisplayName("HeadlessBrowserScraperStrategy should extract listings from rendered DOM")
    void testHeadlessBrowserScraping() {
        DefaultHeadlessBrowserDriver driver = new DefaultHeadlessBrowserDriver();
        ProxyRotator rotator = new ProxyRotator();
        HeadlessBrowserScraperStrategy strategy = new HeadlessBrowserScraperStrategy(driver, rotator);

        assertEquals("HEADLESS", strategy.getSupportedPlatform());

        List<PropertyDto> properties = strategy.scrape("https://dom.ria.com/uk/search", "Елітні квартири");
        assertNotNull(properties);
        assertFalse(properties.isEmpty());
        assertEquals("HEADLESS", properties.get(0).platform());
        assertTrue(properties.get(0).title().contains("Елітна квартира"));
    }
}
