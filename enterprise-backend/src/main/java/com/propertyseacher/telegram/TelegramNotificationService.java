package com.propertyseacher.telegram;

import com.propertyseacher.domain.entity.User;
import com.propertyseacher.domain.repository.UserRepository;
import com.propertyseacher.scraper.PropertyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final UserRepository userRepository;
    @Lazy
    private final PropertyTelegramBot telegramBot;

    public void broadcastNewProperty(PropertyDto dto) {
        broadcastPropertyMessage(dto, "🏠 *Нове оголошення!*");
    }

    public void broadcastPriceChange(PropertyDto dto, String changeInfo) {
        broadcastPropertyMessage(dto, "🔔 *Зміна ціни!* " + changeInfo);
    }

    private void broadcastPropertyMessage(PropertyDto dto, String header) {
        List<User> activeUsers = userRepository.findAllByActiveTrue();
        if (activeUsers.isEmpty()) {
            log.info("No active users to notify about property: {}", dto.title());
            return;
        }

        String caption = String.format(
                "%s [%s]\n\n" +
                "📌 *%s*\n" +
                "💰 *Ціна:* %s\n" +
                "📍 *Джерело:* %s",
                header,
                escapeMarkdown(dto.category()),
                escapeMarkdown(dto.title()),
                escapeMarkdown(dto.price()),
                dto.platform()
        );

        // Modern Inline Keyboard
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();

        InlineKeyboardButton urlButton = new InlineKeyboardButton();
        urlButton.setText("🔗 Відкрити на " + dto.platform());
        urlButton.setUrl(dto.url());
        row1.add(urlButton);

        rows.add(row1);
        keyboard.setKeyboard(rows);

        // Send photo if available
        boolean hasPhotos = dto.photoUrls() != null && !dto.photoUrls().isEmpty();
        String primaryPhoto = hasPhotos ? dto.photoUrls().get(0) : null;

        for (User user : activeUsers) {
            String chatId = user.getTelegramChatId().toString();
            try {
                if (primaryPhoto != null && !primaryPhoto.isBlank()) {
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId);
                    sendPhoto.setPhoto(new InputFile(primaryPhoto));
                    sendPhoto.setCaption(caption);
                    sendPhoto.setParseMode("Markdown");
                    sendPhoto.setReplyMarkup(keyboard);
                    telegramBot.execute(sendPhoto);
                } else {
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setText(caption);
                    message.enableMarkdown(true);
                    message.setReplyMarkup(keyboard);
                    telegramBot.execute(message);
                }
                log.info("Notification sent to chat ID: {}", chatId);
            } catch (TelegramApiException e) {
                log.warn("Failed to send photo/message to {}: {}. Retrying with plain text...", chatId, e.getMessage());
                // Fallback to plain SendMessage if photo fails to load
                try {
                    SendMessage fallback = new SendMessage(chatId, caption);
                    fallback.enableMarkdown(true);
                    fallback.setReplyMarkup(keyboard);
                    telegramBot.execute(fallback);
                } catch (Exception ex) {
                    log.error("Failed text fallback to {}: {}", chatId, ex.getMessage());
                }
            }
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("`", "\\`");
    }
}
