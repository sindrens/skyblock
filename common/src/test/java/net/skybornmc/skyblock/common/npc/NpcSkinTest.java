package net.skybornmc.skyblock.common.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026: NPCs wear real skins (vanilla defaults, a Mojang texture or a player's skin). */
class NpcSkinTest {

    @Test
    void vanillaDefaults() {
        NpcSkin alex = NpcSkin.parse("Alex").orElseThrow();
        assertEquals(NpcSkin.Kind.DEFAULT, alex.kind());
        assertTrue(alex.slim());
        assertEquals("minecraft:entity/player/slim/alex", alex.textureKey().asString());
        NpcSkin kai = NpcSkin.parse("default:kai").orElseThrow();
        assertFalse(kai.slim());
        assertEquals("minecraft:entity/player/wide/kai", kai.textureKey().asString());
        assertTrue(NpcSkin.parse("slim:zuri").orElseThrow().slim());
        assertFalse(NpcSkin.parse("wide:noor").orElseThrow().slim());
        assertEquals(9, NpcSkin.DEFAULTS.size());
        assertTrue(NpcSkin.parse("default:notch").isEmpty());
        assertTrue(NpcSkin.parse("slim:notch").isEmpty());
    }

    @Test
    void texturesAndPlayers() {
        NpcSkin t = NpcSkin.parse("texture:ewogIC=:c2lnbmF0dXJl").orElseThrow();
        assertEquals(NpcSkin.Kind.TEXTURE, t.kind());
        assertEquals("ewogIC=", t.value());
        assertEquals("c2lnbmF0dXJl", t.signature());
        assertNull(NpcSkin.parse("texture:abc").orElseThrow().signature());
        assertTrue(NpcSkin.parse("texture:").isEmpty());
        assertEquals(NpcSkin.Kind.PLAYER, NpcSkin.parse("Notch").orElseThrow().kind());
        assertEquals("alex", NpcSkin.parse("player:alex").orElseThrow().value().toLowerCase(), "player: forces a lookup");
        assertEquals(NpcSkin.Kind.PLAYER, NpcSkin.parse("player:alex").orElseThrow().kind());
        assertTrue(NpcSkin.parse("not a name!").isEmpty());
        assertTrue(NpcSkin.parse("").isEmpty());
        assertTrue(NpcSkin.parse(null).isEmpty());
    }
}
