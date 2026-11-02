package com.propertysearcher.scraper.strategy;

import com.propertysearcher.scraper.PropertyDto;
import com.propertysearcher.scraper.browser.HeadlessBrowserDriver;
import com.propertysearcher.scraper.browser.ProxyNode;
import com.propertysearcher.scraper.browser.ProxyRotator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Headless Browser Strategy for handling complex Single Page Applications (SPA),
 * dynamic JavaScript rendering, and Cloudflare/WAF anti-bot challenges via automated proxy rotation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeadlessBrowserScraperStrategy implements ScraperStrategy {

    public static final String PLATFORM = "HEADLESS";

    private final HeadlessBrowserDriver browserDriver;
    private final ProxyRotator proxyRotator;

    @Override
    public String getSupportedPlatform() {
        return PLATFORM;
    }

    @Override
    public List<PropertyDto> scrape(String targetUrl, String category) {
        ProxyNode proxy = proxyRotator.getNextProxy();
        log.info("[HEADLESS_SCRAPER] Executing headless browser render on {} with proxy rotation ({}:{})",
                targetUrl, proxy != null ? proxy.host() : "direct", proxy != null ? proxy.port() : 0);

        HeadlessBrowserDriver.RenderedPage page = browserDriver.render(targetUrl, proxy, Duration.ofSeconds(15));
        if (page == null || page.htmlContent() == null || page.htmlContent().isBlank()) {
            return List.of();
        }

        Document doc = Jsoup.parse(page.htmlContent(), targetUrl);
        List<PropertyDto> items = new ArrayList<>();

        Elements cards = doc.select(".realty-item, [data-cy='l-card']");
        for (Element card : cards) {
            Element link = card.selectFirst("a[href]");
            if (link == null) continue;
            String href = link.attr("abs:href");
            if (href.isBlank()) href = link.attr("href");
            String title = link.text().trim();
            if (title.isEmpty()) title = "Нерухомість (Headless SPA)";

            Element priceEl = card.selectFirst(".price, [data-testid='ad-price']");
            String price = priceEl != null ? priceEl.text().trim() : "Договірна";

            items.add(new PropertyDto(
                    "headless-" + Math.abs(href.hashCode()),
                    PLATFORM,
                    title,
                    price,
                    href,
                    category,
                    "Dynamic SPA / Anti-Bot Rendered Listing",
                    List.of(),
                    "Київ"
            ));
        }

        log.info("[HEADLESS_SCRAPER] Successfully rendered and extracted {} listings from {}", items.size(), targetUrl);
        return items;
    }
}
