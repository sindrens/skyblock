package net.skybornmc.skyblock.blackmarket;

import net.skybornmc.skyblock.common.db.Database;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * What an online account bought in the current limit periods, for the menu ("1/2 this rotation"). Display only: the buy
 * transaction checks the real counters, so a stale cache can never let a limit pass.
 */
final class Counts {

    private final BlackMarketModule m;
    private final Map<UUID, Map<String, Integer>> cache = new ConcurrentHashMap<>();

    Counts(BlackMarketModule module) {
        this.m = module;
    }

    /** The period keys that count right now (rotation, day, month, season). */
    Set<String> periodKeys(long now) {
        Set<String> keys = new LinkedHashSet<>();
        for (OfferCatalog.Period p : OfferCatalog.Period.values()) {
            keys.add(m.rotation().periodKey(p, now, m.settings.seasonId));
        }
        return keys;
    }

    String periodKey(OfferCatalog.Offer offer, long now) {
        return m.rotation().periodKey(offer.period(), now, m.settings.seasonId);
    }

    /** Reads the account's counters (async) and refreshes its open menu. */
    void load(Player player) {
        UUID account = player.getUniqueId();
        Set<String> keys = periodKeys(System.currentTimeMillis());
        m.core.database().async(c -> BlackMarketStore.counts(c, account.toString(), keys)).whenCompleteAsync((found, error) -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Black Market: could not read the buys of " + player.getName(),
                        Database.cause(error));
                return;
            }
            if (player.isOnline()) {
                cache.put(account, new HashMap<>(found));
                m.refreshMenu(account);
            }
        }, m.main());
    }

    boolean loaded(UUID account) {
        return cache.containsKey(account);
    }

    /** Buys of this offer in its current limit period (0 while unknown). */
    int bought(UUID account, OfferCatalog.Offer offer) {
        Map<String, Integer> map = cache.get(account);
        return map == null ? 0 : map.getOrDefault(BlackMarketStore.countKey(offer.id(), periodKey(offer, System.currentTimeMillis())), 0);
    }

    /** Buys left (Integer.MAX_VALUE without a limit). */
    int left(UUID account, OfferCatalog.Offer offer) {
        return offer.limit() <= 0 ? Integer.MAX_VALUE : Math.max(0, offer.limit() - bought(account, offer));
    }

    /** A buy committed: count it locally right away (the next load reads the real value). */
    void add(UUID account, String offerId, String periodKey) {
        Map<String, Integer> map = cache.get(account);
        if (map != null) {
            map.merge(BlackMarketStore.countKey(offerId, periodKey), 1, Integer::sum);
        }
    }

    void forget(UUID account) {
        cache.remove(account);
    }

    void clear() {
        cache.clear();
    }
}
