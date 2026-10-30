package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The pure rules of the pillars (PHASE-5 M5.1): config parsing, which XP counts, the ISLAND_XP family (balance bonus +
 * island boosts, capped), synergy milestones and the spawner kill rule. No Bukkit state; unit-tested.
 */
final class PillarRules {

    private PillarRules() {
    }

    /** Where a skill's XP goes. */
    record SkillSource(Pillar pillar, double multiplier) {
    }

    // ------------------------------------------------------------------ parsing

    /** {@code "mining: mining 1.0"} entries → skill → pillar and multiplier. Broken entries are reported and skipped. */
    static Map<SkillType, SkillSource> skillSources(List<String> entries, Consumer<String> problem) {
        Map<SkillType, SkillSource> out = new EnumMap<>(SkillType.class);
        for (String entry : entries == null ? List.<String>of() : entries) {
            String[] kv = split(entry);
            if (kv == null) {
                problem.accept("pillars.yml → skill-sources: '" + entry + "' is not 'skill: pillar [multiplier]' (skipped)");
                continue;
            }
            SkillType skill = SkillType.byId(kv[0]).orElse(null);
            String[] parts = kv[1].trim().split("\\s+");
            Pillar pillar = parts.length == 0 ? null : Pillar.byId(parts[0]).orElse(null);
            double multiplier = 1.0;
            boolean ok = skill != null && pillar != null && parts.length <= 2;
            if (ok && parts.length == 2) {
                Double m = number(parts[1]);
                ok = m != null && m >= 0;
                multiplier = ok ? m : 1.0;
            }
            if (!ok) {
                problem.accept("pillars.yml → skill-sources: '" + entry + "' needs a skill, a pillar and an optional multiplier ≥ 0 (skipped)");
                continue;
            }
            out.put(skill, new SkillSource(pillar, multiplier));
        }
        return out;
    }

    /** {@code "key: number"} entries (keys lowercase, without {@code minecraft:}); negative or broken values are skipped. */
    static Map<String, Double> numbers(List<String> entries, String where, Consumer<String> problem) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String entry : entries == null ? List.<String>of() : entries) {
            String[] kv = split(entry);
            Double value = kv == null ? null : number(kv[1]);
            if (kv == null || value == null || value < 0 || kv[0].isBlank()) {
                problem.accept("pillars.yml → " + where + ": '" + entry + "' is not 'name: number ≥ 0' (skipped)");
                continue;
            }
            out.put(key(kv[0]), value);
        }
        return out;
    }

    /** Milestone levels: positive whole numbers, sorted, duplicates removed. */
    static List<Integer> levels(List<String> entries, Consumer<String> problem) {
        TreeSet<Integer> out = new TreeSet<>();
        for (String entry : entries == null ? List.<String>of() : entries) {
            Double value = number(entry == null ? "" : entry.trim());
            if (value == null || value < 1 || value != Math.floor(value) || value > PillarCurve.MAX_LEVEL) {
                problem.accept("pillars.yml → milestones.levels: '" + entry + "' is not a level of 1 or more (skipped)");
                continue;
            }
            out.add(value.intValue());
        }
        return List.copyOf(out);
    }

    /** {@code "level: crystals"} entries → whole crystal amounts per milestone level. */
    static Map<Integer, Long> rewards(List<String> entries, Consumer<String> problem) {
        Map<Integer, Long> out = new TreeMap<>();
        numbers(entries, "milestones.crystals-per-member", problem).forEach((k, v) -> {
            Double level = number(k);
            if (level == null || level < 1 || level != Math.floor(level)) {
                problem.accept("pillars.yml → milestones.crystals-per-member: '" + k + "' is not a level (skipped)");
                return;
            }
            out.put(level.intValue(), (long) Math.floor(v));
        });
        return out;
    }

    /** Lowercase set (sources, damage causes in upper case via {@link #upperSet}). */
    static Set<String> lowerSet(Collection<String> values) {
        Set<String> out = new HashSet<>();
        for (String v : values == null ? List.<String>of() : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    static Set<String> upperSet(Collection<String> values) {
        Set<String> out = new HashSet<>();
        for (String v : values == null ? List.<String>of() : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Lowercase id without the {@code minecraft:} namespace. */
    static String key(String id) {
        String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return k.startsWith("minecraft:") ? k.substring("minecraft:".length()) : k;
    }

    private static String[] split(String entry) {
        if (entry == null) {
            return null;
        }
        int colon = entry.indexOf(':');
        // "minecraft:wheat: 0.25" → split at the last colon followed by a space or at the only colon
        int at = entry.lastIndexOf(": ");
        if (at < 0) {
            at = colon;
        }
        if (at <= 0 || at >= entry.length() - 1) {
            return null;
        }
        return new String[]{entry.substring(0, at).trim(), entry.substring(at + 1).trim()};
    }

    private static Double number(String s) {
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ which XP counts

    /** The pillars players see: the four main ones, plus Conquest once it has a source. */
    static List<Pillar> shown(boolean conquestEnabled) {
        List<Pillar> out = new ArrayList<>(List.of(Pillar.values()));
        if (!conquestEnabled) {
            out.remove(Pillar.CONQUEST);
        }
        return List.copyOf(out);
    }

    /**
     * The base XP a skill gain gives the pillar (RK20): the amount the source asked for, never more than what the player
     * really got (a listener that lowered or zeroed the gain lowers the pillar too). 0 when nothing was added.
     */
    static double baseXp(double baseAmount, double finalAmount) {
        if (!(finalAmount > 0) || !(baseAmount > 0) || Double.isInfinite(baseAmount) || Double.isInfinite(finalAmount)) {
            return 0;
        }
        return Math.min(baseAmount, finalAmount);
    }

    /** True if XP from this skill source never reaches a pillar (e.g. {@code stack_kill}). */
    static boolean excluded(String source, Set<String> excludedSources) {
        return source != null && excludedSources.contains(source.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Spawner kill rule (owner P5-7): only a kill whose last damage came from the killing player, with an allowed cause
     * (own hit, sweep, arrow), counts. Fall, lava, suffocation, other mobs and stack kills give nothing.
     */
    static boolean killCounts(String lastDamageCause, boolean damagedByKiller, Set<String> allowedCauses) {
        return damagedByKiller && lastDamageCause != null && allowedCauses.contains(lastDamageCause.toUpperCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ ISLAND_XP family

    /** Lowest level among the shown pillars (0 when none is shown). */
    static int lowest(Map<Pillar, Integer> levels, List<Pillar> shown) {
        int min = Integer.MAX_VALUE;
        for (Pillar p : shown) {
            min = Math.min(min, levels.getOrDefault(p, 0));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /** Sum of the shown pillars' levels. */
    static int total(Map<Pillar, Integer> levels, List<Pillar> shown) {
        long sum = 0;
        for (Pillar p : shown) {
            sum += levels.getOrDefault(p, 0);
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /**
     * Balance bonus: {@code perPillar}% for each shown pillar at most {@code window} levels below the highest shown
     * pillar (the highest counts itself), at most {@code max}%.
     */
    static double balanceBonus(Map<Pillar, Integer> levels, List<Pillar> shown, int window, double perPillar, double max) {
        if (shown.isEmpty() || !(perPillar > 0) || !(max > 0)) {
            return 0;
        }
        int highest = 0;
        for (Pillar p : shown) {
            highest = Math.max(highest, levels.getOrDefault(p, 0));
        }
        int counted = 0;
        for (Pillar p : shown) {
            if (highest - levels.getOrDefault(p, 0) <= Math.max(0, window)) {
                counted++;
            }
        }
        return Math.min(max, counted * perPillar);
    }

    /** The ISLAND_XP family: all island boosts add up, then the cap applies once; never negative. */
    static double familyPercent(double balance, double external, double cap) {
        double sum = (Double.isFinite(balance) ? balance : 0) + (Double.isFinite(external) ? external : 0);
        return Math.max(0, Math.min(Math.max(0, cap), sum));
    }

    static double multiplier(double familyPercent) {
        return 1 + Math.max(0, familyPercent) / 100.0;
    }

    // ------------------------------------------------------------------ weeks

    /** Week bucket of a day: epoch day of that week's Monday (same buckets as the island stats). */
    static int weekStart(java.time.LocalDate day) {
        return (int) day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay();
    }

    // ------------------------------------------------------------------ milestones

    /** Milestones at or below {@code lowest} that are not reached yet, lowest first. */
    static List<Integer> newlyReached(List<Integer> milestones, int lowest, Set<Integer> reached) {
        List<Integer> out = new ArrayList<>();
        for (int m : milestones) {
            if (m <= lowest && !reached.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }
}
