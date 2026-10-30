package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cached wardrobe data of one online player for their active profile (main thread). Loaded on join and after a
 * profile switch; changed only after the database committed. {@link #generation} discards results of loads that were
 * overtaken by a newer one.
 */
final class Session {

    final UUID player;
    /** The profile key the data below belongs to. */
    UUID profile;
    boolean loaded;
    int generation;
    List<WardrobeStore.Entry> entries = List.of();
    Map<ArmorPiece, WardrobeStore.Equipped> equipped = new EnumMap<>(ArmorPiece.class);
    Set<String> sigils = Set.of();
    Set<String> claims = Set.of();
    volatile SetBonuses.Result bonuses = SetBonuses.Result.EMPTY;
    /** One wardrobe action (equip, buy, chest move, redeem) at a time; the inventory is locked meanwhile. */
    boolean busy;
    /** Pieces were taken off for a profile switch; nothing is rendered until the switch ends. */
    boolean stripped;
    long lastEquip;
    long lastHint;
    /** Set XP earned but not saved yet: entry id → XP (already in {@link #entries}; saved by the flush). */
    final Map<UUID, Double> pendingXp = new java.util.HashMap<>();
    /** Hourly set XP caps (sell). */
    final SetXp.HourlyCap caps = new SetXp.HourlyCap();
    long lastBar;
    /** The bonuses (and worn armor) use the warzone rules right now. */
    boolean warzone;
    /** Where the last trail puff was (moving players only). */
    org.bukkit.Location lastTrail;
    /** The profile's loadout presets (W3), by menu place. */
    List<Loadout> loadouts = List.of();
    /** Loadout slots bought with store credits (account-wide). */
    int boughtSlots;

    Optional<Loadout> loadout(UUID id) {
        return loadouts.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    Optional<Loadout> loadoutNamed(String name) {
        String key = Loadout.key(name);
        return loadouts.stream().filter(l -> l.nameKey().equals(key)).findFirst();
    }

    Optional<Loadout> loadoutAt(int index) {
        return loadouts.stream().filter(l -> l.index() == index).findFirst();
    }

    void putLoadout(Loadout l) {
        List<Loadout> next = new ArrayList<>();
        for (Loadout old : loadouts) {
            if (!old.id().equals(l.id())) {
                next.add(old);
            }
        }
        next.add(l);
        next.sort(java.util.Comparator.comparingInt(Loadout::index));
        loadouts = List.copyOf(next);
    }

    void removeLoadout(UUID id) {
        loadouts = loadouts.stream().filter(l -> !l.id().equals(id)).toList();
    }

    Session(UUID player, UUID profile) {
        this.player = player;
        this.profile = profile;
    }

    Optional<WardrobeStore.Entry> entry(UUID entryId) {
        for (WardrobeStore.Entry e : entries) {
            if (e.entryId().equals(entryId)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** The active, unmerged entry of a set (one per set per profile). */
    Optional<WardrobeStore.Entry> owned(String setId) {
        for (WardrobeStore.Entry e : entries) {
            if (e.setId().equals(setId) && e.mergedSetId() == null) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    Set<String> ownedIds() {
        Set<String> out = new LinkedHashSet<>();
        for (WardrobeStore.Entry e : entries) {
            out.add(e.setId());
            if (e.mergedSetId() != null) {
                out.add(e.mergedSetId());
            }
        }
        return out;
    }

    /** Replaces an entry (same id) with a newer copy (XP, level). */
    void replace(WardrobeStore.Entry e) {
        List<WardrobeStore.Entry> next = new ArrayList<>(entries.size());
        for (WardrobeStore.Entry old : entries) {
            next.add(old.entryId().equals(e.entryId()) ? e : old);
        }
        entries = List.copyOf(next);
    }

    /** Active entries that contain a set (its own entry and merged entries with it). */
    List<WardrobeStore.Entry> containing(String setId) {
        List<WardrobeStore.Entry> out = new ArrayList<>();
        for (WardrobeStore.Entry e : entries) {
            if (e.setId().equals(setId) || setId.equals(e.mergedSetId())) {
                out.add(e);
            }
        }
        return out;
    }

    /** Merged entries (shown after their first set in /wardrobe). */
    List<WardrobeStore.Entry> mergedEntries() {
        return entries.stream().filter(WardrobeStore.Entry::isMerged).toList();
    }

    /** Worn pieces per entry id. */
    Map<UUID, Integer> wornPerEntry() {
        Map<UUID, Integer> out = new java.util.LinkedHashMap<>();
        for (WardrobeStore.Equipped q : equipped.values()) {
            out.merge(q.entryId(), 1, Integer::sum);
        }
        return out;
    }

    void add(WardrobeStore.Entry e) {
        List<WardrobeStore.Entry> next = new ArrayList<>(entries);
        next.add(e);
        entries = List.copyOf(next);
    }

    void remove(UUID entryId) {
        entries = entries.stream().filter(e -> !e.entryId().equals(entryId)).toList();
        equipped.values().removeIf(q -> q.entryId().equals(entryId));
    }

    /** Pieces of an entry worn now. */
    int worn(UUID entryId) {
        int n = 0;
        for (WardrobeStore.Equipped q : equipped.values()) {
            if (q.entryId().equals(entryId)) {
                n++;
            }
        }
        return n;
    }
}
