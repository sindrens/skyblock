package net.skybornmc.skyblock.moderation;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Notification settings (spec 14.9), pure. Every staff alert of the punishment system has a {@link Type}. A staff member
 * gets a type only with its permission ({@code skyblock.staff.notify.<type>}) and while they did not switch it off with
 * {@code /pnotify}; types in {@code extras.notify.always} (default {@code guard}) can't be switched off. Nothing stored =
 * on (the sane default: a new staff member gets every alert their rank allows).
 *
 * <p>Discord: each type may go to its own named webhook ({@code extras.discord.channels}). Nothing is mapped by default.
 * The non-sensitive types (punishments, raid) fall back to the main punishment log channel; the sensitive types
 * (watchlist, appeals with player text, guard, approvals) are only ever sent to a channel mapped for them explicitly.</p>
 */
final class NotifyRules {

    private NotifyRules() {
    }

    /** The notification types of the spec (14.9 table), in menu order. */
    enum Type {
        PUNISHMENTS, SUGGESTIONS, APPROVALS, APPEALS, WATCHLIST, RAID, GUARD, REPORTS, AUTO, REPORT;

        /** Types that may hold player text or private staff matters: never sent to an unmapped Discord channel. */
        static final Set<Type> SENSITIVE = EnumSet.of(WATCHLIST, APPEALS, GUARD, APPROVALS);

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        String permission() {
            return ModerationModule.PERM_NOTIFY + "." + id();
        }

        static Optional<Type> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String clean = raw.strip().toLowerCase(Locale.ROOT);
            for (Type t : values()) {
                if (t.id().equals(clean)) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }

        static List<String> ids() {
            return java.util.Arrays.stream(values()).map(Type::id).toList();
        }
    }

    /**
     * {@code extras.notify} plus {@code extras.discord.channels}.
     *
     * @param always   types that can't be switched off (spec default: guard)
     * @param channels type → webhook name (secrets/discord.yml); empty = no per-type channel
     */
    record Settings(Set<Type> always, Map<Type, String> channels) {

        static final Settings DEFAULT = new Settings(EnumSet.of(Type.GUARD), Map.of());

        Settings {
            always = always == null || always.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(always));
            channels = channels == null ? Map.of() : Map.copyOf(channels);
        }
    }

    /**
     * Whether a staff member gets {@code type}: needs the type's permission; an {@code always} type is then always on;
     * otherwise their own setting, where nothing stored ({@code null}) means on.
     */
    static boolean receives(Type type, boolean hasPermission, Boolean setting, Settings settings) {
        if (!hasPermission) {
            return false;
        }
        if (settings != null && settings.always().contains(type)) {
            return true;
        }
        return setting == null || setting;
    }

    /** Whether {@code /pnotify} may switch {@code type} for this staff member. */
    enum Change { OK, NO_PERMISSION, ALWAYS_ON }

    static Change canChange(Type type, boolean hasPermission, boolean on, Settings settings) {
        if (!hasPermission) {
            return Change.NO_PERMISSION;
        }
        if (!on && settings != null && settings.always().contains(type)) {
            return Change.ALWAYS_ON;
        }
        return Change.OK;
    }

    /**
     * The Discord webhook for {@code type}, or {@code null} (not sent): the channel mapped for it; for a non-sensitive type
     * without a mapping, {@code mainChannel} (the punishment log, {@code extras.discord.channel}); a sensitive type without
     * a mapping is never sent.
     */
    static String discordChannel(Type type, Settings settings, String mainChannel) {
        String mapped = settings == null ? null : settings.channels().get(type);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        return Type.SENSITIVE.contains(type) ? null : mainChannel;
    }

    /** The channel names a type is mapped to (for the webhook sink's switch). */
    static boolean mapped(String channel, Settings settings) {
        return channel != null && settings != null && settings.channels().containsValue(channel);
    }
}
