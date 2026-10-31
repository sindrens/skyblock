package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClassPreloaderTest {

    @Test
    void onlyOwnClassesBelowThePackage() {
        List<String> entries = List.of(
                "net/skybornmc/skyblock/",
                "net/skybornmc/skyblock/SkyblockPlugin.class",
                "net/skybornmc/skyblock/bank/Bank$Inner.class",
                "net/skybornmc/skyblock/package-info.class",
                "module-info.class",
                "META-INF/versions/21/net/skybornmc/skyblock/X.class",
                "com/zaxxer/hikari/HikariConfig.class",
                "messages/core.yml",
                "plugin.yml");
        assertEquals(List.of("net.skybornmc.skyblock.SkyblockPlugin", "net.skybornmc.skyblock.bank.Bank$Inner"),
                ClassPreloader.classNames(entries, "net/skybornmc/skyblock/"));
    }

    @Test
    void loadsWithoutInitializingAndSkipsMissing() {
        ClassPreloader.Result r = ClassPreloader.load(List.of(Tracked.class.getName(), "net.skybornmc.skyblock.DoesNotExist"),
                ClassPreloaderTest.class.getClassLoader());
        assertEquals(1, r.loaded());
        assertEquals(1, r.skipped());
        assertFalse(Marker.set, "the static initializer must not run during a preload");
    }

    static final class Tracked {
        static {
            Marker.set = true;
        }
    }

    static final class Marker {
        static boolean set;
    }
}
