package com.propertyseacher.scraper.strategy;

import com.propertyseacher.scraper.PropertyDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DomRiaScraperStrategy implements ScraperStrategy {

    private static final String PLATFORM = "DOM_RIA";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final Pattern ID_PATTERN = Pattern.compile("-(\\d+)\\.html");

    @Override
    public String getSupportedPlatform() {
        return PLATFORM;
    }

    @Override
    public List<PropertyDto> scrape(String targetUrl, String category) {
        List<PropertyDto> results = new ArrayList<>();
        try {
            log.info("Scraping DOM.RIA target: {} (Category: {})", targetUrl, category);
            Connection connection = Jsoup.connect(targetUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "uk,en;q=0.9")
                    .timeout(12000);

            Document doc = connection.get();
            Elements cards = doc.select("section.realty-item, div.realty-item");

            for (Element card : cards) {
                try {
                    // Extract link
                    Element linkEl = card.selectFirst("a[href*=\"realty\"]");
                    if (linkEl == null) continue;

                    String href = linkEl.attr("href");
                    if (!href.startsWith("http")) {
                        href = "https://dom.ria.com" + href;
                    }

                    // Extract Title & Location
                    String title = linkEl.hasAttr("title") ? linkEl.attr("title").trim() : linkEl.text().trim();
                    if (title.isEmpty()) {
                        title = "Оголошення DOM.RIA";
                    }

                    // Extract Price
                    Element priceEl = card.selectFirst("b[data-tm=\"realty-full-price\"], b.size22, span.price");
                    String price = priceEl != null ? priceEl.text().trim() : "Ціна не вказана";

                    // Extract Photos
                    Elements imgTags = card.select("img[src], img[data-src]");
                    List<String> photos = new ArrayList<>();
                    for (Element img : imgTags) {
                        String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
                        if (src != null && src.startsWith("http") && !src.contains("svg") && !src.contains("icon")) {
                            photos.add(src);
                        }
                    }

                    String externalId = extractId(href);

                    results.add(new PropertyDto(
                            externalId,
                            PLATFORM,
                            title,
                            price,
                            href,
                            category,
                            "", // Details description can be populated on ad view
                            photos,
                            title
                    ));
                } catch (Exception ex) {
                    log.warn("Failed to parse DOM.RIA card: {}", ex.getMessage());
                }
            }
            log.info("Scraped {} properties from DOM.RIA", results.size());
        } catch (IOException e) {
            log.error("Failed to scrape DOM.RIA page {}: {}", targetUrl, e.getMessage());
        }
        return results;
    }

    private String extractId(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return String.valueOf(url.hashCode());
    }
}
