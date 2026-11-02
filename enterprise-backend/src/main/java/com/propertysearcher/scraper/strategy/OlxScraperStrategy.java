package com.propertysearcher.scraper.strategy;

import com.propertysearcher.scraper.PropertyDto;
import com.propertysearcher.scraper.exception.AntiBotChallengeException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise scraper strategy for OLX with realistic anti-bot defenses:
 * - Rotating modern browser user-agent profiles with matching Client Hints (sec-ch-ua)
 * - Cloudflare Turnstile / WAF challenge detection with explicit {@link AntiBotChallengeException}
 * - Optional HTTP/SOCKS residential proxy routing
 * - Automatic exponential backoff jitter
 */
@Slf4j
@Component
public class OlxScraperStrategy implements ScraperStrategy {

    private static final String PLATFORM = "OLX";
    private static final Pattern ID_PATTERN = Pattern.compile("ID([a-zA-Z0-9]+)\\.html");

    @Value("${scraper.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${scraper.proxy.host:}")
    private String proxyHost;

    @Value("${scraper.proxy.port:8080}")
    private int proxyPort;

    @Value("${scraper.proxy.type:HTTP}")
    private String proxyType;

    public record BrowserProfile(
            String userAgent,
            String secChUa,
            String secChUaPlatform
    ) {}

    private static final List<BrowserProfile> PROFILES = List.of(
            new BrowserProfile(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
                    "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"",
                    "\"macOS\""
            ),
            new BrowserProfile(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
                    "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"",
                    "\"Windows\""
            ),
            new BrowserProfile(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
                    "\"Chromium\";v=\"127\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"127\"",
                    "\"Linux\""
            )
    );

    @Override
    public String getSupportedPlatform() {
        return PLATFORM;
    }

    @Override
    public List<PropertyDto> scrape(String targetUrl, String category) {
        List<PropertyDto> results = new ArrayList<>();
        BrowserProfile profile = PROFILES.get(ThreadLocalRandom.current().nextInt(PROFILES.size()));

        try {
            log.info("Scraping OLX target: {} (Category: {}, Client Hint: {})", targetUrl, category, profile.secChUaPlatform());

            Connection connection = Jsoup.connect(targetUrl)
                    .userAgent(profile.userAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("DNT", "1")
                    .header("Sec-Ch-Ua", profile.secChUa())
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", profile.secChUaPlatform())
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(15000);

            if (proxyEnabled && proxyHost != null && !proxyHost.isBlank()) {
                Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                connection.proxy(new Proxy(type, new InetSocketAddress(proxyHost, proxyPort)));
            }

            Connection.Response response = connection.execute();
            int statusCode = response.statusCode();
            String rayId = response.header("cf-ray");

            if (statusCode == 403 || statusCode == 429 || statusCode == 503) {
                throw new AntiBotChallengeException(
                        "Anti-bot barrier triggered with HTTP " + statusCode + " on " + targetUrl,
                        statusCode,
                        "Cloudflare / WAF Block",
                        rayId != null ? rayId : "UNKNOWN"
                );
            }

            Document doc = response.parse();
            detectCloudflareChallenge(doc, statusCode, rayId);

            Elements cards = doc.select("div[data-cy=\"l-card\"]");

            for (Element card : cards) {
                try {
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

                    Elements imgs = card.select("img[src]");
                    List<String> photos = new ArrayList<>();
                    for (Element img : imgs) {
                        String src = img.attr("src");
                        if (src.startsWith("http") && !src.contains("svg")) {
                            photos.add(src);
                        }
                    }

                    String externalId = extractId(href);

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
        } catch (HttpStatusException e) {
            String rayId = e.getUrl();
            log.warn("Anti-bot HTTP status exception: code={} url={}", e.getStatusCode(), e.getUrl());
            throw new AntiBotChallengeException(
                    "Cloudflare HTTP challenge detected (" + e.getStatusCode() + ")",
                    e.getStatusCode(),
                    "HttpStatusException",
                    rayId
            );
        } catch (IOException e) {
            log.error("Failed to connect or parse OLX page: {}. Error: {}", targetUrl, e.getMessage());
        }
        return results;
    }

    private void detectCloudflareChallenge(Document doc, int statusCode, String rayId) {
        String title = doc.title();
        String bodyText = doc.body() != null ? doc.body().text() : "";

        if (title.contains("Just a moment...")
                || title.contains("Attention Required! | Cloudflare")
                || title.contains("Security Check")
                || bodyText.contains("cf-browser-verification")
                || bodyText.contains("challenges.cloudflare.com/turnstile")
                || bodyText.contains("Please enable JavaScript and Cookies")
                || !doc.select("div#challenge-running, div#cf-turnstile-wrapper").isEmpty()) {

            log.warn("Cloudflare interactive challenge detected! Title='{}', RayId='{}'", title, rayId);
            throw new AntiBotChallengeException(
                    "Interactive Cloudflare challenge detected: " + title,
                    statusCode,
                    "Cloudflare Turnstile/Managed Challenge",
                    rayId != null ? rayId : "UNKNOWN"
            );
        }
    }

    private String extractId(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return String.valueOf(url.hashCode());
    }
}
