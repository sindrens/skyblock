package net.skybornmc.skyblock.tools;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config framework writes {@code blueprints.drops-enabled} and {@code abilities.lifesteal-max-heal} before the catalog
 * is read, so a section that only holds those settings must still get the default entries (it used to look "present"
 * and stay empty: no blueprints and no abilities on the live server).
 */
class CatalogDefaultsTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void sectionWithOnlySettingsHasNoEntries() throws Exception {
        YamlConfiguration y = yaml("blueprints:\n  drops-enabled: true\nabilities:\n  lifesteal-max-heal: 4\n");
        assertFalse(ToolsModule.hasEntries(y, "blueprints"));
        assertFalse(ToolsModule.hasEntries(y, "abilities"));
        assertFalse(ToolsModule.hasEntries(y, "skins"));
    }

    @Test
    void replaceKeepsSettingsAndAddsDefaults() throws Exception {
        YamlConfiguration y = yaml("blueprints:\n  drops-enabled: false\n  old_one: {kind: pickaxe}\n");
        YamlConfiguration d = yaml("blueprints:\n  magma_pickaxe: {kind: pickaxe}\n  frost_axe: {kind: axe}\n");
        assertTrue(ToolsModule.hasEntries(y, "blueprints"));
        ToolsModule.replaceEntries(y, d, "blueprints");
        assertFalse(y.getBoolean("blueprints.drops-enabled", true), "setting kept");
        assertFalse(y.contains("blueprints.old_one"), "old entry replaced");
        assertEquals("pickaxe", y.getString("blueprints.magma_pickaxe.kind"));
        assertEquals("axe", y.getString("blueprints.frost_axe.kind"));
        assertTrue(ToolsModule.hasEntries(y, "blueprints"));
    }
}
