package net.skybornmc.skyblock.common.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owner 4 Nov 2026 (PLAN_4NOV phase 1, anti-boosting): a <b>new</b> profile (less than {@code anti-boost.playtime-minutes}
 * of its own playtime) can receive at most {@code anti-boost.max-received} money in total from other players and send at
 * most {@code anti-boost.max-sent}. Counted: {@code /pay}, money in {@code /trade} and auction sales (the seller receives,
 * the buyer sends). Once the profile has played the threshold the totals are ignored. Only money is capped. Published by
 * the economy module: {@code AntiBoost.of(core.services())}.
 *
 * <p><b>Two ways to use it:</b></p>
 * <ul>
 *   <li>inside the money transaction ({@code /pay}, {@code /trade}): {@link #prepare} → tell the player when
 *       {@link Guard#preview()} refuses, else run {@link EconomyService#executeGuarded} / {@link EconomyService#executeBatch}
 *       with the guard. The guard re-checks and counts with locked rows, so two parallel transfers can never both slip
 *       through; when it refused, the result is {@link TxResult.Status#DUPLICATE} and {@link Guard#refused()} says why.</li>
 *   <li>a flow with several transactions (auction purchase): {@link #claim} first (checks and counts atomically on its own),
 *       {@link #release} when the purchase did not happen.</li>
 * </ul>
 * Every refusal shown to a player is passed to {@link #report} once (audit {@code economy/anti_boost}, staff alert after
 * repeated refusals).
 */
public interface AntiBoost {

    /** Audit action of a refusal (category economy). */
    String AUDIT_ACTION = "anti_boost";

    /** Which cap refused. */
    enum Side {
        /** The new profile would receive more than {@code anti-boost.max-received}. */
        RECEIVER,
        /** The new profile would send more than {@code anti-boost.max-sent}. */
        SENDER
    }

    /** Money moving from one player's profile to another's (both profile keys, amount &gt; 0). */
    record Transfer(UUID from, UUID to, long amount) {
    }

    /**
     * The answer for a set of transfers.
     *
     * @param allowed     true when everything fits
     * @param side        the cap that refused (null when allowed)
     * @param profile     the new profile whose cap refused (null when allowed)
     * @param amount      what that profile tried to receive or send in these transfers
     * @param room        how much more it can receive or send now (0 when nothing)
     * @param cap         the cap ({@code max-received} or {@code max-sent})
     * @param minutesLeft playtime minutes until the caps no longer apply to it
     * @param minutes     the threshold ({@code anti-boost.playtime-minutes})
     */
    record Check(boolean allowed, Side side, UUID profile, long amount, long room, long cap, long minutesLeft, long minutes) {

        public static final Check OPEN = new Check(true, null, null, 0, 0, 0, 0, 0);

        /** Refused because the check itself failed (database or playtime lookup): nothing to explain, try again. */
        public boolean error() {
            return !allowed && side == null;
        }
    }

    /** A {@link TxGuard} that checks and counts inside the money transaction. */
    interface Guard extends TxGuard {

        /** The answer from the totals read by {@link #prepare} (not locked; tell the player before running anything). */
        Check preview();

        /** The refusal of the last run of {@link #holds} inside the transaction, or {@link Check#OPEN}. */
        Check refused();
    }

    /** Reads playtimes and totals; completes off the main thread. Never fails: on errors it refuses (fail closed). */
    CompletableFuture<Guard> prepare(List<Transfer> transfers);

    /** Only the preview of {@link #prepare}. */
    default CompletableFuture<Check> check(List<Transfer> transfers) {
        return prepare(transfers).thenApply(Guard::preview);
    }

    /** Checks and counts in one database transaction of its own; undo it with {@link #release}. Fails closed. */
    CompletableFuture<Check> claim(List<Transfer> transfers);

    /** Takes back what {@link #claim} counted (the purchase did not happen). */
    CompletableFuture<Void> release(List<Transfer> transfers);

    /**
     * Writes the audit row of a refusal and counts it for the staff alert. Any thread.
     *
     * @param path      short id of the path ({@code pay}, {@code trade}, {@code ah_buy})
     * @param actor     who tried it
     * @param transfers what was tried
     */
    void report(Check refused, String path, Actor actor, List<Transfer> transfers);

    /**
     * Tells {@code viewer} why a transfer was refused (texts in economy.yml {@code anti-boost.*}, money in the viewer's
     * format): "you are new and can receive/send $X more ..." when the viewer's active profile is the capped one,
     * otherwise "{@code newPlayerName} is new and can receive/send $X more until they have played 3h". An
     * {@linkplain Check#error() error} gets a "try again" line. Plays the error sound. Main thread.
     *
     * @param newPlayerName name of the capped profile's player (null = looked up)
     */
    void tell(Player viewer, Check refused, String newPlayerName);

    /** The published service, if the economy module runs. */
    static Optional<AntiBoost> of(ServiceRegistry services) {
        return services == null ? Optional.empty() : services.optional(AntiBoost.class);
    }

    /** Both guards in order ({@code null} = none); the first that refuses stops. */
    static TxGuard both(TxGuard first, TxGuard second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return c -> first.holds(c) && second.holds(c);
    }
}
