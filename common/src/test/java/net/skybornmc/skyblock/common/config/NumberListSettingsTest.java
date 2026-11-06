package net.skybornmc.skyblock.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3 Nov 2026: number lists were loaded as text, so reading one as a number crashed the island tool menu. */
class NumberListSettingsTest {

    static final class Lists extends ModuleConfig {
        @Setting(desc = "Ints") List<Integer> ints = List.of(1, 2);
        @Setting(desc = "Longs") List<Long> longs = List.of(10L);
        @Setting(desc = "Doubles") List<Double> doubles = List.of(0.5);
        @Setting(desc = "Lines") List<String> lines = List.of("a");

        @Override public String fileName() { return "lists.yml"; }
    }

    private final Logger log = Logger.getLogger("test");

    @Test
    void numberListsLoadAsNumbers(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("lists.yml"), "ints: [5, '150', 500]\nlongs: [7]\ndoubles: [1, 2.5]\nlines: [1, b]\n");
        Lists c = new Lists();
        assertTrue(new ConfigManager(dir.toFile(), log).load(c).isEmpty());
        assertEquals(List.of(5, 150, 500), c.ints);
        int sum = 0;
        for (int i : c.ints) { // the unboxing that used to throw ClassCastException
            sum += i;
        }
        assertEquals(655, sum);
        assertEquals(List.of(7L), c.longs);
        assertEquals(List.of(1.0, 2.5), c.doubles);
        assertEquals(List.of("1", "b"), c.lines);
    }

    @Test
    void aBadEntryKeepsTheDefault(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("lists.yml"), "ints: [5, lots]\n");
        Lists c = new Lists();
        List<String> problems = new ConfigManager(dir.toFile(), log).load(c);
        assertFalse(problems.isEmpty());
        assertEquals(List.of(1, 2), c.ints);
    }
}
