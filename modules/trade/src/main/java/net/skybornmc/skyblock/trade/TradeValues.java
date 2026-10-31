package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Owner R3-38: the auction-house part of the trade value hints. A median price per item is only shown when at least
 * {@code value-hints.ah-min-sellers} different sellers and {@code ah-min-buyers} different buyers sold it recently, and
 * sales between the same person or two accounts sharing an address never count (a player can't pump his own item's
 * value with an alt). Reads the auction house's sold listings and the social module's IP history, read only, off the
 * main thread, cached per item.
 */
final class TradeValues {

    /** One sold listing: profile keys of both sides and the price per item. */
    record Sale(UUID seller, UUID buyer, long unitPrice) {
    }

    /** A cached median (empty = not enough independent sales). */
    record Median(OptionalLong price, int sales, long loadedAt) {
    }

    private final Database db;
    private final LongSupplier clock;
    private final Function<UUID, UUID> ownerOf;
    private final Map<String, Median> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();

    TradeValues(Database db, LongSupplier clock, Function<UUID, UUID> ownerOf) {
        this.db = db;
        this.clock = clock;
        this.ownerOf = ownerOf;
    }

    /**
     * The median unit price of independent sales, pure: pairs where {@code samePerson} holds are dropped first; then the
     * remaining sales need enough different sellers and buyers. Middle value (the lower one of the two middle values
     * for an even count, so a hint never rounds a price up).
     */
    static OptionalLong median(List<Sale> sales, BiPredicate<UUID, UUID> samePerson, int minSellers, int minBuyers) {
        List<Long> prices = new ArrayList<>();
        Set<UUID> sellers = new HashSet<>();
        Set<UUID> buyers = new HashSet<>();
        for (Sale s : sales) {
            if (s.seller() == null || s.buyer() == null || s.unitPrice() <= 0 || samePerson.test(s.seller(), s.buyer())) {
                continue;
            }
            prices.add(s.unitPrice());
            sellers.add(s.seller());
            buyers.add(s.buyer());
        }
        if (prices.isEmpty() || sellers.size() < minSellers || buyers.size() < minBuyers) {
            return OptionalLong.empty();
        }
        prices.sort(Long::compare);
        return OptionalLong.of(prices.get((prices.size() - 1) / 2));
    }

    /** The cached median of an item key, or null when not loaded yet (or older than {@code maxAgeMillis}). */
    Median cached(String itemKey, long maxAgeMillis) {
        Median m = cache.get(itemKey);
        return m == null || clock.getAsLong() - m.loadedAt() > maxAgeMillis ? null : m;
    }

    /**
     * Loads the median of an item key in the background if it is missing or stale; {@code done} runs (off the main thread)
     * when a fresh value arrived. Never more than one load per item at a time.
     */
    void load(String itemKey, TradeConfig cfg, Runnable done) {
        if (itemKey == null || cached(itemKey, cfg.valueCacheMinutes * 60_000L) != null || !loading.add(itemKey)) {
            return;
        }
        long since = clock.getAsLong() - cfg.valueAhDays * 86_400_000L;
        int minSellers = cfg.valueAhMinSellers;
        int minBuyers = cfg.valueAhMinBuyers;
        int limit = cfg.valueAhMaxSales;
        db.async(c -> {
            List<Sale> sales = sales(c, itemKey, since, limit);
            Map<UUID, Set<String>> ips = addresses(c, owners(sales));
            OptionalLong price = median(sales, (a, b) -> samePerson(a, b, ips), minSellers, minBuyers);
            return new Median(price, sales.size(), clock.getAsLong());
        }).whenComplete((median, error) -> {
            loading.remove(itemKey);
            if (error == null && median != null) {
                cache.put(itemKey, median);
                done.run();
            }
        });
    }

    private boolean samePerson(UUID sellerProfile, UUID buyerProfile, Map<UUID, Set<String>> ips) {
        UUID a = owner(sellerProfile);
        UUID b = owner(buyerProfile);
        if (a.equals(b)) {
            return true;
        }
        Set<String> mine = ips.getOrDefault(a, Set.of());
        for (String ip : ips.getOrDefault(b, Set.of())) {
            if (mine.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    private UUID owner(UUID profile) {
        UUID owner = ownerOf.apply(profile);
        return owner == null ? profile : owner;
    }

    private Set<UUID> owners(List<Sale> sales) {
        Set<UUID> out = new HashSet<>();
        for (Sale s : sales) {
            out.add(owner(s.seller()));
            out.add(owner(s.buyer()));
        }
        return out;
    }

    /** Recent money sales of an item on the auction house (empty when the auction module is not installed). */
    private static List<Sale> sales(Connection c, String itemKey, long since, int limit) {
        try {
            return Sql.query(c, "SELECT seller, buyer, price, amount FROM ah_listings WHERE status = ? AND item_key = ? "
                            + "AND currency = ? AND ended_at >= ? AND buyer IS NOT NULL ORDER BY ended_at DESC LIMIT " + Math.max(1, limit),
                    rs -> new Sale(Sql.uuid(rs, "seller"), Sql.uuid(rs, "buyer"),
                            rs.getLong("price") / Math.max(1, rs.getInt("amount"))), "sold", itemKey, "money", since);
        } catch (SQLException e) {
            return List.of();
        }
    }

    /** Every address each account ever joined from (social module history; empty when it is not installed). */
    private static Map<UUID, Set<String>> addresses(Connection c, Collection<UUID> players) {
        Map<UUID, Set<String>> out = new HashMap<>();
        if (players.isEmpty()) {
            return out;
        }
        List<Object> params = new ArrayList<>(players);
        String in = "?,".repeat(players.size() - 1) + "?";
        try {
            for (Object[] row : Sql.query(c, "SELECT uuid, ip FROM social_player_ips WHERE uuid IN (" + in + ")",
                    rs -> new Object[]{Sql.uuid(rs, "uuid"), rs.getString("ip")}, params.toArray())) {
                out.computeIfAbsent((UUID) row[0], k -> new HashSet<>()).add((String) row[1]);
            }
        } catch (SQLException e) {
            return Map.of();
        }
        return out;
    }
}
