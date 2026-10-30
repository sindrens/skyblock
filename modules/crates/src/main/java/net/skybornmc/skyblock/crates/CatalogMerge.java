package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adds crates that are new in the bundled {@code crates.yml} to the server's file (pure, tested). */
final class CatalogMerge {

    private CatalogMerge() {
    }

    /**
     * Copies every bundled crate the file does not have yet. Existing crates are never touched: a reward the owner deleted
     * must not come back, and their odds stay as edited. True if something was added.
     */
    static boolean addMissing(YamlConfiguration yaml, YamlConfiguration bundled) {
        ConfigurationSection root = bundled.getConfigurationSection(CrateCatalog.ROOT);
        if (root == null) {
            return false;
        }
        boolean changed = false;
        for (String id : root.getKeys(false)) {
            String path = CrateCatalog.ROOT + "." + id;
            if (!yaml.contains(path)) {
                ConfigurationSection from = root.getConfigurationSection(id);
                if (from != null) {
                    copy(from, yaml.createSection(path));
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Turns rewards that were {@code coming-soon} in the server's file into the live reward the bundled file now has under
     * the same crate and id (M10.2: boosters went live). Only the type and the new type's missing keys (e.g. {@code booster},
     * {@code percent}, {@code minutes}) are copied; the owner's chance, top band, icon and rarity stay. Rewards the owner
     * gave another type, or deleted, are never touched. True if something changed.
     */
    static boolean upgradeLive(YamlConfiguration yaml, YamlConfiguration bundled) {
        ConfigurationSection root = yaml.getConfigurationSection(CrateCatalog.ROOT);
        ConfigurationSection bundledRoot = bundled.getConfigurationSection(CrateCatalog.ROOT);
        if (root == null || bundledRoot == null) {
            return false;
        }
        boolean changed = false;
        for (String crate : root.getKeys(false)) {
            ConfigurationSection mine = root.getConfigurationSection(crate);
            ConfigurationSection theirs = bundledRoot.getConfigurationSection(crate);
            if (mine == null || theirs == null) {
                continue;
            }
            List<Map<?, ?>> rewards = mine.getMapList("rewards");
            List<Map<?, ?>> live = theirs.getMapList("rewards");
            List<Map<String, Object>> out = new ArrayList<>(rewards.size());
            boolean crateChanged = false;
            for (Map<?, ?> reward : rewards) {
                Map<String, Object> copy = new LinkedHashMap<>();
                reward.forEach((k, v) -> copy.put(String.valueOf(k), v));
                Optional<Map<?, ?>> bundledReward = comingSoon(copy) ? find(live, String.valueOf(copy.get("id"))) : Optional.empty();
                if (bundledReward.isPresent() && !comingSoon(bundledReward.get())) {
                    copy.put("type", bundledReward.get().get("type"));
                    bundledReward.get().forEach((k, v) -> copy.putIfAbsent(String.valueOf(k), v));
                    crateChanged = true;
                }
                out.add(copy);
            }
            if (crateChanged) {
                mine.set("rewards", out);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean comingSoon(Map<?, ?> reward) {
        return RewardType.byKey(String.valueOf(reward.get("type"))).orElse(null) == RewardType.COMING_SOON;
    }

    private static Optional<Map<?, ?>> find(List<Map<?, ?>> rewards, String id) {
        for (Map<?, ?> r : rewards) {
            if (id.equals(String.valueOf(r.get("id")))) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    private static void copy(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            ConfigurationSection inner = from.getConfigurationSection(key);
            if (inner != null) {
                copy(inner, to.createSection(key));
            } else {
                to.set(key, from.get(key));
            }
        }
    }
}
