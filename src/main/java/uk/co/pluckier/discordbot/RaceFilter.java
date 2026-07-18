package uk.co.pluckier.discordbot;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Utility for filtering and finding races based on time criteria.
 */
public class RaceFilter {

    /**
     * Find the next race after the current time
     */
    public static Optional<JsonNode> findNextRace(JsonNode rootNode, LocalTime now) {
        if (rootNode == null || !rootNode.isArray()) {
            return Optional.empty();
        }

        for (JsonNode raceNode : rootNode) {
            String raceTimeStr = raceNode.get("time").asText();
            try {
                LocalTime raceTime = LocalTime.parse(raceTimeStr);
                if (raceTime.isAfter(now)) {
                    return Optional.of(raceNode);
                }
            } catch (Exception ignored) {}
        }

        return Optional.empty();
    }

    /**
     * Get the next N races after the current time
     */
    public static java.util.List<JsonNode> getNextNRaces(JsonNode rootNode, LocalTime now, int count) {
        java.util.List<JsonNode> races = new java.util.ArrayList<>();

        if (rootNode == null || !rootNode.isArray()) {
            return races;
        }

        for (JsonNode raceNode : rootNode) {
            if (races.size() >= count) {
                break;
            }

            String raceTimeStr = raceNode.get("time").asText();
            try {
                LocalTime raceTime = LocalTime.parse(raceTimeStr);
                if (raceTime.isAfter(now)) {
                    races.add(raceNode);
                }
            } catch (Exception ignored) {}
        }

        return races;
    }
}
