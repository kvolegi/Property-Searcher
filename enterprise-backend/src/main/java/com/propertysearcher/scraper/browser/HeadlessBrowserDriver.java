package com.propertysearcher.scraper.browser;

import java.time.Duration;

/**
 * Abstraction for automated headless browser rendering (Playwright, Puppeteer, Selenium, CDP).
 */
public interface HeadlessBrowserDriver {

    record RenderedPage(String htmlContent, int httpStatus, boolean challengeBypassed) {}

    RenderedPage render(String targetUrl, ProxyNode proxy, Duration timeout);
}
