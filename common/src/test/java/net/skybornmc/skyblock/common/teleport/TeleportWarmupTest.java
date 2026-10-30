package net.skybornmc.skyblock.common.teleport;

import net.skybornmc.skyblock.common.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static net.skybornmc.skyblock.common.teleport.TeleportRules.Start;
import static net.skybornmc.skyblock.common.teleport.WarmupState.Cancel;
import static net.skybornmc.skyblock.common.teleport.WarmupState.Cancelled;
import static net.skybornmc.skyblock.common.teleport.WarmupState.Countdown;
import static net.skybornmc.skyblock.common.teleport.WarmupState.Done;
import static net.skybornmc.skyblock.common.teleport.WarmupState.Pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F4: the warmup state machine, start rules (bypass, cooldown, freeze, combat), cooldowns and teleport.yml. */
class TeleportWarmupTest {

    private static final TeleportRules RULES = TeleportRules.DEFAULTS;
    private static final Pos START = new Pos("spawn", 10.5, 64, 10.5);
    private final UUID a = UUID.randomUUID();

    // ------------------------------------------------------------------ state machine

    @Test
    void countsDownEverySecondThenTeleports() {
        WarmupState state = new WarmupState(START, 1_000, 3);
        assertEquals(new Countdown(3), state.tick(START, false, 0, RULES), "first tick right away shows the full time");
        assertEquals(new Countdown(2), state.tick(START, false, 0, RULES));
        assertEquals(new Countdown(1), state.tick(START, false, 0, RULES));
        assertEquals(new Done(), state.tick(START, false, 0, RULES), "after N seconds of countdown it teleports");
        assertEquals(0, state.left());
    }

    @Test
    void zeroOrNegativeSecondsStillCountsOneSecond() {
        WarmupState state = new WarmupState(START, 0, 0);
        assertEquals(new Countdown(1), state.tick(START, false, 0, RULES));
        assertEquals(new Done(), state.tick(START, false, 0, RULES));
    }

    @Test
    void smallMovesAndLookingAroundAreFine() {
        WarmupState state = new WarmupState(START, 0, 3);
        assertEquals(Optional.empty(), state.moved(new Pos("spawn", 11.2, 64, 10.9), RULES), "less than 1 block");
        assertEquals(Optional.empty(), state.moved(new Pos("spawn", 11.5, 64, 10.5), RULES), "exactly 1 block is allowed");
        assertInstanceOf(Countdown.class, state.tick(new Pos("spawn", 10.9, 64.4, 10.1), false, 0, RULES));
    }

    @Test
    void movingAwayCancels() {
        WarmupState state = new WarmupState(START, 0, 3);
        assertEquals(Optional.of(Cancel.MOVED), state.moved(new Pos("spawn", 11.6, 64, 10.5), RULES));
        assertEquals(Optional.of(Cancel.MOVED), state.moved(new Pos("spawn", 10.5, 62.5, 10.5), RULES), "falling counts");
        assertEquals(new Cancelled(Cancel.MOVED), state.tick(new Pos("spawn", 20, 64, 10.5), false, 0, RULES),
                "the per-second check catches moves without a move event (vehicles, pushes)");
    }

    @Test
    void moveThresholdIsConfigurableAndMoveCancelCanBeOff() {
        WarmupState state = new WarmupState(START, 0, 3);
        TeleportRules wide = new TeleportRules(true, 3.0, true, true, false);
        assertEquals(Optional.empty(), state.moved(new Pos("spawn", 12.5, 64, 10.5), wide));
        assertEquals(Optional.of(Cancel.MOVED), state.moved(new Pos("spawn", 14, 64, 10.5), wide));
        TeleportRules off = new TeleportRules(false, 1.0, true, true, false);
        assertEquals(Optional.empty(), state.moved(new Pos("spawn", 100, 64, 10.5), off));
    }

    @Test
    void anotherWorldAlwaysCancelsEvenWithMoveCancelOff() {
        WarmupState state = new WarmupState(START, 0, 3);
        TeleportRules off = new TeleportRules(false, 1.0, false, false, false);
        assertEquals(Optional.of(Cancel.WORLD), state.moved(new Pos("islands", 10.5, 64, 10.5), off));
        assertEquals(new Cancelled(Cancel.WORLD), state.tick(new Pos("islands", 10.5, 64, 10.5), false, 0, off));
    }

    @Test
    void damageCancelsUnlessTurnedOff() {
        WarmupState state = new WarmupState(START, 0, 3);
        assertEquals(Optional.of(Cancel.DAMAGE), state.damaged(RULES));
        assertEquals(Optional.empty(), state.damaged(new TeleportRules(true, 1.0, false, true, false)));
    }

    @Test
    void onlyANewCombatTagCancels() {
        WarmupState state = new WarmupState(START, 5_000, 3);
        assertInstanceOf(Countdown.class, state.tick(START, false, 4_000, RULES), "a tag from before the start is not new");
        assertInstanceOf(Countdown.class, state.tick(START, false, 5_000, RULES), "same millisecond as the start: not new");
        assertEquals(new Cancelled(Cancel.COMBAT), state.tick(START, false, 6_500, RULES));
        WarmupState relaxed = new WarmupState(START, 5_000, 3);
        assertInstanceOf(Countdown.class, relaxed.tick(START, false, 6_500, new TeleportRules(true, 1.0, true, false, false)),
                "cancel.on-combat off");
    }

    @Test
    void freezeCancelsFirst() {
        WarmupState state = new WarmupState(START, 0, 3);
        assertEquals(new Cancelled(Cancel.FROZEN), state.tick(new Pos("islands", 0, 0, 0), true, 99_999, RULES),
                "frozen wins over every other reason");
    }

    @Test
    void lastSecondChecksBeforeTeleporting() {
        WarmupState state = new WarmupState(START, 0, 1);
        assertEquals(new Countdown(1), state.tick(START, false, 0, RULES));
        assertEquals(new Cancelled(Cancel.MOVED), state.tick(new Pos("spawn", 30, 64, 10.5), false, 0, RULES),
                "moving in the last second still cancels instead of teleporting");
    }

    // ------------------------------------------------------------------ start rules

    @Test
    void startRules() {
        assertEquals(Start.WARMUP, RULES.start(3, false, 0, false, 0));
        assertEquals(Start.INSTANT, RULES.start(0, false, 0, false, 0), "0 seconds = instant");
        assertEquals(Start.INSTANT, RULES.start(5, true, 0, false, 0), "bypass skips the warmup");
        assertEquals(Start.COOLDOWN, RULES.start(3, false, 2_000, false, 0));
        assertEquals(Start.COOLDOWN, RULES.start(0, false, 2_000, false, 0), "cooldown applies to instant teleports too");
        assertEquals(Start.INSTANT, RULES.start(3, true, 2_000, false, 0), "bypass skips the cooldown");
        assertEquals(Start.FROZEN, RULES.start(0, true, 0, true, 0), "nobody teleports themselves while frozen");
        assertEquals(Start.WARMUP, RULES.start(3, false, 0, false, 5_000), "combat block is off by default");
    }

    @Test
    void combatBlockWhenEnabled() {
        TeleportRules strict = new TeleportRules(true, 1.0, true, true, true);
        assertEquals(Start.IN_COMBAT, strict.start(3, false, 0, false, 5_000));
        assertEquals(Start.INSTANT, strict.start(3, true, 0, false, 5_000), "bypass skips the combat block");
        assertEquals(Start.INSTANT, strict.start(0, false, 0, false, 5_000), "instant teleports (no warmup) are not blocked");
        assertEquals(Start.WARMUP, strict.start(3, false, 0, false, 0));
    }

    // ------------------------------------------------------------------ cooldowns

    @Test
    void cooldownsPerReason() {
        AtomicLong now = new AtomicLong(10_000);
        TeleportCooldowns cooldowns = new TeleportCooldowns(now::get);
        assertEquals(0, cooldowns.left(a, TeleportReason.SPAWN));
        cooldowns.start(a, TeleportReason.SPAWN, 5);
        assertEquals(5_000, cooldowns.left(a, TeleportReason.SPAWN));
        assertEquals(0, cooldowns.left(a, TeleportReason.TPA), "other reasons are independent");
        now.set(13_000);
        assertEquals(2_000, cooldowns.left(a, TeleportReason.SPAWN));
        now.set(15_000);
        assertEquals(0, cooldowns.left(a, TeleportReason.SPAWN));
    }

    @Test
    void cooldownClearZeroAndPrune() {
        AtomicLong now = new AtomicLong(0);
        TeleportCooldowns cooldowns = new TeleportCooldowns(now::get);
        cooldowns.start(a, TeleportReason.DUNGEON, 10);
        cooldowns.clear(a, TeleportReason.DUNGEON);
        assertEquals(0, cooldowns.left(a, TeleportReason.DUNGEON), "a cancelled warmup clears its cooldown");
        cooldowns.start(a, TeleportReason.SPAWN, 10);
        cooldowns.start(a, TeleportReason.SPAWN, 0);
        assertEquals(0, cooldowns.left(a, TeleportReason.SPAWN), "0 seconds clears");
        cooldowns.start(a, TeleportReason.SPAWN, 10);
        cooldowns.prune(a);
        assertEquals(1, cooldowns.size(), "running cooldowns survive a relog");
        now.set(10_000);
        cooldowns.prune(a);
        assertEquals(0, cooldowns.size(), "expired ones are dropped");
    }

    // ------------------------------------------------------------------ teleport.yml

    @Test
    void defaultsKeepTheOldBehaviour() {
        TeleportConfig config = new TeleportConfig();
        assertEquals(0, config.warmupSeconds(TeleportReason.SPAWN), "/spawn instant (owner decision)");
        assertEquals(5, config.cooldownSeconds(TeleportReason.SPAWN));
        assertEquals(3, config.warmupSeconds(TeleportReason.TPA));
        assertEquals(0, config.warmupSeconds(TeleportReason.ISLAND_HOME));
        assertEquals(3, config.warmupSeconds(TeleportReason.ISLAND_VISIT));
        assertEquals(3, config.warmupSeconds(TeleportReason.ISLAND_WARP));
        assertEquals(3, config.warmupSeconds(TeleportReason.DUNGEON));
        assertEquals(0, config.warmupSeconds(TeleportReason.PROFILE), "profile switch teleports are instant");
        assertEquals(0, config.warmupSeconds(TeleportReason.STAFF));
        assertEquals(0, config.warmupSeconds(TeleportReason.PORTAL), "portals are instant (combat delay is the portal's rule)");
        assertEquals(TeleportRules.DEFAULTS, config.rules());
    }

    @Test
    void bypassPermissionsPerReason() {
        assertEquals("skyblock.depths.bypass", TeleportReason.DUNGEON.bypassPermission(), "dungeon keeps the Depths bypass");
        for (TeleportReason reason : TeleportReason.values()) {
            if (reason != TeleportReason.DUNGEON) {
                assertEquals(TeleportReason.STAFF_BYPASS, reason.bypassPermission(), reason.name());
            }
            assertTrue(reason.id().matches("[a-z-]+"), "config section id " + reason.id());
        }
    }

    @Test
    void configFileHasEverySectionAndLoadsClean(@TempDir Path dir) throws Exception {
        ConfigManager manager = new ConfigManager(dir.toFile(), Logger.getLogger("test"));
        TeleportConfig config = new TeleportConfig();
        assertTrue(manager.load(config).isEmpty(), "no problems with the defaults");
        String yaml = Files.readString(dir.resolve("teleport.yml"));
        for (TeleportReason reason : TeleportReason.values()) {
            if (reason == TeleportReason.PROFILE || reason == TeleportReason.PORTAL || reason == TeleportReason.STAFF) {
                assertFalse(yaml.contains("\n" + reason.id() + ":"), "always instant, no section: " + reason.id());
                continue;
            }
            assertTrue(yaml.contains("\n" + reason.id() + ":"), "section " + reason.id());
        }
        assertTrue(yaml.contains("move-distance: 1.0"));
    }
}
