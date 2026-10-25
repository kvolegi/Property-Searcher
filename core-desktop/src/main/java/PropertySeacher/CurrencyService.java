package PropertySeacher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurrencyService {

    public enum CurrencyMode {
        DUAL,     // Original + Equivalent: e.g. "$65 000 (≈ 2 938 000 грн)"
        USD,      // Forces or emphasizes USD
        UAH,      // Forces or emphasizes UAH
        EUR,      // Forces or emphasizes EUR
        ORIGINAL  // As listed on site
    }

    private static final String NBU_API_URL = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json";
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours TTL

    // Production Fallback Constants (if offline or NBU unreachable):
    private static double usdRate = 41.60;
    private static double eurRate = 45.30;
    private static long lastUpdateTime = 0;

    private static final DecimalFormat NUM_FMT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(' ');
        NUM_FMT = new DecimalFormat("#,###", symbols);
        initRates();
    }

    public static synchronized void initRates() {
        loadCacheFromFile();
        long now = System.currentTimeMillis();
        // If cache is older than 24 hours or missing, refresh in background thread:
        if (now - lastUpdateTime > CACHE_TTL_MS) {
            new Thread(CurrencyService::fetchFromNbuSync, "CurrencyService-NBU-Worker").start();
        }
    }

    public static double getUsdRate() {
        return usdRate;
    }

    public static double getEurRate() {
        return eurRate;
    }

    private static File getCacheFile() {
        File sf = SettingsManager.getSettingsFile();
        if (sf != null && sf.getParentFile() != null) {
            return new File(sf.getParentFile(), "currency_cache.json");
        }
        return new File("currency_cache.json");
    }

    private static synchronized void loadCacheFromFile() {
        File f = getCacheFile();
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject json = new JSONObject(sb.toString());
                usdRate = json.optDouble("usdRate", usdRate);
                eurRate = json.optDouble("eurRate", eurRate);
                lastUpdateTime = json.optLong("timestamp", 0);
            } catch (Exception ignored) {}
        }
    }

    private static synchronized void saveCacheToFile() {
        File f = getCacheFile();
        try {
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            JSONObject json = new JSONObject();
            json.put("usdRate", usdRate);
            json.put("eurRate", eurRate);
            json.put("timestamp", lastUpdateTime);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, StandardCharsets.UTF_8, false))) {
                bw.write(json.toString(2));
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void fetchFromNbuSync() {
        try {
            URL url = new URL(NBU_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "PropertySearcher-Engine/3.0");

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    JSONArray arr = new JSONArray(sb.toString());
                    boolean foundUsd = false;
                    boolean foundEur = false;

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String cc = obj.optString("cc");
                        double rate = obj.optDouble("rate", 0);
                        if ("USD".equalsIgnoreCase(cc) && rate > 0) {
                            usdRate = Math.round(rate * 100.0) / 100.0;
                            foundUsd = true;
                        } else if ("EUR".equalsIgnoreCase(cc) && rate > 0) {
                            eurRate = Math.round(rate * 100.0) / 100.0;
                            foundEur = true;
                        }
                    }

                    if (foundUsd || foundEur) {
                        lastUpdateTime = System.currentTimeMillis();
                        saveCacheToFile();
                        System.out.println("💱 [NBU Currency Rates Updated]: 1 USD = " + usdRate + " UAH | 1 EUR = " + eurRate + " UAH");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ℹ️ NBU Currency API unreachable (using cached/fallback rates: USD=" + usdRate + ", EUR=" + eurRate + "): " + e.getMessage());
        }
    }

    /**
     * Formats price string according to chosen CurrencyMode.
     * Original examples: "2 500 000 грн", "$65 000", "45 000 €", "350 $"
     */
    public static String formatPrice(String rawPrice, CurrencyMode mode) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return "Ціна не вказана";
        }
        if (mode == CurrencyMode.ORIGINAL) {
            return rawPrice.trim();
        }

        // Clean and detect currency
        String lower = rawPrice.toLowerCase().trim();
        boolean isUsd = lower.contains("$") || lower.contains("usd") || lower.contains("дол");
        boolean isEur = lower.contains("€") || lower.contains("eur") || lower.contains("євро");
        boolean isUah = lower.contains("грн") || lower.contains("uah") || lower.contains("₴");

        // Extract digits
        String digitsOnly = rawPrice.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) {
            return rawPrice.trim(); // Non-numeric (e.g. "Договірна")
        }

        long numericVal;
        try {
            numericVal = Long.parseLong(digitsOnly);
        } catch (Exception ex) {
            return rawPrice.trim();
        }

        // Anti-spam sanity check (skip formatting anomalous < 50 UAH values)
        if (numericVal < 50 && isUah) {
            return rawPrice.trim() + " (Символічна/Договірна)";
        }

        if (mode == CurrencyMode.DUAL) {
            if (isUsd) {
                long equivUah = Math.round(numericVal * usdRate);
                return "$" + NUM_FMT.format(numericVal) + " (≈ " + NUM_FMT.format(equivUah) + " грн)";
            } else if (isEur) {
                long equivUah = Math.round(numericVal * eurRate);
                long equivUsd = Math.round((numericVal * eurRate) / usdRate);
                return "€" + NUM_FMT.format(numericVal) + " (≈ " + NUM_FMT.format(equivUah) + " грн / $" + NUM_FMT.format(equivUsd) + ")";
            } else { // default to UAH
                long equivUsd = Math.round(numericVal / usdRate);
                return NUM_FMT.format(numericVal) + " грн (≈ $" + NUM_FMT.format(equivUsd) + ")";
            }
        } else if (mode == CurrencyMode.USD) {
            if (isUsd) {
                return "$" + NUM_FMT.format(numericVal);
            } else if (isEur) {
                long equivUsd = Math.round((numericVal * eurRate) / usdRate);
                return "$" + NUM_FMT.format(equivUsd) + " (оригінал: €" + NUM_FMT.format(numericVal) + ")";
            } else {
                long equivUsd = Math.round(numericVal / usdRate);
                return "$" + NUM_FMT.format(equivUsd) + " (оригінал: " + NUM_FMT.format(numericVal) + " грн)";
            }
        } else if (mode == CurrencyMode.UAH) {
            if (isUah) {
                return NUM_FMT.format(numericVal) + " грн";
            } else if (isUsd) {
                long equivUah = Math.round(numericVal * usdRate);
                return NUM_FMT.format(equivUah) + " грн (оригінал: $" + NUM_FMT.format(numericVal) + ")";
            } else if (isEur) {
                long equivUah = Math.round(numericVal * eurRate);
                return NUM_FMT.format(equivUah) + " грн (оригінал: €" + NUM_FMT.format(numericVal) + ")";
            } else {
                return NUM_FMT.format(numericVal) + " грн";
            }
        } else if (mode == CurrencyMode.EUR) {
            if (isEur) {
                return "€" + NUM_FMT.format(numericVal);
            } else if (isUsd) {
                long equivEur = Math.round((numericVal * usdRate) / eurRate);
                return "€" + NUM_FMT.format(equivEur) + " (оригінал: $" + NUM_FMT.format(numericVal) + ")";
            } else {
                long equivEur = Math.round(numericVal / eurRate);
                return "€" + NUM_FMT.format(equivEur) + " (оригінал: " + NUM_FMT.format(numericVal) + " грн)";
            }
        }

        return rawPrice.trim();
    }
}
