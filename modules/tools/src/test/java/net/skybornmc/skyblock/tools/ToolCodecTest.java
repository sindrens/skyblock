package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolPerk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Perk JSON keeps the crystals put into each enchant (shatter refund, ENCHANTS §4.5) and reads old data without it. */
class ToolCodecTest {

    @Test
    void investedCrystalsRoundTrip() {
        List<ToolPerk> perks = List.of(new ToolPerk(1, "vein_rend", 2, 1, 709), new ToolPerk(2, "dragonfire_blast", 1, 2, 250),
                new ToolPerk(4, "swiftness", 1));
        String json = ToolCodec.perks(perks);
        assertEquals(perks, ToolCodec.perks(json));
        assertEquals(List.of(new ToolPerk(1, "vein_rend", 3)), ToolCodec.perks("[{\"slot\":1,\"id\":\"vein_rend\",\"level\":3}]"),
                "perks from before Phase E have 0 invested");
    }
}
