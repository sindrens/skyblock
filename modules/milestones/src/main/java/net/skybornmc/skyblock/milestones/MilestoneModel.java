package net.skybornmc.skyblock.milestones;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The data of the milestone catalog (Complete Developer Spec §7.6-7.7), built once at load from {@code milestones.yml}.
 * Pure records, no Bukkit, unit-tested.
 */
final class MilestoneModel {

    private MilestoneModel() {
    }

    /** Which menu a chain is in, and who shares it (spec §7.2). */
    enum Menu {
        /** Island Milestones: player actions on island worlds, shared by the island's members. */
        ISLAND,
        /** Personal Milestones: Depths actions and personal stats, per profile. */
        PERSONAL;

        static Optional<Menu> of(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "island" -> Optional.of(ISLAND);
                case "personal" -> Optional.of(PERSONAL);
                default -> Optional.empty();
            };
        }
    }

    /** Where an action must happen to count (spec §7.6 filter {@code world}). */
    enum WorldFilter {
        /** GRIND: the /farm and /mine world (owner 5 Nov 2026); "depths" in an old file means the same (the Depths are gone). */
        ISLAND, GRIND, ANY;

        static Optional<WorldFilter> of(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.of(ANY);
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "island" -> Optional.of(ISLAND);
                case "grind", "farm", "mine", "depths" -> Optional.of(GRIND);
                case "any" -> Optional.of(ANY);
                default -> Optional.empty();
            };
        }
    }

    /**
     * One perk of a tier: {@code value} percent in a bonus family, only for {@code target} when set (spec §9.1: perks
     * match on target only, never on world).
     */
    record Perk(String family, double value, String target) {
    }

    /**
     * One chain (spec glossary: one milestone ladder for one target).
     *
     * @param action  the game action it counts ({@code harvest_crop}, {@code break_block}, {@code kill_mob})
     * @param targets the upper-case targets it counts ({@code WHEAT}); several for e.g. deepslate ore variants
     * @param tiers   tier targets, strictly increasing (tier 1 = index 0)
     * @param perks   tier number -> perks of that tier (the highest claimed tier replaces lower ones, OWNER B3)
     * @param weight  milestone points per tier number (spec §7.9: points = tier x weight)
     */
    record Chain(String id, String name, String icon, Menu menu, String category, String action, Set<String> targets,
                 WorldFilter world, long[] tiers, Map<Integer, List<Perk>> perks, int weight) {

        int maxTier() {
            return tiers.length;
        }

        /** Highest tier whose target is reached (0 = none). Pure; completion is derived, never stored as state. */
        int tierFor(long value) {
            int t = 0;
            for (int i = 0; i < tiers.length; i++) {
                if (value >= tiers[i]) {
                    t = i + 1;
                }
            }
            return t;
        }

        /** Target of a tier (1-based), or -1 past the end. */
        long target(int tier) {
            return tier >= 1 && tier <= tiers.length ? tiers[tier - 1] : -1;
        }

        List<Perk> perksOf(int tier) {
            return perks.getOrDefault(tier, List.of());
        }
    }

    /**
     * A curve preset (spec §7.7 {@code curves}): tier n target = base x growth^(n-1) x difficulty, rounded to two
     * significant figures so the numbers read well (250, 980, 3,800 ...). Always strictly increasing.
     */
    record Curve(double base, double growth, int tiers) {

        long[] targets(double difficulty) {
            long[] out = new long[Math.max(1, tiers)];
            long last = 0;
            for (int i = 0; i < out.length; i++) {
                double raw = base * Math.pow(growth, i) * Math.max(0.0001, difficulty);
                long v = Math.max(1, round2(raw));
                if (v <= last) {
                    v = last + 1;
                }
                out[i] = v;
                last = v;
            }
            return out;
        }

        static long round2(double v) {
            if (v < 100) {
                return Math.round(v);
            }
            double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
            return Math.round(Math.round(v / mag) * mag);
        }
    }
}
