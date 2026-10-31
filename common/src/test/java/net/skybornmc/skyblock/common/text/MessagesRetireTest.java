package net.skybornmc.skyblock.common.text;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesRetireTest {

    private static YamlConfiguration yaml(String key, String value) {
        YamlConfiguration y = new YamlConfiguration();
        y.set(key, value);
        return y;
    }

    @Test
    void unEditedOldTextIsUpgraded() {
        YamlConfiguration live = yaml("a.b", "old text");
        assertTrue(Messages.applyRetired(live, yaml("a.b", "new text"), Map.of("a.b", "old text")));
        assertEquals("new text", live.getString("a.b"));
    }

    @Test
    void ownerEditIsKept() {
        YamlConfiguration live = yaml("a.b", "my own text");
        assertFalse(Messages.applyRetired(live, yaml("a.b", "new text"), Map.of("a.b", "old text")));
        assertEquals("my own text", live.getString("a.b"));
    }

    @Test
    void listsCompareLineByLine() {
        YamlConfiguration live = new YamlConfiguration();
        live.set("k", java.util.List.of("a", "old"));
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("k", java.util.List.of("a", "new"));
        assertTrue(Messages.applyRetired(live, bundled, Map.of("k", "a\nold")));
        assertEquals(java.util.List.of("a", "new"), live.getStringList("k"));
    }

    @Test
    void missingKeysAreIgnored() {
        YamlConfiguration live = new YamlConfiguration();
        assertFalse(Messages.applyRetired(live, yaml("a.b", "new"), Map.of("a.b", "old")));
        assertFalse(live.contains("a.b"));
    }
}
