package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * RecVisit — §5.2 Domain Data: visitId, timestamp. Student 1 "makes" 0..* RecVisit.
 * FR-7 / UC-2 "Check In at UREC"; SD-2 "insert RecVisit".
 */
@Entity
@Table(name = "rec_visits")
public class RecVisit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long visitId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "reader_id", nullable = false)
    private String readerId;
    @Column(name = "visited_at", nullable = false)
    private Instant timestamp;

    protected RecVisit() {}

    public RecVisit(Long studentId, String readerId, Instant timestamp) {
        this.studentId = studentId;
        this.readerId = readerId;
        this.timestamp = timestamp;
    }

    public Long getVisitId() { return visitId; }
    public Long getStudentId() { return studentId; }
    public String getReaderId() { return readerId; }
    public Instant getTimestamp() { return timestamp; }
}
