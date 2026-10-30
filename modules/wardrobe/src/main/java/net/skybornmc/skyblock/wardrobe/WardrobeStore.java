package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All wardrobe SQL (tables in {@code db/wardrobe}). Methods take the caller's connection so several of them (and the
 * economy's payment) run inside one transaction. No Bukkit here (unit-tested on H2).
 */
final class WardrobeStore {

    // ------------------------------------------------------------------ rows

    enum EntryState {
        ACTIVE, MERGED, REMOVED;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static EntryState byKey(String k) {
            for (EntryState s : values()) {
                if (s.key().equalsIgnoreCase(k)) {
                    return s;
                }
            }
            return REMOVED;
        }
    }

    /** One owned set (wardrobe_sets). */
    record Entry(UUID entryId, UUID profile, String setId, String mergedSetId, String lookSetId, int level, double xp,
                 EntryState state, String origin, String originRef, long createdAt) {
        static Entry fresh(UUID profile, String setId, String origin, String originRef, long now) {
            return new Entry(UUID.randomUUID(), profile, setId, null, null, 0, 0, EntryState.ACTIVE, origin, originRef, now);
        }

        /** A merged entry of two sets (level 0, the look of {@code lookSetId}). */
        static Entry merged(UUID profile, String setId, String mergedSetId, String lookSetId, long now) {
            return new Entry(UUID.randomUUID(), profile, setId, mergedSetId, lookSetId, 0, 0, EntryState.ACTIVE, "merge", null, now);
        }

        boolean isMerged() {
            return mergedSetId != null;
        }

        /** The set whose look the pieces use. */
        String look() {
            return lookSetId != null ? lookSetId : setId;
        }

        Entry withXp(double newXp, int newLevel) {
            return new Entry(entryId, profile, setId, mergedSetId, lookSetId, newLevel, newXp, state, origin, originRef, createdAt);
        }
    }

    /** One Wardrobe Tome row. */
    record Tome(UUID id, String kind, long xp, TokenState state, String origin) {
    }

    /** One worn slot (wardrobe_equipped). */
    record Equipped(ArmorPiece piece, UUID entryId, UUID nonce) {
    }

    enum StorageState {
        STORED, RELEASING, RELEASED;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static StorageState byKey(String k) {
            for (StorageState s : values()) {
                if (s.key().equalsIgnoreCase(k)) {
                    return s;
                }
            }
            return RELEASED;
        }
    }

    /** One Wardrobe Chest row. */
    record Stored(UUID itemUuid, UUID profile, String kind, String itemId, String itemName, byte[] item, StorageState state,
                  UUID planId, long storedAt) {
    }

    enum TokenState {
        ACTIVE, REDEEMED, VOID, CONFISCATED;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static TokenState byKey(String k) {
            for (TokenState s : values()) {
                if (s.key().equalsIgnoreCase(k)) {
                    return s;
                }
            }
            return VOID;
        }
    }

    record Token(UUID id, String setId, TokenState state, String origin) {
    }

    // ------------------------------------------------------------------ sets

    private static final String ENTRY_COLUMNS = "entry_id, profile_key, set_id, merged_set_id, look_set_id, level, xp, state, origin, "
            + "origin_ref, created_at";

    List<Entry> activeEntries(Connection c, UUID profile) throws SQLException {
        return Sql.query(c, "SELECT " + ENTRY_COLUMNS + " FROM wardrobe_sets WHERE profile_key = ? AND state = ? ORDER BY created_at",
                WardrobeStore::entry, profile, EntryState.ACTIVE.key());
    }

    Optional<Entry> entry(Connection c, UUID entryId) throws SQLException {
        return Sql.queryOne(c, "SELECT " + ENTRY_COLUMNS + " FROM wardrobe_sets WHERE entry_id = ?", WardrobeStore::entry, entryId);
    }

    /** True if the profile has an active, unmerged copy of the set (locks it for the rest of the transaction). */
    boolean ownsActive(Connection c, UUID profile, String setId) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM wardrobe_sets WHERE profile_key = ? AND active_key = ? FOR UPDATE", rs -> 1, profile, setId)
                .isPresent();
    }

    /**
     * Inserts a new active entry; false when the profile already owns that set (one copy per set per profile, WD6; the
     * unique key {@code (profile_key, active_key)} enforces it even for two transactions at once).
     */
    boolean insertEntry(Connection c, Entry e, int season, UUID by) throws SQLException {
        if (ownsActive(c, e.profile(), e.setId())) {
            return false;
        }
        try {
            Sql.update(c, "INSERT INTO wardrobe_sets (entry_id, profile_key, set_id, merged_set_id, look_set_id, active_key, level, xp, state, "
                            + "origin, origin_ref, season_id, created_at, changed_at, changed_by, version) VALUES (?, ?, ?, NULL, NULL, ?, 0, 0, ?, ?, ?, ?, ?, NULL, ?, 0)",
                    e.entryId(), e.profile(), e.setId(), e.setId(), EntryState.ACTIVE.key(), clip(e.origin(), 16), clip(e.originRef(), 64),
                    season, e.createdAt(), by);
            return true;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                return false;
            }
            throw ex;
        }
    }

    /** Staff removal (WD8): active → removed, worn pieces of it taken off. False if it was not active. */
    boolean removeEntry(Connection c, UUID entryId, UUID by, long now) throws SQLException {
        int n = Sql.update(c, "UPDATE wardrobe_sets SET state = ?, active_key = NULL, changed_at = ?, changed_by = ?, version = version + 1 "
                + "WHERE entry_id = ? AND state = ?", EntryState.REMOVED.key(), now, by, entryId, EntryState.ACTIVE.key());
        if (n == 1) {
            Sql.update(c, "DELETE FROM wardrobe_equipped WHERE entry_id = ?", entryId);
        }
        return n == 1;
    }

    // ------------------------------------------------------------------ levels (W2)

    /** An entry, locked for the rest of the transaction. */
    Optional<Entry> entryForUpdate(Connection c, UUID entryId) throws SQLException {
        return Sql.queryOne(c, "SELECT " + ENTRY_COLUMNS + " FROM wardrobe_sets WHERE entry_id = ? FOR UPDATE", WardrobeStore::entry, entryId);
    }

    /**
     * Adds set XP to an active entry (locked); the level follows from the new total (clamped at the max level).
     *
     * @return the updated entry, or empty when the entry is not active any more (merged, removed: the XP is dropped)
     */
    Optional<Entry> addXp(Connection c, UUID entryId, double delta, SetLevels levels, long now) throws SQLException {
        Optional<Entry> found = entryForUpdate(c, entryId);
        if (found.isEmpty() || found.get().state() != EntryState.ACTIVE) {
            return Optional.empty();
        }
        Entry e = found.get();
        double add = delta > 0 && Double.isFinite(delta) ? delta : 0;
        double xp = levels.clamp(e.xp() + add, e.isMerged());
        // The level is always recomputed from the total, so a changed curve (wardrobe.yml) is applied on the next save.
        int level = levels.level(xp, e.isMerged());
        if (add == 0 && level == e.level() && xp == e.xp()) {
            return Optional.of(e);
        }
        Sql.update(c,"UPDATE wardrobe_sets SET xp = ?, level = ?, version = version + 1 WHERE entry_id = ? AND state = ?",
                xp, level, entryId, EntryState.ACTIVE.key());
        return Optional.of(e.withXp(xp, level));
    }

    /** Sets an active entry's total XP and level (staff, tomes). False if the entry is not active. */
    boolean writeXp(Connection c, UUID entryId, double xp, int level, UUID by, long now) throws SQLException {
        return Sql.update(c, "UPDATE wardrobe_sets SET xp = ?, level = ?, changed_at = ?, changed_by = ?, version = version + 1 "
                + "WHERE entry_id = ? AND state = ?", xp, level, now, by, entryId, EntryState.ACTIVE.key()) == 1;
    }

    // ------------------------------------------------------------------ merging (W2)

    /** active → merged (its worn slots are emptied). False if it was not active. */
    boolean markMerged(Connection c, UUID entryId, UUID by, long now) throws SQLException {
        int n = Sql.update(c, "UPDATE wardrobe_sets SET state = ?, active_key = NULL, changed_at = ?, changed_by = ?, version = version + 1 "
                + "WHERE entry_id = ? AND state = ?", EntryState.MERGED.key(), now, by, entryId, EntryState.ACTIVE.key());
        if (n == 1) {
            Sql.update(c, "DELETE FROM wardrobe_equipped WHERE entry_id = ?", entryId);
        }
        return n == 1;
    }

    /** Inserts a merged entry (active_key NULL: the inputs' sets can be owned again, WD6). */
    void insertMerged(Connection c, Entry e, int season, UUID by) throws SQLException {
        Sql.update(c, "INSERT INTO wardrobe_sets (entry_id, profile_key, set_id, merged_set_id, look_set_id, active_key, level, xp, state, "
                        + "origin, origin_ref, season_id, created_at, changed_at, changed_by, version) VALUES (?, ?, ?, ?, ?, NULL, 0, 0, ?, ?, NULL, ?, ?, NULL, ?, 0)",
                e.entryId(), e.profile(), e.setId(), e.mergedSetId(), e.look(), EntryState.ACTIVE.key(), clip(e.origin(), 16), season,
                e.createdAt(), by);
    }

    /** The merge record (inputs are unique: an entry can only ever be merged once). */
    void insertMergeRecord(Connection c, UUID mergeId, UUID profile, Entry a, Entry b, String look, long fee, UUID by, long now) throws SQLException {
        Sql.update(c, "INSERT INTO wardrobe_merges (merge_id, profile_key, entry_a, entry_b, set_a, set_b, look_set_id, fee, merged_at, merged_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", mergeId, profile, a.entryId(), b.entryId(), a.setId(), b.setId(), look, fee, now, by);
    }

    // ------------------------------------------------------------------ piece enchants (E5)

    /** Armor enchants of every active entry of a profile: entry id → piece → enchants (pieces without any left out). */
    Map<UUID, Map<ArmorPiece, List<ToolPerk>>> pieceEnchants(Connection c, UUID profile) throws SQLException {
        Map<UUID, Map<ArmorPiece, List<ToolPerk>>> out = new java.util.HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT p.entry_id, p.piece, p.enchants FROM wardrobe_pieces p JOIN wardrobe_sets s ON s.entry_id = p.entry_id "
                + "WHERE s.profile_key = ? AND s.state = ?", rs -> new Object[]{Sql.uuid(rs, "entry_id"), rs.getString("piece"), rs.getString("enchants")},
                profile, EntryState.ACTIVE.key())) {
            Optional<ArmorPiece> piece = ArmorPiece.byKey((String) row[1]);
            List<ToolPerk> list = PieceEnchants.decode((String) row[2]);
            if (piece.isPresent() && !list.isEmpty()) {
                out.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(ArmorPiece.class)).put(piece.get(), list);
            }
        }
        return out;
    }

    /** All piece enchants of one entry (no lock). */
    Map<ArmorPiece, List<ToolPerk>> entryEnchants(Connection c, UUID entryId) throws SQLException {
        Map<ArmorPiece, List<ToolPerk>> out = new EnumMap<>(ArmorPiece.class);
        for (Object[] row : Sql.query(c, "SELECT piece, enchants FROM wardrobe_pieces WHERE entry_id = ?",
                rs -> new Object[]{rs.getString("piece"), rs.getString("enchants")}, entryId)) {
            Optional<ArmorPiece> piece = ArmorPiece.byKey((String) row[0]);
            List<ToolPerk> list = PieceEnchants.decode((String) row[1]);
            if (piece.isPresent() && !list.isEmpty()) {
                out.put(piece.get(), list);
            }
        }
        return out;
    }

    /**
     * Locks an entry row and reads one piece's enchants; empty when the entry is not active or not the profile's.
     */
    Optional<List<ToolPerk>> lockPiece(Connection c, UUID profile, UUID entryId, ArmorPiece piece) throws SQLException {
        Optional<Entry> e = entryForUpdate(c, entryId);
        if (e.isEmpty() || e.get().state() != EntryState.ACTIVE || !e.get().profile().equals(profile)) {
            return Optional.empty();
        }
        return Optional.of(Sql.queryOne(c, "SELECT enchants FROM wardrobe_pieces WHERE entry_id = ? AND piece = ?",
                rs -> PieceEnchants.decode(rs.getString(1)), entryId, piece.key()).orElse(List.of()));
    }

    /** Stores a piece's enchants (deletes the row when there are none). */
    void savePiece(Connection c, UUID entryId, ArmorPiece piece, List<ToolPerk> enchants) throws SQLException {
        Sql.update(c, "DELETE FROM wardrobe_pieces WHERE entry_id = ? AND piece = ?", entryId, piece.key());
        if (enchants != null && !enchants.isEmpty()) {
            Sql.update(c, "INSERT INTO wardrobe_pieces (entry_id, piece, enchants) VALUES (?, ?, ?)", entryId, piece.key(),
                    PieceEnchants.encode(enchants));
        }
    }

    /** Removes every piece row of an entry (its enchants moved on, e.g. a merge). */
    void deletePieces(Connection c, UUID entryId) throws SQLException {
        Sql.update(c, "DELETE FROM wardrobe_pieces WHERE entry_id = ?", entryId);
    }

    // ------------------------------------------------------------------ tomes (W2)

    void insertTome(Connection c, UUID id, String kind, long xp, String origin, UUID createdFor, long now) throws SQLException {
        Sql.update(c, "INSERT INTO wardrobe_tomes (tome_uuid, kind, xp_amount, state, origin, created_for, created_at, changed_at, changed_by, "
                + "used_entry, reason) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)", id, clip(kind, 16), xp, TokenState.ACTIVE.key(),
                clip(origin, 64), createdFor, now);
    }

    Optional<Tome> tome(Connection c, UUID id, boolean lock) throws SQLException {
        return Sql.queryOne(c, "SELECT tome_uuid, kind, xp_amount, state, origin FROM wardrobe_tomes WHERE tome_uuid = ?" + (lock ? " FOR UPDATE" : ""),
                rs -> new Tome(Sql.uuid(rs, "tome_uuid"), rs.getString("kind"), rs.getLong("xp_amount"), TokenState.byKey(rs.getString("state")),
                        rs.getString("origin")), id);
    }

    /** {@code from → to} if still in {@code from} (REDEEMED = used on an entry). */
    boolean moveTome(Connection c, UUID id, TokenState from, TokenState to, UUID by, UUID entry, long now, String reason) throws SQLException {
        return Sql.update(c, "UPDATE wardrobe_tomes SET state = ?, changed_at = ?, changed_by = ?, used_entry = COALESCE(?, used_entry), reason = ? "
                + "WHERE tome_uuid = ? AND state = ?", to.key(), now, by, entry, clip(reason, 64), id, from.key()) == 1;
    }

    // ------------------------------------------------------------------ equipped

    Map<ArmorPiece, Equipped> equipped(Connection c, UUID profile) throws SQLException {
        Map<ArmorPiece, Equipped> out = new EnumMap<>(ArmorPiece.class);
        for (Equipped e : Sql.query(c, "SELECT slot, entry_id, nonce FROM wardrobe_equipped WHERE profile_key = ?",
                rs -> ArmorPiece.byKey(rs.getString("slot")).map(p -> {
                    try {
                        return new Equipped(p, Sql.uuid(rs, "entry_id"), Sql.uuid(rs, "nonce"));
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                }).orElse(null), profile)) {
            if (e != null) {
                out.put(e.piece(), e);
            }
        }
        return out;
    }

    /**
     * Writes the worn slots of a profile in one go: {@code wear} puts entries in slots (new nonces), {@code off} empties
     * slots. Only entries that are active and belong to the profile can be worn (checked here, inside the transaction).
     *
     * @return false (and nothing written) if an entry is not active or not the profile's
     */
    boolean writeEquipped(Connection c, UUID profile, Collection<Equipped> wear, Collection<ArmorPiece> off, long now) throws SQLException {
        for (Equipped e : wear) {
            Optional<Entry> entry = Sql.queryOne(c, "SELECT " + ENTRY_COLUMNS + " FROM wardrobe_sets WHERE entry_id = ? FOR UPDATE",
                    WardrobeStore::entry, e.entryId());
            if (entry.isEmpty() || entry.get().state() != EntryState.ACTIVE || !entry.get().profile().equals(profile)) {
                return false;
            }
        }
        for (ArmorPiece p : off) {
            Sql.update(c, "DELETE FROM wardrobe_equipped WHERE profile_key = ? AND slot = ?", profile, p.key());
        }
        for (Equipped e : wear) {
            Sql.update(c, "DELETE FROM wardrobe_equipped WHERE profile_key = ? AND slot = ?", profile, e.piece().key());
            Sql.update(c, "INSERT INTO wardrobe_equipped (profile_key, slot, entry_id, nonce, equipped_at) VALUES (?, ?, ?, ?, ?)",
                    profile, e.piece().key(), e.entryId(), e.nonce(), now);
        }
        return true;
    }

    // ------------------------------------------------------------------ sigils and claims (WD5)

    Set<String> sigils(Connection c, UUID player) throws SQLException {
        return new LinkedHashSet<>(Sql.query(c, "SELECT set_id FROM wardrobe_sigils WHERE player_uuid = ?", rs -> rs.getString(1), player));
    }

    boolean hasSigil(Connection c, UUID player, String setId) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM wardrobe_sigils WHERE player_uuid = ? AND set_id = ? FOR UPDATE", rs -> 1, player, setId).isPresent();
    }

    /** False if the account already has this Sigil. */
    boolean insertSigil(Connection c, UUID player, String setId, long gems, UUID profile, long now) throws SQLException {
        if (hasSigil(c, player, setId)) {
            return false;
        }
        try {
            Sql.update(c, "INSERT INTO wardrobe_sigils (player_uuid, set_id, gems, bought_at, bought_profile) VALUES (?, ?, ?, ?, ?)",
                    player, setId, gems, now, profile);
            return true;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                return false;
            }
            throw ex;
        }
    }

    Set<String> claims(Connection c, UUID profile, int season) throws SQLException {
        return new LinkedHashSet<>(Sql.query(c, "SELECT set_id FROM wardrobe_claims WHERE profile_key = ? AND season_id = ?",
                rs -> rs.getString(1), profile, season));
    }

    /** False if the profile already claimed this set this season. */
    boolean insertClaim(Connection c, UUID profile, String setId, int season, UUID entryId, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM wardrobe_claims WHERE profile_key = ? AND set_id = ? AND season_id = ? FOR UPDATE", rs -> 1,
                profile, setId, season).isPresent()) {
            return false;
        }
        try {
            Sql.update(c, "INSERT INTO wardrobe_claims (profile_key, set_id, season_id, entry_id, claimed_at) VALUES (?, ?, ?, ?, ?)",
                    profile, setId, season, entryId, now);
            return true;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                return false;
            }
            throw ex;
        }
    }

    // ------------------------------------------------------------------ tokens

    void insertToken(Connection c, UUID id, String setId, String origin, UUID createdFor, long now) throws SQLException {
        Sql.update(c, "INSERT INTO set_tokens (token_uuid, set_id, state, origin, created_for, created_at, changed_at, changed_by, "
                + "redeemed_profile, reason) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)", id, setId, TokenState.ACTIVE.key(),
                clip(origin, 64), createdFor, now);
    }

    Optional<Token> token(Connection c, UUID id, boolean lock) throws SQLException {
        return Sql.queryOne(c, "SELECT token_uuid, set_id, state, origin FROM set_tokens WHERE token_uuid = ?" + (lock ? " FOR UPDATE" : ""),
                rs -> new Token(Sql.uuid(rs, "token_uuid"), rs.getString("set_id"), TokenState.byKey(rs.getString("state")), rs.getString("origin")), id);
    }

    /** {@code from → to} if still in {@code from}. */
    boolean moveToken(Connection c, UUID id, TokenState from, TokenState to, UUID by, UUID profile, long now, String reason) throws SQLException {
        return Sql.update(c, "UPDATE set_tokens SET state = ?, changed_at = ?, changed_by = ?, redeemed_profile = COALESCE(?, redeemed_profile), "
                        + "reason = ? WHERE token_uuid = ? AND state = ?", to.key(), now, by, profile, clip(reason, 64), id, from.key()) == 1;
    }

    // ------------------------------------------------------------------ storage (escrow, §4.4)

    private static final String STORED_COLUMNS = "item_uuid, profile_key, kind, item_id, item_name, item, state, plan_id, stored_at";

    /** Items that take space: stored or being handed out. */
    int storageCount(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM wardrobe_storage WHERE profile_key = ? AND state IN (?, ?)", rs -> rs.getInt(1),
                profile, StorageState.STORED.key(), StorageState.RELEASING.key()).orElse(0);
    }

    List<Stored> stored(Connection c, UUID profile) throws SQLException {
        return Sql.query(c, "SELECT " + STORED_COLUMNS + " FROM wardrobe_storage WHERE profile_key = ? AND state = ? ORDER BY stored_at",
                WardrobeStore::stored, profile, StorageState.STORED.key());
    }

    List<Stored> releasing(Connection c, UUID profile) throws SQLException {
        return Sql.query(c, "SELECT " + STORED_COLUMNS + " FROM wardrobe_storage WHERE profile_key = ? AND state = ?",
                WardrobeStore::stored, profile, StorageState.RELEASING.key());
    }

    Optional<Stored> storedItem(Connection c, UUID itemUuid, boolean lock) throws SQLException {
        return Sql.queryOne(c, "SELECT " + STORED_COLUMNS + " FROM wardrobe_storage WHERE item_uuid = ?" + (lock ? " FOR UPDATE" : ""),
                WardrobeStore::stored, itemUuid);
    }

    /** Item uuids among {@code ids} whose row says they are in the chest (stored or being handed out). */
    Set<UUID> inChest(Connection c, Collection<UUID> ids) throws SQLException {
        Set<UUID> out = new LinkedHashSet<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<UUID> list = new ArrayList<>(ids);
        for (int from = 0; from < list.size(); from += 100) {
            List<UUID> part = list.subList(from, Math.min(list.size(), from + 100));
            String marks = String.join(", ", java.util.Collections.nCopies(part.size(), "?"));
            Object[] params = new Object[part.size() + 1];
            for (int i = 0; i < part.size(); i++) {
                params[i] = part.get(i);
            }
            params[part.size()] = StorageState.STORED.key();
            out.addAll(Sql.query(c, "SELECT item_uuid FROM wardrobe_storage WHERE item_uuid IN (" + marks + ") AND state = ?",
                    rs -> Sql.uuid(rs, "item_uuid"), params));
        }
        return out;
    }

    /**
     * Puts an item in the chest (inside the store transaction). Re-storing an item that was taken out before reuses its
     * row. Result: OK, FULL (no room), or STALE (the row says this item is already in the chest: the copy in the
     * player's hand is a stale copy).
     */
    enum PutResult { OK, FULL, STALE }

    PutResult put(Connection c, Stored s, int capacity, long now) throws SQLException {
        Optional<Stored> existing = storedItem(c, s.itemUuid(), true);
        if (existing.isPresent() && existing.get().state() != StorageState.RELEASED) {
            return PutResult.STALE;
        }
        if (storageCount(c, s.profile()) >= capacity) {
            return PutResult.FULL;
        }
        if (existing.isPresent()) {
            int n = Sql.update(c, "UPDATE wardrobe_storage SET profile_key = ?, kind = ?, item_id = ?, item_name = ?, item = ?, state = ?, "
                            + "plan_id = NULL, stored_at = ?, updated_at = ? WHERE item_uuid = ? AND state = ?", s.profile(), clip(s.kind(), 16),
                    clip(s.itemId(), 64), clip(s.itemName(), 128), s.item(), StorageState.STORED.key(), now, now, s.itemUuid(),
                    StorageState.RELEASED.key());
            return n == 1 ? PutResult.OK : PutResult.STALE;
        }
        try {
            Sql.update(c, "INSERT INTO wardrobe_storage (item_uuid, profile_key, kind, item_id, item_name, item, state, plan_id, stored_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)", s.itemUuid(), s.profile(), clip(s.kind(), 16), clip(s.itemId(), 64),
                    clip(s.itemName(), 128), s.item(), StorageState.STORED.key(), now, now);
            return PutResult.OK;
        } catch (SQLException ex) {
            if (duplicate(ex)) {
                return PutResult.STALE;
            }
            throw ex;
        }
    }

    /** stored → releasing (with the plan id) if the item is the profile's and still stored. */
    boolean markReleasing(Connection c, UUID itemUuid, UUID profile, UUID plan, long now) throws SQLException {
        return Sql.update(c, "UPDATE wardrobe_storage SET state = ?, plan_id = ?, updated_at = ? WHERE item_uuid = ? AND profile_key = ? AND state = ?",
                StorageState.RELEASING.key(), plan, now, itemUuid, profile, StorageState.STORED.key()) == 1;
    }

    /** releasing → released once the item is in the player's inventory. */
    boolean markReleased(Connection c, UUID itemUuid, UUID plan, long now) throws SQLException {
        return Sql.update(c, "UPDATE wardrobe_storage SET state = ?, updated_at = ? WHERE item_uuid = ? AND state = ? AND plan_id = ?",
                StorageState.RELEASED.key(), now, itemUuid, StorageState.RELEASING.key(), plan) == 1;
    }

    /** releasing → stored when the item could not be handed out (no room on join). */
    boolean backToStored(Connection c, UUID itemUuid, UUID plan, long now) throws SQLException {
        return Sql.update(c, "UPDATE wardrobe_storage SET state = ?, plan_id = NULL, updated_at = ? WHERE item_uuid = ? AND state = ? AND plan_id = ?",
                StorageState.STORED.key(), now, itemUuid, StorageState.RELEASING.key(), plan) == 1;
    }

    // ------------------------------------------------------------------ helpers

    private static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(Sql.uuid(rs, "entry_id"), Sql.uuid(rs, "profile_key"), rs.getString("set_id"), rs.getString("merged_set_id"),
                rs.getString("look_set_id"), rs.getInt("level"), rs.getDouble("xp"), EntryState.byKey(rs.getString("state")),
                rs.getString("origin"), rs.getString("origin_ref"), rs.getLong("created_at"));
    }

    private static Stored stored(ResultSet rs) throws SQLException {
        return new Stored(Sql.uuid(rs, "item_uuid"), Sql.uuid(rs, "profile_key"), rs.getString("kind"), rs.getString("item_id"),
                rs.getString("item_name"), rs.getBytes("item"), StorageState.byKey(rs.getString("state")), Sql.uuid(rs, "plan_id"),
                rs.getLong("stored_at"));
    }

    /** Unique key violation (SQLState class 23; MariaDB 1062, H2 23505). */
    static boolean duplicate(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("23")) || e.getErrorCode() == 1062 || e.getErrorCode() == 23505;
    }

    static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
