package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Team Flame counting: minutes per member and day, one per IP. */
class TeamActivityTest {

    private final UUID island = UUID.randomUUID();

    private int play(TeamActivity t, long day, UUID member, String ip, int minutes) {
        int last = -1;
        for (int i = 0; i < minutes; i++) {
            int r = t.minute(day, island, member, ip, 30);
            if (r > 0) {
                last = r;
            }
        }
        return last;
    }

    @Test
    void membersCountAfterThirtyMinutesOnTheSameDay() {
        TeamActivity t = new TeamActivity();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(-1, play(t, 1, a, "1.1.1.1", 29));
        assertEquals(1, play(t, 1, a, "1.1.1.1", 1));
        assertEquals(-1, play(t, 1, a, "1.1.1.1", 60), "counted once");
        assertEquals(2, play(t, 1, b, "2.2.2.2", 30));
        assertEquals(2, t.activeToday(island));
    }

    @Test
    void altsOnOneIpCountOnce() {
        TeamActivity t = new TeamActivity();
        assertEquals(1, play(t, 1, UUID.randomUUID(), "9.9.9.9", 30));
        assertEquals(-1, play(t, 1, UUID.randomUUID(), "9.9.9.9", 30));
        assertEquals(1, t.activeToday(island));
        assertEquals(2, play(t, 1, UUID.randomUUID(), null, 30), "no address: counted on its own");
    }

    @Test
    void aNewDayStartsAgain() {
        TeamActivity t = new TeamActivity();
        UUID a = UUID.randomUUID();
        play(t, 1, a, "1.1.1.1", 20);
        assertEquals(-1, play(t, 2, a, "1.1.1.1", 10), "yesterday's minutes don't carry over");
        assertEquals(1, play(t, 2, a, "1.1.1.1", 20));
    }
}
