package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static net.skybornmc.skyblock.moderation.TestData.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner answers round 2: appeal wait of permanent bans (423) and lockdown rank exemption (460). */
class OwnerAnswersRound2Test {

    private static final long DAY = 86_400_000L;
    private static final List<String> CHEATING = List.of("cheating");

    @Test
    void permanentBansWaitThirtyDaysCheatingNinety() {
        long created = 100 * DAY;
        Punishment perm = TestData.punishment(1, Punishment.Type.BAN, created, null, null, null);
        assertEquals(created + 30 * DAY, AppealRules.earliestAppeal(perm, "chat", 30, 90, CHEATING));
        assertEquals(created + 90 * DAY, AppealRules.earliestAppeal(perm, "Cheating", 30, 90, CHEATING), "case-insensitive category");
        assertNull(AppealRules.earliestAppeal(perm, "chat", 0, 90, CHEATING), "0 days = any time");
        Punishment temp = TestData.punishment(2, Punishment.Type.BAN, created, created + 7 * DAY, null, null);
        assertNull(AppealRules.earliestAppeal(temp, "cheating", 30, 90, CHEATING), "temporary bans: no wait");
        Punishment mute = TestData.mute(3, created, null, null);
        assertNull(AppealRules.earliestAppeal(mute, "chat", 30, 90, CHEATING), "mutes: no wait");
        assertNull(AppealRules.earliestAppeal(null, "chat", 30, 90, CHEATING));
    }

    @Test
    void appealingTooEarlyIsRefused() {
        long created = 100 * DAY;
        Punishment perm = TestData.punishment(1, Punishment.Type.BAN, created, null, null, null);
        Long earliest = created + 30 * DAY;
        assertEquals(Optional.of(AppealRules.Problem.TOO_EARLY), AppealRules.canAppeal(perm, STEVE, List.of(), true,
                PunishConfig.Appeals.DEFAULT, created + DAY, earliest));
        assertEquals(Optional.empty(), AppealRules.canAppeal(perm, STEVE, List.of(), true, PunishConfig.Appeals.DEFAULT,
                created + 30 * DAY, earliest), "from day 30");
        assertEquals("appeal.problem.too-early", AppealRules.Problem.TOO_EARLY.key());
    }

    @Test
    void everyRankAboveDefaultIsExemptFromLockdown() {
        assertTrue(Lockdown.rankExempt(true, 1), "free rank");
        assertTrue(Lockdown.rankExempt(true, 15), "a donor rank");
        assertFalse(Lockdown.rankExempt(true, 0), "default");
        assertFalse(Lockdown.rankExempt(false, 50), "no LuckPerms: only the bypass node counts");
    }
}
