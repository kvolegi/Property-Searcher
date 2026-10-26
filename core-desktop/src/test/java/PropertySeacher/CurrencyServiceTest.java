package PropertySeacher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CurrencyService & Financial Conversion Engine Tests")
class CurrencyServiceTest {

    @Test
    @DisplayName("Should maintain valid fallback exchange rates when offline")
    void testExchangeRatesSanity() {
        double usd = CurrencyService.getUsdRate();
        double eur = CurrencyService.getEurRate();

        assertTrue(usd >= 35.0 && usd <= 60.0, "USD rate should be within plausible real-world boundaries");
        assertTrue(eur >= 38.0 && eur <= 70.0, "EUR rate should be within plausible real-world boundaries");
        assertTrue(eur >= usd, "EUR rate should be higher or equal to USD rate");
    }

    @ParameterizedTest
    @CsvSource({
            "'$65 000', 65000",
            "'$ 120,500', 120500",
            "'2 500 000 грн', 2500000",
            "'35000 грн.', 35000",
            "'€45 000', 45000",
            "'100 000 $', 100000"
    })
    @DisplayName("Should reliably parse numeric price values from diverse raw listing texts")
    void testPriceAmountParsing(String rawText, long expectedAmount) {
        long extracted = CurrencyService.extractNumericPrice(rawText);
        assertEquals(expectedAmount, extracted);
    }

    @Test
    @DisplayName("Dual currency mode formatting should contain both original price and equivalent conversion")
    void testDualCurrencyFormatting() {
        String originalPrice = "$50 000";
        String formatted = CurrencyService.formatPrice(originalPrice, CurrencyService.CurrencyMode.DUAL);

        assertNotNull(formatted);
        assertTrue(formatted.contains("$50"), "Must contain formatted original price");
        assertTrue(formatted.contains("грн"), "DUAL mode must provide UAH equivalent");
    }

    @Test
    @DisplayName("USD mode should respect user preference formatting")
    void testSingleCurrencyModes() {
        String originalUsd = "$10 000";
        String usdModeResult = CurrencyService.formatPrice(originalUsd, CurrencyService.CurrencyMode.USD);
        assertNotNull(usdModeResult);
        assertTrue(usdModeResult.contains("$10"), "Should format as USD");
    }
}
