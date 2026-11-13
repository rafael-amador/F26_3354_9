package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * DeviceLink — §5.2 Domain Data: linkedAt, status; operations pair() : boolean, unlink().
 * Student 1 "links" 0..* DeviceLink; DeviceLink 0..* "pairs with" 1 SmartDevice. SD-2 "save DeviceLink".
 */
@Entity
@Table(name = "device_links")
public class DeviceLink {
    public static final String LINKED = "LINKED";
    public static final String UNLINKED = "UNLINKED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "smart_device_id")
    private SmartDevice device;
    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
    @Column(nullable = false)
    private String status;

    protected DeviceLink() {}

    public DeviceLink(Long studentId, SmartDevice device) {
        this.studentId = studentId;
        this.device = device;
        this.status = UNLINKED;
    }

    /** pair() — called once the device gateway confirmed pairing (SD-2 "pairing confirmed"). */
    public boolean pair() {
        this.status = LINKED;
        this.linkedAt = Instant.now();
        return true;
    }

    public void unlink() {
        this.status = UNLINKED;
    }

    public boolean isLinked() { return LINKED.equals(status); }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public SmartDevice getDevice() { return device; }
    public Instant getLinkedAt() { return linkedAt; }
    public String getStatus() { return status; }
}
