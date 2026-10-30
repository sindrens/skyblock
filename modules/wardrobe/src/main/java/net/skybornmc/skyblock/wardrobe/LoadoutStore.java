package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loadout SQL (tables {@code loadouts} and {@code loadout_slots_bought}, {@code db/wardrobe/V003}). Methods take the
 * caller's connection so a swap runs in one transaction with the chest and worn-row writes of {@link WardrobeStore}.
 * No Bukkit (unit-tested on H2).
 */
final class LoadoutStore {

    private static final String COLUMNS = "loadout_id, profile_key, slot_index, name, icon, armor, pet_id, hotbar, updated_at";

    List<Loadout> loadouts(Connection c, UUID profile) throws SQLException {
        return Sql.query(c, "SELECT " + COLUMNS + " FROM loadouts WHERE profile_key = ? ORDER BY slot_index", LoadoutStore::row, profile);
    }

    Optional<Loadout> loadout(Connection c, UUID id, boolean lock) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM loadouts WHERE loadout_id = ?" + (lock ? " FOR UPDATE" : ""), LoadoutStore::row, id);
    }

    /** Slots bought with store credits (account-wide); 0 when nothing was bought. */
    int bought(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT slots_bought FROM loadout_slots_bought WHERE player_uuid = ?", rs -> rs.getInt(1), player).orElse(0);
    }

    /** Adds bought slots (for the future store module and tests). */
    void addBought(Connection c, UUID player, int add, long now) throws SQLException {
        int n = Sql.update(c, "UPDATE loadout_slots_bought SET slots_bought = slots_bought + ?, updated_at = ? WHERE player_uuid = ?", add, now, player);
        if (n == 0) {
            Sql.update(c, "INSERT INTO loadout_slots_bought (player_uuid, slots_bought, updated_at) VALUES (?, ?, ?)", player, Math.max(0, add), now);
        }
    }

    /** Inserts a new preset; the unique keys refuse a used place or name (duplicate key → caller maps it). */
    void insert(Connection c, Loadout l, long now) throws SQLException {
        Sql.update(c, "INSERT INTO loadouts (loadout_id, profile_key, slot_index, name, name_key, icon, armor, pet_id, hotbar, created_at, "
                        + "updated_at, last_used_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)",
                l.id(), l.profile(), l.index(), WardrobeStore.clip(l.name(), 16), WardrobeStore.clip(l.nameKey(), 16), WardrobeStore.clip(l.icon(), 48),
                Loadout.armorJson(l.armor()), WardrobeStore.clip(l.petId(), 64), Loadout.hotbarJson(l.hotbar()), now, now);
    }

    /** New contents (Save current) of the profile's preset; false if it is gone. */
    boolean updateContents(Connection c, Loadout l, long now) throws SQLException {
        return Sql.update(c, "UPDATE loadouts SET armor = ?, pet_id = ?, hotbar = ?, updated_at = ?, version = version + 1 "
                + "WHERE loadout_id = ? AND profile_key = ?", Loadout.armorJson(l.armor()), WardrobeStore.clip(l.petId(), 64),
                Loadout.hotbarJson(l.hotbar()), now, l.id(), l.profile()) == 1;
    }

    boolean rename(Connection c, UUID id, UUID profile, String name, long now) throws SQLException {
        return Sql.update(c, "UPDATE loadouts SET name = ?, name_key = ?, updated_at = ?, version = version + 1 WHERE loadout_id = ? AND profile_key = ?",
                WardrobeStore.clip(name, 16), WardrobeStore.clip(Loadout.key(name), 16), now, id, profile) == 1;
    }

    boolean icon(Connection c, UUID id, UUID profile, String icon, long now) throws SQLException {
        return Sql.update(c, "UPDATE loadouts SET icon = ?, updated_at = ?, version = version + 1 WHERE loadout_id = ? AND profile_key = ?",
                WardrobeStore.clip(icon, 48), now, id, profile) == 1;
    }

    boolean delete(Connection c, UUID id, UUID profile) throws SQLException {
        return Sql.update(c, "DELETE FROM loadouts WHERE loadout_id = ? AND profile_key = ?", id, profile) == 1;
    }

    void touch(Connection c, UUID id, long now) throws SQLException {
        Sql.update(c, "UPDATE loadouts SET last_used_at = ? WHERE loadout_id = ?", now, id);
    }

    /** Merging (W2) replaced two entries by one: presets that wore either now wear the merged entry. */
    int repoint(Connection c, UUID profile, UUID from, UUID to, long now) throws SQLException {
        int changed = 0;
        for (Loadout l : loadouts(c, profile)) {
            java.util.Map<net.skybornmc.skyblock.common.wardrobe.ArmorPiece, Loadout.Armor> armor = new java.util.EnumMap<>(
                    net.skybornmc.skyblock.common.wardrobe.ArmorPiece.class);
            boolean hit = false;
            for (var e : l.armor().entrySet()) {
                if (!e.getValue().empty() && e.getValue().entryId().equals(from)) {
                    armor.put(e.getKey(), new Loadout.Armor(to));
                    hit = true;
                } else {
                    armor.put(e.getKey(), e.getValue());
                }
            }
            if (hit) {
                Sql.update(c, "UPDATE loadouts SET armor = ?, updated_at = ?, version = version + 1 WHERE loadout_id = ?",
                        Loadout.armorJson(armor), now, l.id());
                changed++;
            }
        }
        return changed;
    }

    /**
     * Chest places in use for a swap's capacity check: stored items plus items being handed out by OTHER swaps (the
     * swap's own incoming items leave the chest).
     */
    int chestUsedExcept(Connection c, UUID profile, UUID plan) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM wardrobe_storage WHERE profile_key = ? AND (state = ? OR (state = ? AND "
                        + "(plan_id IS NULL OR plan_id <> ?)))", rs -> rs.getInt(1), profile, WardrobeStore.StorageState.STORED.key(),
                WardrobeStore.StorageState.RELEASING.key(), plan).orElse(0);
    }

    private static Loadout row(ResultSet rs) throws SQLException {
        return new Loadout(Sql.uuid(rs, "loadout_id"), Sql.uuid(rs, "profile_key"), rs.getInt("slot_index"), rs.getString("name"),
                rs.getString("icon"), Loadout.parseArmor(rs.getString("armor")), rs.getString("pet_id"),
                Loadout.parseHotbar(rs.getString("hotbar")), rs.getLong("updated_at"));
    }
}
