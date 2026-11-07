package net.skybornmc.skyblock.islandtools;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Owner 6 Nov 2026 (plans/TOOLS_V3.md): the personal grind enchants of the pickaxe (/mine) and the hoe (/farm). Each
 * player buys their own levels with their own shards; they only work in the grind areas. The island's perks stay the
 * shared island set. Pure: definitions, chance and value per level, prices.
 *
 * <p>Definition line ({@code enchants.list}):
 * {@code kind:id:max:cost:chance1:chanceMax:value1:valueMax:cooldownSeconds[:unlockLevel:unlockPrestige]}. Chance grows
 * evenly, the value on a fast-start curve, and milestones add an extra boost every 4% of the track (tools v6). Cost of level L: {@code cost x L^1.2}
 * shards (level 1 costs about {@code cost}). The unlock is the tool level (and prestige) the enchant needs.</p>
 */
final class GrindEnchants {

    /** What an enchant does when it procs (the value's meaning in brackets). */
    enum Effect {
        /** Breaks nearby blocks of the area (how many). Drops only: no shards, money, other enchants or unlock progress. */
        AREA,
        /** Extra items of the drop (how many). */
        EXTRA_ITEMS,
        /** Bonus shards (how many). */
        SHARDS,
        /** Bonus money: the drop's sell price times the value. */
        CASH,
        /** The block counts this many extra times toward the next crop or ore. */
        PROGRESS,
        /** Percent more skill XP (always on, no chance). */
        SKILL_XP,
        /** A crate key (the value is unused; the crate is enchants.key-crate). */
        KEY,
        /** After a Shard Burst: percent more shards for a few seconds (passive). */
        ECHO,
        /** A break streak: up to this many percent more shards (passive). */
        STREAK_SHARDS,
        /** A break streak: up to this many percent more tool XP (passive). */
        STREAK_XP,
        /** One of every crop or ore the player has unlocked (the value is unused). */
        BASKET,
        /** Pity: after this many blocks without a rare find, the rare chances double (passive). */
        PITY,
        /** Tools v6 (7 Nov 2026), Shard Storm: for this many seconds every break also gives a Shard Burst. */
        STORM,
        /** Frenzy: this many seconds of Haste III (pickaxe, hoe) or Strength (sword). */
        FRENZY,
        /** Golden Touch: the next this-many drops pay their sell price once more (money). */
        GOLDEN,
        /** Duplicator: the next this-many drops are doubled. */
        DUPLICATE,
        /** XP Surge: tool XP worth this many blocks at once. */
        XP_SURGE,
        /** Crystal Find: this many crystals. */
        CRYSTALS,
        /** Booster Find: this many seconds of x2 shards with this tool. */
        FEVER,
        /** Chain Reaction: this percent chance that an area enchant sets off a second one (passive). */
        CHAIN,
        /** Shard Rain: this many shards for you and every island member online. */
        RAIN;

        /** Always on: no chance roll. */
        boolean passive() {
            return this == SKILL_XP || this == ECHO || this == STREAK_SHARDS || this == STREAK_XP || this == PITY || this == CHAIN;
        }
    }

    /**
     * Tools v6 milestones (owner 7 Nov 2026: "progress all the time, and an EXTRA boost every x levels"; replaces the
     * breakthroughs): steady growth on a curve that gives the most per level at the start ({@code shape} below 1), and
     * every {@code everyPercent} of the track an extra {@code bonusPercent} that stays (compounding). Pure.
     */
    record Growth(double shape, double everyPercent, double bonusPercent) {
        static final Growth DEFAULT = new Growth(0.7, 4, 10);
    }

    /** What a milestone raises. */
    enum Target { VALUE, CHANCE, NONE }

    record Def(Kind kind, String id, Effect effect, int max, double cost, double chance1, double chanceMax, double value1,
               double valueMax, int cooldownSeconds, int unlockLevel, int unlockPrestige, Growth growth) {

        Def(Kind kind, String id, Effect effect, int max, double cost, double chance1, double chanceMax, double value1,
            double valueMax, int cooldownSeconds, int unlockLevel, int unlockPrestige) {
            this(kind, id, effect, max, cost, chance1, chanceMax, value1, valueMax, cooldownSeconds, unlockLevel, unlockPrestige, Growth.DEFAULT);
        }

        /** What this enchant's milestones raise: the amount, the chance (area and timed enchants), or nothing. */
        Target target() {
            return switch (effect) {
                case AREA, STORM, FRENZY, FEVER, BASKET -> Target.CHANCE;
                case KEY, PITY, CHAIN -> Target.NONE;
                default -> Target.VALUE;
            };
        }

        /** Levels between two milestones (4% of the track: 100 of 2,500, 40 of 1,000, 20 of 500). */
        int step() {
            return (int) Math.max(1, Math.round(max * growth.everyPercent() / 100.0));
        }

        /** Milestones reached at a level. */
        int milestones(int level) {
            return level <= 0 ? 0 : Math.min(level, max) / step();
        }

        /** The milestone multiplier at a level (1 before the first). Pure. */
        double boost(int level) {
            return target() == Target.NONE ? 1 : Math.pow(1 + growth.bonusPercent() / 100.0, milestones(level));
        }

        /** The level of the next milestone above {@code level} (empty at the top or when milestones raise nothing). */
        java.util.OptionalInt nextMilestone(int level) {
            if (target() == Target.NONE) {
                return java.util.OptionalInt.empty();
            }
            int n = (milestones(level) + 1) * step();
            return n <= max ? java.util.OptionalInt.of(n) : java.util.OptionalInt.empty();
        }

        /** How many milestones going from {@code from} to {@code to} reaches. */
        int crossed(int from, int to) {
            return target() == Target.NONE ? 0 : Math.max(0, milestones(to) - milestones(from));
        }

        boolean crosses(int from, int to) {
            return crossed(from, to) > 0;
        }

        /** A rare find (Lucky Streak's pity counts these): a key, or a proc that is at most 0.1% at max. */
        boolean rare() {
            return effect == Effect.KEY || (!effect.passive() && chanceMax <= 0.1);
        }

        /** Proc chance in percent at a level (0 below level 1): even steps, then the chance milestones. */
        double chance(int level) {
            return level <= 0 ? 0 : Math.min(100, lerp(chance1, chanceMax, level, max) * (target() == Target.CHANCE ? boost(level) : 1));
        }

        /**
         * The effect's amount at a level. Tools v6 (owner 7 Nov 2026): it <b>compounds</b> from level 1 to max (every level
         * the same few tenths of a percent more than the last), so the higher it gets the better each level is.
         */
        double value(int level) {
            return level <= 0 ? 0 : steady(level) * (target() == Target.VALUE ? boost(level) : 1);
        }

        /** The steady part: from value1 to valueMax on the fast-start curve ({@code growth.shape}). */
        double steady(int level) {
            return curve(value1, valueMax, level, max, growth.shape());
        }

        /** Shards for one level ({@code level} >= 1). */
        long priceOf(int level) {
            return Math.max(1, Math.round(cost * Math.pow(Math.max(1, level), 1.2)));
        }

        /** Shards for {@code n} levels after {@code have}. */
        long price(int have, int n) {
            long sum = 0;
            for (int l = have + 1; l <= have + n; l++) {
                sum += priceOf(l);
            }
            return sum;
        }

        /** Levels that can still be bought, at most {@code want}. */
        int buyable(int have, int want) {
            return Math.max(0, Math.min(want, max - have));
        }

        /** The storage key of this enchant: {@code pickaxe:shard_burst}. */
        String key() {
            return kind.id() + ":" + id;
        }
    }

    private final Map<Kind, List<Def>> byKind;

    private GrindEnchants(Map<Kind, List<Def>> byKind) {
        this.byKind = byKind;
    }

    /**
     * Tools v6 round 3: from {@code a} at level 1 to {@code b} at {@code max} along {@code t^shape} (t = how far along the
     * track): shape 1 = even steps, below 1 = most per level at the start and still growing to the end. Pure.
     */
    static double curve(double a, double b, int level, int max, double shape) {
        if (max <= 1) {
            return b;
        }
        int l = Math.max(1, Math.min(level, max));
        double t = (l - 1) / (double) (max - 1);
        return a + (b - a) * Math.pow(t, Math.max(0.05, shape));
    }

    /** Compound (geometric) from {@code a} at level 1 to {@code b} at {@code max}; linear when an end is 0 or below. Pure. */
    static double grow(double a, double b, int level, int max) {
        if (a <= 0 || b <= 0 || a == b) {
            return lerp(a, b, level, max);
        }
        if (max <= 1) {
            return b;
        }
        int l = Math.max(1, Math.min(level, max));
        return a * Math.pow(b / a, (l - 1) / (double) (max - 1));
    }

    /** Linear from {@code a} at level 1 to {@code b} at {@code max}. Pure. */
    static double lerp(double a, double b, int level, int max) {
        if (max <= 1) {
            return b;
        }
        int l = Math.max(1, Math.min(level, max));
        return a + (b - a) * (l - 1) / (double) (max - 1);
    }

    /** The effect of an enchant id (fixed by id; the numbers come from the config). */
    static Optional<Effect> effectOf(String id) {
        return Optional.ofNullable(switch (id) {
            case "blast", "explode", "lightning", "row_reaper", "meteor", "tornado", "nuke", "meteor_shower" -> Effect.AREA;
            case "rich_vein", "bountiful", "big_catch" -> Effect.EXTRA_ITEMS;
            case "shard_burst", "shard_jackpot" -> Effect.SHARDS;
            case "cash_vein", "cash_crop", "golden_crop", "golden_fish" -> Effect.CASH;
            case "quick_study", "motherlode" -> Effect.PROGRESS;
            case "scholar" -> Effect.SKILL_XP;
            case "keyfinder" -> Effect.KEY;
            case "shard_echo" -> Effect.ECHO;
            case "compound_interest" -> Effect.STREAK_SHARDS;
            case "combo" -> Effect.STREAK_XP;
            case "geode", "harvest_basket", "treasure_chest" -> Effect.BASKET;
            case "lucky_streak" -> Effect.PITY;
            case "shard_storm" -> Effect.STORM;
            case "frenzy" -> Effect.FRENZY;
            case "golden_touch" -> Effect.GOLDEN;
            case "duplicator" -> Effect.DUPLICATE;
            case "xp_surge" -> Effect.XP_SURGE;
            case "crystal_find" -> Effect.CRYSTALS;
            case "booster_find" -> Effect.FEVER;
            case "chain_reaction" -> Effect.CHAIN;
            case "shard_rain" -> Effect.RAIN;
            default -> null;
        });
    }

    static GrindEnchants parse(List<String> lines, Consumer<String> problems) {
        return parse(lines, Growth.DEFAULT, problems);
    }

    static GrindEnchants parse(List<String> lines, Growth growth, Consumer<String> problems) {
        Map<Kind, List<Def>> out = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) {
            out.put(k, new ArrayList<>());
        }
        for (String raw : lines) {
            String[] p = raw.strip().split(":");
            if (p.length != 9 && p.length != 11) {
                problems.accept("enchants.list: '" + raw + "' needs kind:id:max:cost:chance1:chanceMax:value1:valueMax:cooldown"
                        + "[:unlockLevel:unlockPrestige] (skipped)");
                continue;
            }
            Optional<Kind> kind = Kind.of(p[0].strip());
            String id = p[1].strip().toLowerCase(Locale.ROOT);
            Optional<Effect> effect = effectOf(id);
            if (kind.isEmpty() || effect.isEmpty()) {
                problems.accept("enchants.list: '" + raw + "' has an unknown tool or enchant (skipped)");
                continue;
            }
            try {
                Def d = new Def(kind.get(), id, effect.get(), Integer.parseInt(p[2].strip()), Double.parseDouble(p[3].strip()),
                        Double.parseDouble(p[4].strip()), Double.parseDouble(p[5].strip()), Double.parseDouble(p[6].strip()),
                        Double.parseDouble(p[7].strip()), Integer.parseInt(p[8].strip()),
                        p.length == 11 ? Math.max(0, Integer.parseInt(p[9].strip())) : 0,
                        p.length == 11 ? Math.max(0, Integer.parseInt(p[10].strip())) : 0, growth);
                if (d.max() < 1 || d.cost() <= 0) {
                    problems.accept("enchants.list: '" + raw + "' needs max >= 1 and cost > 0 (skipped)");
                    continue;
                }
                if (out.get(d.kind()).stream().anyMatch(o -> o.id().equals(d.id()))) {
                    problems.accept("enchants.list: '" + id + "' is listed twice for the " + d.kind().id() + " (the first is used)");
                    continue;
                }
                out.get(d.kind()).add(d);
            } catch (NumberFormatException e) {
                problems.accept("enchants.list: '" + raw + "' has a number that is not a number (skipped)");
            }
        }
        out.replaceAll((k, v) -> List.copyOf(v));
        return new GrindEnchants(out);
    }

    /**
     * The highest level an enchant can have at a tool prestige (plans/TOOLS_V3.md: the cap rises by {@code perPrestige}
     * every prestige, from {@code base} at prestige 0, never above the enchant's max). Pure.
     */
    static int cap(int max, int prestige, int base, int perPrestige) {
        return cap(max, prestige, base, perPrestige, 0);
    }

    /**
     * Tools v6 (owner 7 Nov 2026: "they need to prestige A LOT"): the level cap grows with the tool's <b>lifetime</b> levels
     * (prestige x max level + level), faster the higher you get: {@code max x (lifetime / fullAt)^exponent}, at least
     * {@code base}, plus the island's Mastery levels. {@code fullAt} lifetime levels (100 = prestige 2) open the whole track. Pure.
     */
    static int capByLevels(int max, int lifetime, int base, int fullAt, double exponent, double mastery) {
        double f = Math.min(1, Math.max(0, lifetime) / (double) Math.max(1, fullAt));
        long c = Math.max((long) Math.max(1, base), (long) Math.ceil(max * Math.pow(f, Math.max(0.1, exponent)) - 1e-9))
                + (long) Math.max(0, mastery);
        return (int) Math.min(max, c);
    }

    /** {@link #cap(int, int, int, int)} plus {@code mastery} levels from the island's Farm or Mine Mastery. Pure. */
    static int cap(int max, int prestige, int base, int perPrestige, double mastery) {
        long c = (long) Math.max(1, base) + (long) Math.max(0, perPrestige) * Math.max(0, prestige) + (long) Math.max(0, mastery);
        return (int) Math.min(max, c);
    }

    /** Streak bonus in percent: {@code step} per block in the streak, at most {@code capPercent}. Pure. */
    static double streak(int blocks, double step, double capPercent) {
        return Math.max(0, Math.min(capPercent, blocks * step));
    }

    List<Def> of(Kind kind) {
        return byKind.getOrDefault(kind, List.of());
    }

    Optional<Def> get(Kind kind, String id) {
        return of(kind).stream().filter(d -> d.id().equals(id)).findFirst();
    }
}
