package cometfit.stubs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cometfit.controllers.AdminController;

/**
 * *** STUB *** of the external "Monitoring Service" actor (UC-4 "Track Server Uptime"; SD-4 loop [every interval]
 * "Monitoring Service -> AdminController: healthCheck(servers)"). Runs inside the server process on a timer
 * (cometfit.monitoring.check-interval-sec). A real external monitor would instead call
 * POST /api/admin/health-check with the integration key. Disable with COMETFIT_MONITOR_STUB=false.
 */
@Component
@ConditionalOnProperty(name = "cometfit.monitoring.stub-enabled", havingValue = "true")
public class MonitoringAgentStub {
    private static final Logger log = LoggerFactory.getLogger(MonitoringAgentStub.class);
    private final AdminController adminController;

    public MonitoringAgentStub(AdminController adminController) {
        this.adminController = adminController;
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "#{${cometfit.monitoring.check-interval-sec} * 1000}")
    public void tick() {
        try {
            adminController.healthCheck(null);
        } catch (RuntimeException e) {
            log.warn("Monitoring stub could not run a health check: {}", e.getMessage());
        }
    }
}
