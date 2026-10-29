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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invites, accepts, limits, kicks, transfers and coops against a real H2 database (MySQL mode). */
class MemberStoreTest {

    private static final long HOUR = 3_600_000L;
    private static final long MIN5 = 5 * 60_000L;
    private static final MemberStore.Limits LIMITS = new MemberStore.Limits(1, 3, 24 * HOUR, 4, 8);

    @TempDir
    Path dir;
    private Database db;
    private long now = 1_000 * HOUR;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "members-test";
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
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, 0, "islands", now));
        return assertInstanceOf(IslandStore.CreateResult.Created.class, result).island().id();
    }

    private MemberStore.InviteResult invite(UUID island, UUID inviter, UUID target) {
        return db.blockingTransaction(c -> MemberStore.invite(c, island, inviter, target, now, now + MIN5, LIMITS));
    }

    private MemberStore.AcceptResult accept(UUID island, UUID target) {
        return db.blockingTransaction(c -> MemberStore.accept(c, island, target, now, LIMITS));
    }

    private String type(UUID island) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT type FROM islands WHERE id = ?", rs -> rs.getString(1), island).orElseThrow());
    }

    private void join(UUID island, UUID leader, UUID target) {
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(island, leader, target));
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(island, target));
    }

    @Test
    void firstInviteTurnsTheIslandIntoATeamIslandForever() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        assertEquals("solo", type(id));
        MemberStore.InviteResult.Created first = assertInstanceOf(MemberStore.InviteResult.Created.class, invite(id, leader, UUID.randomUUID()));
        assertTrue(first.converted());
        assertEquals("team", type(id));
        MemberStore.InviteResult.Created second = assertInstanceOf(MemberStore.InviteResult.Created.class, invite(id, leader, UUID.randomUUID()));
        assertFalse(second.converted(), "only the first invite converts");
    }

    @Test
    void refusedInviteDoesNotConvert() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID other = UUID.randomUUID();
        island(other);
        assertInstanceOf(MemberStore.InviteResult.HasIsland.class, invite(id, leader, other));
        assertInstanceOf(MemberStore.InviteResult.AlreadyMember.class, invite(id, leader, leader));
        assertEquals("solo", type(id), "nothing was sent, the island stays solo");
    }

    @Test
    void acceptJoinsAndClearsAllInvitesOfThePlayer() {
        UUID leaderA = UUID.randomUUID();
        UUID a = island(leaderA);
        UUID leaderB = UUID.randomUUID();
        UUID b = island(leaderB);
        UUID target = UUID.randomUUID();
        invite(a, leaderA, target);
        invite(b, leaderB, target);
        MemberStore.AcceptResult.Joined joined = assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(a, target));
        assertEquals(leaderA, joined.inviter());
        assertEquals(0, (int) db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM island_invites WHERE target_uuid = ?",
                rs -> rs.getInt(1), target).orElse(0)));
        assertInstanceOf(MemberStore.AcceptResult.NoInvite.class, accept(b, target));
        List<Island> loaded = db.blocking(c -> IslandStore.loadAll(c, "islands"));
        Island islandA = loaded.stream().filter(i -> i.id().equals(a)).findFirst().orElseThrow();
        assertEquals(Role.MEMBER, islandA.role(target).orElseThrow());
        assertEquals(now, islandA.joinedAt(target));
        assertEquals(leaderA, islandA.invitedBy(target));
        assertTrue(islandA.team());
    }

    @Test
    void invitesExpire() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID target = UUID.randomUUID();
        invite(id, leader, target);
        long almost = now + MIN5 - 1;
        assertEquals(1, db.blocking(c -> MemberStore.loadInvites(c, almost)).size());
        now += MIN5;
        assertInstanceOf(MemberStore.AcceptResult.NoInvite.class, accept(id, target));
        long at = now;
        assertEquals(0, db.blocking(c -> MemberStore.loadInvites(c, at)).size());
    }

    @Test
    void fourMembersIncludingTheLeader() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        join(id, leader, UUID.randomUUID());
        join(id, leader, UUID.randomUUID());
        join(id, leader, UUID.randomUUID());
        assertEquals(4, MemberRules.slots(null, 4, 8));
        assertEquals(4, assertInstanceOf(MemberStore.InviteResult.Full.class, invite(id, leader, UUID.randomUUID())).slots());
        db.blocking(c -> Sql.update(c, "UPDATE islands SET member_slots = ? WHERE id = ?", 5, id));
        join(id, leader, UUID.randomUUID());
        assertInstanceOf(MemberStore.InviteResult.Full.class, invite(id, leader, UUID.randomUUID()));
    }

    @Test
    void inviteSentBeforeTheIslandFilledUpCantOverfillIt() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID late = UUID.randomUUID();
        invite(id, leader, late);
        join(id, leader, UUID.randomUUID());
        join(id, leader, UUID.randomUUID());
        join(id, leader, UUID.randomUUID());
        assertEquals(4, assertInstanceOf(MemberStore.AcceptResult.Full.class, accept(id, late)).slots());
    }

    @Test
    void joinCooldownAndSeasonLimit() {
        UUID hopper = UUID.randomUUID();
        UUID[] leaders = new UUID[4];
        UUID[] islands = new UUID[4];
        for (int i = 0; i < 4; i++) {
            leaders[i] = UUID.randomUUID();
            islands[i] = island(leaders[i]);
        }
        join(islands[0], leaders[0], hopper);
        leave(islands[0], hopper, MemberStore.LEFT_LEAVE);
        MemberStore.InviteResult limited = invite(islands[1], leaders[1], hopper);
        assertInstanceOf(JoinRules.Check.Cooldown.class, assertInstanceOf(MemberStore.InviteResult.Limited.class, limited).check());
        now += 25 * HOUR;
        join(islands[1], leaders[1], hopper);
        leave(islands[1], hopper, MemberStore.LEFT_KICK);
        now += 25 * HOUR;
        join(islands[2], leaders[2], hopper);
        leave(islands[2], hopper, MemberStore.LEFT_LEAVE);
        now += 25 * HOUR;
        MemberStore.InviteResult fourth = invite(islands[3], leaders[3], hopper);
        assertEquals(3, ((JoinRules.Check.SeasonLimit) assertInstanceOf(MemberStore.InviteResult.Limited.class, fourth).check()).max());
        MemberStore.Limits nextSeason = new MemberStore.Limits(2, 3, 24 * HOUR, 4, 8);
        assertInstanceOf(MemberStore.InviteResult.Created.class,
                db.blockingTransaction(c -> MemberStore.invite(c, islands[3], leaders[3], hopper, now, now + MIN5, nextSeason)),
                "a new season resets the count");
    }

    private Optional<Role> leave(UUID island, UUID player, String reason) {
        return db.blockingTransaction(c -> MemberStore.removeMember(c, island, player, reason, 1, now));
    }

    @Test
    void leaderIsNeverRemovedAndFounderLeaveStartsTheCooldownWithoutCounting() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        assertTrue(leave(id, leader, MemberStore.LEFT_KICK).isEmpty(), "the leader can't be removed");
        UUID heir = UUID.randomUUID();
        join(id, leader, heir);
        assertTrue(db.<Boolean>blockingTransaction(c -> MemberStore.transfer(c, id, leader, heir)));
        assertEquals(Role.MOD, leave(id, leader, MemberStore.LEFT_LEAVE).orElseThrow());
        JoinRules.History history = db.blocking(c -> MemberStore.history(c, leader, 1));
        assertEquals(0, history.joinsThisSeason(), "founding an island is not a join");
        assertEquals(now, history.lastLeave());
        assertInstanceOf(JoinRules.Check.Cooldown.class, JoinRules.check(history, null, now, 3, 24 * HOUR));
    }

    @Test
    void transferSwapsLeaderAndOwner() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID heir = UUID.randomUUID();
        join(id, leader, heir);
        assertFalse(db.<Boolean>blockingTransaction(c -> MemberStore.transfer(c, id, heir, leader)), "only the current owner transfers");
        assertFalse(db.<Boolean>blockingTransaction(c -> MemberStore.transfer(c, id, leader, UUID.randomUUID())), "only to a member");
        assertTrue(db.<Boolean>blockingTransaction(c -> MemberStore.transfer(c, id, leader, heir)));
        Island island = db.blocking(c -> IslandStore.loadAll(c, "islands")).getFirst();
        assertEquals(heir, island.owner());
        assertEquals(Role.LEADER, island.role(heir).orElseThrow());
        assertEquals(Role.MOD, island.role(leader).orElseThrow());
        assertEquals(1, island.members().values().stream().filter(r -> r == Role.LEADER).count());
    }

    @Test
    void setRoleNeedsTheExpectedOldRole() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID member = UUID.randomUUID();
        join(id, leader, member);
        assertTrue(db.<Boolean>blocking(c -> MemberStore.setRole(c, id, member, Role.MEMBER, Role.MOD)));
        assertFalse(db.<Boolean>blocking(c -> MemberStore.setRole(c, id, member, Role.MEMBER, Role.MOD)), "already changed");
        assertTrue(db.<Boolean>blocking(c -> MemberStore.setRole(c, id, member, Role.MOD, Role.MEMBER)));
    }

    @Test
    void joinBlockStopsInvitesAndAccepts() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID target = UUID.randomUUID();
        invite(id, leader, target);
        db.blocking(c -> {
            MemberStore.blockJoining(c, target, now + HOUR, "griefing", "Staff", now);
            return null;
        });
        assertEquals(now + HOUR, db.blocking(c -> MemberStore.blockedUntil(c, target, now)).orElseThrow());
        assertInstanceOf(JoinRules.Check.Blocked.class, assertInstanceOf(MemberStore.AcceptResult.Limited.class, accept(id, target)).check());
        db.blocking(c -> {
            MemberStore.blockJoining(c, target, 0, "", "Staff", now);
            return null;
        });
        assertTrue(db.blocking(c -> MemberStore.blockedUntil(c, target, now)).isEmpty(), "lifted");
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(id, target));
    }

    @Test
    void coopsLoadOnlyForNonMembers() throws SQLException {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID coop = UUID.randomUUID();
        db.blocking(c -> {
            MemberStore.addCoop(c, id, coop, leader, now);
            MemberStore.addCoop(c, id, coop, leader, now + 1); // replaces, no duplicate key
            MemberStore.addCoop(c, id, leader, leader, now);
            return null;
        });
        Map<UUID, Island> islands = new HashMap<>();
        for (Island island : db.blocking(c -> IslandStore.loadAll(c, "islands"))) {
            islands.put(island.id(), island);
        }
        Island island = islands.get(id);
        assertEquals(java.util.Set.of(coop), island.coops(), "a member is never also coop");
        assertEquals(Role.COOP, island.roleOrNull(coop));
        assertEquals(Role.LEADER, island.roleOrNull(leader));
        assertTrue(island.role(coop).isEmpty(), "coop is not a stored member");
        assertEquals(1, island.memberCount(), "coop players take no slot");
    }
}
