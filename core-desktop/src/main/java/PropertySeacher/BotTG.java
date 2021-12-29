package PropertySeacher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class BotTG extends TelegramLongPollingBot {
    private String IDC;
    private static final Logger log = LoggerFactory.getLogger(BotTG.class);

    // Adaptive Rate Limiting parameters:
    // SAFE_GLOBAL_RPS: Safe continuous request rate across all categories to prevent CloudFront / DataDome IP blocks
    public static final double TARGET_SAFE_GLOBAL_RPS = 2.2;
    // MIN_THREAD_DELAY_MS: Minimum interval per category thread for sub-2-second sniping
    public static final int MIN_THREAD_DELAY_MS = 1800;

    private static final ExecutorService notificationExecutor = Executors.newCachedThreadPool();

    private final String botName;
    private final String botToken;
    private List<SettingsManager.CategoryEntry> memoryCategories = null;
    private final List<Algoritm> runningAlgorithms = new ArrayList<>();

    public BotTG(String botName, String botToken) {
        this.botName = botName;
        this.botToken = botToken;
        loadLastChatId();
    }

    public BotTG(String botName, String botToken, List<SettingsManager.CategoryEntry> categories) {
        this.botName = botName;
        this.botToken = botToken;
        this.memoryCategories = categories;
        loadLastChatId();
    }

    public void updateCategoriesInMemory(List<SettingsManager.CategoryEntry> updated) {
        this.memoryCategories = updated;
        if (IDC != null && !runningAlgorithms.isEmpty()) {
            startSearch(IDC);
        }
    }

    private void saveLastChatId(String chatId) {
        this.IDC = chatId;
        SettingsManager.saveLastChatId(chatId);
    }

    private void loadLastChatId() {
        String id = SettingsManager.getLastChatId();
        if (id != null && !id.trim().isEmpty()) {
            this.IDC = id.trim();
        }
    }

    public void triggerStartFromGui() {
        if (IDC != null && !IDC.trim().isEmpty()) {
            startSearch(IDC);
        }
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String chatId = message.getChatId().toString();
            saveLastChatId(chatId);

            String text = message.getText().trim();

            if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("старт")) {
                sendWelcomeMessage(chatId);
                // Immediately start monitoring and send latest listings on /start
                startSearch(chatId);
            } else if (text.equalsIgnoreCase("/search") || text.contains("Пошук зараз")) {
                startSearch(chatId);
            } else if (text.equalsIgnoreCase("/targets") || text.contains("Мої категорії") || text.contains("Категорії")) {
                showCategories(chatId);
            } else if (text.contains("Статус") || text.contains("Status")) {
                showStatus(chatId);
            } else if (text.equalsIgnoreCase("/currency") || text.contains("Валюта") || text.contains("Currency")) {
                showCurrencyMenu(chatId);
            } else if (text.equalsIgnoreCase("/language") || text.equalsIgnoreCase("/lang") || text.contains("Мова") || text.contains("Language")) {
                showLanguageMenu(chatId);
            } else if (text.equalsIgnoreCase("/stop") || text.contains("Зупинити") || text.contains("Stop")) {
                stopSearch();
            } else if (text.equalsIgnoreCase("/help") || text.contains("Інструкція") || text.contains("Допомога") || text.contains("Help")) {
                sendHelpMessage(chatId);
            } else if (text.equalsIgnoreCase("/botfather") || text.contains("BotFather")) {
                sendBotFatherHelp(chatId);
            } else {
                sendUnknownCommandMessage(chatId);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        String chatId = callbackQuery.getMessage().getChatId().toString();
        saveLastChatId(chatId);

        if ("cmd_search".equals(data)) {
            startSearch(chatId);
        } else if ("cmd_stop".equals(data)) {
            stopSearch();
        } else if ("cmd_targets".equals(data)) {
            showCategories(chatId);
        } else if ("cmd_status".equals(data)) {
            showStatus(chatId);
        } else if ("cmd_currency".equals(data)) {
            showCurrencyMenu(chatId);
        } else if ("cmd_language".equals(data)) {
            showLanguageMenu(chatId);
        } else if (data != null && data.startsWith("set_lang_")) {
            String code = data.substring(9).toUpperCase();
            I18n.Language lang = I18n.Language.fromString(code);
            SettingsManager.setLanguage(lang);
            sendLanguageChangedMessage(chatId, lang);
        } else if (data != null && data.startsWith("set_curr_")) {
            String modeStr = data.substring(9).toUpperCase();
            try {
                CurrencyService.CurrencyMode mode = CurrencyService.CurrencyMode.valueOf(modeStr);
                SettingsManager.setCurrencyPreference(mode);
                sendCurrencyChangedMessage(chatId, mode);
            } catch (Exception ignored) {}
        } else if ("cmd_help".equals(data)) {
            sendHelpMessage(chatId);
        }
    }

    private void showLanguageMenu(String chatId) {
        I18n.Language current = SettingsManager.getLanguage();
        String text = (current == I18n.Language.EN)
                ? "🌐 <b>Language Settings:</b>\n\nCurrent language: <b>English 🇬🇧</b>\nChoose your preferred interface language:"
                : "🌐 <b>Налаштування мови інтерфейсу:</b>\n\nПоточна мова: <b>Українська 🇺🇦</b>\nОберіть бажану мову інтерфейсу:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton ua = new InlineKeyboardButton();
        ua.setText((current == I18n.Language.UA ? "✅ " : "") + "🇺🇦 Українська");
        ua.setCallbackData("set_lang_ua");

        InlineKeyboardButton en = new InlineKeyboardButton();
        en.setText((current == I18n.Language.EN ? "✅ " : "") + "🇬🇧 English");
        en.setCallbackData("set_lang_en");

        row.add(ua);
        row.add(en);
        rows.add(row);
        markup.setKeyboard(rows);

        SendMessage sm = new SendMessage(chatId, text);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void sendLanguageChangedMessage(String chatId, I18n.Language lang) {
        String msg = (lang == I18n.Language.EN)
                ? "✅ <b>Language set to English!</b>\nAll subsequent notifications and commands will be in English."
                : "✅ <b>Мову перемкнено на Українську!</b>\nУсі наступні сповіщення та команди будуть українською.";
        SendMessage sm = new SendMessage(chatId, msg);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void showCurrencyMenu(String chatId) {
        CurrencyService.CurrencyMode current = SettingsManager.getCurrencyPreference();
        double usd = CurrencyService.getUsdRate();
        double eur = CurrencyService.getEurRate();

        String text = "💵 <b>Налаштування відображення валюти цін:</b>\n\n" +
                "• <b>Поточний режим:</b> <code>" + current.name() + "</code>\n" +
                "• <b>Курс НБУ (кеш):</b> 1 USD = " + usd + " грн | 1 EUR = " + eur + " грн\n\n" +
                "Оберіть бажаний формат відображення цін в оголошеннях:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> r1 = new ArrayList<>();
        InlineKeyboardButton btnDual = new InlineKeyboardButton();
        btnDual.setText((current == CurrencyService.CurrencyMode.DUAL ? "✅ " : "") + "🔄 Подвійна (USD + UAH)");
        btnDual.setCallbackData("set_curr_dual");
        r1.add(btnDual);

        List<InlineKeyboardButton> r2 = new ArrayList<>();
        InlineKeyboardButton btnUsd = new InlineKeyboardButton();
        btnUsd.setText((current == CurrencyService.CurrencyMode.USD ? "✅ " : "") + "🇺🇸 Тільки USD ($)");
        btnUsd.setCallbackData("set_curr_usd");
        InlineKeyboardButton btnUah = new InlineKeyboardButton();
        btnUah.setText((current == CurrencyService.CurrencyMode.UAH ? "✅ " : "") + "🇺🇦 Тільки UAH (грн)");
        btnUah.setCallbackData("set_curr_uah");
        r2.add(btnUsd);
        r2.add(btnUah);

        List<InlineKeyboardButton> r3 = new ArrayList<>();
        InlineKeyboardButton btnEur = new InlineKeyboardButton();
        btnEur.setText((current == CurrencyService.CurrencyMode.EUR ? "✅ " : "") + "🇪🇺 Тільки EUR (€)");
        btnEur.setCallbackData("set_curr_eur");
        InlineKeyboardButton btnOrig = new InlineKeyboardButton();
        btnOrig.setText((current == CurrencyService.CurrencyMode.ORIGINAL ? "✅ " : "") + "🌐 Як на сайті");
        btnOrig.setCallbackData("set_curr_original");
        r3.add(btnEur);
        r3.add(btnOrig);

        rows.add(r1);
        rows.add(r2);
        rows.add(r3);
        markup.setKeyboard(rows);

        SendMessage sm = new SendMessage(chatId, text);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void sendCurrencyChangedMessage(String chatId, CurrencyService.CurrencyMode mode) {
        String desc = "";
        if (mode != null) {
            switch (mode) {
                case DUAL:
                    desc = "🔄 Подвійна: показує ціну з сайту та автоматичний еквівалент (USD + UAH)";
                    break;
                case USD:
                    desc = "🇺🇸 USD: усі ціни конвертуються та наголошуються в доларах ($)";
                    break;
                case UAH:
                    desc = "🇺🇦 UAH: усі ціни конвертуються в гривні (грн)";
                    break;
                case EUR:
                    desc = "🇪🇺 EUR: ціни відображаються в євро (€)";
                    break;
                case ORIGINAL:
                default:
                    desc = "🌐 Як на сайті: без конвертації";
                    break;
            }
        }

        String msg = "✅ <b>Формат валюти оновлено:</b> <code>" + mode.name() + "</code>\n" +
                desc + "\n\n" +
                "Усі наступні надходження розраховуватимуться в цьому режимі.";
        SendMessage sm = new SendMessage(chatId, msg);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private static boolean isNightTime() {
        int hour = java.time.LocalTime.now(java.time.ZoneId.of("Europe/Kyiv")).getHour();
        return (hour >= 23 || hour < 7);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private List<SettingsManager.CategoryEntry> getCurrentCategories() {
        if (memoryCategories != null && !memoryCategories.isEmpty()) {
            return memoryCategories;
        }
        return SettingsManager.loadCategories();
    }

    private void startSearch(String chatId) {
        IDC = chatId;
        stopSearchThreads();

        List<SettingsManager.CategoryEntry> allCategories = getCurrentCategories();
        if (allCategories.isEmpty()) {
            SendMessage smEmpty = new SendMessage(chatId, "⚠️ <b>Немає категорій для пошуку!</b>\nДодайте категорію в додатку через 'Конструктор категорій'.");
            smEmpty.setParseMode("HTML");
            smEmpty.setReplyMarkup(createPersistentMenu());
            try { execute(smEmpty); } catch (Exception ignored) {}
            return;
        }

        List<SettingsManager.CategoryEntry> activeCategories = new ArrayList<>();
        for (SettingsManager.CategoryEntry ce : allCategories) {
            if (ce.active) {
                activeCategories.add(ce);
            }
        }

        if (activeCategories.isEmpty()) {
            SendMessage smEmpty = new SendMessage(chatId, "⚠️ <b>Всі категорії наразі вимкнені (на паузі)!</b>\nУвімкніть потрібні категорії в меню категорій програми.");
            smEmpty.setParseMode("HTML");
            smEmpty.setReplyMarkup(createPersistentMenu());
            try { execute(smEmpty); } catch (Exception ignored) {}
            return;
        }

        int activeCount = activeCategories.size();
        int delayMs = calculateAdaptiveDelay(activeCount);
        double delaySec = delayMs / 1000.0;
        int staggerStep = (activeCount > 1) ? (delayMs / activeCount) : 0;

        SendMessage sm0 = new SendMessage(chatId, "🔍 <b>Моніторинг запущено!</b>\n" +
                "• <b>Активних категорій:</b> " + activeCount + "\n" +
                "• <b>Адаптивний темп:</b> " + String.format(java.util.Locale.US, "%.1f", delaySec) + " с / категорію (CloudFront Safe 🛡)\n\n" +
                "⚡ <i>Відстежую появу нових публікацій у реальному часі...</i>");
        sm0.setParseMode("HTML");
        sm0.setReplyMarkup(createPersistentMenu());
        try {
            execute(sm0);
            System.out.println("🚀 Search started for chat " + chatId + " with " + activeCount + " active categories (delay: " + delayMs + "ms, stagger: " + staggerStep + "ms)");
        } catch (TelegramApiException e) {
            System.err.println("❌ Failed to send start search message: " + e.getMessage());
            log.error("Failed to start search message: {}", e.getMessage());
        }

        for (int i = 0; i < activeCategories.size(); i++) {
            SettingsManager.CategoryEntry category = activeCategories.get(i);
            int initialStagger = i * staggerStep;
            Algoritm algoritm = new Algoritm(category.url, chatId, category.name, delayMs, initialStagger);
            runningAlgorithms.add(algoritm);
            algoritm.start();
        }
    }

    public static int calculateAdaptiveDelay(int activeCount) {
        if (activeCount <= 1) {
            return MIN_THREAD_DELAY_MS;
        }
        long computed = Math.round((activeCount * 1000.0) / TARGET_SAFE_GLOBAL_RPS);
        return (int) Math.max(MIN_THREAD_DELAY_MS, computed);
    }

    private synchronized void stopSearchThreads() {
        for (Algoritm a : runningAlgorithms) {
            a.stopAlgoritm();
        }
        runningAlgorithms.clear();
    }

    private void sendWelcomeMessage(String chatId) {
        String welcome = "👋 <b>Вітаю у Property Searcher (OLX & DOM.RIA)!</b>\n\n" +
                "Бот миттєво відстежує тільки <b>найновіші органічні оголошення</b>, пропускаючи проплачені ТОП-блоки.\n\n" +
                "Оберіть дію на клавіатурі нижче:";

        SendMessage sm = new SendMessage(chatId, welcome);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(createPersistentMenu());

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            log.error("Welcome failed: {}", e.getMessage());
        }
    }

    private void showCategories(String chatId) {
        List<SettingsManager.CategoryEntry> categories = getCurrentCategories();
        if (categories.isEmpty()) {
            SendMessage sm = new SendMessage(chatId, "⚠️ Наразі немає доданих категорій!");
            sm.setParseMode("HTML");
            sm.setReplyMarkup(createPersistentMenu());
            try { execute(sm); } catch (Exception ignored) {}
            return;
        }

        StringBuilder sb = new StringBuilder("📋 <b>Категорії моніторингу:</b>\n\n");
        for (SettingsManager.CategoryEntry c : categories) {
            String plat = c.url.contains("dom.ria") ? "DOM.RIA" : "OLX";
            String status = c.active ? "🟢 ВКЛ" : "⏸ ПАУЗА";
            boolean isOwner = c.url.contains("without_agent") || c.url.contains("private");

            sb.append("• <b>").append(escapeHtml(c.name)).append("</b> [").append(plat).append("]")
              .append(isOwner ? " (Власник)" : "")
              .append(" — <i>").append(status).append("</i>\n");
        }

        SendMessage sm = new SendMessage(chatId, sb.toString());
        sm.setParseMode("HTML");
        sm.setReplyMarkup(createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void showStatus(String chatId) {
        List<SettingsManager.CategoryEntry> all = getCurrentCategories();
        int activeCount = 0;
        for (SettingsManager.CategoryEntry c : all) {
            if (c.active) activeCount++;
        }
        boolean running = !runningAlgorithms.isEmpty();
        int delayMs = calculateAdaptiveDelay(activeCount);
        double delaySec = delayMs / 1000.0;
        double currentRps = activeCount > 0 ? (activeCount / delaySec) : 0.0;

        CurrencyService.CurrencyMode currMode = SettingsManager.getCurrencyPreference();
        boolean night = isNightTime();

        String status = "📊 <b>Статус Property Searcher:</b>\n\n" +
                "• <b>Моніторинг:</b> " + (running ? "🟢 АКТИВНИЙ" : "⏸ ЗУПИНЕНО") + "\n" +
                "• <b>Всього категорій:</b> " + all.size() + " (активних: " + activeCount + ")\n" +
                "• <b>Адаптивний інтервал:</b> " + String.format(java.util.Locale.US, "%.1f", delaySec) + " с на категорію\n" +
                "• <b>Безпека з'єднань:</b> " + String.format(java.util.Locale.US, "%.2f", currentRps) + " req/s (CloudFront Safe 🛡)\n" +
                "• <b>Валюта цін:</b> <code>" + currMode.name() + "</code> (1 $ = " + CurrencyService.getUsdRate() + " ₴ | 1 € = " + CurrencyService.getEurRate() + " ₴)\n" +
                "• <b>Режим сповіщень:</b> " + (night ? "🌙 Нічний (без звуку)" : "☀️ Денний (зі звуком)");

        SendMessage sm = new SendMessage(chatId, status);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void stopSearch() {
        stopSearchThreads();
        if (IDC != null) {
            SendMessage sm3 = new SendMessage(IDC, "⏸ <b>Моніторинг оголошень зупинено.</b>");
            sm3.setParseMode("HTML");
            sm3.setReplyMarkup(createPersistentMenu());
            try {
                execute(sm3);
            } catch (TelegramApiException e) {
                log.error("Stop failed: {}", e.getMessage());
            }
        }
    }

    private void sendHelpMessage(String chatId) {
        String helpMessage = "📋 <b>Список команд:</b>\n\n" +
                "• /start — Головне меню та швидкий запуск\n" +
                "• /search — Розпочати пошук найновіших оголошень\n" +
                "• /targets — Переглянути список категорій\n" +
                "• /currency — Налаштувати валюту цін (DUAL / USD / UAH / EUR)\n" +
                "• /stop — Зупинити моніторинг\n" +
                "• /botfather — Інструкція для @BotFather\n" +
                "• /help — Довідка\n\n" +
                "⚡ <b>Як працює швидкість детекції (OLX & DOM.RIA):</b>\n" +
                "Сучасний OLX використовує кешуючу CDN-мережу (Akamai/Cloudflare). Після публікації оголошення автором воно проходить модерацію сайту і стає доступним на відкритих серверах через <b>30–90 секунд</b>.\n\n" +
                "Property Searcher виявляє його <b>в перші ж 1–2 секунди</b> після фактичної появи у публічному інтернет-просторі та миттєво доставляє повний фотоальбом у ваш Telegram!";

        SendMessage smHelp = new SendMessage(chatId, helpMessage);
        smHelp.setParseMode("HTML");
        smHelp.setReplyMarkup(createPersistentMenu());
        try {
            execute(smHelp);
        } catch (TelegramApiException e) {
            log.error("Help failed: {}", e.getMessage());
        }
    }

    private void sendBotFatherHelp(String chatId) {
        String script = "🛠 <b>Команди для налаштування меню у @BotFather:</b>\n\n" +
                "1. Відкрийте @BotFather у Telegram\n" +
                "2. Надішліть команду <code>/setcommands</code>\n" +
                "3. Оберіть вашого бота\n" +
                "4. Надішліть йому цей список команд:\n\n" +
                "<code>start - Головне меню та запуск</code>\n" +
                "<code>search - Запустити пошук оголошень</code>\n" +
                "<code>targets - Список категорій</code>\n" +
                "<code>currency - Налаштування валюти цін</code>\n" +
                "<code>stop - Зупинити моніторинг</code>\n" +
                "<code>help - Довідка та інструкція</code>";

        SendMessage sm = new SendMessage(chatId, script);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(createPersistentMenu());
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            log.error("Botfather help failed: {}", e.getMessage());
        }
    }

    private void sendUnknownCommandMessage(String chatId) {
        SendMessage sm = new SendMessage(chatId, "Невідома команда. Оберіть дію на клавіатурі нижче.");
        sm.setReplyMarkup(createPersistentMenu());
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            log.error("Unknown command reply failed: {}", e.getMessage());
        }
    }

    private ReplyKeyboardMarkup createPersistentMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        I18n.Language lang = SettingsManager.getLanguage();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow r1 = new KeyboardRow();
        r1.add(I18n.get("tg.menu.search", lang));
        r1.add(I18n.get("tg.menu.categories", lang));

        KeyboardRow r2 = new KeyboardRow();
        r2.add(I18n.get("tg.menu.status", lang));
        r2.add(I18n.get("tg.menu.currency", lang));

        KeyboardRow r3 = new KeyboardRow();
        r3.add(I18n.get("tg.menu.stop", lang));
        r3.add(I18n.get("tg.menu.lang", lang));

        KeyboardRow r4 = new KeyboardRow();
        r4.add(I18n.get("tg.menu.help", lang));

        rows.add(r1);
        rows.add(r2);
        rows.add(r3);
        rows.add(r4);
        markup.setKeyboard(rows);
        return markup;
    }

    public class Algoritm extends Thread {
        private volatile boolean shouldRun = true;
        private final String categoryUrl;
        private final String chatId;
        private final String categoryName;
        private final int baseDelayMs;
        private final int initialStaggerMs;
        private final String[] lastAdLinks = new String[50];
        private String lastSentAdLink = "";

        public Algoritm(String categoryUrl, String chatId, String categoryName, int baseDelayMs, int initialStaggerMs) {
            this.categoryUrl = categoryUrl;
            this.chatId = chatId;
            this.categoryName = categoryName;
            this.baseDelayMs = baseDelayMs;
            this.initialStaggerMs = initialStaggerMs;
        }

        public Algoritm(String categoryUrl, String chatId, String categoryName) {
            this(categoryUrl, chatId, categoryName, 1800, 0);
        }

        @Override
        public void run() {
            // Initial phase stagger offset so threads don't fire simultaneously:
            if (initialStaggerMs > 0) {
                try {
                    Thread.sleep(initialStaggerMs);
                } catch (InterruptedException e) {
                    return;
                }
            }

            // 1. Silent baseline initialization:
            // Buffer up to 30 existing organic ads on the page so NONE of them ever bubble up as "new"!
            initBaseline();

            // 2. Continuous adaptive real-time monitoring loop:
            while (shouldRun) {
                // Apply subtle phase jitter (+/- 150ms) to evade anti-bot metronome detection
                int jitter = ThreadLocalRandom.current().nextInt(-150, 151);
                int sleepTime = Math.max(800, baseDelayMs + jitter);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
                if (!shouldRun) break;

                boolean foundNew = search();

                // Adaptive Burst Check: if a fresh drop was just detected, trigger an instant follow-up
                // micro-check after 500ms to immediately grab simultaneous multi-drops before resuming normal pacing!
                if (foundNew && shouldRun) {
                    try {
                        Thread.sleep(500);
                        search();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }

        public void stopAlgoritm() {
            shouldRun = false;
            this.interrupt();
        }

        private void initBaseline() {
            try {
                ParserOLX ps = new ParserOLX(categoryUrl);
                List<ParserOLX.PropertyResult> initialAds = ps.getTopProperties(30);
                for (ParserOLX.PropertyResult res : initialAds) {
                    if (res != null && res.link != null && !res.link.isBlank()) {
                        String clean = res.link.split("\\?")[0];
                        if (this.lastSentAdLink.isEmpty()) {
                            this.lastSentAdLink = clean;
                        }
                        updateLastAdLinks(clean);
                    }
                }
                System.out.println("📌 [Baseline initialized (SILENT) for " + categoryName + "]: " + initialAds.size() + " active listings buffered");
            } catch (Exception e) {
                System.err.println("⚠️ Could not init baseline for " + categoryName + ": " + e.getMessage());
            }
        }

        public boolean search() {
            try {
                ParserOLX ps = new ParserOLX(categoryUrl);
                // Scan the top 5 organic listings on every pass so multiple simultaneous drops are NEVER missed!
                List<ParserOLX.PropertyResult> candidates = ps.getTopProperties(5);
                if (candidates == null || candidates.isEmpty()) {
                    return false;
                }

                List<ParserOLX.PropertyResult> newAds = new ArrayList<>();
                for (ParserOLX.PropertyResult res : candidates) {
                    if (res != null && res.link != null && !res.link.isBlank()) {
                        String cleanLink = res.link.split("\\?")[0];
                        if (!cleanLink.equals(lastSentAdLink) && !isDuplicate(cleanLink)) {
                            newAds.add(res);
                        }
                    }
                }

                if (newAds.isEmpty()) {
                    return false;
                }

                // Process in chronological order (older candidate to newer candidate in the batch)
                for (int i = newAds.size() - 1; i >= 0; i--) {
                    ParserOLX.PropertyResult res = newAds.get(i);
                    String cleanLink = res.link.split("\\?")[0];
                    lastSentAdLink = cleanLink;
                    updateLastAdLinks(cleanLink);

                    // Asynchronous non-blocking dispatch via worker pool
                    // Allows the monitoring thread to continue immediately without waiting for Telegram network latency!
                    notificationExecutor.submit(() -> dispatchNotification(res, cleanLink));
                }
                return true;
            } catch (Exception e) {
                System.err.println("❌ Search error in " + categoryName + ": " + e.getMessage());
                log.error("Search error in {}: {}", categoryName, e.getMessage());
                return false;
            }
        }

        private void dispatchNotification(ParserOLX.PropertyResult res, String cleanLink) {
            try {
                // Fetch full gallery photos only when a genuine new ad is being sent!
                List<String> allPhotos = new ArrayList<>();
                if (res.photoUrls != null && !res.photoUrls.isEmpty()) {
                    allPhotos.addAll(res.photoUrls);
                } else if (res.photoUrl != null && !res.photoUrl.isBlank()) {
                    allPhotos.add(res.photoUrl);
                }
                if (allPhotos.size() <= 1 && res.link != null) {
                    try {
                        org.jsoup.nodes.Document adDoc = org.jsoup.Jsoup.connect(res.link)
                                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                                .header("Accept-Language", "uk,en;q=0.9")
                                .timeout(5000)
                                .get();
                        org.jsoup.select.Elements galleryImgs = adDoc.select("div.swiper-zoom-container img, div[data-cy=\"ad-photos\"] img, img[data-testid=\"gallery-image\"], div.gallery img");
                        for (org.jsoup.nodes.Element gImg : galleryImgs) {
                            String src = gImg.hasAttr("data-src") ? gImg.attr("data-src") : gImg.attr("src");
                            if (src != null && src.startsWith("http") && !allPhotos.contains(src)) {
                                allPhotos.add(src);
                                if (allPhotos.size() >= 8) break;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                CurrencyService.CurrencyMode currMode = SettingsManager.getCurrencyPreference();
                String formattedPrice = CurrencyService.formatPrice(res.price, currMode);
                boolean isNight = isNightTime();

                String cleanTitle = escapeHtml(res.title);
                String cleanCategory = escapeHtml(categoryName);
                String cleanPrice = escapeHtml(formattedPrice);
                String cleanPlatform = escapeHtml(res.platform);
                String cleanTime = escapeHtml(res.timePublished);

                System.out.println("🔥 [GENUINELY NEW AD DETECTED ON " + cleanPlatform + " | " + cleanCategory + "]: " + cleanTitle + " (" + cleanLink + ") [" + formattedPrice + "]");

                // Concise header: Category name immediately followed by original ad title
                String text = "🏠 <b>" + cleanCategory + ":</b> " + cleanTitle + "\n\n" +
                        (cleanPrice.isEmpty() ? "" : "💰 <b>Ціна:</b> " + cleanPrice + "\n") +
                        (cleanTime.isEmpty() ? "" : "🕒 <b>Час:</b> " + cleanTime + "\n") +
                        "📍 <b>Джерело:</b> " + cleanPlatform + "\n\n" +
                        "🔗 <a href=\"" + res.link + "\">Відкрити на " + cleanPlatform + "</a>";

                InlineKeyboardMarkup cardInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> row1 = new ArrayList<>();

                InlineKeyboardButton openBtn = new InlineKeyboardButton();
                openBtn.setText("🔗 Відкрити на " + cleanPlatform);
                openBtn.setUrl(res.link);
                row1.add(openBtn);
                rows.add(row1);
                cardInline.setKeyboard(rows);

                // Send ALL photos in an album if multiple images available
                if (allPhotos.size() > 1) {
                    try {
                        SendMediaGroup smg = new SendMediaGroup();
                        smg.setChatId(this.chatId);
                        smg.setDisableNotification(isNight);
                        List<InputMedia> mediaList = new ArrayList<>();
                        int count = 0;
                        for (String pUrl : allPhotos) {
                            InputMediaPhoto imp = new InputMediaPhoto(pUrl);
                            if (count == 0) {
                                imp.setCaption(text);
                                imp.setParseMode("HTML");
                            }
                            mediaList.add(imp);
                            count++;
                            if (count >= 10) break; // Telegram max album limit is 10
                        }
                        smg.setMedias(mediaList);
                        BotTG.this.execute(smg);
                        System.out.println("✅ Sent media group (" + mediaList.size() + " photos) to chat " + this.chatId + ": " + cleanTitle);
                        return;
                    } catch (Exception ex) {
                        System.err.println("⚠️ MediaGroup send failed, falling back to single photo: " + ex.getMessage());
                    }
                }

                if (!allPhotos.isEmpty()) {
                    try {
                        SendPhoto photoMsg = new SendPhoto();
                        photoMsg.setChatId(this.chatId);
                        photoMsg.setDisableNotification(isNight);
                        photoMsg.setPhoto(new InputFile(allPhotos.get(0)));
                        photoMsg.setCaption(text);
                        photoMsg.setParseMode("HTML");
                        photoMsg.setReplyMarkup(cardInline);
                        BotTG.this.execute(photoMsg);
                        System.out.println("✅ Sent photo card to chat " + this.chatId + ": " + cleanTitle);
                        return;
                    } catch (Exception ex) {
                        System.err.println("⚠️ Photo send failed, falling back to text: " + ex.getMessage());
                        log.warn("Photo send failed, falling back to text: {}", ex.getMessage());
                    }
                }

                SendMessage sm = new SendMessage();
                sm.setChatId(this.chatId);
                sm.setDisableNotification(isNight);
                sm.setText(text);
                sm.setParseMode("HTML");
                sm.setReplyMarkup(cardInline);
                BotTG.this.execute(sm);
                System.out.println("✅ Sent text card to chat " + this.chatId + ": " + cleanTitle);
            } catch (Exception ex) {
                System.err.println("⚠️ Error dispatching notification: " + ex.getMessage());
            }
        }

        private void updateLastAdLinks(String adLink) {
            for (int i = lastAdLinks.length - 1; i > 0; i--) {
                lastAdLinks[i] = lastAdLinks[i - 1];
            }
            lastAdLinks[0] = adLink;
        }

        private boolean isDuplicate(String adLink) {
            for (String link : lastAdLinks) {
                if (adLink != null && adLink.equals(link)) {
                    return true;
                }
            }
            return false;
        }
    }
}
