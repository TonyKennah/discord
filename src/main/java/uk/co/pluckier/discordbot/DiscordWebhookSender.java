package uk.co.pluckier.discordbot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

public class DiscordWebhookSender {

    private static final String WEBHOOK_URL = "https://discordapp.com/api/webhooks/1528892051146670160/jmNLWC4iVWXZIf5CQz5XIE7ghuppaVcS6Sag6Cp9jDuYm3TXnSvREIF8vaS6dRKIh7yH";
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    // Safety guard to remember the last race time we alerted on
    private static String lastAlertedRaceTime = "";

    public static void main(String[] args) {
        System.out.println("🏁 Automated Racing Engine Started with Dynamic Sleep Scheduling!");
        scheduleNextCheck();
    }

    public void startScheduler() {
        System.out.println("🏁 Automated Racing Engine Started with Dynamic Sleep Scheduling!");
        scheduleNextCheck();
    }

    private static void scheduleNextCheck() {
        LocalTime now = LocalTime.now(ZoneId.of("Europe/London"));
        
        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces();
        JsonNode rootNode = data.getRootNode();
        
        // FIX: Search for races starting AFTER the 4-minute alert window milestone
        // This stops it from matching a race that we are currently alert-processing!
        Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, now.plusMinutes(4));

        if (nextRace.isEmpty()) {
            System.out.println("📭 No more future races found for today. Checking again in 1 hour for any schedule updates...");
            scheduler.schedule(DiscordWebhookSender::scheduleNextCheck, 1, TimeUnit.HOURS);
            return;
        }

        JsonNode raceNode = nextRace.get();
        String raceTimeStr = raceNode.path("time").asText("Unknown Time");

        LocalTime raceTime;
        try {
            raceTime = LocalTime.parse(raceTimeStr);
        } catch (DateTimeParseException e) {
            System.err.println("Could not parse race time string: " + raceTimeStr + ". Retrying in 5 mins.");
            scheduler.schedule(DiscordWebhookSender::scheduleNextCheck, 5, TimeUnit.MINUTES);
            return;
        }

        long minutesUntilRace = now.until(raceTime, ChronoUnit.MINUTES);
        long minutesToSleep = minutesUntilRace - 4;

        if (minutesToSleep <= 0) {
            // Check if we already alerted on this specific race time
            if (raceTimeStr.equals(lastAlertedRaceTime)) {
                long minutesUntilRaceEnds = now.until(raceTime.plusMinutes(1), ChronoUnit.MINUTES);
                scheduler.schedule(DiscordWebhookSender::scheduleNextCheck, Math.max(1, minutesUntilRaceEnds), TimeUnit.MINUTES);
                return;
            }

            System.out.printf("⚠️ Late-added race at %s detected! Firing alert now.\n", raceTimeStr);
            lastAlertedRaceTime = raceTimeStr; // Mark as sent
            sendRaceAlertPayload(raceNode, raceTimeStr, now);
            
            long minutesUntilRaceEnds = now.until(raceTime.plusMinutes(1), ChronoUnit.MINUTES);
            scheduler.schedule(DiscordWebhookSender::scheduleNextCheck, Math.max(1, minutesUntilRaceEnds), TimeUnit.MINUTES);
        } else {
            System.out.printf("💤 Next race at %s (%d mins away). Sleeping for %d minutes...\n", 
                    raceTimeStr, minutesUntilRace, minutesToSleep);
            
            scheduler.schedule(() -> {
                executeAlertSequence(raceTimeStr);
            }, minutesToSleep, TimeUnit.MINUTES);
        }
    }

    private static void executeAlertSequence(String targetRaceTimeStr) {
        LocalTime now = LocalTime.now(ZoneId.of("Europe/London"));
        
        // Safety Double Check: Ensure we haven't already sent this alert
        if (targetRaceTimeStr.equals(lastAlertedRaceTime)) {
            System.out.println("🛡️ Guarded against duplicate fire for race time: " + targetRaceTimeStr);
            scheduleNextCheck();
            return;
        }

        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces();
        
        Optional<JsonNode> currentRace = RaceFilter.findNextRace(data.getRootNode(), now);
        
        if (currentRace.isPresent()) {
            JsonNode raceNode = currentRace.get();
            lastAlertedRaceTime = targetRaceTimeStr; // Mark as sent
            sendRaceAlertPayload(raceNode, targetRaceTimeStr, now);
        }

        // Chain cleanly into calculating the sleep cycle for the subsequent race items
        scheduleNextCheck();
    }

    private static void sendRaceAlertPayload(JsonNode raceNode, String raceTimeStr, LocalTime now) {
        String raceName = raceNode.path("place").asText("Unknown Location");
        String going = raceNode.path("going").asText("Not Specified");
        String detail = raceNode.path("detail").asText("Not Specified").replace("\"", "");
        String runners = raceNode.path("runners").asText("Not Specified");

        StringBuilder fieldsJsonBuilder = new StringBuilder();
        JsonNode runnersNode = raceNode.path("horses");

        if (runnersNode.isArray() && !runnersNode.isEmpty()) {
            for (int i = 0; i < runnersNode.size(); i++) {
                JsonNode runner = runnersNode.get(i);
                String horseNumber = runner.path("number").asText("?");
                String horseName = runner.path("name").asText("Unknown Horse");
                String trainer = runner.path("trainer").asText("Unknown");
                String jockey = runner.path("jockey").asText("Unknown");
                String odds = HorseAnalyzer.getCurrentOdds(HorseAnalyzer.extractOddsList(runner.path("odds")));
                
                String fieldSnippet = """
                    {
                    "name": "%s. %s (%s)",
                    "value": "👟 *J/T:* %s / %s",
                    "inline": true
                    }
                    """.formatted(horseNumber, horseName, odds, jockey, trainer);

                fieldsJsonBuilder.append(fieldSnippet);
                if (i < runnersNode.size() - 1) {
                    fieldsJsonBuilder.append(",\n");
                }
            }
        }

        String timeNowStr = now.toString().substring(0, 5);

        String jsonPayload = """
            {
            "content": "🚨 **Upcoming Race Alert! Starts in 4 minutes!**",
            "embeds": [
                {
                "title": "🏁 RACECARD: %s %s %s",
                "description": "💰 ** %s ** \\nTotal Runners: %s",
                "color": 2424619,
                "fields": [
                    %s
                ],
                "footer": { 
                    "text": "Data refreshed: %s | Automated Racing Engine" 
                }
                }
            ]
            }
            """.formatted(raceTimeStr, raceName, going, detail, runners, fieldsJsonBuilder.toString(), timeNowStr);

        sendRaceTip(jsonPayload);
    }

    public static void sendRaceTip(String jsonPayload) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204 && response.statusCode() != 200) {
                System.err.println("Discord Error Code: " + response.statusCode());
            } else {
                System.out.println("Success! Message sent to Discord.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
