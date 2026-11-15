package cometfit.security;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * TD-5 (2): a short-lived, single-use challenge created when the password is accepted on a device that is not
 * remembered. Only the 6-digit code can complete it ("Verify it's you"). TD-5 (3): the same kind of challenge
 * (purpose SETUP) finishes "Set up two-step verification" after Register. Stores only the SHA-256 hash of the id the
 * client holds. Not a class in the D1/D2 class diagram; it extends AuthService / Session ("Users and Security").
 * Lifetime and attempt limit: A-42.
 */
@Entity
@Table(name = "login_challenges")
public class LoginChallenge {
    public enum Purpose { LOGIN, SETUP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private int attempts;

    protected LoginChallenge() {}

    public LoginChallenge(Long userId, String tokenHash, Purpose purpose, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
    public int recordFailedAttempt() { return ++attempts; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Purpose getPurpose() { return purpose; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
}
