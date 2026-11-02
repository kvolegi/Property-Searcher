package com.propertysearcher.desktop.bot;

import com.propertysearcher.desktop.CategoryCatalog;
import com.propertysearcher.desktop.CurrencyService;
import com.propertysearcher.desktop.I18n;
import com.propertysearcher.desktop.ParserOLX;
import com.propertysearcher.desktop.SettingsManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles asynchronous formatting and delivery of listing cards and rich media groups.
 */
public class TelegramNotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationDispatcher.class);
    private static final ExecutorService notificationExecutor = Executors.newCachedThreadPool();

    public static void dispatchAsync(TelegramLongPollingBot bot,
                                     String chatId,
                                     String categoryName,
                                     ParserOLX.PropertyResult res,
                                     String cleanLink) {
        notificationExecutor.submit(() -> sendNotification(bot, chatId, categoryName, res, cleanLink));
    }

    public static void sendNotification(TelegramLongPollingBot bot,
                                        String chatId,
                                        String categoryName,
                                        ParserOLX.PropertyResult res,
                                        String cleanLink) {
        try {
            List<String> allPhotos = new ArrayList<>();
            if (res.photoUrls != null && !res.photoUrls.isEmpty()) {
                allPhotos.addAll(res.photoUrls);
            } else if (res.photoUrl != null && !res.photoUrl.isBlank()) {
                allPhotos.add(res.photoUrl);
            }

            if (allPhotos.size() <= 1 && res.link != null) {
                try {
                    Document adDoc = Jsoup.connect(res.link)
                            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .header("Accept-Language", "uk,en;q=0.9")
                            .timeout(5000)
                            .get();
                    Elements galleryImgs = adDoc.select("div.swiper-zoom-container img, div[data-cy=\"ad-photos\"] img, img[data-testid=\"gallery-image\"], div.gallery img");
                    for (Element gImg : galleryImgs) {
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

            if (allPhotos.size() > 1) {
                try {
                    SendMediaGroup smg = new SendMediaGroup();
                    smg.setChatId(chatId);
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
                        if (count >= 10) break;
                    }
                    smg.setMedias(mediaList);
                    bot.execute(smg);
                    return;
                } catch (Exception ex) {
                    log.warn("MediaGroup send failed, falling back to single photo: {}", ex.getMessage());
                }
            }

            if (!allPhotos.isEmpty()) {
                try {
                    SendPhoto photoMsg = new SendPhoto();
                    photoMsg.setChatId(chatId);
                    photoMsg.setDisableNotification(isNight);
                    photoMsg.setPhoto(new InputFile(allPhotos.get(0)));
                    photoMsg.setCaption(text);
                    photoMsg.setParseMode("HTML");
                    photoMsg.setReplyMarkup(cardInline);
                    bot.execute(photoMsg);
                    return;
                } catch (Exception ex) {
                    log.warn("Photo send failed, falling back to text: {}", ex.getMessage());
                }
            }

            SendMessage sm = new SendMessage();
            sm.setChatId(chatId);
            sm.setDisableNotification(isNight);
            sm.setText(text);
            sm.setParseMode("HTML");
            sm.setReplyMarkup(cardInline);
            bot.execute(sm);
        } catch (Exception ex) {
            log.error("Error dispatching notification for chat [{}]: {}", chatId, ex.getMessage());
        }
    }

    public static boolean isNightTime() {
        int hour = LocalTime.now(ZoneId.of("Europe/Kyiv")).getHour();
        return (hour >= 23 || hour < 7);
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
