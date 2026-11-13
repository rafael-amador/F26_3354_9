package cometfit.security;

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
 */
@Service
public class AuthService {
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final Duration sessionLifetime;

    public AuthService(UserRepository users, SessionRepository sessions, PasswordHasher hasher,
                       @Value("${cometfit.session-hours}") long sessionHours) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.sessionLifetime = Duration.ofHours(sessionHours);
    }

    /**
     * authenticate(username, password) : Session. Because the NFR makes TOTP mandatory for every user,
     * a Session is only created after {@link #verifyTOTP} also succeeds, so the TOTP code is passed in the
     * same call (A-6). One generic error message is used so usernames cannot be probed.
     */
    @Transactional
    public Session authenticate(String username, String password, String totpCode) {
        User user = users.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .filter(u -> hasher.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid username, password or code"));
        if (!verifyTOTP(user.getUserId(), totpCode)) {
            throw ApiException.unauthorized("Invalid username, password or code");
        }
        Instant now = Instant.now();
        byte[] t = new byte[32];
        RNG.nextBytes(t);
        return sessions.save(new Session(HexFormat.of().formatHex(t), user, now, now.plus(sessionLifetime)));
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
    }
}
