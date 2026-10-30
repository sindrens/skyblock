package net.skybornmc.skyblock.economy.sell;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The daily Hot Item (PHASE-3 §M3.4, ECONOMY_BALANCE §8: +25% for 24 h). Picked from the date alone (server time zone,
 * Europe/Oslo), so it is the same after a restart and needs no storage. It changes the price, not the sell bonus, so the
 * +300% cap never interacts with it. Pure, unit-tested.
 */
public final class HotItem {

    private HotItem() {
    }

    /**
     * Today's Hot Item among {@code keys} (sellable entries, file order), or null if there are none.
     *
     * @param categories only entries of these categories can be hot; empty = all
     * @param salt       changes the sequence ({@code sell.hot-item.salt}), so players can't predict it from the code
     */
    public static String pick(LocalDate day, List<SellCatalog.Entry> entries, List<String> categories, long salt) {
        List<String> eligible = entries.stream().filter(SellCatalog.Entry::sellable)
                .filter(e -> categories == null || categories.isEmpty()
                        || categories.stream().anyMatch(c -> c != null && c.trim().toLowerCase(Locale.ROOT).equals(e.category())))
                .map(SellCatalog.Entry::key).sorted().toList();
        if (eligible.isEmpty()) {
            return null;
        }
        long h = mix(day.toEpochDay() * 0x9E3779B97F4A7C15L ^ salt);
        return eligible.get((int) Math.floorMod(h, (long) eligible.size()));
    }

    /** MurmurHash3 finalizer: a well spread, deterministic 64-bit mix. */
    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
