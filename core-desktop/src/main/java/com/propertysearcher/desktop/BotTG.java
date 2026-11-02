package com.propertysearcher.desktop;

import com.propertysearcher.desktop.bot.TelegramBotService;
import com.propertysearcher.desktop.engine.SearchAlgorithmEngine;

import java.util.List;

/**
 * Desktop Telegram Bot Controller.
 * Inherits full messaging and scheduling capabilities from TelegramBotService.
 */
public class BotTG extends TelegramBotService {

    public BotTG(String botName, String botToken) {
        super(botName, botToken);
    }

    public BotTG(String botName, String botToken, List<SettingsManager.CategoryEntry> categories) {
        super(botName, botToken, categories);
    }

    /**
     * Backward-compatibility wrapper for legacy code expecting BotTG.Algoritm.
     */
    public class Algoritm extends SearchAlgorithmEngine {
        public Algoritm(String categoryUrl, String chatId, String categoryName, int baseDelayMs, int initialStaggerMs) {
            super(categoryUrl, chatId, categoryName, baseDelayMs, initialStaggerMs,
                    (res, link) -> BotTG.this.dispatchNotification(res, link, categoryName));
        }

        public Algoritm(String categoryUrl, String chatId, String categoryName) {
            this(categoryUrl, chatId, categoryName, SearchAlgorithmEngine.MIN_THREAD_DELAY_MS, 0);
        }
    }
}
