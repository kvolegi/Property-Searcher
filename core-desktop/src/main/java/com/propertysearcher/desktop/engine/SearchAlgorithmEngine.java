package com.propertysearcher.desktop.engine;

import com.propertysearcher.desktop.ParserOLX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Enterprise Scraper & Sniping Algorithm Engine.
 * Decoupled from windowing toolkits and Telegram bot lifecycle.
 * Features:
 * - Sub-2-second organic listing detection.
 * - Sliding window ring-buffer deduplication (capacity: 50).
 * - Staggered category scheduling to prevent CloudFront IP rate limiting.
 */
public class SearchAlgorithmEngine implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(SearchAlgorithmEngine.class);

    public static final double TARGET_SAFE_GLOBAL_RPS = 2.2;
    public static final int MIN_THREAD_DELAY_MS = 1800;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private volatile boolean shouldRun = true;

    private final String categoryUrl;
    private final String chatId;
    private final String categoryName;
    private final int baseDelayMs;
    private final int initialStaggerMs;
    private final BiConsumer<ParserOLX.PropertyResult, String> onNewPropertyFound;

    private final String[] lastAdLinks = new String[50];
    private String lastSentAdLink = "";

    public SearchAlgorithmEngine(String categoryUrl,
                                 String chatId,
                                 String categoryName,
                                 int baseDelayMs,
                                 int initialStaggerMs,
                                 BiConsumer<ParserOLX.PropertyResult, String> onNewPropertyFound) {
        this.categoryUrl = categoryUrl;
        this.chatId = chatId;
        this.categoryName = categoryName;
        this.baseDelayMs = baseDelayMs;
        this.initialStaggerMs = initialStaggerMs;
        this.onNewPropertyFound = onNewPropertyFound;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Algorithm-Scheduler-" + categoryName);
            t.setDaemon(true);
            return t;
        });
    }

    public SearchAlgorithmEngine(String categoryUrl,
                                 String chatId,
                                 String categoryName,
                                 BiConsumer<ParserOLX.PropertyResult, String> onNewPropertyFound) {
        this(categoryUrl, chatId, categoryName, MIN_THREAD_DELAY_MS, 0, onNewPropertyFound);
    }

    public static int calculateAdaptiveDelay(int activeCount) {
        if (activeCount <= 1) {
            return MIN_THREAD_DELAY_MS;
        }
        long computed = Math.round((activeCount * 1000.0) / TARGET_SAFE_GLOBAL_RPS);
        return (int) Math.max(MIN_THREAD_DELAY_MS, computed);
    }

    public synchronized void start() {
        if (!shouldRun) return;
        initBaseline();
        scheduledFuture = scheduler.scheduleWithFixedDelay(this, initialStaggerMs, baseDelayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        if (!shouldRun || Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            boolean foundNew = search();
            if (foundNew && shouldRun) {
                search();
            }
        } catch (Exception e) {
            log.error("Error during monitoring pass for [{}]: {}", categoryName, e.getMessage());
        }
    }

    public synchronized void stop() {
        shouldRun = false;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        scheduler.shutdownNow();
    }

    /**
     * Backward-compatibility alias for legacy code calling stopAlgoritm().
     */
    public synchronized void stopAlgoritm() {
        stop();
    }

    private void initBaseline() {
        try {
            ParserOLX ps = new ParserOLX(categoryUrl);
            List<ParserOLX.PropertyResult> initialAds = ps.getTopProperties(30);
            for (int i = 1; i < initialAds.size(); i++) {
                ParserOLX.PropertyResult res = initialAds.get(i);
                if (res != null && res.link != null && !res.link.isBlank()) {
                    String clean = res.link.split("\\?")[0];
                    updateLastAdLinks(clean);
                }
            }
            log.info("Baseline initialized for [{}]: {} active listings buffered",
                    categoryName, Math.max(0, initialAds.size() - 1));
        } catch (Exception e) {
            log.warn("Could not init baseline for [{}]: {}", categoryName, e.getMessage());
        }
    }

    public boolean search() {
        try {
            ParserOLX ps = new ParserOLX(categoryUrl);
            List<ParserOLX.PropertyResult> candidates = ps.getTopProperties(5);
            if (candidates == null || candidates.isEmpty()) {
                return false;
            }

            List<ParserOLX.PropertyResult> newAds = new ArrayList<>();
            for (ParserOLX.PropertyResult res : candidates) {
                if (res != null && res.link != null && !res.link.isBlank()) {
                    String cleanLink = res.link.split("\\?")[0];
                    if (!cleanLink.equals(lastSentAdLink) && !isDuplicate(cleanLink)) {
                        newAds.add(res);
                    }
                }
            }

            if (newAds.isEmpty()) {
                return false;
            }

            for (int i = newAds.size() - 1; i >= 0; i--) {
                ParserOLX.PropertyResult res = newAds.get(i);
                String cleanLink = res.link.split("\\?")[0];
                lastSentAdLink = cleanLink;
                updateLastAdLinks(cleanLink);

                if (onNewPropertyFound != null) {
                    onNewPropertyFound.accept(res, cleanLink);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Search error in [{}]: {}", categoryName, e.getMessage());
            return false;
        }
    }

    private void updateLastAdLinks(String adLink) {
        for (int i = lastAdLinks.length - 1; i > 0; i--) {
            lastAdLinks[i] = lastAdLinks[i - 1];
        }
        lastAdLinks[0] = adLink;
    }

    public boolean isDuplicate(String adLink) {
        for (String link : lastAdLinks) {
            if (adLink != null && adLink.equals(link)) {
                return true;
            }
        }
        return false;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryUrl() {
        return categoryUrl;
    }

    public String getChatId() {
        return chatId;
    }
}
