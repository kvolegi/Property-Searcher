package com.propertysearcher.desktop.engine;

import com.propertysearcher.desktop.ParserOLX;
import com.propertysearcher.desktop.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final int MAX_BUFFER_CAPACITY = 1000;
    private final Deque<String> recentAdQueue = new ArrayDeque<>(MAX_BUFFER_CAPACITY);
    private final Set<String> recentAdSet = new HashSet<>(MAX_BUFFER_CAPACITY);
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

    private synchronized void loadSeenAdsFromFile() {
        File file = SettingsManager.getSeenAdsFile();
        if (file == null || !file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !recentAdSet.contains(line)) {
                    if (recentAdQueue.size() >= MAX_BUFFER_CAPACITY) {
                        String oldest = recentAdQueue.removeLast();
                        recentAdSet.remove(oldest);
                    }
                    recentAdQueue.addFirst(line);
                    recentAdSet.add(line);
                }
            }
        } catch (Exception e) {
            log.warn("Could not read seen ads from file: {}", e.getMessage());
        }
    }

    private synchronized void appendSeenAdToFile(String adLink) {
        if (adLink == null || adLink.isBlank()) return;
        File file = SettingsManager.getSeenAdsFile();
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
                bw.write(adLink);
                bw.newLine();
            }
        } catch (Exception e) {
            log.warn("Could not append seen ad to file: {}", e.getMessage());
        }
    }

    private void initBaseline() {
        loadSeenAdsFromFile();
        try {
            ParserOLX ps = new ParserOLX(categoryUrl);
            List<ParserOLX.PropertyResult> initialAds = ps.getTopProperties(30);
            for (int i = 0; i < initialAds.size(); i++) {
                ParserOLX.PropertyResult res = initialAds.get(i);
                if (res != null && res.link != null && !res.link.isBlank()) {
                    String clean = res.link.split("\\?")[0];
                    updateLastAdLinks(clean);
                }
            }
            log.info("Baseline initialized for [{}]: {} active listings buffered (total seen: {})",
                    categoryName, initialAds.size(), recentAdSet.size());
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

    private synchronized void updateLastAdLinks(String adLink) {
        if (adLink == null || adLink.isBlank()) return;
        boolean isNew = !recentAdSet.contains(adLink);
        if (recentAdSet.contains(adLink)) {
            recentAdQueue.remove(adLink);
        } else if (recentAdQueue.size() >= MAX_BUFFER_CAPACITY) {
            String oldest = recentAdQueue.removeLast();
            recentAdSet.remove(oldest);
        }
        recentAdQueue.addFirst(adLink);
        recentAdSet.add(adLink);

        if (isNew) {
            appendSeenAdToFile(adLink);
        }
    }

    public synchronized boolean isDuplicate(String adLink) {
        return adLink != null && recentAdSet.contains(adLink);
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
