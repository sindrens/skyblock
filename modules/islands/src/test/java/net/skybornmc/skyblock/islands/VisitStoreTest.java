package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lock, bans, warps (limits, moves, signs), visit counters, ratings and likes against a real H2 database (MySQL mode). */
class VisitStoreTest {

    private static final VisitStore.WarpLimits LIMITS = new VisitStore.WarpLimits(3, 10);
    private static final Island.Home HERE = new Island.Home(1.5, 100, 2.5, 90, 0);

    @TempDir
    Path dir;
    private Database db;
    private final long now = 1_000_000_000L;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "visits-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private UUID island(UUID owner) {
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, "islands", now));
        return assertInstanceOf(IslandStore.CreateResult.Created.class, result).island().id();
    }

    private boolean bool(Sql.SqlFunction<Boolean> work) {
        return db.blocking(work);
    }

    private boolean boolTx(Sql.SqlFunction<Boolean> work) {
        return db.blockingTransaction(work);
    }

    private int count(String sql) {
        return db.blocking(c -> Sql.queryOne(c, sql, rs -> rs.getInt(1)).orElse(-1));
    }

    private Island loaded(UUID id) {
        return db.blocking(c -> IslandStore.loadAll(c, "islands")).stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
    }

    private VisitStore.WarpResult warp(UUID island, String name, WarpCategory category, IslandWarp.SignPos sign) {
        return db.blockingTransaction(c -> VisitStore.saveWarp(c, island, name, category, "emerald", HERE, sign, UUID.randomUUID(), now, LIMITS));
    }

    @Test
    void lockIsStoredAndLoaded() {
        UUID id = island(UUID.randomUUID());
        assertFalse(loaded(id).locked());
        assertTrue(bool(c -> VisitStore.setLocked(c, id, true)));
        assertTrue(loaded(id).locked());
        assertNull(loaded(id).warpSlots());
    }

    @Test
    void bansRespectMembersDuplicatesAndTheLimit() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(VisitStore.BanResult.MEMBER, db.blockingTransaction(c -> VisitStore.ban(c, id, leader, leader, now, 2)));
        assertEquals(VisitStore.BanResult.BANNED, db.blockingTransaction(c -> VisitStore.ban(c, id, a, leader, now, 2)));
        assertEquals(VisitStore.BanResult.ALREADY, db.blockingTransaction(c -> VisitStore.ban(c, id, a, leader, now, 2)));
        assertEquals(VisitStore.BanResult.BANNED, db.blockingTransaction(c -> VisitStore.ban(c, id, b, leader, now, 2)));
        assertEquals(VisitStore.BanResult.LIMIT, db.blockingTransaction(c -> VisitStore.ban(c, id, UUID.randomUUID(), leader, now, 2)));
        assertEquals(VisitStore.BanResult.GONE, db.blockingTransaction(c -> VisitStore.ban(c, UUID.randomUUID(), a, leader, now, 2)));
        Map<UUID, Set<UUID>> bans = db.blocking(VisitStore::loadBans);
        assertEquals(Set.of(a, b), bans.get(id));
        VisitStore store = new VisitStore(db);
        assertTrue(store.unban(id, a).join());
        assertFalse(store.unban(id, a).join());
        assertEquals(Set.of(b), db.blocking(VisitStore::loadBans).get(id));
    }

    @Test
    void threeWarpsPerIslandAndMovingNeverCounts() {
        UUID id = island(UUID.randomUUID());
        assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "Shop", WarpCategory.SHOPS, null));
        assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "farm", null, null));
        assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "parkour", WarpCategory.PARKOUR, null));
        VisitStore.WarpResult.Full full = assertInstanceOf(VisitStore.WarpResult.Full.class, warp(id, "fourth", null, null));
        assertEquals(3, full.slots());
        // Same name (any case) = move, even when full; the category stays unless given.
        VisitStore.WarpResult.Moved moved = assertInstanceOf(VisitStore.WarpResult.Moved.class, warp(id, "SHOP", null, null));
        assertEquals(WarpCategory.SHOPS, moved.category());
        List<IslandWarp> warps = db.blocking(VisitStore::loadWarps);
        assertEquals(3, warps.size());
        IslandWarp farm = warps.stream().filter(w -> w.name().equals("farm")).findFirst().orElseThrow();
        assertEquals(WarpCategory.OTHER, farm.category());
        assertTrue(farm.isPublic());
        // More slots from an upgrade.
        db.blocking(c -> Sql.update(c, "UPDATE islands SET warp_slots = ? WHERE id = ?", 4, id));
        assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "fourth", null, null));
        assertEquals(4, loaded(id).warpSlots());
    }

    @Test
    void signWarpsCountTowardSlotsAndNeverTakeOverANameUsedAlready() {
        UUID id = island(UUID.randomUUID());
        IslandWarp.SignPos sign = new IslandWarp.SignPos(10, 101, 12);
        VisitStore.WarpResult.Created created = assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "market", WarpCategory.SHOPS, sign));
        assertEquals(sign, created.warp().sign());
        assertInstanceOf(VisitStore.WarpResult.NameTaken.class, warp(id, "Market", null, new IslandWarp.SignPos(1, 2, 3)));
        assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "b", null, null));
        assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "c", null, null));
        assertInstanceOf(VisitStore.WarpResult.Full.class, warp(id, "d", null, new IslandWarp.SignPos(1, 2, 3)), "a sign can't exceed the slots");
        assertEquals(sign, db.blocking(VisitStore::loadWarps).stream().filter(w -> w.name().equals("market")).findFirst().orElseThrow().sign());
        // Moving a sign warp with the command ends the sign link.
        VisitStore.WarpResult.Moved moved = assertInstanceOf(VisitStore.WarpResult.Moved.class, warp(id, "market", null, null));
        assertNull(moved.sign());
        assertNull(db.blocking(VisitStore::loadWarps).stream().filter(w -> w.name().equals("market")).findFirst().orElseThrow().sign());
    }

    @Test
    void warpEditsAndDelete() {
        UUID id = island(UUID.randomUUID());
        IslandWarp warp = assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "shop", WarpCategory.SHOPS, null)).warp();
        VisitStore store = new VisitStore(db);
        assertTrue(store.setWarpIcon(warp.id(), "diamond").join());
        assertTrue(store.setWarpPublic(warp.id(), false).join());
        assertTrue(store.setWarpCategory(warp.id(), WarpCategory.BUILDS).join());
        IslandWarp loaded = db.blocking(VisitStore::loadWarps).getFirst();
        assertEquals("diamond", loaded.icon());
        assertFalse(loaded.isPublic());
        assertEquals(WarpCategory.BUILDS, loaded.category());
        assertTrue(store.recordWarpVisit(warp.id(), UUID.randomUUID(), 5).join());
        assertTrue(store.deleteWarp(warp.id()).join());
        assertFalse(store.deleteWarp(warp.id()).join());
        assertEquals(0, db.blocking(VisitStore::loadWarps).size());
        assertEquals(0, count("SELECT COUNT(*) FROM island_warp_visits"));
    }

    @Test
    void warpVisitsCountOncePerVisitorPerDay() {
        UUID id = island(UUID.randomUUID());
        IslandWarp warp = assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "shop", null, null)).warp();
        UUID visitor = UUID.randomUUID();
        assertTrue(boolTx(c -> VisitStore.recordWarpVisit(c, warp.id(), visitor, 100)));
        assertFalse(boolTx(c -> VisitStore.recordWarpVisit(c, warp.id(), visitor, 100)));
        assertTrue(boolTx(c -> VisitStore.recordWarpVisit(c, warp.id(), visitor, 101)));
        assertTrue(boolTx(c -> VisitStore.recordWarpVisit(c, warp.id(), UUID.randomUUID(), 101)));
        assertFalse(boolTx(c -> VisitStore.recordWarpVisit(c, 999_999L, visitor, 101)), "unknown warp");
        assertEquals(3, db.blocking(VisitStore::loadWarps).getFirst().visits());
    }

    @Test
    void uniqueVisitorsPerDayAndStats() {
        UUID id = island(UUID.randomUUID());
        UUID other = island(UUID.randomUUID());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(bool(c -> VisitStore.recordVisit(c, id, a, 10, 1)));
        assertFalse(bool(c -> VisitStore.recordVisit(c, id, a, 10, 1)), "same visitor, same day");
        assertTrue(bool(c -> VisitStore.recordVisit(c, id, a, 11, 1)));
        assertTrue(bool(c -> VisitStore.recordVisit(c, id, b, 11, 1)));
        assertTrue(bool(c -> VisitStore.recordVisit(c, other, a, 3, 1)));
        db.blockingTransaction(c -> VisitStore.rate(c, id, a, 1, 5, now));
        db.blockingTransaction(c -> VisitStore.rate(c, id, b, 1, 2, now));
        db.blockingTransaction(c -> VisitStore.rate(c, id, b, 0, 1, now)); // another season
        db.blocking(c -> VisitStore.like(c, id, a, 11, 1, now));
        Map<UUID, Showcase.Stats> stats = db.blocking(c -> VisitStore.stats(c, 10, 1));
        Showcase.Stats s = stats.get(id);
        assertEquals(3, s.visits());
        assertEquals(2, s.ratingCount());
        assertEquals(7, s.ratingSum());
        assertEquals(3.5, s.average());
        assertEquals(1, s.likes());
        assertFalse(stats.containsKey(other), "visits before the window don't count");
        assertEquals(2, db.blocking(c -> VisitStore.stats(c, 11, 1)).get(id).visits());
    }

    @Test
    void oneRatingPerPlayerPerSeasonIsChangeable() {
        UUID id = island(UUID.randomUUID());
        UUID player = UUID.randomUUID();
        assertEquals(Optional.empty(), db.blockingTransaction(c -> VisitStore.rate(c, id, player, 1, 3, now)));
        assertEquals(Optional.of(3), db.blockingTransaction(c -> VisitStore.rate(c, id, player, 1, 5, now)));
        assertEquals(Optional.empty(), db.blockingTransaction(c -> VisitStore.rate(c, id, player, 2, 4, now)), "new season, new rating");
        VisitStore.VoteContext context = db.blocking(c -> VisitStore.voteContext(c, id, player, 1, 7));
        assertEquals(5, context.rating());
        assertFalse(context.visited());
        assertFalse(context.likedToday());
        assertEquals(2, count("SELECT COUNT(*) FROM island_ratings"));
    }

    @Test
    void oneLikePerPlayerPerIslandPerDay() {
        UUID id = island(UUID.randomUUID());
        UUID other = island(UUID.randomUUID());
        UUID player = UUID.randomUUID();
        assertTrue(bool(c -> VisitStore.like(c, id, player, 20, 1, now)));
        assertFalse(bool(c -> VisitStore.like(c, id, player, 20, 1, now)), "twice the same day");
        assertTrue(bool(c -> VisitStore.like(c, other, player, 20, 1, now)), "another island the same day is fine");
        assertTrue(bool(c -> VisitStore.like(c, id, player, 21, 1, now)), "the next day");
        db.blocking(c -> VisitStore.recordVisit(c, id, player, 20, 1));
        VisitStore.VoteContext context = db.blocking(c -> VisitStore.voteContext(c, id, player, 1, 21));
        assertTrue(context.visited());
        assertTrue(context.likedToday());
        assertNull(context.rating());
        assertFalse(new VisitStore(db).like(id, player, 21, 1).join(), "async path: already liked");
    }

    @Test
    void ownerAnswer292CountsOtherAccountsFromTheSameAddress() {
        UUID id = island(UUID.randomUUID());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        String ip = VisitRules.ipHash("10.0.0.1");
        db.blockingTransaction(x -> VisitStore.rate(x, id, a, 1, 5, now, ip));
        db.blockingTransaction(x -> VisitStore.rate(x, id, b, 1, 4, now, ip));
        db.blockingTransaction(x -> VisitStore.rate(x, id, UUID.randomUUID(), 1, 4, now, VisitRules.ipHash("10.0.0.2")));
        assertEquals((Integer) 2, db.<Integer>blocking(x -> VisitStore.otherAccounts(x, id, c, 1, ip, true)), "a and b from the same address");
        assertEquals((Integer) 1, db.<Integer>blocking(x -> VisitStore.otherAccounts(x, id, a, 1, ip, true)), "the voter is not counted");
        assertEquals((Integer) 0, db.<Integer>blocking(x -> VisitStore.otherAccounts(x, id, c, 2, ip, true)), "another season");
        assertEquals((Integer) 0, db.<Integer>blocking(x -> VisitStore.otherAccounts(x, id, c, 1, null, true)), "no address known");
        assertEquals((Integer) 0, db.<Integer>blocking(x -> VisitStore.otherAccounts(x, id, c, 1, ip, false)), "likes are counted separately");
        assertTrue(bool(x -> VisitStore.like(x, id, a, 20, 1, now, ip)));
        assertEquals((Integer) 1, db.<Integer>blocking(x -> VisitStore.otherAccounts(x, id, c, 1, ip, false)));
        VisitStore.VoteContext context = new VisitStore(db).voteContext(id, c, 1, 20, ip, true).join();
        assertEquals(2, context.otherAccounts());
        assertEquals(VisitRules.VoteCheck.IP_LIMIT, VisitRules.canRate(5, 5, null, true, true, null, context.otherAccounts(), 3));
    }

    @Test
    void ownerAnswer293StaffHideAnIslandFromTheBrowser() {
        UUID id = island(UUID.randomUUID());
        assertTrue(bool(c -> VisitStore.setBrowserHidden(c, id, true, "offensive build", "Mod")));
        Island loaded = db.blocking(c -> IslandStore.loadAll(c, "islands")).stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
        assertTrue(loaded.browserHidden());
        assertEquals("offensive build", loaded.browserHiddenNote());
        assertTrue(bool(c -> VisitStore.setBrowserHidden(c, id, false, "ignored", "Mod")));
        Island shown = db.blocking(c -> IslandStore.loadAll(c, "islands")).stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
        assertFalse(shown.browserHidden());
        assertNull(shown.browserHiddenNote(), "the note goes with the hide");
        assertFalse(bool(c -> VisitStore.setBrowserHidden(c, UUID.randomUUID(), true, null, "Mod")), "unknown island");
    }

    @Test
    void ownerAnswer290PlaytimePerIslandForDiscover() {
        UUID id = island(UUID.randomUUID());
        db.blocking(c -> Sql.update(c, "INSERT INTO island_stats_total (island_id, member_key, stat_key, amount, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, UUID.randomUUID(), "playtime", 3600L, now));
        db.blocking(c -> Sql.update(c, "INSERT INTO island_stats_total (island_id, member_key, stat_key, amount, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, UUID.randomUUID(), "playtime", 1800L, now));
        db.blocking(c -> Sql.update(c, "INSERT INTO island_stats_total (island_id, member_key, stat_key, amount, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, UUID.randomUUID(), "blocks_mined", 99L, now));
        assertEquals(Long.valueOf(5400L), db.blocking(VisitStore::playtimes).get(id));
    }

    @Test
    void removingAnIslandDeletesItsWarpsAndBansButKeepsHistory() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID visitor = UUID.randomUUID();
        IslandWarp warp = assertInstanceOf(VisitStore.WarpResult.Created.class, warp(id, "shop", null, null)).warp();
        db.blockingTransaction(c -> VisitStore.recordWarpVisit(c, warp.id(), visitor, 1));
        db.blockingTransaction(c -> VisitStore.ban(c, id, UUID.randomUUID(), leader, now, 10));
        db.blocking(c -> VisitStore.recordVisit(c, id, visitor, 1, 1));
        db.blockingTransaction(c -> VisitStore.rate(c, id, visitor, 1, 4, now));
        assertInstanceOf(IslandStore.DisbandResult.Disbanded.class,
                db.blockingTransaction(c -> IslandStore.disband(c, id, leader, IslandStore.REASON_DISBAND, leader, now)));
        assertEquals(0, db.blocking(VisitStore::loadWarps).size());
        assertTrue(db.blocking(VisitStore::loadBans).isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM island_warp_visits"));
        assertEquals(1, count("SELECT COUNT(*) FROM island_visits"));
        assertEquals(1, count("SELECT COUNT(*) FROM island_ratings"));
    }
}
