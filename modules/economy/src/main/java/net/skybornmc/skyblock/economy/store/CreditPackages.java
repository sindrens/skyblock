package net.skybornmc.skyblock.economy.store;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The credit packages sold on Tebex ({@code economy.yml credit-store.packages}, CREDIT-SHOP 5.1), one line each:
 * {@code <id>=<credits>|<usd>}, e.g. {@code c2700=2700|25}. Pure. A bad line is skipped with one problem line; the
 * amount granted always comes from here, never from the command text.
 */
public final class CreditPackages {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    /** One package: {@code credits} bought credits for {@code usdCents} (receipt only). */
    public record Pack(String id, long credits, int usdCents) {
    }

    private final Map<String, Pack> packs;

    private CreditPackages(Map<String, Pack> packs) {
        this.packs = packs;
    }

    public static CreditPackages parse(List<String> lines, Consumer<String> problems) {
        Map<String, Pack> out = new LinkedHashMap<>();
        for (String line : lines == null ? List.<String>of() : lines) {
            try {
                Pack pack = parseLine(line);
                if (out.putIfAbsent(pack.id(), pack) != null) {
                    problems.accept("economy.yml credit-store.packages: '" + pack.id() + "' is listed twice; the second one is skipped.");
                }
            } catch (IllegalArgumentException e) {
                problems.accept("economy.yml credit-store.packages: '" + line + "' skipped: " + e.getMessage());
            }
        }
        return new CreditPackages(Map.copyOf(out));
    }

    static Pack parseLine(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("empty line");
        }
        int eq = line.indexOf('=');
        int bar = line.indexOf('|');
        if (eq <= 0 || bar < eq) {
            throw new IllegalArgumentException("needs '<id>=<credits>|<usd>'");
        }
        String id = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("the id must be a-z, 0-9 and _ (at most 32)");
        }
        long credits;
        try {
            credits = Long.parseLong(line.substring(eq + 1, bar).trim().replace("_", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("credits must be a whole number");
        }
        if (credits < 1) {
            throw new IllegalArgumentException("credits must be at least 1");
        }
        int cents;
        try {
            BigDecimal usd = new BigDecimal(line.substring(bar + 1).trim());
            if (usd.signum() < 0 || usd.scale() > 2) {
                throw new NumberFormatException();
            }
            cents = usd.movePointRight(2).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("usd must be a price like 25 or 4.99");
        }
        return new Pack(id, credits, cents);
    }

    public Optional<Pack> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(packs.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Collection<String> ids() {
        return packs.keySet().stream().sorted().toList();
    }

    public int size() {
        return packs.size();
    }
}
