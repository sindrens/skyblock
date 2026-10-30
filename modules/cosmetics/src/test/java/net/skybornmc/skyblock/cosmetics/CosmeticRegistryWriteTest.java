package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF3: titles defined by other modules are active at once and written to the file off the main thread. */
class CosmeticRegistryWriteTest {

    @TempDir
    Path dir;

    private CosmeticRegistry registry() throws IOException {
        Files.writeString(dir.resolve(CosmeticRegistry.FILE), """
                titles:
                  founder:
                    display: "Founder"
                    rarity: legendary
                tags: {}
                """);
        CosmeticRegistry r = new CosmeticRegistry(dir.toFile(), name -> {
        });
        r.load(p -> {
        });
        return r;
    }

    private static Cosmetic title(String id) {
        return new Cosmetic(Cosmetics.Kind.TITLE, id, id, Rarity.UNCOMMON, "test", false, true);
    }

    private YamlConfiguration onDisk() {
        return YamlConfiguration.loadConfiguration(new File(dir.toFile(), CosmeticRegistry.FILE));
    }

    @Test
    void activeAtOnceWrittenLaterInOneWrite() throws IOException {
        CosmeticRegistry r = registry();
        Queue<Runnable> background = new ArrayDeque<>();
        List<IOException> failures = new ArrayList<>();
        r.putLater(title("firstflight"), background::add, failures::add);
        r.putLater(title("clean_streak"), background::add, failures::add);
        assertTrue(r.ids(Cosmetics.Kind.TITLE).contains("firstflight"), "usable right away");
        assertTrue(r.ids(Cosmetics.Kind.TITLE).contains("clean_streak"));
        assertFalse(onDisk().isSet("titles.firstflight"), "nothing written on the calling (main) thread");
        assertEquals(1, background.size(), "a burst of definitions shares one write");
        background.poll().run();
        YamlConfiguration yaml = onDisk();
        assertTrue(yaml.isSet("titles.firstflight"));
        assertTrue(yaml.isSet("titles.clean_streak"));
        assertTrue(yaml.isSet("titles.founder"), "existing entries are kept");
        assertTrue(failures.isEmpty());

        r.putLater(title("third"), background::add, failures::add);
        assertEquals(1, background.size(), "after a write, a new definition queues a new write");
    }

    @Test
    void aReloadBeforeTheWriteKeepsTheNewTitle() throws IOException {
        CosmeticRegistry r = registry();
        Queue<Runnable> background = new ArrayDeque<>();
        r.putLater(title("firstflight"), background::add, e -> {
        });
        r.load(p -> {
        });
        assertTrue(r.ids(Cosmetics.Kind.TITLE).contains("firstflight"));
        assertTrue(onDisk().isSet("titles.firstflight"));
        background.poll().run(); // the queued write finds nothing left to do
        assertTrue(onDisk().isSet("titles.firstflight"));
    }
}
