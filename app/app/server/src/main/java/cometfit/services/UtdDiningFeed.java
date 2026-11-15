package cometfit.services;

import java.util.List;

/**
 * Boundary to the external actor "UTD Dining (menu data)" (UC-1 "Maintain Dining Menu &amp; Macros";
 * §5.3.2 External Systems "UTD Dining Menus"; FR-8). The only implementation shipped is the labelled stub
 * {@link cometfit.stubs.StubUtdDiningFeed} (CLAUDE.md rule 4).
 */
public interface UtdDiningFeed {

    record FeedItem(String externalId, String name, String location, int calories,
                    double proteinG, double carbsG, double fatG) {}

    List<String> locations();

    List<FeedItem> menu(String location);
}
