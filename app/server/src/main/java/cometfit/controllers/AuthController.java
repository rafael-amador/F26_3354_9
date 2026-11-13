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

    public record LoginRequest(@NotBlank String username, @NotBlank String password, @NotBlank String totpCode) {}

    public record LoginResponse(String token, Instant expiresAt, UserView user) {}

    public record UserView(Long userId, String username, String displayName, String role, boolean isPublic,
                           List<String> permissions) {
        public static UserView of(User u) {
            List<String> perms = u instanceof Developer d ? d.getPermissions() : List.of();
            return new UserView(u.getUserId(), u.getUsername(), u.getDisplayName(), u.role(), u.isPublic(), perms);
        }
    }

    /** Returns the TOTP secret once so the student can add it to an authenticator app. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest r) {
        AccountService.Registration reg = accounts.registerStudent(r.username(), r.password(), r.email(), r.displayName());
        return Map.of("user", UserView.of(reg.user()), "totpSecret", reg.totpSecret(), "otpauthUri", reg.otpauthUri());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        Session s = authService.authenticate(r.username(), r.password(), r.totpCode());
        return new LoginResponse(s.getToken(), s.getExpiresAt(), UserView.of(s.getUser()));
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
