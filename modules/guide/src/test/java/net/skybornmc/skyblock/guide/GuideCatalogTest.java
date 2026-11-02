package net.skybornmc.skyblock.guide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kindle's quests and finds (owner 2 Nov 2026). */
class GuideCatalogTest {

    private static final Set<String> CRATES = Set.of("ember", "cinder", "blaze", "inferno", "phoenix");

    private static GuideCatalog bundled(List<String> problems) throws Exception {
        try (var in = GuideCatalogTest.class.getClassLoader().getResourceAsStream(GuideCatalog.FILE)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return GuideCatalog.load(yaml, id -> id.matches("[a-z_]+"), CRATES::contains, problems::add);
        }
    }

    @Test
    void bundledFileLoadsCleanAndClimbs() throws Exception {
        List<String> problems = new ArrayList<>();
        GuideCatalog c = bundled(problems);
        assertEquals(List.of(), problems);
        List<GuideCatalog.Entry> story = c.all(GuideCatalog.Kind.STORY);
        assertTrue(story.size() >= 15);
        for (int i = 1; i < story.size(); i++) {
            assertTrue(story.get(i).level() >= story.get(i - 1).level(), "story levels never go down: " + story.get(i).id());
        }
        assertEquals(0, story.getFirst().level(), "a new island can start at once");
        assertTrue(c.all(GuideCatalog.Kind.ERRAND).stream().filter(e -> e.level() == 0).count() >= 2, "errands from the start");
        assertTrue(c.all(GuideCatalog.Kind.TRADE).stream().allMatch(e -> e.rewards().stream()
                .noneMatch(r -> r.type() == GuideCatalog.RewardType.MONEY)), "finds never pay money");
    }

    @Test
    void storyIsTheFirstNotDone() throws Exception {
        List<GuideCatalog.Entry> story = bundled(new ArrayList<>()).all(GuideCatalog.Kind.STORY);
        assertEquals(story.getFirst(), GuideCatalog.currentStory(story, Set.of()));
        assertEquals(story.get(1), GuideCatalog.currentStory(story, Set.of(story.getFirst().id())));
        Set<String> all = new java.util.HashSet<>();
        story.forEach(e -> all.add(e.id()));
        assertNull(GuideCatalog.currentStory(story, all));
    }

    @Test
    void picksAreStableInARotationAndRespectTheLevel() throws Exception {
        List<GuideCatalog.Entry> errands = bundled(new ArrayList<>()).all(GuideCatalog.Kind.ERRAND);
        UUID p = UUID.randomUUID();
        long seed = GuideCatalog.seed(p, 100, GuideCatalog.Kind.ERRAND);
        List<GuideCatalog.Entry> a = GuideCatalog.picks(errands, 0, 2, seed);
        assertEquals(a, GuideCatalog.picks(errands, 0, 2, seed));
        assertEquals(2, a.size());
        assertNotEquals(a.get(0).id(), a.get(1).id());
        assertTrue(a.stream().allMatch(e -> e.level() == 0));
        assertEquals(errands.size(), GuideCatalog.picks(errands, 1000, 999, seed).size(), "never more than the pool");
    }

    @Test
    void rotationsAreThreeHoursLong() {
        assertEquals(GuideCatalog.period(0, 3), GuideCatalog.period(3 * 3_600_000L - 1, 3));
        assertEquals(GuideCatalog.period(0, 3) + 1, GuideCatalog.period(3 * 3_600_000L, 3));
    }

    @Test
    void rewardLines() {
        assertEquals(new GuideCatalog.Reward(GuideCatalog.RewardType.MONEY, "", 500), GuideCatalog.reward("money 500", x -> true, x -> true).orElseThrow());
        assertEquals(new GuideCatalog.Reward(GuideCatalog.RewardType.KEY, "blaze", 1), GuideCatalog.reward("key blaze 1", x -> true, CRATES::contains).orElseThrow());
        assertTrue(GuideCatalog.reward("key nope 1", x -> true, CRATES::contains).isEmpty());
        assertTrue(GuideCatalog.reward("shards 5", x -> true, x -> true).isEmpty(), "no shards (ECON-SHARDS)");
        assertTrue(GuideCatalog.reward("money -5", x -> true, x -> true).isEmpty());
        assertTrue(GuideCatalog.reward("item diamond 9999", x -> true, x -> true).isEmpty());
    }
}
