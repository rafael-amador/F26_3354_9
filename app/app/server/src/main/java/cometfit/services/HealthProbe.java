package cometfit.services;

/**
 * One server component whose uptime is tracked (FR-1). The component names follow the server boxes of the
 * Deliverable 1 §5.3.2 architecture diagram (A-25). {@link #check()} returns normally when healthy and throws when
 * the component is down.
 */
public interface HealthProbe {
    String serviceName();

    void check() throws Exception;
}
