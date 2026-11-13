package cometfit.domain;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * Friendship — §5.2 Domain Data: since. Student 2 "friends with" 0..* Friendship.
 * Stored with the smaller user id in userAId so a pair is unique.
 */
@Entity
@Table(name = "friendships")
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_a_id", nullable = false)
    private Long userAId;
    @Column(name = "user_b_id", nullable = false)
    private Long userBId;
    @Column(nullable = false)
    private Instant since;

    protected Friendship() {}

    public Friendship(Long u1, Long u2, Instant since) {
        this.userAId = Math.min(u1, u2);
        this.userBId = Math.max(u1, u2);
        this.since = since;
    }

    public Long otherThan(Long userId) {
        return userId.equals(userAId) ? userBId : userAId;
    }

    public Long getId() { return id; }
    public Long getUserAId() { return userAId; }
    public Long getUserBId() { return userBId; }
    public Instant getSince() { return since; }
}
