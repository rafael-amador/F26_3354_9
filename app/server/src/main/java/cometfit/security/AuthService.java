package cometfit.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.api.ApiException;

/**
 * AuthService — Deliverable 1 §5.2 ("Users and Security").
 * Operations: authenticate(username, password) : Session, verifyTOTP(userId, code), validateSession(token),
 * authorize(userId, permission). This is the "Auth Gateway: TOTP, Authenticator, Authorization" box of §5.3.2.
 * TD-1 (3): "Remember this device for 30 days" — a remembered device signs in with username + password only; the
 * 6-digit code is required again on a new device or after 30 days (A-39). Still meets NFR Security (TD-1).
 */
@Service
public class AuthService {
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository users;
    private final SessionRepository sessions;
    private final TrustedDeviceRepository trustedDevices;
    private final PasswordHasher hasher;
    private final Duration sessionLifetime;
    private final Duration rememberFor;

    public AuthService(UserRepository users, SessionRepository sessions, TrustedDeviceRepository trustedDevices,
                       PasswordHasher hasher,
                       @Value("${cometfit.session-hours}") long sessionHours,
                       @Value("${cometfit.remember-device-days:30}") long rememberDays) {
        this.users = users;
        this.sessions = sessions;
        this.trustedDevices = trustedDevices;
        this.hasher = hasher;
        this.sessionLifetime = Duration.ofHours(sessionHours);
        this.rememberFor = Duration.ofDays(rememberDays);
    }

    /**
     * Result of a sign-in. {@code deviceToken} / {@code trustedUntil} are set only when the student asked to remember
     * this device and passed the TOTP check in this call (TD-1 (3)). {@code rememberedDevice} = the code was skipped.
     */
    public record LoginResult(Session session, String deviceToken, Instant trustedUntil, boolean rememberedDevice) {}

    /**
     * authenticate(username, password) : Session. Because the NFR makes TOTP mandatory for every user,
     * a Session is only created after {@link #verifyTOTP} also succeeds, so the TOTP code is passed in the
     * same call (A-6). One generic error message is used so usernames cannot be probed.
     */
    @Transactional
    public Session authenticate(String username, String password, String totpCode) {
        return login(username, password, totpCode, false, null).session();
    }

    /**
     * Sign-in with TD-1 (3). The password is always required. The TOTP code is skipped only when {@code deviceToken}
     * belongs to this user and is less than 30 days old; otherwise the code must be valid. Every failure gives the
     * same message (A-31), so the response never tells whether the password alone was right.
     * A remembered sign-in does not extend the 30 days (TD-1: "required again ... after 30 days").
     */
    @Transactional
    public LoginResult login(String username, String password, String totpCode, boolean rememberDevice,
                             String deviceToken) {
        User user = users.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .filter(u -> password != null && hasher.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid username, password or code"));
        Instant now = Instant.now();

        boolean remembered = isTrusted(user.getUserId(), deviceToken, now);
        String newDeviceToken = null;
        Instant trustedUntil = null;
        if (!remembered) {
            if (!verifyTOTP(user.getUserId(), totpCode)) {
                throw ApiException.unauthorized("Invalid username, password or code");
            }
            if (rememberDevice) {
                newDeviceToken = randomHex();
                trustedUntil = now.plus(rememberFor);
                trustedDevices.save(new TrustedDevice(user.getUserId(), sha256(newDeviceToken), now, trustedUntil));
            }
        }
        Session session = sessions.save(new Session(randomHex(), user, now, now.plus(sessionLifetime)));
        return new LoginResult(session, newDeviceToken, trustedUntil, remembered);
    }

    private boolean isTrusted(Long userId, String deviceToken, Instant now) {
        if (deviceToken == null || deviceToken.isBlank()) return false;
        return trustedDevices.findByTokenHash(sha256(deviceToken.trim()))
                .filter(t -> t.getUserId().equals(userId) && !t.isExpired(now))
                .isPresent();
    }

    /** Forget every remembered device of a user (used when the password changes; A-39). */
    @Transactional
    public void forgetTrustedDevices(Long userId) {
        trustedDevices.deleteByUserId(userId);
    }

    private static String randomHex() {
        byte[] t = new byte[32];
        RNG.nextBytes(t);
        return HexFormat.of().formatHex(t);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** verifyTOTP(userId, code) : boolean. */
    @Transactional(readOnly = true)
    public boolean verifyTOTP(Long userId, String code) {
        return users.findById(userId).map(u -> Totp.verify(u.getTotpSecret(), code, Instant.now())).orElse(false);
    }

    /** validateSession(token) : boolean. */
    @Transactional(readOnly = true)
    public boolean validateSession(String token) {
        return findValid(token).isPresent();
    }

    /** Returns the user behind a valid session (used by BaseController.requireSession). */
    @Transactional(readOnly = true)
    public User userForToken(String token) {
        return findValid(token).map(Session::getUser)
                .orElseThrow(() -> ApiException.unauthorized("Session missing or expired; please log in again"));
    }

    /** authorize(userId, permission) : boolean — checks the backend permissions list. */
    @Transactional(readOnly = true)
    public boolean authorize(Long userId, String permission) {
        return users.findById(userId).map(u -> switch (u) {
            case Developer d -> d.hasPermission(permission);
            case Student s -> Permissions.STUDENT_DEFAULT.contains(permission);
            default -> false;
        }).orElse(false);
    }

    @Transactional
    public void logout(String token) {
        if (token != null) sessions.deleteById(token);
    }

    private Optional<Session> findValid(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findById(token).filter(s -> !s.isExpired());
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpiredSessions() {
        sessions.deleteExpired(Instant.now());
        trustedDevices.deleteExpired(Instant.now());
    }
}
