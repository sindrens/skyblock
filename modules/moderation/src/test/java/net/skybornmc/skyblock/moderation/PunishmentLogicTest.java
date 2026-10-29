package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentLogicTest {

    @Test
    void stepsParse() {
        assertEquals(Punishment.Type.WARN, Templates.Step.parse("warn").type());
        Templates.Step mute = Templates.Step.parse("mute 1h");
        assertEquals(Punishment.Type.MUTE, mute.type());
        assertEquals(Duration.ofHours(1), mute.duration());
        assertNull(Templates.Step.parse("ban perm").duration());
        assertTrue(Templates.Step.parse("ipban 30d").ipWide());
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("jail 1h"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("ban soon"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("warn 1h"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("mute 1h extra"));
    }

    @Test
    void describeRoundTrips() {
        for (String raw : List.of("warn", "kick", "mute 1h", "mute 30m", "ban 7d", "ban perm", "ipban 1d", "mute 1h30m", "ban 2w")) {
            Templates.Step step = Templates.Step.parse(raw);
            assertEquals(step, Templates.Step.parse(step.describe()), raw);
        }
        assertEquals("mute 1h", Templates.Step.parse("mute 60m").describe());
    }

    @Test
    void ladderRepeatsLastStep() {
        Templates.Template t = new Templates.Template("toxicity", "chat", null, org.bukkit.Material.PAPER,
                List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 1h"), Templates.Step.parse("mute perm")));
        assertEquals(Punishment.Type.WARN, t.stepFor(0).type());
        assertEquals(Duration.ofHours(1), t.stepFor(1).duration());
        assertNull(t.stepFor(2).duration());
        assertNull(t.stepFor(9).duration());
        assertEquals(2, t.stepIndex(9));
    }

    @Test
    void reasonFlags() {
        ModerationModule.Reason r = ModerationModule.Reason.parse("Hacking -s  fly -ip");
        assertEquals("Hacking fly", r.text());
        assertTrue(r.silent());
        assertTrue(r.ipWide());
        assertFalse(ModerationModule.Reason.parse("spam").silent());
        assertEquals("", ModerationModule.Reason.parse("").text());
    }

    @Test
    void flagSuggestions() {
        assertEquals(List.of("-s", "-ip"), Flags.suggestions(""));
        assertEquals(List.of("-ip"), Flags.suggestions("-i"));
        assertEquals(List.of("-s -ip"), Flags.suggestions("-s "));
        assertEquals(List.of("-ip -s"), Flags.suggestions("-ip "));
        assertEquals(List.of(), Flags.suggestions("-s -ip "));
    }

    @Test
    void statusAndActivity() {
        long now = 1_000_000;
        Punishment temp = TestData.punishment(1, Punishment.Type.BAN, now - 10, now + 1000, null, null);
        assertTrue(temp.active(now));
        assertEquals(Punishment.Status.EXPIRED, temp.status(now + 2000));
        Punishment removed = TestData.punishment(2, Punishment.Type.MUTE, now - 10, null, now - 5, null);
        assertFalse(removed.active(now));
        assertEquals(Punishment.Status.REMOVED, removed.status(now));
        Punishment kick = TestData.punishment(3, Punishment.Type.KICK, now - 10, null, null, null);
        assertFalse(kick.active(now));
        assertEquals(Punishment.Status.DONE, kick.status(now));
        assertTrue(TestData.mute(4, now, null, "auto:warn-rule:2").automatic());
        assertFalse(TestData.mute(5, now, null, null).automatic());
    }

    @Test
    void longestActiveWins() {
        long now = System.currentTimeMillis();
        Punishment shortMute = TestData.mute(1, now, now + TestData.HOUR, null);
        Punishment longMute = TestData.mute(2, now, now + TestData.DAY, null);
        Punishment perm = TestData.mute(3, now, null, null);
        assertEquals(longMute, PunishmentStore.longest(List.of(shortMute, longMute)));
        assertEquals(perm, PunishmentStore.longest(List.of(shortMute, perm, longMute)));
        assertEquals(longMute, PunishmentService.longer(longMute, shortMute));
        assertEquals(longMute, PunishmentService.longer(shortMute, longMute));
    }
}
