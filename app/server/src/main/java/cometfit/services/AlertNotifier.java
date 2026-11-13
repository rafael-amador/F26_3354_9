package cometfit.services;

import java.time.Instant;
import java.util.List;

/**
 * Delivers downtime alerts to developers — UC-4 "Raise Downtime Alert" («extend» Track Server Uptime);
 * SD-4 "alt [service down] send downtime alert" (AdminController -> Developer). The spec names no delivery channel
 * (and the Proposal excludes push notifications), so the shipped implementation is the labelled stub
 * {@link cometfit.stubs.LoggingAlertNotifier} that logs and keeps alerts for the admin dashboard (A-24).
 */
public interface AlertNotifier {
    record DowntimeAlert(Instant raisedAt, String serviceName, boolean recovered, String message) {}

    void sendDowntimeAlert(DowntimeAlert alert);

    List<DowntimeAlert> recent();
}
