package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.npc.NpcSkin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026: every spawn NPC wears a real (vanilla default) skin, Rowan included. */
class NpcSkinsTest {

    @Test
    void everyDefaultSkinIsAVanillaSkin() {
        List<String> problems = new ArrayList<>();
        Map<String, String> skins = RoleRules.pairs("npcs.skins", new NpcsConfig().skins, problems);
        assertEquals(List.of(), problems);
        assertTrue(skins.containsKey("guide"), "Rowan (role guide) has a skin");
        skins.forEach((role, skin) -> assertEquals(NpcSkin.Kind.DEFAULT, NpcSkin.parse(skin).orElseThrow().kind(), role));
        var change = new NpcsConfig().valueChanges().get(7).get("npcs.skins");
        assertTrue(change.matches(List.of()), "an empty live list gets the skins");
        assertEquals(new NpcsConfig().skins, change.to());
    }
}
