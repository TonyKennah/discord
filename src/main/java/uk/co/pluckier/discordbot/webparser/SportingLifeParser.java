package uk.co.pluckier.discordbot.webparser;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import uk.co.pluckier.discordbot.model.Position;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.model.ResultDetails;

public class SportingLifeParser {

    public static List<RaceResult> parseRaceResults(Document doc) {
        List<RaceResult> results = new ArrayList<>();

        if (doc == null) {
            return results;
        }

        try {
            Elements raceContainers = doc.select("div.FastResultsList__FastResultCardContainer-sc-9e8caae5-1");

            for (Element container : raceContainers) {
                String checkText = container.text();
                if (!checkText.contains("Full Result")) {
                    container.remove(); // MEMORY FIX: Remove ignored cards instantly
                    continue;
                }

                // 1. Parse the Placed Horses (Positions)
                List<Position> positionsList = new ArrayList<>();
                Elements horses = container.select("div.FastResultRide__RideRow-sc-bf4bc8c6-0");

                for (Element horse : horses) {
                    Element posEl = horse.selectFirst("span.FastResultRide__RidePosition-sc-bf4bc8c6-1");
                    Element numEl = horse.selectFirst("span.FastResultRide__RideNumber-sc-bf4bc8c6-2");
                    Element nameEl = horse.selectFirst("span.FastResultRide__RideHorseName-sc-bf4bc8c6-3");
                    Element oddsEl = horse.selectFirst("span.FastResultRide__RideOdds-sc-bf4bc8c6-4");

                    String position = (posEl != null) ? posEl.text().trim() : "";
                    String number = (numEl != null) ? numEl.text().trim() : "";
                    String horseName = (nameEl != null) ? nameEl.text().trim() : "";
                    String odds = (oddsEl != null) ? oddsEl.text().trim() : "";

                    if (!horseName.isEmpty()) {
                        positionsList.add(new Position(position, number, horseName, odds));
                    }
                }

                // 2. Parse Extra Metadata
                List<String> extraDetailsList = new ArrayList<>();
                Elements detailLabels = container.select("td.FastResultDetailItem__ItemLabel-sc-55408ced-0");

                for (Element labelEl : detailLabels) {
                    String labelText = labelEl.text().trim();
                    Element parentRow = labelEl.parent();
                    String valueText = "";

                    if (parentRow != null) {
                        Element valueEl = parentRow.selectFirst("td.FastResultDetailItem__ItemValue-sc-55408ced-1");
                        if (valueEl != null) {
                            valueText = valueEl.text().trim();
                        }
                    }

                    if (!labelText.isEmpty()) {
                        if (!valueText.isEmpty()) {
                            extraDetailsList.add(labelText + " " + valueText);
                        } else {
                            extraDetailsList.add(labelText);
                        }
                    }
                }

                // Instantiate the bundled metadata block
                ResultDetails details = new ResultDetails(positionsList, extraDetailsList);

                // 3. Parse Header Time and Place info
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
                        results.add(new RaceResult(place, time, details));
                    }
                }

                // MEMORY FIX: Destroys this race container from the HTML tree right now
                // since we have fully converted it into lightweight Java model strings.
                container.remove();
            }
        } finally {
            // THE CRITICAL HEAP PURGE: Empties out remaining DOM branches completely
            doc.empty();
        }

        return results;
    }
}
