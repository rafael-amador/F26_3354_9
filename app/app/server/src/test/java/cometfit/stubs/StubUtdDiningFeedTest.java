package cometfit.stubs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cometfit.services.UtdDiningFeed.FeedItem;

/** TD-6: the stub UTD Dining feed serves exactly spec/06-utd-dining/utd-dining-menus-2026-11-16.json. */
class StubUtdDiningFeedTest {

    /** The classpath copy must match the team's spec file (CLAUDE.md rule 5: spec/ itself is never edited). */
    @Test
    void resourceIsTheSameDataAsTheSpecFile() throws Exception {
        Path spec = Path.of("..", "..", "spec", "06-utd-dining", "utd-dining-menus-2026-11-16.json");
        Assumptions.assumeTrue(Files.exists(spec), "spec/ not reachable from here (e.g. Docker build)");
        ObjectMapper om = new ObjectMapper();
        JsonNode fromSpec = om.readTree(spec.toFile());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(StubUtdDiningFeed.RESOURCE)) {
            assertThat(in).isNotNull();
            assertThat(om.readTree(in)).isEqualTo(fromSpec);
        }
    }

    @Test
    void sevenLocations54ItemsUniqueIdsAndNullsKept() {
        StubUtdDiningFeed feed = new StubUtdDiningFeed();
        assertThat(feed.locationDetails()).hasSize(7);
        int n = 0;
        Set<String> ids = new HashSet<>();
        for (String loc : feed.locations()) {
            for (FeedItem i : feed.menu(loc)) {
                n++;
                assertThat(ids.add(i.externalId())).as(i.externalId()).isTrue();
                assertThat(i.externalId().length()).isLessThanOrEqualTo(160);
            }
        }
        assertThat(n).isEqualTo(54);
        FeedItem polenta = feed.menu("Dining Hall West").stream().filter(i -> i.name().equals("Creamy Polenta"))
                .findFirst().orElseThrow();
        assertThat(polenta.calories()).isEqualTo(110);
        assertThat(polenta.serving()).isEqualTo("1/2 cup");
        assertThat(polenta.proteinG()).isNull();
        assertThat(polenta.carbsG()).isNull();
        assertThat(polenta.fatG()).isNull();
    }
}
