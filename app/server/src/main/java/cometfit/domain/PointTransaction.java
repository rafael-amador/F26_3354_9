package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/** PointTransaction — §5.2 Domain Data: amount, reason, timestamp. Returned by PointsService.awardPoints. */
@Entity
@Table(name = "point_transactions")
public class PointTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(nullable = false)
    private int amount;
    @Column(nullable = false)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant timestamp;

    protected PointTransaction() {}

    public PointTransaction(Long studentId, int amount, String reason, Instant timestamp) {
        this.studentId = studentId;
        this.amount = amount;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public int getAmount() { return amount; }
    public String getReason() { return reason; }
    public Instant getTimestamp() { return timestamp; }
}
