package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * TD-2 "Demo accounts share their data": seeded alice and bob start with all sharing on and some activity history,
 * so UC-3 (friends' progress and leaderboard) shows real numbers; real new accounts still share nothing
 * (NFR Ethical / Legislative-Privacy, A-18).
 */
class DemoDataTest extends ApiTestSupport {

    private Account demo(String username, String secret) throws Exception {
        String token = login(username, DemoDataSeeder.DEMO_PASSWORD, secret);
        long id = call(get("/api/auth/me"), token, null, 200).get("userId").asLong();
        return new Account(id, username, secret, token);
    }

    @Test
    void demoStudentsAreFriendsAndShareRealNumbers() throws Exception {
        Account alice = demo("alice", DemoDataSeeder.ALICE_SECRET);
        Account bob = demo("bob", DemoDataSeeder.BOB_SECRET);

        JsonNode consent = GET("/api/social/consent", alice, 200);
        assertThat(consent.get("shareCalories").asBoolean() && consent.get("shareWater").asBoolean()
                && consent.get("shareSteps").asBoolean() && consent.get("shareVisits").asBoolean()).isTrue();

        JsonNode friends = GET("/api/social/friends", alice, 200);
        assertThat(friends.findValuesAsText("username")).contains("bob");

        JsonNode stats = GET("/api/social/friends/" + bob.id() + "/stats", alice, 200);
        assertThat(stats.get("caloriesToday").isNull()).isFalse();
        assertThat(stats.get("waterTodayMl").isNull()).isFalse();
        assertThat(stats.get("stepsToday").isNull()).isFalse();
        assertThat(stats.get("visitsThisWeek").isNull()).isFalse();
        assertThat(stats.get("points").asInt()).isPositive();

        JsonNode board = GET("/api/social/leaderboard", bob, 200);
        assertThat(board.size()).isEqualTo(6); // TD-8 §2: six demo friends (was alice + bob in TD-2)
        assertThat(board.findValuesAsText("username")).contains("alice", "bob");

        // "some activity history": visits and points from earlier days
        JsonNode visits = GET("/api/activity/visits", alice, 200);
        assertThat(visits.get("visits").size()).isGreaterThanOrEqualTo(3);
        assertThat(visits.get("balance").asInt()).isPositive();
    }

    /** TD-6 §5: the demo meal history uses real UTD Dining items (spec/06-utd-dining), not placeholder meals. */
    @Test
    void demoMealHistoryUsesRealUtdDiningItems() throws Exception {
        Account alice = demo("alice", DemoDataSeeder.ALICE_SECRET);
        java.time.LocalDate yesterday = java.time.LocalDate.now(java.time.ZoneId.of("America/Chicago")).minusDays(1);
        JsonNode meals = GET("/api/nutrition/meals?date=" + yesterday, alice, 200);
        assertThat(meals.size()).isGreaterThanOrEqualTo(3);
        java.util.Set<String> menuNames = new java.util.HashSet<>();
        for (String loc : Td6UtdDiningTest.LOCATIONS) {
            GET("/api/nutrition/menu?location=" + loc, alice, 200)
                    .forEach(it -> menuNames.add(it.get("name").asText()));
        }
        for (JsonNode m : meals) {
            assertThat(m.get("source").asText()).isEqualTo("DINING_MENU");
            assertThat(m.get("diningItemId").isNull()).isFalse();
            assertThat(menuNames).contains(m.get("foodName").asText());
            assertThat(m.get("foodName").asText()).doesNotContain("(demo)");
        }
        // bob's history includes calories-only items (macros "not published") on some day of the week
        Account bob = demo("bob", DemoDataSeeder.BOB_SECRET);
        boolean sawNull = false;
        for (int d = 1; d <= DemoDataSeeder.HISTORY_DAYS; d++) {
            for (JsonNode m : GET("/api/nutrition/meals?date=" + yesterday.minusDays(d - 1), bob, 200)) {
                sawNull |= m.get("proteinG").isNull();
            }
        }
        assertThat(sawNull).isTrue();
    }

    @Test
    void realNewAccountsStillShareNothing() throws Exception {
        Account fresh = newStudent("fresh");
        JsonNode consent = GET("/api/social/consent", fresh, 200);
        assertThat(consent.get("shareCalories").asBoolean() || consent.get("shareWater").asBoolean()
                || consent.get("shareSteps").asBoolean() || consent.get("shareVisits").asBoolean()).isFalse();
    }
}
