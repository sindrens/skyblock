package net.skybornmc.skyblock.afk;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusSource;
import net.skybornmc.skyblock.common.profile.ProfileService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Personal sell boosters won in the AFK pool, as a {@link BonusSource} of the {@link BonusFamily#SELL} family: they add
 * up with every other sell bonus and the +300% cap applies once (owner O11). Per profile (no transfer between profiles).
 * A won booster is <b>banked</b> and starts when the player is outside the pool, so it is never wasted while AFK.
 * The cache is filled lazily from the database (the source itself never blocks).
 */
final class AfkBoosters implements BonusSource {

    private final AfkModule module;
    private final Map<UUID, AfkStore.Booster> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    AfkBoosters(AfkModule module) {
        this.module = module;
    }

    @Override
    public double value(BonusFamily family, BonusContext context) {
        if (family != BonusFamily.SELL || context == null || context.player() == null) {
            return 0;
        }
        UUID key = ProfileService.of(module.core().services()).profileKey(context.player());
        AfkStore.Booster b = cache.get(key);
        if (b == null) {
            load(key);
            return 0;
        }
        return b.active(System.currentTimeMillis()) ? b.percent() : 0;
    }

    /** The cached booster of a profile (NONE while unknown; starts a load). */
    AfkStore.Booster get(UUID profileKey) {
        AfkStore.Booster b = cache.get(profileKey);
        if (b == null) {
            load(profileKey);
            return AfkStore.Booster.NONE;
        }
        return b;
    }

    void put(UUID profileKey, AfkStore.Booster booster) {
        cache.put(profileKey, booster);
    }

    void forget(UUID profileKey) {
        cache.remove(profileKey);
    }

    void clear() {
        cache.clear();
    }

    private void load(UUID profileKey) {
        if (!loading.add(profileKey)) {
            return;
        }
        module.core().database().async(c -> module.store().booster(c, profileKey))
                .whenComplete((b, error) -> {
                    loading.remove(profileKey);
                    if (b != null) {
                        cache.putIfAbsent(profileKey, b);
                    }
                });
    }
}
