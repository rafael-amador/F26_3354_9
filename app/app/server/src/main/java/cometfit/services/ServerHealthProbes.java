package cometfit.services;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import cometfit.domain.AppModuleRepository;
import cometfit.security.UserRepository;
import cometfit.stubs.OutageSimulator;

/**
 * The tracked server components (FR-1; §5.3.2 boxes "API Gateway", "Auth Gateway", "Social Services",
 * "Activity Service", "Nutrition Service", "Administrator Service", "Encrypted Database"). Each probe exercises the
 * component's own dependency. {@link OutageSimulator} (a labelled stub) can force a probe to fail so the
 * "Raise Downtime Alert" path of UC-4 can be demonstrated on a laptop.
 */
@Configuration
public class ServerHealthProbes {

    public static final List<String> SERVICES = List.of(
            "api-gateway", "auth-gateway", "nutrition-service", "activity-service", "social-service",
            "admin-service", "database");

    @Bean
    List<HealthProbe> healthProbes(JdbcTemplate jdbc, UserRepository users, AppModuleRepository modules,
                                   UtdDiningFeed diningFeed, OutageSimulator outages) {
        return List.of(
                probe("api-gateway", outages, () -> { }),
                probe("auth-gateway", outages, users::count),
                probe("nutrition-service", outages, () -> {
                    if (diningFeed.locations().isEmpty()) throw new IllegalStateException("Dining feed empty");
                }),
                probe("activity-service", outages, () -> jdbc.queryForObject("select count(*) from urec_card_readers", Long.class)),
                probe("social-service", outages, () -> jdbc.queryForObject("select count(*) from friendships", Long.class)),
                probe("admin-service", outages, modules::count),
                probe("database", outages, () -> jdbc.queryForObject("select 1", Integer.class)));
    }

    @FunctionalInterface
    interface Check { void run() throws Exception; }

    private static HealthProbe probe(String name, OutageSimulator outages, Check check) {
        return new HealthProbe() {
            @Override public String serviceName() { return name; }
            @Override public void check() throws Exception {
                if (outages.isDown(name)) throw new IllegalStateException("Simulated outage");
                check.run();
            }
        };
    }
}
