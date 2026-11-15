package cometfit.security;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * A device the student chose to remember at sign-in — TD-1 (3) "Remember this device for 30 days".
 * Holds only the SHA-256 hash of the random device token the client keeps (A-39). Not a class in the D1/D2 class
 * diagram; it extends AuthService / Session in the "Users and Security" package.
 */
@Entity
@Table(name = "trusted_devices")
public class TrustedDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TrustedDevice() {}

    public TrustedDevice(Long userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
