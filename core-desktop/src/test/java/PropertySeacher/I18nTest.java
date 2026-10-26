package PropertySeacher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("I18n Localization & Translation Parity Tests")
class I18nTest {

    private I18n.Language originalLanguage;

    @BeforeEach
    void setUp() {
        originalLanguage = I18n.getLanguage();
    }

    @AfterEach
    void tearDown() {
        I18n.setLanguage(originalLanguage);
    }

    @Test
    @DisplayName("Translation Parity: All keys must exist symmetrically in both UA and EN dictionaries")
    @SuppressWarnings("unchecked")
    void testDictionaryParity() throws Exception {
        Field uaField = I18n.class.getDeclaredField("UA_MAP");
        Field enField = I18n.class.getDeclaredField("EN_MAP");
        uaField.setAccessible(true);
        enField.setAccessible(true);

        Map<String, String> uaMap = (Map<String, String>) uaField.get(null);
        Map<String, String> enMap = (Map<String, String>) enField.get(null);

        assertNotNull(uaMap, "UA_MAP must be initialized");
        assertNotNull(enMap, "EN_MAP must be initialized");

        Set<String> uaKeys = uaMap.keySet();
        Set<String> enKeys = enMap.keySet();

        // Check UA keys missing in EN
        for (String k : uaKeys) {
            assertTrue(enKeys.contains(k), "Missing EN translation for key: '" + k + "'");
            assertFalse(enMap.get(k).trim().isEmpty(), "Empty EN translation value for key: '" + k + "'");
        }

        // Check EN keys missing in UA
        for (String k : enKeys) {
            assertTrue(uaKeys.contains(k), "Missing UA translation for key: '" + k + "'");
            assertFalse(uaMap.get(k).trim().isEmpty(), "Empty UA translation value for key: '" + k + "'");
        }
    }

    @Test
    @DisplayName("Should dynamically switch active language and return correct localized strings")
    void testDynamicLanguageSwitching() {
        I18n.setLanguage(I18n.Language.UA);
        assertEquals("Старт", I18n.get("btn.start"));
        assertEquals("Стоп", I18n.get("btn.stop"));
        assertEquals("Налаштування", I18n.get("btn.settings"));

        I18n.setLanguage(I18n.Language.EN);
        assertEquals("Start", I18n.get("btn.start"));
        assertEquals("Stop", I18n.get("btn.stop"));
        assertEquals("Settings", I18n.get("btn.settings"));
    }

    @Test
    @DisplayName("Should safely return key as fallback when nonexistent key is queried")
    void testNonexistentKeyFallback() {
        String nonExistent = "system.unregistered.dummy.key.12345";
        assertEquals(nonExistent, I18n.get(nonExistent));
    }

    @ParameterizedTest
    @CsvSource({
            "EN, EN",
            "ENG, EN",
            "english, EN",
            "English, EN",
            "UA, UA",
            "uk, UA",
            "ukrainian, UA",
            ", UA"
    })
    @DisplayName("Should reliably parse language enum from various user code representations")
    void testLanguageCodeParsing(String inputCode, String expectedEnum) {
        I18n.Language lang = I18n.Language.fromString(inputCode);
        assertEquals(I18n.Language.valueOf(expectedEnum), lang);
    }

    @Test
    @DisplayName("Category titles must dynamically translate without mixed language fragments")
    void testCategoryLocalization() {
        String uaTitle = "Оренда квартир Київ (OLX)";
        String enTitle = CategoryCatalog.localizeCategoryName(uaTitle, I18n.Language.EN);
        assertEquals("Rent apartments Kyiv (OLX)", enTitle);

        String uaTitle2 = "Продаж квартир Одеса (DOM.RIA)";
        String enTitle2 = CategoryCatalog.localizeCategoryName(uaTitle2, I18n.Language.EN);
        assertEquals("Buy apartments Odesa (DOM.RIA)", enTitle2);

        String uaDirect = CategoryCatalog.localizeCategoryName(uaTitle, I18n.Language.UA);
        assertEquals(uaTitle, uaDirect);
    }

    @Test
    @DisplayName("Price formatting must strictly honor active language (UAH vs грн, English labels)")
    void testPriceFormattingBilingual() {
        String priceUa = CurrencyService.formatPrice("25000 грн", CurrencyService.CurrencyMode.UAH, I18n.Language.UA);
        assertTrue(priceUa.contains("грн"));
        assertFalse(priceUa.contains("UAH"));

        String priceEn = CurrencyService.formatPrice("25000 грн", CurrencyService.CurrencyMode.UAH, I18n.Language.EN);
        assertTrue(priceEn.contains("UAH"));
        assertFalse(priceEn.contains("грн"));

        String emptyEn = CurrencyService.formatPrice("", CurrencyService.CurrencyMode.DUAL, I18n.Language.EN);
        assertEquals("Price not specified", emptyEn);

        String emptyUa = CurrencyService.formatPrice("", CurrencyService.CurrencyMode.DUAL, I18n.Language.UA);
        assertEquals("Ціна не вказана", emptyUa);
    }
}
