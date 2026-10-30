package net.skybornmc.skyblock.moderation;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Training mode for new staff (spec 14.6), pure. A staff member with {@code skyblock.staff.punish.trainee} is a trainee:
 * every action whose kind is in {@code extras.training.requests-for} becomes an approval request (13.4) for a mentor,
 * even a small one, so a trainee never applies a real punishment of those kinds on their own. Warnings are not in the
 * default list (spec: applied directly, and any approver can undo them).
 *
 * <p>The dry run ({@code /p <player> <reason> -dry} and the "Dry run" toggle of {@code /p}) is the sandbox for everyone
 * with {@code skyblock.staff.punish.dry}: it runs the same decision pipeline on the loaded state and only reports the
 * verdict. It never writes a punishment, request or suggestion and never touches the target ({@link DryRun}).</p>
 */
final class Training {

    private Training() {
    }

    /** What a trainee's action can be: the punishment kinds plus sanctions and bulk actions. */
    enum Kind {
        BAN, MUTE, KICK, WARN, ISLAND, SANCTION, BULK;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Kind> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String clean = raw.strip().toLowerCase(Locale.ROOT);
            for (Kind k : values()) {
                if (k.id().equals(clean)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }

        static List<String> ids() {
            return java.util.Arrays.stream(values()).map(Kind::id).toList();
        }
    }

    /**
     * {@code extras.training}.
     *
     * @param requestsFor kinds a trainee's action becomes a request for (spec default: ban, mute, kick, sanction, island,
     *                    bulk)
     */
    record Settings(Set<Kind> requestsFor) {

        static final Settings DEFAULT = new Settings(EnumSet.of(Kind.BAN, Kind.MUTE, Kind.KICK, Kind.SANCTION, Kind.ISLAND,
                Kind.BULK));

        Settings {
            requestsFor = requestsFor == null || requestsFor.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(requestsFor));
        }

        boolean covers(Kind kind) {
            return requestsFor.contains(kind);
        }
    }

    /** The kind of a punishment type ({@code ipban} is a ban; the island steps are one kind). */
    static Kind kindOf(Punishment.Type type) {
        return switch (type) {
            case BAN -> Kind.BAN;
            case MUTE -> Kind.MUTE;
            case KICK -> Kind.KICK;
            case WARN -> Kind.WARN;
            case ISLAND_KICK, ISLAND_BAN, COOP_REMOVE, COOP_BLOCK -> Kind.ISLAND;
        };
    }

    /**
     * True if a trainee's punishment of {@code type} must become a request: its kind is listed, or it is part of a bulk
     * action and {@code bulk} is listed (then every player of the bulk action is one request, also for warns).
     */
    static boolean needsRequest(Settings settings, Punishment.Type type, boolean bulk) {
        if (settings == null) {
            return false;
        }
        return settings.covers(kindOf(type)) || (bulk && settings.covers(Kind.BULK));
    }

    /**
     * True if a trainee may not apply a sanction (spec 13.1). Sanctions have no request queue (P7), so a listed
     * {@code sanction} kind refuses them for trainees instead of queueing.
     */
    static boolean sanctionRefused(Settings settings, boolean trainee) {
        return trainee && settings != null && settings.covers(Kind.SANCTION);
    }
}
