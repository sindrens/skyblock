package net.skybornmc.skyblock.cosmetics;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 2 Nov 2026: bold capital tags reach live servers without overwriting staff edits. */
class TagUpgradesTest {

    private static YamlConfiguration bundled() throws Exception {
        try (var in = TagUpgradesTest.class.getClassLoader().getResourceAsStream(CosmeticRegistry.FILE)) {
            assertNotNull(in);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void uneditedDisplaysUpgradeEditedStayAndNewTagsArriveOnce() throws Exception {
        YamlConfiguration live = new YamlConfiguration();
        live.set("tags.phoenix.display", "<grad:phoenix>Phoenix</grad>");
        live.set("tags.og.display", "<c:flame>Our OG"); // staff edit
        live.set("tags.custom.display", "<c:value>MINE");
        YamlConfiguration bundled = bundled();

        assertTrue(TagUpgrades.apply(live, bundled));
        assertEquals("<grad:phoenix>PHOENIX</grad>", live.getString("tags.phoenix.display"));
        assertEquals("<c:flame>Our OG", live.getString("tags.og.display"));
        assertEquals("<c:value>MINE", live.getString("tags.custom.display"));
        for (String id : TagUpgrades.ADDED) {
            assertTrue(live.contains("tags." + id + ".display"), id);
        }

        live.set("tags.pyro", null); // staff deleted it on purpose
        assertFalse(TagUpgrades.apply(live, bundled), "second load: nothing to do");
        assertFalse(live.contains("tags.pyro"));
    }

    @Test
    void everyBundledTagIsOneCapitalWord() throws Exception {
        YamlConfiguration bundled = bundled();
        for (String id : bundled.getConfigurationSection("tags").getKeys(false)) {
            String visible = bundled.getString("tags." + id + ".display").replaceAll("<[^>]*>", "");
            assertTrue(visible.matches("[A-Z]{2,12}"), id + ": " + visible);
        }
        for (String id : TagUpgrades.OLD_DISPLAYS.keySet()) {
            assertTrue(bundled.contains("tags." + id), id);
        }
    }
}
