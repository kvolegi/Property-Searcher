package com.propertyseacher.analytics;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class PriceAnalyticsService {

    private static final Pattern DIGITS_PATTERN = Pattern.compile("(\\d[\\d\\s]*\\d|\\d)");
    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);

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

        BigDecimal percentage = diff.divide(oldPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("📉 *Ціна знизилася!* %s %s (%s%%)", diff.abs().toPlainString(), currency, percentage.abs().toPlainString());
        } else {
            return String.format("📈 *Ціна зросла:* +%s %s (+%s%%)", diff.toPlainString(), currency, percentage.toPlainString());
        }
    }

    public record StatisticalDistribution(
            BigDecimal mean,
            BigDecimal standardDeviation,
            int sampleSize
    ) {}

    public record DealValuation(
            boolean isHotDeal,
            double discountPercentage,
            double zScore,
            String badge,
            String analysisText
    ) {
        public DealValuation(boolean isHotDeal, double discountPercentage, String badge, String analysisText) {
            this(isHotDeal, discountPercentage, 0.0, badge, analysisText);
        }
    }

    /**
     * Computes the empirical sample mean (mu) and sample standard deviation (sigma).
     */
    public StatisticalDistribution calculateDistribution(List<BigDecimal> sample) {
        if (sample == null || sample.isEmpty()) {
            return null;
        }
        List<BigDecimal> valid = sample.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        int n = valid.size();
        if (n == 0) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal p : valid) {
            sum = sum.add(p);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);

        if (n < 2) {
            return new StatisticalDistribution(mean, BigDecimal.ZERO, n);
        }

        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (BigDecimal p : valid) {
            BigDecimal diff = p.subtract(mean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }

        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(n - 1), 6, RoundingMode.HALF_UP);
        double stdDevDouble = Math.sqrt(variance.doubleValue());
        BigDecimal stdDev = BigDecimal.valueOf(stdDevDouble).setScale(4, RoundingMode.HALF_UP);

        return new StatisticalDistribution(mean, stdDev, n);
    }

    /**
     * Dynamic Statistical Anomaly & Z-Score Bargain Detection Engine.
     * Evaluates whether an incoming listing price deviates significantly below the historical market mean.
     * Formula: z = (mean - price) / standardDeviation
     */
    public DealValuation evaluateStatisticalBargain(BigDecimal price, List<BigDecimal> historicalPrices, String currency, String platform) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return new DealValuation(false, 0.0, 0.0, "📊 Fair Market", "Awaiting valid numeric price.");
        }

        StatisticalDistribution dist = calculateDistribution(historicalPrices);
        if (dist == null || dist.sampleSize() < 3 || dist.standardDeviation().compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal baselineMean = dist != null && dist.mean().compareTo(BigDecimal.ZERO) > 0
                    ? dist.mean()
                    : price;
            return evaluateBargain(price, baselineMean, currency);
        }

        BigDecimal diff = dist.mean().subtract(price);
        double z = diff.divide(dist.standardDeviation(), 4, RoundingMode.HALF_UP).doubleValue();
        double discountPct = 0.0;
        if (dist.mean().compareTo(BigDecimal.ZERO) > 0) {
            discountPct = diff.divide(dist.mean(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // Statistical significance gates:
        // Z >= 2.0 (p < 0.025, 97.5th percentile bargain): Ultra-Hot Deal
        // Z >= 1.5 (p < 0.067, 93rd percentile bargain): Hot Deal
        // Z >= 0.5: Below Market Average
        if (z >= 2.0) {
            String badge = String.format("🔥 ULTRA-HOT DEAL (Z=%.2f, -%.1f%%)", z, discountPct);
            String text = String.format("Statistical anomaly: price is %.2f standard deviations below market mean (%s %s, N=%d). Extreme discount opportunity.",
                    z, dist.mean().setScale(0, RoundingMode.HALF_UP).toPlainString(), currency, dist.sampleSize());
            return new DealValuation(true, discountPct, z, badge, text);
        } else if (z >= 1.5) {
            String badge = String.format("🔥 HOT DEAL (Z=%.2f, -%.1f%%)", z, discountPct);
            String text = String.format("Listing price is %.2f std dev below sample average (%s %s, N=%d). Immediate investor sniping recommended.",
                    z, dist.mean().setScale(0, RoundingMode.HALF_UP).toPlainString(), currency, dist.sampleSize());
            return new DealValuation(true, discountPct, z, badge, text);
        } else if (z >= 0.5) {
            String badge = String.format("⚡ Below Average (-%.1f%%)", discountPct);
            String text = String.format("Priced %.1f%% below category average (Z=%.2f, mean: %s %s).",
                    discountPct, z, dist.mean().setScale(0, RoundingMode.HALF_UP).toPlainString(), currency);
            return new DealValuation(false, discountPct, z, badge, text);
        }

        return new DealValuation(false, Math.max(0.0, discountPct), z, "📊 Fair Market",
                String.format("Within normal statistical bounds (Z=%.2f, mean: %s %s, N=%d).",
                        z, dist.mean().setScale(0, RoundingMode.HALF_UP).toPlainString(), currency, dist.sampleSize()));
    }

    /**
     * Backward-compatible percentage baseline evaluator.
     */
    public DealValuation evaluateBargain(BigDecimal price, BigDecimal benchmarkAveragePrice, String currency) {
        if (price == null || benchmarkAveragePrice == null || benchmarkAveragePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new DealValuation(false, 0.0, 0.0, "📊 Fair Market", "Standard market valuation.");
        }

        BigDecimal diff = benchmarkAveragePrice.subtract(price);
        if (diff.compareTo(BigDecimal.ZERO) <= 0) {
            return new DealValuation(false, 0.0, 0.0, "📊 Market Standard", "Price aligns with current aggregate asks.");
        }

        BigDecimal discountRatio = diff.divide(benchmarkAveragePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        double discountPct = discountRatio.doubleValue();

        if (discountPct >= 20.0) {
            String badge = String.format("🔥 HOT DEAL (-%.1f%%)", discountPct);
            String text = String.format("Listing price is %.1f%% below category benchmark! Immediate investor sniping recommended.", discountPct);
            return new DealValuation(true, discountPct, 0.0, badge, text);
        } else if (discountPct >= 10.0) {
            String badge = String.format("⚡ Below Average (-%.1f%%)", discountPct);
            String text = String.format("Priced %.1f%% below category average.", discountPct);
            return new DealValuation(false, discountPct, 0.0, badge, text);
        }

        return new DealValuation(false, discountPct, 0.0, "📊 Fair Market", "Within normal statistical deviation.");
    }
}
