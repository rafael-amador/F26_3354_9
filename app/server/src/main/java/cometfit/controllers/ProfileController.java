package cometfit.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import cometfit.api.ApiException;
import cometfit.security.AuthService;
import cometfit.security.PasswordHasher;
import cometfit.security.Permissions;
import cometfit.security.Student;
import cometfit.security.User;
import cometfit.security.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The signed-in user's own profile and goals.
 * FR-3 "User can view their own profile" (RTM §4.4 "open your profile"); UC-3 "Edit Public Profile";
 * User.updateProfile() / changePassword() (§5.2); Student.setGoals(cal, water, steps) (§5.2) with the
 * NFR Safety warning "whenever an unsafe goal is set". Bottle size: A-8.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController extends BaseController {
    private final UserRepository users;
    private final PasswordHasher hasher;

    public ProfileController(AuthService authService, UserRepository users, PasswordHasher hasher) {
        super(authService);
        this.users = users;
        this.hasher = hasher;
    }

    public record ProfileView(Long userId, String username, String displayName, String email, String role,
                              boolean isPublic, Integer calorieGoal, Integer waterGoalMl, Integer stepGoal,
                              Integer bottleSizeMl) {
        static ProfileView of(User u) {
            if (u instanceof Student s) {
                return new ProfileView(u.getUserId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.role(),
                        u.isPublic(), s.getCalorieGoal(), s.getWaterGoalMl(), s.getStepGoal(), s.getBottleSizeMl());
            }
            return new ProfileView(u.getUserId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.role(),
                    u.isPublic(), null, null, null, null);
        }
    }

    public record UpdateProfileRequest(@Size(min = 1, max = 80) String displayName, Boolean isPublic) {}

    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank @Size(min = 8) String newPassword) {}

    public record GoalsRequest(@Min(0) @Max(10000) int calorieGoal, @Min(0) @Max(10000) int waterGoalMl,
                               @Min(0) @Max(100000) int stepGoal, @Min(50) @Max(5000) Integer bottleSizeMl) {}

    public record GoalsResponse(ProfileView profile, List<String> warnings) {}

    @GetMapping
    public ProfileView get(@RequestHeader("Authorization") String token) {
        return ProfileView.of(requirePermission(token, Permissions.PROFILE));
    }

    @PutMapping
    public ProfileView update(@RequestHeader("Authorization") String token, @Valid @RequestBody UpdateProfileRequest r) {
        User u = requirePermission(token, Permissions.PROFILE);
        u.updateProfile(r.displayName(), r.isPublic());
        return ProfileView.of(users.save(u));
    }

    @PutMapping("/password")
    public void changePassword(@RequestHeader("Authorization") String token, @Valid @RequestBody ChangePasswordRequest r) {
        User u = requirePermission(token, Permissions.PROFILE);
        if (!u.changePassword(r.oldPassword(), r.newPassword(), hasher)) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        users.save(u);
        authService.forgetTrustedDevices(u.getUserId());   // new password -> code needed again everywhere (A-39)
    }

    /** Student.setGoals — saves and returns safety warnings (NFR Safety). */
    @PutMapping("/goals")
    public GoalsResponse setGoals(@RequestHeader("Authorization") String token, @Valid @RequestBody GoalsRequest r) {
        Student s = requireStudent(token, Permissions.PROFILE);
        List<String> warnings = s.setGoals(r.calorieGoal(), r.waterGoalMl(), r.stepGoal());
        if (r.bottleSizeMl() != null) s.setBottleSizeMl(r.bottleSizeMl());
        return new GoalsResponse(ProfileView.of(users.save(s)), warnings);
    }
}
