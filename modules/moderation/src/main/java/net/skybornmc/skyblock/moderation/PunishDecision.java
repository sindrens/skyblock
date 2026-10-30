package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single decision pipeline every punishment goes through: the {@code /p} menu, {@code /p <player> <reason>}, the
 * direct commands ({@code /tempmute} ...), [Apply] on a suggestion and the automatic rules. Nothing else may decide
 * whether a punishment is applied, so these paths can never drift apart (PUNISH_SPEC_NOTES "Critique / risks").
 *
 * <p>Stages run in the order of {@link Slot}. Each stage either lets the request continue (possibly changed, e.g.
 * probation will raise the step) or stops it with a {@link Verdict}. P1 fills {@link Slot#EXEMPT}, {@link Slot#PERMISSION},
 * {@link Slot#LENGTH_LIMIT} and {@link Slot#NEVER_SHORTEN}; P2 fills {@link Slot#DUPLICATE} ({@link #withDuplicates});
 * later milestones plug into the empty slots with
 * {@link #with(Slot, Stage)} instead of adding a second code path:</p>
 * <ul>
 *   <li>{@link Slot#GUARD}: P9, spec 14.1 (rate limits per staff member, locks)</li>
 *   <li>{@link Slot#APPROVAL}: P4/P8, spec 13.4 + 14.6 (requests for big punishments and trainees). Runs before the
 *       length limit, which stays the hard maximum even for approvers.</li>
 *   <li>{@link Slot#DUPLICATE}: P2, spec 12.2 (same reason by another staff member within 15 s)</li>
 *   <li>{@link Slot#PROBATION}: P4, spec 13.7 (step +1)</li>
 * </ul>
 * Pure: inputs are snapshots (permissions read on the main thread, active punishments read inside the database
 * transaction), so the whole table of cases is unit-tested.
 */
final class PunishDecision {

    /** Pipeline positions, in execution order. */
    enum Slot { EXEMPT, GUARD, PERMISSION, APPROVAL, LENGTH_LIMIT, DUPLICATE, NEVER_SHORTEN, PROBATION }

    enum Outcome {
        /** Apply it. */
        APPLY,
        /** Refused for the actor (no permission, too long, exempt target, ...); nothing changes. */
        BLOCKED,
        /** Not needed: the target is already punished for at least as long (never shorten). */
        SKIPPED
    }

    /**
     * Who is punishing, resolved on the main thread. {@code maxDuration == null} = no length limit. {@code id} = the
     * staff member's uuid ({@code null} for console and the system), {@code force} = may use {@code -force} (P2, 12.2).
     */
    record Actor(String name, boolean system, Set<Punishment.Type> types, boolean ipBan, boolean silent,
                 Duration maxDuration, boolean bypassExempt, java.util.UUID id, boolean force) {

        Actor(String name, boolean system, Set<Punishment.Type> types, boolean ipBan, boolean silent, Duration maxDuration,
              boolean bypassExempt) {
            this(name, system, types, ipBan, silent, maxDuration, bypassExempt, null, false);
        }

        /** The automatic rules ("System (auto)"): all types, no limit, but never past the exempt permission. */
        static Actor system(String name) {
            return new Actor(name, true, Set.of(Punishment.Type.values()), true, true, null, false);
        }
    }

    /**
     * One request.
     *
     * @param duration     {@code null} = permanent (ban/mute) or n/a (warn/kick)
     * @param targetOnline needed for kicks
     * @param activeMute   the target's longest active mute, or {@code null}
     * @param activeBan    the target's longest active ban, or {@code null}
     * @param reasonKey    the reason id / warn-button id (the stored {@code template}); {@code null} = no duplicate check
     *                     (direct commands, [Apply], automatic rules)
     * @param force        {@code -force} was given (needs {@link Actor#force()})
     * @param confirmed    the actor clicked again within {@code duplicate-confirm} after being blocked
     * @param recent       the target's newest not-pardoned punishment for {@code reasonKey}, read inside the transaction
     */
    record Input(Actor actor, Punishment.Type type, Duration duration, boolean ipWide, boolean silent, boolean targetExempt,
                 boolean self, boolean targetOnline, Punishment activeMute, Punishment activeBan, long now,
                 String reasonKey, boolean force, boolean confirmed, Punishment recent) {

        Input(Actor actor, Punishment.Type type, Duration duration, boolean ipWide, boolean silent, boolean targetExempt,
              boolean self, boolean targetOnline, Punishment activeMute, Punishment activeBan, long now) {
            this(actor, type, duration, ipWide, silent, targetExempt, self, targetOnline, activeMute, activeBan, now, null,
                    false, false, null);
        }

        Input withActive(Punishment mute, Punishment ban) {
            return new Input(actor, type, duration, ipWide, silent, targetExempt, self, targetOnline, mute, ban, now,
                    reasonKey, force, confirmed, recent);
        }

        Input withStep(Punishment.Type newType, Duration newDuration, boolean newIpWide) {
            return new Input(actor, newType, newDuration, newIpWide, silent, targetExempt, self, targetOnline, activeMute,
                    activeBan, now, reasonKey, force, confirmed, recent);
        }

        /** The duplicate-check fields (spec 12.2). */
        Input withReason(String key, boolean forceFlag, boolean confirmedClick) {
            return new Input(actor, type, duration, ipWide, silent, targetExempt, self, targetOnline, activeMute, activeBan,
                    now, key, forceFlag, confirmedClick, recent);
        }

        Input withRecent(Punishment newest) {
            return new Input(actor, type, duration, ipWide, silent, targetExempt, self, targetOnline, activeMute, activeBan,
                    now, reasonKey, force, confirmed, newest);
        }

        /** When the new punishment would end ({@code null} = never / n/a). */
        Long endsAt() {
            return duration == null ? null : now + duration.toMillis();
        }
    }

    /**
     * The decision. {@code key} is a message key in {@code messages/moderation.yml} for the actor (for BLOCKED/SKIPPED),
     * {@code stage} the stage that stopped it ({@code apply} when applied), {@code input} the (possibly changed) request.
     */
    record Verdict(Outcome outcome, String stage, String key, Map<String, String> args, Input input) {

        static Verdict apply(Input in) {
            return new Verdict(Outcome.APPLY, "apply", null, Map.of(), in);
        }

        boolean applied() {
            return outcome == Outcome.APPLY;
        }
    }

    /** Continue (maybe with a changed input) or stop. */
    sealed interface Result permits Next, Stop {
    }

    record Next(Input input) implements Result {
    }

    record Stop(Verdict verdict) implements Result {
    }

    @FunctionalInterface
    interface Stage {
        Result check(Input in);
    }

    private final Map<Slot, Stage> stages;

    private PunishDecision(Map<Slot, Stage> stages) {
        this.stages = Collections.unmodifiableMap(new EnumMap<>(stages));
    }

    /** The P1 pipeline: exempt → permission → length limit → never shorten → apply. */
    static PunishDecision standard() {
        return p1();
    }

    /** P2: the P1 pipeline plus the duplicate check (spec 12.2) with this window ({@code null}/zero = off). */
    static PunishDecision withDuplicates(Duration window) {
        return p1().with(Slot.DUPLICATE, duplicate(window));
    }

    private static PunishDecision p1() {
        Map<Slot, Stage> map = new EnumMap<>(Slot.class);
        map.put(Slot.EXEMPT, PunishDecision::exempt);
        map.put(Slot.PERMISSION, PunishDecision::permission);
        map.put(Slot.LENGTH_LIMIT, PunishDecision::lengthLimit);
        map.put(Slot.NEVER_SHORTEN, PunishDecision::neverShorten);
        return new PunishDecision(map);
    }

    /** A copy with {@code stage} in {@code slot} (extension point for P2+). */
    PunishDecision with(Slot slot, Stage stage) {
        Map<Slot, Stage> map = new EnumMap<>(Slot.class);
        map.putAll(stages);
        map.put(slot, stage);
        return new PunishDecision(map);
    }

    /** The filled slots in execution order (for tests and /skyblock diagnostics). */
    List<Slot> slots() {
        return new ArrayList<>(new EnumMap<>(stages).keySet());
    }

    Verdict decide(Input in) {
        Input current = in;
        for (Slot slot : Slot.values()) {
            Stage stage = stages.get(slot);
            if (stage == null) {
                continue;
            }
            Result r = stage.check(current);
            if (r instanceof Stop stop) {
                return stop.verdict();
            }
            current = ((Next) r).input();
        }
        return Verdict.apply(current);
    }

    // ------------------------------------------------------------------ stages

    private static Stop stop(Outcome outcome, String stage, String key, Input in, String... kv) {
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            args.put(kv[i], kv[i + 1]);
        }
        return new Stop(new Verdict(outcome, stage, key, Map.copyOf(args), in));
    }

    /** Target checks: never yourself, never an exempt player (unless bypass), kicks only for online players. */
    static Result exempt(Input in) {
        if (in.self() && !in.actor().system()) {
            return stop(Outcome.BLOCKED, "self", "error.self", in);
        }
        if (in.targetExempt() && !in.actor().bypassExempt()) {
            return stop(Outcome.BLOCKED, "exempt", "error.exempt", in);
        }
        if (in.type() == Punishment.Type.KICK && !in.targetOnline()) {
            return stop(Outcome.BLOCKED, "offline", "error.kick-offline", in);
        }
        return new Next(in);
    }

    /** The actor may use this kind of punishment and its flags. */
    static Result permission(Input in) {
        Actor a = in.actor();
        if (!a.types().contains(in.type()) || (in.ipWide() && !a.ipBan())) {
            return stop(Outcome.BLOCKED, "permission", in.ipWide() ? "error.no-ip" : "error.no-permission-type", in,
                    "type", in.type().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (in.silent() && !a.silent()) {
            return stop(Outcome.BLOCKED, "permission", "error.no-silent", in);
        }
        if (in.force() && !a.force() && !a.system()) {
            return stop(Outcome.BLOCKED, "permission", "error.no-force", in);
        }
        return new Next(in);
    }

    /** Length limit per rank (moderation.yml → limits). Bans and mutes only. */
    static Result lengthLimit(Input in) {
        if (in.type() != Punishment.Type.BAN && in.type() != Punishment.Type.MUTE) {
            return new Next(in);
        }
        Duration max = in.actor().maxDuration();
        if (max == null) {
            return new Next(in);
        }
        if (in.duration() == null || in.duration().compareTo(max) > 0) {
            return stop(Outcome.BLOCKED, "length-limit", "error.too-long", in, "max", PunishConfig.text(max));
        }
        return new Next(in);
    }

    /**
     * Double-punish protection (spec 12.2): the same target got a punishment for the same reason within {@code window}
     * (by anyone, also yourself: a double click or a repeated command is the same mistake). Blocked unless {@code -force}
     * or a second click within {@code duplicate-confirm}. {@code recent} is read in the issue transaction under the
     * target's lock, so two clicks in the same tick can never both pass.
     */
    static Stage duplicate(Duration window) {
        return in -> {
            if (window == null || window.isZero() || window.isNegative() || in.reasonKey() == null || in.actor().system()) {
                return new Next(in);
            }
            Punishment r = in.recent();
            if (r == null || r.removed() || r.template() == null || !r.template().equalsIgnoreCase(in.reasonKey())) {
                return new Next(in);
            }
            long ago = in.now() - r.created();
            if (ago < 0 || ago >= window.toMillis() || in.force() || in.confirmed()) {
                return new Next(in);
            }
            boolean own = in.actor().id() != null && in.actor().id().equals(r.issuer());
            return stop(Outcome.BLOCKED, "duplicate", own ? "error.duplicate-self" : "error.duplicate", in,
                    "staff", r.issuerName(), "ago", PunishConfig.text(Duration.ofSeconds(Math.max(1, ago / 1000))),
                    "reason", r.reason(), "id", String.valueOf(r.id()));
        };
    }

    /** Never replace an active mute/ban with a shorter one (spec 5.5 / 12.2). */
    static Result neverShorten(Input in) {
        Punishment active = switch (in.type()) {
            case MUTE -> in.activeMute();
            case BAN -> in.activeBan();
            default -> null;
        };
        if (active == null || !active.active(in.now())) {
            return new Next(in);
        }
        if (in.ipWide() && !active.ipWide()) {
            return new Next(in); // an IP-wide ban adds coverage even if the account ban is longer
        }
        if (covers(active.expires(), in.endsAt())) {
            String left = active.expires() == null ? "perm" : PunishConfig.text(Duration.ofMillis(Math.max(1000,
                    active.expires() - in.now())));
            return stop(Outcome.SKIPPED, "never-shorten", in.type() == Punishment.Type.MUTE ? "error.already-muted"
                    : "error.already-banned", in, "left", left, "id", String.valueOf(active.id()));
        }
        return new Next(in);
    }

    /** True if something ending at {@code activeEnd} lasts at least as long as {@code newEnd} ({@code null} = never). */
    static boolean covers(Long activeEnd, Long newEnd) {
        if (activeEnd == null) {
            return true;
        }
        if (newEnd == null) {
            return false;
        }
        return activeEnd >= newEnd;
    }
}
