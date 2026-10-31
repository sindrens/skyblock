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
    record Check(boolean allowed, long earned, long required) {

        public static final Check OPEN = new Check(true, 0, 0);

        /** How much more the profile must earn (0 when allowed). */
        public long missing() {
            return allowed ? 0 : Math.max(0, required - earned);
        }
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
