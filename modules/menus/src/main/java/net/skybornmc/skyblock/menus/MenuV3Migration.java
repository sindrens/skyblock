package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI-P2 (G9 v3, I9 v2): one-time switch of a live {@code menu.yml} to the icon keys and area colours. Pure, like
 * {@link HubV2Migration}. Runs only while a {@code hub-groups.<id>.mark} exists, so once:
 * <ul>
 *   <li>a hub, You or Travel entry whose {@code icon} is still the old default item gets its icon key (an icon the
 *       owner changed is kept);</li>
 *   <li>each {@code hub-groups.<id>.mark} becomes a {@code color} (area name);</li>
 *   <li>the {@code slot} of You and Travel entries is removed (they are centred in file order now) only when every
 *       slot is still the old default; then the entries also take the new default order;</li>
 *   <li>the Staff tile ({@code hub.staff}) is added when missing.</li>
 * </ul>
 * The caller keeps {@link #BACKUP} and logs it.
 */
final class MenuV3Migration {

    static final String BACKUP = "menu.yml.before-gui-p2";

    /** Old default item → new icon key, per section and entry id. */
    static final Map<String, Map<String, String[]>> ICONS = Map.of(
            "hub", ordered(
                    "home", "red_bed", "feature/home",
                    "island", "grass_block", "feature/island",
                    "depths", "iron_pickaxe", "feature/depths",
                    "travel", "ender_pearl", "feature/travel",
                    "sell", "gold_nugget", "feature/sell",
                    "shop", "emerald", "feature/shop",
                    "bank", "barrel", "feature/bank",
                    "auction", "gold_block", "feature/auction",
                    "forge", "smithing_table", "feature/forge",
                    "skills", "experience_bottle", "feature/skills",
                    "enchanter", "enchanting_table", "feature/enchanter",
                    "wardrobe", "leather_chestplate", "feature/wardrobe",
                    "rewards", "chest", "feature/rewards",
                    "top", "golden_helmet", "feature/top"),
            "you", ordered(
                    "profiles", "armor_stand", "feature/profiles",
                    "titles", "name_tag", "feature/titles",
                    "achievements", "totem_of_undying", "feature/achievements",
                    "quests", "writable_book", "feature/quests",
                    "collection", "bookshelf", "feature/collection",
                    "settings", "comparator", "feature/settings",
                    "rules", "lectern", "feature/rules",
                    "store", "diamond", "feature/store",
                    "credits", "emerald", "currency/credits"),
            "travel", ordered(
                    "spawn", "campfire", "travel/spawn",
                    "my-island", "grass_block", "feature/island",
                    "depths-gate", "iron_pickaxe", "feature/depths",
                    "crate-yard", "tripwire_hook", "travel/crate_yard",
                    "market", "emerald", "travel/market",
                    "forge-quarter", "smithing_table", "feature/forge",
                    "visit-islands", "compass", "travel/visit"));

    /** The old default slots of You and Travel (GUI-2). */
    static final Map<String, Map<String, Integer>> OLD_SLOTS = Map.of(
            "you", Map.of("profiles", 10, "titles", 11, "achievements", 12, "settings", 13, "store", 14, "rules", 15,
                    "quests", 16, "collection", 24, "credits", 20),
            "travel", Map.of("spawn", 10, "my-island", 11, "depths-gate", 12, "crate-yard", 13, "market", 14,
                    "forge-quarter", 15, "visit-islands", 16));

    /** Old marker groups → area colour (G9). */
    static final Map<String, String> COLORS = Map.of("play", "home", "money", "money", "power", "skills", "more", "reward");

    private MenuV3Migration() {
    }

    private static Map<String, String[]> ordered(String... triples) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (int i = 0; i < triples.length; i += 3) {
            out.put(triples[i], new String[]{triples[i + 1], triples[i + 2]});
        }
        return out;
    }

    /** True when the live file still has the GUI-2 marker panes (the migration has not run). */
    static boolean needed(YamlConfiguration yaml) {
        ConfigurationSection groups = yaml.getConfigurationSection("hub-groups");
        if (groups == null) {
            return false;
        }
        for (String id : groups.getKeys(false)) {
            if (groups.contains(id + ".mark")) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code yaml} was changed (save it). */
    static boolean apply(YamlConfiguration yaml) {
        if (!needed(yaml)) {
            return false;
        }
        for (Map.Entry<String, Map<String, String[]>> section : ICONS.entrySet()) {
            for (Map.Entry<String, String[]> entry : section.getValue().entrySet()) {
                String path = section.getKey() + "." + entry.getKey() + ".icon";
                String icon = yaml.getString(path);
                if (icon != null && icon.strip().toLowerCase(Locale.ROOT).equals(entry.getValue()[0])) {
                    yaml.set(path, entry.getValue()[1]);
                }
            }
        }
        ConfigurationSection groups = yaml.getConfigurationSection("hub-groups");
        for (String id : groups.getKeys(false)) {
            if (groups.contains(id + ".mark")) {
                groups.set(id + ".mark", null);
                if (!groups.contains(id + ".color")) {
                    groups.set(id + ".color", COLORS.getOrDefault(id.toLowerCase(Locale.ROOT), ButtonLayout.DEFAULT_AREA));
                }
            }
        }
        for (String name : List.of("you", "travel")) {
            dropDefaultSlots(yaml, name);
        }
        if (!yaml.isConfigurationSection("hub.staff")) {
            yaml.set("hub.staff.slot", HubMenu.SLOT_STAFF);
            yaml.set("hub.staff.icon", "feature/staff");
            yaml.set("hub.staff.action", ButtonLayout.STAFF);
            yaml.set("hub.staff.command", "staff");
            yaml.set("hub.staff.permission", HubMenu.PERM_STAFF_TILE);
            yaml.set("hub.staff.show-locked", false);
        }
        return true;
    }

    /** Removes every {@code slot} of a section and takes the new default order, but only when all slots are the old defaults. */
    private static void dropDefaultSlots(YamlConfiguration yaml, String name) {
        ConfigurationSection section = yaml.getConfigurationSection(name);
        if (section == null) {
            return;
        }
        Map<String, Integer> defaults = OLD_SLOTS.get(name);
        for (String id : section.getKeys(false)) {
            if (!section.contains(id + ".slot")) {
                continue;
            }
            Integer old = defaults.get(id);
            if (old == null || section.getInt(id + ".slot") != old) {
                return; // the owner arranged them: keep everything as it is (slots are ignored when read)
            }
        }
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        List<String> order = new ArrayList<>(ICONS.get(name).keySet());
        for (String id : section.getKeys(false)) {
            if (!order.contains(id)) {
                order.add(id); // owner-added entries go last, in their old order
            }
        }
        for (String id : order) {
            ConfigurationSection e = section.getConfigurationSection(id);
            if (e == null) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>(e.getValues(false));
            values.remove("slot");
            entries.put(id, values);
        }
        yaml.set(name, null);
        for (Map.Entry<String, Map<String, Object>> e : entries.entrySet()) {
            for (Map.Entry<String, Object> v : e.getValue().entrySet()) {
                yaml.set(name + "." + e.getKey() + "." + v.getKey(), v.getValue());
            }
        }
    }
}
