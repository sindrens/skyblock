package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.command.Durations;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Approval for big punishments (spec 13.4), pure. {@code approval.rules} in {@code punishments.yml} lists what needs an
 * approver ({@code skyblock.staff.punish.approve}); a staff member without it creates a request instead of punishing.
 * The request state machine ({@link #check}) decides who may approve or deny and when; the store applies it atomically.
 */
final class Approval {

    private Approval() {
    }

    /** What a rule looks at. {@code BAN} = every ban (also IP-wide), {@code IPBAN} = IP-wide bans only, {@code MUTE} = every mute. */
    enum Kind { BAN, IPBAN, MUTE, SANCTION }

    /** {@code *} any length, {@code =} exactly, {@code >=} at least, {@code >} longer than. {@code perm} is longer than any time. */
    enum Op { ANY, EQUAL, AT_LEAST, LONGER }

    /**
     * One rule: {@code ban perm}, {@code ipban *}, {@code mute perm}, {@code ban >= 30d}, {@code ban > 7d}, {@code mute 7d}
     * (a bare time = exactly that). {@code value == null} = permanent.
     */
    record Rule(Kind kind, Op op, Duration value, String text) {

        /** Error codes of {@link #parse} (exception message). */
        static final String BAD_KIND = "kind";
        static final String BAD_VALUE = "value";
        static final List<String> KINDS = List.of("ban", "ipban", "mute", "sanction");

        static Rule parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(BAD_KIND);
            }
            String[] parts = raw.strip().toLowerCase(Locale.ROOT).split("\\s+");
            if (parts[0].equals("sanction")) {
                // P7 (spec 13.4 example "any sanction of type money or confiscate"): sanction freeze|trade|money|confiscate|*
                if (parts.length != 2 || (!parts[1].equals("*") && Sanction.Kind.parse(parts[1]).isEmpty())) {
                    throw new IllegalArgumentException(BAD_VALUE);
                }
                return new Rule(Kind.SANCTION, Op.ANY, null, "sanction " + parts[1]);
            }
            Kind kind = switch (parts[0]) {
                case "ban" -> Kind.BAN;
                case "ipban" -> Kind.IPBAN;
                case "mute" -> Kind.MUTE;
                default -> throw new IllegalArgumentException(BAD_KIND);
            };
            String canonical = String.join(" ", parts);
            if (parts.length == 2 && parts[1].equals("*")) {
                return new Rule(kind, Op.ANY, null, canonical);
            }
            Op op;
            String value;
            if (parts.length == 2) {
                op = Op.EQUAL;
                value = parts[1];
            } else if (parts.length == 3 && (parts[1].equals(">=") || parts[1].equals(">") || parts[1].equals("="))) {
                op = switch (parts[1]) {
                    case ">=" -> Op.AT_LEAST;
                    case ">" -> Op.LONGER;
                    default -> Op.EQUAL;
                };
                value = parts[2];
            } else {
                throw new IllegalArgumentException(BAD_VALUE);
            }
            return new Rule(kind, op, length(value), canonical);
        }

        private static Duration length(String value) {
            if (value.equals("perm") || value.equals("permanent")) {
                return null;
            }
            try {
                Duration d = Durations.parse(value);
                if (d.isZero() || d.isNegative()) {
                    throw new IllegalArgumentException(BAD_VALUE);
                }
                return d;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(BAD_VALUE);
            }
        }

        /** True if a punishment of this type, scope and length ({@code null} = permanent) falls under the rule. */
        boolean matches(Punishment.Type type, boolean ipWide, Duration duration) {
            boolean kindOk = switch (kind) {
                case BAN -> type == Punishment.Type.BAN;
                case IPBAN -> type == Punishment.Type.BAN && ipWide;
                case MUTE -> type == Punishment.Type.MUTE;
                case SANCTION -> false;
            };
            if (!kindOk) {
                return false;
            }
            return switch (op) {
                case ANY -> true;
                case EQUAL -> value == null ? duration == null : duration != null && duration.equals(value);
                case AT_LEAST -> value == null ? duration == null : duration == null || duration.compareTo(value) >= 0;
                case LONGER -> value != null && (duration == null || duration.compareTo(value) > 0);
            };
        }
    }

    /**
     * {@code extras.approval}.
     *
     * @param enabled     off = no requests, the old "too long for your rank" error
     * @param expire      a request nobody handled is gone after this
     * @param selfApprove an approver may approve a request they made themselves (spec default false)
     * @param rules       what needs approval
     * @param denyReasons preset deny reasons (ids; texts in messages {@code approval.deny-reasons.<id>})
     */
    record Settings(boolean enabled, Duration expire, boolean selfApprove, List<Rule> rules, List<String> denyReasons) {

        static final Settings DEFAULT = new Settings(true, Duration.ofHours(24), false, List.of(Rule.parse("ban perm"),
                Rule.parse("ipban *"), Rule.parse("mute perm"), Rule.parse("ban >= 30d"), Rule.parse("sanction money"),
                Rule.parse("sanction confiscate")),
                List.of("evidence", "too-harsh", "wrong-reason", "talked"));

        static final Settings DISABLED = new Settings(false, Duration.ofHours(24), false, List.of(), List.of());

        /** The first rule this punishment falls under, or {@code null}. */
        Rule matching(Punishment.Type type, boolean ipWide, Duration duration) {
            for (Rule r : rules) {
                if (r.matches(type, ipWide, duration)) {
                    return r;
                }
            }
            return null;
        }
    }

    /** True if a sanction of this kind needs an approver under {@code settings} (P7; sanctions are never queued). */
    static boolean sanctionNeedsApproval(Settings settings, Sanction.Kind kind) {
        return sanctionRule(settings, kind) != null;
    }

    /** The first {@code sanction <kind>} rule matching {@code kind}, or {@code null} (approval off = never). */
    static Rule sanctionRule(Settings settings, Sanction.Kind kind) {
        if (settings == null || !settings.enabled()) {
            return null;
        }
        for (Rule r : settings.rules()) {
            if (r.kind() == Kind.SANCTION && (r.text().equals("sanction *") || r.text().equals("sanction " + kind.id()))) {
                return r;
            }
        }
        return null;
    }

    /** Why a request may (not) be approved or denied right now. */
    enum Check { OK, NOT_FOUND, EXPIRED, ALREADY_HANDLED, OWN_REQUEST }

    /**
     * The request state machine: only a PENDING request that has not expired can be handled, the first click wins (the
     * store claims it with {@code status = 'PENDING'} under the target's lock), and an approver never approves their own
     * request unless {@code selfApprove}. Denying your own request is allowed (it withdraws it).
     */
    static Check check(PunishRequest r, UUID by, boolean approve, boolean selfApprove, long now) {
        if (r == null) {
            return Check.NOT_FOUND;
        }
        if (r.expired(now)) {
            return Check.EXPIRED;
        }
        if (!r.pending(now)) {
            return Check.ALREADY_HANDLED;
        }
        if (approve && !selfApprove && by != null && by.equals(r.requester())) {
            return Check.OWN_REQUEST;
        }
        return Check.OK;
    }

    /** The key two requests are merged on (spec: same player and reason): the reason id, else the type and reason text. */
    static String mergeKey(String template, Punishment.Type type, String reason) {
        if (template != null && !template.isBlank()) {
            return template.toLowerCase(Locale.ROOT);
        }
        String text = reason == null ? "" : reason.strip().toLowerCase(Locale.ROOT);
        String key = type.name().toLowerCase(Locale.ROOT) + ":" + text;
        return key.length() <= 48 ? key : key.substring(0, 48);
    }
}
