package net.skybornmc.skyblock.common.economy;

import net.skybornmc.skyblock.common.service.ServiceRegistry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owner answer F1 (REVIEW3 follow-ups): a profile can send money away ({@code /pay}, money notes, money in {@code /trade})
 * only after it has <b>earned</b> a set amount itself (economy.yml {@code send-gate.required}, $10k). Earned = income
 * (selling, rewards, interest ...), never money received from other players. Sending to a member of the island the sender
 * is on is always allowed. Published by the economy module: {@code core.services().optional(SendGate.class)}.
 *
 * <p>The gate is an entry check that tells the player what to do; the transaction itself is still atomic and checked by
 * the economy.</p>
 */
public interface SendGate {

    /**
     * The answer for one send.
     *
     * @param allowed  true when the send may go ahead
     * @param earned   what the sending profile has earned so far (0 when the check was skipped)
     * @param required what it must have earned ({@code send-gate.required})
     */
    record Check(boolean allowed, long earned, long required, Kind kind, long left, long nextHours, long nextLimit) {

        public static final Check OPEN = new Check(true, 0, 0, Kind.OPEN, -1, 0, 0);

        /** The old F1 shape (earned / required). */
        public Check(boolean allowed, long earned, long required) {
            this(allowed, earned, required, allowed ? Kind.OPEN : Kind.EARNED, -1, 0, 0);
        }

        /** Owner 6 Nov 2026: the daily pay limit by playtime refused it: {@code left} may still go today (-1 = no limit). */
        public static Check limit(long left, long nextHours, long nextLimit) {
            return new Check(false, 0, 0, Kind.DAILY_LIMIT, left, nextHours, nextLimit);
        }

        /** Owner 6 Nov 2026: shards only go to members of your own island. */
        public static final Check ISLAND_ONLY = new Check(false, 0, 0, Kind.ISLAND_ONLY, 0, 0, 0);

        /** How much more the profile must earn (0 when allowed or another rule refused it). */
        public long missing() {
            return allowed || kind != Kind.EARNED ? 0 : Math.max(0, required - earned);
        }
    }

    /** Why a send was refused. */
    enum Kind {
        OPEN,
        /** The old F1 rule: earn send-gate.required first (off by default since 6 Nov 2026). */
        EARNED,
        /** The daily pay limit by playtime is used up (or the amount is bigger than what is left). */
        DAILY_LIMIT,
        /** Shards may only be paid to members of your own island. */
        ISLAND_ONLY
    }

    /**
     * Owner 6 Nov 2026: whether {@code fromProfile} may send {@code amount} of {@code currency} to {@code toProfile} today:
     * the F1 rule, the daily pay limit by playtime and the island-only rule for shards. Main thread; completes off it.
     */
    default CompletableFuture<Check> check(UUID fromProfile, UUID toProfile, Currency currency, long amount) {
        return check(fromProfile, toProfile, currency);
    }

    /** Tells the player why a send was refused, in the economy's words (any {@link Kind}). Main thread. */
    default void tell(org.bukkit.entity.Player player, Check check) {
    }

    /**
     * Whether profile {@code fromProfile} may send {@code currency} to {@code toProfile} ({@code null} = to anyone, e.g. a
     * money note). Call on the main thread (island membership is read there); completes off the main thread.
     */
    CompletableFuture<Check> check(UUID fromProfile, UUID toProfile, Currency currency);

    /** The published gate, if the economy module runs. */
    static Optional<SendGate> of(ServiceRegistry services) {
        return services == null ? Optional.empty() : services.optional(SendGate.class);
    }
}
