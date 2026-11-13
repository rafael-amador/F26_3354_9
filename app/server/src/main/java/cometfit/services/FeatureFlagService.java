package cometfit.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cometfit.api.ApiException;
import cometfit.domain.AppModule;
import cometfit.domain.AppModuleRepository;

/**
 * FeatureFlagService — Deliverable 1 §5.2 "Services": isEnabled(moduleId) : boolean,
 * setEnabled(moduleId, enabled) : void; "manages" AppModule.
 * FR-5 / UC-4; SD-4 "setEnabled(moduleId, false) / save flag state / isEnabled(moduleId) -> false".
 */
@Service
public class FeatureFlagService {
    private final AppModuleRepository modules;

    public FeatureFlagService(AppModuleRepository modules) {
        this.modules = modules;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String moduleId) {
        return modules.findById(moduleId).map(AppModule::isEnabled).orElse(true);
    }

    @Transactional
    public AppModule setEnabled(String moduleId, boolean enabled) {
        AppModule m = modules.findById(moduleId)
                .orElseThrow(() -> ApiException.notFound("Unknown module " + moduleId));
        m.setEnabled(enabled);
        return modules.save(m);
    }

    @Transactional(readOnly = true)
    public List<AppModule> all() {
        return modules.findAllByOrderByModuleId();
    }
}
