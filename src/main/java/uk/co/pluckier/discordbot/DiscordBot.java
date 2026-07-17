package uk.co.pluckier.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import uk.co.pluckier.discordbot.RaceDataManager;

public class DiscordBot {
    public static void main(String[] args) {
        // Initialize the bot here
        System.out.println("Discord Bot is starting...");
    
        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces(); // Fetch and load today's races

        try {
            JDA jda = JDABuilder.createDefault(ConfigLoader.getToken()) // Use the token from config.properties
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT) 
                    .addEventListeners(new MessageListener(data))    
                    .build();

            // CRUCIAL: This blocks the main thread so the program doesn't exit
            jda.awaitReady(); 
            System.out.println("Bot is successfully connected and online!");

        } catch (InterruptedException e) {
            System.err.println("Bot startup was interrupted.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Failed to start the bot. Check your token!");
            e.printStackTrace();
        }
    }

}