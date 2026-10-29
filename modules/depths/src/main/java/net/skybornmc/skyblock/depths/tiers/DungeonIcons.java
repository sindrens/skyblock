package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Field;
import net.skybornmc.skyblock.depths.zones.Zone;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Items of {@code /dungeon}: tab items per wing, the tier item (the zone's ore/log block for mine and forest, the
 * configured combat icon per tier) and the locked item. All from {@code depths.yml → dungeon.*}.
 */
final class DungeonIcons {

    private DungeonIcons() {
    }

    /** {@code ["mine=iron_pickaxe", ...]} → {@code {mine: iron_pickaxe}}; blank or malformed entries are skipped. */
    static Map<String, String> wingMap(List<String> entries) {
        Map<String, String> out = new HashMap<>();
        if (entries == null) {
            return out;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            out.put(entry.substring(0, eq).trim().toLowerCase(Locale.ROOT), entry.substring(eq + 1).trim());
        }
        return out;
    }

    /**
     * The id of a zone's tier item before it is resolved: the first resource block of its first field (mine, forest),
     * else the combat icon of its tier, else {@code null} (use the wing fallback).
     */
    static String zoneIconId(Zone zone, List<String> combatIcons) {
        for (Field field : zone.fields()) {
            for (Field.Resource resource : field.resources()) {
                if (resource.block() != null && !resource.block().isBlank()) {
                    return resource.block();
                }
            }
        }
        if (zone.aspect() == Aspect.COMBAT && combatIcons != null && zone.tier() >= 1 && zone.tier() <= combatIcons.size()) {
            return combatIcons.get(zone.tier() - 1);
        }
        return null;
    }

    static Material tab(DepthsConfig config, Aspect aspect) {
        return material(wingMap(config.dungeonTabIcons).get(aspect.id()), Material.IRON_PICKAXE);
    }

    static Material tier(DepthsConfig config, Zone zone) {
        Material fallback = material(wingMap(config.dungeonFallbackIcons).get(zone.aspect().id()), Material.STONE);
        return material(zoneIconId(zone, config.dungeonCombatIcons), fallback);
    }

    static Material locked(DepthsConfig config) {
        return material(config.dungeonLockedIcon, Material.GRAY_DYE);
    }

    /** An item material from an id ({@code coal_ore}, {@code minecraft:coal_ore}), or {@code fallback}. */
    static Material material(String id, Material fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(id.trim());
        return material != null && material.isItem() && !material.isAir() ? material : fallback;
    }
}
