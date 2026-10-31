package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner R3-6: {@code /eco} changes above {@code eco.approve-above.<currency>} ($1T for money) wait here until an owner or
 * admin other than the requester clicks Allow (chat button for Java, the pending list in {@code /eco admin} for
 * Bedrock). A waiting change is dropped after {@code eco.pending-minutes}. Kept in memory: a restart drops every waiting
 * change (nothing was applied, the requester simply runs it again). Main thread only. Pure apart from the clock passed in.
 */
final class EcoApprovals {

    /**
     * One waiting change.
     *
     * @param id         short id shown in chat and used by {@code /eco allow <id>}
     * @param staff      requester (player UUID)
     * @param profileKey the target's profile the change goes to (decided when it was asked)
     * @param amount     the command's amount (give/take: the amount, set: the new balance, reset: the start balance)
     * @param change     how much the balance would move (what was compared with the limit)
     */
    record Pending(int id, UUID staff, String staffName, EcoAction action, UUID target, String targetName, UUID profileKey,
                   Currency currency, long amount, long change, String note, long created, long expires) {

        boolean expired(long now) {
            return now >= expires;
        }
    }

    /** Why someone can't allow or deny a waiting change; {@code key} is under {@code eco.approve.} in the messages. */
    enum Problem {
        UNKNOWN("unknown"),
        NO_PERMISSION("no-permission"),
        OWN_REQUEST("own-request");

        final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    private final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private int nextId = 1;

    /** True if a change of {@code change} must wait for an Allow ({@code limit} 0 = never). Up to the limit applies at once. */
    static boolean needsApproval(long change, long limit) {
        return limit > 0 && change > limit;
    }

    /** How much a command moves the balance: give/take the amount, set/reset the distance to the new balance. */
    static long change(EcoAction action, long amount, long balanceNow) {
        return switch (action) {
            case GIVE, TAKE -> Math.max(0, amount);
            case SET, RESET -> Math.abs(amount - Math.max(0, balanceNow));
        };
    }

    /**
     * Who may decide: an approver (owner/admin permission) who did not ask for it, or anyone with the approve-own permission
     * (the owner: "click in the alert to allow", R3-6). The console ({@code approver == null}) may always decide.
     */
    static Optional<Problem> canDecide(Pending p, UUID approver, boolean hasPermission) {
        return canDecide(p, approver, hasPermission, false);
    }

    static Optional<Problem> canDecide(Pending p, UUID approver, boolean hasPermission, boolean mayApproveOwn) {
        if (p == null) {
            return Optional.of(Problem.UNKNOWN);
        }
        if (approver == null) {
            return Optional.empty();
        }
        if (!hasPermission) {
            return Optional.of(Problem.NO_PERMISSION);
        }
        if (approver.equals(p.staff()) && !mayApproveOwn) {
            return Optional.of(Problem.OWN_REQUEST);
        }
        return Optional.empty();
    }

    Pending add(UUID staff, String staffName, EcoAction action, UUID target, String targetName, UUID profileKey, Currency currency,
                long amount, long change, String note, long now, long lifetimeMillis) {
        Pending p = new Pending(nextId++, staff, staffName, action, target, targetName, profileKey, currency, amount, change, note,
                now, now + lifetimeMillis);
        pending.put(p.id(), p);
        return p;
    }

    /** The waiting change with this id, if still valid. */
    Optional<Pending> get(int id, long now) {
        Pending p = pending.get(id);
        return p == null || p.expired(now) ? Optional.empty() : Optional.of(p);
    }

    /** Removes and returns the waiting change (first click wins), if still valid. */
    Optional<Pending> take(int id, long now) {
        Pending p = pending.remove(id);
        return p == null || p.expired(now) ? Optional.empty() : Optional.of(p);
    }

    /** Every valid waiting change, oldest first. */
    List<Pending> list(long now) {
        return pending.values().stream().filter(p -> !p.expired(now)).toList();
    }

    /** Ids for tab completion. */
    List<String> ids(long now) {
        return list(now).stream().map(p -> String.valueOf(p.id())).toList();
    }

    /** Removes and returns every expired change (the requester is told). */
    List<Pending> expire(long now) {
        List<Pending> out = new ArrayList<>();
        pending.values().removeIf(p -> {
            if (p.expired(now)) {
                out.add(p);
                return true;
            }
            return false;
        });
        return out;
    }
}
