package net.skybornmc.skyblock.chat;

import net.kyori.adventure.text.format.NamedTextColor;
import net.skybornmc.skyblock.common.pack.BadgeGradients;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankBadgeKeyTest {

    @Test
    void groupNameAndGradientChangeTheFingerprint() {
        Display.RankView base = new Display.RankView("", NamedTextColor.RED, 5, "immortal", "Immortal", "phoenix");
        String key = Display.key(base, "", false, "", "");
        assertEquals(key, Display.key(new Display.RankView("", NamedTextColor.RED, 5, "immortal", "Immortal", "phoenix"), "", false, "", ""));
        assertNotEquals(key, Display.key(new Display.RankView("", NamedTextColor.RED, 5, "immortal", "Immortal", "frost"), "", false, "", ""));
        assertNotEquals(key, Display.key(new Display.RankView("", NamedTextColor.RED, 5, "immortal", "Baby Phoenix", "phoenix"), "", false, "", ""));
        assertNotEquals(key, Display.key(new Display.RankView("", NamedTextColor.RED, 5, "pyrelord", "Immortal", "phoenix"), "", false, "", ""));
    }

    @Test
    void defaultApprovedGradientsAreValidAndPhoenixIsFirst() {
        var parsed = BadgeGradients.parse(new ChatConfig().immortalGradients);
        assertEquals(List.of("phoenix", "ember", "dusk", "frost", "void"), List.copyOf(parsed.keySet()));
        parsed.values().forEach(g -> assertTrue(g.size() >= 2));
    }
}
