package uk.co.pluckier.discordbot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        // Loads the file from the root directory of your project
        try (InputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
        } catch (IOException ex) {
            System.err.println("CRITICAL: Could not find config.properties file!");
            ex.printStackTrace();
        }
    }

    public static String getToken() {
        return properties.getProperty("discord.token");
    }
}
