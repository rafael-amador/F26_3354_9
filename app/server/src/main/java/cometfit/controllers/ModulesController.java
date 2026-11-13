package cometfit.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cometfit.domain.AppModule;
import cometfit.security.AuthService;
import cometfit.services.FeatureFlagService;

/**
 * Lets the student's Mobile App ask FeatureFlagService isEnabled(moduleId) — SD-4 "Mobile App (Student) ->
 * isEnabled(moduleId) -> false -> show maintenance notice, other features stay available". UC-4 "Show Maintenance
 * Notice" (actor Student). FR-5.
 */
@RestController
@RequestMapping("/api/modules")
public class ModulesController extends BaseController {
    private final FeatureFlagService featureFlagService;

    public ModulesController(AuthService authService, FeatureFlagService featureFlagService) {
        super(authService);
        this.featureFlagService = featureFlagService;
    }

    public record ModuleState(String moduleId, String name, boolean enabled) {}

    @GetMapping
    public List<ModuleState> all(@RequestHeader("Authorization") String token) {
        requireSession(token);
        return featureFlagService.all().stream()
                .map(m -> new ModuleState(m.getModuleId(), m.getName(), m.isEnabled())).toList();
    }

    /** isEnabled(moduleId) : boolean. */
    @GetMapping("/{moduleId}")
    public boolean isEnabled(@RequestHeader("Authorization") String token, @PathVariable String moduleId) {
        requireSession(token);
        return featureFlagService.isEnabled(moduleId);
    }
}
