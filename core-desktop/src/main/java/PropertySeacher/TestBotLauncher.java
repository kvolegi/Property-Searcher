package PropertySeacher;

public class TestBotLauncher {
    public static void main(String[] args) {
        String botName = "Property_Searcher_bot";
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN") != null ? 
                System.getenv("TELEGRAM_BOT_TOKEN") : "YOUR_BOT_TOKEN";
        System.out.println("Starting Property Searcher test run for: " + botName);
        System.out.println("Categories found: " + SettingsManager.loadCategories().size());
        System.out.println("Last Chat ID: " + SettingsManager.getLastChatId());

        try {
            CreateBot createBot = new CreateBot(botName, botToken);
            createBot.createBot();
            System.out.println("Bot is successfully registered and actively listening for new listings!");

            long startTime = System.currentTimeMillis();
            while (true) {
                Thread.sleep(30000);
                long elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000;
                System.out.println("⏳ [Active Monitoring Heartbeat] Running for " + elapsedMinutes + " min... Checking for new postings on OLX and DOM.RIA.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
