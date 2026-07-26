package uk.co.pluckier.discordbot.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.gargoylesoftware.htmlunit.*;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;

public class Util {

    public static void storeSingleResult(RaceResult result) {
        // 1. Sanitize the string to make sure it stays exactly on one line
        String sanitizedRaw = result.rawText().replace("\n", " ").replace("\r", " ");
        String newLine = result.time() + "|" + result.place() + "|" + sanitizedRaw;
        
        // 2. Append the new data to your storage file
        try (java.io.FileWriter writer = new java.io.FileWriter(ConfigLoader.getStorageFile(), true)) {
            writer.write(newLine + "\n");
        } catch (IOException e) {
            System.err.println("Local file persistence failed for " + result.time() + ": " + e.getMessage());
        }
    }

    public static void sendSingleResultToDiscord(WebClient webClient, RaceResult result) {
        String cleanRawText = result.rawText();
        
        if (cleanRawText.length() > 1500) {
            cleanRawText = cleanRawText.substring(0, 1400) + "... [Content Truncated]";
        }

        cleanRawText = cleanRawText
            .replace("\\", "\\\\") 
            .replace("\r", " ")    
            .replace("\n", " ")    
            .replace("\t", " ")    
            .replace("\"", "\\\""); 

        String contentString = "## 🏇 **New Result**\\n" +
                               "⏱️ **" + result.time() + "** 📍 **" + result.place() + "**\\n" +
                               "📋 *" + cleanRawText + "*";

        String jsonPayload = "{\"content\": \"" + contentString + "\"}";
        
        try {
            WebRequest webRequest = new WebRequest(new java.net.URL(ConfigLoader.getWebhookURL()), HttpMethod.POST);
            byte[] payloadBytes = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            webRequest.setRequestBody(new String(payloadBytes, java.nio.charset.StandardCharsets.ISO_8859_1));
            
            webRequest.setAdditionalHeader("Content-Type", "application/json; charset=UTF-8");
            webRequest.setAdditionalHeader("Accept", "application/json");
            
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            WebResponse response = webClient.getPage(webRequest).getWebResponse();
            
            if (response.getStatusCode() == 204 || response.getStatusCode() == 200) {
                System.out.println("Dispatched webhook for result: " + result.time() + " " + result.place());
            } else {
                System.err.println("Discord rejected payload with status " + response.getStatusCode());
            }
        } catch (IOException e) {
            System.err.println("Webhook transport failure for " + result.time() + ": " + e.getMessage());
        }
    }

    public static List<RaceResult> parseRaceResults(String html) {
        List<RaceResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        
        Elements raceContainers = doc.select("div.FastResultsList__FastResultCardContainer-sc-9e8caae5-1");
        
        for (Element container : raceContainers) {
            String rawText = container.text().trim();
            if(!rawText.contains("Full Result")){
                continue;
            }
            Elements placeAndTimes = container.select("span.FastResultCard__HeaderTimeCourseText-sc-4ab88fff-3");

            for (Element placeAndTime : placeAndTimes) {
                Element timeElement = placeAndTime.selectFirst("span.time-short");
                String time = (timeElement != null) ? timeElement.text().trim() : "";
                
                Element placeElementCopy = placeAndTime.clone();
                Element timeInCopy = placeElementCopy.selectFirst("span.time-short");
                if (timeInCopy != null) {
                    timeInCopy.remove(); 
                }
                String place = placeElementCopy.text().trim();
                
                if (!place.isEmpty() && !time.isEmpty()) {
                    results.add(new RaceResult(place, time, rawText));
                }
            }
        }
        return results;
    }
    
}