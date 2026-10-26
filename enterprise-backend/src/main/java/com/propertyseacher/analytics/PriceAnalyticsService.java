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

    public record DealValuation(
            boolean isHotDeal,
            double discountPercentage,
            String badge,
            String analysisText
    ) {}

    /**
     * Statistical Anomaly & Bargain Detection Engine
     * Evaluates whether an incoming listing qualifies as a high-yield "Hot Deal" relative to category benchmarks.
     */
    public DealValuation evaluateBargain(BigDecimal price, BigDecimal benchmarkAveragePrice, String currency) {
        if (price == null || benchmarkAveragePrice == null || benchmarkAveragePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new DealValuation(false, 0.0, "📊 Fair Market", "Standard market valuation.");
        }

        BigDecimal diff = benchmarkAveragePrice.subtract(price);
        if (diff.compareTo(BigDecimal.ZERO) <= 0) {
            return new DealValuation(false, 0.0, "📊 Market Standard", "Price aligns with current aggregate asks.");
        }

        BigDecimal discountRatio = diff.divide(benchmarkAveragePrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        double discountPct = discountRatio.doubleValue();

        if (discountPct >= 20.0) {
            String badge = String.format("🔥 HOT DEAL (-%.1f%%)", discountPct);
            String text = String.format("Listing price is %.1f%% below category benchmark! Immediate investor sniping recommended.", discountPct);
            return new DealValuation(true, discountPct, badge, text);
        } else if (discountPct >= 10.0) {
            String badge = String.format("⚡ Below Average (-%.1f%%)", discountPct);
            String text = String.format("Priced %.1f%% below category average.", discountPct);
            return new DealValuation(false, discountPct, badge, text);
        }

        return new DealValuation(false, discountPct, "📊 Fair Market", "Within normal statistical deviation.");
    }
}
