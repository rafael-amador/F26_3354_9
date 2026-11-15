package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * FriendRequest — §5.2 Domain Data: requestId, status, sentAt; operations accept() : Friendship, decline().
 * Student "sends" / "receives" 0..* FriendRequest. UC-3 "Add / Accept Friend"; SD-3 "insert FriendRequest (pending)".
 */
@Entity
@Table(name = "friend_requests")
public class FriendRequest {
    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long requestId;
    @Column(name = "sender_id", nullable = false)
    private Long senderId;
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;
    @Column(nullable = false)
    private String status;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected FriendRequest() {}

    public FriendRequest(Long senderId, Long receiverId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status = PENDING;
        this.sentAt = Instant.now();
    }

    /** accept() : Friendship (SD-3 "create Friendship"). */
    public Friendship accept() {
        if (!PENDING.equals(status)) throw new IllegalStateException("Request is not pending");
        this.status = ACCEPTED;
        return new Friendship(senderId, receiverId, Instant.now());
    }

    public void decline() {
        if (!PENDING.equals(status)) throw new IllegalStateException("Request is not pending");
        this.status = DECLINED;
    }

    public Long getRequestId() { return requestId; }
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public String getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }
}
