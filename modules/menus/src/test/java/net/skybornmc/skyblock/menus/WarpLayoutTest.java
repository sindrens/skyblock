package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UX3: the /menu → Warps list (menu.yml → warps), its targets, texts and the one-time Warps button switch. */
class WarpLayoutTest {

    private static YamlConfiguration resource(String name) throws Exception {
        try (var in = WarpLayoutTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void targetsParse() {
        assertEquals(new WarpLayout.Command("spawn"), WarpLayout.target("command spawn"));
        assertEquals(new WarpLayout.Command("is warp"), WarpLayout.target("command  /is warp "));
        assertEquals(new WarpLayout.Point("entries", "tavern_road"), WarpLayout.target("point entries tavern_road"));
        assertEquals(new WarpLayout.Place("spawn", 0.5, 99, 33.5, 0f, 0f), WarpLayout.target("location spawn 0.5 99 33.5"));
        assertEquals(new WarpLayout.Place("depths", 7.5, 100, 14.5, 165f, 90f), WarpLayout.target("location depths 7.5 100 14.5 165 200"),
                "pitch clamped");
        for (String bad : new String[]{null, "", "spawn", "command", "command /", "point entries", "point a b c",
                "location spawn 1 2", "location spawn 1 2 3 4", "location spawn x 2 3", "location spawn NaN 2 3", "teleport spawn"}) {
            assertNull(WarpLayout.target(bad), String.valueOf(bad));
        }
    }

    @Test
    void roadEntriesStandInTheMiddleOfTheBlock() {
        assertEquals(-45.5, WarpLayout.centre(-46));
        assertEquals(10.5, WarpLayout.centre(10));
        assertEquals(7.5, WarpLayout.centre(7.5), "feet positions stay");
    }

    @Test
    void reasonsMapToTeleportReasons() {
        assertEquals("spawn", WarpsMenu.reason("spawn").id());
        assertEquals("dungeon", WarpsMenu.reason("dungeon").id());
        assertEquals("spawn", WarpsMenu.reason("nonsense").id(), "fallback: the /spawn warmup");
    }

    /** Every bundled warp has a valid target, a free inner slot and its texts; the hub button opens it. */
    @Test
    void bundledWarpsAreComplete() throws Exception {
        YamlConfiguration menu = resource("menu.yml");
        YamlConfiguration texts = resource("messages/navigation.yml");
        ConfigurationSection warps = menu.getConfigurationSection("warps");
        assertNotNull(warps);
        Set<Integer> slots = new HashSet<>();
        for (String id : warps.getKeys(false)) {
            ConfigurationSection e = warps.getConfigurationSection(id);
            assertNotNull(e, id);
            int slot = e.getInt("slot", -1);
            assertFalse(HubLayout.RESERVED.contains(slot), id + " slot " + slot);
            assertTrue(slot > 0 && slot < 54 && slots.add(slot), id + " slot " + slot);
            assertNotNull(WarpLayout.target(e.getString("target")), id + " target");
            assertTrue(WarpLayout.REASONS.contains(e.getString("reason", WarpLayout.DEFAULT_REASON)), id + " reason");
            assertTrue(texts.isString("warps.entries." + id + ".name"), id + " name");
            assertTrue(texts.isList("warps.entries." + id + ".lore"), id + " lore");
        }
        assertTrue(slots.size() >= 6, "spawn, market, crates, forge, tavern, hall, depths, player warps");
        assertInstanceOf(WarpLayout.Command.class, WarpLayout.target(warps.getString("spawn.target")));
        assertEquals(HubWarpsMigration.WARPS, menu.getString("entries.warps.command"));
        assertEquals("active", menu.getString("entries.warps.status"), "warps is a built-in screen, not a server command");
        for (String key : new String[]{"warps.title", "warps.action", "warps.fallback-name", "warps.gone", "warps.empty.name"}) {
            assertTrue(texts.contains(key), key);
        }
    }

    @Test
    void oldWarpsButtonIsSwitchedOnce() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("entries.warps.slot", 28);
        old.set("entries.warps.command", "is warp");
        old.set("entries.warps.status", "auto");
        assertTrue(HubWarpsMigration.apply(old));
        assertEquals("warps", old.getString("entries.warps.command"));
        assertEquals("active", old.getString("entries.warps.status"));
        assertFalse(HubWarpsMigration.apply(old), "only once");

        YamlConfiguration owner = new YamlConfiguration();
        owner.set("entries.warps.command", "warp list");
        assertFalse(HubWarpsMigration.apply(owner), "an owner-chosen command stays");
        assertEquals("warp list", owner.getString("entries.warps.command"));
        assertFalse(HubWarpsMigration.apply(new YamlConfiguration()));
    }
}
