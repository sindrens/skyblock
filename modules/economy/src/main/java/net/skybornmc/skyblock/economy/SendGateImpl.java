package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountType;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.SendGate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Owner F1: a profile can send money away only after it earned {@code send-gate.required} itself; sending to a member of
 * its own island is always allowed. Earned = the sum of the profile's ledger deposits (purse and personal bank) with an
 * income reason ({@code send-gate.earned-reasons}): money from other players, staff, notes and refunds never counts.
 * Once a profile reached the requirement it is remembered ({@code eco_send_unlocked} + memory), so the sum runs at most
 * until then.
 */
final class SendGateImpl implements SendGate {

    private final Database db;
    private final Supplier<EconomyConfig> config;
    /** (from profile, to profile) → both are stored members of the same island. Main thread. */
    private final BiPredicate<UUID, UUID> sameIsland;
    private final LongSupplier clock;
    private final Set<UUID> unlocked = ConcurrentHashMap.newKeySet();
    /** profile → island member profiles (main thread; for the pay limit, so island payments never count). */
    private final java.util.function.Function<UUID, Set<UUID>> islandMates;
    /** profile → its account's playtime in ms (main thread; online players from memory, else 0 → read from the database). */
    private final java.util.function.Function<UUID, Long> onlinePlaytime;
    /** profile → owning account (for the database playtime of offline senders). */
    private final java.util.function.Function<UUID, UUID> ownerOf;
    private final java.util.function.BiConsumer<org.bukkit.entity.Player, Check> teller;

    SendGateImpl(Database db, Supplier<EconomyConfig> config, BiPredicate<UUID, UUID> sameIsland, LongSupplier clock) {
        this(db, config, sameIsland, clock, p -> Set.of(), p -> -1L, p -> p, (pl, c) -> { });
    }

    SendGateImpl(Database db, Supplier<EconomyConfig> config, BiPredicate<UUID, UUID> sameIsland, LongSupplier clock,
                 java.util.function.Function<UUID, Set<UUID>> islandMates, java.util.function.Function<UUID, Long> onlinePlaytime,
                 java.util.function.Function<UUID, UUID> ownerOf, java.util.function.BiConsumer<org.bukkit.entity.Player, Check> teller) {
        this.db = db;
        this.config = config;
        this.sameIsland = sameIsland;
        this.clock = clock;
        this.islandMates = islandMates;
        this.onlinePlaytime = onlinePlaytime;
        this.ownerOf = ownerOf;
        this.teller = teller;
    }

    // ------------------------------------------------------------------ owner 6 Nov 2026: pay limit by playtime

    /** One step of the ladder: from {@code hours} of playtime a profile may send {@code limit} a day (-1 = no limit). */
    record Tier(long hours, long limit) {
    }

    /** {@code "hours:limit"} lines, sorted by hours; broken lines are skipped. Pure. */
    static List<Tier> tiers(List<String> lines) {
        List<Tier> out = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.strip().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                String limit = parts[1].strip();
                out.add(new Tier(Long.parseLong(parts[0].strip()), limit.equals("-1") || limit.equalsIgnoreCase("none") ? -1
                        : net.skybornmc.skyblock.common.util.Format.parseAmount(limit)));
            } catch (RuntimeException e) {
                // skipped; the config check reports it
            }
        }
        out.sort(java.util.Comparator.comparingLong(Tier::hours));
        return out;
    }

    /** The tier a profile with {@code playedHours} is on (the first one when none fits). Pure. */
    static Tier tierFor(List<Tier> tiers, long playedHours) {
        Tier at = tiers.isEmpty() ? new Tier(0, -1) : tiers.getFirst();
        for (Tier t : tiers) {
            if (playedHours >= t.hours()) {
                at = t;
            }
        }
        return at;
    }

    /** The next tier after {@code at}, or null at the top. Pure. */
    static Tier nextTier(List<Tier> tiers, Tier at) {
        for (Tier t : tiers) {
            if (t.hours() > at.hours()) {
                return t;
            }
        }
        return null;
    }

    /** The decision for the daily limit, pure: open when the amount fits in what is left today. */
    static Check decideLimit(List<Tier> tiers, long playedHours, long sentToday, long amount) {
        Tier at = tierFor(tiers, playedHours);
        if (at.limit() < 0) {
            return Check.OPEN;
        }
        long left = Math.max(0, at.limit() - sentToday);
        if (amount <= left) {
            return Check.OPEN;
        }
        Tier next = nextTier(tiers, at);
        return Check.limit(left, next == null ? 0 : next.hours(), next == null ? 0 : next.limit());
    }

    /** Start of today (the server's zone, economy.yml pay-limit.zone) in ms. Pure. */
    static long dayStart(long now, java.time.ZoneId zone) {
        return java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Money a profile sent to other players today (pay, trade, auction buys, money notes), minus what went to its island mates. */
    static long sentToday(Connection c, UUID profile, long since, Set<UUID> mates) throws SQLException {
        long[] total = {0};
        Sql.query(c, "SELECT to_id, amount FROM eco_ledger WHERE from_id = ? AND from_type = ? AND currency = ? AND created_at >= ? "
                + "AND reason IN (?, ?, ?, ?)", rs -> {
            UUID to = Sql.uuid(rs, "to_id");
            long amount = rs.getLong("amount");
            if (to == null || !mates.contains(to)) {
                total[0] += amount;
            }
            return 0;
        }, profile, AccountType.PLAYER.key(), Currency.MONEY.key(), since,
                net.skybornmc.skyblock.common.economy.TxReason.PAY, net.skybornmc.skyblock.common.economy.TxReason.TRADE,
                net.skybornmc.skyblock.common.economy.TxReason.AH_BUY, net.skybornmc.skyblock.common.economy.TxReason.NOTE_WITHDRAW);
        return total[0];
    }

    private static long playtimeHours(Connection c, UUID account) throws SQLException {
        return Sql.queryOne(c, "SELECT playtime_ms FROM players WHERE uuid = ?", rs -> rs.getLong(1), account).orElse(0L) / 3_600_000L;
    }

    @Override
    public CompletableFuture<Check> check(UUID fromProfile, UUID toProfile, Currency currency, long amount) {
        EconomyConfig cfg = config.get();
        boolean island = toProfile != null && fromProfile != null && sameIsland.test(fromProfile, toProfile);
        // Owner 6 Nov 2026: shards are personal; they may only be paid to members of your own island.
        if (currency.isShards() && cfg.shardsIslandOnly) {
            return CompletableFuture.completedFuture(island ? Check.OPEN : Check.ISLAND_ONLY);
        }
        if (island || fromProfile == null || currency != Currency.MONEY) {
            return check(fromProfile, toProfile, currency);
        }
        List<Tier> tiers = tiers(cfg.payLimitTiers);
        if (tiers.isEmpty()) {
            return check(fromProfile, toProfile, currency);
        }
        Set<UUID> mates = Set.copyOf(islandMates.apply(fromProfile));
        long memory = onlinePlaytime.apply(fromProfile);
        UUID account = ownerOf.apply(fromProfile);
        long since = dayStart(clock.getAsLong(), zone(cfg));
        return check(fromProfile, toProfile, currency).thenCompose(first -> {
            if (!first.allowed()) {
                return CompletableFuture.completedFuture(first);
            }
            return db.async(c -> {
                long hours = memory >= 0 ? memory / 3_600_000L : playtimeHours(c, account);
                return decideLimit(tiers, hours, sentToday(c, fromProfile, since, mates), amount);
            });
        });
    }

    private static java.time.ZoneId zone(EconomyConfig cfg) {
        try {
            return java.time.ZoneId.of(cfg.payLimitZone);
        } catch (RuntimeException e) {
            return java.time.ZoneId.of("Europe/Oslo");
        }
    }

    /** The ladder for the /paylimit view: tiers, the sender's hours and what it sent today. Main thread; completes off it. */
    CompletableFuture<long[]> status(UUID profile) {
        EconomyConfig cfg = config.get();
        Set<UUID> mates = Set.copyOf(islandMates.apply(profile));
        long memory = onlinePlaytime.apply(profile);
        UUID account = ownerOf.apply(profile);
        long since = dayStart(clock.getAsLong(), zone(cfg));
        return db.async(c -> new long[]{memory >= 0 ? memory / 60_000L : playtimeHours(c, account) * 60, sentToday(c, profile, since, mates)});
    }

    @Override
    public void tell(org.bukkit.entity.Player player, Check check) {
        teller.accept(player, check);
    }

    /**
     * The decision, pure: open when the rule is off, the currency is not covered, the receiver is on the sender's island
     * or the profile already earned enough.
     */
    static Check decide(long required, boolean gated, boolean sameIsland, boolean unlocked, long earned) {
        if (required <= 0 || !gated || sameIsland || unlocked) {
            return Check.OPEN;
        }
        return new Check(earned >= required, earned, required);
    }

    @Override
    public CompletableFuture<Check> check(UUID fromProfile, UUID toProfile, Currency currency) {
        EconomyConfig cfg = config.get();
        long required = cfg.sendGateRequired;
        boolean island = toProfile != null && fromProfile != null && sameIsland.test(fromProfile, toProfile);
        Check quick = decide(required, cfg.sendGated(currency), island, fromProfile != null && unlocked.contains(fromProfile), 0);
        if (quick == Check.OPEN || fromProfile == null) {
            return CompletableFuture.completedFuture(quick == Check.OPEN ? quick : new Check(false, 0, required));
        }
        List<String> reasons = List.copyOf(cfg.sendGateEarnedReasons);
        return db.async(c -> {
            if (isUnlocked(c, fromProfile)) {
                return Check.OPEN;
            }
            long earned = earned(c, fromProfile, reasons);
            Check result = decide(required, true, false, false, earned);
            if (result.allowed()) {
                remember(c, fromProfile, earned);
            }
            return result;
        }).thenApply(result -> {
            if (result.allowed()) {
                unlocked.add(fromProfile);
            }
            return result;
        });
    }

    private static boolean isUnlocked(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM eco_send_unlocked WHERE profile_id = ?", rs -> 1, profile).isPresent();
    }

    /** Income of a profile in money: ledger deposits to its purse or personal bank with one of {@code reasons}. */
    static long earned(Connection c, UUID profile, List<String> reasons) throws SQLException {
        if (reasons.isEmpty()) {
            return 0;
        }
        List<Object> params = new ArrayList<>();
        params.add(profile);
        params.add(AccountType.PLAYER.key());
        params.add(AccountType.BANK.key());
        params.add(Currency.MONEY.key());
        params.addAll(reasons);
        String in = "?,".repeat(reasons.size() - 1) + "?";
        return Sql.queryOne(c, "SELECT COALESCE(SUM(amount), 0) FROM eco_ledger WHERE to_id = ? AND to_type IN (?, ?) "
                + "AND currency = ? AND from_id IS NULL AND reason IN (" + in + ")", rs -> rs.getLong(1), params.toArray()).orElse(0L);
    }

    private void remember(Connection c, UUID profile, long earned) {
        try {
            if (!isUnlocked(c, profile)) {
                Sql.update(c, "INSERT INTO eco_send_unlocked (profile_id, earned, unlocked_at) VALUES (?, ?, ?)", profile, earned,
                        clock.getAsLong());
            }
        } catch (SQLException e) {
            // a parallel check inserted it first: same result
        }
    }

    /** Forgets the memory copy (profile deleted). The row stays harmless: the profile id is never reused. */
    void forget(UUID profile) {
        unlocked.remove(profile);
    }
}
