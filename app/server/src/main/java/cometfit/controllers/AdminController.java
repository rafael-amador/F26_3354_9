package cometfit.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import cometfit.api.ApiException;
import cometfit.domain.AppModule;
import cometfit.domain.UptimeRecord;
import cometfit.security.AuthService;
import cometfit.security.Developer;
import cometfit.security.IntegrationKeyVerifier;
import cometfit.security.Permissions;
import cometfit.services.AlertNotifier;
import cometfit.services.AlertNotifier.DowntimeAlert;
import cometfit.services.FeatureFlagService;
import cometfit.services.MonitoringService;
import cometfit.services.MonitoringService.StatusChange;
import cometfit.services.MonitoringService.UptimeReport;
import cometfit.services.ServerHealthProbes;
import cometfit.stubs.OutageSimulator;

/**
 * AdminController — Deliverable 1 §5.2 "Controllers": healthCheck(servers), getUptimeReport() : UptimeReport,
 * disableModule(moduleId), enableModule(moduleId). Uses FeatureFlagService and MonitoringService.
 * Follows SD-4 "Monitor and Control System (UC-4)". FR-1 (uptime + status), FR-5 (shut down portions of the app).
 * §5.3.2 "Administrator Service: uptime, logs". Not behind a feature flag (it is the control plane).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController extends BaseController {
    private final MonitoringService monitoringService;
    private final FeatureFlagService featureFlagService;
    private final AlertNotifier alerts;
    private final IntegrationKeyVerifier integrationKey;
    private final OutageSimulator outages;

    public AdminController(AuthService authService, MonitoringService monitoringService,
                           FeatureFlagService featureFlagService, AlertNotifier alerts,
                           IntegrationKeyVerifier integrationKey, OutageSimulator outages) {
        super(authService);
        this.monitoringService = monitoringService;
        this.featureFlagService = featureFlagService;
        this.alerts = alerts;
        this.integrationKey = integrationKey;
        this.outages = outages;
    }

    public record HealthCheckRequest(List<String> servers) {}

    public record AccessView(Long userId, String username, String adminRole, List<String> permissions) {}

    public record OutageRequest(String serviceName, boolean down) {}

    // ---- loop [every interval]: Monitoring Service -> healthCheck(servers) ---------------------------------

    /** HTTP entry for an external monitoring service (integration key, A-12). */
    @PostMapping("/health-check")
    public List<UptimeRecord> healthCheckEndpoint(
            @RequestHeader(value = IntegrationKeyVerifier.HEADER, required = false) String key,
            @RequestBody(required = false) HealthCheckRequest r) {
        integrationKey.verify(key);
        return healthCheck(r == null ? null : r.servers());
    }

    /**
     * healthCheck(servers) — SD-4 "healthCheck(servers) -> insert UptimeRecord; alt [service down] send downtime
     * alert". UC-4 "Raise Downtime Alert" «extend» "Track Server Uptime".
     */
    public List<UptimeRecord> healthCheck(List<String> servers) {
        List<UptimeRecord> recs = monitoringService.checkServers(servers);
        for (StatusChange c : monitoringService.statusChanges(recs)) {
            alerts.sendDowntimeAlert(new DowntimeAlert(Instant.now(), c.serviceName(), c.nowUp(),
                    c.nowUp() ? c.serviceName() + " recovered" : c.serviceName() + " is DOWN"));
        }
        return recs;
    }

    // ---- Developer: login (password + TOTP) -> authorize(admin, permissions) -> access granted -------------

    /** UC-4 "Authorize Admin (Permissions + TOTP)": the session already required TOTP; this checks permissions. */
    @GetMapping("/access")
    public AccessView authorize(@RequestHeader("Authorization") String token) {
        Developer d = requireDeveloper(token, Permissions.ADMIN_UPTIME);
        return new AccessView(d.getUserId(), d.getUsername(), d.getAdminRole(), d.getPermissions());
    }

    /** getUptimeReport() : UptimeReport — SD-4 "view uptime metrics -> getUptimeReport() -> uptime % and incident history". FR-1. */
    @GetMapping("/uptime")
    public UptimeReport getUptimeReport(@RequestHeader("Authorization") String token,
                                        @RequestParam(defaultValue = "24") int hours) {
        requireDeveloper(token, Permissions.ADMIN_UPTIME);
        if (hours < 1 || hours > 24 * 30) throw ApiException.badRequest("hours must be 1..720");
        return monitoringService.report(hours);
    }

    @GetMapping("/alerts")
    public List<DowntimeAlert> alerts(@RequestHeader("Authorization") String token) {
        requireDeveloper(token, Permissions.ADMIN_UPTIME);
        return alerts.recent();
    }

    /** Lets a developer trigger an immediate check instead of waiting for the next interval. */
    @PostMapping("/uptime/check-now")
    public List<UptimeRecord> checkNow(@RequestHeader("Authorization") String token) {
        requireDeveloper(token, Permissions.ADMIN_UPTIME);
        return healthCheck(null);
    }

    // ---- modules (FR-5) ------------------------------------------------------------------------------------

    @GetMapping("/modules")
    public List<AppModule> modules(@RequestHeader("Authorization") String token) {
        requireDeveloper(token, Permissions.ADMIN_MODULES);
        return featureFlagService.all();
    }

    /** disableModule(moduleId) — SD-4 "disable module (e.g. Friends) -> setEnabled(moduleId, false) -> module disabled". */
    @PostMapping("/modules/{moduleId}/disable")
    public AppModule disableModule(@RequestHeader("Authorization") String token, @PathVariable String moduleId) {
        requireDeveloper(token, Permissions.ADMIN_MODULES);
        return featureFlagService.setEnabled(moduleId, false);
    }

    /** enableModule(moduleId) — SD-4 "re-enable module after fix -> setEnabled(moduleId, true)". */
    @PostMapping("/modules/{moduleId}/enable")
    public AppModule enableModule(@RequestHeader("Authorization") String token, @PathVariable String moduleId) {
        requireDeveloper(token, Permissions.ADMIN_MODULES);
        return featureFlagService.setEnabled(moduleId, true);
    }

    // ---- demo stub ------------------------------------------------------------------------------------------

    /** STUB control: force a component to report down/up (see {@link OutageSimulator}). */
    @PostMapping("/stub/outage")
    public Map<String, Object> simulateOutage(@RequestHeader("Authorization") String token, @RequestBody OutageRequest r) {
        requireDeveloper(token, Permissions.ADMIN_UPTIME);
        if (r == null || !ServerHealthProbes.SERVICES.contains(r.serviceName()))
            throw ApiException.badRequest("serviceName must be one of " + ServerHealthProbes.SERVICES);
        outages.set(r.serviceName(), r.down());
        healthCheck(List.of(r.serviceName()));
        Set<String> down = outages.current();
        return Map.of("simulatedDown", down);
    }
}
