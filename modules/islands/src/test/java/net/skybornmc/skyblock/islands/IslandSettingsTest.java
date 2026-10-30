package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ISL2 /is settings: flag defaults (owner round 3), parsing, per-island overrides, storage; real clock; island fly rule. */
class IslandSettingsTest {

    @TempDir
    Path dir;

    @Test
    void ownerDefaultsShipInTheConfig() {
        FlagDefaults.Result result = new IslandsConfig().flagDefaults();
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        FlagDefaults d = result.defaults();
        assertEquals(IslandFlag.OFF, d.value(IslandFlag.MOB_SPAWNING));
        assertEquals(IslandFlag.ON, d.value(IslandFlag.ANIMAL_SPAWNING));
        assertEquals(IslandFlag.OFF, d.value(IslandFlag.FIRE_SPREAD));
        assertEquals(IslandFlag.OFF, d.value(IslandFlag.EXPLOSIONS));
        assertEquals(IslandFlag.ON, d.value(IslandFlag.LEAF_DECAY));
        assertEquals(IslandFlag.OFF, d.value(IslandFlag.CROP_TRAMPLING));
        assertEquals(IslandFlag.ON, d.value(IslandFlag.NOTIFICATIONS));
        assertEquals(IslandFlag.ON, d.value(IslandFlag.VISITOR_FLY));
        assertEquals("sunny", d.value(IslandFlag.WEATHER));
        assertEquals("day", d.value(IslandFlag.TIME));
        for (IslandFlag flag : IslandFlag.values()) {
            assertEquals(FlagDefaults.owner(flag), FlagDefaults.owner().value(flag), flag.key());
        }
        RolePermissions perms = new IslandsConfig().rolePermissions().permissions();
        assertFalse(perms.defaultAllows(PermRole.VISITOR, IslandPerm.PICKUP), "visitors pick up items: off");
    }

    @Test
    void invalidDefaultsFallBackToTheOwnersValues() {
        Map<IslandFlag, String> raw = new EnumMap<>(IslandFlag.class);
        raw.put(IslandFlag.WEATHER, "snow");
        raw.put(IslandFlag.TIME, "NIGHT");
        raw.put(IslandFlag.EXPLOSIONS, "yes");
        FlagDefaults.Result result = FlagDefaults.parse(raw);
        assertEquals(1, result.problems().size());
        assertEquals("sunny", result.defaults().value(IslandFlag.WEATHER));
        assertEquals("night", result.defaults().value(IslandFlag.TIME));
        assertEquals(IslandFlag.ON, result.defaults().value(IslandFlag.EXPLOSIONS));
    }

    @Test
    void parsingAndCycling() {
        assertEquals(IslandFlag.ON, IslandFlag.FIRE_SPREAD.parse("TRUE").orElseThrow());
        assertEquals(IslandFlag.OFF, IslandFlag.FIRE_SPREAD.parse("disable").orElseThrow());
        assertTrue(IslandFlag.FIRE_SPREAD.parse("rain").isEmpty());
        assertEquals("storm", IslandFlag.WEATHER.parse("Storm").orElseThrow());
        assertTrue(IslandFlag.WEATHER.parse("on").isEmpty());
        assertEquals(IslandFlag.OFF, IslandFlag.LEAF_DECAY.next(IslandFlag.ON));
        assertEquals(IslandFlag.ON, IslandFlag.LEAF_DECAY.next(IslandFlag.OFF));
        assertEquals("rain", IslandFlag.WEATHER.next("sunny"));
        assertEquals("sunny", IslandFlag.WEATHER.next("storm"));
        assertEquals("real", IslandFlag.TIME.next("night"));
        assertEquals("day", IslandFlag.TIME.next("real"));
        assertEquals(List.of("day", "noon", "sunset", "night", "real"), IslandFlag.TIME.choices());
        assertEquals(IslandFlag.CROP_TRAMPLING, IslandFlag.byKey("crop_trampling").orElseThrow());
        assertTrue(IslandFlag.byKey("mob-stacking").isEmpty(), "mob stacking is not a setting");
        assertEquals(IslandFlag.values().length, IslandSettingsManager.keys().stream().distinct().count());
    }

    @Test
    void islandValuesOverrideDefaultsAndVirtualFlagsAreNeverStored() {
        FlagDefaults defaults = FlagDefaults.owner();
        IslandSettings settings = new IslandSettings();
        assertTrue(settings.isEmpty());
        assertFalse(settings.on(IslandFlag.MOB_SPAWNING, defaults));
        settings.set(IslandFlag.MOB_SPAWNING, "on");
        settings.set(IslandFlag.TIME, "night");
        assertTrue(settings.on(IslandFlag.MOB_SPAWNING, defaults));
        assertEquals("night", settings.value(IslandFlag.TIME, defaults));
        settings.set(IslandFlag.TIME, "bogus");
        assertNull(settings.override(IslandFlag.TIME), "invalid values clear the override");
        settings.set(IslandFlag.VISITOR_PICKUP, "on");
        assertNull(settings.override(IslandFlag.VISITOR_PICKUP), "visitor pickup is the matrix permission");
        settings.set(IslandFlag.MOB_SPAWNING, null);
        assertTrue(settings.isEmpty());
    }

    @Test
    void settingsMenuHasASlotAndIconForEverySetting() {
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        for (IslandFlag flag : IslandFlag.values()) {
            Integer slot = SettingsMenu.SLOTS.get(flag);
            assertTrue(slot != null && slot > 9 && slot < 45 && slot % 9 != 0 && slot % 9 != 8, "slot of " + flag);
            assertTrue(slots.add(slot), "unique slot " + slot);
            assertTrue(SettingsMenu.ICONS.containsKey(flag), "icon of " + flag);
        }
    }

    @Test
    void realTimeMapsTheClockToTicks() {
        assertEquals(0, IslandClock.realTicks(LocalTime.of(6, 0)));
        assertEquals(6000, IslandClock.realTicks(LocalTime.of(12, 0)));
        assertEquals(18000, IslandClock.realTicks(LocalTime.of(0, 0)));
        assertEquals(23000, IslandClock.realTicks(LocalTime.of(5, 0)));
        assertEquals(12500, IslandClock.realTicks(LocalTime.of(18, 30)));
    }

    @Test
    void islandFlyRule() {
        assertTrue(IslandFly.allowed(true, true, false, true, false), "member with the permission");
        assertTrue(IslandFly.allowed(true, true, false, false, true), "member, island has Wings");
        assertFalse(IslandFly.allowed(true, true, true, false, false), "nothing grants fly");
        assertTrue(IslandFly.allowed(true, false, true, true, false), "visitor while visitor fly is on");
        assertFalse(IslandFly.allowed(true, false, false, true, true), "visitor fly off");
        assertFalse(IslandFly.allowed(false, true, true, true, true), "outside the border");
    }

    @Test
    void invitesMenuListsReceivedFirstNewestFirst() {
        UUID me = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        Invites.Invite older = new Invites.Invite(UUID.randomUUID(), me, UUID.randomUUID(), 1, 100);
        Invites.Invite newer = new Invites.Invite(UUID.randomUUID(), me, UUID.randomUUID(), 5, 100);
        Invites.Invite sent = new Invites.Invite(island, UUID.randomUUID(), me, 9, 100);
        List<InvitesMenu.Entry> order = InvitesMenu.order(List.of(older, newer), List.of(sent));
        assertEquals(List.of(newer, older, sent), order.stream().map(InvitesMenu.Entry::invite).toList());
        assertFalse(order.getFirst().sent());
        assertTrue(order.getLast().sent());
    }

    @Test
    void settingsAreStoredLoadedAndRemovedWithTheIsland() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "settings-test";
        config.poolSize = 2;
        Database db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        try {
            new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
            UUID owner = UUID.randomUUID();
            IslandStore.CreateResult created = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, 0, "islands", 1000));
            UUID id = assertInstanceOf(IslandStore.CreateResult.Created.class, created).island().id();
            db.blocking(c -> {
                IslandStore.saveSetting(c, id, IslandFlag.WEATHER, "storm", 1000);
                IslandStore.saveSetting(c, id, IslandFlag.EXPLOSIONS, "on", 1000);
                IslandStore.saveSetting(c, id, IslandFlag.EXPLOSIONS, null, 1001); // back to the default
                return null;
            });
            Island island = db.blocking(c -> IslandStore.loadAll(c, "islands")).getFirst();
            assertEquals("storm", island.settings().override(IslandFlag.WEATHER));
            assertNull(island.settings().override(IslandFlag.EXPLOSIONS));
            db.blocking(c -> IslandStore.disband(c, id, owner, IslandStore.REASON_DISBAND, owner, 2000));
            int rows = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM island_settings WHERE island_id = ?", rs -> rs.getInt(1), id)
                    .orElse(0));
            assertEquals(0, rows);
        } finally {
            db.close();
        }
    }
}
