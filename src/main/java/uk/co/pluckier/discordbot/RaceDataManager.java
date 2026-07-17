package uk.co.pluckier.discordbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RaceDataManager {
    private JsonNode rootNode; // Stores the raw JSON content in memory

    /**
     * Dynamically generates the URL using today's date, 
     * fetches the JSON from the web, and loads it into memory.
     */
    public void fetchTodaysRaces() {
        try {
            // 1. Get today's system date and format it to "dd-MM-yyyy"
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            String formattedDate = today.format(formatter);

            // 2. Build the dynamic URL string
            String urlString = "https://www.pluckier.co.uk/" + formattedDate + "-races.json";
            System.out.println("Fetching race data from: " + urlString);

            // 3. Create the HTTP client and request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Accept", "application/json") // Good practice for web requests
                    .GET()
                    .build();

            // 4. Send the request and store the body response as a raw String
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 5. Handle the HTTP response status code
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                this.rootNode = mapper.readTree(response.body());
                System.out.println("Successfully loaded data for " + formattedDate + "!");
            } else {
                System.err.println("Failed to fetch data! Server responded with status code: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("An error occurred while fetching or parsing the race data.");
            e.printStackTrace();
        }
    }

    /**
     * Helper getter to access the root node from other classes (like your Discord listener)
     */
    public JsonNode getRootNode() {
        return this.rootNode;
    }
}
