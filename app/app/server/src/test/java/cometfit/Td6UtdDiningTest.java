package cometfit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * TD-6 "Real UTD Dining locations and menu items" (supersedes A-16), through the REST API (FR-8, UC-1 "Browse Campus
 * Dining Menu", SD-1 alt [Dining menu item]): all 7 locations and 54 items of spec/06-utd-dining load with the
 * numbers exactly as given, unpublished macros stay null, a calories-only item can be logged and daily totals still
 * work (null counted as 0 g, A-55), and the A-16 placeholders are gone.
 */
class Td6UtdDiningTest extends ApiTestSupport {

    static final List<String> LOCATIONS = List.of("Dining Hall West", "Chick-fil-A", "Panda Express",
            "The Halal Shack", "Firehouse Subs", "Taco Bell Cantina", "Starbucks");

    /** MockMvc encodes the URL template itself, so the location name is passed as is (none contains '&' or '#'). */
    private JsonNode menu(Account a, String location) throws Exception {
        return GET("/api/nutrition/menu?location=" + location, a, 200);
    }

    private JsonNode item(Account a, String location, String name) throws Exception {
        for (JsonNode it : menu(a, location)) if (it.get("name").asText().equals(name)) return it;
        throw new AssertionError(name + " not on the " + location + " menu");
    }

    @Test
    void allSevenLocationsAnd54ItemsLoad() throws Exception {
        Account a = newStudent("td6all");
        JsonNode names = GET("/api/nutrition/menu/locations", a, 200);
        List<String> got = new ArrayList<>();
        names.forEach(n -> got.add(n.asText()));
        assertThat(got).containsExactlyElementsOf(LOCATIONS);

        // TD-6 §2: building next to each location name, plus menu label and source URL
        JsonNode info = GET("/api/nutrition/menu/location-info", a, 200);
        assertThat(info).hasSize(7);
        assertThat(info.get(0).get("name").asText()).isEqualTo("Dining Hall West");
        assertThat(info.get(0).get("building").asText()).isEqualTo("Andromeda Hall (RHA)");
        assertThat(info.get(0).get("menu").asText()).isEqualTo("Lunch, Monday 2026-11-16 (rotates daily)");
        assertThat(info.get(5).get("building").asText()).isEqualTo("Parking Structure 3 (PS3)");
        for (JsonNode l : info) assertThat(l.get("source").asText()).startsWith("https://dineoncampus.com/utdallasdining/");

        int[] expected = {11, 8, 8, 7, 6, 7, 7};
        int total = 0;
        for (int i = 0; i < LOCATIONS.size(); i++) {
            JsonNode items = menu(a, LOCATIONS.get(i));
            assertThat(items).as(LOCATIONS.get(i)).hasSize(expected[i]);
            for (JsonNode it : items) {
                assertThat(it.get("location").asText()).isEqualTo(LOCATIONS.get(i));
                assertThat(it.get("building").asText()).isEqualTo(info.get(i).get("building").asText());
            }
            total += items.size();
        }
        assertThat(total).isEqualTo(54);

        // names, serving sizes and numbers exactly as given (TD-6 §1)
        JsonNode sandwich = item(a, "Chick-fil-A", "Chicken Sandwich");
        assertThat(sandwich.get("serving").asText()).isEqualTo("1 each");
        assertThat(sandwich.get("calories").asInt()).isEqualTo(420);
        assertThat(sandwich.get("proteinG").asDouble()).isEqualTo(29.0);
        assertThat(sandwich.get("carbsG").asDouble()).isEqualTo(41.0);
        assertThat(sandwich.get("fatG").asDouble()).isEqualTo(18.0);
        assertThat(item(a, "The Halal Shack", "Halal Chicken Naan Roll").get("calories").asInt()).isEqualTo(667);
        JsonNode americano = item(a, "Starbucks", "Caffe Americano (Grande)");
        assertThat(americano.get("fatG").isNull()).isFalse();            // a published 0 is 0, not "not published"
        assertThat(americano.get("fatG").asDouble()).isZero();

        // refetching does not duplicate rows (upsert by feed id)
        assertThat(menu(a, "Starbucks")).hasSize(7);
    }

    @Test
    void nullMacrosStayNull() throws Exception {
        Account a = newStudent("td6null");
        for (JsonNode it : menu(a, "Dining Hall West")) {          // macros_published: false
            assertThat(it.get("calories").asInt()).isPositive();
            assertThat(it.get("proteinG").isNull() && it.get("carbsG").isNull() && it.get("fatG").isNull())
                    .as(it.get("name").asText()).isTrue();
        }
        for (JsonNode it : menu(a, "The Halal Shack")) assertThat(it.get("proteinG").isNull()).isTrue();
        JsonNode fruit = item(a, "Chick-fil-A", "Fruit Cup");         // only fat missing
        assertThat(fruit.get("fatG").isNull()).isTrue();
        assertThat(fruit.get("carbsG").asDouble()).isEqualTo(14.0);
        assertThat(fruit.get("proteinG").asDouble()).isEqualTo(1.0);
        JsonNode beefy = item(a, "Taco Bell Cantina", "Beefy 5-Layer Burrito");
        assertThat(beefy.get("calories").asInt()).isEqualTo(500);
        assertThat(beefy.get("proteinG").isNull()).isTrue();
    }

    @Test
    void loggingACaloriesOnlyItemWorks() throws Exception {
        Account a = newStudent("td6log");
        JsonNode nachos = item(a, "Dining Hall West", "Italian Nachos");
        JsonNode r = POST("/api/nutrition/meals", a, Map.of("source", "DINING_MENU",
                "diningItemId", nachos.get("itemId").asLong()), 201);
        JsonNode saved = r.get("saved");
        assertThat(saved.get("foodName").asText()).isEqualTo("Italian Nachos");
        assertThat(saved.get("calories").asInt()).isEqualTo(220);
        assertThat(saved.get("proteinG").isNull() && saved.get("carbsG").isNull() && saved.get("fatG").isNull()).isTrue();
        assertThat(r.get("summary").get("calories").asInt()).isEqualTo(220);
        assertThat(r.get("summary").get("proteinG").asDouble()).isZero();
        assertThat(r.get("summary").get("mealsWithUnpublishedMacros").asInt()).isEqualTo(1);

        // still null when the log is reopened
        JsonNode meals = GET("/api/nutrition/meals", a, 200);
        assertThat(meals.get(0).get("proteinG").isNull()).isTrue();

        // daily totals still work next to an item with published macros (null counted as 0 g)
        JsonNode cfa = item(a, "Chick-fil-A", "Chicken Sandwich");
        POST("/api/nutrition/meals", a, Map.of("source", "DINING_MENU", "diningItemId", cfa.get("itemId").asLong()), 201);
        JsonNode s = GET("/api/nutrition/summary", a, 200);
        assertThat(s.get("calories").asInt()).isEqualTo(640);
        assertThat(s.get("proteinG").asDouble()).isEqualTo(29.0);
        assertThat(s.get("carbsG").asDouble()).isEqualTo(41.0);
        assertThat(s.get("fatG").asDouble()).isEqualTo(18.0);
        assertThat(s.get("mealCount").asInt()).isEqualTo(2);
        assertThat(s.get("mealsWithUnpublishedMacros").asInt()).isEqualTo(1);
    }

    @Test
    void placeholderLocationsAreGone() throws Exception {
        Account a = newStudent("td6old");
        GET("/api/nutrition/menu?location=Dining Hall", a, 404);
        GET("/api/nutrition/menu?location=Campus Store", a, 404);
    }
}
