package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Hand-outs of milestone rewards from {@code collection_grants} rows: {@code pending} → {@code sending} (one atomic
 * update, so a row is never handed out twice) → {@code delivered}. Same pattern as the daily rewards:
 * <ul>
 *   <li>A module that is missing right now (crates, cosmetics, wardrobe off) leaves the row {@code pending}; it is tried
 *       again every {@code delivery.retry-seconds} and at the next start.</li>
 *   <li>Rows still {@code sending} at start were cut off by a crash: titles (idempotent) are granted again, keys and set
 *       tokens go to {@code review} for staff ({@code grant_review} in /logs). Lost, never doubled.</li>
 * </ul>
 */
final class Grants {

    enum Result { DELIVERED, RETRY, FAILED }

    private static final int BATCH = 200;

    private final CollectionModule m;
    private volatile boolean recovered;

    Grants(CollectionModule module) {
        this.m = module;
    }

    /** Hands out the rows of a milestone that just committed. Main thread. */
    void handOut(List<CollectionStore.GrantRow> rows) {
        rows.forEach(this::take);
    }

    /** Periodic: recover interrupted rows once, hand out waiting ones, prune old delivered rows. Main thread. */
    void retry() {
        if (!recovered) {
            recovered = true;
            recover().whenCompleteAsync((v, e) -> retryPending(), m.main());
            return;
        }
        retryPending();
    }

    private void retryPending() {
        long keepBefore = System.currentTimeMillis() - m.settings.keepDays * 86_400_000L;
        m.core.database().async(c -> {
            CollectionStore.pruneDelivered(c, keepBefore);
            return CollectionStore.grants(c, CollectionStore.PENDING, BATCH);
        }).whenCompleteAsync((rows, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not read waiting hand-outs", Database.cause(e));
                return;
            }
            for (CollectionStore.GrantRow row : rows) {
                take(row);
            }
        }, m.main());
    }

    private CompletableFuture<Void> recover() {
        long now = System.currentTimeMillis();
        return m.core.database().async(c -> {
            List<CollectionStore.GrantRow> reviewed = new ArrayList<>();
            for (CollectionStore.GrantRow row : CollectionStore.grants(c, CollectionStore.SENDING, 10_000)) {
                String to = Milestones.TITLE.equals(row.kind()) ? CollectionStore.PENDING : CollectionStore.REVIEW;
                if (CollectionStore.move(c, row.id(), CollectionStore.SENDING, to, now) && to.equals(CollectionStore.REVIEW)) {
                    reviewed.add(row);
                }
            }
            return reviewed;
        }).thenAcceptAsync(reviewed -> {
            for (CollectionStore.GrantRow row : reviewed) {
                m.audit("grant_review", Actor.SYSTEM, row.player(), row.amount(), details(row));
            }
            if (!reviewed.isEmpty()) {
                m.core.plugin().getLogger().warning("Collection: " + reviewed.size() + " milestone reward(s) were cut off by a stop or "
                        + "crash; they are marked 'review' (grant_review in /logs, category collection). Check the player's keys and "
                        + "/box and give what is missing by hand.");
            }
        }, m.main()).exceptionally(e -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not check interrupted hand-outs", Database.cause(e));
            return null;
        });
    }

    private boolean available(CollectionStore.GrantRow row) {
        return switch (row.kind()) {
            case Milestones.KEY -> m.core.services().has(CrateKeys.class);
            case Milestones.TITLE -> m.cosmetics().isPresent();
            case Milestones.SET -> m.core.services().has(WardrobeService.class);
            default -> false;
        };
    }

    private void take(CollectionStore.GrantRow row) {
        if (!available(row)) {
            return; // stays pending; tried again later
        }
        long now = System.currentTimeMillis();
        m.core.database().async(c -> CollectionStore.move(c, row.id(), CollectionStore.PENDING, CollectionStore.SENDING, now))
                .whenCompleteAsync((taken, e) -> {
                    if (e != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not take hand-out " + row.id(), Database.cause(e));
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

    private void finish(CollectionStore.GrantRow row, Result result, Throwable error) {
        Result r = result == null ? Result.FAILED : result;
        String to = switch (r) {
            case DELIVERED -> CollectionStore.DELIVERED;
            case RETRY -> CollectionStore.PENDING;
            case FAILED -> CollectionStore.FAILED;
        };
        long now = System.currentTimeMillis();
        m.core.database().async(c -> CollectionStore.move(c, row.id(), CollectionStore.SENDING, to, now)).whenComplete((v, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not mark hand-out " + row.id() + " as " + to
                        + " (it is reviewed at the next start)", Database.cause(e));
            }
        });
        if (r == Result.FAILED) {
            m.core.plugin().getLogger().log(Level.WARNING, "Collection: hand-out " + row.kind() + " " + row.ref() + " x" + row.amount()
                    + " for profile " + row.profile() + " failed; see grant_failed in /logs", error == null ? null : Database.cause(error));
            Map<String, Object> d = details(row);
            d.put("error", error == null ? "" : String.valueOf(Database.cause(error).getMessage()));
            m.audit("grant_failed", Actor.SYSTEM, row.player(), row.amount(), d);
        } else if (r == Result.DELIVERED) {
            m.audit("grant_delivered", Actor.SYSTEM, row.player(), row.amount(), details(row));
        }
    }

    static Map<String, Object> details(CollectionStore.GrantRow row) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("grant", row.id().toString());
        d.put("profile", row.profile().toString());
        d.put("milestone", row.milestone());
        d.put("kind", row.kind());
        d.put("item", row.ref());
        return d;
    }

    private CompletableFuture<Result> perform(CollectionStore.GrantRow row) {
        Actor actor = Actor.system(CollectionModule.ID);
        String origin = "collection:" + row.milestone();
        return switch (row.kind()) {
            case Milestones.KEY -> {
                Optional<CrateKeys> keys = m.core.services().optional(CrateKeys.class);
                if (keys.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                if (!keys.get().exists(row.ref())) {
                    yield CompletableFuture.completedFuture(Result.FAILED);
                }
                yield keys.get().giveKeys(row.profile(), row.ref(), (int) Math.min(64, Math.max(1, row.amount())), CollectionModule.ID, actor)
                        .thenApply(ok -> Boolean.TRUE.equals(ok) ? Result.DELIVERED : Result.RETRY);
            }
            case Milestones.TITLE -> {
                Optional<Cosmetics> cosmetics = m.cosmetics();
                if (cosmetics.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                // The milestone notice names the title, so the cosmetics module's own "new title" line is not sent.
                yield cosmetics.get().grant(row.player(), Cosmetics.Kind.TITLE, row.ref(), origin, false).thenApply(isNew -> Result.DELIVERED);
            }
            case Milestones.SET -> {
                Optional<WardrobeService> wardrobe = m.core.services().optional(WardrobeService.class);
                if (wardrobe.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                yield wardrobe.get().createToken(row.ref(), origin, row.player(), actor).thenComposeAsync(token -> {
                    if (token == null || token.isEmpty()) {
                        return CompletableFuture.completedFuture(Result.FAILED);
                    }
                    BoundItems.markAll(List.of(token), row.profile());
                    Player receiver = m.receiver(row.profile());
                    if (receiver != null) {
                        ItemBox.give(m.core.services(), receiver, BoxSource.REWARDS, token);
                        return CompletableFuture.completedFuture(Result.DELIVERED);
                    }
                    return ItemBox.of(m.core.services()).map(b -> b.deliver(row.profile(), List.of(token), BoxSource.REWARDS, actor)
                            .thenApply(d -> Result.DELIVERED)).orElse(CompletableFuture.completedFuture(Result.FAILED));
                }, m.main());
            }
            default -> CompletableFuture.completedFuture(Result.FAILED);
        };
    }
}
