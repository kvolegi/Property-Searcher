package com.propertyseacher.analytics;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PriceAnalyticsService {

    private static final Pattern DIGITS_PATTERN = Pattern.compile("(\\d[\\d\\s]*\\d|\\d)");

    public BigDecimal parseNumericPrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        String clean = rawPrice.replace(" ", "").replace("\u00a0", "").replaceAll("[^0-9.]", "");
        if (clean.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String detectCurrency(String rawPrice) {
        if (rawPrice == null) return "UAH";
        String lower = rawPrice.toLowerCase();
        if (lower.contains("$") || lower.contains("usd") || lower.contains("дол")) return "USD";
        if (lower.contains("€") || lower.contains("eur") || lower.contains("євр")) return "EUR";
        return "UAH";
    }

    public String calculatePriceChangeSummary(BigDecimal oldPrice, BigDecimal newPrice, String currency) {
        if (oldPrice == null || newPrice == null || oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        BigDecimal diff = newPrice.subtract(oldPrice);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }

        BigDecimal percentage = diff.divide(oldPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, java.math.RoundingMode.HALF_UP);

        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("📉 *Ціна знизилася!* %s %s (%s%%)", diff.abs().toPlainString(), currency, percentage.abs().toPlainString());
        } else {
            return String.format("📈 *Ціна зросла:* +%s %s (+%s%%)", diff.toPlainString(), currency, percentage.toPlainString());
        }
    }
}
