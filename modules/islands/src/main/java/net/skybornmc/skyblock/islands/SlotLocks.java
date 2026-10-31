package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

/**
 * Owner F10 (REVIEW3): after a member leaves or is kicked, that member slot stays locked for
 * {@code members.slot-lock-hours} (48 h) before the island can invite someone into it. Pure rules, unit-tested; the
 * locks are stored in {@code island_slot_locks} (V016) and cached on {@link Island#slotLocks()}.
 *
 * <ul>
 *   <li>A leave by choice and a kick by the island lock a slot. Leaving costs the island nothing else (contribution
 *       rules come with Phase 5).</li>
 *   <li>Staff removals ({@link MemberStore#LEFT_REMOVED}: {@code /isadmin kick}, punishments, deleted profiles) never
 *       lock, and neither does kicking a member who was not online for {@code members.slot-lock-inactive-days} (21)
 *       days (brief §4.2). An unknown last-seen time counts as active (the safe side: the slot locks).</li>
 *   <li>Locks only limit invites and accepting them; staff adding a member ({@code /isadmin addmember}, restore) is
 *       not limited.</li>
 * </ul>
 */
final class SlotLocks {

    /** Last-seen value for "unknown" (counts as active). */
    static final long UNKNOWN = -1L;

    private SlotLocks() {
    }

    /**
     * True if this removal locks the freed slot.
     *
     * @param reason        {@link MemberStore#LEFT_LEAVE}, {@link MemberStore#LEFT_KICK} or {@link MemberStore#LEFT_REMOVED}
     * @param lastSeen      the removed member's last time online (epoch millis), {@link Long#MAX_VALUE} while online,
     *                      {@link #UNKNOWN} when not known
     * @param inactiveDays  members away at least this many days don't lock when kicked (0 = that exemption is off)
     * @param lockHours     {@code members.slot-lock-hours}; 0 = slot locks are off
     */
    static boolean locks(String reason, long lastSeen, long now, int inactiveDays, int lockHours) {
        if (lockHours <= 0) {
            return false;
        }
        if (MemberStore.LEFT_LEAVE.equals(reason)) {
            return true;
        }
        if (!MemberStore.LEFT_KICK.equals(reason)) {
            return false; // staff removals and removed islands never lock
        }
        return !inactive(lastSeen, now, inactiveDays);
    }

    /** True if {@code lastSeen} is at least {@code inactiveDays} days before {@code now} (unknown or online = active). */
    static boolean inactive(long lastSeen, long now, int inactiveDays) {
        if (inactiveDays <= 0 || lastSeen == UNKNOWN || lastSeen == Long.MAX_VALUE || lastSeen <= 0) {
            return false;
        }
        return now - lastSeen >= inactiveDays * 86_400_000L;
    }

    /** When a lock made at {@code now} runs out. */
    static long until(long now, int lockHours) {
        return now + lockHours * 3_600_000L;
    }

    /** Locks still running at {@code now}. */
    static int active(Collection<Long> untils, long now) {
        int n = 0;
        for (long until : untils) {
            if (until > now) {
                n++;
            }
        }
        return n;
    }

    /** Slots an invite can still use: {@code slots - members - locked}, never negative. */
    static int free(int slots, int members, int locked) {
        return Math.max(0, slots - members - locked);
    }

    /** True when the island has room by members alone but every free slot is locked (the "slot opens again" case). */
    static boolean blockedByLocks(int slots, int members, int locked) {
        return members < slots && free(slots, members, locked) == 0;
    }

    /**
     * When the next slot opens for an invite: the time enough locks have run out for {@code members + locks < slots}.
     * Empty when a slot is free now, or the island is full by members alone (locks don't matter then).
     */
    static OptionalLong opensAt(Collection<Long> untils, int slots, int members, long now) {
        List<Long> running = new ArrayList<>();
        for (long until : untils) {
            if (until > now) {
                running.add(until);
            }
        }
        if (!blockedByLocks(slots, members, running.size())) {
            return OptionalLong.empty();
        }
        running.sort(null);
        // members + running >= slots: (members + running - slots + 1) locks must run out, the earliest first.
        int mustExpire = members + running.size() - slots + 1;
        return OptionalLong.of(running.get(mustExpire - 1));
    }

    /** Remaining time as hours and minutes ("31h 12m", "45m"; under a minute rounds up to "1m"). */
    static String remaining(long millis) {
        long minutes = Math.max(1, (Math.max(0, millis) + 59_999) / 60_000);
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + "m";
        }
        return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
    }
}
