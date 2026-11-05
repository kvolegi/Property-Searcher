package com.propertysearcher.desktop;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class SettingsManager {

    public static class CategoryEntry {
        public String name;
        public String url;
        public boolean active;

        public CategoryEntry(String name, String url, boolean active) {
            this.name = name;
            this.url = url;
            this.active = active;
        }
    }

    public enum UiColorTheme {
        MINT,
        GRAY;

        public static UiColorTheme fromString(String val) {
            if (val == null) return MINT;
            if ("GRAY".equalsIgnoreCase(val) || "GREY".equalsIgnoreCase(val) || "CORPORATE_PRO".equalsIgnoreCase(val) || "CORPORATE".equalsIgnoreCase(val)) {
                return GRAY;
            }
            return MINT;
        }
    }

    public enum UiFontMode {
        COMIC_SANS("Comic Sans MS"),
        SYSTEM("Segoe UI");

        public final String fontName;

        UiFontMode(String fontName) {
            this.fontName = fontName;
        }

        public static UiFontMode fromString(String val) {
            if (val == null) return COMIC_SANS;
            if ("SYSTEM".equalsIgnoreCase(val) || "STANDARD".equalsIgnoreCase(val) || "SEGOE".equalsIgnoreCase(val) || "CORPORATE_PRO".equalsIgnoreCase(val)) {
                return SYSTEM;
            }
            return COMIC_SANS;
        }
    }

    public enum UiThemeMode {
        COMIC_SANS("Comic Sans MS"),
        CORPORATE_PRO("Segoe UI");

        public final String fontName;

        UiThemeMode(String fontName) {
            this.fontName = fontName;
        }

        public static UiThemeMode fromString(String val) {
            if (val == null) return COMIC_SANS;
            if ("CORPORATE_PRO".equalsIgnoreCase(val) || "CORPORATE".equalsIgnoreCase(val) || "MODERN".equalsIgnoreCase(val)) {
                return CORPORATE_PRO;
            }
            return COMIC_SANS;
        }
    }

    public static class AppSettings {
        public String botName = "";
        public String botToken = "";
        public I18n.Language language = I18n.Language.UA;
        public CurrencyService.CurrencyMode currency = CurrencyService.CurrencyMode.DUAL;
        public UiColorTheme uiColor = UiColorTheme.MINT;
        public UiFontMode uiFont = UiFontMode.COMIC_SANS;
        public boolean languageConfigured = false;
        public List<CategoryEntry> categories = new ArrayList<>();
    }

    public static File getUserConfigDir() {
        String userHome = System.getProperty("user.home", ".");
        File dir = new File(userHome, ".propertysearcher");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getSeenAdsFile() {
        return new File(getUserConfigDir(), "seen_ads.txt");
    }

    public static File getSettingsFile() {
        // 1. User home directory ~/.propertysearcher/settings.txt
        File userFile = new File(getUserConfigDir(), "settings.txt");
        if (userFile.exists()) return userFile;

        // 2. Current working directory
        File f1 = new File("settings.txt");
        if (f1.exists()) {
            try {
                Files.copy(f1.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return userFile;
            } catch (Exception ignored) {
                return f1;
            }
        }

        // 3. Relative to JAR / app bundle location
        try {
            File jarDir = new File(SettingsManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            if (jarDir != null) {
                File f2 = new File(jarDir, "settings.txt");
                if (f2.exists()) {
                    try {
                        Files.copy(f2.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        return userFile;
                    } catch (Exception ignored) {
                        return f2;
                    }
                }
                File parent = jarDir.getParentFile();
                if (parent != null) {
                    File f3 = new File(parent, "settings.txt");
                    if (f3.exists()) {
                        try {
                            Files.copy(f3.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            return userFile;
                        } catch (Exception ignored) {
                            return f3;
                        }
                    }
                    File f4 = new File(parent.getParentFile(), "settings.txt");
                    if (f4 != null && f4.exists()) {
                        try {
                            Files.copy(f4.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            return userFile;
                        } catch (Exception ignored) {
                            return f4;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return userFile;
    }

    public static File getLastChatIdFile() {
        File userFile = new File(getUserConfigDir(), "last_chat_id.txt");
        if (userFile.exists()) return userFile;
        File sf = getSettingsFile();
        if (sf != null && sf.getParentFile() != null) {
            File parentFile = new File(sf.getParentFile(), "last_chat_id.txt");
            if (parentFile.exists()) return parentFile;
        }
        return userFile;
    }

    public static String getLastChatId() {
        File f = getLastChatIdFile();
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line = br.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            } catch (Exception ignored) {}
        }
        File fallback = new File("last_chat_id.txt");
        if (fallback.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(fallback))) {
                String line = br.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static void saveLastChatId(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) return;
        File f = getLastChatIdFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))) {
            bw.write(chatId.trim());
        } catch (Exception ignored) {}

        File f2 = new File("last_chat_id.txt");
        if (!f2.getAbsolutePath().equals(f.getAbsolutePath())) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(f2, false))) {
                bw.write(chatId.trim());
            } catch (Exception ignored) {}
        }
    }

    public static boolean isDirective(String line) {
        if (line == null) return false;
        String t = line.trim().toUpperCase();
        return t.startsWith("LANGUAGE=") || t.startsWith("LANGUAGE:") ||
               t.startsWith("CURRENCY=") || t.startsWith("CURRENCY:") ||
               t.startsWith("UI_COLOR=") || t.startsWith("UI_COLOR:") ||
               t.startsWith("UI_FONT=") || t.startsWith("UI_FONT:") ||
               t.startsWith("UI_THEME=") || t.startsWith("UI_THEME:") ||
               t.startsWith("BOT_NAME=") || t.startsWith("BOT_TOKEN=");
    }

    public static String sanitizeCredential(String val) {
        if (val == null) return "";
        val = val.trim();
        if (isDirective(val) || val.contains("LANGUAGE=") || val.contains("UI_COLOR=") || val.contains("UI_FONT=") || val.contains("UI_THEME=")) {
            return "";
        }
        return val;
    }

    public static AppSettings loadSettings() {
        return loadSettingsFromFile(getSettingsFile());
    }

    public static AppSettings loadSettingsFromFile(File file) {
        AppSettings s = new AppSettings();
        if (file == null || !file.exists()) return s;

        List<String> rawLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawLines.add(line);
            }
        } catch (Exception e) {
            System.err.println("Помилка зчитування файлу " + file + ": " + e.getMessage());
            return s;
        }

        int lineIdx = 0;
        int total = rawLines.size();

        // Line 0: Bot Name (if not directive)
        if (lineIdx < total) {
            String l0 = rawLines.get(lineIdx).trim();
            if (!isDirective(l0)) {
                s.botName = sanitizeCredential(l0);
                lineIdx++;
            }
        }

        // Line 1: Bot Token (if not directive)
        if (lineIdx < total) {
            String l1 = rawLines.get(lineIdx).trim();
            if (!isDirective(l1)) {
                s.botToken = sanitizeCredential(l1);
                lineIdx++;
            }
        }

        // Parse remaining lines
        while (lineIdx < total) {
            String line = rawLines.get(lineIdx++).trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("BOT_NAME=") || line.startsWith("BOT_NAME:")) {
                s.botName = sanitizeCredential(line.substring(9).trim());
            } else if (line.startsWith("BOT_TOKEN=") || line.startsWith("BOT_TOKEN:")) {
                s.botToken = sanitizeCredential(line.substring(10).trim());
            } else if (line.startsWith("LANGUAGE=") || line.startsWith("LANGUAGE:")) {
                String val = line.substring(9).trim();
                s.language = I18n.Language.fromString(val);
                s.languageConfigured = true;
            } else if (line.startsWith("CURRENCY=") || line.startsWith("CURRENCY:")) {
                String val = line.substring(9).trim().toUpperCase();
                try { s.currency = CurrencyService.CurrencyMode.valueOf(val); } catch (Exception ignored) {}
            } else if (line.startsWith("UI_COLOR=") || line.startsWith("UI_COLOR:")) {
                String val = line.substring(9).trim();
                s.uiColor = UiColorTheme.fromString(val);
            } else if (line.startsWith("UI_FONT=") || line.startsWith("UI_FONT:")) {
                String val = line.substring(8).trim();
                s.uiFont = UiFontMode.fromString(val);
            } else if (line.startsWith("UI_THEME=") || line.startsWith("UI_THEME:")) {
                String val = line.substring(9).trim();
                UiThemeMode th = UiThemeMode.fromString(val);
                if (th == UiThemeMode.CORPORATE_PRO) {
                    s.uiColor = UiColorTheme.GRAY;
                    s.uiFont = UiFontMode.SYSTEM;
                }
            } else {
                // Category title
                boolean active = true;
                if (line.startsWith("#OFF#") || line.startsWith("[OFF]") || line.startsWith("#DISABLED#")) {
                    active = false;
                    line = line.replaceFirst("^(#OFF#|\\[OFF\\]|#DISABLED#)\\s*", "").trim();
                }

                if (lineIdx < total) {
                    String url = rawLines.get(lineIdx++).trim();
                    if (!url.isEmpty() && !isDirective(url)) {
                        s.categories.add(new CategoryEntry(line, url, active));
                    }
                }
            }
        }
        return s;
    }

    public static void saveToFile(File file, AppSettings s) {
        if (file == null) return;
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.write(sanitizeCredential(s.botName) + "\n");
                writer.write(sanitizeCredential(s.botToken) + "\n");
                writer.write("LANGUAGE=" + (s.language != null ? s.language.name() : "UA") + "\n");
                writer.write("CURRENCY=" + (s.currency != null ? s.currency.name() : "DUAL") + "\n");
                writer.write("UI_COLOR=" + (s.uiColor != null ? s.uiColor.name() : "MINT") + "\n");
                writer.write("UI_FONT=" + (s.uiFont != null ? s.uiFont.name() : "COMIC_SANS") + "\n");
                writer.write("UI_THEME=" + (s.uiColor == UiColorTheme.GRAY ? "CORPORATE_PRO" : "COMIC_SANS") + "\n\n");

                if (s.categories != null) {
                    for (CategoryEntry c : s.categories) {
                        if (c.active) {
                            writer.write(c.name + "\n" + c.url + "\n\n");
                        } else {
                            writer.write("#OFF# " + c.name + "\n" + c.url + "\n\n");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Помилка збереження налаштувань у " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    public static List<File> getAllPossibleSettingsFiles() {
        List<File> list = new ArrayList<>();
        File userFile = new File(getUserConfigDir(), "settings.txt");
        list.add(userFile);
        list.add(new File("settings.txt"));

        try {
            File jarDir = new File(SettingsManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            if (jarDir != null) {
                File f2 = new File(jarDir, "settings.txt");
                if (!list.contains(f2)) list.add(f2);
                File parent = jarDir.getParentFile();
                if (parent != null) {
                    File f3 = new File(parent, "settings.txt");
                    if (!list.contains(f3)) list.add(f3);
                    File p2 = parent.getParentFile();
                    if (p2 != null) {
                        File f4 = new File(p2, "settings.txt");
                        if (!list.contains(f4)) list.add(f4);
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void saveSettings(AppSettings s) {
        File primary = getSettingsFile();
        saveToFile(primary, s);
        for (File f : getAllPossibleSettingsFiles()) {
            if (f != null && !f.getAbsolutePath().equals(primary.getAbsolutePath()) && f.exists()) {
                saveToFile(f, s);
            }
        }
    }

    public static void saveSettings(String botName, String botToken, List<CategoryEntry> categories) {
        AppSettings s = loadSettings();
        s.botName = sanitizeCredential(botName);
        s.botToken = sanitizeCredential(botToken);
        if (categories != null) s.categories = categories;
        saveSettings(s);
    }

    public static void saveToFile(File file, String botName, String botToken, List<CategoryEntry> categories) {
        AppSettings s = loadSettingsFromFile(file);
        s.botName = sanitizeCredential(botName);
        s.botToken = sanitizeCredential(botToken);
        if (categories != null) s.categories = categories;
        saveToFile(file, s);
    }

    public static List<CategoryEntry> loadCategories() {
        return loadSettings().categories;
    }

    public static List<CategoryEntry> loadCategoriesFromFile(File file) {
        return loadSettingsFromFile(file).categories;
    }

    public static CurrencyService.CurrencyMode getCurrencyPreference() {
        return loadSettings().currency;
    }

    public static void setCurrencyPreference(CurrencyService.CurrencyMode mode) {
        if (mode == null) mode = CurrencyService.CurrencyMode.DUAL;
        AppSettings s = loadSettings();
        s.currency = mode;
        saveSettings(s);
    }

    public static UiColorTheme getColorPreference() {
        return loadSettings().uiColor;
    }

    public static void setColorPreference(UiColorTheme color) {
        if (color == null) color = UiColorTheme.MINT;
        AppSettings s = loadSettings();
        s.uiColor = color;
        saveSettings(s);
    }

    public static UiFontMode getFontPreference() {
        return loadSettings().uiFont;
    }

    public static void setFontPreference(UiFontMode font) {
        if (font == null) font = UiFontMode.COMIC_SANS;
        AppSettings s = loadSettings();
        s.uiFont = font;
        saveSettings(s);
    }

    public static UiThemeMode getUiThemePreference() {
        UiColorTheme col = getColorPreference();
        return col == UiColorTheme.GRAY ? UiThemeMode.CORPORATE_PRO : UiThemeMode.COMIC_SANS;
    }

    public static void setUiThemePreference(UiThemeMode mode) {
        if (mode == null) mode = UiThemeMode.COMIC_SANS;
        setColorPreference(mode == UiThemeMode.CORPORATE_PRO ? UiColorTheme.GRAY : UiColorTheme.MINT);
        setFontPreference(mode == UiThemeMode.CORPORATE_PRO ? UiFontMode.SYSTEM : UiFontMode.COMIC_SANS);
    }

    public static boolean isLanguageConfigured() {
        return loadSettings().languageConfigured;
    }

    public static I18n.Language getLanguage() {
        I18n.Language lang = loadSettings().language;
        I18n.setLanguage(lang);
        return lang;
    }

    public static void setLanguage(I18n.Language lang) {
        if (lang == null) lang = I18n.Language.UA;
        I18n.setLanguage(lang);
        AppSettings s = loadSettings();
        s.language = lang;
        s.languageConfigured = true;
        saveSettings(s);
    }
}
