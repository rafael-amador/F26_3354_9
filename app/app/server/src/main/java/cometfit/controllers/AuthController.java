package cometfit.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import cometfit.security.AccountService;
import cometfit.security.AuthService;
import cometfit.security.Developer;
import cometfit.security.Session;
import cometfit.security.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Log In (Password + TOTP) — included by every use case in Fig. 4.3 (UC-1..UC-4) and the
 * "login (password + TOTP)" message of SD-4. NFR Security (username + password + TOTP MFA).
 * Registration: A-3. TD-5: POST /login (password) then POST /verify (6-digit code) unless the device is remembered.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {
    private final AccountService accounts;

    public AuthController(AuthService authService, AccountService accounts) {
        super(authService);
        this.accounts = accounts;
    }

    public record RegisterRequest(@NotBlank String username, @NotBlank String password,
                                  @NotBlank String email, String displayName) {}

    /**
     * TD-5 (1): step 1 is username + password only. deviceToken = a device remembered under TD-1 (3); no code field.
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password, String deviceToken) {}

    /**
     * TD-5 (2): step 2 "Verify it's you" (or TD-5 (3) setup). rememberDevice = the
     * "Remember this device for 30 days" checkbox, which is on the verify screen.
     */
    public record VerifyRequest(@NotBlank String challengeId, String totpCode, Boolean rememberDevice) {}

    /**
     * Either a signed-in session (token != null) or, when {@code verificationRequired}, a challenge for step 2 with no
     * token. deviceToken / trustedUntil only when a device was just remembered; the client stores them (TD-1 (3)).
     */
    public record LoginResponse(boolean verificationRequired, String challengeId, Instant challengeExpiresAt,
                                String token, Instant expiresAt, UserView user, String deviceToken,
                                Instant trustedUntil, boolean rememberedDevice) {
        static LoginResponse signedIn(AuthService.LoginResult res) {
            Session s = res.session();
            return new LoginResponse(false, null, null, s.getToken(), s.getExpiresAt(), UserView.of(s.getUser()),
                    res.deviceToken(), res.trustedUntil(), res.rememberedDevice());
        }
        static LoginResponse challenge(AuthService.Challenge c) {
            return new LoginResponse(true, c.challengeId(), c.expiresAt(), null, null, null, null, null, false);
        }
    }

    public record UserView(Long userId, String username, String displayName, String role, boolean isPublic,
                           List<String> permissions) {
        public static UserView of(User u) {
            List<String> perms = u instanceof Developer d ? d.getPermissions() : List.of();
            return new UserView(u.getUserId(), u.getUsername(), u.getDisplayName(), u.role(), u.isPublic(), perms);
        }
    }

    /**
     * Returns the TOTP secret once so the student can add it to Duo Mobile / any authenticator app. The client shows
     * otpauthUri as a QR code on every screen size and as the "Add to authenticator app" button, and totpSecret as
     * "Copy setup key" (TD-5 (3), TD-1 (1)). setupChallengeId lets the first 6-digit code finish setup via /verify
     * and sign in (TD-5 (3)). No session is returned here.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest r) {
        AccountService.Registration reg = accounts.registerStudent(r.username(), r.password(), r.email(), r.displayName());
        AuthService.Challenge setup = authService.startSetup(reg.user().getUserId());
        return Map.of("user", UserView.of(reg.user()), "totpSecret", reg.totpSecret(), "otpauthUri", reg.otpauthUri(),
                "setupChallengeId", setup.challengeId(), "setupExpiresAt", setup.expiresAt());
    }

    /** TD-5 (1), (2): password step. Remembered device -> session; otherwise a challenge and no session. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        AuthService.PasswordStep step = authService.authenticate(r.username(), r.password(), r.deviceToken());
        return step.result() != null ? LoginResponse.signedIn(step.result()) : LoginResponse.challenge(step.challenge());
    }

    /** TD-5 (2) "Verify it's you" / TD-5 (3) setup: the 6-digit code completes the challenge and creates the session. */
    @PostMapping("/verify")
    public LoginResponse verify(@Valid @RequestBody VerifyRequest r) {
        return LoginResponse.signedIn(authService.verifyChallenge(r.challengeId(), r.totpCode(),
                Boolean.TRUE.equals(r.rememberDevice())));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String token) {
        authService.logout(stripBearer(token));
    }

    @GetMapping("/me")
    public UserView me(@RequestHeader("Authorization") String token) {
        return UserView.of(requireSession(token));
    }
}
