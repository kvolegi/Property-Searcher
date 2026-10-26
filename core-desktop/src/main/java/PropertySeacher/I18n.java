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
        // ==========================================
        // --- UKRAINIAN (UA) ---
        // ==========================================
        UA_MAP.put("app.title", "Property Searcher 3");
        UA_MAP.put("btn.start", "Старт");
        UA_MAP.put("btn.stop", "Стоп");
        UA_MAP.put("btn.settings", "Налаштування");
        UA_MAP.put("btn.categories", "Категорії пошуку");
        UA_MAP.put("btn.builder", "Конструктор категорій");
        UA_MAP.put("btn.instruction", "Інструкція та BotFather");
        UA_MAP.put("btn.lang", "Мова (Language)");

        UA_MAP.put("gui.token_btn", "Токен та назва");
        UA_MAP.put("gui.category_btn", "Категорії пошуку");
        UA_MAP.put("gui.instruction_btn", "Інструкція та BotFather");
        UA_MAP.put("gui.lang_btn", "🌐 Мова / Language (UA / EN)");
        UA_MAP.put("gui.back_btn", "Назад");
        UA_MAP.put("gui.save_btn", "Зберегти");
        UA_MAP.put("gui.bot_name_lbl", "Назва бота:");
        UA_MAP.put("gui.token_lbl", "Токен:");
        UA_MAP.put("gui.token_dialog_title", "Введення токену та назви");
        UA_MAP.put("gui.settings_dialog_title", "Налаштування");
        UA_MAP.put("gui.token_len_err", "Токен повинен містити принаймні 30 символів!");
        UA_MAP.put("gui.token_required_err", "Перед запуском бота введіть токен і назву!");
        UA_MAP.put("gui.categories_required_err", "Перед запуском бота додайте хоча б одну категорію!");
        UA_MAP.put("gui.bot_running_err", "Бот уже запущений!");
        UA_MAP.put("gui.launch_failed", "Не вдалося запустити бота: ");

        UA_MAP.put("dialog.warning_title", "Увага");

        UA_MAP.put("cat.dialog_title", "Керування категоріями пошуку (OLX & DOM.RIA)");
        UA_MAP.put("cat.header_title", "Категорії моніторингу нерухомості");
        UA_MAP.put("cat.smart_builder_btn", "Конструктор категорій");
        UA_MAP.put("cat.manual_add_btn", "Додати URL вручну");
        UA_MAP.put("cat.toggle_btn", "ВКЛ / ПАУЗА");
        UA_MAP.put("cat.edit_btn", "Редагувати");
        UA_MAP.put("cat.remove_btn", "Видалити");
        UA_MAP.put("cat.close_btn", "Закрити");
        UA_MAP.put("cat.badge_on", " 🟢 ВКЛ ");
        UA_MAP.put("cat.badge_pause", " ⏸ ПАУЗА ");
        UA_MAP.put("cat.badge_owner", " Власник ");
        UA_MAP.put("cat.total", "Всього");
        UA_MAP.put("cat.active", "активних");
        UA_MAP.put("cat.select_warning", "Оберіть категорію зі списку!");
        UA_MAP.put("cat.select_active_warning", "Оберіть категорію зі списку для перемикання активності!");
        UA_MAP.put("cat.select_delete_warning", "Оберіть категорію зі списку для видалення!");
        UA_MAP.put("cat.select_edit_warning", "Оберіть категорію зі списку для редагування!");
        UA_MAP.put("cat.edit_name_prompt", "Змініть назву категорії:");
        UA_MAP.put("cat.edit_url_prompt", "Змініть URL категорії:");

        UA_MAP.put("manual.dialog_title", "Ручне додавання посилання на пошук");
        UA_MAP.put("manual.name_lbl", "Назва категорії (наприклад: Квартири Одеса купівля):");
        UA_MAP.put("manual.url_lbl", "Пряме посилання на пошук (OLX або DOM.RIA):");
        UA_MAP.put("manual.add_btn", "Додати");
        UA_MAP.put("manual.cancel_btn", "Скасувати");
        UA_MAP.put("manual.enter_warning", "Введіть назву та URL!");

        UA_MAP.put("builder.dialog_title", "Конструктор категорій нерухомості (OLX & DOM.RIA)");
        UA_MAP.put("builder.platform_lbl", "Платформа пошуку:");
        UA_MAP.put("builder.city_lbl", "Місто / Регіон:");
        UA_MAP.put("builder.type_lbl", "Тип нерухомості:");
        UA_MAP.put("builder.deal_lbl", "Тип операції:");
        UA_MAP.put("builder.price_min_lbl", "Ціна від (UAH):");
        UA_MAP.put("builder.price_max_lbl", "Ціна до (UAH):");
        UA_MAP.put("builder.owner_chk", "Тільки від власника (без посередників)");
        UA_MAP.put("builder.custom_title_lbl", "Власна назва (необов'язково):");
        UA_MAP.put("builder.preview_url_lbl", "Згенерований URL пошуку:");
        UA_MAP.put("builder.add_btn", "Додати категорію");
        UA_MAP.put("builder.cancel_btn", "Скасувати");
        UA_MAP.put("builder.enter_warning", "Заповніть назву категорії!");
        UA_MAP.put("builder.price_from", "Від");
        UA_MAP.put("builder.price_to", "До");
        UA_MAP.put("builder.direct_owner", "100% Без посередників (Власник)");
        UA_MAP.put("builder.owner_suffix", " [Власник]");

        UA_MAP.put("ins.dialog_title", "Інструкція та налаштування BotFather");
        UA_MAP.put("ins.tab_botfather", "BotFather (Команди меню)");
        UA_MAP.put("ins.tab_quickstart", "Швидкий старт та Робота");
        UA_MAP.put("ins.tab_filters", "Фільтри та Архітектура");
        UA_MAP.put("ins.copy_btn", "📋 Скопіювати команди для BotFather");
        UA_MAP.put("ins.copied_notice", "Команди успішно скопійовано в буфер обміну!\n\nТепер надішліть їх боту @BotFather після команди /setcommands.");
        UA_MAP.put("ins.copied_title", "Успішно скопійовано");
        UA_MAP.put("ins.close_btn", "Закрити");
        UA_MAP.put("ins.botfather_step_title", "Налаштування меню та кнопок бота в Telegram:");
        UA_MAP.put("ins.botfather_step_desc", "1. У Telegram відкрийте офіційного бота <b>@BotFather</b>.<br>2. Надішліть йому команду <code>/setcommands</code> та виберіть вашого створеного бота.<br>3. Натисніть кнопку нижче та надішліть скопійований список команд у чат @BotFather:");
        UA_MAP.put("ins.botfather_commands", "start - Головне меню та запуск\nsearch - Запустити пошук оголошень\ntargets - Список активних категорій\ncurrency - Валюта цін (USD/UAH)\nstop - Зупинити моніторинг\nhelp - Довідка та інструкція");
        UA_MAP.put("ins.copied_button_text", "Скопійовано в буфер!");

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

        UA_MAP.put("tg.welcome", "👋 <b>Вітаю у Property Searcher 3!</b>\n\nБот миттєво відстежує тільки <b>найновіші органічні оголошення</b> (OLX & DOM.RIA), пропускаючи проплачені ТОП-блоки.\n\nОберіть дію на клавіатурі нижче:");
        UA_MAP.put("tg.menu.search", "🔍 Пошук зараз");
        UA_MAP.put("tg.menu.categories", "📋 Мої категорії");
        UA_MAP.put("tg.menu.status", "📊 Статус системи");
        UA_MAP.put("tg.menu.currency", "💵 Валюта цін");
        UA_MAP.put("tg.menu.stop", "⏸ Зупинити");
        UA_MAP.put("tg.menu.help", "ℹ️ Допомога та Інструкція");
        UA_MAP.put("tg.menu.lang", "🌐 Мова / Language");
        UA_MAP.put("tg.stopped", "⏸ <b>Моніторинг оголошень зупинено.</b>");
        UA_MAP.put("tg.unknown_cmd", "Невідома команда. Оберіть дію на клавіатурі нижче.");
        UA_MAP.put("tg.status_title", "📊 <b>Статус Property Searcher:</b>\n\n");
        UA_MAP.put("tg.currency_title", "💵 <b>Налаштування відображення валюти цін:</b>\n\n");
        UA_MAP.put("tg.currency_changed", "✅ <b>Режим валюти змінено на: %s</b>\nУсі наступні ціни будуть конвертуватися автоматично.");

        UA_MAP.put("card.category", "Категорія");
        UA_MAP.put("card.price", "Ціна");
        UA_MAP.put("card.time", "Час");
        UA_MAP.put("card.source", "Джерело");
        UA_MAP.put("card.open", "Відкрити на");

        // ==========================================
        // --- ENGLISH (EN) ---
        // ==========================================
        EN_MAP.put("app.title", "Property Searcher 3");
        EN_MAP.put("btn.start", "Start");
        EN_MAP.put("btn.stop", "Stop");
        EN_MAP.put("btn.settings", "Settings");
        EN_MAP.put("btn.categories", "Search Categories");
        EN_MAP.put("btn.builder", "Category Builder");
        EN_MAP.put("btn.instruction", "Manual & BotFather");
        EN_MAP.put("btn.lang", "Language (Мова)");

        EN_MAP.put("gui.token_btn", "Token & Bot Name");
        EN_MAP.put("gui.category_btn", "Search Categories");
        EN_MAP.put("gui.instruction_btn", "Manual & BotFather");
        EN_MAP.put("gui.lang_btn", "🌐 Language / Мова (UA / EN)");
        EN_MAP.put("gui.back_btn", "Back");
        EN_MAP.put("gui.save_btn", "Save");
        EN_MAP.put("gui.bot_name_lbl", "Bot Name:");
        EN_MAP.put("gui.token_lbl", "Token:");
        EN_MAP.put("gui.token_dialog_title", "Enter Bot Token & Name");
        EN_MAP.put("gui.settings_dialog_title", "Settings");
        EN_MAP.put("gui.token_len_err", "Token must contain at least 30 characters!");
        EN_MAP.put("gui.token_required_err", "Please enter bot token and name before starting!");
        EN_MAP.put("gui.categories_required_err", "Please add at least one search category before starting!");
        EN_MAP.put("gui.bot_running_err", "Bot is already running!");
        EN_MAP.put("gui.launch_failed", "Failed to launch bot: ");

        EN_MAP.put("dialog.warning_title", "Warning");

        EN_MAP.put("cat.dialog_title", "Manage Search Categories (OLX & DOM.RIA)");
        EN_MAP.put("cat.header_title", "Real Estate Monitoring Categories");
        EN_MAP.put("cat.smart_builder_btn", "Category Builder");
        EN_MAP.put("cat.manual_add_btn", "Add URL Manually");
        EN_MAP.put("cat.toggle_btn", "ON / PAUSE");
        EN_MAP.put("cat.edit_btn", "Edit");
        EN_MAP.put("cat.remove_btn", "Delete");
        EN_MAP.put("cat.close_btn", "Close");
        EN_MAP.put("cat.badge_on", " 🟢 ON ");
        EN_MAP.put("cat.badge_pause", " ⏸ PAUSE ");
        EN_MAP.put("cat.badge_owner", " Owner ");
        EN_MAP.put("cat.total", "Total");
        EN_MAP.put("cat.active", "active");
        EN_MAP.put("cat.select_warning", "Please select a category from the list!");
        EN_MAP.put("cat.select_active_warning", "Please select a category from the list to toggle status!");
        EN_MAP.put("cat.select_delete_warning", "Please select a category from the list to delete!");
        EN_MAP.put("cat.select_edit_warning", "Please select a category from the list to edit!");
        EN_MAP.put("cat.edit_name_prompt", "Change category name:");
        EN_MAP.put("cat.edit_url_prompt", "Change category URL:");

        EN_MAP.put("manual.dialog_title", "Manual Search URL Addition");
        EN_MAP.put("manual.name_lbl", "Category Name (e.g. Apartments Odesa Buy):");
        EN_MAP.put("manual.url_lbl", "Direct Search URL (OLX or DOM.RIA):");
        EN_MAP.put("manual.add_btn", "Add");
        EN_MAP.put("manual.cancel_btn", "Cancel");
        EN_MAP.put("manual.enter_warning", "Please enter category name and URL!");

        EN_MAP.put("builder.dialog_title", "Real Estate Category Builder (OLX & DOM.RIA)");
        EN_MAP.put("builder.platform_lbl", "Platform:");
        EN_MAP.put("builder.city_lbl", "City / Region:");
        EN_MAP.put("builder.type_lbl", "Property Type:");
        EN_MAP.put("builder.deal_lbl", "Deal Type:");
        EN_MAP.put("builder.price_min_lbl", "Price From (UAH):");
        EN_MAP.put("builder.price_max_lbl", "Price To (UAH):");
        EN_MAP.put("builder.owner_chk", "Owner only (without agency / direct)");
        EN_MAP.put("builder.custom_title_lbl", "Custom Name (optional):");
        EN_MAP.put("builder.preview_url_lbl", "Generated Target URL:");
        EN_MAP.put("builder.add_btn", "Add Category");
        EN_MAP.put("builder.cancel_btn", "Cancel");
        EN_MAP.put("builder.enter_warning", "Please enter category name!");
        EN_MAP.put("builder.price_from", "From");
        EN_MAP.put("builder.price_to", "To");
        EN_MAP.put("builder.direct_owner", "100% Direct / Owner Only (No Agencies)");
        EN_MAP.put("builder.owner_suffix", " [Owner]");

        EN_MAP.put("ins.dialog_title", "Manual & BotFather Setup");
        EN_MAP.put("ins.tab_botfather", "BotFather (Menu Commands)");
        EN_MAP.put("ins.tab_quickstart", "Quick Start & Bot");
        EN_MAP.put("ins.tab_filters", "Filters & Architecture");
        EN_MAP.put("ins.copy_btn", "📋 Copy Commands for BotFather");
        EN_MAP.put("ins.copied_notice", "Commands copied to clipboard successfully!\n\nNow send them to @BotFather after the /setcommands command.");
        EN_MAP.put("ins.copied_title", "Copied Successfully");
        EN_MAP.put("ins.close_btn", "Close");
        EN_MAP.put("ins.botfather_step_title", "Configuring bot menu and commands in Telegram:");
        EN_MAP.put("ins.botfather_step_desc", "1. Open official <b>@BotFather</b> in Telegram.<br>2. Send <code>/setcommands</code> and select your bot.<br>3. Click the button below and paste the copied commands into the @BotFather chat:");
        EN_MAP.put("ins.botfather_commands", "start - Main menu and instant start\nsearch - Start listing monitoring\ntargets - Manage active categories\ncurrency - Price currency (USD/UAH)\nstop - Pause monitoring\nhelp - Guide and documentation");
        EN_MAP.put("ins.copied_button_text", "Copied to clipboard!");

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
        EN_MAP.put("tg.stopped", "⏸ <b>Listing monitoring paused.</b>");
        EN_MAP.put("tg.unknown_cmd", "Unknown command. Please select an action from the keyboard below.");
        EN_MAP.put("tg.status_title", "📊 <b>Property Searcher Status:</b>\n\n");
        EN_MAP.put("tg.currency_title", "💵 <b>Price Currency Display Settings:</b>\n\n");
        EN_MAP.put("tg.currency_changed", "✅ <b>Currency mode changed to: %s</b>\nAll subsequent prices will be converted automatically.");

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
        String fallback = UA_MAP.get(key);
        return (fallback != null) ? fallback : key;
    }

    public static String t(String key) {
        return get(key);
    }

    public static String get(String key, Language lang) {
        Map<String, String> map = (lang == Language.EN) ? EN_MAP : UA_MAP;
        String val = map.get(key);
        if (val != null) return val;
        String fallback = UA_MAP.get(key);
        return (fallback != null) ? fallback : key;
    }
}
