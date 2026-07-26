package uk.co.pluckier.discordbot.webparser;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import uk.co.pluckier.discordbot.model.RaceResult;

public class SportingLifeParser {

    public static List<RaceResult> parseRaceResults(String html) {
        List<RaceResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        
        Elements raceContainers = doc.select("div.FastResultsList__FastResultCardContainer-sc-9e8caae5-1");
        
        for (Element container : raceContainers) {
            String rawText = container.text().trim();
            if (!rawText.contains("Full Result")) {
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
