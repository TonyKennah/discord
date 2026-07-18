package uk.co.pluckier.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import java.awt.Color;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.fasterxml.jackson.databind.JsonNode;

public class MessageListener extends ListenerAdapter {

    private final RaceDataManager data;
    private LocalDate lastFetchedDate;

    public MessageListener(RaceDataManager data ) {
        this.data = data;
        this.lastFetchedDate = LocalDate.now();
    }


    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages sent by bots to avoid infinite loops
        if (event.getAuthor().isBot()) return;

        
        String targetChannelId = "1527431734889807952"; 
        if (!event.getChannel().getId().equals(targetChannelId)) {
            return; // Ignore the message completely if it's from another channel
        }

        String message = event.getMessage().getContentRaw();

        if (!LocalDate.now().equals(lastFetchedDate)) {
            System.out.println("New day detected! Reloading race data...");
            data.fetchTodaysRaces();             // Pull fresh data from the server
            this.lastFetchedDate = LocalDate.now(); // Update tracking flag
        }

        // Respond to a !ping command
         if (message.equalsIgnoreCase("!winner")) {
            MessageEmbed raceEmbed = getNextRaceWinnerEmbed();
            event.getChannel().sendMessageEmbeds(raceEmbed).queue();
        } else if (message.equalsIgnoreCase("!site")) {
            event.getChannel().sendMessage("https://pluckier.github.io/racing 🏇").queue();
        } else if (message.equalsIgnoreCase("!tips")) {
            event.getChannel().sendMessage("https://pluckier.github.io/tips 📝").queue();
        } else if (message.equalsIgnoreCase("!races")) {
            event.getChannel().sendMessageEmbeds(getTodaysRacesEmbed()).queue();
        } else if (message.matches("^!w\\d+$")) {
            // Extract the number from the command (e.g., "!w10" -> 10)
            int numRaces = Integer.parseInt(message.substring(2));
            event.getChannel().sendMessageEmbeds(getNextRacesWinnerEmbed(numRaces)).queue();
        } else if (message.equalsIgnoreCase("!next")) {
            event.getChannel().sendMessage(getNextRace()).queue();
        } else if (message.equalsIgnoreCase("!help")) {
            event.getChannel().sendMessageEmbeds(getHelp()).queue();
        } 
    }


    private MessageEmbed getHelp() {
        EmbedBuilder helpEmbed = new EmbedBuilder();
    
        helpEmbed.setColor(new java.awt.Color(52, 152, 219)); // Clean Blue Color
        helpEmbed.setTitle("📚 Pluckier Racing Bot - Help Menu");
        helpEmbed.setDescription("Here is a list of all available commands you can use with this bot:");

        // General Utility Commands
        helpEmbed.addField("🔧 Utility Commands", 
            "`!help` - Display this help menu.\n" +
            "`!site` - Get the link to the racing site.\n" +
            "`!tips` - Get the link to the tips site.", 
            false);

        // Racing Core Commands
        helpEmbed.addField("🏇 Racing Commands", 
            "`!winner` - Get the predicted winner for the next race.\n" +
            "`!wX` - Get the predicted winners for the next X races.\n" +
            "`!next` - Get info on the next upcoming race.\n" +
            "`!races` - Get the complete list of today's races.", 
            false);

        helpEmbed.setFooter("Tip: Type commands exactly as shown above.");
        helpEmbed.setTimestamp(java.time.Instant.now());

        // Send the finished card
        return helpEmbed.build();
    }
    
    public MessageEmbed getTodaysRacesEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        
        embed.setColor(new Color(41, 128, 185)); // Classic Blue accent
        embed.setTitle("📅 Today's Racing Card");
        
        // Safeguard check in case the JSON failed to load earlier
        JsonNode rootNode = data.getRootNode();
        if (rootNode == null || !rootNode.isArray() || rootNode.size() == 0) {
            embed.setDescription("❌ No racing data available for today.");
            return embed.build();
        }

        StringBuilder listBuilder = new StringBuilder();
        listBuilder.append("Here is the complete schedule of races available today:\n\n");

        // Loop through the root array directly 
        for (JsonNode raceNode : rootNode) {
            String raceName = raceNode.path("place").asText("Unknown Location");
            String raceTime = raceNode.path("time").asText("--:--");
            String raceRunners = raceNode.path("runners").asText("--");
            String raceDetails = raceNode.path("detail").asText("-");
            String raceDistance = raceNode.path("distance").asText("-");
            
            // Formats each row cleanly with emojis and monospace code text font
            listBuilder.append("⏰ `")
                    .append(raceTime)
                    .append("` 📍 **")
                    .append(raceName)
                    .append(" - ")
                    .append(raceDetails.contains("Handicap")? "Handicap" : "")
                    .append(raceDetails.contains("Class 1")? " ⭐ Class 1" : "")
                    .append("** (")
                    .append(raceRunners)
                    .append(" runners)\n");
        }

        embed.setDescription(listBuilder.toString());
        embed.setFooter("Use !winner to get the top pick for the upcoming post-time");
        embed.setTimestamp(Instant.now());

        return embed.build();
    }

    private String getNextRace() {
        //determined by the race time property and the time now.

        JsonNode rootNode = data.getRootNode();
        for (JsonNode raceNode : rootNode) {
            LocalTime now = LocalTime.now();
            String raceTimeStr = raceNode.get("time").asText();
            LocalTime raceTime = LocalTime.parse(raceTimeStr);
            if (raceTime.isAfter(now)) {
                String raceName = raceNode.get("place").asText();
                return "Next Race: " + raceTimeStr + " " + raceName;
            }
        }
        return "No upcoming races found.";
    }

    /**
     * Helper class to store horse prediction data
     */
    private static class HorsePrediction {
        String name;
        int highestRating;
        double avgRatingLast3;
        String currentOdds;

        HorsePrediction(String name, int highestRating, double avgRatingLast3, String currentOdds) {
            this.name = name;
            this.highestRating = highestRating;
            this.avgRatingLast3 = avgRatingLast3;
            this.currentOdds = currentOdds;
        }
    }

    /**
     * Calculate the average rating from the first 3 races in the past history
     */
    private double calculateFirst3RacesAverage(JsonNode pastNode) {
        if (pastNode == null || !pastNode.isArray() || pastNode.size() == 0) {
            return 0.0;
        }

        // Get the first 3 races (or fewer if less than 3 exist)
        int endIndex = Math.min(3, pastNode.size());
        List<Integer> firstThreeRatings = new ArrayList<>();

        for (int i = 0; i < endIndex; i++) {
            JsonNode pastRace = pastNode.get(i);
            String ratingStr = pastRace.get("name").asText();
            try {
                int rating = Integer.parseInt(ratingStr);
                firstThreeRatings.add(rating);
            } catch (NumberFormatException ignored) {}
        }

        if (firstThreeRatings.isEmpty()) {
            return 0.0;
        }

        return firstThreeRatings.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    public MessageEmbed getNextRaceWinnerEmbed() {
        LocalTime now = LocalTime.now();

        JsonNode rootNode = data.getRootNode();
        for (JsonNode raceNode : rootNode) {
            String raceTimeStr = raceNode.get("time").asText();
            String racePlaceStr = raceNode.get("place").asText();
            LocalTime raceTime = LocalTime.parse(raceTimeStr);

            // 1. Find the next race scheduled after current time
            if (raceTime.isAfter(now)) {
                JsonNode horsesNode = raceNode.get("horses");
                if (horsesNode != null && horsesNode.isArray()) {
                    
                    HorsePrediction bestHistorical = null;
                    HorsePrediction bestFirst3Races = null;

                    // 2. Loop through horses in this race
                    for (JsonNode horse : horsesNode) {
                        // Non-runner safeguard check
                        JsonNode oddsNode = horse.get("odds");
                        if (oddsNode == null || oddsNode.isNull() || "null".equalsIgnoreCase(oddsNode.asText())) {
                            continue; 
                        }

                        if (oddsNode.isArray() && oddsNode.size() > 0) {
                            JsonNode lastOdds = oddsNode.get(oddsNode.size() - 1);
                            if (lastOdds == null || lastOdds.isNull() || "null".equalsIgnoreCase(lastOdds.asText())) {
                                continue;
                            }
                        }

                        String horseName = horse.get("name").asText();

                        // Get the odds as a list of strings
                        List<String> oddsList = new ArrayList<>();

                        if (oddsNode != null && oddsNode.isArray()) {
                            for (JsonNode odd : oddsNode) {
                                oddsList.add(odd.asText());
                            }
                        }

                        String currentOdds = oddsList.isEmpty() ? "N/A" : oddsList.get(oddsList.size() - 1);

                        JsonNode pastNode = horse.get("past");

                        if (pastNode != null && pastNode.isArray()) {
                            // Find highest historical rating
                            int highestRating = -1;
                            for (JsonNode pastRace : pastNode) {
                                String ratingStr = pastRace.get("name").asText();
                                try {
                                    int rating = Integer.parseInt(ratingStr);
                                    if (rating > highestRating) {
                                        highestRating = rating;
                                    }
                                } catch (NumberFormatException ignored) {}
                            }

                            // Calculate average rating from first 3 races
                            double avgFirst3 = calculateFirst3RacesAverage(pastNode);

                            // Update best historical prediction (based on highest individual rating)
                            if (highestRating > -1) {
                                if (bestHistorical == null || highestRating > bestHistorical.highestRating) {
                                    bestHistorical = new HorsePrediction(horseName, highestRating, avgFirst3, currentOdds);
                                }
                            }

                            // Update best first 3 races prediction (based on average of first 3 races)
                            if (avgFirst3 > 0) {
                                if (bestFirst3Races == null || avgFirst3 > bestFirst3Races.avgRatingLast3) {
                                    bestFirst3Races = new HorsePrediction(horseName, highestRating, avgFirst3, currentOdds);
                                }
                            }
                        }
                    }

                    // 4. Build and return the successful prediction Embed card
                    if (bestHistorical != null || bestFirst3Races != null) {
                        EmbedBuilder embedBuilder = new EmbedBuilder()
                                .setColor(new Color(30, 130, 76)) // Racing Green
                                .setTitle("🏁 Next Race Winner Prediction")
                                .setDescription("Analyzing the next card based on performance metrics.")
                                .addField("⏰ ", "`" + raceTimeStr + "` " + racePlaceStr, true);

                        // Add historical best runner
                        if (bestHistorical != null) {
                            embedBuilder.addField(
                                "📊 Best Historical", 
                                "**" + bestHistorical.name + "**\nRating: `" + bestHistorical.highestRating + "` | Odds: `" + bestHistorical.currentOdds + "`",
                                true
                            );
                        }

                        // Add best first 3 races runner
                        if (bestFirst3Races != null) {
                            embedBuilder.addField(
                                "📈 Best Early Form (First 3)", 
                                "**" + bestFirst3Races.name + "**\nAvg Rating: `" + String.format("%.2f", bestFirst3Races.avgRatingLast3) + "` | Odds: `" + bestFirst3Races.currentOdds + "`",
                                true
                            );
                        }

                        embedBuilder.setFooter("Data sourced dynamically from Pluckier Racing")
                                .setTimestamp(java.time.Instant.now());

                        return embedBuilder.build();
                    }
                }
            }
        }

        // 5. Fallback Embed card if no upcoming races are scheduled
        return new EmbedBuilder()
                .setColor(new Color(217, 30, 30)) // Warning Red
                .setTitle("🚫 No Races Remaining")
                .setDescription("There are no further races scheduled after your local system time for today.")
                .build();
    }




    public MessageEmbed getNextRacesWinnerEmbed(int maxRaces) {
        LocalTime now = LocalTime.now();
        
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(30, 130, 76)) // Racing Green
                .setTitle("🏁 Upcoming Race Winner Predictions")
                .setDescription("Analyzing the next " + maxRaces + " cards based on historical performance values.")
                .setFooter("Data sourced dynamically from Pluckier Racing")
                .setTimestamp(java.time.Instant.now());

        int racesAdded = 0;

        JsonNode rootNode = data.getRootNode();
        for (JsonNode raceNode : rootNode) {
            // Stop the loop completely once we hit our limit (e.g., 5 or 6)
            if (racesAdded >= maxRaces) {
                break;
            }

            String raceTimeStr = raceNode.get("time").asText();
            LocalTime raceTime = LocalTime.parse(raceTimeStr);
            String currentOdds = "";

            if (raceTime.isAfter(now)) {
                JsonNode horsesNode = raceNode.get("horses");
                if (horsesNode != null && horsesNode.isArray()) {
                    
                    String topHorseName = "Unknown";
                    int highestRating = -1;

                    for (JsonNode horse : horsesNode) {
                        // Non-runner safeguard check
                        JsonNode oddsNode = horse.get("odds");
                        if (oddsNode == null || oddsNode.isNull() || "null".equalsIgnoreCase(oddsNode.asText())) {
                            continue; 
                        }

                        if (oddsNode.isArray() && oddsNode.size() > 0) {
                            JsonNode lastOdds = oddsNode.get(oddsNode.size() - 1);
                            if (lastOdds == null || lastOdds.isNull() || "null".equalsIgnoreCase(lastOdds.asText())) {
                                continue;
                            }
                        }

                        List<String> oddsList = new ArrayList<>();

                        if (oddsNode != null && oddsNode.isArray()) {
                            for (JsonNode odd : oddsNode) {
                                oddsList.add(odd.asText());
                            }
                        }

                        String horseName = horse.get("name").asText();
                        JsonNode pastNode = horse.get("past");

                        if (pastNode != null && pastNode.isArray()) {
                            for (JsonNode pastRace : pastNode) {
                                String ratingStr = pastRace.get("name").asText();
                                try {
                                    int rating = Integer.parseInt(ratingStr);

                                    if (rating > highestRating) {
                                        highestRating = rating;
                                        topHorseName = horseName;
                                        if (!oddsList.isEmpty()) {
                                            currentOdds = oddsList.get(oddsList.size() - 1);
                                        }
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }

                    // If we found a valid top-rated horse, add it to the embed
                    if (highestRating != -1) {
                        String racePlace = raceNode.has("place") ? raceNode.get("place").asText() : "Unknown Track";
                        
                        // False means stacked vertically (cleaner layout for lists)
                        embed.addField(
                            "⏰ " + raceTimeStr + " - " + racePlace, 
                            "🐎 **" + topHorseName + "** — Current Odds: `" + currentOdds + "`", 
                            false
                        );
                        
                        racesAdded++;
                    }
                }
            }
        }

        // Fallback if zero upcoming races matched the criteria
        if (racesAdded == 0) {
            return new EmbedBuilder()
                    .setColor(new Color(217, 30, 30)) // Warning Red
                    .setTitle("🚫 No Races Remaining")
                    .setDescription("There are no further races scheduled after your local system time for today.")
                    .build();
        }

        return embed.build();
    }


}
