package PropertySeacher;

import com.propertysearcher.desktop.SettingsManager;

import java.util.List;

/**
 * @deprecated Legacy adapter retained for backwards compatibility.
 * Use {@link com.propertysearcher.desktop.BotTG} or {@link com.propertysearcher.desktop.bot.TelegramBotService}.
 */
@Deprecated
public class BotTG extends com.propertysearcher.desktop.BotTG {

    public BotTG(String botName, String botToken) {
        super(botName, botToken);
    }

    public BotTG(String botName, String botToken, List<SettingsManager.CategoryEntry> categories) {
        super(botName, botToken, categories);
    }
}
