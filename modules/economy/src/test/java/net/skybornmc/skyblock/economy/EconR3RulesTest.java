package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.SendGate;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-R3 pure rules: notes per minute (R3-40), /eco approval (R3-6), /eco targets (R3-39), the F1 send gate. */
class EconR3RulesTest {

    private final UUID admin = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    // ------------------------------------------------------------------ R3-40

    @Test
    void twentyNotesAMinuteThenWait() {
        NoteRate rate = new NoteRate();
        long t = 1_000_000;
        for (int i = 0; i < 20; i++) {
            assertEquals(0, rate.tryTake(player, 20, t + i * 100L), "note " + (i + 1));
        }
        long wait = rate.tryTake(player, 20, t + 2_500);
        assertEquals(60_000 - 2_500, wait, "the 21st waits until the first one is a minute old");
        assertEquals(0, rate.tryTake(player, 20, t + 60_000), "a minute after the first note one slot is free again");
        assertTrue(rate.tryTake(player, 20, t + 60_001) > 0, "but only one");
        assertEquals(0, rate.tryTake(UUID.randomUUID(), 20, t), "per player");
        assertEquals(0, rate.tryTake(player, 0, t), "0 = no limit");
    }

    @Test
    void aFailedNoteGivesItsSlotBack() {
        NoteRate rate = new NoteRate();
        for (int i = 0; i < 2; i++) {
            rate.tryTake(player, 2, 10);
        }
        assertTrue(rate.tryTake(player, 2, 20) > 0);
        rate.giveBack(player);
        assertEquals(0, rate.tryTake(player, 2, 30));
    }

    @Test
    void noteCooldownKeyIsRemoved() {
        assertTrue(new EconomyConfig().removedKeys().containsKey("notes.withdraw-cooldown-seconds"));
        assertEquals(20, new EconomyConfig().noteMaxPerMinute);
        assertEquals(90, new EconomyConfig().notePurgeDays);
    }

    // ------------------------------------------------------------------ R3-6

    @Test
    void upToTheLimitAppliesAtOnceAboveItWaits() {
        long limit = new EconomyConfig().ecoApproveAbove(Currency.MONEY);
        assertEquals(1_000_000_000_000L, limit, "owner: $1T");
        assertFalse(EcoApprovals.needsApproval(limit, limit), "exactly $1T applies at once");
        assertTrue(EcoApprovals.needsApproval(limit + 1, limit));
        assertFalse(EcoApprovals.needsApproval(Long.MAX_VALUE, 0), "0 = never wait");
    }

    @Test
    void setAndResetCountTheDistanceToTheNewBalance() {
        assertEquals(500, EcoApprovals.change(EcoAction.GIVE, 500, 9_999));
        assertEquals(500, EcoApprovals.change(EcoAction.TAKE, 500, 9_999));
        assertEquals(5_000_000_000_000L, EcoApprovals.change(EcoAction.SET, 0, 5_000_000_000_000L), "wiping $5T is a $5T change");
        assertEquals(2_000_000_000_000L, EcoApprovals.change(EcoAction.SET, 3_000_000_000_000L, 1_000_000_000_000L));
        assertEquals(0, EcoApprovals.change(EcoAction.RESET, 1_000, 1_000));
    }

    @Test
    void anotherOwnerOrAdminMustAllowIt() {
        EcoApprovals approvals = new EcoApprovals();
        EcoApprovals.Pending p = approvals.add(admin, "Admin", EcoAction.GIVE, player, "Steve", player, Currency.MONEY,
                2_000_000_000_000L, 2_000_000_000_000L, "Event prize", 0, 600_000);
        assertEquals(Optional.of(EcoApprovals.Problem.OWN_REQUEST), EcoApprovals.canDecide(p, admin, true));
        assertEquals(Optional.of(EcoApprovals.Problem.NO_PERMISSION), EcoApprovals.canDecide(p, UUID.randomUUID(), false));
        assertEquals(Optional.empty(), EcoApprovals.canDecide(p, owner, true));
        assertEquals(Optional.empty(), EcoApprovals.canDecide(p, null, false), "the console may decide");
        assertEquals(Optional.of(EcoApprovals.Problem.UNKNOWN), EcoApprovals.canDecide(null, owner, true));
        assertEquals(java.util.List.of(String.valueOf(p.id())), approvals.ids(1));
        assertTrue(approvals.take(p.id(), 1).isPresent(), "first click wins");
        assertTrue(approvals.take(p.id(), 2).isEmpty(), "a second click finds nothing");
    }

    @Test
    void aWaitingChangeEndsAfterTenMinutes() {
        EcoApprovals approvals = new EcoApprovals();
        long minutes = new EconomyConfig().ecoPendingMinutes;
        assertEquals(10, minutes);
        EcoApprovals.Pending p = approvals.add(admin, "Admin", EcoAction.SET, player, "Steve", player, Currency.MONEY, 0, 5, "x",
                1_000, minutes * 60_000L);
        assertTrue(approvals.get(p.id(), 1_000 + 599_999).isPresent());
        assertTrue(approvals.get(p.id(), 1_000 + 600_000).isEmpty(), "expired: can't be allowed any more");
        assertEquals(java.util.List.of(p), approvals.expire(1_000 + 600_000));
        assertTrue(approvals.list(0).isEmpty());
    }

    // ------------------------------------------------------------------ R3-39

    @Test
    void ecoNeverTouchesYourOwnOrASameAddressAccount() {
        assertEquals(EcoTargets.Problem.SELF, EcoTargets.quick(admin, admin, "1.2.3.4", "1.2.3.4"));
        assertEquals(EcoTargets.Problem.SAME_IP, EcoTargets.quick(admin, player, "1.2.3.4", "1.2.3.4"));
        assertNull(EcoTargets.quick(admin, player, "1.2.3.4", "5.6.7.8"));
        assertNull(EcoTargets.quick(admin, player, "1.2.3.4", null), "offline: the IP history decides");
        assertNull(EcoTargets.quick(null, player, null, null), "the console");
    }

    // ------------------------------------------------------------------ F1

    @Test
    void sendGateNeedsTenThousandEarnedExceptToIslandMembers() {
        EconomyConfig cfg = new EconomyConfig();
        assertEquals(10_000, cfg.sendGateRequired);
        SendGate.Check blocked = SendGateImpl.decide(10_000, true, false, false, 2_500);
        assertFalse(blocked.allowed());
        assertEquals(7_500, blocked.missing(), "the message says how much more to earn");
        assertTrue(SendGateImpl.decide(10_000, true, false, false, 10_000).allowed());
        assertTrue(SendGateImpl.decide(10_000, true, true, false, 0).allowed(), "island members: always");
        assertTrue(SendGateImpl.decide(10_000, true, false, true, 0).allowed(), "already unlocked");
        assertTrue(SendGateImpl.decide(0, true, false, false, 0).allowed(), "0 = off");
        assertTrue(SendGateImpl.decide(10_000, false, false, false, 0).allowed(), "currency not covered");
        assertTrue(cfg.sendGated(Currency.MONEY));
        assertTrue(cfg.sendGated(Currency.SHARDS));
        assertFalse(cfg.sendGateEarnedReasons.contains("pay"), "money from players never counts");
        assertFalse(cfg.sendGateEarnedReasons.contains("note_redeem"));
        assertFalse(cfg.sendGateEarnedReasons.contains("admin_give"));
        assertFalse(cfg.sendGateEarnedReasons.contains("start_balance"));
    }
}
