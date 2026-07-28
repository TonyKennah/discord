package uk.co.pluckier.discordbot.racedata;

import com.fasterxml.jackson.databind.JsonNode;
import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.filters.RaceFilter;
import uk.co.pluckier.discordbot.utils.SharedHttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaceDataManager {
    private static final Logger log = LoggerFactory.getLogger(RaceDataManager.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    // Cooldown duration boundary (10 minutes in milliseconds)
    private static final long FETCH_COOLDOWN_MS = 10 * 60 * 1000;

    private JsonNode rootNode;
    private LocalTime firstRaceTime;
    private LocalTime lastRaceTime;
    private LocalTime nextRaceTime;

    // Track when the last network call successfully finished
    private long lastFetchTimeMillis = 0;

    public void forceFetchTodaysRaces() {
        log.info("🧹 Forcing immediate data fetch (Resetting cooldown)...");
        this.lastFetchTimeMillis = 0; // Force the next call to NOT skip
        fetchTodaysRaces();
    }

    public void fetchTodaysRaces() {
        long currentTime = System.currentTimeMillis();

        // 1. COOLDOWN CHECK: Skip if a fresh call was executed less than 10 minutes ago
        // We ensure rootNode isn't null just in case the first boot needs an emergency
        // download
        if (this.rootNode != null && (currentTime - lastFetchTimeMillis < FETCH_COOLDOWN_MS)) {
            long secondsRemaining = (FETCH_COOLDOWN_MS - (currentTime - lastFetchTimeMillis)) / 1000;
            log.info("Skipping fresh network fetch. Data is cached. Cooldown remaining: {}s", secondsRemaining);
            return;
        }

        try {
            LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
            String formattedDate = today.format(DATE_FORMATTER);
            String urlString = ConfigLoader.getWebSite() + formattedDate + ConfigLoader.getFileExtension();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = SharedHttpClient.getClient()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // 2. SAFE PLACEMENT: Only clear old data now that we have a valid 200 OK stream
                // ready
                this.rootNode = null;
                this.firstRaceTime = null;
                this.lastRaceTime = null;

                try (InputStream responseStream = response.body()) {
                    this.rootNode = SharedHttpClient.getMapper().readTree(responseStream);
                    extractFirstAndLastRaceTimes();
                    extractNextRaceTime();

                    // Update timestamp upon a successful download process completion
                    this.lastFetchTimeMillis = System.currentTimeMillis();
                    log.info("Successfully fetched fresh race schedule data from server.");
                }
            } else {
                log.error("Failed to fetch data! Code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("An error occurred while fetching or parsing race data.", e);
        }
    }

    private void extractNextRaceTime() {
        if (this.rootNode == null || this.rootNode.isEmpty()) {
            return;
        }
        LocalTime currentTime = LocalTime.now(ZoneId.of("Europe/London"));
        Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, currentTime);
        if (nextRace.isPresent()) {
            this.nextRaceTime = LocalTime.parse(nextRace.get().path("time").asText(), TIME_FORMATTER);
        } else {
            this.nextRaceTime = currentTime;
        }

    }

    private void extractFirstAndLastRaceTimes() {
        if (this.rootNode == null || this.rootNode.isEmpty()) {
            return;
        }

        try {
            JsonNode meetings = rootNode;

            if (meetings.isArray() && !meetings.isEmpty()) {
                JsonNode firstMeetingRaces = meetings.get(0);
                if (firstMeetingRaces != null && !firstMeetingRaces.path("time").isMissingNode()) {
                    String firstTimeStr = firstMeetingRaces.path("time").asText();
                    this.firstRaceTime = LocalTime.parse(firstTimeStr, TIME_FORMATTER);
                }

                JsonNode lastMeetingRaces = meetings.get(meetings.size() - 1);
                if (lastMeetingRaces != null && !lastMeetingRaces.path("time").isMissingNode()) {
                    String lastTimeStr = lastMeetingRaces.path("time").asText();
                    this.lastRaceTime = LocalTime.parse(lastTimeStr, TIME_FORMATTER);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse first/last race times. Check JSON structure or TIME_FORMATTER.", e);
        }
    }

    public JsonNode getRootNode() {
        return this.rootNode;
    }

    public LocalTime getFirstRaceTime() {
        return this.firstRaceTime;
    }

    public LocalTime getLastRaceTime() {
        return this.lastRaceTime;
    }

    public LocalTime getNextRaceTime() {
        return this.nextRaceTime;
    }
}
