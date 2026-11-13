package cometfit.security;

import java.util.List;
import java.util.Set;

/**
 * Backend permissions list. NFR Security: "Interactions with the application must be authorized via a
 * backend permissions list". UC-4 "Authorize Admin (Permissions + TOTP)". Names are A-13.
 */
public final class Permissions {
    private Permissions() {}

    // Student permissions (granted to every Student)
    public static final String NUTRITION = "nutrition.use";   // UC-1
    public static final String ACTIVITY = "activity.use";     // UC-2
    public static final String SOCIAL = "social.use";         // UC-3
    public static final String PROFILE = "profile.use";       // FR-3

    // Developer permissions (stored per Developer in developer_permissions)
    public static final String ADMIN_UPTIME = "admin.uptime.view";       // FR-1
    public static final String ADMIN_MODULES = "admin.modules.manage";   // FR-5

    public static final Set<String> STUDENT_DEFAULT = Set.of(NUTRITION, ACTIVITY, SOCIAL, PROFILE);
    public static final List<String> DEVELOPER_DEFAULT = List.of(ADMIN_UPTIME, ADMIN_MODULES, PROFILE);
}
