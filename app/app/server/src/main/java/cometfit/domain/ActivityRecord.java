package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * ActivityRecord — §5.2 Domain Data: steps, heartRate, timestamp.
 * SmartDevice 1 "produces" 0..* ActivityRecord; Student 1 "has" 0..* ActivityRecord.
 * SD-2 "insert ActivityRecord". steps = steps counted since the previous sync (A-17).
 */
@Entity
@Table(name = "activity_records")
public class ActivityRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "smart_device_id", nullable = false)
    private Long smartDeviceId;
    @Column(nullable = false)
    private int steps;
    @Column(name = "heart_rate")
    private Integer heartRate;
    @Column(name = "recorded_at", nullable = false)
    private Instant timestamp;

    protected ActivityRecord() {}

    public ActivityRecord(Long studentId, Long smartDeviceId, int steps, Integer heartRate, Instant timestamp) {
        this.studentId = studentId;
        this.smartDeviceId = smartDeviceId;
        this.steps = steps;
        this.heartRate = heartRate;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getSmartDeviceId() { return smartDeviceId; }
    public int getSteps() { return steps; }
    public Integer getHeartRate() { return heartRate; }
    public Instant getTimestamp() { return timestamp; }
}
