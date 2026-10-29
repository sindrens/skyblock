package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static net.skybornmc.skyblock.moderation.TestData.DAY;
import static net.skybornmc.skyblock.moderation.TestData.HOUR;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The single decision pipeline as one table of cases (PUNISH_SPEC_NOTES "Critique / risks"). */
class PunishDecisionTest {

    private static final long NOW = 10_000L * DAY;
    private final PunishDecision pipeline = PunishDecision.standard();

    private record Case(String name, PunishDecision.Input input, PunishDecision.Outcome outcome, String stage, String key) {
    }

    private static PunishDecision.Input in(PunishDecision.Actor actor, Punishment.Type type, Duration duration) {
        return new PunishDecision.Input(actor, type, duration, false, false, false, false, true, null, null, NOW);
    }

    private static Punishment activeMute(Long expires) {
        return TestData.mute(7, NOW - HOUR, expires, null);
    }

    @Test
    void table() {
        PunishDecision.Actor helper = TestData.staff(Duration.ofDays(1));
        PunishDecision.Actor admin = TestData.staff(null);
        PunishDecision.Actor noBan = new PunishDecision.Actor("Helper", false, Set.of(Punishment.Type.WARN, Punishment.Type.MUTE),
                false, false, Duration.ofDays(1), false);
        PunishDecision.Actor bypass = new PunishDecision.Actor("Owner", false, Set.of(Punishment.Type.values()), true, true, null, true);
        PunishDecision.Actor system = PunishDecision.Actor.system("System (auto)");
        PunishDecision.Input mute1h = in(helper, Punishment.Type.MUTE, Duration.ofHours(1));
        List<Case> cases = List.of(
                new Case("plain mute", mute1h, PunishDecision.Outcome.APPLY, "apply", null),
                new Case("plain warn", in(helper, Punishment.Type.WARN, null), PunishDecision.Outcome.APPLY, "apply", null),
                new Case("self", new PunishDecision.Input(helper, Punishment.Type.WARN, null, false, false, false, true, true, null, null, NOW),
                        PunishDecision.Outcome.BLOCKED, "self", "error.self"),
                new Case("exempt target", new PunishDecision.Input(helper, Punishment.Type.MUTE, Duration.ofHours(1), false, false, true,
                        false, true, null, null, NOW), PunishDecision.Outcome.BLOCKED, "exempt", "error.exempt"),
                new Case("exempt target, bypass", new PunishDecision.Input(bypass, Punishment.Type.MUTE, Duration.ofHours(1), false, false,
                        true, false, true, null, null, NOW), PunishDecision.Outcome.APPLY, "apply", null),
                new Case("exempt target, automatic rule", new PunishDecision.Input(system, Punishment.Type.MUTE, Duration.ofHours(1), false,
                        false, true, false, false, null, null, NOW), PunishDecision.Outcome.BLOCKED, "exempt", "error.exempt"),
                new Case("kick offline", new PunishDecision.Input(helper, Punishment.Type.KICK, null, false, false, false, false, false,
                        null, null, NOW), PunishDecision.Outcome.BLOCKED, "offline", "error.kick-offline"),
                new Case("no ban permission", in(noBan, Punishment.Type.BAN, Duration.ofHours(1)), PunishDecision.Outcome.BLOCKED,
                        "permission", "error.no-permission-type"),
                new Case("no silent permission", new PunishDecision.Input(noBan, Punishment.Type.WARN, null, false, true, false, false,
                        true, null, null, NOW), PunishDecision.Outcome.BLOCKED, "permission", "error.no-silent"),
                new Case("no ip permission", new PunishDecision.Input(noBan, Punishment.Type.MUTE, Duration.ofHours(1), true, false,
                        false, false, true, null, null, NOW), PunishDecision.Outcome.BLOCKED, "permission", "error.no-ip"),
                new Case("too long for rank", in(helper, Punishment.Type.BAN, Duration.ofDays(7)), PunishDecision.Outcome.BLOCKED,
                        "length-limit", "error.too-long"),
                new Case("perm for limited rank", in(helper, Punishment.Type.MUTE, null), PunishDecision.Outcome.BLOCKED,
                        "length-limit", "error.too-long"),
                new Case("perm for admin", in(admin, Punishment.Type.BAN, null), PunishDecision.Outcome.APPLY, "apply", null),
                new Case("system has no limit", in(system, Punishment.Type.BAN, Duration.ofDays(30)), PunishDecision.Outcome.APPLY,
                        "apply", null),
                new Case("already muted longer", mute1h.withActive(activeMute(NOW + DAY), null), PunishDecision.Outcome.SKIPPED,
                        "never-shorten", "error.already-muted"),
                new Case("already muted perm", mute1h.withActive(activeMute(null), null), PunishDecision.Outcome.SKIPPED,
                        "never-shorten", "error.already-muted"),
                new Case("muted shorter: extend", in(helper, Punishment.Type.MUTE, Duration.ofDays(1)).withActive(activeMute(NOW + HOUR),
                        null), PunishDecision.Outcome.APPLY, "apply", null),
                new Case("perm mute over temp", in(admin, Punishment.Type.MUTE, null).withActive(activeMute(NOW + DAY), null),
                        PunishDecision.Outcome.APPLY, "apply", null),
                new Case("expired mute is ignored", mute1h.withActive(activeMute(NOW - 1), null), PunishDecision.Outcome.APPLY,
                        "apply", null),
                new Case("already banned longer", in(admin, Punishment.Type.BAN, Duration.ofDays(1))
                        .withActive(null, TestData.punishment(8, Punishment.Type.BAN, NOW - HOUR, null, null, null)),
                        PunishDecision.Outcome.SKIPPED, "never-shorten", "error.already-banned"),
                new Case("ip ban adds coverage", new PunishDecision.Input(admin, Punishment.Type.BAN, Duration.ofDays(1), true, false,
                        false, false, false, null, TestData.punishment(8, Punishment.Type.BAN, NOW - HOUR, null, null, null), NOW),
                        PunishDecision.Outcome.APPLY, "apply", null),
                new Case("warn while muted", in(helper, Punishment.Type.WARN, null).withActive(activeMute(null), null),
                        PunishDecision.Outcome.APPLY, "apply", null),
                new Case("exempt beats length limit", new PunishDecision.Input(helper, Punishment.Type.BAN, null, false, false, true,
                        false, true, null, null, NOW), PunishDecision.Outcome.BLOCKED, "exempt", "error.exempt"));
        for (Case c : cases) {
            PunishDecision.Verdict v = pipeline.decide(c.input());
            assertEquals(c.outcome(), v.outcome(), c.name());
            assertEquals(c.stage(), v.stage(), c.name());
            assertEquals(c.key(), v.key(), c.name());
        }
    }

    @Test
    void verdictArgumentsForMessages() {
        PunishDecision.Verdict tooLong = pipeline.decide(in(TestData.staff(Duration.ofDays(1)), Punishment.Type.BAN, Duration.ofDays(7)));
        assertEquals("1d", tooLong.args().get("max"));
        PunishDecision.Verdict already = pipeline.decide(in(TestData.staff(null), Punishment.Type.MUTE, Duration.ofHours(1))
                .withActive(activeMute(NOW + 3 * DAY), null));
        assertEquals("3d", already.args().get("left"));
        assertEquals("7", already.args().get("id"));
    }

    @Test
    void stagesRunInSlotOrderAndCanBeExtended() {
        assertEquals(List.of(PunishDecision.Slot.EXEMPT, PunishDecision.Slot.PERMISSION, PunishDecision.Slot.LENGTH_LIMIT,
                PunishDecision.Slot.NEVER_SHORTEN), pipeline.slots());
        // A later milestone (e.g. probation) can change the step before it is applied, without a second code path.
        PunishDecision withProbation = pipeline.with(PunishDecision.Slot.PROBATION,
                in -> new PunishDecision.Next(in.withStep(in.type(), Duration.ofDays(1), in.ipWide())));
        PunishDecision.Verdict v = withProbation.decide(in(TestData.staff(null), Punishment.Type.MUTE, Duration.ofHours(1)));
        assertEquals(Duration.ofDays(1), v.input().duration());
        // A duplicate check that stops runs before never-shorten.
        PunishDecision withDuplicate = pipeline.with(PunishDecision.Slot.DUPLICATE, in -> new PunishDecision.Stop(
                new PunishDecision.Verdict(PunishDecision.Outcome.BLOCKED, "duplicate", "error.duplicate", java.util.Map.of(), in)));
        assertEquals("duplicate", withDuplicate.decide(in(TestData.staff(null), Punishment.Type.MUTE, Duration.ofHours(1))
                .withActive(activeMute(null), null)).stage());
        assertEquals(List.of(PunishDecision.Slot.EXEMPT, PunishDecision.Slot.PERMISSION, PunishDecision.Slot.LENGTH_LIMIT,
                PunishDecision.Slot.DUPLICATE, PunishDecision.Slot.NEVER_SHORTEN), withDuplicate.slots());
    }
}
