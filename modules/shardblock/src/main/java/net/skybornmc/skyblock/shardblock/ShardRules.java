package net.skybornmc.skyblock.shardblock;

import net.skybornmc.skyblock.common.economy.Currency;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * The shard block numbers (ECON-SHARDS step 7), pure: level tables, upgrade costs, the double roll, the day key of the
 * daily cap and the cap math. Built once per (re)load from {@link ShardBlockConfig}; broken entries are reported and
 * replaced by safe values.
 */
public final class ShardRules {

    /** The two upgrade paths of a block. */
    public enum Kind {
        RESPAWN("respawn", "respawn_level"),
        DOUBLE("double", "double_level");

        private final String key;
        private final String column;

        Kind(String key, String column) {
            this.key = key;
            this.column = column;
        }

        public String key() {
            return key;
        }

        /** The database column of this level (fixed names, never user input). */
        String column() {
            return column;
        }
    }

    /** One upgrade price. */
    public record Cost(Currency currency, long amount) {
    }

    private final List<Integer> respawnSeconds;
    private final List<Double> doublePercent;
    private final List<Cost> respawnCosts;
    private final List<Cost> doubleCosts;

    ShardRules(List<Integer> respawnSeconds, List<Double> doublePercent, List<Cost> respawnCosts, List<Cost> doubleCosts) {
        this.respawnSeconds = List.copyOf(respawnSeconds);
        this.doublePercent = List.copyOf(doublePercent);
        this.respawnCosts = List.copyOf(respawnCosts);
        this.doubleCosts = List.copyOf(doubleCosts);
    }

    /** Reads the config lists; problems are reported, bad entries skipped, missing costs make the last levels unbuyable. */
    public static ShardRules of(ShardBlockConfig config, Consumer<String> problem) {
        List<Integer> seconds = new ArrayList<>();
        for (String raw : config.respawnSeconds) {
            try {
                int v = Integer.parseInt(raw.trim());
                if (v < 1) {
                    throw new NumberFormatException();
                }
                seconds.add(v);
            } catch (NumberFormatException e) {
                problem.accept("levels.respawn-seconds: '" + raw + "' is not a whole number of seconds (1 or more), skipped");
            }
        }
        if (seconds.isEmpty()) {
            seconds.add(1800);
            problem.accept("levels.respawn-seconds: empty, using 1800");
        }
        List<Double> percent = new ArrayList<>();
        for (String raw : config.doublePercent) {
            try {
                double v = Double.parseDouble(raw.trim());
                if (v < 0 || v > 100) {
                    throw new NumberFormatException();
                }
                percent.add(v);
            } catch (NumberFormatException e) {
                problem.accept("levels.double-percent: '" + raw + "' is not 0-100, skipped");
            }
        }
        if (percent.isEmpty()) {
            percent.add(0.0);
            problem.accept("levels.double-percent: empty, using 0");
        }
        List<Cost> respawn = costs("upgrades.respawn", config.respawnCosts, problem);
        List<Cost> dbl = costs("upgrades.double", config.doubleCosts, problem);
        if (respawn.size() != seconds.size() - 1) {
            problem.accept("upgrades.respawn: " + respawn.size() + " costs for " + (seconds.size() - 1) + " levels above 0 (levels without a cost can't be bought)");
        }
        if (dbl.size() != percent.size() - 1) {
            problem.accept("upgrades.double: " + dbl.size() + " costs for " + (percent.size() - 1) + " levels above 0 (levels without a cost can't be bought)");
        }
        return new ShardRules(seconds, percent, respawn, dbl);
    }

    private static List<Cost> costs(String path, List<String> raw, Consumer<String> problem) {
        List<Cost> out = new ArrayList<>();
        for (String entry : raw) {
            Cost c = parseCost(entry);
            if (c == null) {
                problem.accept(path + ": '" + entry + "' must be currency:amount (crystals, shards or money, amount 1 or more); "
                        + "that level and the ones above it can't be bought");
                break;
            }
            out.add(c);
        }
        return out;
    }

    /** {@code crystals:100} → a cost, or null. Credits are never a shard block price. */
    static Cost parseCost(String raw) {
        if (raw == null) {
            return null;
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String cur = raw.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        Currency currency = switch (cur) {
            case "crystals" -> Currency.CRYSTALS;
            case "shards" -> Currency.SHARDS;
            case "money" -> Currency.MONEY;
            default -> null;
        };
        try {
            long amount = Long.parseLong(raw.substring(colon + 1).trim());
            return currency == null || amount < 1 ? null : new Cost(currency, amount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Highest level of a path (0-based; the number of steps in its table minus one). */
    public int maxLevel(Kind kind) {
        return kind == Kind.RESPAWN ? respawnSeconds.size() - 1 : doublePercent.size() - 1;
    }

    /** Regrow seconds at a respawn level (clamped to the table). */
    public int respawnSeconds(int level) {
        return respawnSeconds.get(clamp(level, respawnSeconds.size()));
    }

    /** Double chance in % at a double level (clamped to the table). */
    public double doublePercent(int level) {
        return doublePercent.get(clamp(level, doublePercent.size()));
    }

    /** The price of {@code nextLevel} (1 = the first upgrade), or null when it can't be bought (maxed or no cost). */
    public Cost cost(Kind kind, int nextLevel) {
        List<Cost> list = kind == Kind.RESPAWN ? respawnCosts : doubleCosts;
        if (nextLevel < 1 || nextLevel > maxLevel(kind) || nextLevel - 1 >= list.size()) {
            return null;
        }
        return list.get(nextLevel - 1);
    }

    private static int clamp(int level, int size) {
        return Math.max(0, Math.min(size - 1, level));
    }

    /** Shards of one break: {@code perBreak}, or twice that with the double chance. */
    public static int rollAmount(int perBreak, double doublePercent, RandomGenerator random) {
        int base = Math.max(1, perBreak);
        return doublePercent > 0 && random.nextDouble() * 100.0 < doublePercent ? base * 2 : base;
    }

    /** The day number (days since 1970-01-01 in {@code zone}) of the daily cap. */
    public static int dayKey(Instant now, ZoneId zone) {
        return (int) LocalDate.ofInstant(now, zone).toEpochDay();
    }

    /** Shards a profile may still get today ({@code cap} 0 = no cap → {@link Integer#MAX_VALUE}). */
    public static long capRemaining(long usedToday, int cap) {
        return cap <= 0 ? Integer.MAX_VALUE : Math.max(0, cap - Math.max(0, usedToday));
    }

    /** The zone of the daily cap; a broken value falls back to Europe/Oslo. */
    public static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of("Europe/Oslo");
        }
    }
}
