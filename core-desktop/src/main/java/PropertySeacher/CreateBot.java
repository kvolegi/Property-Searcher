package PropertySeacher;

import java.util.List;

/**
 * @deprecated Use {@link com.propertysearcher.desktop.CreateBot}.
 */
@Deprecated
public class CreateBot extends com.propertysearcher.desktop.CreateBot {
    public CreateBot(String botName, String botToken, List<com.propertysearcher.desktop.SettingsManager.CategoryEntry> categories) {
        super(botName, botToken, categories);
    }
    public CreateBot(String botName, String botToken) {
        super(botName, botToken);
    }
}
