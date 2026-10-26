package PropertySeacher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CategoryCatalog implements Serializable {

    public static class City {
        public final String name;
        public final String nameEn;
        public final String olxCode;
        public final String domRiaSlug;

        public City(String name, String olxCode, String domRiaSlug) {
            this(name, name, olxCode, domRiaSlug);
        }

        public City(String name, String nameEn, String olxCode, String domRiaSlug) {
            this.name = name;
            this.nameEn = nameEn;
            this.olxCode = olxCode;
            this.domRiaSlug = domRiaSlug;
        }

        public String getName(I18n.Language lang) {
            return (lang == I18n.Language.EN && nameEn != null) ? nameEn : name;
        }

        @Override
        public String toString() {
            return getName(I18n.getLanguage());
        }
    }

    public static class PropertyType {
        public final String name;
        public final String nameEn;
        public final String olxCategory;
        public final String domRiaCategory;

        public PropertyType(String name, String olxCategory, String domRiaCategory) {
            this(name, name, olxCategory, domRiaCategory);
        }

        public PropertyType(String name, String nameEn, String olxCategory, String domRiaCategory) {
            this.name = name;
            this.nameEn = nameEn;
            this.olxCategory = olxCategory;
            this.domRiaCategory = domRiaCategory;
        }

        public String getName(I18n.Language lang) {
            return (lang == I18n.Language.EN && nameEn != null) ? nameEn : name;
        }

        @Override
        public String toString() {
            return getName(I18n.getLanguage());
        }
    }

    public static class DealType {
        public final String name;
        public final String nameEn;
        public final String olxSegment;
        public final String domRiaPrefix;

        public DealType(String name, String olxSegment, String domRiaPrefix) {
            this(name, name, olxSegment, domRiaPrefix);
        }

        public DealType(String name, String nameEn, String olxSegment, String domRiaPrefix) {
            this.name = name;
            this.nameEn = nameEn;
            this.olxSegment = olxSegment;
            this.domRiaPrefix = domRiaPrefix;
        }

        public String getName(I18n.Language lang) {
            return (lang == I18n.Language.EN && nameEn != null) ? nameEn : name;
        }

        @Override
        public String toString() {
            return getName(I18n.getLanguage());
        }
    }

    // Micro DB: Standardized Ukrainian Cities (universal bilingual labels)
    public static List<City> getCities() {
        List<City> list = new ArrayList<>();
        list.add(new City("Київ", "Kyiv", "ko", "kiev"));
        list.add(new City("Одеса", "Odesa", "od", "odessa"));
        list.add(new City("Львів", "Lviv", "lvr", "lvov"));
        list.add(new City("Дніпро", "Dnipro", "dnp", "dnepr"));
        list.add(new City("Житомир", "Zhytomyr", "zht", "zhitomir"));
        list.add(new City("Харків", "Kharkiv", "khk", "kharkov"));
        list.add(new City("Івано-Франківськ", "Ivano-Frankivsk", "ifr", "ivano-frankovsk"));
        list.add(new City("Вінниця", "Vinnytsia", "vin", "vinnitsa"));
        list.add(new City("Миколаїв", "Mykolaiv", "nik", "nikolaev"));
        list.add(new City("Запоріжжя", "Zaporizhzhia", "zap", "zaporozhe"));
        list.add(new City("Полтава", "Poltava", "pol", "poltava"));
        list.add(new City("Черкаси", "Cherkasy", "chk", "cherkassy"));
        list.add(new City("Чернігів", "Chernihiv", "chg", "chernigov"));
        list.add(new City("Рівне", "Rivne", "rvn", "rovno"));
        list.add(new City("Луцьк", "Lutsk", "vol", "lutsk"));
        list.add(new City("Ужгород", "Uzhhorod", "zak", "uzhgorod"));
        list.add(new City("Чернівці", "Chernivtsi", "chn", "chernovtsy"));
        list.add(new City("Тернопіль", "Ternopil", "ter", "ternopol"));
        list.add(new City("Хмельницький", "Khmelnytskyi", "khm", "khmelnitskiy"));
        list.add(new City("Кропивницький", "Kropyvnytskyi", "kro", "kropivnitskiy"));
        return list;
    }

    // Property Types (Bilingual)
    public static List<PropertyType> getPropertyTypes() {
        List<PropertyType> list = new ArrayList<>();
        list.add(new PropertyType("Квартири", "Apartments", "kvartiry", "kvartir"));
        list.add(new PropertyType("Будинки / Дачі", "Houses / Cottages", "doma", "domov"));
        list.add(new PropertyType("Земельні ділянки", "Land Plots", "zemlya", "zemli"));
        list.add(new PropertyType("Комерційна нерухомість", "Commercial Real Estate", "kommercheskaya-nedvizhimost", "kommercheskaya"));
        list.add(new PropertyType("Кімнати", "Rooms", "komnaty", "komnat"));
        list.add(new PropertyType("Гаражі / Паркомісця", "Garages / Parking", "garazhy-parkovki", "garazhey"));
        return list;
    }

    // Deal Types: Купівля / Продаж IS DEFAULT (FIRST!)
    public static List<DealType> getDealTypes() {
        List<DealType> list = new ArrayList<>();
        list.add(new DealType("Купівля / Продаж", "Buy / Sale", "prodazha", "prodazha"));
        list.add(new DealType("Довгострокова оренда", "Long-term Rent", "dolgosrochnaya-arenda", "arenda"));
        list.add(new DealType("Подобова оренда", "Daily Rent", "posutochno", "posutochno"));
        return list;
    }

    /**
     * Smart URL Builder for OLX and DOM.RIA
     * Generates proper list layout (view=list) and newest sorting (search[order]=created_at:desc)
     */
    public static String buildUrl(
            String platform,
            City city,
            PropertyType propertyType,
            DealType dealType,
            Integer priceMin,
            Integer priceMax,
            boolean ownerOnly
    ) {
        if ("DOM.RIA".equalsIgnoreCase(platform)) {
            // e.g. https://dom.ria.com/uk/prodazha-kvartir/odessa/?price_cur=1&sort=1&without_agent=1
            StringBuilder sb = new StringBuilder("https://dom.ria.com/uk/");
            sb.append(dealType.domRiaPrefix).append("-").append(propertyType.domRiaCategory).append("/");
            sb.append(city.domRiaSlug).append("/?price_cur=1&sort=1");

            if (priceMin != null && priceMin > 0) {
                sb.append("&price_min=").append(priceMin);
            }
            if (priceMax != null && priceMax > 0) {
                sb.append("&price_max=").append(priceMax);
            }
            if (ownerOnly) {
                sb.append("&without_agent=1");
            }
            return sb.toString();
        } else {
            // OLX Builder
            // e.g. https://www.olx.ua/uk/nedvizhimost/kvartiry/od/?currency=UAH&search[order]=created_at:desc&view=list
            StringBuilder sb = new StringBuilder("https://www.olx.ua/uk/nedvizhimost/");
            sb.append(propertyType.olxCategory).append("/");
            sb.append(city.olxCode).append("/?currency=UAH&search%5Border%5D=created_at%3Adesc&view=list");

            if (priceMin != null && priceMin > 0) {
                sb.append("&search%5Bfilter_float_price%3Afrom%5D=").append(priceMin);
            }
            if (priceMax != null && priceMax > 0) {
                sb.append("&search%5Bfilter_float_price%3Ato%5D=").append(priceMax);
            }
            if (ownerOnly) {
                sb.append("&search%5Bprivate_business%5D=private");
            }
            return sb.toString();
        }
    }
}
