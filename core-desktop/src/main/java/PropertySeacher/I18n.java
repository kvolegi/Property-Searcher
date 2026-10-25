package PropertySeacher;

import java.util.HashMap;
import java.util.Map;

public class I18n {

    public enum Language {
        UA("Українська", "🇺🇦"),
        EN("English", "🇬🇧");

        public final String title;
        public final String flag;

        Language(String title, String flag) {
            this.title = title;
            this.flag = flag;
        }

        public static Language fromString(String code) {
            if (code == null) return UA;
            if ("EN".equalsIgnoreCase(code) || "ENG".equalsIgnoreCase(code) || "ENGLISH".equalsIgnoreCase(code)) {
                return EN;
            }
            return UA;
        }
    }

    private static volatile Language currentLang = Language.UA;
    private static final Map<String, String> UA_MAP = new HashMap<>();
    private static final Map<String, String> EN_MAP = new HashMap<>();

    static {
        // --- UKRAINIAN (UA) ---
        UA_MAP.put("app.title", "Property Searcher 3 — Снайпінг нерухомості (OLX & DOM.RIA)");
        UA_MAP.put("btn.start", "Запустити пошук");
        UA_MAP.put("btn.stop", "Зупинити");
        UA_MAP.put("btn.settings", "Налаштування бота");
        UA_MAP.put("btn.categories", "Категорії пошуку");
        UA_MAP.put("btn.builder", "Конструктор категорій");
        UA_MAP.put("btn.instruction", "Інструкція та BotFather");
        UA_MAP.put("btn.lang", "Мова (Language)");

        UA_MAP.put("status.active", "🟢 АКТИВНИЙ");
        UA_MAP.put("status.paused", "⏸ ЗУПИНЕНО");
        UA_MAP.put("status.monitoring", "Моніторинг");
        UA_MAP.put("status.total_categories", "Всього категорій");
        UA_MAP.put("status.active_categories", "активних");
        UA_MAP.put("status.adaptive_delay", "Адаптивний інтервал");
        UA_MAP.put("status.safe_rps", "Безпека з'єднань");
        UA_MAP.put("status.currency_mode", "Валюта цін");
        UA_MAP.put("status.night_mode", "Режим сповіщень");
        UA_MAP.put("status.night_silent", "🌙 Нічний (без звуку)");
        UA_MAP.put("status.day_sound", "☀️ Денний (зі звуком)");

        UA_MAP.put("tg.welcome", "👋 <b>Вітаю у Property Searcher 3!</b>\n\nБот миттєво відстежує тільки <b>найновіші органічні оголошення</b> (OLX & DOM.RIA), оминаючи рекламу та ТОП-блоки.\n\nОберіть дію на клавіатурі нижче:");
        UA_MAP.put("tg.menu.search", "🔍 Пошук зараз");
        UA_MAP.put("tg.menu.categories", "📋 Мої категорії");
        UA_MAP.put("tg.menu.status", "📊 Статус моніторингу");
        UA_MAP.put("tg.menu.currency", "💵 Валюта цін");
        UA_MAP.put("tg.menu.stop", "⏸ Зупинити");
        UA_MAP.put("tg.menu.help", "ℹ️ Інструкція та допомога");
        UA_MAP.put("tg.menu.lang", "🌐 Мова / Language");

        UA_MAP.put("card.category", "Категорія");
        UA_MAP.put("card.price", "Ціна");
        UA_MAP.put("card.time", "Час");
        UA_MAP.put("card.source", "Джерело");
        UA_MAP.put("card.open", "Відкрити на");

        // --- ENGLISH (EN) ---
        EN_MAP.put("app.title", "Property Searcher 3 — Real Estate Sniping Engine (OLX & DOM.RIA)");
        EN_MAP.put("btn.start", "Start Monitoring");
        EN_MAP.put("btn.stop", "Stop");
        EN_MAP.put("btn.settings", "Bot Settings");
        EN_MAP.put("btn.categories", "Search Targets");
        EN_MAP.put("btn.builder", "Category Builder");
        EN_MAP.put("btn.instruction", "Manual & BotFather");
        EN_MAP.put("btn.lang", "Language (Мова)");

        EN_MAP.put("status.active", "🟢 ACTIVE");
        EN_MAP.put("status.paused", "⏸ PAUSED");
        EN_MAP.put("status.monitoring", "Monitoring");
        EN_MAP.put("status.total_categories", "Total categories");
        EN_MAP.put("status.active_categories", "active");
        EN_MAP.put("status.adaptive_delay", "Adaptive pacing");
        EN_MAP.put("status.safe_rps", "Connection safety");
        EN_MAP.put("status.currency_mode", "Price currency");
        EN_MAP.put("status.night_mode", "Notification mode");
        EN_MAP.put("status.night_silent", "🌙 Night (silent)");
        EN_MAP.put("status.day_sound", "☀️ Day (with sound)");

        EN_MAP.put("tg.welcome", "👋 <b>Welcome to Property Searcher 3!</b>\n\nInstant high-velocity monitoring for <b>organic real estate listings</b> (OLX & DOM.RIA), bypassing sponsored ads and promoted top-slots.\n\nSelect an action on the keyboard below:");
        EN_MAP.put("tg.menu.search", "🔍 Search Now");
        EN_MAP.put("tg.menu.categories", "📋 My Categories");
        EN_MAP.put("tg.menu.status", "📊 System Status");
        EN_MAP.put("tg.menu.currency", "💵 Price Currency");
        EN_MAP.put("tg.menu.stop", "⏸ Stop");
        EN_MAP.put("tg.menu.help", "ℹ️ Help & Manual");
        EN_MAP.put("tg.menu.lang", "🌐 Language / Мова");

        EN_MAP.put("card.category", "Category");
        EN_MAP.put("card.price", "Price");
        EN_MAP.put("card.time", "Time");
        EN_MAP.put("card.source", "Source");
        EN_MAP.put("card.open", "Open on");
    }

    public static void setLanguage(Language lang) {
        if (lang != null) {
            currentLang = lang;
        }
    }

    public static Language getLanguage() {
        return currentLang;
    }

    public static String get(String key) {
        Map<String, String> map = (currentLang == Language.EN) ? EN_MAP : UA_MAP;
        String val = map.get(key);
        if (val != null) return val;
        // Fallback to UA if missing in EN
        String fallback = UA_MAP.get(key);
        return (fallback != null) ? fallback : key;
    }

    public static String get(String key, Language lang) {
        Map<String, String> map = (lang == Language.EN) ? EN_MAP : UA_MAP;
        String val = map.get(key);
        if (val != null) return val;
        String fallback = UA_MAP.get(key);
        return (fallback != null) ? fallback : key;
    }
}
