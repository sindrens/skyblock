package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6 against a real (H2, MySQL mode) database: every new punishment stores server id + scope, the network scope filter on
 * the join/chat checks with {@code enabled: false} and {@code true} (spec 13.12), and /psuggest's dedupe that appends
 * details and never applies anything (13.3).
 */
class P6StoreTest {

    private static final UUID ALEX = TestData.ALEX;

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private final AtomicReference<NetworkScope.Settings> network = new AtomicReference<>(NetworkScope.Settings.DEFAULT);
    private final PunishDecision decision = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT);
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p6-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p6-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p6-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p6-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        store.network(network::get);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static PunishDecision.Actor staff() {
        return new PunishDecision.Actor("Alex", false, Set.of(Punishment.Type.values()), true, true, null, false, ALEX, false, true);
    }

    private Punishment issue(UUID target, Punishment.Type type, Duration length) {
        Punishment draft = new Punishment(0, type, target, "Steve", null, false, "Spam", null, null, ALEX, "Alex", now, null,
                false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(staff(), type, length, false, false, false, false, true, null, null, now);
        PunishmentStore.IssueResult r = store.issue(draft, in, decision, null, Duration.ofHours(24)).join();
        assertTrue(r.verdict().applied(), () -> r.verdict().toString());
        return r.punishment();
    }

    private record Where(String server, String scope) {
    }

    private Where where(long id) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT server_id, scope FROM punishments WHERE id = ?",
                rs -> new Where(rs.getString("server_id"), rs.getString("scope")), id).orElseThrow());
    }

    private void move(long id, String server, String scope) {
        db.blocking(c -> Sql.update(c, "UPDATE punishments SET server_id = ?, scope = ? WHERE id = ?", server, scope, id));
    }

    @Test
    void newRowsStoreServerAndScope() {
        UUID steve = UUID.randomUUID();
        Punishment ban = issue(steve, Punishment.Type.BAN, Duration.ofDays(1));
        assertEquals(new Where("phoenix", "network"), where(ban.id()), "bans: default scope network");
        Punishment warn = issue(UUID.randomUUID(), Punishment.Type.WARN, null);
        assertEquals(new Where("phoenix", "server"), where(warn.id()), "warnings stay local history");
        network.set(new NetworkScope.Settings(false, "lobby", "server"));
        Punishment mute = issue(UUID.randomUUID(), Punishment.Type.MUTE, Duration.ofHours(1));
        assertEquals(new Where("lobby", "server"), where(mute.id()), "settings are read live (reload)");
    }

    @Test
    void filterOffEveryActiveBanCounts() {
        UUID steve = UUID.randomUUID();
        Punishment ban = issue(steve, Punishment.Type.BAN, Duration.ofDays(1));
        move(ban.id(), "lobby", "server");
        assertTrue(store.activeBan(steve, null).join().isPresent(), "one server, filter off: nothing changes");
        assertTrue(store.activeBlocking(Punishment.Type.BAN, steve, null).isPresent(), "login check too");
    }

    @Test
    void filterOnOnlyNetworkOrOwnServerCounts() {
        network.set(new NetworkScope.Settings(true, "phoenix", "network"));
        UUID local = UUID.randomUUID();
        Punishment otherServer = issue(local, Punishment.Type.BAN, Duration.ofDays(1));
        move(otherServer.id(), "lobby", "server");
        assertFalse(store.activeBan(local, null).join().isPresent(), "a server ban from another server does not count here");
        assertFalse(store.activeBlocking(Punishment.Type.BAN, local, null).isPresent());

        UUID net = UUID.randomUUID();
        Punishment networkBan = issue(net, Punishment.Type.BAN, Duration.ofDays(1));
        move(networkBan.id(), "lobby", "network");
        assertTrue(store.activeBan(net, null).join().isPresent(), "network bans count everywhere");

        UUID own = UUID.randomUUID();
        Punishment ownMute = issue(own, Punishment.Type.MUTE, Duration.ofHours(1));
        move(ownMute.id(), "phoenix", "server");
        assertTrue(store.activeMute(own, null).join().isPresent(), "server mutes count where they were given");

        UUID old = UUID.randomUUID();
        Punishment legacy = issue(old, Punishment.Type.MUTE, Duration.ofHours(1));
        move(legacy.id(), null, null);
        assertTrue(store.activeMute(old, null).join().isPresent(), "rows from before P6 count as network");

        network.set(NetworkScope.Settings.DEFAULT);
        assertTrue(store.activeBan(local, null).join().isPresent(), "filter off again: counts");
    }

    @Test
    void psuggestAppendsDetailsAndNeverApplies() {
        UUID steve = UUID.randomUUID();
        Templates.Step ban = Templates.Step.parse("ban 30d");
        Long expires = now + Duration.ofHours(24).toMillis();
        PunishmentStore.ApiSuggestion a = store.suggestApi(steve, "Steve", "hacking", ban, "Hacked client",
                "Grim: Reach vl 10", now, expires, true).join();
        assertFalse(a.refreshed());
        PunishmentStore.ApiSuggestion b = store.suggestApi(steve, "Steve", "hacking", ban, "Hacked client",
                "Grim: Reach vl 20", now + 1000, expires, true).join();
        assertTrue(b.refreshed(), "one pending suggestion per player + reason");
        assertEquals(a.suggestion().id(), b.suggestion().id());
        assertEquals("Grim: Reach vl 10 | Grim: Reach vl 20", b.suggestion().details(), "details appended");
        for (int vl = 30; vl <= 70; vl += 10) {
            store.suggestApi(steve, "Steve", "hacking", ban, "Hacked client", "Grim: Reach vl " + vl, now + vl, expires, true).join();
        }
        Suggestion s = store.apiSuggestions(steve).join().getFirst();
        assertEquals(5, s.details().split(" \\| ").length, "max 5 lines");
        assertTrue(s.details().endsWith("vl 70"), "newest kept");
        assertEquals(Suggestion.Status.PENDING, s.status());
        assertNull(s.punishmentId());
        assertTrue(store.history(steve).join().isEmpty(), "never applies anything (any warn-rules mode, never-auto reasons)");
        assertTrue(store.suggestions(steve).join().isEmpty(), "never feeds the warn rules");
        // the chat-filter path keeps replacing (12.8)
        PunishmentStore.ApiSuggestion spam = store.suggestApi(steve, "Steve", "spam", Templates.Step.parse("mute 30m"), "Spam",
                "3 hits", now, expires).join();
        PunishmentStore.ApiSuggestion spam2 = store.suggestApi(steve, "Steve", "spam", Templates.Step.parse("mute 30m"), "Spam",
                "5 hits", now + 1, expires).join();
        assertEquals(spam.suggestion().id(), spam2.suggestion().id());
        assertEquals("5 hits", spam2.suggestion().details());
    }
}
