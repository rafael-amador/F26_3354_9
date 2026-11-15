package cometfit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * UC-3 View Friends' Progress — follows SD-3. Covers FR-3 and its RTM verification method ("open your profile and
 * search for a user and open their profile"), and NFR Legislative-Privacy (consent before sharing).
 */
class Uc3SocialTest extends ApiTestSupport {

    private long befriend(Account a, Account b) throws Exception {
        JsonNode req = POST("/api/social/requests", a, Map.of("targetId", b.id()), 201);
        long id = req.get("requestId").asLong();
        POST("/api/social/requests/" + id + "/accept", b, null, 200);
        return id;
    }

    /** FR-3 RTM: open your profile, search for a user, open their profile. */
    @Test
    void fr3_ownProfileSearchAndOtherProfiles() throws Exception {
        Account me = newStudent("prof");
        Account other = newStudent("findme");
        JsonNode mine = GET("/api/profile", me, 200);
        assertThat(mine.get("username").asText()).isEqualTo(me.username());

        JsonNode found = GET("/api/social/users/search?q=" + other.username(), me, 200);
        assertThat(found).hasSize(1);
        JsonNode theirs = GET("/api/social/users/" + found.get(0).get("userId").asLong(), me, 200);
        assertThat(theirs.get("displayName").asText()).isEqualTo("Name " + other.username());
        assertThat(theirs.get("relation").asText()).isEqualTo("NONE");

        // UC-3 "Edit Public Profile": a private profile hides the display name from non-friends
        PUT("/api/profile", other, Map.of("displayName", "Hidden Person", "isPublic", false), 200);
        JsonNode priv = GET("/api/social/users/" + other.id(), me, 200);
        assertThat(priv.get("displayName").isNull()).isTrue();
        assertThat(priv.get("username").asText()).isEqualTo(other.username());
        befriend(me, other);
        assertThat(GET("/api/social/users/" + other.id(), me, 200).get("displayName").asText()).isEqualTo("Hidden Person");
    }

    /** SD-3: sendRequest -> FriendRequest(pending) -> friend notified -> acceptRequest -> Friendship. */
    @Test
    void friendRequestLifecycle() throws Exception {
        Account a = newStudent("req");
        Account b = newStudent("req");
        JsonNode req = POST("/api/social/requests", a, Map.of("targetId", b.id()), 201);
        assertThat(req.get("status").asText()).isEqualTo("PENDING");
        POST("/api/social/requests", a, Map.of("targetId", b.id()), 409);         // duplicate
        POST("/api/social/requests", b, Map.of("targetId", a.id()), 409);         // reverse pending
        POST("/api/social/requests", a, Map.of("targetId", a.id()), 400);         // self

        // "notify of request": b sees it in their incoming list
        JsonNode incoming = GET("/api/social/requests", b, 200);
        assertThat(incoming.get(0).get("fromUsername").asText()).isEqualTo(a.username());
        POST("/api/social/requests/" + req.get("requestId").asLong() + "/accept", a, null, 403);   // not a's to accept
        POST("/api/social/requests/" + req.get("requestId").asLong() + "/accept", b, null, 200);
        assertThat(GET("/api/social/friends", a, 200).get(0).get("username").asText()).isEqualTo(b.username());
        assertThat(GET("/api/social/friends", b, 200).get(0).get("username").asText()).isEqualTo(a.username());

        // UC-3 "Remove Friend"
        DELETE("/api/social/friends/" + b.id(), a, 204);
        assertThat(GET("/api/social/friends", a, 200)).isEmpty();
    }

    @Test
    void declinedRequestDoesNotCreateAFriendship() throws Exception {
        Account a = newStudent("dec");
        Account b = newStudent("dec");
        long id = POST("/api/social/requests", a, Map.of("targetId", b.id()), 201).get("requestId").asLong();
        POST("/api/social/requests/" + id + "/decline", b, null, 204);
        assertThat(GET("/api/social/friends", a, 200)).isEmpty();
        POST("/api/social/requests/" + id + "/accept", b, null, 409);
    }

    /** SD-3: getFriendStats -> PrivacyService.filterByConsent -> consented stats only. NFR Legislative-Privacy. */
    @Test
    void friendStatsShowOnlyConsentedFields() throws Exception {
        Account me = newStudent("viewer");
        Account friend = newStudent("sharer");
        GET("/api/social/friends/" + friend.id() + "/stats", me, 403);           // not friends yet
        befriend(me, friend);
        POST("/api/nutrition/meals", friend, Map.of("source", "MANUAL", "foodName", "Pasta", "calories", 700), 201);
        POST("/api/nutrition/water", friend, Map.of("volumeMl", 400), 201);

        // default consent: nothing shared
        JsonNode none = GET("/api/social/friends/" + friend.id() + "/stats", me, 200);
        assertThat(none.get("caloriesToday").isNull()).isTrue();
        assertThat(none.get("waterTodayMl").isNull()).isTrue();
        assertThat(none.get("stepsToday").isNull()).isTrue();
        assertThat(none.get("visitsThisWeek").isNull()).isTrue();
        assertThat(none.get("points").isNull()).isTrue();

        // Friend: "choose fields to share -> setConsent(fields)"
        PUT("/api/social/consent", friend, Map.of("shareCalories", true, "shareWater", false, "shareSteps", false, "shareVisits", true), 200);
        JsonNode some = GET("/api/social/friends/" + friend.id() + "/stats", me, 200);
        assertThat(some.get("caloriesToday").asInt()).isEqualTo(700);
        assertThat(some.get("waterTodayMl").isNull()).isTrue();
        assertThat(some.get("visitsThisWeek").asInt()).isZero();
        assertThat(some.get("points").asInt()).isZero();
        assertThat(some.get("leaderboardRank").asInt()).isPositive();
        assertThat(GET("/api/social/consent", friend, 200).get("shareCalories").asBoolean()).isTrue();
    }

    /** UC-3 "View Leaderboard": me + friends ranked by points; friends listed only with consent. */
    @Test
    void leaderboardRanksFriendsWhoShare() throws Exception {
        Account me = newStudent("lb");
        Account sharing = newStudent("lbshare");
        Account hiding = newStudent("lbhide");
        befriend(me, sharing);
        befriend(me, hiding);
        PUT("/api/social/consent", sharing, Map.of("shareCalories", false, "shareWater", false, "shareSteps", false, "shareVisits", true), 200);
        POST("/api/activity/stub-reader/swipe", sharing, Map.of(), 201);    // +10 points
        POST("/api/activity/stub-reader/swipe", hiding, Map.of(), 201);

        JsonNode board = GET("/api/social/leaderboard", me, 200);
        assertThat(board).hasSize(2);
        assertThat(board.get(0).get("username").asText()).isEqualTo(sharing.username());
        assertThat(board.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(board.get(0).get("points").asInt()).isEqualTo(10);
        assertThat(board.get(1).get("me").asBoolean()).isTrue();
        assertThat(board.toString()).doesNotContain(hiding.username());
    }
}
