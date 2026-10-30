package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every wardrobe data change as one atomic database transaction (WARDROBE §2.4, §4.4, §8.4). Purchases go through the
 * economy's guarded transaction ({@code EconomyService#executeGuarded}): the new wardrobe row (and Sigil/claim) and the
 * payment commit together or not at all, with an idempotency key per purchase. Futures never complete exceptionally.
 * No Bukkit (unit-tested on H2).
 */
final class WardrobeOps {

    /** Pays together with extra SQL (the economy's guarded transaction). */
    @FunctionalInterface
    interface Payer {
        CompletableFuture<TxResult> run(Transaction transaction, TxGuard guard);
    }

    enum Status {
        OK,
        /** Not enough money/crystals/gems. Nothing changed. */
        NOT_ENOUGH,
        /** The profile already owns this set. Nothing changed. */
        OWNED,
        /** The account has no Sigil for this gem set (claim). Nothing changed. */
        NO_SIGIL,
        /** This profile already claimed the set this season. Nothing changed. */
        CLAIMED,
        /** The account already has this Sigil (buy again refused; claim instead). Nothing changed. */
        HAS_SIGIL,
        /** The token row is not active (a copy) or is for another set. Nothing changed. */
        TOKEN_INVALID,
        /** The entry/item is gone, not active or not the profile's. Nothing changed. */
        GONE,
        /** The Wardrobe Chest is full. Nothing changed. */
        FULL,
        /** The item is already in the chest: the one in hand is a stale copy. Nothing changed. */
        STALE,
        /** A merge rule refused (re-checked on the locked rows). Nothing changed. */
        REFUSED,
        /** A tome can't add anything to this set (at or past the tome level cap). Nothing changed. */
        TOME_CAPPED,
        /** Database or economy error. Nothing changed. */
        ERROR
    }

    /**
     * @param entry   the new wardrobe entry (purchases, claims, tokens, staff give)
     * @param stored  the chest row taken out (take)
     * @param balance balance after a payment, when known
     */
    record Result(Status status, WardrobeStore.Entry entry, WardrobeStore.Stored stored, Long balance) {
        static Result of(Status s) {
            return new Result(s, null, null, null);
        }

        static Result ok(WardrobeStore.Entry entry) {
            return new Result(Status.OK, entry, null, null);
        }

        boolean ok() {
            return status == Status.OK;
        }
    }

    /** Who acts, for ledger and audit rows. */
    record Who(UUID player, String name, UUID profile) {
        Actor actor() {
            return Actor.player(player, name);
        }
    }

    /** Thrown inside a plain transaction to roll it back with a status. */
    private static final class Refused extends RuntimeException {
        final Status status;

        Refused(Status status) {
            super(status.name(), null, false, false);
            this.status = status;
        }
    }

    /** Why a guard said no (reset on every retry). */
    private static final class Why {
        Status status;
    }

    static final String REASON_BUY = "wardrobe_buy";
    static final String REASON_MERGE = "wardrobe_merge";

    private static final LoadoutStore LOADOUTS = new LoadoutStore();

    private final Database db;
    private final WardrobeStore store;
    private final Payer payer;
    private final LongSupplier clock;
    private final Logger log;

    WardrobeOps(Database db, WardrobeStore store, Payer payer, LongSupplier clock, Logger log) {
        this.db = db;
        this.store = store;
        this.payer = payer;
        this.clock = clock;
        this.log = log;
    }

    // ------------------------------------------------------------------ buying

    /**
     * Buys a set (money, crystals or gems). Gem purchases also write the account's Sigil and this profile's claim for the
     * season (WD5). Idempotency key {@code wardrobe_buy:<entry id>}: a retried request can never charge twice, and a
     * second purchase of an owned set rolls back before anything is paid.
     */
    CompletableFuture<Result> buy(Who who, SetDef def, int season) {
        Currency currency = def.source().type().currency();
        long price = def.source().price();
        if (currency == null || price <= 0) {
            return CompletableFuture.completedFuture(Result.of(Status.ERROR));
        }
        long now = clock.getAsLong();
        String origin = def.source().type().key();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(who.profile(), def.id(), origin, null, now);
        Why why = new Why();
        TxGuard guard = c -> {
            why.status = null;
            if (currency == Currency.GEMS) {
                if (!store.insertSigil(c, who.player(), def.id(), price, who.profile(), now)) {
                    why.status = Status.HAS_SIGIL;
                    return false;
                }
                if (!store.insertClaim(c, who.profile(), def.id(), season, entry.entryId(), now)) {
                    why.status = Status.CLAIMED;
                    return false;
                }
            }
            if (!store.insertEntry(c, entry, season, who.player())) {
                why.status = Status.OWNED;
                return false;
            }
            return true;
        };
        Transaction tx = Transaction.withdraw(AccountRef.player(who.profile()), currency, price, REASON_BUY)
                .actor(who.actor())
                .reference(def.id())
                .idempotencyKey(REASON_BUY + ":" + entry.entryId())
                .detail("set", def.id())
                .detail("entry", entry.entryId().toString())
                .detail("target_name", who.name());
        return safe("buy " + def.id(), () -> payer.run(tx, guard).thenApply(r -> switch (r.status()) {
            case SUCCESS -> new Result(Status.OK, entry, null, r.fromBalance());
            case INSUFFICIENT_FUNDS -> Result.of(Status.NOT_ENOUGH);
            case DUPLICATE -> Result.of(why.status != null ? why.status : Status.ERROR);
            default -> Result.of(Status.ERROR);
        }));
    }

    /** Claims a gem set on this profile for free (the account owns its Sigil; once per profile per season). */
    CompletableFuture<Result> claim(Who who, String setId, int season) {
        long now = clock.getAsLong();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(who.profile(), setId, "gems_claim", null, now);
        return plain("claim " + setId, c -> {
            if (!store.hasSigil(c, who.player(), setId)) {
                throw new Refused(Status.NO_SIGIL);
            }
            if (!store.insertClaim(c, who.profile(), setId, season, entry.entryId(), now)) {
                throw new Refused(Status.CLAIMED);
            }
            if (!store.insertEntry(c, entry, season, who.player())) {
                throw new Refused(Status.OWNED);
            }
            return new Result(Status.OK, entry, null, null);
        });
    }

    // ------------------------------------------------------------------ tokens

    /** Writes a new token row (before the item is handed out). */
    CompletableFuture<Boolean> mintToken(UUID tokenId, String setId, String origin, UUID owner) {
        long now = clock.getAsLong();
        return db.transaction(c -> {
            store.insertToken(c, tokenId, setId, origin, owner, now);
            return true;
        }).exceptionally(e -> {
            log.log(Level.SEVERE, "Wardrobe: could not write Set Token " + tokenId, Database.cause(e));
            return false;
        });
    }

    /** A token that was minted but never reached a player: active → void. */
    CompletableFuture<Boolean> voidToken(UUID tokenId, String reason) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.moveToken(c, tokenId, WardrobeStore.TokenState.ACTIVE, WardrobeStore.TokenState.VOID, null, null, now, reason))
                .exceptionally(e -> false);
    }

    /**
     * Redeems a token (the item was already taken from the player): the token row active → redeemed and the set
     * added, in one transaction. TOKEN_INVALID = the item was a copy (keep it taken); OWNED/ERROR = give it back.
     */
    CompletableFuture<Result> redeem(Who who, UUID tokenId, String setId, int season) {
        long now = clock.getAsLong();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(who.profile(), setId, "token", tokenId.toString(), now);
        return plain("redeem " + tokenId, c -> {
            Optional<WardrobeStore.Token> token = store.token(c, tokenId, true);
            if (token.isEmpty() || token.get().state() != WardrobeStore.TokenState.ACTIVE || !token.get().setId().equals(setId)) {
                throw new Refused(Status.TOKEN_INVALID);
            }
            if (!store.insertEntry(c, entry, season, who.player())) {
                throw new Refused(Status.OWNED);
            }
            if (!store.moveToken(c, tokenId, WardrobeStore.TokenState.ACTIVE, WardrobeStore.TokenState.REDEEMED, who.player(), who.profile(),
                    now, "redeem")) {
                throw new Refused(Status.TOKEN_INVALID);
            }
            return new Result(Status.OK, entry, null, null);
        });
    }

    /** A copied token was used: active rows are left alone, the copy is just recorded as taken (audit). */
    CompletableFuture<Optional<WardrobeStore.Token>> tokenRow(UUID tokenId) {
        return db.async(c -> store.token(c, tokenId, false)).exceptionally(e -> Optional.empty());
    }

    // ------------------------------------------------------------------ staff

    CompletableFuture<Result> giveSet(UUID profile, String setId, UUID staff, int season) {
        long now = clock.getAsLong();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(profile, setId, "staff", staff == null ? null : staff.toString(), now);
        return plain("give " + setId, c -> {
            if (!store.insertEntry(c, entry, season, staff)) {
                throw new Refused(Status.OWNED);
            }
            return new Result(Status.OK, entry, null, null);
        });
    }

    CompletableFuture<Result> removeSet(UUID entryId, UUID staff) {
        long now = clock.getAsLong();
        return plain("remove " + entryId, c -> {
            Optional<WardrobeStore.Entry> entry = store.entry(c, entryId);
            if (entry.isEmpty() || !store.removeEntry(c, entryId, staff, now)) {
                throw new Refused(Status.GONE);
            }
            return new Result(Status.OK, entry.get(), null, null);
        });
    }

    // ------------------------------------------------------------------ set levels (W2)

    /**
     * Saves earned set XP (entry id → XP to add) in one transaction. XP of entries that are not active any more is
     * dropped. False on a database error (the caller keeps the XP for the next try).
     */
    CompletableFuture<Boolean> flushXp(Map<UUID, Double> deltas, SetLevels levels) {
        if (deltas.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        long now = clock.getAsLong();
        Map<UUID, Double> copy = new TreeMap<>(deltas); // fixed lock order
        try {
            return db.transaction(c -> {
                for (Map.Entry<UUID, Double> e : copy.entrySet()) {
                    store.addXp(c, e.getKey(), e.getValue(), levels, now);
                }
                return true;
            }).exceptionally(e -> {
                log.log(Level.WARNING, "Wardrobe: could not save set XP of " + copy.size() + " set(s) (kept for the next try)", Database.cause(e));
                return false;
            });
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Wardrobe: could not start saving set XP", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /** Blocking flush for shutdown. */
    boolean flushXpNow(Map<UUID, Double> deltas, SetLevels levels) {
        if (deltas.isEmpty()) {
            return true;
        }
        long now = clock.getAsLong();
        try {
            return db.blockingTransaction(c -> {
                for (Map.Entry<UUID, Double> e : new TreeMap<>(deltas).entrySet()) {
                    store.addXp(c, e.getKey(), e.getValue(), levels, now);
                }
                return true;
            });
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Wardrobe: could not save set XP on shutdown", e);
            return false;
        }
    }

    /** Staff: sets an active entry of a profile to a level (total XP = the start of that level). GONE if not active. */
    CompletableFuture<Result> setLevel(UUID entryId, UUID profile, int level, SetLevels levels, UUID staff) {
        long now = clock.getAsLong();
        return plain("set level " + entryId, c -> {
            Optional<WardrobeStore.Entry> e = store.entryForUpdate(c, entryId);
            if (e.isEmpty() || e.get().state() != WardrobeStore.EntryState.ACTIVE || !e.get().profile().equals(profile)) {
                throw new Refused(Status.GONE);
            }
            int l = Math.max(0, Math.min(level, levels.maxLevel()));
            double xp = levels.total(l, e.get().isMerged());
            if (!store.writeXp(c, entryId, xp, l, staff, now)) {
                throw new Refused(Status.GONE);
            }
            return Result.ok(e.get().withXp(xp, l));
        });
    }

    // ------------------------------------------------------------------ merging (W2, §5)

    /**
     * What a merge needs: both inputs (as the menu saw them), the look, the fee and the inputs' XP not saved yet
     * (written first, inside the same transaction, so a level reached a moment ago counts).
     */
    record MergeRequest(Who who, UUID entryA, SetDef defA, UUID entryB, SetDef defB, String lookSetId, long fee,
                        double pendingA, double pendingB, SetLevels levels, int season, MergeEnchants enchants) {
        MergeRequest {
            enchants = enchants == null ? MergeEnchants.NONE : enchants;
        }

        /** A merge whose inputs carry no armor enchants. */
        MergeRequest(Who who, UUID entryA, SetDef defA, UUID entryB, SetDef defB, String lookSetId, long fee,
                     double pendingA, double pendingB, SetLevels levels, int season) {
            this(who, entryA, defA, entryB, defB, lookSetId, fee, pendingA, pendingB, levels, season, MergeEnchants.NONE);
        }
    }

    /**
     * E5 (WARDROBE §5.1 "Enchants"): the merged pieces keep the enchants of the look set's entry; the other entry's enchants
     * come back as enchant books into the Wardrobe Chest (even past its size, so nothing is lost and no merge is blocked).
     *
     * @param otherExpected the other entry's enchants as the menu saw them (re-checked on the locked rows: a change = GONE)
     * @param books         one prepared book per enchant of {@code otherExpected} ({@link MergeRules#returnedBooks})
     * @param sink          writes a book's {@code enchant_books} row in the same transaction (the enchants module)
     */
    record MergeEnchants(Map<ArmorPiece, List<ToolPerk>> otherExpected, List<ReturnedBook> books, BookSink sink) {
        static final MergeEnchants NONE = new MergeEnchants(Map.of(), List.of(), null);

        MergeEnchants {
            otherExpected = otherExpected == null ? Map.of() : Map.copyOf(otherExpected);
            books = books == null ? List.of() : List.copyOf(books);
        }
    }

    /**
     * A book an armor enchant comes back as: the item was built on the main thread (id = its unique id), its row and its
     * chest row are written in the merge transaction.
     */
    record ReturnedBook(UUID id, String enchant, int level, ArmorPiece piece, String kind, String itemId, String name, byte[] item) {
    }

    /** Writes a returned book's {@code enchant_books} row inside the merge transaction. */
    @FunctionalInterface
    interface BookSink {
        void insert(Connection c, ReturnedBook book, UUID player, UUID profile, String reason) throws SQLException;
    }

    /** Why the guard refused (reset on every retry). */
    private static final class MergeWhy {
        Status status;
        MergeRules.Refusal refusal;
        Map<ArmorPiece, List<ToolPerk>> enchants = Map.of();
    }

    /** Result of a merge: the new entry, or the status (and the refusal for REFUSED). */
    record MergeResult(Status status, WardrobeStore.Entry entry, MergeRules.Refusal refusal, Long balance,
                       Map<ArmorPiece, List<ToolPerk>> enchants) {
        MergeResult {
            enchants = enchants == null ? Map.of() : Map.copyOf(enchants);
        }

        MergeResult(Status status, WardrobeStore.Entry entry, MergeRules.Refusal refusal, Long balance) {
            this(status, entry, refusal, balance, Map.of());
        }

        boolean ok() {
            return status == Status.OK;
        }
    }

    /**
     * Merges two entries in <b>one</b> database transaction (WD22): the inputs' unsaved XP is written, both rows are
     * locked and re-checked against every rule, both become {@code merged} (their worn slots emptied), the new entry is
     * inserted at level 0, the merge record written and the crystal fee paid (idempotency key
     * {@code wardrobe_merge:<new entry>}). Any failure rolls all of it back: a crash at any point leaves either both
     * inputs or the merged set, never both and never neither.
     */
    CompletableFuture<MergeResult> merge(MergeRequest r) {
        long now = clock.getAsLong();
        WardrobeStore.Entry fresh = WardrobeStore.Entry.merged(r.who().profile(), r.defA().id(), r.defB().id(), r.lookSetId(), now);
        MergeWhy why = new MergeWhy();
        TxGuard guard = c -> {
            why.status = null;
            why.refusal = null;
            why.enchants = Map.of();
            // Lock in a fixed order (two merges of overlapping entries can't deadlock).
            boolean aFirst = r.entryA().compareTo(r.entryB()) < 0;
            UUID first = aFirst ? r.entryA() : r.entryB();
            UUID second = aFirst ? r.entryB() : r.entryA();
            Optional<WardrobeStore.Entry> one = store.addXp(c, first, aFirst ? r.pendingA() : r.pendingB(), r.levels(), now);
            Optional<WardrobeStore.Entry> two = store.addXp(c, second, aFirst ? r.pendingB() : r.pendingA(), r.levels(), now);
            if (one.isEmpty() || two.isEmpty()) {
                why.status = Status.GONE;
                return false;
            }
            WardrobeStore.Entry a = aFirst ? one.get() : two.get();
            WardrobeStore.Entry b = aFirst ? two.get() : one.get();
            if (!a.profile().equals(r.who().profile()) || !b.profile().equals(r.who().profile())
                    || !a.setId().equals(r.defA().id()) || !b.setId().equals(r.defB().id())) {
                why.status = Status.GONE;
                return false;
            }
            Optional<MergeRules.Refusal> refusal = MergeRules.check(
                    new MergeRules.Side(a.entryId(), r.defA(), a.level(), a.isMerged(), a.state() == WardrobeStore.EntryState.ACTIVE),
                    new MergeRules.Side(b.entryId(), r.defB(), b.level(), b.isMerged(), b.state() == WardrobeStore.EntryState.ACTIVE),
                    r.levels().maxLevel());
            if (refusal.isPresent()) {
                why.status = Status.REFUSED;
                why.refusal = refusal.get();
                return false;
            }
            if (!store.markMerged(c, a.entryId(), r.who().player(), now) || !store.markMerged(c, b.entryId(), r.who().player(), now)) {
                why.status = Status.GONE;
                return false;
            }
            store.insertMerged(c, fresh, r.season(), r.who().player());
            store.insertMergeRecord(c, fresh.entryId(), r.who().profile(), a, b, r.lookSetId(), r.fee(), r.who().player(), now);
            // E5: the look's enchants move to the merged entry, the other entry's come back as books.
            WardrobeStore.Entry look = a.setId().equals(r.lookSetId()) ? a : b;
            WardrobeStore.Entry other = look == a ? b : a;
            if (!MergeRules.sameEnchants(store.entryEnchants(c, other.entryId()), r.enchants().otherExpected())) {
                why.status = Status.GONE;
                return false;
            }
            Map<ArmorPiece, List<ToolPerk>> kept = store.entryEnchants(c, look.entryId());
            for (Map.Entry<ArmorPiece, List<ToolPerk>> e : kept.entrySet()) {
                store.savePiece(c, fresh.entryId(), e.getKey(), e.getValue());
            }
            store.deletePieces(c, a.entryId());
            store.deletePieces(c, b.entryId());
            if (!r.enchants().books().isEmpty() && r.enchants().sink() == null) {
                why.status = Status.ERROR;
                return false;
            }
            for (ReturnedBook book : r.enchants().books()) {
                r.enchants().sink().insert(c, book, r.who().player(), r.who().profile(), REASON_MERGE + ":" + fresh.entryId());
                WardrobeStore.Stored row = new WardrobeStore.Stored(book.id(), r.who().profile(), book.kind(), book.itemId(), book.name(),
                        book.item(), WardrobeStore.StorageState.STORED, null, now);
                if (store.put(c, row, Integer.MAX_VALUE, now) != WardrobeStore.PutResult.OK) {
                    why.status = Status.ERROR;
                    return false;
                }
            }
            why.enchants = kept;
            // W3 (WD22): loadouts that wore either input now wear the merged set.
            LOADOUTS.repoint(c, r.who().profile(), a.entryId(), fresh.entryId(), now);
            LOADOUTS.repoint(c, r.who().profile(), b.entryId(), fresh.entryId(), now);
            return true;
        };
        if (r.fee() <= 0) {
            return safeMerge(() -> db.transaction(c -> {
                if (!guard.holds(c)) {
                    throw new Refused(Status.ERROR);
                }
                return new MergeResult(Status.OK, fresh, null, null, why.enchants);
            }).exceptionally(e -> {
                Throwable cause = Database.cause(e);
                if (cause instanceof Refused) {
                    return new MergeResult(why.status != null ? why.status : Status.ERROR, null, why.refusal, null);
                }
                log.log(Level.SEVERE, "Wardrobe: merge failed and was rolled back", cause);
                return new MergeResult(Status.ERROR, null, null, null);
            }));
        }
        Transaction tx = Transaction.withdraw(AccountRef.player(r.who().profile()), Currency.CRYSTALS, r.fee(), REASON_MERGE)
                .actor(r.who().actor())
                .reference(r.defA().id() + "+" + r.defB().id())
                .idempotencyKey(REASON_MERGE + ":" + fresh.entryId())
                .detail("entry", fresh.entryId().toString())
                .detail("set_a", r.defA().id())
                .detail("set_b", r.defB().id())
                .detail("target_name", r.who().name());
        return safeMerge(() -> payer.run(tx, guard).thenApply(res -> switch (res.status()) {
            case SUCCESS -> new MergeResult(Status.OK, fresh, null, res.fromBalance(), why.enchants);
            case INSUFFICIENT_FUNDS -> new MergeResult(Status.NOT_ENOUGH, null, null, null);
            case DUPLICATE -> new MergeResult(why.status != null ? why.status : Status.ERROR, null, why.refusal, null);
            default -> new MergeResult(Status.ERROR, null, null, null);
        }));
    }

    private CompletableFuture<MergeResult> safeMerge(java.util.function.Supplier<CompletableFuture<MergeResult>> start) {
        try {
            return start.get().exceptionally(e -> {
                log.log(Level.SEVERE, "Wardrobe: merge failed", Database.cause(e));
                return new MergeResult(Status.ERROR, null, null, null);
            });
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Wardrobe: could not start a merge", e);
            return CompletableFuture.completedFuture(new MergeResult(Status.ERROR, null, null, null));
        }
    }

    // ------------------------------------------------------------------ tomes (W2, WD13)

    /** Writes a new tome row (before the item is handed out). */
    CompletableFuture<Boolean> mintTome(UUID tomeId, String kind, long xp, String origin, UUID owner) {
        long now = clock.getAsLong();
        return db.transaction(c -> {
            store.insertTome(c, tomeId, kind, xp, origin, owner, now);
            return true;
        }).exceptionally(e -> {
            log.log(Level.SEVERE, "Wardrobe: could not write Wardrobe Tome " + tomeId, Database.cause(e));
            return false;
        });
    }

    /** A tome that was minted but never reached a player: active → void. */
    CompletableFuture<Boolean> voidTome(UUID tomeId, String reason) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.moveTome(c, tomeId, WardrobeStore.TokenState.ACTIVE, WardrobeStore.TokenState.VOID, null, null, now, reason))
                .exceptionally(e -> false);
    }

    /**
     * Uses a tome on an entry in one transaction: the tome row active → redeemed (used) and the entry's XP raised by the
     * row's amount, but never past the start of {@code maxTomeLevel}. TOKEN_INVALID = the item was a copy (take it);
     * TOME_CAPPED / GONE = nothing happened (keep the item).
     */
    CompletableFuture<Result> useTome(Who who, UUID tomeId, UUID entryId, double pending, SetLevels levels, int maxTomeLevel) {
        long now = clock.getAsLong();
        return plain("tome " + tomeId, c -> {
            Optional<WardrobeStore.Tome> tome = store.tome(c, tomeId, true);
            if (tome.isEmpty() || tome.get().state() != WardrobeStore.TokenState.ACTIVE) {
                throw new Refused(Status.TOKEN_INVALID);
            }
            Optional<WardrobeStore.Entry> entry = store.addXp(c, entryId, pending, levels, now);
            if (entry.isEmpty() || !entry.get().profile().equals(who.profile())) {
                throw new Refused(Status.GONE);
            }
            WardrobeStore.Entry e = entry.get();
            if (!levels.tomeUsable(e.xp(), maxTomeLevel, e.isMerged())) {
                throw new Refused(Status.TOME_CAPPED);
            }
            double xp = levels.tome(e.xp(), tome.get().xp(), maxTomeLevel, e.isMerged());
            int level = levels.level(xp, e.isMerged());
            if (!store.writeXp(c, entryId, xp, level, who.player(), now)
                    || !store.moveTome(c, tomeId, WardrobeStore.TokenState.ACTIVE, WardrobeStore.TokenState.REDEEMED, who.player(), entryId, now, "used")) {
                throw new Refused(Status.GONE);
            }
            return Result.ok(e.withXp(xp, level));
        });
    }

    CompletableFuture<Optional<WardrobeStore.Tome>> tomeRow(UUID tomeId) {
        return db.async(c -> store.tome(c, tomeId, false)).exceptionally(e -> Optional.empty());
    }

    // ------------------------------------------------------------------ equipping

    /** Writes worn slots (new nonces) and emptied slots in one transaction; GONE if an entry is not the profile's active set. */
    CompletableFuture<Result> equip(UUID profile, Collection<WardrobeStore.Equipped> wear, Collection<ArmorPiece> off) {
        long now = clock.getAsLong();
        return plain("equip", c -> {
            if (!store.writeEquipped(c, profile, wear, off, now)) {
                throw new Refused(Status.GONE);
            }
            return Result.of(Status.OK);
        });
    }

    // ------------------------------------------------------------------ storage (escrow)

    /** Store: the item is already out of the inventory (kept by the caller). OK, FULL, STALE or ERROR. */
    CompletableFuture<Result> store(WardrobeStore.Stored item, int capacity) {
        long now = clock.getAsLong();
        return plain("store " + item.itemUuid(), c -> switch (store.put(c, item, capacity, now)) {
            case OK -> new Result(Status.OK, null, item, null);
            case FULL -> throw new Refused(Status.FULL);
            case STALE -> throw new Refused(Status.STALE);
        });
    }

    /** Take, step 1: stored → releasing with the plan id; returns the row (with the serialized item) to hand out. */
    CompletableFuture<Result> take(UUID itemUuid, UUID profile, UUID plan) {
        long now = clock.getAsLong();
        return plain("take " + itemUuid, c -> {
            if (!store.markReleasing(c, itemUuid, profile, plan, now)) {
                throw new Refused(Status.GONE);
            }
            return new Result(Status.OK, null, store.storedItem(c, itemUuid, false).orElseThrow(() -> new Refused(Status.GONE)), null);
        });
    }

    /** Take, step 2: the item is in the inventory. */
    CompletableFuture<Boolean> released(UUID itemUuid, UUID plan) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.markReleased(c, itemUuid, plan, now)).exceptionally(e -> {
            log.log(Level.WARNING, "Wardrobe: could not mark " + itemUuid + " released (fixed on the next join)", Database.cause(e));
            return false;
        });
    }

    /** The item could not be handed out: back in the chest. */
    CompletableFuture<Boolean> backToStored(UUID itemUuid, UUID plan) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.backToStored(c, itemUuid, plan, now)).exceptionally(e -> false);
    }

    /** Rows of the chest being handed out (recovery on join) and chest rows of items that are also in the inventory. */
    record Recovery(List<WardrobeStore.Stored> releasing, java.util.Set<UUID> stale) {
    }

    CompletableFuture<Recovery> recovery(UUID profile, Collection<UUID> inventoryIds) {
        return db.async(c -> new Recovery(store.releasing(c, profile), store.inChest(c, inventoryIds)));
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Work {
        Result run(Connection c) throws SQLException;
    }

    private CompletableFuture<Result> plain(String what, Work work) {
        return safe(what, () -> db.transaction(work::run).exceptionally(e -> {
            Throwable cause = Database.cause(e);
            if (cause instanceof Refused r) {
                return Result.of(r.status);
            }
            log.log(Level.SEVERE, "Wardrobe: " + what + " failed and was rolled back", cause);
            return Result.of(Status.ERROR);
        }));
    }

    private CompletableFuture<Result> safe(String what, java.util.function.Supplier<CompletableFuture<Result>> start) {
        try {
            return start.get().exceptionally(e -> {
                log.log(Level.SEVERE, "Wardrobe: " + what + " failed", Database.cause(e));
                return Result.of(Status.ERROR);
            });
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Wardrobe: could not start " + what, e);
            return CompletableFuture.completedFuture(Result.of(Status.ERROR));
        }
    }
}
