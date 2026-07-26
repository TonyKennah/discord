package uk.co.pluckier.discordbot.webhooks;

import java.io.IOException;
import com.gargoylesoftware.htmlunit.*;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;

public class DiscordWebhookClient {

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
}
