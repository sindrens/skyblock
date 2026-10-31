package net.skybornmc.skyblock.pass;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Hand-outs after a committed claim, from {@code pass_grants} rows: {@code pending} → {@code sending} (one atomic update, so
 * a row is never handed out twice) → {@code delivered}.
 * <ul>
 *   <li>Keys through {@link CrateKeys} (key source {@code pass} = tradeable, P10-6; overflow keys {@code pass_overflow} =
 *       bound). Boosters through {@link BoosterService} as bound items: inventory when online on that profile (the rest to
 *       {@code /box}), else straight into that profile's box. Titles and tags through {@link Cosmetics} (account-wide).</li>
 *   <li>A missing module leaves the row pending (tried again every {@code delivery.retry-seconds} and at the next start).</li>
 *   <li>Rows still {@code sending} at start were interrupted by a crash: titles and tags (idempotent) are sent again, keys and
 *       boosters go to {@code review} for staff ({@code grant_review} in /logs). Lost, never doubled.</li>
 * </ul>
 */
final class PassGrants {

    enum Result { DELIVERED, RETRY, FAILED }

    private static final int BATCH = 200;

    private final PassModule m;
    private volatile boolean recovered;

    PassGrants(PassModule module) {
        this.m = module;
    }

    /** Hands out the rows of a claim that just committed. Main thread. */
    void handOut(List<PassStore.GrantRow> rows) {
        for (PassStore.GrantRow row : rows) {
            take(row);
        }
    }

    /** Periodic: recover interrupted rows once, hand out waiting ones, prune old data. Main thread. */
    void retry() {
        if (!recovered) {
            recovered = true;
            recover().whenCompleteAsync((v, e) -> retryPending(), m.main());
            return;
        }
        retryPending();
    }

    private void retryPending() {
        long now = System.currentTimeMillis();
        long before = now - m.settings.keepDays * 86_400_000L;
        m.core.database().async(c -> {
            PassStore.prune(c, before, before);
            return PassStore.grants(c, PassStore.PENDING, BATCH);
        }).whenCompleteAsync((rows, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Pass: could not read waiting hand-outs", Database.cause(e));
                return;
            }
            handOut(rows);
        }, m.main());
    }

    private CompletableFuture<Void> recover() {
        long now = System.currentTimeMillis();
        return m.core.database().async(c -> {
            List<PassStore.GrantRow> reviewed = new ArrayList<>();
            for (PassStore.GrantRow row : PassStore.grants(c, PassStore.SENDING, 10_000)) {
                boolean idempotent = PassStore.TITLE.equals(row.kind()) || PassStore.TAG.equals(row.kind());
                String to = idempotent ? PassStore.PENDING : PassStore.REVIEW;
                if (PassStore.move(c, row.id(), PassStore.SENDING, to, now) && !idempotent) {
                    reviewed.add(row);
                }
            }
            return reviewed;
        }).thenAcceptAsync(reviewed -> {
            for (PassStore.GrantRow row : reviewed) {
                m.audit("grant_review", Actor.SYSTEM, row.player(), null, row.amount(), details(row));
            }
            if (!reviewed.isEmpty()) {
                m.core.plugin().getLogger().warning("Pass: " + reviewed.size() + " pass reward hand-out(s) were interrupted by a stop or "
                        + "crash; they are marked 'review' (grant_review in /logs, category pass). Check the player's keys and /box and "
                        + "give what is missing by hand.");
            }
        }, m.main()).exceptionally(e -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Pass: could not check interrupted hand-outs", Database.cause(e));
            return null;
        });
    }

    private boolean available(PassStore.GrantRow row) {
        return switch (row.kind()) {
            case PassStore.KEY -> m.core.services().has(CrateKeys.class);
            case PassStore.BOOSTER -> m.core.services().has(BoosterService.class);
            case PassStore.TITLE, PassStore.TAG -> m.cosmetics().isPresent();
            default -> false;
        };
    }

    private void take(PassStore.GrantRow row) {
        if (!available(row)) {
            return; // stays pending; retried later
        }
        long now = System.currentTimeMillis();
        m.core.database().async(c -> PassStore.move(c, row.id(), PassStore.PENDING, PassStore.SENDING, now))
                .whenCompleteAsync((taken, e) -> {
                    if (e != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Pass: could not take hand-out " + row.id(), Database.cause(e));
                        return;
                    }
                    if (!Boolean.TRUE.equals(taken)) {
                        return; // someone else has it
                    }
                    CompletableFuture<Result> done;
                    try {
                        done = perform(row);
                    } catch (RuntimeException ex) {
                        done = CompletableFuture.failedFuture(ex);
                    }
                    done.whenCompleteAsync((result, error) -> finish(row, error != null ? Result.FAILED : result, error), m.main());
                }, m.main());
    }

    private void finish(PassStore.GrantRow row, Result result, Throwable error) {
        Result r = result == null ? Result.FAILED : result;
        String to = switch (r) {
            case DELIVERED -> PassStore.DELIVERED;
            case RETRY -> PassStore.PENDING;
            case FAILED -> PassStore.FAILED;
        };
        long now = System.currentTimeMillis();
        m.core.database().async(c -> PassStore.move(c, row.id(), PassStore.SENDING, to, now)).whenComplete((v, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Pass: could not mark hand-out " + row.id() + " as " + to
                        + " (it is reviewed at the next start)", Database.cause(e));
            }
        });
        if (r == Result.FAILED) {
            m.core.plugin().getLogger().log(Level.WARNING, "Pass: hand-out " + row.kind() + " " + row.ref() + " x" + row.amount()
                    + " for profile " + row.profile() + " failed; see grant_failed in /logs", error == null ? null : Database.cause(error));
            Map<String, Object> d = details(row);
            d.put("error", error == null ? "" : String.valueOf(Database.cause(error).getMessage()));
            m.audit("grant_failed", Actor.SYSTEM, row.player(), null, row.amount(), d);
        } else if (r == Result.DELIVERED && PassStore.BOOSTER.equals(row.kind())) {
            // Keys and cosmetics are logged by the crates and cosmetics modules.
            m.audit("grant_delivered", Actor.SYSTEM, row.player(), null, row.amount(), details(row));
        }
    }

    private CompletableFuture<Result> perform(PassStore.GrantRow row) {
        Actor actor = Actor.system(PassModule.ID);
        String origin = "pass:" + row.season();
        switch (row.kind()) {
            case PassStore.KEY -> {
                Optional<CrateKeys> keys = m.core.services().optional(CrateKeys.class);
                String[] ref = row.ref().split(":", 2);
                if (keys.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.RETRY);
                }
                if (!keys.get().exists(ref[0]) || row.amount() <= 0 || row.amount() > Integer.MAX_VALUE) {
                    return CompletableFuture.completedFuture(Result.FAILED);
                }
                String reason = ref.length > 1 ? ref[1] : PassClaims.KEY_REASON;
                return keys.get().giveKeys(row.profile(), ref[0], (int) row.amount(), reason, actor)
                        .thenApply(ok -> Boolean.TRUE.equals(ok) ? Result.DELIVERED : Result.RETRY);
            }
            case PassStore.TITLE, PassStore.TAG -> {
                Optional<Cosmetics> cosmetics = m.cosmetics();
                if (cosmetics.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.RETRY);
                }
                Cosmetics.Kind kind = PassStore.TITLE.equals(row.kind()) ? Cosmetics.Kind.TITLE : Cosmetics.Kind.TAG;
                return cosmetics.get().grant(row.player(), kind, row.ref(), origin).thenApply(isNew -> Result.DELIVERED);
            }
            case PassStore.BOOSTER -> {
                Optional<BoosterService> boosters = BoosterService.of(m.core.services());
                if (boosters.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.RETRY);
                }
                String[] ref = row.ref().split(":");
                Optional<BoosterType> type = ref.length == 3 ? BoosterType.byId(ref[0]) : Optional.empty();
                if (type.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.FAILED);
                }
                int percent;
                int minutes;
                try {
                    percent = Integer.parseInt(ref[1]);
                    minutes = Integer.parseInt(ref[2]);
                } catch (NumberFormatException e) {
                    return CompletableFuture.completedFuture(Result.FAILED);
                }
                List<ItemStack> items = new ArrayList<>();
                for (long i = 0; i < Math.min(64, row.amount()); i++) {
                    boosters.get().createPersonal(type.get(), percent, minutes, row.profile(), origin).ifPresent(items::add);
                }
                if (items.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.FAILED);
                }
                return deliverItems(row, items, actor);
            }
            default -> {
                return CompletableFuture.completedFuture(Result.FAILED);
            }
        }
    }

    /** Inventory (rest to /box) when online on that profile, else straight into that profile's box. Main thread. */
    private CompletableFuture<Result> deliverItems(PassStore.GrantRow row, List<ItemStack> items, Actor actor) {
        Player receiver = m.receiver(row.profile());
        if (receiver != null) {
            ItemBox.give(m.core.services(), receiver, BoxSource.REWARDS, items);
            PlayerSaves.save(receiver, "a battle pass reward");
            return CompletableFuture.completedFuture(Result.DELIVERED);
        }
        Optional<ItemBox> box = ItemBox.of(m.core.services());
        if (box.isEmpty()) {
            return CompletableFuture.completedFuture(Result.RETRY);
        }
        return box.get().deliver(row.profile(), items, BoxSource.REWARDS, actor).thenApply(d -> Result.DELIVERED);
    }

    private static Map<String, Object> details(PassStore.GrantRow row) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("grant", row.id().toString());
        d.put("profile", row.profile().toString());
        d.put("season", row.season());
        d.put("tier", row.tier());
        d.put("kind", row.kind());
        d.put("ref", row.ref());
        return d;
    }
}
