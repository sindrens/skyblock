package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.moderation.SanctionService;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The sanction rules (P7, spec 13.1), pure: which active sanction blocks which use, the economy safety net, lengths
 * and the "take at most the balance" rule for removing money. Unit-tested as a table of cases.
 */
final class SanctionRules {

    private SanctionRules() {
    }

    /**
     * {@code punishments.yml → extras.sanctions}.
     *
     * @param durations       lengths offered for freeze and block trading ({@code null} entry = until lifted), max 7
     * @param reasons         preset reason ids (texts {@code sanctions.reasons.<id>}), max 7
     * @param freezeBlocks    uses blocked while the balance is frozen
     * @param freezeCurrencies currencies a freeze stops from leaving the player's accounts
     * @param freezeExempt    ledger reasons that may still take money from a frozen player (staff actions)
     * @param freezeReceive   ledger reasons a frozen player may not receive either (taking money out of an island bank)
     * @param tradeBlocks     uses blocked while trading is blocked
     * @param tradeReasons    ledger reasons refused when either side is trade-blocked
     * @param moneyMax        most money one "remove money" may take
     * @param dupeSuggest     a detected dupe creates a suggestion (never applied by itself)
     * @param dupeReason      the reason id of that suggestion
     */
    record Settings(boolean enabled, List<Duration> durations, List<String> reasons, Set<SanctionService.Use> freezeBlocks,
                    Set<Currency> freezeCurrencies, Set<String> freezeExempt, Set<String> freezeReceive,
                    Set<SanctionService.Use> tradeBlocks, Set<String> tradeReasons, long moneyMax, boolean dupeSuggest,
                    String dupeReason) {

        static final Settings DEFAULT = new Settings(true,
                java.util.Collections.unmodifiableList(java.util.Arrays.asList(Duration.ofDays(1), Duration.ofDays(3),
                        Duration.ofDays(7), Duration.ofDays(30), null)),
                List.of("duping", "scamming", "rmt", "exploit", "other"),
                EnumSet.of(SanctionService.Use.PAY, SanctionService.Use.NOTE, SanctionService.Use.BANK, SanctionService.Use.SHOP,
                        SanctionService.Use.AUCTION_LIST, SanctionService.Use.AUCTION_BUY, SanctionService.Use.TRADE),
                EnumSet.of(Currency.MONEY),
                Set.of("admin_take", "admin_set", "admin_reset", "sanction_remove"),
                Set.of("bank_withdraw"),
                EnumSet.of(SanctionService.Use.PAY, SanctionService.Use.NOTE, SanctionService.Use.AUCTION_LIST,
                        SanctionService.Use.AUCTION_BUY, SanctionService.Use.TRADE),
                Set.of("pay", "trade", "ah_buy", "note_withdraw"),
                1_000_000_000_000L, true, "exploiting");

        Set<SanctionService.Use> blocks(Sanction.Kind kind) {
            return switch (kind) {
                case FREEZE -> freezeBlocks;
                case TRADE -> tradeBlocks;
                default -> Set.of();
            };
        }
    }

    /** The active sanction (first freeze, then block trading) that blocks {@code use}, or empty. */
    static Optional<Sanction> blocking(Collection<Sanction> active, SanctionService.Use use, Settings s, long now) {
        if (!s.enabled() || active == null) {
            return Optional.empty();
        }
        Sanction best = null;
        for (Sanction x : active) {
            if (x.blocksAt(now) && s.blocks(x.kind()).contains(use)
                    && (best == null || x.kind().ordinal() < best.kind().ordinal())) {
                best = x;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The active sanction of {@code kind}, if any (the one that lasts longest; until lifted wins). */
    static Optional<Sanction> active(Collection<Sanction> list, Sanction.Kind kind, long now) {
        Sanction best = null;
        if (list == null) {
            return Optional.empty();
        }
        for (Sanction x : list) {
            if (x.kind() == kind && x.blocksAt(now) && (best == null || lastsLonger(x, best))) {
                best = x;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean lastsLonger(Sanction a, Sanction b) {
        if (a.expires() == null) {
            return b.expires() != null || a.id() > b.id();
        }
        return b.expires() != null && a.expires() > b.expires();
    }

    /**
     * The economy safety net. A transaction is refused when
     * <ul>
     *   <li>money of a frozen currency leaves a player or bank account of a frozen player (reason not exempt), or a
     *       frozen player receives it with a "receive" reason (taking money out of an island bank), or</li>
     *   <li>its reason is a trade reason and either side (or the acting player) is trade-blocked.</li>
     * </ul>
     *
     * @param sanctionsOf the active sanctions of a <b>player UUID</b>
     * @param ownerOf     profile key → player UUID
     */
    static boolean blocks(Transaction tx, Function<UUID, Collection<Sanction>> sanctionsOf, Function<UUID, UUID> ownerOf,
                          Settings s, long now) {
        if (!s.enabled() || tx == null) {
            return false;
        }
        UUID from = person(tx.from(), ownerOf);
        UUID to = person(tx.to(), ownerOf);
        UUID actor = tx.actor() == null ? null : tx.actor().uuid();
        if (s.freezeCurrencies().contains(tx.currency())) {
            if (from != null && !s.freezeExempt().contains(tx.reason()) && has(sanctionsOf.apply(from), Sanction.Kind.FREEZE, now)) {
                return true;
            }
            if (s.freezeReceive().contains(tx.reason())
                    && ((to != null && has(sanctionsOf.apply(to), Sanction.Kind.FREEZE, now))
                    || (actor != null && has(sanctionsOf.apply(actor), Sanction.Kind.FREEZE, now)))) {
                return true;
            }
        }
        if (s.tradeReasons().contains(tx.reason())) {
            for (UUID side : new UUID[]{from, to, actor}) {
                if (side != null && has(sanctionsOf.apply(side), Sanction.Kind.TRADE, now)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The player behind a player or bank account (islands belong to nobody here), or {@code null}. */
    private static UUID person(AccountRef ref, Function<UUID, UUID> ownerOf) {
        if (ref == null || ref.isIsland()) {
            return null;
        }
        UUID owner = ownerOf.apply(ref.owner());
        return owner == null ? ref.owner() : owner;
    }

    private static boolean has(Collection<Sanction> list, Sanction.Kind kind, long now) {
        if (list == null) {
            return false;
        }
        for (Sanction x : list) {
            if (x.kind() == kind && x.blocksAt(now)) {
                return true;
            }
        }
        return false;
    }

    /** End of a freeze/trade block given now ({@code null} length = until lifted). */
    static Long expires(Duration length, long now) {
        return length == null ? null : now + length.toMillis();
    }

    /** Money to take: at most the balance and {@code max}, never negative ({@code allow-negative: false}). */
    static long takeable(long requested, long balance, long max) {
        if (requested <= 0 || balance <= 0) {
            return 0;
        }
        return Math.min(Math.min(requested, balance), Math.max(0, max));
    }

    /** Why a sanction can't be restored right now, or {@code null}. */
    static String restoreProblem(Sanction s) {
        if (s == null) {
            return "not-found";
        }
        if (s.kind() != Sanction.Kind.MONEY && s.kind() != Sanction.Kind.ITEM) {
            return "not-restorable";
        }
        if (s.state() == Sanction.State.RESTORED) {
            return "already";
        }
        if (s.state() != Sanction.State.ACTIVE) {
            return "not-held";
        }
        return null;
    }

    /** Why a freeze/trade block can't be lifted, or {@code null}. */
    static String liftProblem(Sanction s, long now) {
        if (s == null) {
            return "not-found";
        }
        if (!s.kind().blocking()) {
            return "not-liftable";
        }
        if (!s.blocksAt(now)) {
            return "not-active";
        }
        return null;
    }
}
