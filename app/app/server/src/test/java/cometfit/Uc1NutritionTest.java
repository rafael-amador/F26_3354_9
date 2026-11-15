package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * UC-1 Track Nutrition &amp; Hydration — follows SD-1. Covers FR-2, FR-6, FR-8 and their RTM verification methods
 * (Deliverable 1 §4.4), plus NFR Safety (unsafe-goal warnings).
 */
class Uc1NutritionTest extends ApiTestSupport {

    /** FR-2 RTM: "Open the intake category, enter in the intake data, close the intake category and reopen". */
    @Test
    void fr2_manualMealIsSavedAndStillThereWhenReopened() throws Exception {
        Account a = newStudent("meal");
        JsonNode r = POST("/api/nutrition/meals", a, Map.of("source", "MANUAL", "foodName", "Burrito",
                "calories", 650, "proteinG", 30, "carbsG", 70, "fatG", 22), 201);
        assertThat(r.get("saved").get("entryId").asLong()).isPositive();
        assertThat(r.get("summary").get("calories").asInt()).isEqualTo(650);

        JsonNode meals = GET("/api/nutrition/meals", a, 200);   // "reopen"
        assertThat(meals).hasSize(1);
        assertThat(meals.get(0).get("foodName").asText()).isEqualTo("Burrito");
        assertThat(meals.get(0).get("source").asText()).isEqualTo("MANUAL");
    }

    @Test
    void fr2_mealValidation() throws Exception {
        Account a = newStudent("val");
        POST("/api/nutrition/meals", a, Map.of("source", "MANUAL", "calories", 100), 400);              // no name
        POST("/api/nutrition/meals", a, Map.of("source", "MANUAL", "foodName", "x", "calories", -5), 400);
        POST("/api/nutrition/meals", a, Map.of("source", "DINING_MENU"), 400);                           // no item
    }

    /** SD-1 alt [Photo entry]: submitMealPhoto -> estimateMacros -> estimate for review; saved only after confirm. */
    @Test
    void photoEntryReturnsAnEstimateThenSavesAfterConfirmation() throws Exception {
        Account a = newStudent("photo");
        MockMultipartFile img = new MockMultipartFile("image", "meal.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5, 6});
        JsonNode est = call(multipart("/api/nutrition/meals/photo").file(img), a.token(), null, 200);
        assertThat(est.get("foodItems").size()).isGreaterThanOrEqualTo(1);
        JsonNode draft = est.get("draft");
        assertThat(draft.get("source").asText()).isEqualTo("PHOTO");
        assertThat(draft.get("entryId").isNull()).isTrue();
        assertThat(GET("/api/nutrition/meals", a, 200)).isEmpty();          // not stored yet

        // same photo -> same estimate (deterministic stub)
        JsonNode again = call(multipart("/api/nutrition/meals/photo").file(img), a.token(), null, 200);
        assertThat(again.get("draft").get("calories")).isEqualTo(draft.get("calories"));

        // confirm / edit estimate -> saveMealEntry
        POST("/api/nutrition/meals", a, Map.of("source", "PHOTO", "foodName", "Edited meal",
                "calories", draft.get("calories").asInt() + 50, "proteinG", 10, "carbsG", 10, "fatG", 10), 201);
        JsonNode meals = GET("/api/nutrition/meals", a, 200);
        assertThat(meals.get(0).get("source").asText()).isEqualTo("PHOTO");
        assertThat(meals.get(0).get("calories").asInt()).isEqualTo(draft.get("calories").asInt() + 50);
    }

    @Test
    void photoMustBeAnImage() throws Exception {
        Account a = newStudent("notimg");
        MockMultipartFile txt = new MockMultipartFile("image", "a.txt", "text/plain", "hello".getBytes());
        call(multipart("/api/nutrition/meals/photo").file(txt), a.token(), null, 400);
    }

    /** FR-8 RTM: "open the food information tab and check different restaurants in UTD Dining for their nutritional values". */
    @Test
    void fr8_diningMenusListItemsWithMacrosPerLocation() throws Exception {
        Account a = newStudent("dine");
        JsonNode locations = GET("/api/nutrition/menu/locations", a, 200);
        assertThat(locations.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode loc : locations) {
            JsonNode items = GET("/api/nutrition/menu?location=" + loc.asText(), a, 200);
            assertThat(items.size()).isPositive();
            for (JsonNode it : items) {
                assertThat(it.get("location").asText()).isEqualTo(loc.asText());
                assertThat(it.has("calories") && it.has("proteinG") && it.has("carbsG") && it.has("fatG")).isTrue();
            }
        }
        GET("/api/nutrition/menu?location=Nowhere", a, 404);
    }

    /** SD-1 alt [Dining menu item]: select item -> saveMealEntry; MealEntry "based on" DiningItem. */
    @Test
    void diningItemBecomesAMealEntryWithTheItemsMacros() throws Exception {
        Account a = newStudent("dineadd");
        String loc = GET("/api/nutrition/menu/locations", a, 200).get(0).asText();
        JsonNode item = GET("/api/nutrition/menu?location=" + loc, a, 200).get(0);
        JsonNode r = POST("/api/nutrition/meals", a, Map.of("source", "DINING_MENU",
                "diningItemId", item.get("itemId").asLong(), "calories", 1), 201);   // client calories ignored
        JsonNode saved = r.get("saved");
        assertThat(saved.get("foodName").asText()).isEqualTo(item.get("name").asText());
        assertThat(saved.get("calories").asInt()).isEqualTo(item.get("calories").asInt());
        assertThat(saved.get("diningItemId").asLong()).isEqualTo(item.get("itemId").asLong());
        assertThat(saved.get("source").asText()).isEqualTo("DINING_MENU");
    }

    /** FR-6 RTM: "enter in the intake data and if the amount of water drank is too low, it will display a message". */
    @Test
    void fr6_waterIsTrackedAndALowIntakeMessageIsShown() throws Exception {
        Account a = newStudent("water");
        JsonNode s0 = GET("/api/nutrition/summary", a, 200);
        assertThat(s0.get("waterMl").asInt()).isZero();
        assertThat(s0.get("messages").toString()).contains("water intake is low");

        JsonNode r = POST("/api/nutrition/water", a, Map.of("volumeMl", 500), 201);
        assertThat(r.get("summary").get("waterMl").asInt()).isEqualTo(500);
        assertThat(r.get("summary").get("messages").toString()).contains("water intake is low");

        int goal = r.get("summary").get("waterGoalMl").asInt();
        JsonNode done = POST("/api/nutrition/water", a, Map.of("volumeMl", goal - 500), 201).get("summary");
        assertThat(done.get("waterMl").asInt()).isEqualTo(goal);
        assertThat(done.get("messages").toString()).contains("Water goal reached").doesNotContain("water intake is low");
        assertThat(GET("/api/nutrition/water", a, 200)).hasSize(2);

        POST("/api/nutrition/water", a, Map.of("volumeMl", 0), 400);
        POST("/api/nutrition/water", a, Map.of("volumeMl", 99999), 400);
    }

    /**
     * D2 §4.1 test plan through the REST API: "entering three 500 mL refills should result in a daily total of
     * 1,500 mL"; team repo code LogWater: liters or gallons, stored as liters, message "Added ...L to ...".
     */
    @Test
    void d2_threeRefillsOf500mlTotal1500ml_andGallonsAreConverted() throws Exception {
        Account a = newStudent("refill");
        JsonNode last = null;
        for (int i = 0; i < 3; i++) {
            last = POST("/api/nutrition/water", a, Map.of("amount", 0.5, "isLiter", true), 201);
        }
        assertThat(last.get("summary").get("waterMl").asInt()).isEqualTo(1500);
        assertThat(last.get("summary").get("waterLiters").asDouble()).isEqualTo(1.5);
        assertThat(last.get("saved").get("amount").asDouble()).isEqualTo(0.5);
        assertThat(last.get("message").asText()).isEqualTo("Added 0.5L to Name " + a.username());
        assertThat(GET("/api/nutrition/water", a, 200)).hasSize(3);

        JsonNode gal = POST("/api/nutrition/water", a, Map.of("amount", 0.3, "isLiter", false), 201);   // TC-1 values
        assertThat(gal.get("saved").get("amount").asDouble()).isEqualTo(1.13);
        assertThat(gal.get("message").asText()).startsWith("Added 1.13L to ");
        assertThat(gal.get("summary").get("waterMl").asInt()).isEqualTo(2630);
    }

    /** D2 §4.1: "negative or missing values should be rejected with an appropriate validation message" (TC-2). */
    @Test
    void d2_negativeOrMissingWaterIsRejectedWithAMessage() throws Exception {
        Account a = newStudent("badwater");
        JsonNode neg = POST("/api/nutrition/water", a, Map.of("amount", -0.3, "isLiter", true), 400);
        assertThat(neg.get("message").asText()).isEqualTo("Negative amount.");
        JsonNode missing = POST("/api/nutrition/water", a, Map.of("isLiter", true), 400);
        assertThat(missing.get("message").asText()).isEqualTo("Amount is required.");
        assertThat(GET("/api/nutrition/water", a, 200)).isEmpty();
    }

    /** UC-1 "View Daily Summary & Goals": totals of calories, macros and water vs the student's goals. */
    @Test
    void dailySummaryTotalsEverythingAgainstGoals() throws Exception {
        Account a = newStudent("sum");
        POST("/api/nutrition/meals", a, Map.of("source", "MANUAL", "foodName", "A", "calories", 300, "proteinG", 10.5, "carbsG", 20, "fatG", 5), 201);
        POST("/api/nutrition/meals", a, Map.of("source", "MANUAL", "foodName", "B", "calories", 200, "proteinG", 4.5, "carbsG", 30, "fatG", 1), 201);
        POST("/api/nutrition/water", a, Map.of("volumeMl", 750), 201);
        JsonNode s = GET("/api/nutrition/summary", a, 200);
        assertThat(s.get("calories").asInt()).isEqualTo(500);
        assertThat(s.get("proteinG").asDouble()).isEqualTo(15.0);
        assertThat(s.get("carbsG").asDouble()).isEqualTo(50.0);
        assertThat(s.get("fatG").asDouble()).isEqualTo(6.0);
        assertThat(s.get("waterMl").asInt()).isEqualTo(750);
        assertThat(s.get("calorieGoal").asInt()).isEqualTo(2000);
        assertThat(s.get("mealCount").asInt()).isEqualTo(2);
    }

    /** NFR Safety: "warning whenever an unsafe goal is set, such as a low/high calorie target or excessive water intake". */
    @Test
    void unsafeGoalsAreSavedWithWarnings() throws Exception {
        Account a = newStudent("goal");
        JsonNode ok = PUT("/api/profile/goals", a, Map.of("calorieGoal", 2200, "waterGoalMl", 2500, "stepGoal", 8000, "bottleSizeMl", 750), 200);
        assertThat(ok.get("warnings")).isEmpty();
        assertThat(ok.get("profile").get("bottleSizeMl").asInt()).isEqualTo(750);

        JsonNode low = PUT("/api/profile/goals", a, Map.of("calorieGoal", 800, "waterGoalMl", 6000, "stepGoal", 8000), 200);
        assertThat(low.get("warnings").toString()).contains("below 1200 kcal").contains("excessive water intake");
        assertThat(low.get("profile").get("calorieGoal").asInt()).isEqualTo(800);   // still saved
        assertThat(GET("/api/nutrition/summary", a, 200).get("messages").toString()).contains("below 1200 kcal");

        JsonNode high = PUT("/api/profile/goals", a, Map.of("calorieGoal", 5000, "waterGoalMl", 500, "stepGoal", 50000), 200);
        assertThat(high.get("warnings").toString()).contains("above 4000 kcal").contains("dehydrated").contains("over-exertion");
    }
}
