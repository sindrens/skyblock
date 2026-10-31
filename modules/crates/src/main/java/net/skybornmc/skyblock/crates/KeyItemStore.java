package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL and rules of key items (PHASE-10 M10.4, table {@code crate_key_items} in {@code V002__key_items.sql}). Static
 * methods on a caller's connection; every method that changes something must run inside one transaction:
 * <ul>
 *   <li>{@link #withdraw}: takes {@code n} <b>tradeable</b> keys (conditional {@code UPDATE}, bound keys are never
 *       touched) and writes {@code n} {@code active} rows, all or nothing.</li>
 *   <li>{@link #redeem}: flips one row {@code active → redeemed} (conditional, exactly once) and adds one tradeable key
 *       to the redeeming profile. A row that is not active (or missing) is a copy.</li>
 *   <li>{@link #refund}: rows of items that were never handed out go back to the profile they came from.</li>
 * </ul>
 */
final class KeyItemStore {

    static final String ACTIVE = "active";
    static final String REDEEMED = "redeemed";
    static final String REFUNDED = "refunded";

    /** One key item as stored. */
    record Row(UUID itemId, String crate, UUID account, String accountName, UUID profile, String state, long createdAt,
               UUID redeemedProfile, UUID redeemedBy, String redeemedName, Long redeemedAt) {
    }

    /** What a redeem did. */
    enum Outcome {
        /** The key is on the redeeming profile now; the item is used up. */
        REDEEMED,
        /** Owner decision P4: withdrawn by this account on another profile. Nothing changed; the item goes back. */
        OTHER_PROFILE,
        /** The crate is not in crates.yml any more. Nothing changed; the item goes back. */
        UNKNOWN_CRATE,
        /** The row was redeemed (or refunded) already: this item is a copy. */
        ALREADY_USED,
        /** No row for this id: a copy of a made-up or foreign item. */
        UNKNOWN
    }

    /** @param row the row as read before the change (null for {@link Outcome#UNKNOWN}) */
    record Redeem(Outcome outcome, Row row) {
    }

    private KeyItemStore() {
    }

    // ------------------------------------------------------------------ pure rules

    /** Keys one withdraw takes: {@code want} clamped to 1..max and to the tradeable keys there are (0 = none). Pure. */
    static int amount(long tradeable, long want, int max) {
        if (tradeable < 1 || want < 1 || max < 1) {
            return 0;
        }
        return (int) Math.min(Math.min(want, max), tradeable);
    }

    /**
     * Whether {@code row} may be redeemed by {@code account} on {@code profile} (pure; {@code crateExists} = the crate is
     * still in the catalog). The account that withdrew it may only put it back on the same profile (P4); anybody else
     * may redeem it.
     */
    static Outcome decide(Row row, UUID account, UUID profile, boolean crateExists) {
        if (row == null) {
            return Outcome.UNKNOWN;
        }
        if (!ACTIVE.equals(row.state())) {
            return Outcome.ALREADY_USED;
        }
        if (row.account().equals(account) && !row.profile().equals(profile)) {
            return Outcome.OTHER_PROFILE;
        }
        return crateExists ? Outcome.REDEEMED : Outcome.UNKNOWN_CRATE;
    }

    /** True when a redeem outcome means the item is a copy (it stays taken and is reported). Pure. */
    static boolean copy(Outcome outcome) {
        return outcome == Outcome.ALREADY_USED || outcome == Outcome.UNKNOWN;
    }

    // ------------------------------------------------------------------ SQL

    static Optional<Row> row(Connection c, UUID itemId) throws SQLException {
        return Sql.queryOne(c, "SELECT * FROM crate_key_items WHERE item_uuid = ?", KeyItemStore::map, itemId);
    }

    /**
     * Takes {@code ids.size()} tradeable keys of {@code crate} from {@code profile} and writes one active row per id.
     * False (nothing changed) when the profile has fewer tradeable keys. Call inside one transaction.
     */
    static boolean withdraw(Connection c, UUID profile, String crate, UUID account, String accountName, List<UUID> ids, long now)
            throws SQLException {
        if (ids.isEmpty() || !CrateStore.take(c, profile, crate, 0, ids.size(), now)) {
            return false;
        }
        List<Object[]> rows = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            rows.add(new Object[]{id, crate, account, name(accountName), profile, ACTIVE, now, now});
        }
        Sql.batch(c, "INSERT INTO crate_key_items (item_uuid, crate_id, account_uuid, account_name, profile_key, item_state, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows);
        return true;
    }

    /**
     * Redeems one key item for {@code account} on {@code profile}: on {@link Outcome#REDEEMED} the row is used up and one
     * tradeable key added, in the caller's transaction. Every other outcome changes nothing.
     *
     * @param crateExists whether a crate id is in the catalog (checked against the row's crate)
     */
    static Redeem redeem(Connection c, UUID itemId, UUID profile, UUID account, String name, long now,
                         java.util.function.Predicate<String> crateExists) throws SQLException {
        Row row = row(c, itemId).orElse(null);
        Outcome outcome = decide(row, account, profile, row != null && crateExists.test(row.crate()));
        if (outcome != Outcome.REDEEMED) {
            return new Redeem(outcome, row);
        }
        if (Sql.update(c, "UPDATE crate_key_items SET item_state = ?, redeemed_profile = ?, redeemed_by = ?, redeemed_name = ?, "
                        + "redeemed_at = ?, updated_at = ? WHERE item_uuid = ? AND item_state = ?",
                REDEEMED, profile, account, name(name), now, now, itemId, ACTIVE) != 1) {
            return new Redeem(Outcome.ALREADY_USED, row); // another redeem of the same id won the race
        }
        CrateStore.addKeys(c, profile, row.crate(), 0, 1, now);
        return new Redeem(Outcome.REDEEMED, row);
    }

    /**
     * Items that were never handed out (the player left or switched profile first): their active rows become
     * {@code refunded} and the keys go back to {@code profile} as tradeable keys. Returns the keys given back.
     */
    static int refund(Connection c, UUID profile, String crate, List<UUID> ids, long now) throws SQLException {
        int back = 0;
        for (UUID id : ids) {
            back += Sql.update(c, "UPDATE crate_key_items SET item_state = ?, updated_at = ? WHERE item_uuid = ? AND item_state = ? "
                    + "AND profile_key = ? AND crate_id = ?", REFUNDED, now, id, ACTIVE, profile, crate);
        }
        CrateStore.addKeys(c, profile, crate, 0, back, now);
        return back;
    }

    private static String name(String raw) {
        String n = raw == null ? "" : raw;
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    static Row map(ResultSet rs) throws SQLException {
        return new Row(Sql.uuid(rs, "item_uuid"), rs.getString("crate_id"), Sql.uuid(rs, "account_uuid"), rs.getString("account_name"),
                Sql.uuid(rs, "profile_key"), rs.getString("item_state"), rs.getLong("created_at"), Sql.uuid(rs, "redeemed_profile"),
                Sql.uuid(rs, "redeemed_by"), rs.getString("redeemed_name"), Sql.nullableLong(rs, "redeemed_at"));
    }
}
