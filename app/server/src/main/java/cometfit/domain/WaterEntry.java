package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * WaterEntry — §5.2 Domain Data: entryId, volumeMl, loggedAt. Student 1 "logs" 0..* WaterEntry.
 * FR-6 / UC-1 "Log Water Intake"; SD-1 "insert WaterEntry".
 */
@Entity
@Table(name = "water_entries")
public class WaterEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long entryId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "volume_ml", nullable = false)
    private int volumeMl;
    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    protected WaterEntry() {}

    public WaterEntry(Long studentId, int volumeMl, Instant loggedAt) {
        this.studentId = studentId;
        this.volumeMl = volumeMl;
        this.loggedAt = loggedAt;
    }

    public Long getEntryId() { return entryId; }
    public Long getStudentId() { return studentId; }
    public int getVolumeMl() { return volumeMl; }
    public Instant getLoggedAt() { return loggedAt; }
}
