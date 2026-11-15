package cometfit.controllers;

import cometfit.api.ApiException;
import cometfit.security.AuthService;
import cometfit.security.Developer;
import cometfit.security.Student;
import cometfit.security.User;

/**
 * BaseController — Deliverable 1 §5.2 "Controllers" package (abstract):
 * #authService : AuthService, #requireSession(token) : User.
 * Every controller extends it, so every request goes through the auth gateway first (§5.3.2 "routing to
 * proper call after authorization"; SD-1 "authenticatedRequest(session) / session valid").
 */
public abstract class BaseController {
    protected final AuthService authService;

    protected BaseController(AuthService authService) {
        this.authService = authService;
    }

    /** requireSession(token) : User. Accepts a raw token or an "Authorization: Bearer ..." header value. */
    protected User requireSession(String token) {
        return authService.userForToken(stripBearer(token));
    }

    /** requireSession + authorize(userId, permission) against the backend permissions list (NFR Security). */
    protected User requirePermission(String token, String permission) {
        User u = requireSession(token);
        if (!authService.authorize(u.getUserId(), permission)) {
            throw ApiException.forbidden("You do not have permission '" + permission + "'");
        }
        return u;
    }

    protected Student requireStudent(String token, String permission) {
        User u = requirePermission(token, permission);
        if (u instanceof Student s) return s;
        throw ApiException.forbidden("Only student accounts can use this feature");
    }

    protected Developer requireDeveloper(String token, String permission) {
        User u = requirePermission(token, permission);
        if (u instanceof Developer d) return d;
        throw ApiException.forbidden("Developer account required");
    }

    public static String stripBearer(String header) {
        if (header == null) return null;
        String h = header.trim();
        return h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : h;
    }
}
