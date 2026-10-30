package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loadout data on H2 (MySQL mode): presets per profile, and the swap transaction (LD4): outgoing items stored, incoming
 * items releasing and worn rows written all together or not at all, with the crash recovery seeing exactly one place
 * for every item.
 */
class LoadoutOpsTest {

    @TempDir
    Path dir;

    private Database db;
    private final WardrobeStore store = new WardrobeStore();
    private final LoadoutStore loadoutStore = new LoadoutStore();
    private WardrobeOps ops;
    private LoadoutOps loadouts;
    private final UUID player = UUID.randomUUID();
    private final UUID profile1 = player;
    private final UUID profile2 = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "loadout-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("loadout-test"));
        db.start();
        new Migrations(db, Logger.getLogger("loadout-test")).migrate("wardrobe", getClass().getClassLoader());
        ops = new WardrobeOps(db, store, (tx, guard) -> CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR)),
                System::currentTimeMillis, Logger.getLogger("loadout-test"));
        loadouts = new LoadoutOps(db, store, loadoutStore, System::currentTimeMillis, Logger.getLogger("loadout-test"));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private WardrobeStore.Stored item(UUID id, UUID profile) {
        return new WardrobeStore.Stored(id, profile, "tool", "tool_iron_pickaxe", "Iron Pickaxe", new byte[]{7, 7},
                WardrobeStore.StorageState.STORED, null, 1L);
    }

    private Loadout preset(UUID profile, int index, String name) {
        Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
        armor.put(ArmorPiece.HEAD, Loadout.Armor.NONE);
        Map<Integer, Loadout.Tool> hotbar = new TreeMap<>();
        hotbar.put(0, new Loadout.Tool(UUID.randomUUID(), "Pick", "iron_pickaxe"));
        return new Loadout(UUID.randomUUID(), profile, index, name, "iron_pickaxe", armor, null, hotbar, 1L);
    }

    private List<Loadout> presets(UUID profile) {
        return db.blocking(c -> loadoutStore.loadouts(c, profile));
    }

    private Map<UUID, WardrobeStore.StorageState> chest(UUID profile) {
        return db.blocking(c -> {
            Map<UUID, WardrobeStore.StorageState> out = new java.util.HashMap<>();
            for (WardrobeStore.Stored s : store.stored(c, profile)) {
                out.put(s.itemUuid(), s.state());
            }
            for (WardrobeStore.Stored s : store.releasing(c, profile)) {
                out.put(s.itemUuid(), s.state());
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ presets

    @Test
    void presetsAreSavedPerProfileWithUniquePlacesAndNames() {
        Loadout mining = preset(profile1, 0, "Mining");
        assertEquals(LoadoutOps.Status.OK, loadouts.create(mining, 2).join().status());
        assertEquals(LoadoutOps.Status.NO_SLOT, loadouts.create(preset(profile1, 0, "Other"), 2).join().status(), "place used");
        assertEquals(LoadoutOps.Status.TAKEN, loadouts.create(preset(profile1, 1, "MINING"), 2).join().status(), "name used");
        assertEquals(LoadoutOps.Status.NO_SLOT, loadouts.create(preset(profile1, 2, "Combat"), 2).join().status(), "only 2 slots");
        assertEquals(LoadoutOps.Status.OK, loadouts.create(preset(profile2, 0, "Mining"), 2).join().status(), "other profile: own presets");

        List<Loadout> saved = presets(profile1);
        assertEquals(1, saved.size());
        assertEquals(mining.hotbar(), saved.getFirst().hotbar(), "JSON round trip");
        assertEquals(mining.armor(), saved.getFirst().armor());

        assertEquals(LoadoutOps.Status.OK, loadouts.rename(mining.id(), profile1, "Deep-mine").join().status());
        assertEquals("Deep-mine", presets(profile1).getFirst().name());
        assertEquals(LoadoutOps.Status.GONE, loadouts.rename(mining.id(), profile2, "Stolen").join().status(), "P4: not from another profile");
        assertEquals(LoadoutOps.Status.OK, loadouts.icon(mining.id(), profile1, "diamond_pickaxe").join().status());
        assertEquals("diamond_pickaxe", presets(profile1).getFirst().icon());

        Loadout changed = presets(profile1).getFirst().withContents(Map.of(), "pet_1", Map.of(), 5L);
        assertEquals(LoadoutOps.Status.OK, loadouts.overwrite(changed).join().status());
        assertTrue(presets(profile1).getFirst().hotbar().isEmpty());
        assertEquals("pet_1", presets(profile1).getFirst().petId());

        assertEquals(LoadoutOps.Status.GONE, loadouts.delete(mining.id(), profile2).join().status());
        assertEquals(LoadoutOps.Status.OK, loadouts.delete(mining.id(), profile1).join().status());
        assertTrue(presets(profile1).isEmpty());
        assertEquals(1, presets(profile2).size());
    }

    @Test
    void boughtSlotsAreAccountWide() {
        assertEquals(0, (int) db.blocking(c -> loadoutStore.bought(c, player)));
        db.blocking(c -> {
            loadoutStore.addBought(c, player, 1, 1L);
            loadoutStore.addBought(c, player, 1, 2L);
            return null;
        });
        assertEquals(2, (int) db.blocking(c -> loadoutStore.bought(c, player)));
    }

    // ------------------------------------------------------------------ the swap

    @Test
    void swapStoresTakesAndWearsInOneTransaction() {
        WardrobeStore.Entry set = ops.giveSet(profile1, "delver", null, 0).join().entry();
        UUID incoming = UUID.randomUUID();
        UUID outgoing = UUID.randomUUID();
        assertTrue(ops.store(item(incoming, profile1), 27).join().ok());
        Loadout l = preset(profile1, 0, "Mining");
        assertTrue(loadouts.create(l, 2).join().ok());
        UUID plan = UUID.randomUUID();
        UUID nonce = UUID.randomUUID();
        LoadoutOps.Result r = loadouts.swap(new LoadoutOps.Swap(profile1, plan, l.id(), List.of(item(outgoing, profile1)), List.of(incoming),
                List.of(new WardrobeStore.Equipped(ArmorPiece.HEAD, set.entryId(), nonce)), List.of(), 27)).join();
        assertEquals(LoadoutOps.Status.OK, r.status());
        assertEquals(Set.of(incoming), r.taken().keySet());
        assertEquals(Map.of(incoming, WardrobeStore.StorageState.RELEASING, outgoing, WardrobeStore.StorageState.STORED), chest(profile1));
        assertEquals(nonce, db.blocking(c -> store.equipped(c, profile1)).get(ArmorPiece.HEAD).nonce());

        // Crash before the main thread ran: the join recovery sees the incoming item to hand out and the outgoing item
        // (still in the player's file) as a stale copy to remove. Exactly one place each.
        WardrobeOps.Recovery rec = ops.recovery(profile1, List.of(outgoing)).join();
        assertEquals(List.of(incoming), rec.releasing().stream().map(WardrobeStore.Stored::itemUuid).toList());
        assertEquals(Set.of(outgoing), rec.stale());

        assertTrue(loadouts.released(List.of(incoming), plan).join());
        assertEquals(Map.of(outgoing, WardrobeStore.StorageState.STORED), chest(profile1));
    }

    @Test
    void anIncomingItemThatLeftTheChestRollsEverythingBack() {
        WardrobeStore.Entry set = ops.giveSet(profile1, "delver", null, 0).join().entry();
        UUID outgoing = UUID.randomUUID();
        LoadoutOps.Result r = loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(item(outgoing, profile1)),
                List.of(UUID.randomUUID()), List.of(new WardrobeStore.Equipped(ArmorPiece.HEAD, set.entryId(), UUID.randomUUID())), List.of(), 27))
                .join();
        assertEquals(LoadoutOps.Status.GONE, r.status());
        assertTrue(chest(profile1).isEmpty(), "the outgoing item was not stored");
        assertTrue(db.blocking(c -> store.equipped(c, profile1)).isEmpty(), "no worn rows");
    }

    @Test
    void anotherProfilesChestItemCantBeSwappedIn() {
        UUID theirs = UUID.randomUUID();
        assertTrue(ops.store(item(theirs, profile2), 27).join().ok());
        LoadoutOps.Result r = loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(), List.of(theirs), List.of(),
                List.of(), 27)).join();
        assertEquals(LoadoutOps.Status.GONE, r.status(), "P4: nothing moves between own profiles");
        assertEquals(Map.of(theirs, WardrobeStore.StorageState.STORED), chest(profile2));
    }

    @Test
    void capacityCountsTheIncomingItemsAsLeaving() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(ops.store(item(a, profile1), 2).join().ok());
        assertTrue(ops.store(item(b, profile1), 2).join().ok());
        // Full chest (2/2): taking one out and putting one in fits.
        UUID out1 = UUID.randomUUID();
        assertEquals(LoadoutOps.Status.OK, loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(item(out1, profile1)),
                List.of(a), List.of(), List.of(), 2)).join().status());
        // Now 2 places used by others (b stored, out1 stored; a releasing under another plan counts too): no room.
        UUID out2 = UUID.randomUUID();
        assertEquals(LoadoutOps.Status.FULL, loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(item(out2, profile1)),
                List.of(), List.of(), List.of(), 2)).join().status());
        assertFalse(chest(profile1).containsKey(out2));
    }

    @Test
    void aStaleOutgoingCopyRollsBack() {
        UUID dup = UUID.randomUUID();
        UUID incoming = UUID.randomUUID();
        assertTrue(ops.store(item(dup, profile1), 27).join().ok());
        assertTrue(ops.store(item(incoming, profile1), 27).join().ok());
        LoadoutOps.Result r = loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(item(dup, profile1)), List.of(incoming),
                List.of(), List.of(), 27)).join();
        assertEquals(LoadoutOps.Status.STALE, r.status());
        assertEquals(WardrobeStore.StorageState.STORED, chest(profile1).get(incoming), "the incoming item was not handed out");
    }

    @Test
    void aSetThatIsNotOwnedRollsBack() {
        UUID incoming = UUID.randomUUID();
        assertTrue(ops.store(item(incoming, profile1), 27).join().ok());
        WardrobeStore.Entry other = ops.giveSet(profile2, "delver", null, 0).join().entry();
        LoadoutOps.Result r = loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(), List.of(incoming),
                List.of(new WardrobeStore.Equipped(ArmorPiece.HEAD, other.entryId(), UUID.randomUUID())), List.of(), 27)).join();
        assertEquals(LoadoutOps.Status.GONE, r.status());
        assertEquals(WardrobeStore.StorageState.STORED, chest(profile1).get(incoming));
    }

    @Test
    void takingOffWorksWithoutItems() {
        WardrobeStore.Entry set = ops.giveSet(profile1, "delver", null, 0).join().entry();
        assertTrue(ops.equip(profile1, List.of(new WardrobeStore.Equipped(ArmorPiece.LEGS, set.entryId(), UUID.randomUUID())), List.of()).join().ok());
        assertTrue(loadouts.swap(new LoadoutOps.Swap(profile1, UUID.randomUUID(), null, List.of(), List.of(), List.of(), List.of(ArmorPiece.LEGS), 27))
                .join().ok());
        assertTrue(db.blocking(c -> store.equipped(c, profile1)).isEmpty());
    }

    @Test
    void repointAfterAMerge() {
        UUID a = UUID.randomUUID();
        UUID merged = UUID.randomUUID();
        Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
        armor.put(ArmorPiece.CHEST, new Loadout.Armor(a));
        Loadout l = new Loadout(UUID.randomUUID(), profile1, 0, "X", "stone", armor, null, Map.of(), 1L);
        assertTrue(loadouts.create(l, 2).join().ok());
        assertEquals(1, (int) db.blocking(c -> loadoutStore.repoint(c, profile1, a, merged, 2L)));
        assertEquals(merged, presets(profile1).getFirst().armor().get(ArmorPiece.CHEST).entryId());
        assertEquals(0, (int) db.blocking(c -> loadoutStore.repoint(c, profile2, merged, a, 3L)), "only the profile's presets");
    }
}
