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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OlxScraperStrategy implements ScraperStrategy {

    private static final String PLATFORM = "OLX";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final Pattern ID_PATTERN = Pattern.compile("ID([a-zA-Z0-9]+)\\.html");

    @Override
    public String getSupportedPlatform() {
        return PLATFORM;
    }

    @Override
    public List<PropertyDto> scrape(String targetUrl, String category) {
        List<PropertyDto> results = new ArrayList<>();
        try {
            log.info("Scraping OLX target: {} (Category: {})", targetUrl, category);
            Connection connection = Jsoup.connect(targetUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "uk,en;q=0.9")
                    .timeout(15000);

            Document doc = connection.get();
            Elements cards = doc.select("div[data-cy=\"l-card\"]");

            for (Element card : cards) {
                try {
                    // Filter out TOP / Promoted ads
                    boolean isFeatured = card.select("div[data-testid=\"adCard-featured\"]").first() != null
                            || card.text().contains("ТОП")
                            || card.text().contains("TOP");

                    Element linkTag = card.selectFirst("a[href]");
                    if (linkTag == null) continue;

                    String href = linkTag.attr("abs:href");
                    if (href.isEmpty() || href.contains("promoted")) continue;

                    Element titleTag = card.selectFirst("h4, h6");
                    String title = titleTag != null ? titleTag.text().trim() : "Без назви";

                    Element priceTag = card.selectFirst("p[data-testid=\"ad-price\"]");
                    String price = priceTag != null ? priceTag.text().trim() : "Ціна не вказана";

                    // Photo extraction
                    Elements imgs = card.select("img[src]");
                    List<String> photos = new ArrayList<>();
                    for (Element img : imgs) {
                        String src = img.attr("src");
                        if (src.startsWith("http") && !src.contains("svg")) {
                            photos.add(src);
                        }
                    }

                    // Extract external ID or fallback to hash of URL
                    String externalId = extractId(href);

                    // Skip promoted cards to ensure strict chronological latest arrival
                    if (isFeatured) {
                        log.debug("Skipping promoted TOP OLX ad: {}", title);
                        continue;
                    }

                    results.add(new PropertyDto(externalId, PLATFORM, title, price, href, category, "", photos, ""));
                } catch (Exception ex) {
                    log.warn("Failed to parse individual OLX card: {}", ex.getMessage());
                }
            }
            log.info("Scraped {} organic properties from OLX target", results.size());
        } catch (IOException e) {
            log.error("Failed to connect or parse OLX page: {}. Error: {}", targetUrl, e.getMessage());
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
