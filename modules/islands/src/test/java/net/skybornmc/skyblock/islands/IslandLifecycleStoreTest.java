package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M2.7 against a real H2 database: disband transaction, slot clearing → free → reuse, cooldown rules, staff add. */
class IslandLifecycleStoreTest {

    private static final long HOUR = 3_600_000L;
    private static final long MIN5 = 5 * 60_000L;
    private static final MemberStore.Limits LIMITS = new MemberStore.Limits(1, 3, 24 * HOUR, 4, 8);

    @TempDir
    Path dir;
    private Database db;
    private final long now = 1_000 * HOUR;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "lifecycle-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private Island create(UUID owner, long at, long cooldown) {
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, cooldown, "islands", at));
        return assertInstanceOf(IslandStore.CreateResult.Created.class, result).island();
    }

    private IslandStore.CreateResult tryCreate(UUID owner, long at) {
        return db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, 24 * HOUR, "islands", at));
    }

    private IslandStore.DisbandResult disband(UUID island, UUID expectedOwner, String reason, long at) {
        return db.blockingTransaction(c -> IslandStore.disband(c, island, expectedOwner, reason, expectedOwner, at));
    }

    private void join(UUID island, UUID leader, UUID target, long at) {
        assertInstanceOf(MemberStore.InviteResult.Created.class,
                db.blockingTransaction(c -> MemberStore.invite(c, island, leader, target, at, at + MIN5, LIMITS)));
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, db.blockingTransaction(c -> MemberStore.accept(c, island, target, at, LIMITS)));
    }

    private int count(String sql, Object... params) {
        return db.blocking(c -> Sql.queryOne(c, sql, rs -> rs.getInt(1), params).orElse(0));
    }

    private String slotState(int slot) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT state FROM island_slots WHERE id = ?", rs -> rs.getString(1), slot).orElseThrow());
    }

    @Test
    void disbandRemovesEverythingButKeepsTheIslandRowForHistory() {
        UUID leader = UUID.randomUUID();
        Island island = create(leader, now, 0);
        UUID member = UUID.randomUUID();
        join(island.id(), leader, member, now + 1);
        UUID coop = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        db.blocking(c -> {
            MemberStore.addCoop(c, island.id(), coop, leader, now + 2);
            MemberStore.invite(c, island.id(), leader, invited, now + 2, now + MIN5, LIMITS);
            IslandStore.savePerm(c, island.id(), PermRole.MEMBER, IslandPerm.BREAK, false, now + 2);
            return null;
        });

        IslandStore.DisbandResult.Disbanded done = assertInstanceOf(IslandStore.DisbandResult.Disbanded.class,
                disband(island.id(), leader, IslandStore.REASON_DISBAND, now + 10));
        assertEquals(leader, done.owner());
        assertEquals(island.slotId(), done.slotId());
        assertEquals(Role.LEADER, done.members().get(leader));
        assertEquals(Role.MEMBER, done.members().get(member));
        assertEquals(List.of(coop), done.coops());

        assertEquals(1, count("SELECT COUNT(*) FROM islands WHERE id = ? AND deleted_at = ? AND deleted_reason = ? AND season_id = 1",
                island.id(), now + 10, "disband"), "the row stays, marked deleted, with its season");
        assertEquals(0, count("SELECT COUNT(*) FROM island_members WHERE island_id = ?", island.id()));
        assertEquals(0, count("SELECT COUNT(*) FROM island_coops WHERE island_id = ?", island.id()));
        assertEquals(0, count("SELECT COUNT(*) FROM island_invites WHERE island_id = ?", island.id()));
        assertEquals(0, count("SELECT COUNT(*) FROM island_role_perms WHERE island_id = ?", island.id()));
        assertEquals("clearing", slotState(island.slotId()));
        assertEquals(1, count("SELECT COUNT(*) FROM player_island_joins WHERE uuid = ? AND left_reason = ? AND left_at = ?",
                member, "disband", now + 10), "the join history records why the member left");
        assertTrue(db.blocking(c -> IslandStore.loadAll(c, "islands")).isEmpty(), "removed islands are not loaded");

        assertInstanceOf(IslandStore.DisbandResult.Gone.class, disband(island.id(), leader, IslandStore.REASON_DISBAND, now + 11),
                "a second disband finds nothing");
    }

    @Test
    void onlyTheCurrentLeaderCanDisband() {
        UUID leader = UUID.randomUUID();
        Island island = create(leader, now, 0);
        assertInstanceOf(IslandStore.DisbandResult.NotLeader.class, disband(island.id(), UUID.randomUUID(), IslandStore.REASON_DISBAND, now));
        assertEquals("used", slotState(island.slotId()), "nothing changed");
        assertEquals(1, count("SELECT COUNT(*) FROM island_members WHERE island_id = ?", island.id()));
    }

    @Test
    void aClearingSlotIsNotReusedUntilItIsFree() {
        UUID first = UUID.randomUUID();
        Island island = create(first, now, 0);
        disband(island.id(), first, IslandStore.REASON_DELETE, now + 1);
        List<IslandStore.ClearingSlot> clearing = db.blocking(IslandStore::clearingSlots);
        assertEquals(1, clearing.size());
        assertEquals(island.slotId(), clearing.getFirst().slotId());
        assertEquals(island.id(), clearing.getFirst().islandId());
        assertEquals(3, clearing.getFirst().sizeChunks());

        Island second = create(UUID.randomUUID(), now + 2, 0);
        assertFalse(second.slotId() == island.slotId(), "a half-cleared slot never gets a new island");

        assertTrue(db.<Boolean>blocking(c -> IslandStore.freeSlot(c, island.slotId(), now + 3)));
        assertFalse(db.<Boolean>blocking(c -> IslandStore.freeSlot(c, island.slotId(), now + 4)), "free -> free is not a move");
        assertEquals("free", slotState(island.slotId()));
        assertEquals(0, count("SELECT COUNT(*) FROM island_slots WHERE id = ? AND island_id IS NOT NULL", island.slotId()));
        assertTrue(db.blocking(IslandStore::clearingSlots).isEmpty());

        Island third = create(UUID.randomUUID(), now + 5, 0);
        assertEquals(island.slotId(), third.slotId(), "the freed slot is reused first");
        assertEquals("used", slotState(third.slotId()));
    }

    @Test
    void onlyAPlayerDisbandStartsTheCreationCooldown() {
        UUID owner = UUID.randomUUID();
        Island deleted = create(owner, now, 0);
        disband(deleted.id(), null, IslandStore.REASON_DELETE, now + 1);
        assertTrue(db.blocking(c -> IslandStore.lastDisband(c, owner)).isEmpty(), "a staff delete is no cooldown");
        Island reset = assertInstanceOf(IslandStore.CreateResult.Created.class, tryCreate(owner, now + 2)).island();
        disband(reset.id(), null, IslandStore.REASON_RESET, now + 3);
        Island again = assertInstanceOf(IslandStore.CreateResult.Created.class, tryCreate(owner, now + 4), "a reset is no cooldown").island();

        disband(again.id(), owner, IslandStore.REASON_DISBAND, now + 5);
        assertEquals(now + 5, db.blocking(c -> IslandStore.lastDisband(c, owner)).orElseThrow());
        IslandStore.CreateResult.Cooldown cooldown = assertInstanceOf(IslandStore.CreateResult.Cooldown.class, tryCreate(owner, now + 5 + HOUR));
        assertEquals(23 * HOUR, cooldown.remainingMillis());
        assertInstanceOf(IslandStore.CreateResult.Created.class, tryCreate(owner, now + 5 + 24 * HOUR));
    }

    @Test
    void disbandNeverStartsTheJoinCooldown() {
        UUID leader = UUID.randomUUID();
        Island island = create(leader, now, 0);
        UUID member = UUID.randomUUID();
        join(island.id(), leader, member, now);
        long later = now + 30 * HOUR; // the join itself is older than the 24 h cooldown
        disband(island.id(), leader, IslandStore.REASON_DISBAND, later);

        JoinRules.History memberHistory = db.blocking(c -> MemberStore.history(c, member, 1));
        assertEquals(null, memberHistory.lastLeave(), "a disband is not a leave for the cooldown");
        assertInstanceOf(JoinRules.Check.Ok.class, db.blocking(c -> MemberStore.joinCheck(c, member, later + 1, LIMITS)));
        assertInstanceOf(JoinRules.Check.Ok.class, db.blocking(c -> MemberStore.joinCheck(c, leader, later + 1, LIMITS)),
                "the old leader can accept an invite right away");

        // a real leave still counts
        UUID other = UUID.randomUUID();
        Island second = create(other, later, 0);
        join(second.id(), other, member, later + 1);
        db.blockingTransaction(c -> MemberStore.removeMember(c, second.id(), member, MemberStore.LEFT_LEAVE, 1, later + 2));
        assertEquals(later + 2, db.blocking(c -> MemberStore.history(c, member, 1)).lastLeave());
    }

    @Test
    void staffAddMemberSkipsJoinLimitsButKeepsSlotsAndOneIsland() {
        UUID leader = UUID.randomUUID();
        Island island = create(leader, now, 0);
        UUID staff = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        // the target is on cooldown and at the season limit: staff can still add them
        db.blocking(c -> Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, left_at, left_reason, counted) "
                + "VALUES (?, ?, 1, ?, ?, 'kick', TRUE), (?, ?, 1, ?, ?, 'kick', TRUE), (?, ?, 1, ?, ?, 'kick', TRUE)",
                target, UUID.randomUUID(), now - 3, now - 2, target, UUID.randomUUID(), now - 3, now - 2, target, UUID.randomUUID(), now - 3, now - 1));
        MemberStore.AddResult.Added added = assertInstanceOf(MemberStore.AddResult.Added.class,
                db.blockingTransaction(c -> MemberStore.addMember(c, island.id(), target, staff, now, LIMITS)));
        assertTrue(added.converted(), "a second member makes it a team island");
        assertEquals("team", db.blocking(c -> Sql.queryOne(c, "SELECT type FROM islands WHERE id = ?", rs -> rs.getString(1), island.id()).orElseThrow()));
        assertEquals(1, count("SELECT COUNT(*) FROM player_island_joins WHERE uuid = ? AND island_id = ? AND counted = FALSE", target, island.id()),
                "a staff add never counts as one of the season's joins");
        assertEquals(3, db.blocking(c -> MemberStore.history(c, target, 1)).joinsThisSeason());

        assertInstanceOf(MemberStore.AddResult.HasIsland.class,
                db.blockingTransaction(c -> MemberStore.addMember(c, island.id(), target, staff, now, LIMITS)));
        UUID owner2 = UUID.randomUUID();
        create(owner2, now, 0);
        assertInstanceOf(MemberStore.AddResult.HasIsland.class,
                db.blockingTransaction(c -> MemberStore.addMember(c, island.id(), owner2, staff, now, LIMITS)));
        for (int i = 0; i < 2; i++) {
            UUID extra = UUID.randomUUID();
            assertInstanceOf(MemberStore.AddResult.Added.class,
                    db.blockingTransaction(c -> MemberStore.addMember(c, island.id(), extra, staff, now, LIMITS)));
        }
        assertInstanceOf(MemberStore.AddResult.Full.class,
                db.blockingTransaction(c -> MemberStore.addMember(c, island.id(), UUID.randomUUID(), staff, now, LIMITS)));
        disband(island.id(), null, IslandStore.REASON_DELETE, now + 1);
        assertInstanceOf(MemberStore.AddResult.IslandGone.class,
                db.blockingTransaction(c -> MemberStore.addMember(c, island.id(), UUID.randomUUID(), staff, now, LIMITS)));
    }
}
