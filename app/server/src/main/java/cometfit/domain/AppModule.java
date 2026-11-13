package cometfit.domain;

import jakarta.persistence.*;

/**
 * AppModule — §5.2 Domain Data: moduleId, name, enabled. Developer 1 "administers" 0..* AppModule;
 * FeatureFlagService "manages" AppModule. FR-5 / UC-4 "Disable / Re-enable App Module". Module list: A-11.
 */
@Entity
@Table(name = "app_modules")
public class AppModule {
    public static final String NUTRITION = "NUTRITION";
    public static final String ACTIVITY = "ACTIVITY";
    public static final String SOCIAL = "SOCIAL";

    @Id @Column(name = "module_id")
    private String moduleId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean enabled;

    protected AppModule() {}

    public AppModule(String moduleId, String name, boolean enabled) {
        this.moduleId = moduleId;
        this.name = name;
        this.enabled = enabled;
    }

    public String getModuleId() { return moduleId; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
