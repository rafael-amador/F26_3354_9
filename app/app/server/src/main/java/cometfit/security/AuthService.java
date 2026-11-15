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
 * TD-5: two steps. authenticate() checks the password; a remembered device is signed in, any other device gets a
 * single-use challenge, and only verifyChallenge() with a valid code creates the Session.
 */
@Service
public class AuthService {
    private static final SecureRandom RNG = new SecureRandom();
    /** TD-5 (2) "short-lived" challenge: A-42. */
    public static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    /** TD-5 (3) setup step needs time to install/scan in Duo Mobile: A-42. */
    public static final Duration SETUP_CHALLENGE_LIFETIME = Duration.ofMinutes(15);
    public static final int MAX_CODE_ATTEMPTS = 5;

    private final UserRepository users;
    private final SessionRepository sessions;
    private final TrustedDeviceRepository trustedDevices;
    private final LoginChallengeRepository challenges;
    private final PasswordHasher hasher;
    private final Duration sessionLifetime;
    private final Duration rememberFor;

    public AuthService(UserRepository users, SessionRepository sessions, TrustedDeviceRepository trustedDevices,
                       LoginChallengeRepository challenges, PasswordHasher hasher,
                       @Value("${cometfit.session-hours}") long sessionHours,
                       @Value("${cometfit.remember-device-days:30}") long rememberDays) {
        this.users = users;
        this.sessions = sessions;
        this.trustedDevices = trustedDevices;
        this.challenges = challenges;
        this.hasher = hasher;
        this.sessionLifetime = Duration.ofHours(sessionHours);
        this.rememberFor = Duration.ofDays(rememberDays);
    }

    /** One generic message for every credential failure (A-31, TD-5 (5)). */
    public static final String GENERIC_FAILURE = "Invalid username, password or code";

    /**
     * Result of a completed sign-in. {@code deviceToken} / {@code trustedUntil} are set only when the student asked to
     * remember this device and passed the TOTP check (TD-1 (3)). {@code rememberedDevice} = the code was skipped.
     */
    public record LoginResult(Session session, String deviceToken, Instant trustedUntil, boolean rememberedDevice) {}

    /** A pending second step (TD-5 (2) "Verify it's you", TD-5 (3) setup). Holds no session. */
    public record Challenge(String challengeId, Instant expiresAt, LoginChallenge.Purpose purpose) {}

    /** Outcome of the password step: exactly one of {@code result} (remembered device) or {@code challenge} is set. */
    public record PasswordStep(LoginResult result, Challenge challenge) {}

    /**
     * authenticate(username, password) — D1 §5.2, step 1 of TD-5. The password is always checked. If
     * {@code deviceToken} is a remembered device of this user that is less than 30 days old (TD-1 (3)), the user is
     * signed in with no second step (TD-5 (2)); the 30 days are not extended. Otherwise NO session is created: the
     * caller gets a short-lived, single-use challenge that only {@link #verifyChallenge} with a valid code completes.
     * Every failure gives the same message (A-31).
     */
    @Transactional
    public PasswordStep authenticate(String username, String password, String deviceToken) {
        User user = users.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .filter(u -> password != null && hasher.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized(GENERIC_FAILURE));
        Instant now = Instant.now();
        if (isTrusted(user.getUserId(), deviceToken, now)) {
            Session session = sessions.save(new Session(randomHex(), user, now, now.plus(sessionLifetime)));
            return new PasswordStep(new LoginResult(session, null, null, true), null);
        }
        return new PasswordStep(null, newChallenge(user.getUserId(), LoginChallenge.Purpose.LOGIN, now));
    }

    /** TD-5 (3): after Register, the first 6-digit code finishes "Set up two-step verification" and signs in. */
    @Transactional
    public Challenge startSetup(Long userId) {
        return newChallenge(userId, LoginChallenge.Purpose.SETUP, Instant.now());
    }

    private Challenge newChallenge(Long userId, LoginChallenge.Purpose purpose, Instant now) {
        String id = randomHex();
        Instant expires = now.plus(purpose == LoginChallenge.Purpose.SETUP ? SETUP_CHALLENGE_LIFETIME : CHALLENGE_LIFETIME);
        challenges.save(new LoginChallenge(userId, sha256(id), purpose, now, expires));
        return new Challenge(id, expires, purpose);
    }

    /**
     * Step 2 of TD-5: complete a challenge with the 6-digit TOTP code (verifyTOTP). The challenge is single-use
     * (deleted on success), expires (A-42: 5 min for sign-in, 15 min for setup) and is dropped after
     * {@value #MAX_CODE_ATTEMPTS} wrong codes. On success a Session is created and, if asked, this device is
     * remembered for 30 days (TD-1 (3)). {@code noRollbackFor} keeps the attempt counter when a wrong code throws.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult verifyChallenge(String challengeId, String totpCode, boolean rememberDevice) {
        if (challengeId == null || challengeId.isBlank()) throw ApiException.unauthorized(GENERIC_FAILURE);
        Instant now = Instant.now();
        LoginChallenge c = challenges.findByTokenHash(sha256(challengeId.trim()))
                .orElseThrow(() -> ApiException.unauthorized(GENERIC_FAILURE));
        if (c.isExpired(now)) {
            challenges.delete(c);
            throw ApiException.unauthorized(GENERIC_FAILURE);
        }
        if (!verifyTOTP(c.getUserId(), totpCode)) {
            if (c.recordFailedAttempt() >= MAX_CODE_ATTEMPTS) challenges.delete(c);
            else challenges.save(c);
            throw ApiException.unauthorized(GENERIC_FAILURE);
        }
        challenges.delete(c);   // single-use
        User user = users.findById(c.getUserId()).orElseThrow(() -> ApiException.unauthorized(GENERIC_FAILURE));
        String newDeviceToken = null;
        Instant trustedUntil = null;
        if (rememberDevice) {
            newDeviceToken = randomHex();
            trustedUntil = now.plus(rememberFor);
            trustedDevices.save(new TrustedDevice(user.getUserId(), sha256(newDeviceToken), now, trustedUntil));
        }
        Session session = sessions.save(new Session(randomHex(), user, now, now.plus(sessionLifetime)));
        return new LoginResult(session, newDeviceToken, trustedUntil, false);
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
        challenges.deleteExpired(Instant.now());
    }
}
