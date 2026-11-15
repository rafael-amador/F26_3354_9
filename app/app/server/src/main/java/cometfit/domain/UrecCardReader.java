package cometfit.domain;

import jakarta.persistence.*;

/**
 * UrecCardReader — §5.2 Domain Data: readerId, location; operation sendSwipe(studentId).
 * UrecCardReader 1 "creates" 0..* RecVisit. This entity is the registry of known readers; the physical
 * reader is an external system (§5.3.2 "External Systems: UREC Card Reader") and is stubbed by
 * {@link cometfit.stubs.FakeUrecCardReader}.
 */
@Entity
@Table(name = "urec_card_readers")
public class UrecCardReader {
    @Id @Column(name = "reader_id")
    private String readerId;
    @Column(nullable = false)
    private String location;

    protected UrecCardReader() {}

    public UrecCardReader(String readerId, String location) {
        this.readerId = readerId;
        this.location = location;
    }

    public String getReaderId() { return readerId; }
    public String getLocation() { return location; }
}
