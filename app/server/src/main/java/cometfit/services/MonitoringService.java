package cometfit.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.domain.UptimeRecord;
import cometfit.domain.UptimeRecordRepository;

/**
 * MonitoringService — Deliverable 1 §5.2 "Services": checkIntervalSec : int, checkServers() : List&lt;UptimeRecord&gt;,
 * getUptimePercent(range) : double; "records" UptimeRecord.
 * FR-1 "track server uptime, and current status"; UC-4 "Track Server Uptime"; SD-4 "healthCheck(servers) -> insert
 * UptimeRecord" and "getUptimeReport() -> query UptimeRecords -> uptime % and incident history".
 */
@Service
public class MonitoringService {
    private final List<HealthProbe> probes;
    private final UptimeRecordRepository records;
    private final int checkIntervalSec;
    /** Last known status per service, used to raise an alert only when a service goes down / recovers (A-24). */
    private final Map<String, Boolean> lastStatus = new ConcurrentHashMap<>();

    public MonitoringService(List<HealthProbe> probes, UptimeRecordRepository records,
                             @Value("${cometfit.monitoring.check-interval-sec}") int checkIntervalSec) {
        this.probes = probes;
        this.records = records;
        this.checkIntervalSec = checkIntervalSec;
    }

    public int getCheckIntervalSec() { return checkIntervalSec; }

    public record StatusChange(String serviceName, boolean nowUp) {}

    public record ServiceStatus(String serviceName, boolean up, Instant lastCheckedAt, int lastResponseMs,
                                double uptimePercent, long checks) {}

    public record Incident(String serviceName, Instant startedAt, Instant endedAt, long durationSec, boolean ongoing) {}

    public record UptimeReport(Instant generatedAt, int rangeHours, double overallUptimePercent, boolean allUp,
                               List<ServiceStatus> services, List<Incident> incidents, int checkIntervalSec) {}

    /** checkServers() — probes every server (or only the named ones) and stores one UptimeRecord each. */
    @Transactional
    public List<UptimeRecord> checkServers(Collection<String> only) {
        List<UptimeRecord> out = new ArrayList<>();
        for (HealthProbe p : probes) {
            if (only != null && !only.isEmpty() && !only.contains(p.serviceName())) continue;
            long t0 = System.nanoTime();
            boolean up;
            try {
                p.check();
                up = true;
            } catch (Exception e) {
                up = false;
            }
            int ms = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - t0) / 1_000_000);
            out.add(records.save(new UptimeRecord(Instant.now(), p.serviceName(), up, ms)));
        }
        return out;
    }

    /** Status transitions since the previous check (first observation of a down service counts as a change). */
    public List<StatusChange> statusChanges(List<UptimeRecord> latest) {
        List<StatusChange> changes = new ArrayList<>();
        for (UptimeRecord r : latest) {
            Boolean prev = lastStatus.put(r.getServiceName(), r.isUp());
            if ((prev == null && !r.isUp()) || (prev != null && prev != r.isUp())) {
                changes.add(new StatusChange(r.getServiceName(), r.isUp()));
            }
        }
        return changes;
    }

    /** getUptimePercent(range) : double — share of successful checks over the last {@code range}. */
    @Transactional(readOnly = true)
    public double getUptimePercent(Duration range) {
        return percent(records.findByCheckedAtAfterOrderByCheckedAtDesc(Instant.now().minus(range)));
    }

    @Transactional(readOnly = true)
    public UptimeReport report(int rangeHours) {
        List<UptimeRecord> recs = records.findByCheckedAtAfterOrderByCheckedAtDesc(Instant.now().minus(Duration.ofHours(rangeHours)));
        Map<String, List<UptimeRecord>> byService = new LinkedHashMap<>();
        for (HealthProbe p : probes) byService.put(p.serviceName(), new ArrayList<>());
        for (UptimeRecord r : recs) byService.computeIfAbsent(r.getServiceName(), k -> new ArrayList<>()).add(r);

        List<ServiceStatus> statuses = new ArrayList<>();
        List<Incident> incidents = new ArrayList<>();
        byService.forEach((name, list) -> {
            UptimeRecord last = list.isEmpty() ? null : list.get(0);
            statuses.add(new ServiceStatus(name, last == null || last.isUp(), last == null ? null : last.getCheckedAt(),
                    last == null ? 0 : last.getResponseMs(), percent(list), list.size()));
            incidents.addAll(incidentsOf(name, list));
        });
        incidents.sort(Comparator.comparing(Incident::startedAt).reversed());
        boolean allUp = statuses.stream().allMatch(ServiceStatus::up);
        return new UptimeReport(Instant.now(), rangeHours, percent(recs), allUp, statuses, incidents, checkIntervalSec);
    }

    /** Consecutive "down" records of one service form an incident. {@code newestFirst} is ordered newest first. */
    static List<Incident> incidentsOf(String name, List<UptimeRecord> newestFirst) {
        List<Incident> out = new ArrayList<>();
        Instant start = null, end = null;
        boolean ongoing = false;
        for (int i = newestFirst.size() - 1; i >= 0; i--) {   // oldest -> newest
            UptimeRecord r = newestFirst.get(i);
            if (!r.isUp()) {
                if (start == null) start = r.getCheckedAt();
                if (i == 0) ongoing = true;
            } else if (start != null) {
                end = r.getCheckedAt();
                out.add(new Incident(name, start, end, Duration.between(start, end).toSeconds(), false));
                start = null;
            }
        }
        if (start != null) {
            Instant now = Instant.now();
            out.add(new Incident(name, start, null, Duration.between(start, now).toSeconds(), ongoing));
        }
        return out;
    }

    private static double percent(List<UptimeRecord> list) {
        if (list.isEmpty()) return 100.0;
        long up = list.stream().filter(UptimeRecord::isUp).count();
        return Math.round(up * 10000.0 / list.size()) / 100.0;
    }

    /** Keep 30 days of records (A-25). */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void purgeOld() {
        records.deleteOlderThan(Instant.now().minus(Duration.ofDays(30)));
    }
}
