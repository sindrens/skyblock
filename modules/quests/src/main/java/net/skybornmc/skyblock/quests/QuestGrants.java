package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.pass.PassXp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Hand-outs after a committed payout (all-done keys through {@link CrateKeys}, pass XP through {@link PassXp}), from
 * {@code quest_grants} rows: {@code pending} → {@code sending} (one atomic update, never handed out twice) →
 * {@code delivered}. A missing module leaves the row pending (tried again every {@code delivery.retry-seconds} and at the
 * next start); a row a crash left in {@code sending} goes to {@code review} for staff ({@code grant_review} in /logs).
 * Lost, never doubled.
 */
final class QuestGrants {

    private static final int BATCH = 200;

    private final QuestsModule m;
    private volatile boolean recovered;

    QuestGrants(QuestsModule module) {
        this.m = module;
    }

    void handOut(List<QuestStore.GrantRow> rows) {
        for (QuestStore.GrantRow row : rows) {
            if (available(row)) {
                take(row);
            }
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
        int keepBeforeDay = QuestWindows.day(now, m.zone()) - m.settings.keepDays;
        long grantsBefore = now - m.settings.keepDays * 86_400_000L;
        m.core.database().async(c -> {
            QuestStore.prune(c, keepBeforeDay, grantsBefore);
            return QuestStore.grants(c, QuestStore.PENDING, BATCH);
        }).whenCompleteAsync((rows, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Quests: could not read waiting hand-outs", Database.cause(e));
                return;
            }
            handOut(rows);
        }, m.main());
    }

    private CompletableFuture<Void> recover() {
        long now = System.currentTimeMillis();
        return m.core.database().async(c -> {
            List<QuestStore.GrantRow> moved = new ArrayList<>();
            for (QuestStore.GrantRow row : QuestStore.grants(c, QuestStore.SENDING, 10_000)) {
                if (QuestStore.move(c, row.id(), QuestStore.SENDING, QuestStore.REVIEW, now)) {
                    moved.add(row);
                }
            }
            return moved;
        }).thenAcceptAsync(moved -> {
            for (QuestStore.GrantRow row : moved) {
                m.audit("grant_review", Actor.SYSTEM, row.player(), null, row.amount(), details(row));
            }
            if (!moved.isEmpty()) {
                m.core.plugin().getLogger().warning("Quests: " + moved.size() + " quest reward hand-out(s) were interrupted by a stop or "
                        + "crash; they are marked for staff review (grant_review in /logs).");
            }
        }, m.main()).exceptionally(e -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Quests: could not check interrupted hand-outs", Database.cause(e));
            return null;
        });
    }

    private boolean available(QuestStore.GrantRow row) {
        return switch (row.kind()) {
            case QuestStore.KEY -> m.core.services().optional(CrateKeys.class).isPresent();
            case QuestStore.PASS_XP -> PassXp.of(m.core.services()).isPresent();
            default -> false;
        };
    }

    private void take(QuestStore.GrantRow row) {
        long now = System.currentTimeMillis();
        m.core.database().async(c -> QuestStore.move(c, row.id(), QuestStore.PENDING, QuestStore.SENDING, now)).whenCompleteAsync((taken, e) -> {
            if (e != null || !Boolean.TRUE.equals(taken)) {
                return; // another attempt has it, or the database is down (stays pending)
            }
            deliver(row).whenCompleteAsync((ok, error) -> {
                String to = error == null && Boolean.TRUE.equals(ok) ? QuestStore.DELIVERED : QuestStore.PENDING;
                long at = System.currentTimeMillis();
                m.core.database().async(c -> QuestStore.move(c, row.id(), QuestStore.SENDING, to, at)).whenComplete((v, err) -> {
                    if (err != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Quests: hand-out " + row.id() + " state not saved", Database.cause(err));
                    }
                });
                if (QuestStore.DELIVERED.equals(to) && QuestStore.PASS_XP.equals(row.kind())) {
                    m.audit("grant_delivered", Actor.SYSTEM, row.player(), null, row.amount(), details(row));
                }
            }, m.main());
        }, m.main());
    }

    /** Hands one row out; completes with false when the other module said no (the row waits). */
    private CompletableFuture<Boolean> deliver(QuestStore.GrantRow row) {
        try {
            if (QuestStore.KEY.equals(row.kind())) {
                Optional<CrateKeys> keys = m.core.services().optional(CrateKeys.class);
                String[] ref = row.ref().split(":", 2);
                if (keys.isEmpty() || row.amount() <= 0 || row.amount() > Integer.MAX_VALUE) {
                    return CompletableFuture.completedFuture(false);
                }
                String reason = ref.length > 1 ? ref[1] : "quest";
                return keys.get().giveKeys(row.profile(), ref[0], (int) row.amount(), reason, Actor.system("quests"));
            }
            if (QuestStore.PASS_XP.equals(row.kind())) {
                Optional<PassXp> pass = PassXp.of(m.core.services());
                if (pass.isEmpty() || row.amount() <= 0 || row.amount() > Integer.MAX_VALUE) {
                    return CompletableFuture.completedFuture(false);
                }
                return pass.get().addXp(row.profile(), (int) row.amount(), "quest_" + row.ref());
            }
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Quests: hand-out " + row.id() + " failed", e);
        }
        return CompletableFuture.completedFuture(false);
    }

    private static Map<String, Object> details(QuestStore.GrantRow row) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("grant", row.id().toString());
        d.put("profile", row.profile().toString());
        d.put("kind", row.kind());
        d.put("ref", row.ref());
        d.put("source", row.sourceRef());
        return d;
    }
}
