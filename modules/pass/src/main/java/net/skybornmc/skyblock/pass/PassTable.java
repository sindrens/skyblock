package net.skybornmc.skyblock.pass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The reward tables of a season, parsed from {@code pass.yml} (no Bukkit; unit-tested).
 * <ul>
 *   <li><b>Free track</b> (per profile): the small reward of every level in turn, plus the milestone extras of that level;
 *       overflow tiers (above the last level) give the overflow reward.</li>
 *   <li><b>Premium track</b> (per account): cosmetics only (R3-44, Mojang rules). The parser refuses money, crystals, keys
 *       and boosters there, so a paid track can never hold currencies or random rewards.</li>
 * </ul>
 */
final class PassTable {

    enum Track {
        FREE("free"), PREMIUM("premium");

        private final String id;

        Track(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        static Optional<Track> byId(String id) {
            for (Track t : values()) {
                if (t.id.equalsIgnoreCase(id == null ? "" : id.trim())) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }
    }

    enum Kind {
        MONEY, CRYSTALS, KEY, BOOSTER, TITLE, TAG;

        boolean cosmetic() {
            return this == TITLE || this == TAG;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One reward.
     *
     * @param ref     crate id (key), {@code type:percent:minutes} (booster), cosmetic id (title, tag); empty otherwise
     * @param amount  keys, crystal pouch base; 1 for the others
     * @param minutes minutes of income (money); 0 otherwise
     * @param rarity  title rarity word (may be empty)
     */
    record Reward(Kind kind, String ref, long amount, double minutes, String rarity) {
    }

    static final PassTable EMPTY = new PassTable(List.of(), new TreeMap<>(), List.of(), new TreeMap<>());

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final List<String> BOOSTERS = List.of("sell", "skill_xp", "tool_xp", "crystals");
    private static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic", "special");

    private final List<Reward> everyLevel;
    private final NavigableMap<Integer, List<Reward>> freeMilestones;
    private final List<Reward> overflow;
    private final NavigableMap<Integer, List<Reward>> premium;

    private PassTable(List<Reward> everyLevel, NavigableMap<Integer, List<Reward>> freeMilestones, List<Reward> overflow,
                      NavigableMap<Integer, List<Reward>> premium) {
        this.everyLevel = List.copyOf(everyLevel);
        this.freeMilestones = Collections.unmodifiableNavigableMap(freeMilestones);
        this.overflow = List.copyOf(overflow);
        this.premium = Collections.unmodifiableNavigableMap(premium);
    }

    /** Parses all four lists; problems (skipped lines) go to {@code problem}. */
    static PassTable parse(List<String> everyLevel, List<String> freeMilestones, List<String> overflow, List<String> premium,
                           int maxLevel, Consumer<String> problem) {
        List<Reward> every = new ArrayList<>();
        for (String line : everyLevel) {
            reward(line, false, "free.every-level", problem).ifPresent(every::add);
        }
        List<Reward> over = new ArrayList<>();
        for (String line : overflow) {
            reward(line, false, "free.overflow", problem).ifPresent(over::add);
        }
        return new PassTable(every, milestones(freeMilestones, false, "free.milestones", maxLevel, problem), over,
                milestones(premium, true, "premium.milestones", maxLevel, problem));
    }

    private static NavigableMap<Integer, List<Reward>> milestones(List<String> lines, boolean cosmeticOnly, String where, int maxLevel,
                                                                  Consumer<String> problem) {
        NavigableMap<Integer, List<Reward>> out = new TreeMap<>();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                problem.accept("pass.yml -> " + where + ": '" + line + "' needs '<level>: <reward>' (skipped)");
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(line.substring(0, colon).trim());
            } catch (NumberFormatException e) {
                problem.accept("pass.yml -> " + where + ": '" + line + "' does not start with a level number (skipped)");
                continue;
            }
            if (level < 1 || level > maxLevel) {
                problem.accept("pass.yml -> " + where + ": level " + level + " is outside 1-" + maxLevel + " (skipped)");
                continue;
            }
            Optional<Reward> r = reward(line.substring(colon + 1), cosmeticOnly, where, problem);
            r.ifPresent(reward -> out.computeIfAbsent(level, k -> new ArrayList<>()).add(reward));
        }
        out.replaceAll((k, v) -> List.copyOf(v));
        return out;
    }

    /** One reward from words like {@code key ember 2}; empty (with a problem) when it is not valid there. */
    static Optional<Reward> reward(String raw, boolean cosmeticOnly, String where, Consumer<String> problem) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String[] w = text.isEmpty() ? new String[0] : text.split("\\s+");
        String at = "pass.yml -> " + where + ": '" + text + "'";
        if (w.length == 0) {
            problem.accept(at + " is empty (skipped)");
            return Optional.empty();
        }
        Kind kind;
        try {
            kind = Kind.valueOf(w[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problem.accept(at + ": unknown reward '" + w[0] + "' (skipped)");
            return Optional.empty();
        }
        if (cosmeticOnly && !kind.cosmetic()) {
            problem.accept(at + ": the premium track is cosmetics only (title or tag; R3-44), so this is skipped");
            return Optional.empty();
        }
        try {
            return switch (kind) {
                case MONEY -> {
                    double minutes = Double.parseDouble(need(w, 1));
                    yield minutes > 0 && minutes <= 1_000 ? Optional.of(new Reward(kind, "", 1, minutes, ""))
                            : invalid(at, "minutes must be 0-1000", problem);
                }
                case CRYSTALS -> {
                    long base = Long.parseLong(need(w, 1));
                    yield base > 0 && base <= 1_000_000 ? Optional.of(new Reward(kind, "", base, 0, ""))
                            : invalid(at, "the pouch base must be 1-1000000", problem);
                }
                case KEY -> {
                    String crate = need(w, 1);
                    long n = w.length > 2 ? Long.parseLong(w[2]) : 1;
                    yield ID.matcher(crate).matches() && n >= 1 && n <= 64 ? Optional.of(new Reward(kind, crate, n, 0, ""))
                            : invalid(at, "needs a crate id and 1-64 keys", problem);
                }
                case BOOSTER -> {
                    String type = need(w, 1);
                    int percent = Integer.parseInt(need(w, 2));
                    int minutes = Integer.parseInt(need(w, 3));
                    yield BOOSTERS.contains(type) && percent > 0 && percent <= 1_000 && minutes > 0 && minutes <= 1_440
                            ? Optional.of(new Reward(kind, type + ":" + percent + ":" + minutes, 1, 0, ""))
                            : invalid(at, "needs booster <sell|skill_xp|tool_xp|crystals> <percent> <minutes>", problem);
                }
                case TITLE, TAG -> {
                    String id = need(w, 1);
                    String rarity = kind == Kind.TITLE && w.length > 2 ? w[2] : "";
                    if (!ID.matcher(id).matches()) {
                        yield invalid(at, "the id may only use a-z, 0-9 and _", problem);
                    }
                    if (!rarity.isEmpty() && !RARITIES.contains(rarity)) {
                        yield invalid(at, "unknown rarity '" + rarity + "'", problem);
                    }
                    yield Optional.of(new Reward(kind, id, 1, 0, rarity));
                }
            };
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return invalid(at, "a number is missing or wrong", problem);
        }
    }

    private static String need(String[] w, int i) {
        if (i >= w.length) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        return w[i];
    }

    private static Optional<Reward> invalid(String at, String why, Consumer<String> problem) {
        problem.accept(at + ": " + why + " (skipped)");
        return Optional.empty();
    }

    // ================================================================== lookups

    /** Rewards of a free track tier (overflow tiers above {@code maxLevel} give the overflow reward). */
    List<Reward> free(int tier, int maxLevel) {
        if (tier < 1) {
            return List.of();
        }
        if (tier > maxLevel) {
            return overflow;
        }
        List<Reward> out = new ArrayList<>(freeMilestones.getOrDefault(tier, List.of()));
        if (!everyLevel.isEmpty()) {
            out.add(everyLevel.get((tier - 1) % everyLevel.size()));
        }
        return out;
    }

    /** Rewards of a premium tier (cosmetics only; empty for most levels). */
    List<Reward> premium(int tier) {
        return premium.getOrDefault(tier, List.of());
    }

    List<Reward> rewards(Track track, int tier, int maxLevel) {
        return track == Track.FREE ? free(tier, maxLevel) : premium(tier);
    }

    /** Levels with a premium reward, ascending. */
    List<Integer> premiumTiers() {
        return List.copyOf(premium.keySet());
    }

    boolean hasOverflow() {
        return !overflow.isEmpty();
    }

    /** Every title and tag the tables give (to define titles at start and to check tags). */
    List<Reward> cosmetics() {
        List<Reward> out = new ArrayList<>();
        for (List<Reward> list : freeMilestones.values()) {
            list.stream().filter(r -> r.kind().cosmetic()).forEach(out::add);
        }
        everyLevel.stream().filter(r -> r.kind().cosmetic()).forEach(out::add);
        overflow.stream().filter(r -> r.kind().cosmetic()).forEach(out::add);
        for (Map.Entry<Integer, List<Reward>> e : premium.entrySet()) {
            out.addAll(e.getValue());
        }
        return out;
    }
}
