package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-time switch of a live {@code crates.yml} to the tiered Eclipse Crate (Complete Developer Spec, owner F7a/V1-V3; pure,
 * no file access). The live {@code crates.eclipse} section is replaced by the bundled one only while its reward ids are
 * still exactly the old bundled ones and none has a band (an edited table stays, and the log says so). Marker
 * {@code eclipse-version: 2}, so a second run changes nothing.
 */
final class EclipseMigration {

    static final String KEY = "eclipse-version";
    static final int VERSION = 2;

    /** Reward ids of the old single-prize Eclipse table (catalog v2). */
    static final Set<String> OLD_IDS = Set.of("book", "money", "crystals", "month_skin", "month_tag", "month_theme", "pet_egg",
            "shard_block", "greater_tome", "collector", "global_booster", "autosell_chest", "phoenix_key", "mythic_book",
            "regalia_token", "month_title");

    record Result(boolean changed, String log) {
    }

    private EclipseMigration() {
    }

    static Result migrate(YamlConfiguration live, YamlConfiguration bundled) {
        if (live.getInt(KEY, 1) >= VERSION) {
            return new Result(false, "");
        }
        live.set(KEY, VERSION);
        String path = CrateCatalog.ROOT + ".eclipse";
        ConfigurationSection now = live.getConfigurationSection(path);
        ConfigurationSection next = bundled.getConfigurationSection(path);
        if (next == null) {
            return new Result(true, "eclipse: the jar has no Eclipse section, nothing switched");
        }
        if (now == null) {
            return new Result(true, "eclipse: no live Eclipse section (it is added from the jar)");
        }
        Set<String> ids = new HashSet<>();
        boolean banded = false;
        for (Map<?, ?> r : now.getMapList("rewards")) {
            ids.add(String.valueOf(r.get("id")));
            banded |= r.containsKey("band");
        }
        if (banded || !ids.equals(OLD_IDS)) {
            return new Result(true, "eclipse: kept your edited Eclipse table " + ids + "; switch it to the tiered pool by hand "
                    + "(see the bundled crates.yml)");
        }
        live.set(path, null);
        for (String key : next.getKeys(false)) {
            Object v = next.get(key);
            if (v instanceof ConfigurationSection sec) {
                live.createSection(path + "." + key, sec.getValues(true));
            } else {
                live.set(path + "." + key, v);
            }
        }
        return new Result(true, "eclipse: switched to the tiered pool (" + next.getMapList("rewards").size() + " rewards, no pity)");
    }

    /** Ids of a section's rewards (tests). */
    static List<String> ids(ConfigurationSection section) {
        return section.getMapList("rewards").stream().map(r -> String.valueOf(r.get("id"))).toList();
    }
}
