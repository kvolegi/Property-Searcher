package PropertySeacher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

public class TestBotLauncher {
    public static void main(String[] args) {
        String botName = "Property_Searcher_bot";
        String botToken = "8937208825:AAHKwl9aNpWLAeVRXB3fhgxbquQhP8MsVKQ";

        File sf = SettingsManager.getSettingsFile();
        if (sf != null && sf.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(sf))) {
                String line1 = br.readLine();
                String line2 = br.readLine();
                if (line1 != null && !line1.trim().isEmpty()) botName = line1.trim();
                if (line2 != null && !line2.trim().isEmpty()) botToken = line2.trim();
            } catch (Exception ignored) {}
        }

        if (System.getenv("TELEGRAM_BOT_TOKEN") != null) {
            botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        }

        List<SettingsManager.CategoryEntry> categories = SettingsManager.loadCategories();
        String lastChatId = SettingsManager.getLastChatId();

        System.out.println("Starting Property Searcher for bot: @" + botName);
        System.out.println("Active categories count: " + categories.size());
        for (SettingsManager.CategoryEntry c : categories) {
            System.out.println("  • " + c.name + " -> " + c.url + " [Active: " + c.active + "]");
        }
        System.out.println("Target User Chat ID: " + lastChatId);

        try {
            CreateBot createBot = new CreateBot(botName, botToken, categories);
            createBot.createBot();
            System.out.println("✅ Bot is online and actively monitoring! Live alerts are streaming to Telegram chat: " + lastChatId);

            long startTime = System.currentTimeMillis();
            while (true) {
                Thread.sleep(30000);
                long elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000;
                System.out.println("⏳ [Active Monitoring Heartbeat] Running for " + elapsedMinutes + " min... Actively checking OLX and DOM.RIA for updates.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
