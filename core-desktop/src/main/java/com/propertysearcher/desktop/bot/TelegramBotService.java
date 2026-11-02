package com.propertysearcher.desktop.bot;

import com.propertysearcher.desktop.CategoryCatalog;
import com.propertysearcher.desktop.CurrencyService;
import com.propertysearcher.desktop.I18n;
import com.propertysearcher.desktop.ParserOLX;
import com.propertysearcher.desktop.SettingsManager;
import com.propertysearcher.desktop.engine.SearchAlgorithmEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Telegram Bot Service for real-time property monitoring.
 * Orchestrates incoming commands, menu navigation, and decoupled scraping engines.
 */
public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    public static final double TARGET_SAFE_GLOBAL_RPS = SearchAlgorithmEngine.TARGET_SAFE_GLOBAL_RPS;
    public static final int MIN_THREAD_DELAY_MS = SearchAlgorithmEngine.MIN_THREAD_DELAY_MS;

    private String idc;
    private final String botName;
    private final String botToken;
    private List<SettingsManager.CategoryEntry> memoryCategories = null;
    protected final List<SearchAlgorithmEngine> runningEngines = new ArrayList<>();

    public TelegramBotService(String botName, String botToken) {
        this.botName = botName;
        this.botToken = botToken;
        loadLastChatId();
    }

    public TelegramBotService(String botName, String botToken, List<SettingsManager.CategoryEntry> categories) {
        this.botName = botName;
        this.botToken = botToken;
        this.memoryCategories = categories;
        loadLastChatId();
    }

    public static int calculateAdaptiveDelay(int activeCount) {
        return SearchAlgorithmEngine.calculateAdaptiveDelay(activeCount);
    }

    public void updateCategoriesInMemory(List<SettingsManager.CategoryEntry> updated) {
        this.memoryCategories = updated;
        if (idc != null && !runningEngines.isEmpty()) {
            startSearch(idc);
        }
    }

    public void saveLastChatId(String chatId) {
        this.idc = chatId;
        SettingsManager.saveLastChatId(chatId);
    }

    public void loadLastChatId() {
        String id = SettingsManager.getLastChatId();
        if (id != null && !id.trim().isEmpty()) {
            this.idc = id.trim();
        }
    }

    public void triggerStartFromGui() {
        if (idc != null && !idc.trim().isEmpty()) {
            startSearch(idc);
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

    protected void handleCallbackQuery(CallbackQuery callbackQuery) {
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
        } else if (data != null && data.startsWith("set_curr_")) {
            String modeStr = data.substring(9).toUpperCase();
            try {
                CurrencyService.CurrencyMode mode = CurrencyService.CurrencyMode.valueOf(modeStr);
                SettingsManager.setCurrencyPreference(mode);
                sendCurrencyChangedMessage(chatId, mode);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void startSearch(String chatId) {
        this.idc = chatId;
        stopSearchThreads();

        List<SettingsManager.CategoryEntry> allCategories = getCurrentCategories();
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        if (allCategories.isEmpty()) {
            String emptyTxt = isEn
                    ? "📋 <b>No categories added yet!</b>\nAdd search links using the Builder or send OLX / DOM.RIA URLs to this chat."
                    : "📋 <b>Категорії ще не додані!</b>\nДодайте посилання через Конструктор або надішліть OLX / DOM.RIA URL в цей чат.";
            SendMessage sm = new SendMessage(chatId, emptyTxt);
            sm.setParseMode("HTML");
            sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
            try { execute(sm); } catch (Exception ignored) {}
            return;
        }

        List<SettingsManager.CategoryEntry> activeCategories = new ArrayList<>();
        for (SettingsManager.CategoryEntry c : allCategories) {
            if (c.active) activeCategories.add(c);
        }

        if (activeCategories.isEmpty()) {
            String pausedTxt = isEn
                    ? "⏸ <b>All categories are paused!</b>\nEnable active targets via 'Search Targets'."
                    : "⏸ <b>Усі категорії на паузі!</b>\nУвімкніть потрібні в меню «Мої категорії».";
            SendMessage sm = new SendMessage(chatId, pausedTxt);
            sm.setParseMode("HTML");
            sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
            try { execute(sm); } catch (Exception ignored) {}
            return;
        }

        int activeCount = activeCategories.size();
        int delayMs = calculateAdaptiveDelay(activeCount);
        double delaySec = delayMs / 1000.0;
        int staggerStep = (activeCount > 1) ? (delayMs / activeCount) : 0;

        String startedTxt = isEn
                ? "🔍 <b>Monitoring started!</b>\n" +
                "• Active targets: <b>" + activeCount + "</b>\n" +
                "• Interval: <b>" + String.format("%.1f", delaySec) + "s</b> (Rate-limiting protection enabled)\n\n" +
                "<i>New listings will arrive instantly in this chat.</i>"
                : "🔍 <b>Моніторинг запущено!</b>\n" +
                "• Активних категорій: <b>" + activeCount + "</b>\n" +
                "• Інтервал: <b>" + String.format("%.1f", delaySec) + "с</b> (Захист CloudFront/DataDome)\n\n" +
                "<i>Нові оголошення надходитимуть сюди автоматично.</i>";

        SendMessage sm = new SendMessage(chatId, startedTxt);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}

        for (int i = 0; i < activeCategories.size(); i++) {
            SettingsManager.CategoryEntry cat = activeCategories.get(i);
            int stagger = i * staggerStep;
            SearchAlgorithmEngine engine = createEngine(cat.url, chatId, cat.name, delayMs, stagger);
            runningEngines.add(engine);
            engine.start();
        }
    }

    protected SearchAlgorithmEngine createEngine(String categoryUrl, String chatId, String categoryName, int baseDelayMs, int initialStaggerMs) {
        return new SearchAlgorithmEngine(categoryUrl, chatId, categoryName, baseDelayMs, initialStaggerMs,
                (res, link) -> dispatchNotification(res, link, categoryName));
    }

    public synchronized void stopSearch() {
        stopSearchThreads();
        if (idc != null && !idc.isEmpty()) {
            stopSearch(idc);
        }
    }

    public synchronized void stopSearch(String targetChatId) {
        stopSearchThreads();
        I18n.Language lang = SettingsManager.getLanguage();
        String msg = (lang == I18n.Language.EN)
                ? "⏹ <b>Monitoring paused.</b>\nPress 'Search' to resume."
                : "⏹ <b>Моніторинг зупинено.</b>\nНатисніть «Пошук», щоб відновити роботу.";
        SendMessage sm = new SendMessage(targetChatId, msg);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    protected synchronized void stopSearchThreads() {
        for (SearchAlgorithmEngine engine : runningEngines) {
            engine.stop();
        }
        runningEngines.clear();
    }

    public void dispatchNotification(ParserOLX.PropertyResult res, String cleanLink, String categoryName) {
        TelegramNotificationDispatcher.dispatchAsync(this, this.idc, categoryName, res, cleanLink);
    }

    public List<SettingsManager.CategoryEntry> getCurrentCategories() {
        if (memoryCategories != null && !memoryCategories.isEmpty()) {
            return memoryCategories;
        }
        return SettingsManager.loadCategories();
    }

    public void onLanguageChanged(I18n.Language lang) {
        String target = (idc != null && !idc.isEmpty()) ? idc : SettingsManager.getLastChatId();
        if (target != null) {
            String msg = (lang == I18n.Language.EN)
                    ? "🌐 <b>Interface language changed to English 🇬🇧</b>"
                    : "🌐 <b>Мову інтерфейсу перемкнено на Українську 🇺🇦</b>";
            SendMessage sm = new SendMessage(target, msg);
            sm.setParseMode("HTML");
            sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
            try { execute(sm); } catch (Exception ignored) {}
        }
    }

    private void sendWelcomeMessage(String chatId) {
        I18n.Language lang = SettingsManager.getLanguage();
        String welcome = I18n.get("tg.welcome", lang);
        SendMessage sm = new SendMessage(chatId, welcome);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception e) {
            log.error("Welcome failed: {}", e.getMessage());
        }
    }

    private void showCategories(String chatId, Integer messageIdToEdit) {
        List<SettingsManager.CategoryEntry> categories = getCurrentCategories();
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        if (categories.isEmpty()) {
            String empty = isEn
                    ? "📋 <b>Search Targets:</b>\n\n⚠️ <i>No categories added yet!</i>"
                    : "📋 <b>Категорії моніторингу:</b>\n\n⚠️ <i>Категорій поки що немає!</i>";
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> r1 = new ArrayList<>();
            r1.add(createBtn(isEn ? "✨ Wizard" : "✨ Майстер", "wiz_start"));
            r1.add(createBtn(isEn ? "🔗 Add by Link" : "🔗 Додати посиланням", "btn_add_url"));
            rows.add(r1);
            markup.setKeyboard(rows);
            sendOrEditMessage(chatId, messageIdToEdit, empty, markup);
            return;
        }

        int activeCount = 0;
        for (SettingsManager.CategoryEntry c : categories) if (c.active) activeCount++;

        StringBuilder sb = new StringBuilder();
        sb.append(isEn ? "📋 <b>Target Categories:</b>\n" : "📋 <b>Категорії моніторингу:</b>\n");
        sb.append(isEn ? "Total: <b>" : "Всього: <b>").append(categories.size())
                .append(isEn ? "</b> | Active: <b>" : "</b> | Активних: <b>").append(activeCount).append("</b>\n\n");

        for (int i = 0; i < categories.size(); i++) {
            SettingsManager.CategoryEntry c = categories.get(i);
            String plat = c.url.contains("dom.ria") ? "DOM.RIA" : "OLX";
            String status = c.active ? (isEn ? "🟢 ACTIVE" : "🟢 ВКЛ") : (isEn ? "⏸ PAUSED" : "⏸ ПАУЗА");
            sb.append("<b>").append(i + 1).append(". ").append(TelegramNotificationDispatcher.escapeHtml(c.name))
                    .append("</b> [").append(plat).append("]\n• ").append(status).append("\n\n");
        }

        InlineKeyboardMarkup markup = TelegramMenuFactory.createCategoriesMenu(categories, isEn, lang);
        sendOrEditMessage(chatId, messageIdToEdit, sb.toString(), markup);
    }

    private void handleCategoryToggle(String chatId, Integer messageId, String data) {
        try {
            int index = Integer.parseInt(data.substring(8));
            List<SettingsManager.CategoryEntry> list = getCurrentCategories();
            if (index >= 0 && index < list.size()) {
                SettingsManager.CategoryEntry c = list.get(index);
                c.active = !c.active;
                this.memoryCategories = list;
                SettingsManager.saveSettings(botName, botToken, list);
                showCategories(chatId, messageId);
                if (!runningEngines.isEmpty()) {
                    startSearch(chatId);
                }
            }
        } catch (Exception ignored) {}
    }

    private void handleCategoryDelete(String chatId, Integer messageId, String data) {
        try {
            int index = Integer.parseInt(data.substring(8));
            List<SettingsManager.CategoryEntry> list = getCurrentCategories();
            if (index >= 0 && index < list.size()) {
                list.remove(index);
                this.memoryCategories = list;
                SettingsManager.saveSettings(botName, botToken, list);
                showCategories(chatId, messageId);
                if (!runningEngines.isEmpty()) {
                    startSearch(chatId);
                }
            }
        } catch (Exception ignored) {}
    }

    private void showStatus(String chatId) {
        List<SettingsManager.CategoryEntry> all = getCurrentCategories();
        int active = 0;
        for (SettingsManager.CategoryEntry c : all) if (c.active) active++;
        boolean running = !runningEngines.isEmpty();
        int delay = calculateAdaptiveDelay(active);

        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        String statusTxt = isEn
                ? "📊 <b>System Status:</b>\n\n" +
                "• <b>Engine:</b> " + (running ? "🟢 ACTIVE" : "⏸ IDLE") + "\n" +
                "• <b>Active Threads:</b> " + runningEngines.size() + "\n" +
                "• <b>Total Targets:</b> " + all.size() + " (" + active + " active)\n" +
                "• <b>Adaptive Cycle:</b> " + String.format("%.1f", delay / 1000.0) + "s\n" +
                "• <b>Currency:</b> " + SettingsManager.getCurrencyPreference().name() + "\n" +
                "• <b>Language:</b> " + lang.name()
                : "📊 <b>Статус системи:</b>\n\n" +
                "• <b>Стан:</b> " + (running ? "🟢 АКТИВНИЙ" : "⏸ ПАУЗА") + "\n" +
                "• <b>Потоків:</b> " + runningEngines.size() + "\n" +
                "• <b>Категорій:</b> " + all.size() + " (активних: " + active + ")\n" +
                "• <b>Адаптивний цикл:</b> " + String.format("%.1f", delay / 1000.0) + "с\n" +
                "• <b>Валюта:</b> " + SettingsManager.getCurrencyPreference().name() + "\n" +
                "• <b>Мова:</b> " + lang.name();

        SendMessage sm = new SendMessage(chatId, statusTxt);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void showCurrencyMenu(String chatId) {
        CurrencyService.CurrencyMode current = SettingsManager.getCurrencyPreference();
        double usd = CurrencyService.getUsdRate();
        double eur = CurrencyService.getEurRate();
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);

        String text = isEn
                ? "💵 <b>Currency Display Format:</b>\n\n" +
                "• Current: <code>" + current.name() + "</code>\n" +
                "• NBU: 1 USD = " + usd + " UAH | 1 EUR = " + eur + " UAH\n\n" +
                "Choose pricing display for alert cards:"
                : "💵 <b>Формат відображення валюти цін:</b>\n\n" +
                "• Поточний: <code>" + current.name() + "</code>\n" +
                "• Курс НБУ: 1 USD = " + usd + " грн | 1 EUR = " + eur + " грн\n\n" +
                "Оберіть формат відображення в оголошеннях:";

        SendMessage sm = new SendMessage(chatId, text);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createCurrencyMenu(current, isEn));
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void sendCurrencyChangedMessage(String chatId, CurrencyService.CurrencyMode mode) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String msg = isEn
                ? "✅ Currency set to <code>" + mode.name() + "</code>!"
                : "✅ Валюту оновлено на <code>" + mode.name() + "</code>!";
        SendMessage sm = new SendMessage(chatId, msg);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void showLanguageMenu(String chatId) {
        I18n.Language current = SettingsManager.getLanguage();
        String text = (current == I18n.Language.EN)
                ? "🌐 <b>Language Settings:</b>\nChoose preferred interface language:"
                : "🌐 <b>Налаштування мови інтерфейсу:</b>\nОберіть мову інтерфейсу:";

        SendMessage sm = new SendMessage(chatId, text);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createLanguageMenu(current));
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void sendLanguageChangedMessage(String chatId, I18n.Language lang) {
        String msg = (lang == I18n.Language.EN)
                ? "✅ <b>Language set to English!</b>"
                : "✅ <b>Мову перемкнено на Українську!</b>";
        SendMessage sm = new SendMessage(chatId, msg);
        sm.setParseMode("HTML");
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void showHelpHub(String chatId, Integer messageIdToEdit, String activeTab) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String text;
        if ("help_tab_urls".equals(activeTab)) {
            text = isEn
                    ? "🔗 <b>Supported URLs:</b>\nPaste any search link from OLX.ua or DOM.RIA.com."
                    : "🔗 <b>Підтримувані URL:</b>\nНадсилайте будь-яке посилання на пошук з OLX.ua або DOM.RIA.com.";
        } else if ("help_tab_wizard".equals(activeTab)) {
            text = isEn
                    ? "✨ <b>Category Wizard:</b>\nBuild search targets step-by-step with city and deal filters."
                    : "✨ <b>Майстер категорій:</b>\nСтворюйте готові пошуки у кілька кліків.";
        } else {
            text = isEn
                    ? "🚀 <b>Quickstart:</b>\nInstant detection of newest real estate listings with direct owner filters."
                    : "🚀 <b>Швидкий старт:</b>\nМиттєве перехоплення свіжих оголошень без посередників.";
        }
        sendOrEditMessage(chatId, messageIdToEdit, text, TelegramMenuFactory.createHelpHubMarkup(activeTab, isEn));
    }

    private void handleWizardStep(String chatId, Integer messageIdToEdit, String data) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String text = isEn ? "✨ <b>Category Builder:</b> Select city:" : "✨ <b>Майстер:</b> Оберіть місто:";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> r1 = new ArrayList<>();
        r1.add(createBtn("Київ", "cmd_targets"));
        r1.add(createBtn("Львів", "cmd_targets"));
        r1.add(createBtn("Одеса", "cmd_targets"));
        rows.add(r1);
        markup.setKeyboard(rows);
        sendOrEditMessage(chatId, messageIdToEdit, text, markup);
    }

    private void handleDirectUrlInput(String chatId, String rawText) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String url = rawText.trim();
        SettingsManager.CategoryEntry newEntry = new SettingsManager.CategoryEntry("Custom Search", url, true);
        List<SettingsManager.CategoryEntry> list = getCurrentCategories();
        list.add(newEntry);
        this.memoryCategories = list;
        SettingsManager.saveSettings(botName, botToken, list);

        String msg = isEn ? "✅ Target added: " + url : "✅ Категорію додано: " + url;
        SendMessage sm = new SendMessage(chatId, msg);
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void sendAddUrlPrompt(String chatId, Integer messageIdToEdit) {
        I18n.Language lang = SettingsManager.getLanguage();
        boolean isEn = (lang == I18n.Language.EN);
        String msg = isEn
                ? "🔗 Send an OLX.ua or DOM.RIA search link directly to this chat."
                : "🔗 Надішліть посилання з OLX.ua або DOM.RIA сюди в чат.";
        sendOrEditMessage(chatId, messageIdToEdit, msg, null);
    }

    private void sendBotFatherHelp(String chatId) {
        SendMessage sm = new SendMessage(chatId, "🤖 /setcommands: start, search, targets, currency, stop, help");
        try { execute(sm); } catch (Exception ignored) {}
    }

    private void sendUnknownCommandMessage(String chatId) {
        I18n.Language lang = SettingsManager.getLanguage();
        String msg = (lang == I18n.Language.EN) ? "❓ Unknown command. Use /help" : "❓ Невідома команда. Натисніть /help";
        SendMessage sm = new SendMessage(chatId, msg);
        sm.setReplyMarkup(TelegramMenuFactory.createPersistentMenu());
        try { execute(sm); } catch (Exception ignored) {}
    }

    protected void sendOrEditMessage(String chatId, Integer messageIdToEdit, String text, InlineKeyboardMarkup markup) {
        if (messageIdToEdit != null) {
            EditMessageText em = new EditMessageText();
            em.setChatId(chatId);
            em.setMessageId(messageIdToEdit);
            em.setText(text);
            em.setParseMode("HTML");
            if (markup != null) em.setReplyMarkup(markup);
            try {
                execute(em);
                return;
            } catch (Exception ignored) {}
        }
        SendMessage sm = new SendMessage(chatId, text);
        sm.setParseMode("HTML");
        if (markup != null) sm.setReplyMarkup(markup);
        try { execute(sm); } catch (Exception ignored) {}
    }

    protected static InlineKeyboardButton createBtn(String text, String callbackData) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callbackData);
        return b;
    }
}
