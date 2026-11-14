package cometfit.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import cometfit.api.ApiException;
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
 * Registration: A-3.
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
     * totpCode may be left out only when deviceToken is a valid remembered device (TD-1 (3)).
     * rememberDevice = the "Remember this device for 30 days" checkbox.
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password, String totpCode,
                               Boolean rememberDevice, String deviceToken) {}

    /** deviceToken / trustedUntil only when a device was just remembered; the client stores them (TD-1 (3)). */
    public record LoginResponse(String token, Instant expiresAt, UserView user, String deviceToken,
                                Instant trustedUntil, boolean rememberedDevice) {}

    public record UserView(Long userId, String username, String displayName, String role, boolean isPublic,
                           List<String> permissions) {
        public static UserView of(User u) {
            List<String> perms = u instanceof Developer d ? d.getPermissions() : List.of();
            return new UserView(u.getUserId(), u.getUsername(), u.getDisplayName(), u.role(), u.isPublic(), perms);
        }
    }

    /**
     * Returns the TOTP secret once so the student can add it to an authenticator app. The client turns otpauthUri
     * into the "Add to authenticator app" button and the large-screen QR code, and totpSecret into
     * "Copy setup key" (TD-1 (1), (2)).
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest r) {
        AccountService.Registration reg = accounts.registerStudent(r.username(), r.password(), r.email(), r.displayName());
        return Map.of("user", UserView.of(reg.user()), "totpSecret", reg.totpSecret(), "otpauthUri", reg.otpauthUri());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        boolean noCode = r.totpCode() == null || r.totpCode().isBlank();
        boolean noDevice = r.deviceToken() == null || r.deviceToken().isBlank();
        if (noCode && noDevice) {   // checked before the credentials, so it reveals nothing about the password
            throw ApiException.badRequest("Enter the 6-digit code from your authenticator app");
        }
        AuthService.LoginResult res = authService.login(r.username(), r.password(), r.totpCode(),
                Boolean.TRUE.equals(r.rememberDevice()), r.deviceToken());
        Session s = res.session();
        return new LoginResponse(s.getToken(), s.getExpiresAt(), UserView.of(s.getUser()), res.deviceToken(),
                res.trustedUntil(), res.rememberedDevice());
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
