package net.skybornmc.skyblock.auction;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL of the auction house ({@code db/auction}). Bukkit-free so the state machine runs against H2 in unit tests.
 *
 * <p>Every state change is a conditional {@code UPDATE … WHERE id = ? AND status = ?}; only the caller whose UPDATE hit
 * exactly one row may continue. Moving an item into the collection bin happens in the same transaction as the state
 * change that frees it ({@code INSERT … SELECT} from the listing row), so an item is always in exactly one place.</p>
 */
final class AuctionStore {

    private static final String COLUMNS = "id, season_id, seller, seller_name, currency, price, fee, tax, item, item_key, item_uuid, "
            + "material, amount, item_name, category, status, created_at, expires_at, buyer, buyer_name, buy_started_at, ended_at, "
            + "seller_notice";
    private static final String OPEN = "('pending', 'active', 'buying')";
    private static final String TO_COLLECTION = "INSERT INTO ah_collection (owner, listing_id, source, item, material, amount, "
            + "item_name, created_at) SELECT %s, id, ?, item, material, amount, item_name, ? FROM ah_listings WHERE id = ?";

    private final Database db;

    AuctionStore(Database db) {
        this.db = db;
    }

    /** A listing about to be inserted. */
    record NewListing(int seasonId, UUID seller, String sellerName, String currency, long price, long fee, ItemSnapshot item,
                      ListingStatus status, long createdAt, long expiresAt) {
    }

    /** Result of {@link #create}: the inserted listing, or why the rules refused it (nothing inserted). */
    record Created(Listing listing, ListingRules.Rejection rejection) {
    }

    // ================================================================== listing

    /**
     * Checks slots, cooldown and relist limit against the seller's history and inserts the listing (with the serialized
     * item) in <b>one transaction</b>.
     */
    CompletableFuture<Created> create(NewListing n, AuctionConfig config, int slots) {
        return db.transaction(c -> {
            ListingRules.Stats stats = stats(c, n.seller(), n.item().itemKey(), ListingRules.relistSince(config, n.createdAt()));
            Optional<ListingRules.Rejection> problem = ListingRules.history(config, stats, slots, n.createdAt());
            if (problem.isPresent()) {
                return new Created(null, problem.get());
            }
            ItemSnapshot item = n.item();
            long id = Sql.insertReturningId(c, "INSERT INTO ah_listings (season_id, seller, seller_name, currency, price, fee, tax, "
                            + "item, item_key, item_uuid, material, amount, item_name, category, status, created_at, expires_at, "
                            + "seller_notice) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    n.seasonId(), n.seller(), n.sellerName(), n.currency(), n.price(), n.fee(), item.bytes(), item.itemKey(),
                    item.itemUuid(), item.material(), item.amount(), item.name(), item.category().key(), n.status().key(),
                    n.createdAt(), n.expiresAt(), false);
            return new Created(new Listing(id, n.seller(), n.sellerName(), n.currency(), n.price(), n.fee(), 0, item, n.status(),
                    n.createdAt(), n.expiresAt(), null, null, null, null, false), null);
        });
    }

    static ListingRules.Stats stats(Connection c, UUID seller, String itemKey, long since) throws SQLException {
        int open = Sql.queryOne(c, "SELECT COUNT(*) FROM ah_listings WHERE seller = ? AND status IN " + OPEN,
                rs -> rs.getInt(1), seller).orElse(0);
        long last = Sql.queryOne(c, "SELECT MAX(created_at) FROM ah_listings WHERE seller = ?",
                rs -> rs.getLong(1), seller).orElse(0L);
        int same = Sql.queryOne(c, "SELECT COUNT(*) FROM ah_listings WHERE seller = ? AND item_key = ? AND created_at >= ?",
                rs -> rs.getInt(1), seller, itemKey, since).orElse(0);
        return new ListingRules.Stats(open, last, same);
    }

    /** PENDING → ACTIVE once the listing fee is paid. */
    CompletableFuture<Boolean> activate(long id) {
        return db.async(c -> Sql.update(c, "UPDATE ah_listings SET status = 'active' WHERE id = ? AND status = 'pending'", id) == 1);
    }

    /**
     * PENDING → FAILED (fee could not be paid); the item goes to the seller's collection bin in the same transaction.
     *
     * @return the collection row id, or empty if the listing was not pending
     */
    CompletableFuture<Optional<Long>> failPending(long id, long now) {
        return db.transaction(c -> {
            if (Sql.update(c, "UPDATE ah_listings SET status = 'failed', ended_at = ? WHERE id = ? AND status = 'pending'", now, id) != 1) {
                return Optional.<Long>empty();
            }
            return Optional.of(toCollection(c, "seller", CollectionEntry.Source.FAILED, id, now));
        });
    }

    // ================================================================== buying

    /**
     * ACTIVE → BUYING for one buyer: only if still active, not ended and not the seller's own listing. At most one buyer
     * can hold a listing; everyone else gets {@code false}.
     */
    CompletableFuture<Boolean> claimForBuy(long id, UUID buyer, String buyerName, long now) {
        return db.async(c -> Sql.update(c, "UPDATE ah_listings SET status = 'buying', buyer = ?, buyer_name = ?, buy_started_at = ? "
                + "WHERE id = ? AND status = 'active' AND expires_at > ? AND seller <> ?", buyer, buyerName, now, id, now, buyer) == 1);
    }

    /** BUYING → ACTIVE: the buyer could not pay (nothing was taken). */
    CompletableFuture<Boolean> revertBuy(long id, UUID buyer) {
        return db.async(c -> Sql.update(c, "UPDATE ah_listings SET status = 'active', buyer = NULL, buyer_name = NULL, "
                + "buy_started_at = NULL WHERE id = ? AND status = 'buying' AND buyer = ?", id, buyer) == 1);
    }

    /**
     * BUYING → SOLD after both payments went through; the item goes into the buyer's collection bin in the same
     * transaction (it is delivered to the inventory from there).
     *
     * @return the collection row id, or empty if the listing was not in BUYING for this buyer
     */
    CompletableFuture<Optional<Long>> finishSale(long id, UUID buyer, long tax, long now) {
        return db.transaction(c -> {
            if (Sql.update(c, "UPDATE ah_listings SET status = 'sold', tax = ?, ended_at = ?, seller_notice = ? "
                    + "WHERE id = ? AND status = 'buying' AND buyer = ?", tax, now, true, id, buyer) != 1) {
                return Optional.<Long>empty();
            }
            return Optional.of(toCollection(c, "buyer", CollectionEntry.Source.BOUGHT, id, now));
        });
    }

    // ================================================================== cancel / expire

    /**
     * ACTIVE → CANCELLED (by the seller, or by staff when {@code seller == null}); the item goes to the seller's
     * collection bin in the same transaction.
     */
    CompletableFuture<Optional<Long>> cancel(long id, UUID seller, CollectionEntry.Source source, long now) {
        return db.transaction(c -> {
            int rows = seller == null
                    ? Sql.update(c, "UPDATE ah_listings SET status = 'cancelled', ended_at = ? WHERE id = ? AND status = 'active'", now, id)
                    : Sql.update(c, "UPDATE ah_listings SET status = 'cancelled', ended_at = ? WHERE id = ? AND status = 'active' "
                    + "AND seller = ?", now, id, seller);
            if (rows != 1) {
                return Optional.<Long>empty();
            }
            return Optional.of(toCollection(c, "seller", source, id, now));
        });
    }

    /** What {@link #deleteProfile} removed: the cancelled listings and the deleted collection bin entries. */
    record ProfileWipe(List<Listing> listings, List<CollectionEntry> entries) {
        boolean empty() {
            return listings.isEmpty() && entries.isEmpty();
        }
    }

    /**
     * Owner answer 118 (round 1): a deleted profile loses everything, auction items included. In <b>one transaction</b>
     * every ACTIVE listing of {@code seller} becomes CANCELLED <b>without</b> a collection entry (the item is deleted with the
     * profile, never returned) and the profile's collection bin is emptied. Listings in PENDING/BUYING are left to finish
     * (the caller runs this again shortly after). The rows keep the item data as history.
     */
    CompletableFuture<ProfileWipe> deleteProfile(UUID seller, long now) {
        return db.transaction(c -> {
            List<Listing> listings = Sql.query(c, "SELECT " + COLUMNS + " FROM ah_listings WHERE seller = ? AND status = 'active' "
                    + "ORDER BY id", AuctionStore::listing, seller);
            List<Listing> cancelled = new java.util.ArrayList<>();
            for (Listing l : listings) {
                if (Sql.update(c, "UPDATE ah_listings SET status = 'cancelled', ended_at = ? WHERE id = ? AND status = 'active'",
                        now, l.id()) == 1) {
                    cancelled.add(l);
                }
            }
            List<CollectionEntry> entries = Sql.query(c, "SELECT id, owner, listing_id, source, item, material, amount, item_name, "
                    + "created_at FROM ah_collection WHERE owner = ? ORDER BY id", AuctionStore::entry, seller);
            for (CollectionEntry e : entries) {
                Sql.update(c, "DELETE FROM ah_collection WHERE id = ?", e.id());
            }
            return new ProfileWipe(List.copyOf(cancelled), List.copyOf(entries));
        });
    }

    /** Ids of active listings whose time is up. */
    CompletableFuture<List<Long>> due(long now, int limit) {
        return db.async(c -> Sql.query(c, "SELECT id FROM ah_listings WHERE status = 'active' AND expires_at <= ? ORDER BY expires_at "
                + "LIMIT " + Math.max(1, limit), rs -> rs.getLong(1), now));
    }

    /** ACTIVE → EXPIRED (time is up); the item goes to the seller's collection bin in the same transaction. */
    CompletableFuture<Boolean> expire(long id, long now) {
        return db.transaction(c -> {
            if (Sql.update(c, "UPDATE ah_listings SET status = 'expired', ended_at = ?, seller_notice = ? WHERE id = ? "
                    + "AND status = 'active' AND expires_at <= ?", now, true, id, now) != 1) {
                return false;
            }
            toCollection(c, "seller", CollectionEntry.Source.EXPIRED, id, now);
            return true;
        });
    }

    /** Copies the listing's item into the bin of its seller or buyer; returns the new collection row id. */
    private static long toCollection(Connection c, String ownerColumn, CollectionEntry.Source source, long listingId, long now)
            throws SQLException {
        if (Sql.update(c, String.format(TO_COLLECTION, ownerColumn), source.key(), now, listingId) != 1) {
            throw new SQLException("Listing " + listingId + " could not be moved to the collection bin");
        }
        return Sql.queryOne(c, "SELECT id FROM ah_collection WHERE listing_id = ?", rs -> rs.getLong(1), listingId)
                .orElseThrow(() -> new SQLException("Collection row of listing " + listingId + " not found"));
    }

    // ================================================================== reads

    CompletableFuture<Optional<Listing>> get(long id) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + COLUMNS + " FROM ah_listings WHERE id = ?", AuctionStore::listing, id));
    }

    /** Every active listing (the browse cache). */
    CompletableFuture<List<Listing>> active() {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM ah_listings WHERE status = 'active'", AuctionStore::listing));
    }

    /** A seller's open listings (pending, active, buying), newest first. */
    CompletableFuture<List<Listing>> open(UUID seller) {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM ah_listings WHERE seller = ? AND status IN " + OPEN
                + " ORDER BY id DESC", AuctionStore::listing, seller));
    }

    /** Listings stuck in PENDING or BUYING since before {@code before} (crash, database error). */
    CompletableFuture<List<Listing>> stuck(long before) {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM ah_listings WHERE (status = 'pending' AND created_at <= ?) "
                + "OR (status = 'buying' AND buy_started_at <= ?) ORDER BY id", AuctionStore::listing, before, before));
    }

    /** Sold/expired listings the seller has not been told about, and marks them as told (one transaction). */
    CompletableFuture<List<Listing>> takeNotices(UUID seller) {
        return db.transaction(c -> {
            List<Listing> list = Sql.query(c, "SELECT " + COLUMNS + " FROM ah_listings WHERE seller = ? AND seller_notice = ? "
                    + "ORDER BY id", AuctionStore::listing, seller, true);
            for (Listing l : list) {
                Sql.update(c, "UPDATE ah_listings SET seller_notice = ? WHERE id = ?", false, l.id());
            }
            return list;
        });
    }

    /** The seller was told right away (online). */
    CompletableFuture<Void> clearNotice(long id) {
        return db.run(c -> Sql.update(c, "UPDATE ah_listings SET seller_notice = ? WHERE id = ?", false, id));
    }

    // ================================================================== collection bin

    CompletableFuture<List<CollectionEntry>> collection(UUID owner) {
        return db.async(c -> Sql.query(c, "SELECT id, owner, listing_id, source, item, material, amount, item_name, created_at "
                + "FROM ah_collection WHERE owner = ? ORDER BY id", AuctionStore::entry, owner));
    }

    CompletableFuture<Integer> collectionCount(UUID owner) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM ah_collection WHERE owner = ?", rs -> rs.getInt(1), owner).orElse(0));
    }

    /**
     * Takes an item out of the bin: reads it and deletes it in one transaction. Only the caller whose DELETE hit the row
     * gets the item, so two clicks (or two servers) can never both receive it.
     */
    CompletableFuture<Optional<CollectionEntry>> take(long id, UUID owner) {
        return db.transaction(c -> {
            Optional<CollectionEntry> entry = Sql.queryOne(c, "SELECT id, owner, listing_id, source, item, material, amount, "
                    + "item_name, created_at FROM ah_collection WHERE id = ? AND owner = ?", AuctionStore::entry, id, owner);
            if (entry.isEmpty() || Sql.update(c, "DELETE FROM ah_collection WHERE id = ? AND owner = ?", id, owner) != 1) {
                return Optional.<CollectionEntry>empty();
            }
            return entry;
        });
    }

    /** Puts an item (back) into the bin, e.g. what did not fit after a delivery. */
    CompletableFuture<Long> put(UUID owner, CollectionEntry.Source source, byte[] bytes, String material, int amount, String name,
                                long now) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO ah_collection (owner, listing_id, source, item, material, amount, "
                + "item_name, created_at) VALUES (?, NULL, ?, ?, ?, ?, ?, ?)", owner, source.key(), bytes, material, amount, name, now));
    }

    // ================================================================== mapping

    static Listing listing(ResultSet rs) throws SQLException {
        String category = rs.getString("category");
        ItemSnapshot item = new ItemSnapshot(rs.getBytes("item"), rs.getString("item_key"), rs.getString("item_uuid"),
                rs.getString("material"), rs.getInt("amount"), rs.getString("item_name"),
                Category.byKey(category).orElse(Category.SPECIAL));
        return new Listing(rs.getLong("id"), Sql.uuid(rs, "seller"), rs.getString("seller_name"), rs.getString("currency"),
                rs.getLong("price"), rs.getLong("fee"), rs.getLong("tax"), item,
                ListingStatus.byKey(rs.getString("status")).orElse(ListingStatus.FAILED), rs.getLong("created_at"),
                rs.getLong("expires_at"), Sql.uuid(rs, "buyer"), rs.getString("buyer_name"), Sql.nullableLong(rs, "buy_started_at"),
                Sql.nullableLong(rs, "ended_at"), rs.getBoolean("seller_notice"));
    }

    static CollectionEntry entry(ResultSet rs) throws SQLException {
        return new CollectionEntry(rs.getLong("id"), Sql.uuid(rs, "owner"), Sql.nullableLong(rs, "listing_id"), rs.getString("source"),
                rs.getBytes("item"), rs.getString("material"), rs.getInt("amount"), rs.getString("item_name"), rs.getLong("created_at"));
    }
}
