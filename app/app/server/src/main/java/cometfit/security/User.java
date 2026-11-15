package cometfit.security;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Abstract User — Deliverable 1 §5.2 class diagram, package "Users and Security".
 * Attributes: userId, username, passwordHash, email, totpSecret, displayName, isPublic.
 * Operations: updateProfile(), changePassword(old, new).
 * email and totpSecret are encrypted at rest (NFR Security; A-5).
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type")
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email_enc", nullable = false)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "totp_secret_enc", nullable = false)
    private String totpSecret;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** updateProfile() from §5.2 — edits the user-entered public information (UC-3 "Edit Public Profile", FR-3). */
    public void updateProfile(String displayName, Boolean isPublic) {
        if (displayName != null && !displayName.isBlank()) this.displayName = displayName.trim();
        if (isPublic != null) this.isPublic = isPublic;
    }

    /** changePassword(old, new) from §5.2. Returns false if the old password does not match. */
    public boolean changePassword(String oldPassword, String newPassword, PasswordHasher hasher) {
        if (!hasher.matches(oldPassword, passwordHash)) return false;
        this.passwordHash = hasher.hash(newPassword);
        return true;
    }

    /** Role used by AuthService.authorize (NFR Security "backend permissions list"). */
    public abstract String role();

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public Instant getCreatedAt() { return createdAt; }
}
