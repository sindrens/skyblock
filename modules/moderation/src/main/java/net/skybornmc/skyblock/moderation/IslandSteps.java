package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Island ladder steps (P7, spec 13.2), pure: which island steps are possible for one target, and what is used instead.
 *
 * <ul>
 *   <li>Island steps need the islands module ({@link Context#available}). Without it every island step falls to the next
 *       possible step of the ladder (spec: "the next available step in the ladder is used, with a staff message").</li>
 *   <li>{@code island-kick}, {@code island-ban} and {@code coop-remove} need an island the target belongs to and does
 *       <b>not</b> own. The owner of an island is never island-banned from it: for an owner these steps are skipped to
 *       the next {@code coop-block} (or the next non-island step); without an island to the next possible step.</li>
 *   <li>{@code coop-block} only needs the islands module.</li>
 *   <li>No possible step later in the ladder: the nearest possible step before it; none at all: a warning.</li>
 * </ul>
 * The ladder keeps its length, so the offence count still picks the same index; only unusable steps are replaced.
 *
 * <p>P9 (spec 14.5): {@code report-block} steps follow the same rule. They need the reports (staff) module's
 * {@code ReportBlocking} service ({@link Context#reports}); without it the step says "not available" and the next possible
 * step is used.</p>
 */
final class IslandSteps {

    private IslandSteps() {
    }

    /** Why an island step (or a report block, {@code NO_REPORTS}) can't be used for this target. */
    enum Why { UNAVAILABLE, OWNER, NO_ISLAND, NO_REPORTS }

    /**
     * What the islands module says about the target (resolved on the main thread before punishing).
     *
     * @param available  the islands module runs and supports the moderation hooks
     * @param islandId   the island of the target's active (offline: last active) profile, or {@code null}
     * @param islandName its shown name ({@code null} without an island)
     * @param owner      the target owns (leads) that island
     * @param reports    report blocks can be enforced (the staff module provides {@code ReportBlocking}, P9)
     */
    record Context(boolean available, UUID islandId, String islandName, boolean owner, boolean reports) {

        /** Without report blocks (before P9, and wherever nothing enforces them). */
        Context(boolean available, UUID islandId, String islandName, boolean owner) {
            this(available, islandId, islandName, owner, false);
        }

        /** No islands module: every island step is replaced (and report blocks too). */
        static final Context UNAVAILABLE = new Context(false, null, null, false);

        /** Islands module running, target without an island. */
        static final Context NO_ISLAND = new Context(true, null, null, false);

        /** Same context with report blocks available or not. */
        Context withReports(boolean value) {
            return new Context(available, islandId, islandName, owner, value);
        }

        boolean hasIsland() {
            return islandId != null;
        }
    }

    /** Why {@code step} can't be used, or {@code null} if it can. Non-island steps can always be used. */
    static Why problem(Templates.Step step, Context ctx) {
        if (step.type() == Punishment.Type.REPORT_BLOCK) {
            return ctx != null && ctx.reports() ? null : Why.NO_REPORTS;
        }
        if (!step.type().island()) {
            return null;
        }
        if (ctx == null || !ctx.available()) {
            return Why.UNAVAILABLE;
        }
        if (step.type() == Punishment.Type.COOP_BLOCK) {
            return null;
        }
        if (!ctx.hasIsland()) {
            return Why.NO_ISLAND;
        }
        return ctx.owner() ? Why.OWNER : null;
    }

    /** The ladder with every unusable island step replaced (same length). */
    static List<Templates.Step> effective(List<Templates.Step> ladder, Context ctx) {
        List<Templates.Step> out = new ArrayList<>(ladder.size());
        for (int i = 0; i < ladder.size(); i++) {
            out.add(replacement(ladder, i, ctx));
        }
        return List.copyOf(out);
    }

    /** {@code template} with its effective ladder for this target (the same object when nothing changes). */
    static Templates.Template adapt(Templates.Template template, Context ctx) {
        if (template == null || template.ladder().stream().noneMatch(s -> s.type().island()
                || s.type() == Punishment.Type.REPORT_BLOCK)) {
            return template;
        }
        List<Templates.Step> ladder = effective(template.ladder(), ctx);
        return ladder.equals(template.ladder()) ? template : template.withLadder(ladder);
    }

    /** The step used at {@code index}: the step itself when usable, else its replacement. */
    static Templates.Step replacement(List<Templates.Step> ladder, int index, Context ctx) {
        Templates.Step step = ladder.get(index);
        Why why = problem(step, ctx);
        if (why == null) {
            return step;
        }
        for (int i = index + 1; i < ladder.size(); i++) {
            Templates.Step later = ladder.get(i);
            if (problem(later, ctx) != null) {
                continue;
            }
            // spec: an owner's island steps are skipped to coop-block; any later non-island step also stops the search
            return later;
        }
        for (int i = index - 1; i >= 0; i--) {
            Templates.Step earlier = ladder.get(i);
            if (problem(earlier, ctx) == null) {
                return earlier;
            }
        }
        return new Templates.Step(Punishment.Type.WARN, null, false);
    }

    /** Why the step at {@code index} was replaced, or {@code null} if it is used as written. */
    static Why replaced(List<Templates.Step> ladder, int index, Context ctx) {
        if (index < 0 || index >= ladder.size()) {
            return null;
        }
        return problem(ladder.get(index), ctx);
    }
}
