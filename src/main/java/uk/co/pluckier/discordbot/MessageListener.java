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

    public MessageEmbed getNextRaceWinnerEmbed() {
        LocalTime now = LocalTime.now();

        JsonNode rootNode = data.getRootNode();
        for (JsonNode raceNode : rootNode) {
            String raceTimeStr = raceNode.get("time").asText();
            String racePlaceStr = raceNode.get("place").asText();
            String currentOdds = "";
            LocalTime raceTime = LocalTime.parse(raceTimeStr);

            // 1. Find the next race scheduled after current time
            if (raceTime.isAfter(now)) {
                JsonNode horsesNode = raceNode.get("horses");
                if (horsesNode != null && horsesNode.isArray()) {
                    
                    String topHorseName = "Unknown";
                    int highestRating = -1;

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

                        // 1. Get the horse name

                        // 2. Get the odds as a list of strings
                        List<String> oddsList = new ArrayList<>();

                        if (oddsNode != null && oddsNode.isArray()) {
                            for (JsonNode odd : oddsNode) {
                                oddsList.add(odd.asText());
                            }
                        }




                        JsonNode pastNode = horse.get("past");

                        if (pastNode != null && pastNode.isArray()) {
                            // 3. Loop through past performances
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

                    // 4. Build and return the successful prediction Embed card [1]
                    if (highestRating != -1) {
                        return new EmbedBuilder()
                                .setColor(new Color(30, 130, 76)) // Racing Green [1]
                                .setTitle("🏁 Next Race Winner Prediction") // [1]
                                .setDescription("Analyzing the next card based on historical performance values.") // [1]
                                .addField("⏰ ", "`" + raceTimeStr + "` "+racePlaceStr, true) // [1]
                                .addField("🐎 Top Runner", "**" + topHorseName + "**", true) // [1]
                                .addField("⭐ Odds", "`" + currentOdds + "`", true) // [1]
                                .setFooter("Data sourced dynamically from Pluckier Racing") // [1]
                                .setTimestamp(java.time.Instant.now()) // [1]
                                .build();
                    }
                }
            }
        }

        // 5. Fallback Embed card if no upcoming races are scheduled [1]
        return new EmbedBuilder()
                .setColor(new Color(217, 30, 30)) // Warning Red [1]
                .setTitle("🚫 No Races Remaining") // [1]
                .setDescription("There are no further races scheduled after your local system time for today.") // [1]
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
