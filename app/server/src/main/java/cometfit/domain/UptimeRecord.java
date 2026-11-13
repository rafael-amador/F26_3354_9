package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * UptimeRecord — §5.2 Domain Data: checkedAt, serviceName, isUp, responseMs.
 * MonitoringService "records" UptimeRecord. FR-1 / UC-4 "Track Server Uptime"; SD-4 "insert UptimeRecord".
 */
@Entity
@Table(name = "uptime_records")
public class UptimeRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
    @Column(name = "service_name", nullable = false)
    private String serviceName;
    @Column(name = "is_up", nullable = false)
    private boolean isUp;
    @Column(name = "response_ms", nullable = false)
    private int responseMs;

    protected UptimeRecord() {}

    public UptimeRecord(Instant checkedAt, String serviceName, boolean isUp, int responseMs) {
        this.checkedAt = checkedAt;
        this.serviceName = serviceName;
        this.isUp = isUp;
        this.responseMs = responseMs;
    }

    public Long getId() { return id; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getServiceName() { return serviceName; }
    public boolean isUp() { return isUp; }
    public int getResponseMs() { return responseMs; }
}
