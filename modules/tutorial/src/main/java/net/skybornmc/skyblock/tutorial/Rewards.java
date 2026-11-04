package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Chapter reward lines (pure, unit-tested): {@code money 500}, {@code crystals 20}, {@code shards 100} (Farming Shards),
 * {@code mining_shards 100}, {@code key <crate> <n>}, {@code tag <id>}. Owner 4 Nov 2026 (plan T answer 4): the chapter
 * rewards as planned, so the farm and mine chapters pay their own shard type. Paid once per account.
 */
final class Rewards {

    enum Kind { CURRENCY, KEY, TAG }

    /**
     * One reward.
     *
     * @param currency for {@link Kind#CURRENCY}
     * @param id       crate id ({@link Kind#KEY}) or tag id ({@link Kind#TAG})
     */
    record Reward(Kind kind, Currency currency, String id, long amount) {
    }

    /** Currencies a chapter may pay: never credits (bought or earned) from a tutorial. */
    static boolean payable(Currency c) {
        return c != null && !c.isCredits();
    }

    private Rewards() {
    }

    static List<Reward> parse(List<String> lines, Consumer<String> warn) {
        List<Reward> out = new ArrayList<>();
        for (String raw : lines) {
            parse(raw).ifPresentOrElse(out::add, () -> warn.accept("tutorial.yml: reward '" + raw
                    + "' is not 'money <n>', 'crystals <n>', 'shards <n>', 'mining_shards <n>', 'key <crate> <n>' or 'tag <id>' (skipped)"));
        }
        return out;
    }

    static Optional<Reward> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] p = raw.strip().split("\\s+");
        String kind = p[0].toLowerCase(Locale.ROOT);
        try {
            if (kind.equals("key") && p.length >= 2 && p.length <= 3) {
                long n = p.length == 3 ? Long.parseLong(p[2]) : 1;
                return n > 0 && n <= 64 ? Optional.of(new Reward(Kind.KEY, null, p[1].toLowerCase(Locale.ROOT), n)) : Optional.empty();
            }
            if (kind.equals("tag") && p.length == 2) {
                return Optional.of(new Reward(Kind.TAG, null, p[1].toLowerCase(Locale.ROOT), 1));
            }
            Optional<Currency> c = Currency.byCanonicalKey(kind);
            if (c.isPresent() && payable(c.get()) && p.length == 2) {
                long n = Long.parseLong(p[1]);
                return n > 0 ? Optional.of(new Reward(Kind.CURRENCY, c.get(), null, n)) : Optional.empty();
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
