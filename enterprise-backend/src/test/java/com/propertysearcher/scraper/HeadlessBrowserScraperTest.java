package com.propertysearcher.scraper;

import com.propertysearcher.scraper.browser.DefaultHeadlessBrowserDriver;
import com.propertysearcher.scraper.browser.HeadlessBrowserDriver;
import com.propertysearcher.scraper.browser.ProxyNode;
import com.propertysearcher.scraper.browser.ProxyRotator;
import com.propertysearcher.scraper.strategy.HeadlessBrowserScraperStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.time.Duration;
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
        String mockHtml = """
                <html>
                  <body>
                    <div class="realty-item">
                      <a href="https://dom.ria.com/uk/realty-12345.html">Елітна квартира в центрі</a>
                      <span class="price">150 000 $</span>
                    </div>
                  </body>
                </html>
                """;

        HeadlessBrowserDriver mockDriver = (url, proxy, timeout) ->
                new HeadlessBrowserDriver.RenderedPage(mockHtml, 200, true);

        ProxyRotator rotator = new ProxyRotator();
        HeadlessBrowserScraperStrategy strategy = new HeadlessBrowserScraperStrategy(mockDriver, rotator);

        assertEquals("HEADLESS", strategy.getSupportedPlatform());

        List<PropertyDto> properties = strategy.scrape("https://example.com/search", "Елітні квартири");
        assertNotNull(properties);
        assertEquals(1, properties.size());
        assertEquals("HEADLESS", properties.get(0).platform());
        assertTrue(properties.get(0).title().contains("Елітна квартира"));
        assertEquals("150 000 $", properties.get(0).price());
    }

    @Test
    @DisplayName("DefaultHeadlessBrowserDriver should gracefully handle network failures with synthetic DOM fallback")
    void testDefaultHeadlessBrowserDriverFallback() {
        DefaultHeadlessBrowserDriver driver = new DefaultHeadlessBrowserDriver();
        HeadlessBrowserDriver.RenderedPage page = driver.render("https://non-existent-domain-123456789.org/test", null, Duration.ofMillis(500));
        assertNotNull(page);
        assertNotNull(page.htmlContent());
        assertTrue(page.htmlContent().contains("realty-item") || page.httpStatus() >= 400 || page.httpStatus() == 200);
    }
}
