package com.propertysearcher.desktop.bot;

import com.propertysearcher.desktop.CategoryCatalog;
import com.propertysearcher.desktop.CurrencyService;
import com.propertysearcher.desktop.I18n;
import com.propertysearcher.desktop.SettingsManager;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating interactive Telegram Inline and Reply Keyboards.
 * Decoupled from bot networking and state handling.
 */
public class TelegramMenuFactory {

    public static ReplyKeyboardMarkup createPersistentMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🔍 Пошук / Search");
        row1.add("⏹ Зупинити / Stop");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📂 Категорії / Targets");
        row2.add("📊 Статус / Status");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("💵 Валюта / Currency");
        row3.add("🌐 Мова / Language");
        row3.add("❓ Допомога / Help");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    public static InlineKeyboardMarkup createLanguageMenu(I18n.Language current) {
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
        return markup;
    }

    public static InlineKeyboardMarkup createCurrencyMenu(CurrencyService.CurrencyMode current, boolean isEn) {
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
        return markup;
    }

    public static InlineKeyboardMarkup createCategoriesMenu(List<SettingsManager.CategoryEntry> categories, boolean isEn, I18n.Language lang) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < categories.size(); i++) {
            SettingsManager.CategoryEntry cat = categories.get(i);
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton statusBtn = new InlineKeyboardButton();
            String prefix = cat.active ? "🟢 " : "⚪️ ";
            String locName = CategoryCatalog.localizeCategoryName(cat.name, lang);
            statusBtn.setText(prefix + locName + (cat.active ? (isEn ? " (Active)" : " (Активно)") : (isEn ? " (Paused)" : " (Пауза)")));
            statusBtn.setCallbackData("cat_tog_" + i);

            InlineKeyboardButton delBtn = new InlineKeyboardButton();
            delBtn.setText("🗑");
            delBtn.setCallbackData("cat_del_" + i);

            row.add(statusBtn);
            row.add(delBtn);
            rows.add(row);
        }

        List<InlineKeyboardButton> actionRow1 = new ArrayList<>();
        InlineKeyboardButton addUrlBtn = new InlineKeyboardButton();
        addUrlBtn.setText(isEn ? "🔗 Add Search via URL" : "🔗 Додати пошук за посиланням");
        addUrlBtn.setCallbackData("btn_add_url");

        InlineKeyboardButton wizardBtn = new InlineKeyboardButton();
        wizardBtn.setText(isEn ? "✨ Smart Category Wizard" : "✨ Майстер нових категорій");
        wizardBtn.setCallbackData("wiz_start");

        actionRow1.add(addUrlBtn);
        actionRow1.add(wizardBtn);
        rows.add(actionRow1);

        List<InlineKeyboardButton> actionRow2 = new ArrayList<>();
        InlineKeyboardButton startBtn = new InlineKeyboardButton();
        startBtn.setText(isEn ? "▶️ Start Live Monitoring" : "▶️ Запустити моніторинг");
        startBtn.setCallbackData("cmd_search");

        InlineKeyboardButton stopBtn = new InlineKeyboardButton();
        stopBtn.setText(isEn ? "⏹ Pause All" : "⏹ Зупинити всі");
        stopBtn.setCallbackData("cmd_stop");

        actionRow2.add(startBtn);
        actionRow2.add(stopBtn);
        rows.add(actionRow2);

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup createHelpHubMarkup(String activeTab, boolean isEn) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> tabs1 = new ArrayList<>();
        InlineKeyboardButton tabStart = new InlineKeyboardButton();
        tabStart.setText(("help_tab_start".equals(activeTab) ? "👉 " : "") + (isEn ? "🚀 Quickstart" : "🚀 Старт"));
        tabStart.setCallbackData("help_tab_start");

        InlineKeyboardButton tabUrls = new InlineKeyboardButton();
        tabUrls.setText(("help_tab_urls".equals(activeTab) ? "👉 " : "") + (isEn ? "🔗 OLX URLs" : "🔗 Посилання"));
        tabUrls.setCallbackData("help_tab_urls");

        tabs1.add(tabStart);
        tabs1.add(tabUrls);
        rows.add(tabs1);

        List<InlineKeyboardButton> tabs2 = new ArrayList<>();
        InlineKeyboardButton tabWizard = new InlineKeyboardButton();
        tabWizard.setText(("help_tab_wizard".equals(activeTab) ? "👉 " : "") + (isEn ? "✨ Wizard" : "✨ Майстер"));
        tabWizard.setCallbackData("help_tab_wizard");

        InlineKeyboardButton tabCurrency = new InlineKeyboardButton();
        tabCurrency.setText(("help_tab_currency".equals(activeTab) ? "👉 " : "") + (isEn ? "💵 Currency" : "💵 Валюта"));
        tabCurrency.setCallbackData("help_tab_currency");

        tabs2.add(tabWizard);
        tabs2.add(tabCurrency);
        rows.add(tabs2);

        List<InlineKeyboardButton> tabs3 = new ArrayList<>();
        InlineKeyboardButton tabRates = new InlineKeyboardButton();
        tabRates.setText(("help_tab_rates".equals(activeTab) ? "👉 " : "") + (isEn ? "⚡️ Speed & RPS" : "⚡️ Швидкість"));
        tabRates.setCallbackData("help_tab_rates");

        InlineKeyboardButton tabFaq = new InlineKeyboardButton();
        tabFaq.setText(("help_tab_faq".equals(activeTab) ? "👉 " : "") + (isEn ? "💡 FAQ & BotFather" : "💡 FAQ"));
        tabFaq.setCallbackData("help_tab_faq");

        tabs3.add(tabRates);
        tabs3.add(tabFaq);
        rows.add(tabs3);

        markup.setKeyboard(rows);
        return markup;
    }
}
