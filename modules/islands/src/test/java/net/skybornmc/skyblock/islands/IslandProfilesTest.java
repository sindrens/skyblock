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
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * PROF on islands: membership per profile key, join limits per player (P11), two own profiles never on the same island,
 * also not one after the other (P3), rows from before profiles count as profile 1, own other profile can't visit (P4).
 */
class IslandProfilesTest {

    private static final long HOUR = 3_600_000L;
    private static final long MIN5 = 5 * 60_000L;
    private static final MemberStore.Limits LIMITS = new MemberStore.Limits(1, 2, 0, 4, 8);

    @TempDir
    Path dir;
    private Database db;
    private final long now = 1_000 * HOUR;
    private final UUID steve = UUID.randomUUID();
    private final UUID steve2 = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "island-profiles-test";
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

    private MemberStore.InviteResult invite(UUID island, UUID leader, UUID key, UUID player) {
        return db.blockingTransaction(c -> MemberStore.invite(c, island, leader, key, player, now, now + MIN5, LIMITS));
    }

    private MemberStore.AcceptResult accept(UUID island, UUID key, UUID player) {
        return db.blockingTransaction(c -> MemberStore.accept(c, island, key, player, now, LIMITS));
    }

    private void leave(UUID island, UUID key, UUID player) {
        db.blockingTransaction(c -> MemberStore.removeMember(c, island, key, player, MemberStore.LEFT_LEAVE, 1, now + 1));
    }

    @Test
    void eachProfileHasItsOwnIsland() {
        UUID one = island(steve);
        UUID two = island(steve2);
        assertEquals(steve, db.blocking(c -> Sql.queryOne(c, "SELECT owner_uuid FROM islands WHERE id = ?", rs -> Sql.uuid(rs, "owner_uuid"), one))
                .orElseThrow());
        assertEquals(steve2, db.blocking(c -> Sql.queryOne(c, "SELECT owner_uuid FROM islands WHERE id = ?", rs -> Sql.uuid(rs, "owner_uuid"), two))
                .orElseThrow());
    }

    @Test
    void joinLimitsArePerPlayerNotPerProfile() {
        UUID leaderA = UUID.randomUUID();
        UUID leaderB = UUID.randomUUID();
        UUID leaderC = UUID.randomUUID();
        UUID a = island(leaderA);
        UUID b = island(leaderB);
        UUID c3 = island(leaderC);
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(a, leaderA, steve, steve));
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(a, steve, steve));
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(b, leaderB, steve2, steve));
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(b, steve2, steve));
        assertEquals(2, db.blocking(c -> MemberStore.history(c, steve, 1)).joinsThisSeason(), "both profiles' joins count for the player");
        // the season limit (2) is reached for the player, whatever profile asks
        UUID steve3 = UUID.randomUUID();
        MemberStore.InviteResult third = invite(c3, leaderC, steve3, steve);
        assertInstanceOf(MemberStore.InviteResult.Limited.class, third);
    }

    @Test
    void twoOwnProfilesNeverOnTheSameIslandEvenOneAfterTheOther() {
        UUID leader = UUID.randomUUID();
        UUID a = island(leader);
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(a, leader, steve2, steve));
        assertInstanceOf(MemberStore.AcceptResult.Joined.class, accept(a, steve2, steve));
        leave(a, steve2, steve);
        // profile 1 (key = player UUID) can't join the island profile 2 was on
        assertInstanceOf(MemberStore.InviteResult.OwnProfile.class, invite(a, leader, steve, steve));
        db.blocking(c -> Sql.update(c, "INSERT INTO island_invites (island_id, target_uuid, inviter_uuid, created_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?)", a, steve, leader, now, now + MIN5));
        assertInstanceOf(MemberStore.AcceptResult.OwnProfile.class, accept(a, steve, steve));
        assertInstanceOf(MemberStore.AddResult.OwnProfile.class,
                db.blockingTransaction(c -> MemberStore.addMember(c, a, steve, steve, UUID.randomUUID(), now, LIMITS)));
        // the same profile may come back
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(a, leader, steve2, steve));
    }

    @Test
    void rowsFromBeforeProfilesBelongToProfileOne() {
        UUID leader = UUID.randomUUID();
        UUID a = island(leader);
        // a join + leave written before profiles: no profile id
        db.blocking(c -> Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, left_at, left_reason, counted) "
                + "VALUES (?, ?, 1, ?, ?, 'leave', TRUE)", steve, a, now - 10, now - 5));
        assertInstanceOf(MemberStore.InviteResult.OwnProfile.class, invite(a, leader, steve2, steve), "profile 2 can't follow profile 1");
        assertInstanceOf(MemberStore.InviteResult.Created.class, invite(a, leader, steve, steve), "profile 1 itself may rejoin");
    }

    @Test
    void aCurrentProfileOneMemberBlocksTheOtherProfiles() {
        UUID leader = UUID.randomUUID();
        UUID a = island(leader);
        db.blocking(c -> Sql.update(c, "INSERT INTO island_coops (island_id, uuid, granted_by, created_at) VALUES (?, ?, ?, ?)",
                a, steve, leader, now));
        assertInstanceOf(MemberStore.InviteResult.OwnProfile.class, invite(a, leader, steve2, steve));
    }

    @Test
    void ownOtherProfileCanNeverVisitEvenWithBypass() {
        assertEquals(VisitRules.Access.OWN_PROFILE, VisitRules.access(null, false, false, true, true));
        assertEquals(VisitRules.Access.OWN_PROFILE, VisitRules.access(null, false, false, false, true));
        assertEquals(false, VisitRules.access(null, false, false, false, true).allowed());
        assertEquals(VisitRules.Access.VISITOR, VisitRules.access(null, false, false, false, false));
        assertEquals(VisitRules.Access.MEMBER, VisitRules.access(net.skybornmc.skyblock.common.island.IslandService.Role.MEMBER,
                false, false, false, false));
    }
}
