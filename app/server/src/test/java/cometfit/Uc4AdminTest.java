package cometfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

import cometfit.services.FeatureFlagService;
import cometfit.stubs.OutageSimulator;

/**
 * UC-4 Monitor and Control System — follows SD-4. Covers FR-1 and FR-5 and their RTM verification methods
 * (Deliverable 1 §4.4).
 */
class Uc4AdminTest extends ApiTestSupport {
    @Autowired FeatureFlagService flags;
    @Autowired OutageSimulator outages;

    @AfterEach
    void reset() {
        flags.setEnabled("NUTRITION", true);
        flags.setEnabled("ACTIVITY", true);
        flags.setEnabled("SOCIAL", true);
        outages.set("social-service", false);
    }

    /** SD-4: login (password + TOTP) -> authorize(admin, permissions) -> access granted. */
    @Test
    void developerIsAuthorizedByPermissions() throws Exception {
        Account dev = developer();
        JsonNode access = GET("/api/admin/access", dev, 200);
        assertThat(access.get("permissions").toString()).contains("admin.uptime.view").contains("admin.modules.manage");
        GET("/api/admin/access", newStudent("noadmin"), 403);
    }

    /** FR-1 RTM: "open the server monitoring dashboard and verify that the current uptime percentage and server status are displayed". */
    @Test
    void fr1_uptimePercentageAndServerStatus() throws Exception {
        Account dev = developer();
        POST("/api/admin/uptime/check-now", dev, null, 200);
        JsonNode report = GET("/api/admin/uptime?hours=24", dev, 200);
        assertThat(report.get("overallUptimePercent").asDouble()).isBetween(0.0, 100.0);
        assertThat(report.get("services").size()).isEqualTo(7);
        for (JsonNode s : report.get("services")) {
            assertThat(s.has("up") && s.has("uptimePercent") && s.has("lastCheckedAt")).isTrue();
        }
        assertThat(report.get("checkIntervalSec").asInt()).isPositive();
    }

    /** SD-4 loop: Monitoring Service -> healthCheck(servers) -> insert UptimeRecord (external call needs the key). */
    @Test
    void externalMonitorMustPresentIntegrationKey() throws Exception {
        call(post("/api/admin/health-check"), null, Map.of("servers", java.util.List.of("database")), 401);
        JsonNode recs = body(mvc.perform(post("/api/admin/health-check").header("X-Integration-Key", "dev-integration-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"servers\":[\"database\"]}")).andReturn(), 200);
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).get("serviceName").asText()).isEqualTo("database");
        assertThat(recs.get(0).get("up").asBoolean()).isTrue();
    }

    /** UC-4 "Raise Downtime Alert" («extend» Track Server Uptime); SD-4 alt [service down] send downtime alert. */
    @Test
    void downtimeRaisesAlertAndIncident() throws Exception {
        Account dev = developer();
        POST("/api/admin/uptime/check-now", dev, null, 200);
        POST("/api/admin/stub/outage", dev, Map.of("serviceName", "social-service", "down", true), 200);

        JsonNode report = GET("/api/admin/uptime", dev, 200);
        JsonNode social = null;
        for (JsonNode s : report.get("services")) if (s.get("serviceName").asText().equals("social-service")) social = s;
        assertThat(social.get("up").asBoolean()).isFalse();
        assertThat(report.get("allUp").asBoolean()).isFalse();
        assertThat(report.get("incidents").toString()).contains("social-service");
        assertThat(GET("/api/admin/alerts", dev, 200).get(0).get("message").asText()).isEqualTo("social-service is DOWN");

        POST("/api/admin/stub/outage", dev, Map.of("serviceName", "social-service", "down", false), 200);
        assertThat(GET("/api/admin/alerts", dev, 200).get(0).get("message").asText()).isEqualTo("social-service recovered");
        POST("/api/admin/stub/outage", dev, Map.of("serviceName", "nope", "down", true), 400);
    }

    /**
     * FR-5 RTM: "shut down a portion of the app and then open the app". SD-4: disableModule -> setEnabled(false) ->
     * Mobile App isEnabled -> false -> maintenance notice, other features stay available; then enableModule.
     */
    @Test
    void fr5_disabledModuleShowsMaintenanceWhileOthersKeepWorking() throws Exception {
        Account dev = developer();
        Account student = newStudent("maint");
        assertThat(POST("/api/admin/modules/SOCIAL/disable", dev, null, 200).get("enabled").asBoolean()).isFalse();

        GET("/api/modules/SOCIAL", student, 200);
        assertThat(GET("/api/modules/SOCIAL", student, 200).asBoolean()).isFalse();
        JsonNode blocked = GET("/api/social/friends", student, 503);
        assertThat(blocked.get("code").asText()).isEqualTo("MODULE_DISABLED");
        assertThat(blocked.get("message").asText()).contains("maintenance");
        GET("/api/nutrition/summary", student, 200);                       // other features stay available
        GET("/api/activity/visits", student, 200);
        GET("/api/profile", student, 200);

        POST("/api/admin/modules/SOCIAL/enable", dev, null, 200);
        GET("/api/social/friends", student, 200);
        assertThat(GET("/api/modules/SOCIAL", student, 200).asBoolean()).isTrue();
        POST("/api/admin/modules/UNKNOWN/disable", dev, null, 404);
    }
}
