package PropertySeacher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserOLX {

    private String url;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    public static String toKyivTime(String timeHhMm) {
        if (timeHhMm == null || timeHhMm.isBlank()) {
            return LocalTime.now(KYIV_ZONE).format(TIME_FMT);
        }
        try {
            Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(timeHhMm);
            if (m.find()) {
                LocalTime parsed = LocalTime.parse(m.group(1), TIME_FMT);
                int offsetHours = KYIV_ZONE.getRules().getOffset(java.time.Instant.now()).getTotalSeconds() / 3600;
                return parsed.plusHours(offsetHours).format(TIME_FMT);
            }
        } catch (Exception ignored) {}
        return timeHhMm;
    }

    public ParserOLX(String url) {
        this.url = url;
    }

    public static class PropertyResult {
        public String title;
        public String price;
        public String link;
        public String photoUrl;
        public List<String> photoUrls;
        public String platform;
        public String timePublished;

        public PropertyResult(String title, String price, String link, List<String> photoUrls, String platform, String timePublished) {
            this.title = title;
            this.price = price;
            this.link = (link != null) ? link.split("\\?")[0] : null;
            this.photoUrls = (photoUrls != null) ? photoUrls : new ArrayList<>();
            this.photoUrl = (!this.photoUrls.isEmpty()) ? this.photoUrls.get(0) : null;
            this.platform = platform;
            this.timePublished = (timePublished != null && !timePublished.isBlank()) 
                    ? timePublished 
                    : LocalTime.now(KYIV_ZONE).format(TIME_FMT);
        }

        public PropertyResult(String title, String price, String link, String photoUrl, String platform, String timePublished) {
            this.title = title;
            this.price = price;
            this.link = (link != null) ? link.split("\\?")[0] : null;
            this.photoUrl = photoUrl;
            this.photoUrls = new ArrayList<>();
            if (photoUrl != null && !photoUrl.isBlank()) {
                this.photoUrls.add(photoUrl);
            }
            this.platform = platform;
            this.timePublished = (timePublished != null && !timePublished.isBlank()) 
                    ? timePublished 
                    : LocalTime.now(KYIV_ZONE).format(TIME_FMT);
        }

        public PropertyResult(String title, String price, String link, String photoUrl, String platform) {
            this(title, price, link, photoUrl, platform, LocalTime.now(KYIV_ZONE).format(TIME_FMT));
        }
    }

    public List<PropertyResult> getTopProperties(int maxCount) {
        if (url == null || url.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (url.contains("dom.ria.com")) {
            return parseDomRiaList(maxCount);
        } else {
            return parseOlxList(maxCount);
        }
    }

    public PropertyResult getLatestProperty() {
        List<PropertyResult> list = getTopProperties(1);
        return (!list.isEmpty()) ? list.get(0) : null;
    }

    public String getOLX() {
        PropertyResult res = getLatestProperty();
        return res != null ? res.link : null;
    }

    private List<PropertyResult> parseDomRiaList(int maxCount) {
        List<PropertyResult> results = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "uk,en;q=0.9")
                    .timeout(15000)
                    .get();

            Elements cards = doc.select("section.realty-item, div.realty-item");
            String todayIso = LocalDate.now(KYIV_ZONE).toString();

            for (Element card : cards) {
                Element linkEl = card.selectFirst("a[href*=\"realty\"]");
                if (linkEl == null) continue;

                Element timeEl = card.selectFirst("time");
                String dt = (timeEl != null) ? timeEl.attr("datetime").trim() : "";
                String timeText = (timeEl != null) ? timeEl.text().trim() : "";

                // Strict filter: only accept listings published TODAY!
                boolean isToday = timeText.contains("Сьогодні") || dt.startsWith(todayIso);
                if (!isToday) {
                    continue;
                }

                String href = linkEl.attr("href");
                if (!href.startsWith("http")) {
                    href = "https://dom.ria.com" + href;
                }
                href = href.split("\\?")[0];

                String title = linkEl.hasAttr("title") ? linkEl.attr("title").trim() : linkEl.text().trim();
                Element priceEl = card.selectFirst("b[data-tm=\"realty-full-price\"], b.size22, span.price");
                String price = priceEl != null ? priceEl.text().trim() : "Ціна не вказана";

                List<String> photos = new ArrayList<>();
                Elements imgEls = card.select("img");
                for (Element imgEl : imgEls) {
                    String p = imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src");
                    if (p != null && p.startsWith("http") && !photos.contains(p)) {
                        photos.add(p);
                    }
                }

                String timeStr = "";
                Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(dt.isEmpty() ? timeText : dt);
                if (m.find()) {
                    timeStr = m.group(1);
                } else {
                    timeStr = LocalTime.now(KYIV_ZONE).format(TIME_FMT);
                }

                results.add(new PropertyResult(title, price, href, photos, "DOM.RIA", timeStr));
                if (results.size() >= maxCount) break;
            }
        } catch (IOException e) {
            System.out.println("Помилка підключення до DOM.RIA: " + e.getMessage());
        }
        return results;
    }

    private List<PropertyResult> parseOlxList(int maxCount) {
        List<PropertyResult> results = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "uk,en;q=0.9")
                    .timeout(15000)
                    .get();

            Elements cards = doc.select("div[data-cy=\"l-card\"]");
            for (Element card : cards) {
                boolean isFeatured = card.select("div[data-testid=\"adCard-featured\"]").first() != null
                        || card.text().contains("ТОП")
                        || card.text().contains("TOP");

                // Skip promoted/top ads so we always grab fresh organic offers
                if (isFeatured) {
                    continue;
                }

                Element linkTag = card.selectFirst("a[href]");
                if (linkTag == null) continue;

                String href = linkTag.attr("abs:href");
                if (href.isEmpty() || href.contains("promoted")) continue;
                href = href.split("\\?")[0];

                Element titleTag = card.selectFirst("h4, h6");
                String title = titleTag != null ? titleTag.text().trim() : "Оголошення OLX";

                Element priceTag = card.selectFirst("p[data-testid=\"ad-price\"]");
                String price = priceTag != null ? priceTag.text().trim() : "Ціна за домовленістю";

                List<String> photos = new ArrayList<>();
                Element img = card.selectFirst("img[src]");
                if (img != null && img.attr("src").startsWith("http")) {
                    photos.add(img.attr("src"));
                }

                String timeStr = "";
                Element locDateEl = card.selectFirst("p[data-testid=\"location-date\"]");
                if (locDateEl != null) {
                    String fullDateText = locDateEl.text().trim();
                    String datePart = fullDateText;
                    if (fullDateText.contains(" - ")) {
                        datePart = fullDateText.substring(fullDateText.lastIndexOf(" - ") + 3).trim();
                    }
                    Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(datePart);
                    if (m.find()) {
                        timeStr = toKyivTime(m.group(1));
                    } else {
                        timeStr = datePart.replaceAll("\\s*р\\.?$", "").trim();
                    }
                }
                if (timeStr.isEmpty()) {
                    timeStr = LocalTime.now(KYIV_ZONE).format(TIME_FMT);
                }

                results.add(new PropertyResult(title, price, href, photos, "OLX", timeStr));
                if (results.size() >= maxCount) break;
            }

            // Fallback to 4th ad (first organic ad in classic OLX list view) if empty
            if (results.isEmpty() && cards.size() >= 4) {
                Element fourthAd = cards.get(3);
                Element linkTag = fourthAd.selectFirst("a[href]");
                if (linkTag != null) {
                    String href = linkTag.attr("abs:href").split("\\?")[0];
                    String timeStr = LocalTime.now(KYIV_ZONE).format(TIME_FMT);
                    results.add(new PropertyResult("Оголошення OLX", "", href, (String) null, "OLX", timeStr));
                }
            }
        } catch (IOException e) {
            System.out.println("Помилка підключення до OLX: " + e.getMessage());
        }
        return results;
    }
}
