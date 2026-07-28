package uk.co.pluckier.discordbot.webhooks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;

import uk.co.pluckier.discordbot.webparser.SportingLifeParser;
import uk.co.pluckier.discordbot.DiscordBot;
import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;
import uk.co.pluckier.discordbot.utils.RaceResultPersistence;
import uk.co.pluckier.discordbot.utils.SharedHttpClient;

public class ResultBotSender {

    private static final Logger log = LoggerFactory.getLogger(ResultBotSender.class);
    private RaceDataManager data;

    public static void main(String[] args) {
        log.info("--- Starting Single Test Run ---");
        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces();
        ResultBotSender bot = new ResultBotSender(data);
        bot.checkForNewResults();
        log.info("--- Single Test Run Finished ---");
    }

    // Use a daemon thread factory for the scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "result-bot-scheduler");
            t.setDaemon(true);
            return t;
        }
    });

    // Bounded, concurrent set for known results
    private final Set<String> knownResultsCache = ConcurrentHashMap.newKeySet();

    // Executor to handle sending individual results without blocking scheduler
    // Use a bounded queue to avoid unbounded accumulation and provide backpressure
    private final ThreadPoolExecutor resultSenderExecutor = new ThreadPoolExecutor(
            2, // core pool size
            2, // max pool size
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(200),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "result-sender");
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    public ResultBotSender(RaceDataManager data) {
        this.data = data;
        loadResultsFromStorage();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void loadResultsFromStorage() {
        File file = new File(ConfigLoader.getStorageFile());
        if (!file.exists()) {
            log.info("No local history file found. Starting with an empty cache.");
            return;
        }

        log.info("Loading historical results from " + ConfigLoader.getStorageFile() + "...");
        int loadedCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    knownResultsCache.add(parts[0].trim() + "|" + parts[1].trim());
                    loadedCount++;
                }
            }
            log.info("Successfully restored " + loadedCount + " past results into memory cache.");
        } catch (IOException e) {
            log.error("Failed to read history storage file: " + e.getMessage());
        }
    }

    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::checkForNewResults, 0, 5, TimeUnit.MINUTES);
        log.info("ResultBotSender started. Checking for new results every 5 minutes.");
    }

    private void checkForNewResults() {
        if (!isWithinRacingHours()) {
            log.info("Skipping check. Current UK time is outside active racing hours." +
                    " First race at " + data.getFirstRaceTime().toString() + " " +
                    " Last race at " + data.getLastRaceTime().toString());
            return;
        }

        org.jsoup.nodes.Document doc = null;
        try {
            String url = ConfigLoader.getResultsURL();

            // MEMORY FIX: Connect directly using JSoup to download the raw HTML text
            // string.
            // This drops the virtual Chrome rendering engine completely to save RAM.
            doc = org.jsoup.Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                    .timeout(15000) // 15-second safety limit so your background thread never hangs
                    .get();

            // Refactored to use dedicated Parser Class
            List<RaceResult> results = SportingLifeParser.parseRaceResults(doc);
            log.info("Found " + results.size() + " total results on page.");

            List<RaceResult> newResults = filterNewResults(results);
            log.info("Found " + newResults.size() + " genuine new results.");

            if (!newResults.isEmpty()) {
                log.info("Processing " + newResults.size() + " new results individually...");

                for (RaceResult singleResult : newResults) {
                    // Build payload synchronously so we do NOT capture heavy variables
                    String payload = DiscordWebhookClient.buildPayload(singleResult);

                    // Submit only the small payload to the executor
                    resultSenderExecutor.submit(() -> {
                        try {
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create(ConfigLoader.getResultsWebhookURL()))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                                    .build();

                            SharedHttpClient.getClient()
                                    .sendAsync(req, HttpResponse.BodyHandlers.ofString())
                                    .thenAccept(response -> {
                                        if (response.statusCode() == 204 || response.statusCode() == 200) {
                                            log.info("Dispatched webhook for result: " + singleResult.time()
                                                    + " " + singleResult.place());
                                        } else {
                                            log.error(
                                                    "Discord rejected payload with status " + response.statusCode());
                                        }
                                    })
                                    .exceptionally(ex -> {
                                        log.error("Webhook send failure: " + ex.getMessage());
                                        return null;
                                    });

                            // Update cache and persist (small strings)
                            knownResultsCache.add(singleResult.time() + "|" + singleResult.place());
                            RaceResultPersistence.storeSingleResult(singleResult);
                        } catch (Exception e) {
                            log.error("Error sending/storing result: " + e.getMessage());
                        }
                    });
                }

                // Triggers cache file pruning and returns the new memory mappings
                Set<String> synchronizedCache = RaceResultPersistence.pruneStorageFile();
                if (synchronizedCache != null) {
                    knownResultsCache.clear();
                    knownResultsCache.addAll(synchronizedCache);
                }
            } else {
                log.info("No new results found.");
            }
        } catch (Exception e) {
            log.error("Error tracking results: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // CRITICAL PROTECTION: This code runs NO MATTER WHAT.
            // Even if an unexpected error breaks the try loop above, the DOM tree is
            // cleared.
            if (doc != null) {
                doc.empty();
                doc = null; // Sever the reference so GC reclaims it instantly
            }
        }
    }

    private List<RaceResult> filterNewResults(List<RaceResult> currentResults) {
        List<RaceResult> newResults = new ArrayList<>();
        for (RaceResult webResult : currentResults) {
            String lookupKey = webResult.time() + "|" + webResult.place();
            if (!knownResultsCache.contains(lookupKey)) {
                newResults.add(webResult);
            }
        }
        return newResults;
    }

    private boolean isWithinRacingHours() {
        java.time.ZonedDateTime ukTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/London"));
        java.time.LocalTime currentTime = ukTime.toLocalTime();

        java.time.LocalTime startWindow = data.getFirstRaceTime();
        java.time.LocalTime endWindow = data.getLastRaceTime().plusMinutes(30);

        return !currentTime.isBefore(startWindow) && !currentTime.isAfter(endWindow);
    }

    public void stop() {
        log.info("Stopping ResultBotSender scheduler and executors...");
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            resultSenderExecutor.shutdown(); // allow tasks to finish
            if (!resultSenderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                resultSenderExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            resultSenderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
