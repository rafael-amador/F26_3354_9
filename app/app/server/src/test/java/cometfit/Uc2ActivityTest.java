package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

import cometfit.services.PointsService;

/**
 * UC-2 Track Activity and Rec Center Visits — follows SD-2. Covers FR-4 and FR-7 and their RTM verification
 * methods (Deliverable 1 §4.4).
 */
class Uc2ActivityTest extends ApiTestSupport {

    private Map<String, Object> device(String id, boolean permission) {
        return Map.of("deviceId", id, "deviceType", "watch", "platform", "simulated", "healthPermissionGranted", permission);
    }

    /** FR-4 RTM: "use 'link smart device' and connect your smart watch ... verify that the device's activity data actually synchronizes". */
    @Test
    void fr4_linkDeviceThenStepsSynchronise() throws Exception {
        Account a = newStudent("dev");
        String id = "W-" + a.username();
        POST("/api/activity/devices", a, device(id, false), 400);       // UC-2 «include» Grant Health Permissions
        JsonNode link = POST("/api/activity/devices", a, device(id, true), 201);
        assertThat(link.get("status").asText()).isEqualTo("LINKED");
        assertThat(GET("/api/activity/devices", a, 200)).hasSize(1);

        JsonNode r1 = POST("/api/activity/steps", a, Map.of("deviceId", id, "steps", 1200, "heartRate", 88), 201);
        JsonNode r2 = POST("/api/activity/steps", a, Map.of("deviceId", id, "steps", 800, "heartRate", 95), 201);
        assertThat(r1.get("stepsToday").asInt()).isEqualTo(1200);
        assertThat(r2.get("stepsToday").asInt()).isEqualTo(2000);
        assertThat(GET("/api/activity/records", a, 200)).hasSize(2);
        JsonNode sum = GET("/api/nutrition/summary", a, 200);
        assertThat(sum.get("steps").asInt()).isEqualTo(2000);
        assertThat(sum.get("latestHeartRate").asInt()).isEqualTo(95);
    }

    @Test
    void pairingFailureAndUnlinkedDevicesAreRejected() throws Exception {
        Account a = newStudent("devfail");
        POST("/api/activity/devices", a, device("FAIL-1-" + a.username(), true), 502);
        POST("/api/activity/steps", a, Map.of("deviceId", "never-linked", "steps", 10), 400);

        String id = "W2-" + a.username();
        POST("/api/activity/devices", a, device(id, true), 201);
        DELETE("/api/activity/devices/" + id, a, 204);
        assertThat(GET("/api/activity/devices", a, 200)).isEmpty();
        POST("/api/activity/steps", a, Map.of("deviceId", id, "steps", 10), 400);

        Account b = newStudent("devother");
        POST("/api/activity/devices", a, device(id, true), 201);
        POST("/api/activity/devices", b, device(id, true), 409);           // already someone else's device
    }

    /** UC-2 "Sync Steps" «extend» "Earn Points": reaching the step goal awards points once per day. */
    @Test
    void reachingTheStepGoalEarnsPointsOncePerDay() throws Exception {
        Account a = newStudent("goal");
        PUT("/api/profile/goals", a, Map.of("calorieGoal", 2000, "waterGoalMl", 2000, "stepGoal", 1000), 200);
        String id = "W3-" + a.username();
        POST("/api/activity/devices", a, device(id, true), 201);
        JsonNode r1 = POST("/api/activity/steps", a, Map.of("deviceId", id, "steps", 600), 201);
        assertThat(r1.get("pointsAwarded").isNull()).isTrue();
        JsonNode r2 = POST("/api/activity/steps", a, Map.of("deviceId", id, "steps", 600), 201);
        assertThat(r2.get("pointsAwarded").get("amount").asInt()).isEqualTo(PointsService.STEP_GOAL_POINTS);
        JsonNode r3 = POST("/api/activity/steps", a, Map.of("deviceId", id, "steps", 600), 201);
        assertThat(r3.get("pointsAwarded").isNull()).isTrue();
        assertThat(r3.get("balance").asInt()).isEqualTo(PointsService.STEP_GOAL_POINTS);
    }

    /**
     * FR-7 RTM: "swipe into the rec center and open the app and check if your rec center visits are updated".
     * SD-2: swipe -> recordVisit -> insert RecVisit -> awardPoints(studentId, VISIT) -> new balance.
     */
    @Test
    void fr7_cardSwipeRecordsVisitAndAwardsPoints() throws Exception {
        Account a = newStudent("urec");
        // the external card reader calls the API with the integration key
        Map<String, Object> swipe = Map.of("studentId", a.id(), "readerId", "UREC-FRONT-1");
        call(post("/api/activity/visits"), null, swipe, 401);
        JsonNode v1 = body(mvc.perform(post("/api/activity/visits").header("X-Integration-Key", "dev-integration-key")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(swipe))).andReturn(), 201);
        assertThat(v1.get("pointsAwarded").get("amount").asInt()).isEqualTo(PointsService.VISIT_POINTS);
        assertThat(v1.get("balance").asInt()).isEqualTo(PointsService.VISIT_POINTS);

        // second swipe the same day (via the in-app reader stub): logged, no extra points
        JsonNode v2 = POST("/api/activity/stub-reader/swipe", a, Map.of(), 201);
        assertThat(v2.get("pointsAwarded").isNull()).isTrue();

        // "open the app and check if your rec center visits are updated"
        JsonNode hist = GET("/api/activity/visits", a, 200);
        assertThat(hist.get("visits")).hasSize(2);
        assertThat(hist.get("visitsThisWeek").asInt()).isEqualTo(2);
        assertThat(hist.get("balance").asInt()).isEqualTo(PointsService.VISIT_POINTS);
        assertThat(hist.get("transactions").get(0).get("reason").asText()).isEqualTo("VISIT");
    }

    @Test
    void unknownReaderOrCardIsRejected() throws Exception {
        Account a = newStudent("urecbad");
        POST("/api/activity/stub-reader/swipe", a, Map.of("readerId", "NOT-A-READER"), 403);
        Map<String, Object> bad = Map.of("studentId", 999999, "readerId", "UREC-FRONT-1");
        body(mvc.perform(post("/api/activity/visits").header("X-Integration-Key", "dev-integration-key")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad))).andReturn(), 404);
    }
}
