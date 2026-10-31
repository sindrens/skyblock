package net.skybornmc.skyblock.blackmarket;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * The live rotation (PHASE-10 M10.11): which offers are in stock right now and how much shared stock is left. The clock
 * is checked every {@code rotation.check-seconds}; a new rotation is picked (deterministic per rotation id) and stored
 * once, then announced. Main thread, except the database work.
 */
final class Market {

    static final long UNSET = Long.MIN_VALUE;

    private final BlackMarketModule m;
    private volatile long currentId = UNSET;
    private volatile List<BlackMarketStore.RotationRow> rows = List.of();
    private volatile boolean loading;

    Market(BlackMarketModule module) {
        this.m = module;
    }

    long currentId() {
        return currentId;
    }

    /** True once the current rotation is known. */
    boolean ready() {
        return currentId != UNSET && currentId == m.rotation().id(System.currentTimeMillis());
    }

    List<BlackMarketStore.RotationRow> rows() {
        return rows;
    }

    /** The row of a rotating offer in the current rotation. */
    Optional<BlackMarketStore.RotationRow> row(String offerId) {
        return rows.stream().filter(r -> r.offerId().equals(offerId)).findFirst();
    }

    /** The headline row (slot group order: the headline is first when there is one). */
    Optional<BlackMarketStore.RotationRow> headline() {
        return rows.stream().filter(r -> m.catalog().get(r.offerId()).map(o -> o.slot() == OfferCatalog.Slot.HEADLINE).orElse(false))
                .findFirst();
    }

    /** Clock check (repeating task and menu opens). */
    void tick() {
        long id = m.rotation().id(System.currentTimeMillis());
        if (id != currentId && !loading) {
            load(id);
        }
    }

    /** Forget the cached rotation (reload: the clock settings may have changed). */
    void reset() {
        currentId = UNSET;
        rows = List.of();
    }

    private void load(long id) {
        loading = true;
        List<Rotation.Pick> picks = m.picks(id, 0L);
        long start = m.rotation().start(id);
        m.core.database().transaction(c -> BlackMarketStore.ensureRotation(c, id, picks, start)).whenCompleteAsync((stored, error) -> {
            loading = false;
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Black Market: could not load rotation " + id, Database.cause(error));
                return;
            }
            boolean rotated = currentId != UNSET && id > currentId;
            currentId = id;
            rows = stored;
            if (rotated) {
                rotated(stored, Actor.SYSTEM, "rotation");
            }
            m.refreshMenus();
        }, m.main());
    }

    /** Reads the shared stock again (after a buy). */
    void refreshStock() {
        long id = currentId;
        if (id == UNSET) {
            return;
        }
        m.core.database().async(c -> BlackMarketStore.rotation(c, id)).whenCompleteAsync((stored, error) -> {
            if (error == null && id == currentId) {
                rows = stored;
                m.refreshMenus();
            }
        }, m.main());
    }

    /** Staff reroll: new picks for the current rotation (a fresh salt), stored and announced. */
    CompletableFuture<List<BlackMarketStore.RotationRow>> reroll(Actor actor) {
        long id = m.rotation().id(System.currentTimeMillis());
        List<Rotation.Pick> picks = m.picks(id, System.nanoTime());
        long start = m.rotation().start(id);
        return m.core.database().transaction(c -> BlackMarketStore.replaceRotation(c, id, picks, start)).thenApplyAsync(stored -> {
            currentId = id;
            rows = stored;
            rotated(stored, actor, "reroll");
            m.refreshMenus();
            return stored;
        }, m.main());
    }

    /** Audit row and the chat line with the new headline. */
    private void rotated(List<BlackMarketStore.RotationRow> stored, Actor actor, String action) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("rotation", currentId);
        d.put("offers", String.join(", ", stored.stream().map(BlackMarketStore.RotationRow::offerId).toList()));
        m.core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.BLACKMARKET, action, actor, String.valueOf(currentId), null,
                (long) stored.size(), null, d);
        if (!m.settings.enabled || !m.settings.announce) {
            return;
        }
        headline().flatMap(r -> m.catalog().get(r.offerId())).ifPresent(offer -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission(BlackMarketModule.PERM_USE)) {
                    m.msg.send(p, "rotation.announce", Msg.c("offer", m.offerName(p, offer)), Msg.p("price", m.shards(offer.price())));
                }
            }
        });
    }
}
