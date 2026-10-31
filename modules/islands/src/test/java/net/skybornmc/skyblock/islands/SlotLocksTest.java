package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner F10: member slot locks after a leave or kick (timing and exemptions). */
class SlotLocksTest {

    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;
    private final long now = 10_000 * DAY;

    @Test
    void leaveAndKickLockStaffRemovalsDont() {
        assertTrue(SlotLocks.locks(MemberStore.LEFT_LEAVE, SlotLocks.UNKNOWN, now, 21, 48));
        assertTrue(SlotLocks.locks(MemberStore.LEFT_KICK, now - DAY, now, 21, 48));
        assertFalse(SlotLocks.locks(MemberStore.LEFT_REMOVED, now, now, 21, 48), "staff kicks, bans, deleted profiles");
        assertFalse(SlotLocks.locks(MemberStore.LEFT_DISBAND, now, now, 21, 48));
        assertFalse(SlotLocks.locks(MemberStore.LEFT_LEAVE, now, now, 21, 0), "0 hours = off");
    }

    @Test
    void kickingAMemberInactive21DaysFreesTheSlotAtOnce() {
        assertFalse(SlotLocks.locks(MemberStore.LEFT_KICK, now - 21 * DAY, now, 21, 48), "exactly 21 days away");
        assertFalse(SlotLocks.locks(MemberStore.LEFT_KICK, now - 60 * DAY, now, 21, 48));
        assertTrue(SlotLocks.locks(MemberStore.LEFT_KICK, now - 21 * DAY + 1, now, 21, 48), "one ms short of 21 days");
        assertTrue(SlotLocks.locks(MemberStore.LEFT_KICK, Long.MAX_VALUE, now, 21, 48), "online = active");
        assertTrue(SlotLocks.locks(MemberStore.LEFT_KICK, SlotLocks.UNKNOWN, now, 21, 48), "unknown counts as active (safe side)");
        assertTrue(SlotLocks.locks(MemberStore.LEFT_KICK, now - 90 * DAY, now, 0, 48), "0 days = the exemption is off");
        // A member who leaves by choice always locks, however long they were away.
        assertTrue(SlotLocks.locks(MemberStore.LEFT_LEAVE, now - 90 * DAY, now, 21, 48));
    }

    @Test
    void lockRunsFortyEightHours() {
        long until = SlotLocks.until(now, 48);
        assertEquals(now + 48 * HOUR, until);
        assertEquals(1, SlotLocks.active(List.of(until), until - 1));
        assertEquals(0, SlotLocks.active(List.of(until), until));
    }

    @Test
    void freeSlotsCountLocks() {
        assertEquals(1, SlotLocks.free(5, 3, 1));
        assertEquals(0, SlotLocks.free(5, 4, 1));
        assertEquals(0, SlotLocks.free(5, 3, 4), "never negative");
        assertTrue(SlotLocks.blockedByLocks(5, 4, 1));
        assertFalse(SlotLocks.blockedByLocks(5, 5, 1), "full by members: the normal 'island is full' message");
        assertFalse(SlotLocks.blockedByLocks(5, 3, 1));
    }

    @Test
    void opensAtIsWhenEnoughLocksRanOut() {
        long a = now + 10 * HOUR;
        long b = now + 30 * HOUR;
        long c = now + 40 * HOUR;
        // 5 slots, 3 members, 2 locks: the first lock to run out opens a slot.
        assertEquals(OptionalLong.of(a), SlotLocks.opensAt(List.of(b, a), 5, 3, now));
        // 5 slots, 2 members, 3 locks: also the first one.
        assertEquals(OptionalLong.of(a), SlotLocks.opensAt(List.of(c, a, b), 5, 2, now));
        // 5 slots, 3 members, 3 locks: 2 locks must run out (the second earliest).
        assertEquals(OptionalLong.of(b), SlotLocks.opensAt(List.of(c, a, b), 5, 3, now));
        // Slots lowered by staff: 4 slots, 3 members, 3 locks -> all 3 must run out.
        assertEquals(OptionalLong.of(c), SlotLocks.opensAt(List.of(c, a, b), 4, 3, now));
        // A free slot now, or full by members: nothing to wait for.
        assertEquals(OptionalLong.empty(), SlotLocks.opensAt(List.of(a), 5, 2, now));
        assertEquals(OptionalLong.empty(), SlotLocks.opensAt(List.of(a), 5, 5, now));
        // Expired locks don't count.
        assertEquals(OptionalLong.empty(), SlotLocks.opensAt(List.of(now - 1), 5, 4, now));
    }

    @Test
    void remainingShowsHoursAndMinutes() {
        assertEquals("31h 12m", SlotLocks.remaining(31 * HOUR + 12 * 60_000L));
        assertEquals("48h", SlotLocks.remaining(48 * HOUR));
        assertEquals("45m", SlotLocks.remaining(45 * 60_000L));
        assertEquals("1m", SlotLocks.remaining(5_000L), "under a minute rounds up");
        assertEquals("1m", SlotLocks.remaining(-5L));
        assertEquals("2m", SlotLocks.remaining(60_001L));
    }
}
