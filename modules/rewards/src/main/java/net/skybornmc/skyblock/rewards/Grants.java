package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.production.ProductionService;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.pass.PassXp;
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
 * Hand-outs after a committed claim (keys, books, scrolls, boosters, fuel, titles, pass XP), from {@code reward_grants}
 * rows: {@code pending} → {@code sending} (one atomic update, so a row is never handed out twice) → {@code delivered}.
 * <ul>
 *   <li>When the module behind a grant is not there right now (crates, enchants ... disabled) the row goes back to
 *       {@code pending} and is tried again later (every {@code delivery.retry-seconds} and at the next start).</li>
 *   <li>Items go to the inventory when the player is online on that profile (the rest to {@code /box}, then the player
 *       file is saved), otherwise straight into that profile's box. Calendar items are bound to the profile.</li>
 *   <li>Rows still {@code sending} at start were interrupted by a crash: titles and pass XP (idempotent) are sent again, everything
 *       else is moved to {@code review} for staff ({@code grant_review} in /logs). Lost, never doubled.</li>
 * </ul>
 */
final class Grants {

    /** What a hand-out attempt did. */
    enum Result {
        DELIVERED,
        /** Nothing was given (the module is missing or said no): back to pending, tried again later. */
        RETRY,
        /** Something went wrong after the row was taken: staff check it. */
        FAILED
    }

    private static final int BATCH = 200;

    private final RewardsModule m;
    private volatile boolean recovered;

    Grants(RewardsModule module) {
        this.m = module;
    }

    void recoverLater() {
        recovered = false;
    }

    /** Hands out the rows of a claim that just committed. Main thread. */
    void handOut(List<RewardStore.GrantRow> rows) {
        for (RewardStore.GrantRow row : rows) {
            take(row);
        }
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
        long now = System.currentTimeMillis();
        long keepBefore = now - m.settings.keepDays * 86_400_000L;
        m.core.database().async(c -> {
            RewardStore.pruneDelivered(c, keepBefore);
            return RewardStore.grants(c, RewardStore.PENDING, BATCH);
        }).whenCompleteAsync((rows, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not read waiting hand-outs", Database.cause(e));
                return;
            }
            for (RewardStore.GrantRow row : rows) {
                if (available(row)) {
                    take(row);
                }
            }
        }, m.main());
    }

    /** Rows a crash left in {@code sending}: titles again, the rest to review. */
    private CompletableFuture<Void> recover() {
        long now = System.currentTimeMillis();
        return m.core.database().async(c -> {
            List<RewardStore.GrantRow> sending = RewardStore.grants(c, RewardStore.SENDING, 10_000);
            List<RewardStore.GrantRow> reviewed = new ArrayList<>();
            for (RewardStore.GrantRow row : sending) {
                // Titles and pass XP (idempotency key per row) are safe to send again; everything else is checked by staff.
                String to = Payout.TITLE.equals(row.kind()) || Payout.PASS_XP.equals(row.kind()) ? RewardStore.PENDING : RewardStore.REVIEW;
                if (RewardStore.move(c, row.id(), RewardStore.SENDING, to, now) && to.equals(RewardStore.REVIEW)) {
                    reviewed.add(row);
                }
            }
            return reviewed;
        }).thenAcceptAsync(reviewed -> {
            for (RewardStore.GrantRow row : reviewed) {
                Map<String, Object> d = details(row);
                m.audit("grant_review", Actor.SYSTEM, row.player(), null, row.amount(), d);
            }
            if (!reviewed.isEmpty()) {
                m.core.plugin().getLogger().warning("Rewards: " + reviewed.size() + " reward hand-out(s) were interrupted by a stop or crash; "
                        + "they are marked 'review' (grant_review in /logs, category rewards). Check the player's inventory and /box and "
                        + "give what is missing by hand.");
            }
        }, m.main()).exceptionally(e -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not check interrupted hand-outs", Database.cause(e));
            return null;
        });
    }

    /** True when the module a row needs is there (otherwise it waits as pending without being touched). */
    private boolean available(RewardStore.GrantRow row) {
        return switch (row.kind()) {
            case Payout.KEY -> m.core.services().has(CrateKeys.class);
            case Payout.BOOK, Payout.SCROLL -> m.core.services().has(EnchantService.class);
            case Payout.BOOSTER -> m.core.services().has(BoosterService.class);
            case Payout.FUEL -> m.fuelSupported();
            case Payout.TITLE -> m.cosmetics().isPresent();
            case Payout.PASS_XP -> m.core.services().has(PassXp.class);
            default -> false;
        };
    }

    /** pending → sending (atomic), then the hand-out. */
    private void take(RewardStore.GrantRow row) {
        if (!available(row)) {
            return; // stays pending; retried later
        }
        long now = System.currentTimeMillis();
        m.core.database().async(c -> RewardStore.move(c, row.id(), RewardStore.PENDING, RewardStore.SENDING, now))
                .whenCompleteAsync((taken, e) -> {
                    if (e != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not take hand-out " + row.id(), Database.cause(e));
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

    private void finish(RewardStore.GrantRow row, Result result, Throwable error) {
        String to = switch (result == null ? Result.FAILED : result) {
            case DELIVERED -> RewardStore.DELIVERED;
            case RETRY -> RewardStore.PENDING;
            case FAILED -> RewardStore.FAILED;
        };
        long now = System.currentTimeMillis();
        m.core.database().async(c -> RewardStore.move(c, row.id(), RewardStore.SENDING, to, now)).whenComplete((v, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not mark hand-out " + row.id() + " as " + to
                        + " (it is reviewed at the next start)", Database.cause(e));
            }
        });
        if (result == Result.FAILED || result == null) {
            m.core.plugin().getLogger().log(Level.WARNING, "Rewards: hand-out " + row.kind() + " " + row.ref() + " x" + row.amount()
                    + " for profile " + row.profile() + " failed; see grant_failed in /logs", error == null ? null : Database.cause(error));
            Map<String, Object> d = details(row);
            d.put("error", error == null ? "" : String.valueOf(Database.cause(error).getMessage()));
            m.audit("grant_failed", Actor.SYSTEM, row.player(), null, row.amount(), d);
        } else if (result == Result.DELIVERED && !Payout.KEY.equals(row.kind()) && !Payout.TITLE.equals(row.kind())) {
            // Keys and titles are logged by the crates and cosmetics modules.
            m.audit("grant_delivered", Actor.SYSTEM, row.player(), null, row.amount(), details(row));
        }
        m.refreshMenus(row.profile());
    }

    private static Map<String, Object> details(RewardStore.GrantRow row) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("grant", row.id().toString());
        d.put("profile", row.profile().toString());
        d.put("source", row.source());
        d.put("ref", row.sourceRef());
        d.put("kind", row.kind());
        d.put("item", row.ref());
        return d;
    }

    /** The key/pass XP reason: the grant source for playtime and votes (crates binds vote, vote_streak, vote_party keys). */
    static String reason(RewardStore.GrantRow row) {
        return switch (row.source()) {
            case "playtime", "vote", "vote_streak", "vote_party" -> row.source();
            default -> "daily";
        };
    }

    // ================================================================== hand-outs (main thread)

    private CompletableFuture<Result> perform(RewardStore.GrantRow row) {
        Actor actor = Actor.system(RewardsModule.ID);
        String origin = "rewards:" + row.source();
        int amount = (int) Math.max(1, Math.min(64, row.amount()));
        return switch (row.kind()) {
            case Payout.KEY -> {
                Optional<CrateKeys> keys = m.core.services().optional(CrateKeys.class);
                if (keys.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                if (!keys.get().exists(row.ref())) {
                    yield CompletableFuture.completedFuture(Result.FAILED);
                }
                yield keys.get().giveKeys(row.profile(), row.ref(), (int) Math.min(Integer.MAX_VALUE, row.amount()), reason(row), actor)
                        .thenApply(ok -> Boolean.TRUE.equals(ok) ? Result.DELIVERED : Result.RETRY);
            }
            case Payout.TITLE -> {
                Optional<Cosmetics> cosmetics = m.cosmetics();
                if (cosmetics.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                yield cosmetics.get().grant(row.player(), Cosmetics.Kind.TITLE, row.ref(), origin).thenApply(isNew -> Result.DELIVERED);
            }
            case Payout.PASS_XP -> {
                Optional<PassXp> pass = PassXp.of(m.core.services());
                if (pass.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                yield pass.get().addXp(row.profile(), (int) Math.min(Integer.MAX_VALUE, row.amount()), reason(row), "rewards:" + row.id())
                        .thenApply(ok -> Boolean.TRUE.equals(ok) ? Result.DELIVERED : Result.RETRY);
            }
            case Payout.BOOK, Payout.SCROLL, Payout.BOOSTER, Payout.FUEL -> createItems(row, amount, origin)
                    .thenComposeAsync(items -> items.isEmpty() ? CompletableFuture.completedFuture(Result.FAILED)
                            : deliverItems(row, items, actor), m.main());
            default -> CompletableFuture.completedFuture(Result.FAILED);
        };
    }

    private CompletableFuture<List<ItemStack>> createItems(RewardStore.GrantRow row, int amount, String origin) {
        switch (row.kind()) {
            case Payout.BOOK -> {
                EnchantService e = m.core.services().optional(EnchantService.class).orElseThrow();
                int colon = row.ref().lastIndexOf(':');
                String id = colon > 0 ? row.ref().substring(0, colon) : row.ref();
                int level = 1;
                if (colon > 0) {
                    Long parsed = RewardSpec.number(row.ref().substring(colon + 1));
                    level = parsed == null ? 1 : (int) Math.min(Integer.MAX_VALUE, parsed);
                }
                level = Math.max(1, Math.min(level, Math.max(1, e.maxLevel(id))));
                List<CompletableFuture<ItemStack>> books = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    books.add(e.createBook(id, level, origin, row.player()));
                }
                return all(books);
            }
            case Payout.SCROLL -> {
                EnchantService e = m.core.services().optional(EnchantService.class).orElseThrow();
                List<CompletableFuture<ItemStack>> scrolls = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    scrolls.add(e.createScroll(origin, row.player()));
                }
                return all(scrolls);
            }
            case Payout.BOOSTER -> {
                BoosterService b = BoosterService.of(m.core.services()).orElseThrow();
                String[] ref = row.ref().split(":");
                if (ref.length != 3) {
                    return CompletableFuture.completedFuture(List.of());
                }
                Optional<BoosterType> type = BoosterType.byId(ref[0]);
                Long percent = RewardSpec.number(ref[1]);
                Long minutes = RewardSpec.number(ref[2]);
                if (type.isEmpty() || percent == null || minutes == null || percent > Integer.MAX_VALUE || minutes > Integer.MAX_VALUE) {
                    return CompletableFuture.completedFuture(List.of());
                }
                List<ItemStack> out = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    b.createPersonal(type.get(), percent.intValue(), minutes.intValue(), row.profile(), origin).ifPresent(out::add);
                }
                return CompletableFuture.completedFuture(out);
            }
            case Payout.FUEL -> {
                // PHASE-7 M7.1: fuel minutes become bound Fuel Cells (1 / 3 / 6 h), used on the island later.
                Optional<ProductionService> production = ProductionService.of(m.core.services());
                return CompletableFuture.completedFuture(production.map(p -> p.createFuelCells(row.profile(), row.amount(), origin))
                        .orElse(List.of()));
            }
            default -> {
                return CompletableFuture.completedFuture(List.of());
            }
        }
    }

    private static CompletableFuture<List<ItemStack>> all(List<CompletableFuture<ItemStack>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<ItemStack> out = new ArrayList<>();
            for (CompletableFuture<ItemStack> f : futures) {
                ItemStack s = f.join();
                if (s != null && !s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        });
    }

    /** Inventory (rest to /box) when online on that profile, else straight into that profile's box. Main thread. */
    private CompletableFuture<Result> deliverItems(RewardStore.GrantRow row, List<ItemStack> items, Actor actor) {
        if (m.settings.itemsBound) {
            BoundItems.markAll(items, row.profile());
        }
        Player receiver = m.receiver(row.profile());
        if (receiver != null) {
            ItemBox.give(m.core.services(), receiver, BoxSource.REWARDS, items);
            if (m.settings.savePlayerData) {
                PlayerSaves.save(receiver, "a daily or playtime reward");
            }
            return CompletableFuture.completedFuture(Result.DELIVERED);
        }
        Optional<ItemBox> box = ItemBox.of(m.core.services());
        if (box.isEmpty()) {
            return CompletableFuture.completedFuture(Result.FAILED);
        }
        return box.get().deliver(row.profile(), items, BoxSource.REWARDS, actor).thenApply(d -> Result.DELIVERED);
    }
}
