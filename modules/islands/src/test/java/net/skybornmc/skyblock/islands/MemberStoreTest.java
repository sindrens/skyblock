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
    private static final MemberStore.Limits LIMITS = new MemberStore.Limits(1, 3, 4, 8);

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
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, "islands", now));
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
    void islandBecomesATeamIslandOnlyWhenTheInviteIsAccepted() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID target = UUID.randomUUID();
        MemberStore.InviteResult.Created sent = assertInstanceOf(MemberStore.InviteResult.Created.class, invite(id, leader, target));
        assertFalse(sent.converted(), "an invite alone never converts (owner 2026-10-30)");
        assertEquals("solo", type(id));
        MemberStore.AcceptResult.Joined joined = assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(id, target));
        assertTrue(joined.converted());
        assertEquals("team", type(id));
        UUID second = UUID.randomUUID();
        invite(id, leader, second);
        MemberStore.AcceptResult.Joined again = assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(id, second));
        assertFalse(again.converted(), "only the first accepted invite converts");
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
    void noJoinCooldownButASeasonLimit() {
        UUID hopper = UUID.randomUUID();
        UUID[] leaders = new UUID[4];
        UUID[] islands = new UUID[4];
        for (int i = 0; i < 4; i++) {
            leaders[i] = UUID.randomUUID();
            islands[i] = island(leaders[i]);
        }
        // Owner answers 121/161/258/259: leave, kick and join again right away, no waiting.
        join(islands[0], leaders[0], hopper);
        leave(islands[0], hopper, MemberStore.LEFT_LEAVE);
        now += 1;
        join(islands[1], leaders[1], hopper);
        leave(islands[1], hopper, MemberStore.LEFT_KICK);
        now += 1;
        join(islands[2], leaders[2], hopper);
        leave(islands[2], hopper, MemberStore.LEFT_LEAVE);
        now += 1;
        MemberStore.InviteResult fourth = invite(islands[3], leaders[3], hopper);
        assertEquals(3, ((JoinRules.Check.SeasonLimit) assertInstanceOf(MemberStore.InviteResult.Limited.class, fourth).check()).max());
        MemberStore.Limits nextSeason = new MemberStore.Limits(2, 3, 4, 8);
        assertInstanceOf(MemberStore.InviteResult.Created.class,
                db.blockingTransaction(c -> MemberStore.invite(c, islands[3], leaders[3], hopper, now, now + MIN5, nextSeason)),
                "a new season resets the count");
    }

    private Optional<Role> leave(UUID island, UUID player, String reason) {
        return db.blockingTransaction(c -> MemberStore.removeMember(c, island, player, reason, 1, now));
    }

    @Test
    void leaderIsNeverRemovedAndFounderLeaveIsNoJoin() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        assertTrue(leave(id, leader, MemberStore.LEFT_KICK).isEmpty(), "the leader can't be removed");
        UUID heir = UUID.randomUUID();
        join(id, leader, heir);
        assertTrue(db.<Boolean>blockingTransaction(c -> MemberStore.transfer(c, id, leader, heir)));
        assertEquals(Role.ADMIN, leave(id, leader, MemberStore.LEFT_LEAVE).orElseThrow(), "ISL2: the old leader is an admin");
        JoinRules.History history = db.blocking(c -> MemberStore.history(c, leader, 1));
        assertEquals(0, history.joinsThisSeason(), "founding an island is not a join");
        assertEquals(now, history.lastLeave());
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(history, null, now, 3));
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
        assertEquals(Role.ADMIN, island.role(leader).orElseThrow(), "ISL2: the old leader becomes an admin");
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

    // ------------------------------------------------------------------ ISL2

    @Test
    void kickedPlayerCanJoinAnotherIslandRightAway() {
        UUID hopper = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID islandA = island(a);
        UUID islandB = island(b);
        join(islandA, a, hopper);
        leave(islandA, hopper, MemberStore.LEFT_KICK);
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(islandB, b, hopper), "no cooldown after a kick (answer 161)");
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(islandB, hopper));
    }

    @Test
    void adminRoleIsStoredAndLoaded() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID member = UUID.randomUUID();
        join(id, leader, member);
        assertTrue(db.<Boolean>blocking(c -> MemberStore.setRole(c, id, member, Role.MEMBER, Role.MOD)));
        assertTrue(db.<Boolean>blocking(c -> MemberStore.setRole(c, id, member, Role.MOD, Role.ADMIN)));
        Island island = db.blocking(c -> IslandStore.loadAll(c, "islands")).getFirst();
        assertEquals(Role.ADMIN, island.role(member).orElseThrow());
        assertEquals("admin", MemberStore.key(Role.ADMIN));
        assertEquals(Role.ADMIN, IslandStore.role("admin"));
    }

    // ------------------------------------------------------------------ owner answer 251: kick backups

    private long backup(UUID island, UUID member, Role role, Long money, byte[] inventory) {
        return db.blocking(c -> KickBackupStore.save(c, new KickBackupStore.NewBackup(island, "Island", member, member, "Alex", role,
                money, inventory, inventory == null ? null : new byte[]{9}, MemberStore.LEFT_KICK, "Steve", now)));
    }

    @Test
    void kickBackupRestoresTheMemberWithTheOldRoleOnce() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID member = UUID.randomUUID();
        join(id, leader, member);
        assertTrue(db.<Boolean>blocking(c -> MemberStore.setRole(c, id, member, Role.MEMBER, Role.MOD)));
        assertEquals(Role.MOD, leave(id, member, MemberStore.LEFT_KICK).orElseThrow());
        long backupId = backup(id, member, Role.MOD, 12_345L, new byte[]{1, 2, 3});

        KickBackupStore.Backup saved = db.blocking(c -> KickBackupStore.ofPlayer(c, member, 10)).getFirst();
        assertEquals(backupId, saved.id());
        assertEquals(12_345L, saved.money());
        assertTrue(saved.open());
        assertTrue(saved.itemsOpen());
        assertEquals(Role.MOD, saved.role());

        now += HOUR;
        KickBackupStore.RestoreResult first = db.blocking(c -> KickBackupStore.restoreMembership(c, backupId, "Admin", now, LIMITS));
        assertInstanceOf(KickBackupStore.RestoreResult.Restored.class, first);
        Island island = db.blocking(c -> IslandStore.loadAll(c, "islands")).getFirst();
        assertEquals(Role.MOD, island.role(member).orElseThrow(), "back with the role they had");
        assertInstanceOf(KickBackupStore.RestoreResult.AlreadyRestored.class,
                db.blocking(c -> KickBackupStore.restoreMembership(c, backupId, "Admin", now, LIMITS)), "only once");
        assertTrue(db.<Boolean>blocking(c -> KickBackupStore.claimItems(c, backupId, now)));
        assertFalse(db.<Boolean>blocking(c -> KickBackupStore.claimItems(c, backupId, now)), "items only once too");
        KickBackupStore.Backup after = db.blocking(c -> KickBackupStore.ofPlayer(c, member, 10)).getFirst();
        assertFalse(after.open());
        assertFalse(after.itemsOpen());
        assertEquals("Admin", after.restoredBy());
    }

    @Test
    void refusedRestoreKeepsTheBackupOpen() {
        UUID leader = UUID.randomUUID();
        UUID id = island(leader);
        UUID member = UUID.randomUUID();
        join(id, leader, member);
        leave(id, member, MemberStore.LEFT_KICK);
        long backupId = backup(id, member, Role.MEMBER, null, null);
        island(member); // they made their own island meanwhile
        KickBackupStore.RestoreResult refused = db.blocking(c -> KickBackupStore.restoreMembership(c, backupId, "Admin", now, LIMITS));
        assertInstanceOf(MemberStore.AddResult.HasIsland.class, assertInstanceOf(KickBackupStore.RestoreResult.Refused.class, refused).reason());
        KickBackupStore.Backup still = db.blocking(c -> KickBackupStore.ofPlayer(c, member, 10)).getFirst();
        assertTrue(still.open(), "nothing claimed: staff can try again");
        assertFalse(still.itemsOpen(), "no items were captured (offline at the kick)");
        assertFalse(db.<Boolean>blocking(c -> KickBackupStore.claimItems(c, backupId, now)));
    }

    @Test
    void onlyKicksOfStoredMembersGetABackup() {
        assertTrue(KickBackups.wanted(MemberStore.LEFT_KICK, Role.MEMBER, ""));
        assertTrue(KickBackups.wanted(MemberStore.LEFT_REMOVED, Role.ADMIN, "griefing"), "staff/moderation removal");
        assertFalse(KickBackups.wanted(MemberStore.LEFT_LEAVE, Role.MEMBER, ""), "leaving is their own choice");
        assertFalse(KickBackups.wanted(MemberStore.LEFT_REMOVED, Role.MEMBER, KickBackups.NOTE_PROFILE_DELETED), "profile gone");
        assertFalse(KickBackups.wanted(MemberStore.LEFT_KICK, Role.LEADER, ""));
        assertFalse(KickBackups.wanted(MemberStore.LEFT_KICK, null, ""));
        assertEquals(500, KickBackups.missingMoney(1_500, 1_000));
        assertEquals(0, KickBackups.missingMoney(1_000, 4_000), "money is never taken away");
        assertEquals(1_000, KickBackups.missingMoney(1_000, -5));
    }
}
