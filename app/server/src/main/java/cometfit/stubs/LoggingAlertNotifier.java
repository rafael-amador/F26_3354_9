package cometfit.stubs;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cometfit.services.AlertNotifier;

/**
 * *** STUB *** alert channel to developers (UC-4 "Raise Downtime Alert"). Writes a WARN log line and keeps the last
 * 50 alerts in memory so the admin dashboard can show them. No e-mail / SMS / pager is contacted (A-24).
 */
@Component
public class LoggingAlertNotifier implements AlertNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);
    private static final int KEEP = 50;
    private final Deque<DowntimeAlert> alerts = new ConcurrentLinkedDeque<>();

    @Override
    public void sendDowntimeAlert(DowntimeAlert alert) {
        log.warn("[DOWNTIME ALERT stub] {} - {}", alert.serviceName(), alert.message());
        alerts.addFirst(alert);
        while (alerts.size() > KEEP) alerts.pollLast();
    }

    @Override
    public List<DowntimeAlert> recent() {
        return new ArrayList<>(alerts);
    }
}
