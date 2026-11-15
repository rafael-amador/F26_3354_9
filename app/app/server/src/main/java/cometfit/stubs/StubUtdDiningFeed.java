package cometfit.stubs;

import java.util.List;

import org.springframework.stereotype.Component;

import cometfit.services.UtdDiningFeed;

/**
 * *** STUB *** of the external "UTD Dining (menu data)" actor (UC-1, FR-8, §5.3.2 "UTD Dining Menus").
 * Serves a small fixed menu for two locations named after the places FR-8's rationale mentions ("campus stores
 * and dining hall"). Item names and macro values are placeholder demo data, not real menu facts (A-16).
 */
@Component
public class StubUtdDiningFeed implements UtdDiningFeed {

    public static final String DINING_HALL = "Dining Hall";
    public static final String CAMPUS_STORE = "Campus Store";

    private static final List<FeedItem> ITEMS = List.of(
            new FeedItem("DH-001", "Grilled Chicken Plate", DINING_HALL, 520, 45, 40, 18),
            new FeedItem("DH-002", "Veggie Stir Fry", DINING_HALL, 430, 14, 62, 13),
            new FeedItem("DH-003", "Cheeseburger", DINING_HALL, 690, 34, 45, 39),
            new FeedItem("DH-004", "Caesar Salad", DINING_HALL, 360, 10, 18, 27),
            new FeedItem("DH-005", "Oatmeal with Berries", DINING_HALL, 290, 8, 52, 5),
            new FeedItem("CS-001", "Turkey Wrap", CAMPUS_STORE, 450, 28, 44, 16),
            new FeedItem("CS-002", "Greek Yogurt Cup", CAMPUS_STORE, 150, 15, 17, 2),
            new FeedItem("CS-003", "Protein Bar", CAMPUS_STORE, 210, 20, 23, 7),
            new FeedItem("CS-004", "Fruit Cup", CAMPUS_STORE, 90, 1, 23, 0),
            new FeedItem("CS-005", "Bottled Smoothie", CAMPUS_STORE, 260, 6, 55, 2));

    @Override
    public List<String> locations() {
        return List.of(DINING_HALL, CAMPUS_STORE);
    }

    @Override
    public List<FeedItem> menu(String location) {
        return ITEMS.stream().filter(i -> i.location().equalsIgnoreCase(location)).toList();
    }
}
