package uk.co.pluckier.discordbot;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Main Discord message listener. Handles command routing and message processing.
 * Delegates heavy lifting to utility classes for better separation of concerns.
 */
public class MessageListener extends ListenerAdapter {

    private static final String TARGET_CHANNEL_ID = "1527431734889807952";

    private final RaceDataManager data;
    private LocalDate lastFetchedDate;

    public MessageListener(RaceDataManager data) {
        this.data = data;
        this.lastFetchedDate = LocalDate.now();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages sent by bots to avoid infinite loops
        if (event.getAuthor().isBot()) return;

        // Only respond in the target channel
        if (!event.getChannel().getId().equals(TARGET_CHANNEL_ID)) {
            return;
        }

        String message = event.getMessage().getContentRaw();

        // Reload data if it's a new day
        reloadRaceDataIfNewDay();

        // Route command
        routeCommand(message, event);
    }

    /**
     * Reload race data if it's a new day
     */
    private void reloadRaceDataIfNewDay() {
        if (!LocalDate.now().equals(lastFetchedDate)) {
            System.out.println("New day detected! Reloading race data...");
            data.fetchTodaysRaces();
            this.lastFetchedDate = LocalDate.now();
        }
    }

    /**
     * Route message to appropriate command handler
     */
    private void routeCommand(String message, MessageReceivedEvent event) {
        if (message.equalsIgnoreCase("!winner")) {
            event.getChannel().sendMessageEmbeds(getNextRaceWinnerEmbed()).queue();
        } else if (message.equalsIgnoreCase("!site")) {
            event.getChannel().sendMessage("https://pluckier.github.io/racing 🏇").queue();
        } else if (message.equalsIgnoreCase("!tips")) {
            event.getChannel().sendMessage("https://pluckier.github.io/tips 📝").queue();
        } else if (message.equalsIgnoreCase("!races")) {
            event.getChannel().sendMessageEmbeds(getTodaysRacesEmbed()).queue();
        } else if (message.matches("^!w\\d+$")) {
            int numRaces = Integer.parseInt(message.substring(2));
            event.getChannel().sendMessageEmbeds(getNextRacesWinnerEmbed(numRaces)).queue();
        } else if (message.equalsIgnoreCase("!next")) {
            event.getChannel().sendMessage(getNextRaceInfo()).queue();
        } else if (message.equalsIgnoreCase("!help")) {
            event.getChannel().sendMessageEmbeds(RaceEmbedBuilder.buildHelpEmbed()).queue();
        }
    }

    /**
     * Get the next race information as a simple string
     */
    private String getNextRaceInfo() {
        JsonNode rootNode = data.getRootNode();
        Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, LocalTime.now());

        if (nextRace.isPresent()) {
            JsonNode raceNode = nextRace.get();
            String raceTime = raceNode.get("time").asText();
            String raceName = raceNode.get("place").asText();
            return "Next Race: " + raceTime + " " + raceName;
        }

        return "No upcoming races found.";
    }

    /**
     * Get today's races as an embed
     */
    private MessageEmbed getTodaysRacesEmbed() {
        JsonNode rootNode = data.getRootNode();

        if (rootNode == null || !rootNode.isArray() || rootNode.size() == 0) {
            return RaceEmbedBuilder.buildNoDataEmbed();
        }

        var embed = RaceEmbedBuilder.buildTodaysRacesEmbed();

        StringBuilder listBuilder = new StringBuilder();
        listBuilder.append("Here is the complete schedule of races available today:\n\n");

        for (JsonNode raceNode : rootNode) {
            String raceName = raceNode.path("place").asText("Unknown Location");
            String raceTime = raceNode.path("time").asText("--:--");
            String raceRunners = raceNode.path("runners").asText("--");
            String raceDetails = raceNode.path("detail").asText("-");

            listBuilder.append("⏰ `")
                    .append(raceTime)
                    .append("` 📍 **")
                    .append(raceName)
                    .append(" - ")
                    .append(raceDetails.contains("Handicap") ? "Handicap" : "")
                    .append(raceDetails.contains("Class 1") ? " ⭐ Class 1" : "")
                    .append("** (")
                    .append(raceRunners)
                    .append(" runners)\n");
        }

        embed.setDescription(listBuilder.toString());
        embed.setFooter("Use !winner to get the top pick for the upcoming post-time");
        embed.setTimestamp(java.time.Instant.now());

        return embed.build();
    }

    /**
     * Get the next race winner prediction embed
     */
    private MessageEmbed getNextRaceWinnerEmbed() {
        JsonNode rootNode = data.getRootNode();
        Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, LocalTime.now());

        if (!nextRace.isPresent()) {
            return RaceEmbedBuilder.buildNoRacesEmbed();
        }

        JsonNode raceNode = nextRace.get();
        String raceTimeStr = raceNode.get("time").asText();
        String racePlaceStr = raceNode.get("place").asText();

        JsonNode horsesNode = raceNode.get("horses");
        if (horsesNode == null || !horsesNode.isArray()) {
            return RaceEmbedBuilder.buildNoRacesEmbed();
        }

        HorseAnalyzer.HorsePrediction bestHistorical = null;
        HorseAnalyzer.HorsePrediction bestFirst3 = null;

        for (JsonNode horse : horsesNode) {
            HorseAnalyzer.HorsePrediction prediction = HorseAnalyzer.analyzeHorse(horse);
            if (prediction == null) continue;

            // Track best historical rating
            if (bestHistorical == null || prediction.highestRating > bestHistorical.highestRating) {
                bestHistorical = prediction;
            }

            // Track best first 3 races average
            if (prediction.avgRatingFirst3 > 0) {
                if (bestFirst3 == null || prediction.avgRatingFirst3 > bestFirst3.avgRatingFirst3) {
                    bestFirst3 = prediction;
                }
            }
        }

        return RaceEmbedBuilder.buildNextRaceWinnerEmbed(raceTimeStr, racePlaceStr, bestHistorical, bestFirst3);
    }

    /**
     * Get the next N races winner predictions embed
     */
    private MessageEmbed getNextRacesWinnerEmbed(int maxRaces) {
        JsonNode rootNode = data.getRootNode();
        List<JsonNode> races = RaceFilter.getNextNRaces(rootNode, LocalTime.now(), maxRaces);

        if (races.isEmpty()) {
            return RaceEmbedBuilder.buildNoRacesEmbed();
        }

        var embed = RaceEmbedBuilder.buildNextNRacesEmbed(maxRaces);

        for (JsonNode raceNode : races) {
            String raceTimeStr = raceNode.get("time").asText();
            String racePlace = raceNode.has("place") ? raceNode.get("place").asText() : "Unknown Track";

            JsonNode horsesNode = raceNode.get("horses");
            if (horsesNode == null || !horsesNode.isArray()) {
                continue;
            }

            HorseAnalyzer.HorsePrediction bestHorse = null;

            for (JsonNode horse : horsesNode) {
                HorseAnalyzer.HorsePrediction prediction = HorseAnalyzer.analyzeHorse(horse);
                if (prediction == null) continue;

                if (bestHorse == null || prediction.highestRating > bestHorse.highestRating) {
                    bestHorse = prediction;
                }
            }

            if (bestHorse != null) {
                embed.addField(
                    "⏰ " + raceTimeStr + " - " + racePlace,
                    "🐎 **" + bestHorse.name + "** — Current Odds: `" + bestHorse.currentOdds + "`",
                    false
                );
            }
        }

        return embed.build();
    }
}
