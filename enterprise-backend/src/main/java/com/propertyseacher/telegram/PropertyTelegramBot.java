package com.propertyseacher.telegram;

import com.propertyseacher.domain.entity.SearchTarget;
import com.propertyseacher.domain.entity.User;
import com.propertyseacher.domain.repository.SearchTargetRepository;
import com.propertyseacher.domain.repository.UserRepository;
import com.propertyseacher.service.TrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class PropertyTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(PropertyTelegramBot.class);

    private final String botUsername;
    private final UserRepository userRepository;
    private final SearchTargetRepository searchTargetRepository;

    @Autowired
    @Lazy
    private TrackingService trackingService;

    public PropertyTelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            UserRepository userRepository,
            SearchTargetRepository searchTargetRepository) {
        super(botToken);
        this.botUsername = botUsername;
        this.userRepository = userRepository;
        this.searchTargetRepository = searchTargetRepository;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();
        String firstName = update.getMessage().getFrom().getFirstName();

        // Register or retrieve user
        User user = userRepository.findByTelegramChatId(chatId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .telegramChatId(chatId)
                        .username(username)
                        .firstName(firstName)
                        .active(true)
                        .build()));

        if (messageText.equalsIgnoreCase("/start")) {
            sendWelcome(chatId, firstName);
        } else if (messageText.equalsIgnoreCase("/targets") || messageText.contains("Категорії")) {
            showTargets(chatId);
        } else if (messageText.equalsIgnoreCase("/search") || messageText.contains("Пошук зараз")) {
            sendResponse(chatId, "⚡ Запуск сканування OLX та DOM.RIA...");
            if (trackingService != null) {
                new Thread(trackingService::trackAllTargets).start();
            }
        } else if (messageText.equalsIgnoreCase("/mode") || messageText.contains("Режим")) {
            String currentMode = trackingService != null ? trackingService.getCurrentMode() : "LIVE_SNIPE";
            sendResponse(chatId, "⚙️ *Поточний режим:* `" + currentMode + "`\n\n" +
                    "• *LIVE_SNIPE* — ультрашвидке відстеження без затримок I/O (пам'ять).\n" +
                    "• *DATABASE_ANALYTICS* — повна аналітика, дедуплікація та історія цін.");
        } else if (messageText.equalsIgnoreCase("/botfather") || messageText.contains("Команди для BotFather")) {
            sendBotFatherHelp(chatId);
        } else if (messageText.equalsIgnoreCase("/stop") || messageText.contains("Пауза")) {
            user.setActive(false);
            userRepository.save(user);
            sendResponse(chatId, "⏸ Сповіщення призупинено. Натисніть /resume для продовження.");
        } else if (messageText.equalsIgnoreCase("/resume") || messageText.contains("Відновити")) {
            user.setActive(true);
            userRepository.save(user);
            sendResponse(chatId, "▶️ Сповіщення знову активні!");
        } else {
            sendResponse(chatId, "Я розумію команди меню нижче або /start, /search, /targets, /mode, /botfather");
        }
    }

    private void sendWelcome(Long chatId, String firstName) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("👋 Вітаємо у *Property Searcher 3.0* (OLX & DOM.RIA)!\n\n" +
                "Бот автоматично відстежує появу найновіших пропозицій житла та надсилає їх разом із фотографіями та кнопками переходу.\n\n" +
                "Оберіть дію на клавіатурі або введіть /botfather для налаштування меню у BotFather.");
        message.enableMarkdown(true);
        message.setReplyMarkup(createPersistentMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send welcome to {}: {}", chatId, e.getMessage());
        }
    }

    private void showTargets(Long chatId) {
        List<SearchTarget> targets = searchTargetRepository.findAllByActiveTrue();
        if (targets.isEmpty()) {
            sendResponse(chatId, "Наразі немає активних напрямків моніторингу.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 *Активні напрямки моніторингу (OLX + DOM.RIA):*\n\n");
        for (SearchTarget t : targets) {
            sb.append("• *").append(t.getTitle()).append("* [").append(t.getPlatform()).append("]\n");
        }
        sendResponse(chatId, sb.toString());
    }

    private void sendBotFatherHelp(Long chatId) {
        String script = "🛠 *Команди для налаштування меню в @BotFather:*\n\n" +
                "1. Відкрийте @BotFather\n" +
                "2. Надішліть команду `/setcommands`\n" +
                "3. Виберіть вашого бота\n" +
                "4. Скопіюйте та надішліть йому цей текст:\n\n" +
                "`start - Головне меню та запуск бота`\n" +
                "`search - Запустити сканування прямо зараз`\n" +
                "`targets - Список відстежуваних категорій`\n" +
                "`mode - Поточний режим роботи системи`\n" +
                "`stop - Призупинити сповіщення`\n" +
                "`resume - Відновити надсилання оголошень`\n" +
                "`botfather - Інструкція швидкого налаштування`";

        sendResponse(chatId, script);
    }

    private ReplyKeyboardMarkup createPersistentMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow r1 = new KeyboardRow();
        r1.add("🔍 Пошук зараз");
        r1.add("📋 Категорії");

        KeyboardRow r2 = new KeyboardRow();
        r2.add("⚙️ Режим");
        r2.add("⏸ Пауза");
        r2.add("▶️ Відновити");

        KeyboardRow r3 = new KeyboardRow();
        r3.add("🛠 Команди для BotFather");

        rows.add(r1);
        rows.add(r2);
        rows.add(r3);
        markup.setKeyboard(rows);

        return markup;
    }

    private void sendResponse(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.enableMarkdown(true);
        message.setReplyMarkup(createPersistentMenu());
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send reply to {}: {}", chatId, e.getMessage());
        }
    }
}
