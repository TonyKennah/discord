package uk.co.pluckier.discordbot.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;

public class RaceResultPersistence {

    public static void storeSingleResult(RaceResult result) {
        String sanitizedRaw = result.rawText().replace("\n", " ").replace("\r", " ");
        String newLine = result.time() + "|" + result.place() + "|" + sanitizedRaw;
        
        try (java.io.FileWriter writer = new java.io.FileWriter(ConfigLoader.getStorageFile(), true)) {
            writer.write(newLine + "\n");
        } catch (IOException e) {
            System.err.println("Local file persistence failed for " + result.time() + ": " + e.getMessage());
        }
    }

    public static Set<String> pruneStorageFile() {
        try {
            File file = new File(ConfigLoader.getStorageFile());
            if (!file.exists()) return null;

            List<String> allLines = Files.readAllLines(file.toPath());
            
            if (allLines.size() > 150) {
                List<String> trimmedLines = allLines.subList(allLines.size() - 100, allLines.size());
                Files.write(file.toPath(), trimmedLines);
                
                Set<String> updatedCacheKeys = new HashSet<>();
                for (String line : trimmedLines) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 2) {
                        updatedCacheKeys.add(parts[0].trim() + "|" + parts[1].trim());
                    }
                }
                System.out.println("Housekeeping complete: Cleaned file and RAM cache bounds safely.");
                return updatedCacheKeys;
            }
        } catch (IOException e) {
            System.err.println("Failed to prune historical storage file: " + e.getMessage());
        }
        return null;
    }
}
