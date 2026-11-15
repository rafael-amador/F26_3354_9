package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import com.fasterxml.jackson.databind.JsonNode;

import cometfit.DemoDataSeeder.DemoStudent;

/**
 * TD-8 "More demo friends so the leaderboard looks like a real friend group" (extends TD-2; demo data only).
 * UC-3 "View Friend's Statistics", "View Leaderboard", "Add / Accept Friend". The per-metric boards are rebuilt here
 * the same way the Social screen does (A-48: consented friend stats + my own numbers, highest first).
 */
class Td8DemoFriendsTest extends ApiTestSupport {

    @Autowired DemoDataSeeder seeder;

    private Account demo(DemoStudent s) throws Exception {
        String token = login(s.username(), DemoDataSeeder.DEMO_PASSWORD, s.secret());
        long id = call(get("/api/auth/me"), token, null, 200).get("userId").asLong();
        return new Account(id, s.username(), s.secret(), token);
    }

    private static final List<String> SIX =
            List.of("alice", "bob", "starryskies23", "nebulanomad", "lunarlifter", "orbitrunner");

    /** TD-8 §1, §2: four new students, all sharing on, and the six are all friends with each other. */
    @Test
    void sixDemoStudentsAreAllFriendsAndShareEverything() throws Exception {
        assertThat(DemoDataSeeder.FRIEND_GROUP).extracting(DemoStudent::username).containsExactlyElementsOf(SIX);
        for (DemoStudent s : DemoDataSeeder.FRIEND_GROUP) {
            Account a = demo(s);
            JsonNode consent = GET("/api/social/consent", a, 200);
            assertThat(consent.get("shareCalories").asBoolean() && consent.get("shareWater").asBoolean()
                    && consent.get("shareSteps").asBoolean() && consent.get("shareVisits").asBoolean())
                    .as(s.username() + " shares everything").isTrue();
            List<String> expected = new ArrayList<>(SIX);
            expected.remove(s.username());
            assertThat(GET("/api/social/friends", a, 200).findValuesAsText("username"))
                    .as(s.username() + "'s friends").containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    /** TD-8 §3: alice's leaderboards list six people, alice is neither first nor last, leaders differ by board. */
    @Test
    void aliceIsNeitherFirstNorLastOnAnyBoard() throws Exception {
        Account alice = demo(DemoDataSeeder.FRIEND_GROUP.get(0));
        Account bob = demo(DemoDataSeeder.FRIEND_GROUP.get(1));

        // Points board (server, A-21)
        JsonNode points = GET("/api/social/leaderboard", alice, 200);
        assertThat(points.size()).isEqualTo(6);
        assertThat(points.findValuesAsText("username")).containsExactlyInAnyOrderElementsOf(SIX);
        assertThat(points.get(0).get("username").asText()).isEqualTo("orbitrunner");
        assertThat(points.get(0).get("me").asBoolean()).isFalse();
        assertThat(points.get(5).get("me").asBoolean()).isFalse();

        // Per-metric boards: friends' consented stats + alice's own numbers (read through bob, who is her friend).
        Map<String, JsonNode> stats = new HashMap<>();
        for (JsonNode f : GET("/api/social/friends", alice, 200)) {
            stats.put(f.get("username").asText(), GET("/api/social/friends/" + f.get("userId").asLong() + "/stats", alice, 200));
        }
        stats.put("alice", GET("/api/social/friends/" + alice.id() + "/stats", bob, 200));
        assertThat(stats).hasSize(6);

        Map<String, String> leaders = new HashMap<>();
        for (String metric : List.of("waterTodayMl", "caloriesToday", "visitsThisWeek", "points")) {
            List<String> order = rank(stats, s -> s.get(metric).asInt());
            assertThat(order.get(0)).as(metric + " leader").isNotEqualTo("alice");
            assertThat(order.get(5)).as(metric + " last").isNotEqualTo("alice");
            // no tie with alice's neighbours, so her place does not depend on the alphabetical tie-break
            int i = order.indexOf("alice");
            int mine = stats.get("alice").get(metric).asInt();
            assertThat(stats.get(order.get(i - 1)).get(metric).asInt()).isGreaterThan(mine);
            assertThat(stats.get(order.get(i + 1)).get(metric).asInt()).isLessThan(mine);
            leaders.put(metric, order.get(0));
        }
        // "one friend is the water leader, another the UREC regular, another logs the most calories"
        assertThat(leaders.get("waterTodayMl")).isEqualTo("starryskies23");
        assertThat(leaders.get("caloriesToday")).isEqualTo("nebulanomad");
        assertThat(leaders.get("visitsThisWeek")).isEqualTo("orbitrunner");
    }

    private static List<String> rank(Map<String, JsonNode> stats, Function<JsonNode, Integer> value) {
        List<String> names = new ArrayList<>(stats.keySet());
        names.sort(Comparator.comparing((String n) -> value.apply(stats.get(n))).reversed());
        return names;
    }

    /** TD-8 §3: today's numbers are filled in for every demo friend, whatever the time of day (A-61). */
    @Test
    void todaysNumbersAreFilledIn() throws Exception {
        Account alice = demo(DemoDataSeeder.FRIEND_GROUP.get(0));
        for (JsonNode f : GET("/api/social/friends", alice, 200)) {
            JsonNode s = GET("/api/social/friends/" + f.get("userId").asLong() + "/stats", alice, 200);
            String who = f.get("username").asText();
            assertThat(s.get("caloriesToday").asInt()).as(who).isPositive();
            assertThat(s.get("waterTodayMl").asInt()).as(who).isPositive();
            assertThat(s.get("stepsToday").asInt()).as(who).isPositive();
            assertThat(s.get("visitsThisWeek").asInt()).as(who).isPositive();
            assertThat(s.get("points").asInt()).as(who).isPositive();
        }
    }

    /** TD-8 §3 + TD-6: the new students' meals are real UTD Dining items. */
    @Test
    void newStudentsEatRealUtdDiningItems() throws Exception {
        Set<String> menuNames = new HashSet<>();
        Account alice = demo(DemoDataSeeder.FRIEND_GROUP.get(0));
        for (String loc : Td6UtdDiningTest.LOCATIONS) {
            GET("/api/nutrition/menu?location=" + loc, alice, 200).forEach(it -> menuNames.add(it.get("name").asText()));
        }
        LocalDate yesterday = LocalDate.now(ZoneId.of("America/Chicago")).minusDays(1);
        for (DemoStudent s : DemoDataSeeder.FRIEND_GROUP.subList(2, 6)) {
            JsonNode meals = GET("/api/nutrition/meals?date=" + yesterday, demo(s), 200);
            assertThat(meals.size()).as(s.username()).isGreaterThanOrEqualTo(3);
            for (JsonNode m : meals) {
                assertThat(m.get("source").asText()).isEqualTo("DINING_MENU");
                assertThat(menuNames).contains(m.get("foodName").asText());
            }
        }
    }

    /** TD-8 §4: one pending friend request to alice from cosmocomet (Cass), who is not a friend yet. */
    @Test
    void cosmocometHasAPendingRequestToAlice() throws Exception {
        Account alice = demo(DemoDataSeeder.FRIEND_GROUP.get(0));
        Account cass = demo(DemoDataSeeder.REQUESTER);
        JsonNode incoming = GET("/api/social/requests", alice, 200);
        assertThat(incoming.size()).isEqualTo(1);
        assertThat(incoming.get(0).get("fromUsername").asText()).isEqualTo("cosmocomet");
        assertThat(incoming.get(0).get("toUsername").asText()).isEqualTo("alice");
        assertThat(incoming.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(GET("/api/social/friends", alice, 200).findValuesAsText("username")).doesNotContain("cosmocomet");
        assertThat(GET("/api/social/friends", cass, 200).size()).isZero();
        assertThat(GET("/api/social/users/" + cass.id(), alice, 200).get("relation").asText())
                .isEqualTo("REQUEST_RECEIVED");
    }

    /** TD-8 §5: running the seeder again (server restart on a kept database) creates no duplicates. */
    @Test
    void seedingAgainDuplicatesNothing() throws Exception {
        Account alice = demo(DemoDataSeeder.FRIEND_GROUP.get(0));
        int meals = GET("/api/nutrition/meals?date=" + LocalDate.now(ZoneId.of("America/Chicago")).minusDays(1), alice, 200).size();
        seeder.run(new DefaultApplicationArguments());
        assertThat(GET("/api/social/friends", alice, 200).size()).isEqualTo(5);
        assertThat(GET("/api/social/requests", alice, 200).size()).isEqualTo(1);
        assertThat(GET("/api/social/leaderboard", alice, 200).size()).isEqualTo(6);
        assertThat(GET("/api/nutrition/meals?date=" + LocalDate.now(ZoneId.of("America/Chicago")).minusDays(1), alice, 200).size())
                .isEqualTo(meals);
    }
}
