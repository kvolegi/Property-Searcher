package PropertySeacher;

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

    public static File getSettingsFile() {
        // 1. Current working directory
        File f1 = new File("settings.txt");
        if (f1.exists()) return f1;

        // 2. Relative to JAR / app bundle location
        try {
            File jarDir = new File(SettingsManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            if (jarDir != null) {
                File f2 = new File(jarDir, "settings.txt");
                if (f2.exists()) return f2;
                File parent = jarDir.getParentFile();
                if (parent != null) {
                    File f3 = new File(parent, "settings.txt");
                    if (f3.exists()) return f3;
                    File f4 = new File(parent.getParentFile(), "settings.txt");
                    if (f4 != null && f4.exists()) return f4;
                }
            }
        } catch (Exception ignored) {}

        // 3. Known project locations
        String[] candidates = {
                "план 2/dist/settings.txt",
                "dist/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/план 2/dist/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/settings.txt"
        };
        for (String c : candidates) {
            File fc = new File(c);
            if (fc.exists()) return fc;
        }

        return new File("settings.txt");
    }

    public static File getLastChatIdFile() {
        File sf = getSettingsFile();
        if (sf != null && sf.getParentFile() != null) {
            return new File(sf.getParentFile(), "last_chat_id.txt");
        }
        return new File("last_chat_id.txt");
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
        // Fallback check
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

    public static List<CategoryEntry> loadCategories() {
        List<CategoryEntry> list = new ArrayList<>();
        File file = getSettingsFile();
        if (!file.exists()) return list;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // bot name
            reader.readLine(); // bot token

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("CURRENCY=") || line.startsWith("CURRENCY:") ||
                    line.startsWith("LANGUAGE=") || line.startsWith("LANGUAGE:")) {
                    continue; // Global configuration directives, not categories
                }

                boolean active = true;
                if (line.startsWith("#OFF#") || line.startsWith("[OFF]") || line.startsWith("#DISABLED#")) {
                    active = false;
                    line = line.replaceFirst("^(#OFF#|\\[OFF\\]|#DISABLED#)\\s*", "").trim();
                }

                String url = reader.readLine();
                if (url != null && !url.trim().isEmpty()) {
                    list.add(new CategoryEntry(line, url.trim(), active));
                }
            }
        } catch (Exception e) {
            System.err.println("Помилка зчитування категорій: " + e.getMessage());
        }
        return list;
    }

    public static CurrencyService.CurrencyMode getCurrencyPreference() {
        File file = getSettingsFile();
        if (!file.exists()) return CurrencyService.CurrencyMode.DUAL;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("CURRENCY=") || line.startsWith("CURRENCY:")) {
                    String val = line.substring(9).trim().toUpperCase();
                    try {
                        return CurrencyService.CurrencyMode.valueOf(val);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return CurrencyService.CurrencyMode.DUAL;
    }

    public static void setCurrencyPreference(CurrencyService.CurrencyMode mode) {
        if (mode == null) mode = CurrencyService.CurrencyMode.DUAL;
        File file = getSettingsFile();
        List<String> lines = new ArrayList<>();
        boolean replaced = false;

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("CURRENCY=") || line.trim().startsWith("CURRENCY:")) {
                        lines.add("CURRENCY=" + mode.name());
                        replaced = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!replaced) {
            // Insert after bot token (around line 2 or 3)
            int insertIdx = Math.min(2, lines.size());
            lines.add(insertIdx, "CURRENCY=" + mode.name());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            for (String l : lines) {
                writer.write(l + "\n");
            }
        } catch (Exception ignored) {}

        // Sync across other target locations
        String[] syncTargets = {
                "settings.txt",
                "план 2/dist/settings.txt",
                "план 2/^ Pseacher versions ^/Property Seacher 2.1.2/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/план 2/dist/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/settings.txt"
        };
        for (String st : syncTargets) {
            File sf = new File(st);
            if (sf.exists() && !sf.getAbsolutePath().equals(file.getAbsolutePath())) {
                try {
                    Files.copy(file.toPath(), sf.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean isLanguageConfigured() {
        File file = getSettingsFile();
        if (!file.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("LANGUAGE=") || line.trim().startsWith("LANGUAGE:")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static I18n.Language getLanguage() {
        File file = getSettingsFile();
        if (!file.exists()) return I18n.Language.UA;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("LANGUAGE=") || line.startsWith("LANGUAGE:")) {
                    String val = line.substring(9).trim();
                    return I18n.Language.fromString(val);
                }
            }
        } catch (Exception ignored) {}
        return I18n.Language.UA;
    }

    public static void setLanguage(I18n.Language lang) {
        if (lang == null) lang = I18n.Language.UA;
        I18n.setLanguage(lang);
        File file = getSettingsFile();
        List<String> lines = new ArrayList<>();
        boolean replaced = false;

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("LANGUAGE=") || line.trim().startsWith("LANGUAGE:")) {
                        lines.add("LANGUAGE=" + lang.name());
                        replaced = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!replaced) {
            int insertIdx = Math.min(2, lines.size());
            lines.add(insertIdx, "LANGUAGE=" + lang.name());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            for (String l : lines) {
                writer.write(l + "\n");
            }
        } catch (Exception ignored) {}

        String[] syncTargets = {
                "settings.txt",
                "план 2/dist/settings.txt",
                "план 2/^ Pseacher versions ^/Property Seacher 2.1.2/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/план 2/dist/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/settings.txt"
        };
        for (String st : syncTargets) {
            File sf = new File(st);
            if (sf.exists() && !sf.getAbsolutePath().equals(file.getAbsolutePath())) {
                try {
                    Files.copy(file.toPath(), sf.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
    }

    public static void saveSettings(String botName, String botToken, List<CategoryEntry> categories) {
        File target = getSettingsFile();
        saveToFile(target, botName, botToken, categories);

        // Also sync to other known locations if they exist
        String[] syncTargets = {
                "settings.txt",
                "план 2/dist/settings.txt",
                "план 2/^ Pseacher versions ^/Property Seacher 2.1.2/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/план 2/dist/settings.txt",
                System.getProperty("user.home") + "/Desktop/Antigravity/settings.txt"
        };

        for (String st : syncTargets) {
            File sf = new File(st);
            if (sf.exists() && !sf.getAbsolutePath().equals(target.getAbsolutePath())) {
                try {
                    Files.copy(target.toPath(), sf.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void saveToFile(File file, String botName, String botToken, List<CategoryEntry> categories) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.write((botName != null ? botName : "") + "\n");
                writer.write((botToken != null ? botToken : "") + "\n\n");

                for (CategoryEntry c : categories) {
                    if (c.active) {
                        writer.write(c.name + "\n" + c.url + "\n\n");
                    } else {
                        writer.write("#OFF# " + c.name + "\n" + c.url + "\n\n");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Помилка збереження налаштувань у " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}
