package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * WaterEntry — §5.2 Domain Data (D1 = D2 §2.5.2): entryId, volumeMl, loggedAt. Student 1 "logs" 0..* WaterEntry.
 * FR-6 / UC-1 "Log Water Intake"; SD-1 "insert WaterEntry".
 * Stored as liters in table water_log (team repo code {@code db/init.sql} WATER_LOG.amount, {@code LogWater.java}
 * "only liters will be entered into db"; A-34). {@link #getVolumeMl()} is derived for the class-diagram attribute.
 */
@Entity
@Table(name = "water_log")
public class WaterEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long entryId;
    @Column(name = "user_id", nullable = false)
    private Long studentId;
    /** Liters, truncated to two decimals (LogWater.enter). */
    @Column(name = "amount", nullable = false)
    private float amount;
    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    protected WaterEntry() {}

    public WaterEntry(Long studentId, double amountLiters, Instant loggedAt) {
        this.studentId = studentId;
        this.amount = (float) amountLiters;
        this.loggedAt = loggedAt;
    }

    public Long getEntryId() { return entryId; }
    public Long getStudentId() { return studentId; }
    /** Amount in liters (2 decimals). */
    public double getAmount() { return Math.round(amount * 100.0) / 100.0; }
    /** §5.2 volumeMl, derived from the stored liters. */
    public int getVolumeMl() { return (int) Math.round(amount * 1000.0); }
    public Instant getLoggedAt() { return loggedAt; }
}
