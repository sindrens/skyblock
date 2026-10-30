package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@link CrateKeys} for other modules (AFK pool now; votes, calendar, quests, pass later). Whether the keys are bound or
 * tradeable follows the source ({@code keys.bound-sources} / {@code keys.tradeable-sources}; unknown = bound). Every
 * grant is one audit row {@code crates/key_give} with the source as reason.
 */
final class CrateKeysService implements CrateKeys {

    /** Most keys one call can give (a bug or a bad config can't flood a profile). */
    static final int MAX_PER_CALL = 1_000;

    private final CratesModule m;

    CrateKeysService(CratesModule module) {
        this.m = module;
    }

    @Override
    public boolean exists(String crateId) {
        return m.catalog().crate(crateId).isPresent();
    }

    @Override
    public CompletableFuture<Boolean> giveKeys(UUID profileKey, String crateId, int amount, String reason, Actor actor) {
        if (profileKey == null || amount < 1 || amount > MAX_PER_CALL) {
            return CompletableFuture.completedFuture(false);
        }
        CrateCatalog.Crate crate = m.catalog().crate(crateId).orElse(null);
        if (crate == null) {
            return CompletableFuture.completedFuture(false);
        }
        return grant(profileKey, crate, amount, m.settings.bound(reason), reason, actor, null, null);
    }

    /**
     * Adds keys with an explicit bound/tradeable choice (staff give, P10-41) or the source's rule ({@link #giveKeys}).
     * One transaction, one audit row {@code crates/key_give} (actor, reason, bound), then open crate menus refresh.
     *
     * @param owner     the player the profile belongs to (null = looked up; only reliable for online players)
     * @param ownerName their name for the audit row (null = none)
     */
    CompletableFuture<Boolean> grant(UUID profileKey, CrateCatalog.Crate crate, int amount, boolean bound, String reason, Actor actor,
                                     UUID owner, String ownerName) {
        if (profileKey == null || crate == null || amount < 1 || amount > MAX_PER_CALL) {
            return CompletableFuture.completedFuture(false);
        }
        long now = System.currentTimeMillis();
        CompletableFuture<Boolean> done = m.core.database().transaction(c -> {
            CrateStore.addKeys(c, profileKey, crate.id(), bound ? amount : 0, bound ? 0 : amount, now);
            return true;
        });
        return done.handle((ok, error) -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Crates: could not give " + amount + " " + crate.id() + " key(s) to profile "
                        + profileKey + " (" + reason + "); the caller keeps them and tries again", Database.cause(error));
                return false;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("crate", crate.id());
            d.put("reason", reason == null ? "" : reason);
            d.put("bound", bound);
            d.put("profile", profileKey.toString());
            m.audit("key_give", actor == null ? Actor.SYSTEM : actor, owner != null ? owner : m.profiles().ownerOf(profileKey), ownerName,
                    (long) amount, d);
            m.main().execute(() -> m.changed(profileKey));
            return true;
        });
    }
}
