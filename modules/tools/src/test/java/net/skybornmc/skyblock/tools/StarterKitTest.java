package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Starter kit: 24 h re-claim cooldown, only missing pieces. */
class StarterKitTest {

    private static final long DAY = Duration.ofHours(24).toMillis();
    private static final List<ToolKind> KIT = List.of(ToolKind.PICKAXE, ToolKind.AXE, ToolKind.HOE, ToolKind.SWORD, ToolKind.ROD);

    @Test
    void cooldown() {
        assertEquals(0, StarterKit.remainingMillis(null, 1_000, DAY), "never claimed");
        assertEquals(DAY, StarterKit.remainingMillis(1_000L, 1_000, DAY), "just claimed");
        assertEquals(DAY - 5_000, StarterKit.remainingMillis(1_000L, 6_000, DAY));
        assertEquals(0, StarterKit.remainingMillis(1_000L, 1_000 + DAY, DAY), "exactly 24 h later");
        assertEquals(0, StarterKit.remainingMillis(1_000L, 1_000 + 2 * DAY, DAY));
        assertEquals(0, StarterKit.remainingMillis(1_000L, 2_000, 0), "no cooldown configured");
    }

    @Test
    void onlyMissingPieces() {
        assertEquals(KIT, StarterKit.missing(KIT, Set.of()));
        assertEquals(List.of(ToolKind.AXE, ToolKind.ROD), StarterKit.missing(KIT, Set.of(ToolKind.PICKAXE, ToolKind.HOE, ToolKind.SWORD)));
        assertEquals(List.of(), StarterKit.missing(KIT, Set.of(ToolKind.values())));
    }
}
