package cometfit.security;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.api.ApiException;
import cometfit.domain.ConsentSettings;
import cometfit.domain.ConsentSettingsRepository;
import cometfit.domain.PointAccount;
import cometfit.domain.PointAccountRepository;

/**
 * Creates accounts. The spec has "Log In (Password + TOTP)" in every use case but no sign-up use case;
 * self-registration for students is A-3. Each new Student gets its 1 ConsentSettings and 1 PointAccount
 * (§5.2 multiplicities "configures 1", "owns 1"). Developers are created by seed/config only (A-14).
 */
@Service
public class AccountService {
    public static final String ISSUER = "CometFit";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final ConsentSettingsRepository consents;
    private final PointAccountRepository points;

    public AccountService(UserRepository users, PasswordHasher hasher,
                          ConsentSettingsRepository consents, PointAccountRepository points) {
        this.users = users;
        this.hasher = hasher;
        this.consents = consents;
        this.points = points;
    }

    public record Registration(User user, String totpSecret, String otpauthUri) {}

    @Transactional
    public Registration registerStudent(String username, String password, String email, String displayName) {
        return registerStudent(username, password, email, displayName, Totp.newSecret());
    }

    @Transactional
    public Registration registerStudent(String username, String password, String email, String displayName,
                                        String totpSecret) {
        validate(username, password, email);
        Student s = new Student();
        fill(s, username, password, email, displayName, totpSecret);
        s = users.save(s);
        consents.save(new ConsentSettings(s.getUserId()));
        points.save(new PointAccount(s.getUserId()));
        return new Registration(s, totpSecret, Totp.otpauthUri(ISSUER, s.getUsername(), totpSecret));
    }

    @Transactional
    public Registration createDeveloper(String username, String password, String email, String displayName,
                                        String adminRole, List<String> permissions, String totpSecret) {
        validate(username, password, email);
        Developer d = new Developer();
        fill(d, username, password, email, displayName, totpSecret);
        d.setAdminRole(adminRole);
        d.setPermissions(permissions);
        d = users.save(d);
        return new Registration(d, totpSecret, Totp.otpauthUri(ISSUER, d.getUsername(), totpSecret));
    }

    private void fill(User u, String username, String password, String email, String displayName, String secret) {
        u.setUsername(username.trim());
        u.setPasswordHash(hasher.hash(password));
        u.setEmail(email.trim());
        u.setDisplayName(displayName == null || displayName.isBlank() ? username.trim() : displayName.trim());
        u.setTotpSecret(secret);
        u.setPublic(true);
    }

    /** Credential rules: A-4. */
    private void validate(String username, String password, String email) {
        if (username == null || !username.trim().matches("[A-Za-z0-9_.]{3,30}"))
            throw ApiException.badRequest("Username must be 3-30 letters, digits, '_' or '.'");
        if (password == null || password.length() < 8)
            throw ApiException.badRequest("Password must be at least 8 characters");
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw ApiException.badRequest("A valid email is required");
        if (users.existsByUsernameIgnoreCase(username.trim()))
            throw ApiException.conflict("That username is taken");
    }
}
