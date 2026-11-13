package cometfit.stubs;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * *** STUB *** In-memory switch that makes a named server component report "down" to the health probes.
 * Exists only so UC-4's "Raise Downtime Alert" / SD-4 "alt [service down] send downtime alert" can be shown in a
 * local demo without actually breaking a server. Toggled by a developer from the admin dashboard.
 */
@Component
public class OutageSimulator {
    private final Set<String> down = ConcurrentHashMap.newKeySet();

    public boolean isDown(String service) { return down.contains(service); }

    public void set(String service, boolean isDown) {
        if (isDown) down.add(service); else down.remove(service);
    }

    public Set<String> current() { return Set.copyOf(down); }
}
