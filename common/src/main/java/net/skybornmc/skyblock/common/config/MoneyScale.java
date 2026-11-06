package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Money x100 (FULL_PLAN §1, docs/plans/ECONOMY_SCALE.md, batch 10): every money amount is multiplied by {@link #FACTOR}
 * exactly once. Shards, crystals and credits never are. Pure (unit-tested).
 *
 * <p>Each file that holds money gets a top-level marker {@code money-scale: 100}. A file that is created now carries the
 * new defaults (already x100) and gets the marker at once, so it is never scaled. A live file without the marker is
 * scaled once, owner-edited values included, then marked. {@link Rule}s say how one value is scaled: a number, a list of
 * numbers, one field of a {@code "a:b:c"} line, or amounts written as {@code 20k} / {@code 1.5M}.</p>
 */
public final class MoneyScale {

    /** The one factor. */
    public static final long FACTOR = 100;
    /** The marker key at the top of every file that holds money. */
    public static final String KEY = "money-scale";

    private MoneyScale() {
    }

    /** How one value is scaled. Returns the value unchanged when it can't be read (the caller logs nothing then). */
    @FunctionalInterface
    public interface Rule {
        Object apply(Object raw, long factor);
    }

    /** A plain number (int, long, double, or a number written as text). */
    public static Rule number() {
        return MoneyScale::scaleNumber;
    }

    /** A list of numbers (or number texts). */
    public static Rule numbers() {
        return (raw, f) -> {
            if (!(raw instanceof List<?> list)) {
                return scaleNumber(raw, f);
            }
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(scaleNumber(o, f));
            }
            return out;
        };
    }

    /**
     * A list of lines where one field is money: {@code fields(1, ":")} on {@code "0:20k"} scales {@code 20k}; whitespace
     * as separator ({@code " "}) splits on any run of spaces. A single line (not a list) works too. Negative amounts
     * ({@code -1} = no limit) stay as they are.
     */
    public static Rule field(int index, String separator) {
        return (raw, f) -> {
            if (raw instanceof List<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object o : list) {
                    out.add(o instanceof String s ? scaleField(s, index, separator, f) : o);
                }
                return out;
            }
            return raw instanceof String s ? scaleField(s, index, separator, f) : raw;
        };
    }

    /** Lines of a list that match {@code when} get {@code rule}; the others stay (e.g. only {@code money:N} lines). */
    public static Rule onlyLines(Predicate<String> when, Rule rule) {
        return (raw, f) -> {
            if (!(raw instanceof List<?> list)) {
                return raw instanceof String s && when.test(s) ? rule.apply(s, f) : raw;
            }
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(o instanceof String s && when.test(s) ? rule.apply(s, f) : o);
            }
            return out;
        };
    }

    /** Scales field {@code index} of {@code line} (fields keep their text, spacing of a whitespace split is normalised). */
    static String scaleField(String line, int index, String separator, long factor) {
        boolean spaces = separator.isBlank();
        String[] parts = spaces ? line.strip().split("\\s+") : line.split(java.util.regex.Pattern.quote(separator), -1);
        if (index < 0 || index >= parts.length) {
            return line;
        }
        String scaled = scaleAmountText(parts[index], factor);
        if (scaled == null) {
            return line;
        }
        parts[index] = scaled;
        return String.join(spaces ? " " : separator, parts);
    }

    /** {@code 20k} → {@code 2M}, {@code 1500} → {@code 150000}, {@code -1} stays; null when it is not an amount. */
    static String scaleAmountText(String raw, long factor) {
        String t = raw.strip();
        if (t.isEmpty()) {
            return null;
        }
        String lead = raw.substring(0, raw.indexOf(t.charAt(0)));
        String trail = raw.substring(raw.indexOf(t.charAt(0)) + t.length());
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        long unit = switch (last) {
            case 'k' -> 1_000L;
            case 'm' -> 1_000_000L;
            case 'b' -> 1_000_000_000L;
            case 't' -> 1_000_000_000_000L;
            default -> 1L;
        };
        String digits = unit == 1 ? t : t.substring(0, t.length() - 1);
        BigDecimal value;
        try {
            value = new BigDecimal(digits.replace("_", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (value.signum() < 0) {
            return raw; // -1 = "no limit"
        }
        BigDecimal scaled = value.multiply(BigDecimal.valueOf(unit)).multiply(BigDecimal.valueOf(factor));
        return lead + compact(scaled, unit != 1) + trail;
    }

    /** Writes an amount back: with a suffix when the input had one ({@code 2M}), else as a plain number. */
    private static String compact(BigDecimal v, boolean suffix) {
        if (suffix) {
            long[] units = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};
            String[] names = {"T", "B", "M", "k"};
            for (int i = 0; i < units.length; i++) {
                BigDecimal u = BigDecimal.valueOf(units[i]);
                if (v.compareTo(u) >= 0) {
                    return v.divide(u).stripTrailingZeros().toPlainString() + names[i];
                }
            }
        }
        return v.stripTrailingZeros().toPlainString();
    }

    static Object scaleNumber(Object raw, long factor) {
        if (raw instanceof Integer i) {
            long v = (long) i * factor;
            return v <= Integer.MAX_VALUE && v >= Integer.MIN_VALUE ? (Object) (int) v : (Object) v;
        }
        if (raw instanceof Long l) {
            return Math.multiplyExact(l, factor);
        }
        if (raw instanceof Double d) {
            return BigDecimal.valueOf(d).multiply(BigDecimal.valueOf(factor)).stripTrailingZeros().doubleValue();
        }
        if (raw instanceof Float d) {
            return (double) d * factor;
        }
        if (raw instanceof String s) {
            String scaled = scaleAmountText(s, factor);
            return scaled == null ? raw : scaled;
        }
        return raw;
    }

    /** The scale a file is at: the marker, or 1 for an existing file without it. */
    public static long fileScale(ConfigurationSection yaml) {
        long v = yaml.getLong(KEY, 1);
        return v <= 0 ? 1 : v;
    }

    /**
     * Applies {@code rule} to the value at {@code path} (if present), multiplying by {@code factor}. Returns a log line
     * ({@code path: old -> new}) when it changed, else null.
     */
    public static String apply(ConfigurationSection yaml, String path, Rule rule, long factor) {
        if (!yaml.contains(path) || yaml.isConfigurationSection(path)) {
            return null;
        }
        Object before = yaml.get(path);
        Object after = rule.apply(before, factor);
        if (after == null || after.equals(before)) {
            return null;
        }
        yaml.set(path, after);
        return path + ": " + before + " -> " + after;
    }

    /** Lowercase helper for line predicates. */
    public static Predicate<String> startsWith(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return s -> s.strip().toLowerCase(Locale.ROOT).startsWith(p);
    }
}
