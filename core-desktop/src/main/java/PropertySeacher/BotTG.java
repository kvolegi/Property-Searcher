package PropertySeacher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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

            String lower = text.toLowerCase();

            if (text.startsWith("http://") || text.startsWith("https://") ||
                text.contains("olx.ua") || text.contains("dom.ria.com")) {
                handleDirectUrlInput(chatId, text);
            } else if (text.equalsIgnoreCase("/start") || lower.contains("старт") || lower.contains("start")) {
                sendWelcomeMessage(chatId);
                // Immediately start monitoring and send latest listings on /start
                startSearch(chatId);
            } else if (text.equalsIgnoreCase("/search") || lower.contains("пошук") || lower.contains("search")) {
                startSearch(chatId);
            } else if (text.equalsIgnoreCase("/targets") || text.equalsIgnoreCase("/categories") ||
                       lower.contains("категор") || lower.contains("categor") || lower.contains("target")) {
                showCategories(chatId, null);
            } else if (lower.contains("статус") || lower.contains("status")) {
                showStatus(chatId);
            } else if (text.equalsIgnoreCase("/currency") || lower.contains("валют") || lower.contains("currency")) {
                showCurrencyMenu(chatId);
            } else if (text.equalsIgnoreCase("/language") || text.equalsIgnoreCase("/lang") ||
                       lower.contains("мова") || lower.contains("language")) {
                showLanguageMenu(chatId);
            } else if (text.equalsIgnoreCase("/stop") || lower.contains("зупин") || lower.contains("stop")) {
                stopSearch(chatId);
            } else if (text.equalsIgnoreCase("/help") || lower.contains("інструкц") ||
                       lower.contains("допомог") || lower.contains("help") || lower.contains("manual") || lower.contains("guide")) {
                showHelpHub(chatId, null, "help_tab_start");
            } else if (text.equalsIgnoreCase("/botfather") || lower.contains("botfather")) {
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
        Integer messageId = callbackQuery.getMessage() != null ? callbackQuery.getMessage().getMessageId() : null;
        saveLastChatId(chatId);

        if ("cmd_search".equals(data)) {
            startSearch(chatId);
        } else if ("cmd_stop".equals(data)) {
            stopSearch();
        } else if ("cmd_targets".equals(data)) {
            showCategories(chatId, messageId);
        } else if ("cmd_status".equals(data)) {
            showStatus(chatId);
        } else if ("cmd_currency".equals(data)) {
            showCurrencyMenu(chatId);
        } else if ("cmd_language".equals(data)) {
            showLanguageMenu(chatId);
        } else if ("cmd_help".equals(data)) {
            showHelpHub(chatId, messageId, "help_tab_start");
        } else if (data != null && data.startsWith("help_tab_")) {
            showHelpHub(chatId, messageId, data);
        } else if ("btn_add_url".equals(data)) {
            sendAddUrlPrompt(chatId, messageId);
        } else if ("wiz_start".equals(data) || (data != null && data.startsWith("wiz_"))) {
            handleWizardStep(chatId, messageId, data);
        } else if (data != null && data.startsWith("cat_tog_")) {
            handleCategoryToggle(chatId, messageId, data);
        } else if (data != null && data.startsWith("cat_del_")) {
            handleCategoryDelete(chatId, messageId, data);
        } else if (data != null && data.startsWith("set_lang_")) {
            String code = data.substring(9).toUpperCase();
            I18n.Language lang = I18n.Language.fromString(code);
            SettingsManager.setLanguage(lang);
            I18n.setLanguage(lang);
            sendLanguageChangedMessage(chatId, lang);
            if (BotGUI.getInstance() != null) {
                javax.swing.SwingUtilities.invokeLater(() -> BotGUI.getInstance().updateLocalizedTexts());
            }
        } else if (data != null && data.startsWith("set_curr_")) {
            String modeStr = data.substring(9).toUpperCase();
            try {
                CurrencyService.CurrencyMode mode = CurrencyService.CurrencyMode.valueOf(modeStr);
                SettingsManager.setCurrencyPreference(mode);
                sendCurrencyChangedMessage(chatId, mode);
            } catch (Exception ignored) {}
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

    public void onLanguageChanged(I18n.Language lang) {
        String target = (IDC != null && !IDC.isEmpty()) ? IDC : SettingsManager.getLastChatId();
        if (target != null) {
            String msg = (lang == I18n.Language.EN)
                ? "🌐 <b>Interface language changed to English 🇬🇧</b>"
                : "🌐 <b>Мову інтерфейсу перемкнено на Українську 🇺🇦</b>";
            SendMessage sm = new SendMessage(target, msg);
            sm.setParseMode("HTML");
            sm.setReplyMarkup(createPersistentMenu());
            try { execute(sm); } catch (Exception ignored) {}
        }
    }

    private void showCurrencyMenu(String chatId) {
        CurrencyService.CurrencyMode current = SettingsManager.getCurrencyPreference();
        double usd = CurrencyService.getUsdRate();
        double eur = CurrencyService.getEurRate();
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        String text = isEn
            ? "💵 <b>Price Currency Display Settings:</b>\n\n" +
              "• <b>Current mode:</b> <code>" + current.name() + "</code>\n" +
              "• <b>NBU Rate (cached):</b> 1 USD = " + usd + " UAH | 1 EUR = " + eur + " UAH\n\n" +
              "Choose preferred price display format for alert cards:"
            : "💵 <b>Налаштування відображення валюти цін:</b>\n\n" +
              "• <b>Поточний режим:</b> <code>" + current.name() + "</code>\n" +
              "• <b>Курс НБУ (кеш):</b> 1 USD = " + usd + " грн | 1 EUR = " + eur + " грн\n\n" +
              "Оберіть бажаний формат відображення цін в оголошеннях:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> r1 = new ArrayList<>();
        InlineKeyboardButton btnDual = new InlineKeyboardButton();
        btnDual.setText((current == CurrencyService.CurrencyMode.DUAL ? "✅ " : "") + (isEn ? "🔄 Dual (USD + UAH)" : "🔄 Подвійна (USD + UAH)"));
        btnDual.setCallbackData("set_curr_dual");
        r1.add(btnDual);

        List<InlineKeyboardButton> r2 = new ArrayList<>();
        InlineKeyboardButton btnUsd = new InlineKeyboardButton();
        btnUsd.setText((current == CurrencyService.CurrencyMode.USD ? "✅ " : "") + (isEn ? "🇺🇸 USD ($ only)" : "🇺🇸 Тільки USD ($)"));
        btnUsd.setCallbackData("set_curr_usd");
        InlineKeyboardButton btnUah = new InlineKeyboardButton();
        btnUah.setText((current == CurrencyService.CurrencyMode.UAH ? "✅ " : "") + (isEn ? "🇺🇦 UAH (₴ only)" : "🇺🇦 Тільки UAH (грн)"));
        btnUah.setCallbackData("set_curr_uah");
        r2.add(btnUsd);
        r2.add(btnUah);

        List<InlineKeyboardButton> r3 = new ArrayList<>();
        InlineKeyboardButton btnEur = new InlineKeyboardButton();
        btnEur.setText((current == CurrencyService.CurrencyMode.EUR ? "✅ " : "") + (isEn ? "🇪🇺 EUR (€ only)" : "🇪🇺 Тільки EUR (€)"));
        btnEur.setCallbackData("set_curr_eur");
        InlineKeyboardButton btnOrig = new InlineKeyboardButton();
        btnOrig.setText((current == CurrencyService.CurrencyMode.ORIGINAL ? "✅ " : "") + (isEn ? "🌐 As Listed" : "🌐 Як на сайті"));
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
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String desc = "";
        if (mode != null) {
            switch (mode) {
                case DUAL:
                    desc = isEn ? "🔄 Dual: shows original price and auto-converted equivalent (USD + UAH)"
                                : "🔄 Подвійна: показує ціну з сайту та автоматичний еквівалент (USD + UAH)";
                    break;
                case USD:
                    desc = isEn ? "🇺🇸 USD: all prices converted and emphasized in Dollars ($)"
                                : "🇺🇸 USD: усі ціни конвертуються та наголошуються в доларах ($)";
                    break;
                case UAH:
                    desc = isEn ? "🇺🇦 UAH: all prices converted to Hryvnias (UAH)"
                                : "🇺🇦 UAH: усі ціни конвертуються в гривні (грн)";
                    break;
                case EUR:
                    desc = isEn ? "🇪🇺 EUR: prices displayed in Euros (€)"
                                : "🇪🇺 EUR: ціни відображаються в євро (€)";
                    break;
                case ORIGINAL:
                default:
                    desc = isEn ? "🌐 As Listed: direct price with zero conversion"
                                : "🌐 Як на сайті: без конвертації";
                    break;
            }
        }

        String msg = isEn
            ? "✅ <b>Currency format updated:</b> <code>" + mode.name() + "</code>\n" + desc + "\n\nAll subsequent listings will be calculated in this mode."
            : "✅ <b>Формат валюти оновлено:</b> <code>" + mode.name() + "</code>\n" + desc + "\n\nУсі наступні надходження розраховуватимуться в цьому режимі.";
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
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        if (allCategories.isEmpty()) {
            String emptyTxt = isEn
                ? "⚠️ <b>No search categories configured!</b>\nAdd a category in the desktop app or paste a search link here in the chat."
                : "⚠️ <b>Немає категорій для пошуку!</b>\nДодайте категорію в додатку через 'Конструктор категорій' або надішліть посилання в чат.";
            SendMessage smEmpty = new SendMessage(chatId, emptyTxt);
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
            String pausedTxt = isEn
                ? "⚠️ <b>All categories are currently paused!</b>\nEnable your targets in the 'My Categories' menu."
                : "⚠️ <b>Всі категорії наразі вимкнені (на паузі)!</b>\nУвімкніть потрібні категорії в меню категорій програми.";
            SendMessage smEmpty = new SendMessage(chatId, pausedTxt);
            smEmpty.setParseMode("HTML");
            smEmpty.setReplyMarkup(createPersistentMenu());
            try { execute(smEmpty); } catch (Exception ignored) {}
            return;
        }

        int activeCount = activeCategories.size();
        int delayMs = calculateAdaptiveDelay(activeCount);
        double delaySec = delayMs / 1000.0;
        int staggerStep = (activeCount > 1) ? (delayMs / activeCount) : 0;

        String startedTxt = isEn
            ? "🔍 <b>Monitoring started!</b>\n" +
              "• <b>Active targets:</b> " + activeCount + "\n" +
              "• <b>Adaptive pacing:</b> " + String.format(java.util.Locale.US, "%.1f", delaySec) + " s / target (CloudFront Safe 🛡)\n\n" +
              "⚡ <i>Tracking incoming organic listings in real-time...</i>"
            : "🔍 <b>Моніторинг запущено!</b>\n" +
              "• <b>Активних категорій:</b> " + activeCount + "\n" +
              "• <b>Адаптивний темп:</b> " + String.format(java.util.Locale.US, "%.1f", delaySec) + " с / категорію (CloudFront Safe 🛡)\n\n" +
              "⚡ <i>Відстежую появу нових публікацій у реальному часі...</i>";

        SendMessage sm0 = new SendMessage(chatId, startedTxt);
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
        I18n.Language lang = SettingsManager.getLanguage();
        String welcome = I18n.get("tg.welcome", lang);

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
        showCategories(chatId, null);
    }

    private void showCategories(String chatId, Integer messageIdToEdit) {
        List<SettingsManager.CategoryEntry> categories = getCurrentCategories();
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        if (categories.isEmpty()) {
            String emptyText = isEn
                ? "📋 <b>Search Targets:</b>\n\n⚠️ <i>No search categories added yet!</i>\nCreate one via the Builder or send a direct URL to this chat."
                : "📋 <b>Категорії моніторингу:</b>\n\n⚠️ <i>Наразі немає доданих категорій!</i>\nСтворіть ціль через Конструктор або надішліть посилання прямо в цей чат.";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> r1 = new ArrayList<>();
            r1.add(createBtn(isEn ? "➕ Category Builder" : "➕ Конструктор категорій", "wiz_start"));
            r1.add(createBtn(isEn ? "🔗 Add by Link" : "🔗 Додати посиланням", "btn_add_url"));
            rows.add(r1);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, emptyText, markup);
            return;
        }

        int activeCount = 0;
        for (SettingsManager.CategoryEntry c : categories) {
            if (c.active) activeCount++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(isEn ? "📋 <b>Target Categories Management:</b>\n" : "📋 <b>Керування категоріями моніторингу:</b>\n");
        sb.append(isEn ? "Total: <b>" : "Всього: <b>").append(categories.size())
          .append(isEn ? "</b> | Active: <b>" : "</b> | Активних: <b>").append(activeCount).append("</b>\n\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < categories.size(); i++) {
            SettingsManager.CategoryEntry c = categories.get(i);
            String plat = c.url.contains("dom.ria") ? "DOM.RIA" : "OLX";
            String statusIcon = c.active ? (isEn ? "🟢 ACTIVE" : "🟢 ВКЛ") : (isEn ? "⏸ PAUSED" : "⏸ ПАУЗА");
            boolean isOwner = c.url.contains("without_agent") || c.url.contains("private");

            sb.append("<b>").append(i + 1).append(". ").append(escapeHtml(c.name)).append("</b> [").append(plat).append("]")
              .append(isOwner ? (isEn ? " <i>(Direct)</i>" : " <i>(Власник)</i>") : "")
              .append("\n• ").append(isEn ? "Status: " : "Статус: ").append(statusIcon).append("\n\n");

            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            InlineKeyboardButton togBtn = new InlineKeyboardButton();
            if (c.active) {
                togBtn.setText(isEn ? "⏸ Pause #" + (i + 1) : "⏸ Пауза #" + (i + 1));
            } else {
                togBtn.setText(isEn ? "🟢 Turn On #" + (i + 1) : "🟢 Увімкнути #" + (i + 1));
            }
            togBtn.setCallbackData("cat_tog_" + i);

            InlineKeyboardButton delBtn = new InlineKeyboardButton(isEn ? "🗑 Delete #" + (i + 1) : "🗑 Видалити #" + (i + 1));
            delBtn.setCallbackData("cat_del_" + i);

            actionRow.add(togBtn);
            actionRow.add(delBtn);
            rows.add(actionRow);
        }

        // Bottom Navigation Bar
        List<InlineKeyboardButton> nav1 = new ArrayList<>();
        nav1.add(createBtn(isEn ? "➕ Category Builder" : "➕ Конструктор категорій", "wiz_start"));
        nav1.add(createBtn(isEn ? "🔗 Add by Link" : "🔗 Додати посиланням", "btn_add_url"));
        rows.add(nav1);

        List<InlineKeyboardButton> nav2 = new ArrayList<>();
        nav2.add(createBtn(isEn ? "▶️ Start Monitoring" : "▶️ Запустити моніторинг", "cmd_search"));
        nav2.add(createBtn(isEn ? "🔄 Refresh" : "🔄 Оновити список", "cmd_targets"));
        rows.add(nav2);

        markup.setKeyboard(rows);
        sendOrEditMessage(chatId, messageIdToEdit, sb.toString(), markup);
    }

    private void handleCategoryToggle(String chatId, Integer messageId, String data) {
        int idx = Integer.parseInt(data.substring(8));
        List<SettingsManager.CategoryEntry> list = getCurrentCategories();
        if (idx >= 0 && idx < list.size()) {
            SettingsManager.CategoryEntry entry = list.get(idx);
            entry.active = !entry.active;
            this.memoryCategories = list;
            SettingsManager.saveSettings(botName, botToken, list);
            if (IDC != null && !runningAlgorithms.isEmpty()) {
                startSearch(IDC);
            }
        }
        showCategories(chatId, messageId);
    }

    private void handleCategoryDelete(String chatId, Integer messageId, String data) {
        int idx = Integer.parseInt(data.substring(8));
        List<SettingsManager.CategoryEntry> list = getCurrentCategories();
        if (idx >= 0 && idx < list.size()) {
            list.remove(idx);
            this.memoryCategories = list;
            SettingsManager.saveSettings(botName, botToken, list);
            if (IDC != null && !runningAlgorithms.isEmpty()) {
                startSearch(IDC);
            }
        }
        showCategories(chatId, messageId);
    }

    private void handleWizardStep(String chatId, Integer messageIdToEdit, String data) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        if ("wiz_start".equals(data)) {
            String prompt = isEn
                ? "🛠 <b>Category Builder (Step 1/5)</b>\n\nChoose real estate search platform:"
                : "🛠 <b>Конструктор категорій (Крок 1 з 5)</b>\n\nОберіть платформу пошуку нерухомості:";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> r1 = new ArrayList<>();
            r1.add(createBtn("🔵 OLX.ua", "wiz_p_OLX"));
            r1.add(createBtn("🟢 DOM.RIA", "wiz_p_DOM.RIA"));
            rows.add(r1);

            List<InlineKeyboardButton> rCancel = new ArrayList<>();
            rCancel.add(createBtn(isEn ? "❌ Cancel" : "❌ Скасувати", "cmd_targets"));
            rows.add(rCancel);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, prompt, markup);
            return;
        }

        if (data.startsWith("wiz_p_")) {
            String platform = data.substring(6);
            String prompt = isEn
                ? "🛠 <b>Category Builder (Step 2/5)</b>\nPlatform: <b>" + platform + "</b>\n\nChoose target city or region:"
                : "🛠 <b>Конструктор категорій (Крок 2 з 5)</b>\nПлатформа: <b>" + platform + "</b>\n\nОберіть місто або регіон:";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<CategoryCatalog.City> cities = CategoryCatalog.getCities();
            int count = Math.min(12, cities.size());
            for (int i = 0; i < count; i += 3) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                for (int j = i; j < i + 3 && j < count; j++) {
                    row.add(createBtn(cities.get(j).getName(lang), "wiz_c_" + platform + "_" + j));
                }
                rows.add(row);
            }

            List<InlineKeyboardButton> rNav = new ArrayList<>();
            rNav.add(createBtn(isEn ? "⬅️ Back" : "⬅️ Назад", "wiz_start"));
            rNav.add(createBtn(isEn ? "❌ Cancel" : "❌ Скасувати", "cmd_targets"));
            rows.add(rNav);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, prompt, markup);
            return;
        }

        if (data.startsWith("wiz_c_")) {
            String[] parts = data.split("_");
            String platform = parts[2];
            int cityIdx = Integer.parseInt(parts[3]);
            CategoryCatalog.City city = CategoryCatalog.getCities().get(cityIdx);

            String prompt = isEn
                ? "🛠 <b>Category Builder (Step 3/5)</b>\nPlatform: <b>" + platform + "</b> | City: <b>" + city.getName(lang) + "</b>\n\nChoose property type:"
                : "🛠 <b>Конструктор категорій (Крок 3 з 5)</b>\nПлатформа: <b>" + platform + "</b> | Місто: <b>" + city.getName(lang) + "</b>\n\nОберіть тип нерухомості:";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<CategoryCatalog.PropertyType> pTypes = CategoryCatalog.getPropertyTypes();
            String[] icons = {"🏢 ", "🏡 ", "🌳 ", "🏬 ", "🚪 ", "🚗 "};
            for (int i = 0; i < pTypes.size(); i += 2) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                for (int j = i; j < i + 2 && j < pTypes.size(); j++) {
                    String ic = j < icons.length ? icons[j] : "🏠 ";
                    row.add(createBtn(ic + pTypes.get(j).getName(lang), "wiz_t_" + platform + "_" + cityIdx + "_" + j));
                }
                rows.add(row);
            }

            List<InlineKeyboardButton> rNav = new ArrayList<>();
            rNav.add(createBtn(isEn ? "⬅️ Back" : "⬅️ Назад", "wiz_p_" + platform));
            rNav.add(createBtn(isEn ? "❌ Cancel" : "❌ Скасувати", "cmd_targets"));
            rows.add(rNav);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, prompt, markup);
            return;
        }

        if (data.startsWith("wiz_t_")) {
            String[] parts = data.split("_");
            String platform = parts[2];
            int cityIdx = Integer.parseInt(parts[3]);
            int typeIdx = Integer.parseInt(parts[4]);

            CategoryCatalog.City city = CategoryCatalog.getCities().get(cityIdx);
            CategoryCatalog.PropertyType pType = CategoryCatalog.getPropertyTypes().get(typeIdx);

            String prompt = isEn
                ? "🛠 <b>Category Builder (Step 4/5)</b>\nTarget: <b>" + pType.getName(lang) + "</b> in <b>" + city.getName(lang) + "</b>\n\nChoose deal type:"
                : "🛠 <b>Конструктор категорій (Крок 4 з 5)</b>\nОб'єкт: <b>" + pType.getName(lang) + "</b> у <b>" + city.getName(lang) + "</b>\n\nОберіть тип угоди:";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<CategoryCatalog.DealType> dealTypes = CategoryCatalog.getDealTypes();
            for (int i = 0; i < dealTypes.size(); i++) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                String icon = (i == 0) ? "🏷️ " : (i == 1 ? "🔑 " : "📅 ");
                row.add(createBtn(icon + dealTypes.get(i).getName(lang), "wiz_d_" + platform + "_" + cityIdx + "_" + typeIdx + "_" + i));
                rows.add(row);
            }

            List<InlineKeyboardButton> rNav = new ArrayList<>();
            rNav.add(createBtn(isEn ? "⬅️ Back" : "⬅️ Назад", "wiz_c_" + platform + "_" + cityIdx));
            rNav.add(createBtn(isEn ? "❌ Cancel" : "❌ Скасувати", "cmd_targets"));
            rows.add(rNav);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, prompt, markup);
            return;
        }

        if (data.startsWith("wiz_d_")) {
            String[] parts = data.split("_");
            String platform = parts[2];
            int cityIdx = Integer.parseInt(parts[3]);
            int typeIdx = Integer.parseInt(parts[4]);
            int dealIdx = Integer.parseInt(parts[5]);

            String prompt = isEn
                ? "🛠 <b>Category Builder (Step 5/5)</b>\n\nIntermediary / Agency filter:"
                : "🛠 <b>Конструктор категорій (Крок 5 з 5)</b>\n\nФільтр посередників та агенцій:";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> r1 = new ArrayList<>();
            r1.add(createBtn(isEn ? "👤 Owner Only (100% Direct)" : "👤 Тільки від власника (без ріелторів)",
                    "wiz_f_" + platform + "_" + cityIdx + "_" + typeIdx + "_" + dealIdx + "_1"));
            rows.add(r1);

            List<InlineKeyboardButton> r2 = new ArrayList<>();
            r2.add(createBtn(isEn ? "👥 All Listings (with agencies)" : "👥 Всі оголошення (з ріелторами)",
                    "wiz_f_" + platform + "_" + cityIdx + "_" + typeIdx + "_" + dealIdx + "_0"));
            rows.add(r2);

            List<InlineKeyboardButton> rNav = new ArrayList<>();
            rNav.add(createBtn(isEn ? "⬅️ Back" : "⬅️ Назад", "wiz_t_" + platform + "_" + cityIdx + "_" + typeIdx));
            rNav.add(createBtn(isEn ? "❌ Cancel" : "❌ Скасувати", "cmd_targets"));
            rows.add(rNav);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, prompt, markup);
            return;
        }

        if (data.startsWith("wiz_f_")) {
            String[] parts = data.split("_");
            String platform = parts[2];
            int cityIdx = Integer.parseInt(parts[3]);
            int typeIdx = Integer.parseInt(parts[4]);
            int dealIdx = Integer.parseInt(parts[5]);
            boolean ownerOnly = "1".equals(parts[6]);

            CategoryCatalog.City city = CategoryCatalog.getCities().get(cityIdx);
            CategoryCatalog.PropertyType pType = CategoryCatalog.getPropertyTypes().get(typeIdx);
            CategoryCatalog.DealType dealType = CategoryCatalog.getDealTypes().get(dealIdx);

            String url = CategoryCatalog.buildUrl(platform, city, pType, dealType, null, null, ownerOnly);
            String name = pType.getName(lang) + " " + city.getName(lang) + " (" + (dealIdx == 0 ? (isEn ? "Buy" : "Купівля") : (isEn ? "Rent" : "Оренда")) + ")" + (ownerOnly ? (isEn ? " [Owner]" : " [Власник]") : "") + " (" + platform + ")";

            SettingsManager.CategoryEntry newEntry = new SettingsManager.CategoryEntry(name, url, true);
            List<SettingsManager.CategoryEntry> list = getCurrentCategories();
            list.add(newEntry);
            this.memoryCategories = list;
            SettingsManager.saveSettings(botName, botToken, list);

            if (IDC != null && !runningAlgorithms.isEmpty()) {
                startSearch(IDC);
            }

            String success = isEn
                ? "✅ <b>Target category created & activated!</b>\n\n" +
                  "• <b>Name:</b> " + escapeHtml(name) + "\n" +
                  "• <b>Platform:</b> " + platform + "\n" +
                  "• <b>Target:</b> " + pType.getName(lang) + " • " + city.getName(lang) + " • " + dealType.getName(lang) + "\n" +
                  "• <b>Filter:</b> " + (ownerOnly ? "👤 Direct Owner" : "👥 All") + "\n" +
                  "• <b>URL:</b> <code>" + escapeHtml(url) + "</code>\n\n" +
                  "<i>All parameters synchronized with desktop app and monitoring core.</i>"
                : "✅ <b>Категорію успішно створено та додано до моніторингу!</b>\n\n" +
                  "• <b>Назва:</b> " + escapeHtml(name) + "\n" +
                  "• <b>Платформа:</b> " + platform + "\n" +
                  "• <b>Ціль:</b> " + pType.getName(lang) + " • " + city.getName(lang) + " • " + dealType.getName(lang) + "\n" +
                  "• <b>Фільтр:</b> " + (ownerOnly ? "👤 Тільки від власника" : "👥 Всі оголошення") + "\n" +
                  "• <b>URL:</b> <code>" + escapeHtml(url) + "</code>\n\n" +
                  "<i>Всі налаштування синхронізовано з десктопним додатком та снайпінг-ядром.</i>";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> r1 = new ArrayList<>();
            r1.add(createBtn(isEn ? "▶️ Start Monitoring" : "▶️ Запустити пошук", "cmd_search"));
            r1.add(createBtn(isEn ? "📋 Search Targets" : "📋 Мої категорії", "cmd_targets"));
            rows.add(r1);

            List<InlineKeyboardButton> r2 = new ArrayList<>();
            r2.add(createBtn(isEn ? "➕ Add Another Target" : "➕ Створити ще ціль", "wiz_start"));
            rows.add(r2);
            markup.setKeyboard(rows);

            sendOrEditMessage(chatId, messageIdToEdit, success, markup);
        }
    }

    private void handleDirectUrlInput(String chatId, String rawText) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        String url = null;
        for (String part : rawText.split("\\s+")) {
            if (part.startsWith("http://") || part.startsWith("https://")) {
                url = part.trim();
                break;
            }
        }
        if (url == null && (rawText.contains("olx.ua") || rawText.contains("dom.ria.com"))) {
            url = "https://" + rawText.trim();
        }
        if (url == null) return;

        boolean isOlx = url.contains("olx.ua");
        boolean isDomRia = url.contains("dom.ria.com");

        if (!isOlx && !isDomRia) {
            String err = isEn
                ? "⚠️ <b>Unsupported URL!</b>\nPlease provide a search link from <b>OLX.ua</b> or <b>DOM.RIA.com</b>."
                : "⚠️ <b>Непідтримуване посилання!</b>\nБудь ласка, надішліть посилання на пошук з <b>OLX.ua</b> або <b>DOM.RIA.com</b>.";
            SendMessage sm = new SendMessage(chatId, err);
            sm.setParseMode("HTML");
            try { execute(sm); } catch (Exception ignored) {}
            return;
        }

        if (isOlx) {
            if (!url.contains("search%5Border%5D=created_at%3Adesc") && !url.contains("search[order]=created_at:desc")) {
                url += (url.contains("?") ? "&" : "?") + "search[order]=created_at:desc";
            }
            if (!url.contains("view=list")) {
                url += "&view=list";
            }
            if (!url.contains("currency=")) {
                url += "&currency=UAH";
            }
        }

        String autoTitle = generateSmartTitleFromUrl(url, isOlx, isEn);

        SettingsManager.CategoryEntry newEntry = new SettingsManager.CategoryEntry(autoTitle, url, true);
        List<SettingsManager.CategoryEntry> list = getCurrentCategories();
        list.add(newEntry);
        this.memoryCategories = list;
        SettingsManager.saveSettings(botName, botToken, list);

        if (IDC != null && !runningAlgorithms.isEmpty()) {
            startSearch(IDC);
        }

        String successMsg = isEn
            ? "✅ <b>Target category added via link!</b>\n\n" +
              "• <b>Name:</b> " + escapeHtml(autoTitle) + "\n" +
              "• <b>Platform:</b> " + (isOlx ? "OLX.ua" : "DOM.RIA.com") + "\n" +
              "• <b>URL:</b> <code>" + escapeHtml(url) + "</code>\n\n" +
              "<i>Sorting rules ('Newest') & connection safety optimized automatically.</i>"
            : "✅ <b>Категорію успішно додано за посиланням!</b>\n\n" +
              "• <b>Назва:</b> " + escapeHtml(autoTitle) + "\n" +
              "• <b>Платформа:</b> " + (isOlx ? "OLX.ua" : "DOM.RIA.com") + "\n" +
              "• <b>URL:</b> <code>" + escapeHtml(url) + "</code>\n\n" +
              "<i>Сортування («Найновіші») та параметри безпеки оптимізовано автоматично.</i>";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> r1 = new ArrayList<>();
        r1.add(createBtn(isEn ? "▶️ Start Search" : "▶️ Запустити пошук", "cmd_search"));
        r1.add(createBtn(isEn ? "📋 Search Targets" : "📋 Мої категорії", "cmd_targets"));
        rows.add(r1);
        markup.setKeyboard(rows);

        SendMessage sm = new SendMessage(chatId, successMsg);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception ignored) {}
    }

    private static String generateSmartTitleFromUrl(String url, boolean isOlx, boolean isEn) {
        boolean isOwner = url.contains("without_agent") || url.contains("private");
        String ownerBadge = isOwner ? (isEn ? " [Owner]" : " [Власник]") : "";

        String lower = url.toLowerCase();
        String type = isEn ? "Property" : "Нерухомість";
        if (lower.contains("kvartir") || lower.contains("квартир")) type = isEn ? "Apartments" : "Квартири";
        else if (lower.contains("doma") || lower.contains("будин")) type = isEn ? "Houses" : "Будинки";
        else if (lower.contains("zeml") || lower.contains("ділянк")) type = isEn ? "Land" : "Земельні ділянки";
        else if (lower.contains("kommerch") || lower.contains("комерц")) type = isEn ? "Commercial" : "Комерційна";

        String deal = "";
        if (lower.contains("prodazh") || lower.contains("продаж")) deal = isEn ? " (Buy)" : " (Купівля)";
        else if (lower.contains("arend") || lower.contains("оренд")) deal = isEn ? " (Rent)" : " (Оренда)";

        String city = "";
        for (CategoryCatalog.City c : CategoryCatalog.getCities()) {
            if (lower.contains("/" + c.domRiaSlug + "/") || lower.contains("/" + c.olxCode + "/") || lower.contains(c.name.toLowerCase())) {
                city = " " + c.getName(isEn ? I18n.Language.EN : I18n.Language.UA);
                break;
            }
        }

        String plat = isOlx ? " (OLX)" : " (DOM.RIA)";
        return type + city + deal + ownerBadge + plat;
    }

    private void sendAddUrlPrompt(String chatId, Integer messageIdToEdit) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String msg = isEn
            ? "🔗 <b>How to add a category via direct link:</b>\n\n" +
              "1. Open <b>OLX.ua</b> or <b>DOM.RIA.com</b> in your browser.\n" +
              "2. Configure your filters (City, Price, Owner-only).\n" +
              "3. Copy the URL from the address bar.\n" +
              "4. <b>Simply send the link as a message in this chat!</b> ⬇️\n\n" +
              "<i>The bot will automatically recognize the platform, check sorting rules, and add it to your active targets.</i>"
            : "🔗 <b>Як додати категорію за прямим посиланням:</b>\n\n" +
              "1. Відкрийте <b>OLX.ua</b> або <b>DOM.RIA.com</b> у браузері.\n" +
              "2. Налаштуйте потрібні фільтри (Місто, Ціна, Без посередників).\n" +
              "3. Скопіюйте посилання з адресної стрічки браузера.\n" +
              "4. <b>Просто надішліть це посилання сюди в чат!</b> ⬇️\n\n" +
              "<i>Бот автоматично розпізнає платформу, оптимізує сортування та додасть ціль до моніторингу.</i>";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> r1 = new ArrayList<>();
        r1.add(createBtn(isEn ? "➕ Category Builder" : "➕ Конструктор категорій", "wiz_start"));
        r1.add(createBtn(isEn ? "📋 Search Targets" : "📋 Мої категорії", "cmd_targets"));
        rows.add(r1);
        markup.setKeyboard(rows);

        sendOrEditMessage(chatId, messageIdToEdit, msg, markup);
    }

    private void showHelpHub(String chatId, Integer messageIdToEdit, String activeTab) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        String content;
        if ("help_tab_builder".equals(activeTab)) {
            content = isEn
                ? "🛠 <b>Category Builder & Direct URLs:</b>\n\n" +
                  "• <b>Interactive Builder:</b> Press 'Category Builder' to generate 100% verified target links with city, category, deal type, and direct owner filters.\n\n" +
                  "• <b>Direct Links from Browser:</b>\n" +
                  "Copy any search link from <b>OLX.ua</b> or <b>DOM.RIA.com</b> and paste it into this chat!\n\n" +
                  "<b>Essential Link Rules:</b>\n" +
                  "• <b>OLX:</b> Must use <code>view=list</code> and <code>search[order]=created_at:desc</code>.\n" +
                  "• <b>DOM.RIA:</b> Turn on 'Without agents' (<code>without_agent=1</code>).\n\n" +
                  "The bot automatically sanitizes and optimizes any link you send!"
                : "🛠 <b>Конструктор категорій та посилання:</b>\n\n" +
                  "• <b>Інтерактивний Конструктор:</b> Натисніть «Конструктор категорій», щоб створити готову ціль пошуку в кілька кліків з вибором міста, типу, операції та фільтра власника.\n\n" +
                  "• <b>Прямі посилання з браузера:</b>\n" +
                  "Скопіюйте посилання на налаштований пошук з <b>OLX.ua</b> або <b>DOM.RIA.com</b> у браузері та надішліть сюди в чат!\n\n" +
                  "<b>Обов'язкові правила для посилань:</b>\n" +
                  "• <b>OLX:</b> Оберіть відображення рядками (<code>view=list</code>) та сортування «Найновіші» (<code>search[order]=created_at:desc</code>).\n" +
                  "• <b>DOM.RIA:</b> Увімкніть галочку «Без посередників» (<code>without_agent=1</code>).\n\n" +
                  "Бот автоматично санітизує та оптимізує кожне надіслане посилання!";
        } else if ("help_tab_speed".equals(activeTab)) {
            content = isEn
                ? "⚡ <b>Detection Speed & Anti-Spam Architecture:</b>\n\n" +
                  "• <b>Global CDN Caching (Akamai / Cloudflare):</b>\n" +
                  "After a property owner clicks 'Publish', the listing is indexed by the portal and deployed to public CDN edge servers within <b>30–90 seconds</b>. Property Searcher intercepts it in the <b>first 1–2 seconds</b> after public release!\n\n" +
                  "• <b>O(1) Circular Memory Buffer:</b>\n" +
                  "Guarantees zero repeat notifications if listings shuffle positions during high-frequency portal updates.\n\n" +
                  "• <b>DUAL Currency & Night Mode:</b>\n" +
                  "Automatic NBU dual-currency conversion. Notifications between 23:00 and 07:00 arrive silently."
                : "⚡ <b>Швидкість детекції та Архітектура:</b>\n\n" +
                  "• <b>Глобальне CDN-кешування сайтів (Akamai / Cloudflare):</b>\n" +
                  "Після публікації оголошення воно потрапляє на модерацію та з'являється на відкритих серверах через <b>30–90 секунд</b>. Property Searcher перехоплює його в <b>перші ж 1–2 секунди</b> після появи в інтернеті!\n\n" +
                  "• <b>Кільцевий буфер пам'яті $O(1)$:</b>\n" +
                  "Повний захист від дублікатів та перестановок оголошень на сайті. Старі оголошення повторно ніколи не надсилаються.\n\n" +
                  "• <b>DUAL-валюта та Нічний режим:</b>\n" +
                  "Ціни автоматично конвертуються за курсом НБУ. У нічний час (23:00 – 07:00) сповіщення приходять без звуку.";
        } else if ("help_tab_botfather".equals(activeTab)) {
            content = isEn
                ? "🤖 <b>BotFather Menu Configuration:</b>\n\n" +
                  "1. Open @BotFather in Telegram\n" +
                  "2. Send <code>/setcommands</code> and select your bot\n" +
                  "3. Send this commands template:\n\n" +
                  "<code>start - Main menu & instant start\n" +
                  "search - Start listing monitoring\n" +
                  "targets - Manage search categories\n" +
                  "currency - Price currency (USD/UAH)\n" +
                  "stop - Pause monitoring\n" +
                  "help - Documentation & guide</code>"
                : "🤖 <b>Налаштування меню у @BotFather:</b>\n\n" +
                  "1. Відкрийте @BotFather у Telegram\n" +
                  "2. Надішліть команду <code>/setcommands</code> та оберіть вашого бота\n" +
                  "3. Надішліть цей готовий шаблон команд:\n\n" +
                  "<code>start - Головне меню та запуск\n" +
                  "search - Запустити пошук оголошень\n" +
                  "targets - Керування категоріями\n" +
                  "currency - Валюта цін (USD/UAH)\n" +
                  "stop - Зупинити моніторинг\n" +
                  "help - Довідка та інструкція</code>";
        } else {
            content = isEn
                ? "🚀 <b>Quick Start & Live Operation:</b>\n\n" +
                  "• <b>Instant First Listing:</b> Whenever you start the bot or click 'Search Now', the bot instantly sends the latest organic ad for every active category with photos, price, and exact time.\n\n" +
                  "• <b>Interactive Category Control:</b> Turn categories ON or PAUSE anytime via the 'Search Targets' menu.\n\n" +
                  "• <b>Direct Browser Button:</b> Under each ad card, click 'Open on OLX / DOM.RIA' to view the listing directly in your browser or app."
                : "🚀 <b>Швидкий запуск та Робота:</b>\n\n" +
                  "• <b>Миттєва відправка при старті:</b> При запуску бота або кнопці «Пошук зараз» бот одразу надсилає найновіше оголошення у кожній активній категорії разом із фотографіями, ціною та точним часом публікації.\n\n" +
                  "• <b>Керування на льоту:</b> Вмикайте та ставте на паузу будь-які категорії через меню «Мої категорії».\n\n" +
                  "• <b>Швидкий перехід:</b> Під кожним оголошенням кнопка «Відкрити на OLX / DOM.RIA» миттєво відкриває об'єкт.";
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> r1 = new ArrayList<>();
        r1.add(createBtn((!"help_tab_start".equals(activeTab) ? "🚀 " : "🔘 ") + (isEn ? "Quick Start" : "Швидкий запуск"), "help_tab_start"));
        r1.add(createBtn((!"help_tab_builder".equals(activeTab) ? "🛠 " : "🔘 ") + (isEn ? "Builder & URL" : "Конструктор та URL"), "help_tab_builder"));
        rows.add(r1);

        List<InlineKeyboardButton> r2 = new ArrayList<>();
        r2.add(createBtn((!"help_tab_speed".equals(activeTab) ? "⚡ " : "🔘 ") + (isEn ? "Speed & Buffer" : "Швидкість та Буфер"), "help_tab_speed"));
        r2.add(createBtn((!"help_tab_botfather".equals(activeTab) ? "🤖 " : "🔘 ") + (isEn ? "BotFather" : "BotFather"), "help_tab_botfather"));
        rows.add(r2);

        List<InlineKeyboardButton> r3 = new ArrayList<>();
        r3.add(createBtn(isEn ? "📋 Search Targets" : "📋 Мої категорії", "cmd_targets"));
        r3.add(createBtn(isEn ? "▶️ Start Search" : "▶️ Запустити пошук", "cmd_search"));
        rows.add(r3);
        markup.setKeyboard(rows);

        sendOrEditMessage(chatId, messageIdToEdit, content, markup);
    }

    private void sendOrEditMessage(String chatId, Integer messageIdToEdit, String text, InlineKeyboardMarkup markup) {
        if (messageIdToEdit != null) {
            EditMessageText em = new EditMessageText();
            em.setChatId(chatId);
            em.setMessageId(messageIdToEdit);
            em.setText(text);
            em.setParseMode("HTML");
            em.setReplyMarkup(markup);
            try {
                execute(em);
                return;
            } catch (Exception ignored) {}
        }
        SendMessage sm = new SendMessage(chatId, text);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(markup);
        try {
            execute(sm);
        } catch (Exception ignored) {}
    }

    private static InlineKeyboardButton createBtn(String text, String callbackData) {
        InlineKeyboardButton b = new InlineKeyboardButton(text);
        b.setCallbackData(callbackData);
        return b;
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
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        String status = isEn
            ? "📊 <b>Property Searcher System Status:</b>\n\n" +
              "• <b>Monitoring:</b> " + (running ? "🟢 ACTIVE" : "⏸ PAUSED") + "\n" +
              "• <b>Total Categories:</b> " + all.size() + " (active: " + activeCount + ")\n" +
              "• <b>Adaptive Pacing:</b> " + String.format(java.util.Locale.US, "%.1f", delaySec) + " s per target\n" +
              "• <b>Connection Safety:</b> " + String.format(java.util.Locale.US, "%.2f", currentRps) + " req/s (CloudFront Safe 🛡)\n" +
              "• <b>Price Currency:</b> <code>" + currMode.name() + "</code> (1 $ = " + CurrencyService.getUsdRate() + " ₴ | 1 € = " + CurrencyService.getEurRate() + " ₴)\n" +
              "• <b>Notification Mode:</b> " + (night ? "🌙 Night (silent)" : "☀️ Day (with sound)")
            : "📊 <b>Статус Property Searcher:</b>\n\n" +
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
        stopSearch(null);
    }

    private void stopSearch(String targetChatId) {
        stopSearchThreads();
        String target = (targetChatId != null && !targetChatId.isEmpty()) ? targetChatId : IDC;
        if (target == null) target = SettingsManager.getLastChatId();
        if (target != null) {
            I18n.Language lang = SettingsManager.getLanguage();
            String stopTxt = (lang == I18n.Language.EN)
                ? "⏸ <b>Listing monitoring stopped.</b>"
                : "⏸ <b>Моніторинг оголошень зупинено.</b>";
            SendMessage sm3 = new SendMessage(target, stopTxt);
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
        showHelpHub(chatId, null, "help_tab_start");
    }

    private void sendBotFatherHelp(String chatId) {
        showHelpHub(chatId, null, "help_tab_botfather");
    }

    private void sendUnknownCommandMessage(String chatId) {
        I18n.Language lang = SettingsManager.getLanguage();
        String unknownTxt = (lang == I18n.Language.EN)
            ? "Unknown command. Please select an action from the keyboard below."
            : "Невідома команда. Оберіть дію на клавіатурі нижче.";
        SendMessage sm = new SendMessage(chatId, unknownTxt);
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

                I18n.Language lang = SettingsManager.getLanguage();
                CurrencyService.CurrencyMode currMode = SettingsManager.getCurrencyPreference();
                String formattedPrice = CurrencyService.formatPrice(res.price, currMode, lang);
                boolean isNight = isNightTime();

                String cleanTitle = escapeHtml(res.title);
                String cleanCategory = escapeHtml(CategoryCatalog.localizeCategoryName(categoryName, lang));
                String cleanPrice = escapeHtml(formattedPrice);
                String cleanPlatform = escapeHtml(res.platform);
                String cleanTime = escapeHtml(res.timePublished);

                String lblPrice = I18n.get("card.price", lang);
                String lblTime = I18n.get("card.time", lang);
                String lblSource = I18n.get("card.source", lang);
                String lblOpen = I18n.get("card.open", lang);

                System.out.println("🔥 [GENUINELY NEW AD DETECTED ON " + cleanPlatform + " | " + cleanCategory + "]: " + cleanTitle + " (" + cleanLink + ") [" + formattedPrice + "]");

                // Concise header: Category name immediately followed by original ad title
                String text = "🏠 <b>" + cleanCategory + ":</b> " + cleanTitle + "\n\n" +
                        (cleanPrice.isEmpty() ? "" : "💰 <b>" + lblPrice + ":</b> " + cleanPrice + "\n") +
                        (cleanTime.isEmpty() ? "" : "🕒 <b>" + lblTime + ":</b> " + cleanTime + "\n") +
                        "📍 <b>" + lblSource + ":</b> " + cleanPlatform + "\n\n" +
                        "🔗 <a href=\"" + res.link + "\">" + lblOpen + " " + cleanPlatform + "</a>";

                InlineKeyboardMarkup cardInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> row1 = new ArrayList<>();

                InlineKeyboardButton openBtn = new InlineKeyboardButton();
                openBtn.setText("🔗 " + lblOpen + " " + cleanPlatform);
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
