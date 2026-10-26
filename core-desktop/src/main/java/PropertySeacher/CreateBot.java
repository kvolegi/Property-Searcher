package PropertySeacher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

public class CreateBot {

    private static final Logger log = LoggerFactory.getLogger(CreateBot.class);
    private String botName;
    private String botToken;
    private List<SettingsManager.CategoryEntry> categories;
    private BotTG botTG;

    public CreateBot(String botName, String botToken, List<SettingsManager.CategoryEntry> categories) {
        this.botName = botName;
        this.botToken = botToken;
        this.categories = categories;
    }

    public CreateBot(String botName, String botToken) {
        this(botName, botToken, null);
    }

    public void createBot() throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            log.info("Реєстрація бота...");
            botTG = new BotTG(botName, botToken, categories);
            telegramBotsApi.registerBot(botTG);
            botTG.triggerStartFromGui();
        } catch (TelegramApiRequestException e) {
            log.error("Помилка реєстрації бота.", e);
        }
        log.info("Телеграм-бот готовий до роботи!");
    }

    public void updateLanguage(I18n.Language lang) {
        if (botTG != null) {
            botTG.onLanguageChanged(lang);
        }
    }
}
