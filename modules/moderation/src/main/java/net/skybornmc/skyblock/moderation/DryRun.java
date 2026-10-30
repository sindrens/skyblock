package net.skybornmc.skyblock.moderation;

import java.util.Locale;

/**
 * The dry run of spec 14.6, pure: turns a pipeline verdict ({@link PunishDecision}, the same one the real click uses) into
 * what the staff member is told: "would apply: mute 1d, offence #2, probation +1 step, needs approval". Building a
 * report never writes anything; the caller only sends it and logs it as training.
 */
final class DryRun {

    private DryRun() {
    }

    /** The flag of {@code /p <player> <reason> -dry}. */
    static final String FLAG = "-dry";

    enum Outcome { APPLY, REQUEST, BLOCKED, SKIPPED }

    /**
     * What would happen.
     *
     * @param step           the step that would really be applied (probation may have raised it)
     * @param offence        offence number (1 = first; 0 = no ladder: warn buttons, kick)
     * @param probationBonus steps added by probation (0 = none)
     * @param why            reason id for a request or a block (message {@code dry-run.why.<id>}), or {@code null}
     */
    record Report(Outcome outcome, Templates.Step step, int offence, int probationBonus, String why) {

        String outcomeId() {
            return outcome.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The report for {@code verdict}. {@code normal} = the step without probation (weight kept for warns),
     * {@code normalIndex} = its ladder index ({@code -1} when there is no ladder: warn buttons, kick), {@code previous} =
     * earlier counted offences ({@code -1} = no ladder).
     */
    static Report of(PunishDecision.Verdict verdict, Templates.Step normal, int normalIndex, int previous) {
        PunishDecision.Input in = verdict.input();
        Templates.Step step = in == null ? normal
                : new Templates.Step(in.type(), in.type().instant() || in.type() == Punishment.Type.WARN ? null : in.duration(),
                in.ipWide(), in.type() == Punishment.Type.WARN ? normal.weight() : Weights.ONE);
        int bonus = 0;
        if (normalIndex >= 0) {
            int raised = PunishMenus.raisedIndex(verdict, normalIndex);
            bonus = Math.max(0, raised - normalIndex);
            if (in != null && in.escalation() != null && in.escalation().raised()) {
                Templates.Step r = in.escalation().step();
                step = new Templates.Step(r.type(), r.duration(), in.ipWide(), r.weight());
            }
        }
        Outcome outcome = switch (verdict.outcome()) {
            case APPLY -> Outcome.APPLY;
            case REQUEST -> Outcome.REQUEST;
            case BLOCKED -> Outcome.BLOCKED;
            case SKIPPED -> Outcome.SKIPPED;
        };
        return new Report(outcome, step, previous < 0 ? 0 : previous + 1, bonus, why(verdict));
    }

    /** Why it is a request or not applied: {@code trainee}, {@code approval}, {@code limit}, or the blocking stage. */
    static String why(PunishDecision.Verdict v) {
        return switch (v.outcome()) {
            case APPLY -> null;
            case REQUEST -> "training".equals(v.stage()) ? "trainee"
                    : "limit".equals(v.args().getOrDefault("rule", "limit")) ? "limit" : "approval";
            case BLOCKED, SKIPPED -> v.stage();
        };
    }
}
