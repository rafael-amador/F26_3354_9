package cometfit.services;

import java.util.List;

/**
 * Boundary to the external actor "UTD Dining (menu data)" (UC-1 "Maintain Dining Menu &amp; Macros";
 * §5.3.2 External Systems "UTD Dining Menus"; FR-8). The only implementation shipped is the labelled stub
 * {@link cometfit.stubs.StubUtdDiningFeed} (CLAUDE.md rule 4).
 *
 * TD-6: locations carry the building, menu label and source URL; items carry the serving size. Macros are
 * {@code null} when the source did not publish them ("not published"), never invented (TD-6 §3).
 */
public interface UtdDiningFeed {

    /** One dining location (TD-6 §1-2): name, building shown next to it, menu label, source URL. */
    record Location(String name, String building, String menu, String source, boolean macrosPublished) {}

    /** One menu item. proteinG / carbsG / fatG are null when not published (TD-6 §3). */
    record FeedItem(String externalId, String name, String location, String serving, int calories,
                    Double proteinG, Double carbsG, Double fatG) {}

    List<Location> locationDetails();

    default List<String> locations() {
        return locationDetails().stream().map(Location::name).toList();
    }

    List<FeedItem> menu(String location);
}
