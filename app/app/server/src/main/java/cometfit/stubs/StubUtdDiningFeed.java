package cometfit.stubs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cometfit.services.UtdDiningFeed;

/**
 * *** STUB *** of the external "UTD Dining (menu data)" actor (UC-1, FR-8, §5.3.2 "UTD Dining Menus").
 *
 * TD-6 (supersedes A-16): serves the UTD Dining locations and items of
 * {@code spec/06-utd-dining/utd-dining-menus-2026-11-16.json} (copied unchanged to the classpath resource
 * {@value #RESOURCE}; {@code StubUtdDiningFeedTest} checks the copy matches the spec file). Names, serving sizes and
 * nutrition numbers are used exactly as given; a JSON {@code null} macro stays {@code null} ("not published").
 * No live calls to Dine On Campus are made (TD-6 §4): the same menu is served every day (A-52).
 */
@Component
public class StubUtdDiningFeed implements UtdDiningFeed {

    public static final String RESOURCE = "utd-dining/utd-dining-menus-2026-11-16.json";

    private final List<Location> locations;
    private final List<FeedItem> items;

    public StubUtdDiningFeed() {
        this(RESOURCE);
    }

    StubUtdDiningFeed(String resource) {
        JsonNode root;
        try (InputStream in = StubUtdDiningFeed.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("UTD Dining stub data missing: " + resource);
            root = new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Location> locs = new ArrayList<>();
        List<FeedItem> all = new ArrayList<>();
        for (JsonNode l : root.path("locations")) {
            String name = l.path("name").asText();
            locs.add(new Location(name, text(l, "building"), text(l, "menu"), text(l, "source"),
                    l.path("macros_published").asBoolean(false)));
            for (JsonNode i : l.path("items")) {
                String itemName = i.path("name").asText();
                all.add(new FeedItem(externalId(name, itemName), itemName, name, text(i, "serving"),
                        i.path("calories").asInt(), macro(i, "protein_g"), macro(i, "carbs_g"), macro(i, "fat_g")));
            }
        }
        this.locations = Collections.unmodifiableList(locs);
        this.items = Collections.unmodifiableList(all);
    }

    @Override
    public List<Location> locationDetails() {
        return locations;
    }

    @Override
    public List<FeedItem> menu(String location) {
        return items.stream().filter(i -> i.location().equalsIgnoreCase(location)).toList();
    }

    /** JSON null or missing = not published (TD-6 §3). */
    private static Double macro(JsonNode item, String field) {
        JsonNode v = item.get(field);
        return v == null || v.isNull() ? null : v.asDouble();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Stable feed id "UTD-<location>-<item>" (slugs) so refreshed menus update rows instead of duplicating. */
    static String externalId(String location, String item) {
        String id = "UTD-" + slug(location) + "-" + slug(item);
        return id.length() > 160 ? id.substring(0, 160) : id;
    }

    private static String slug(String s) {
        String ascii = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
