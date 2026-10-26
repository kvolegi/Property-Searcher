package PropertySeacher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CategoryCatalog & URL Construction Engine Tests")
class CategoryCatalogTest {

    @Test
    @DisplayName("Should build valid OLX search URL with mandatory order and list view")
    void testOlxUrlBuilderStandard() {
        CategoryCatalog.City kyiv = new CategoryCatalog.City("Київ", "ko", "kiev");
        CategoryCatalog.PropertyType apartments = new CategoryCatalog.PropertyType("Квартири", "kvartiry", "kvartir");
        CategoryCatalog.DealType buy = new CategoryCatalog.DealType("Купівля / Продаж", "prodazha", "prodazha");

        String url = CategoryCatalog.buildUrl("OLX", kyiv, apartments, buy, 15000, 50000, true);

        assertNotNull(url);
        assertTrue(url.startsWith("https://www.olx.ua/uk/nedvizhimost/kvartiry/ko/"));
        assertTrue(url.contains("view=list"), "OLX URL must enforce row layout for fast parsing");
        assertTrue(url.contains("search%5Border%5D=created_at%3Adesc"), "OLX URL must enforce newest first order");
        assertTrue(url.contains("currency=UAH"));
        assertTrue(url.contains("search%5Bfilter_float_price%3Afrom%5D=15000"));
        assertTrue(url.contains("search%5Bfilter_float_price%3Ato%5D=50000"));
        assertTrue(url.contains("search%5Bprivate_business%5D=private"), "Owner filter must be appended for private-only search");
    }

    @Test
    @DisplayName("Should build valid DOM.RIA search URL with sorting and direct-owner parameters")
    void testDomRiaUrlBuilderStandard() {
        CategoryCatalog.City odesa = new CategoryCatalog.City("Одеса", "od", "odessa");
        CategoryCatalog.PropertyType apartments = new CategoryCatalog.PropertyType("Квартири", "kvartiry", "kvartir");
        CategoryCatalog.DealType rent = new CategoryCatalog.DealType("Довгострокова оренда", "dolgosrochnaya-arenda", "arenda");

        String url = CategoryCatalog.buildUrl("DOM.RIA", odesa, apartments, rent, null, null, true);

        assertNotNull(url);
        assertTrue(url.startsWith("https://dom.ria.com/uk/arenda-kvartir/odessa/"));
        assertTrue(url.contains("sort=1"), "DOM.RIA URL must enforce sort=1 (newest first)");
        assertTrue(url.contains("price_cur=1"), "DOM.RIA URL must set UAH/default currency parameter");
        assertTrue(url.contains("without_agent=1"), "DOM.RIA URL must enforce 100% direct owner filter");
    }

    @Test
    @DisplayName("Should exclude price bounds when not specified or zero")
    void testUrlBuilderWithoutPriceBounds() {
        CategoryCatalog.City lviv = new CategoryCatalog.City("Львів", "lvr", "lvov");
        CategoryCatalog.PropertyType houses = new CategoryCatalog.PropertyType("Будинки / Дачі", "doma", "domov");
        CategoryCatalog.DealType buy = new CategoryCatalog.DealType("Купівля / Продаж", "prodazha", "prodazha");

        String url = CategoryCatalog.buildUrl("OLX", lviv, houses, buy, null, 0, false);

        assertFalse(url.contains("price%3Afrom"));
        assertFalse(url.contains("price%3Ato"));
        assertFalse(url.contains("private_business"));
    }

    @Test
    @DisplayName("Sanity check: City catalog must contain major regional capitals without duplicates")
    void testCatalogIntegrity() {
        List<CategoryCatalog.City> cities = CategoryCatalog.getCities();
        assertFalse(cities.isEmpty(), "Cities catalog must not be empty");
        assertTrue(cities.size() >= 20, "Catalog must cover at least 20 Ukrainian regional centres");

        Set<String> uniqueCodes = new HashSet<>();
        Set<String> uniqueNames = new HashSet<>();
        for (CategoryCatalog.City city : cities) {
            assertNotNull(city.name);
            assertNotNull(city.olxCode);
            assertNotNull(city.domRiaSlug);
            assertTrue(uniqueCodes.add(city.olxCode), "Duplicate OLX city code found: " + city.olxCode);
            assertTrue(uniqueNames.add(city.name), "Duplicate city name found: " + city.name);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Київ, ko, kiev",
            "Одеса, od, odessa",
            "Львів, lvr, lvov",
            "Дніпро, dnp, dnepr",
            "Харків, khk, kharkov"
    })
    @DisplayName("Parameterized verification of key Ukrainian metropolitan city codes")
    void testMajorCityMappings(String cityName, String expectedOlx, String expectedDomRia) {
        CategoryCatalog.City city = CategoryCatalog.getCities().stream()
                .filter(c -> c.name.equals(cityName))
                .findFirst()
                .orElse(null);

        assertNotNull(city, "City not found in catalog: " + cityName);
        assertEquals(expectedOlx, city.olxCode);
        assertEquals(expectedDomRia, city.domRiaSlug);
    }
}
