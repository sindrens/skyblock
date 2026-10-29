package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.command.Durations;
import org.bukkit.Material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The active {@code punishments.yml}: categories → reasons → escalation ladder, the {@code /p} layout and the warn rules.
 * Parsed and validated by {@link PunishConfigParser}; a file with any error is rejected as a whole and the previous
 * working config stays active (see {@link ModerationModule#loadPunishments}).
 */
final class Templates {

    /** One ladder step: what happens on the n-th offence. {@code duration == null} = permanent (or n/a for warn/kick). */
    record Step(Punishment.Type type, Duration duration, boolean ipWide) {

        /** Error codes thrown by {@link #parse} (as the exception message). */
        static final String BAD_KIND = "kind";
        static final String BAD_TIME = "time";

        /** The step kinds the parser accepts (also used for "did you mean"). */
        static final List<String> KINDS = List.of("warn", "kick", "mute", "ban", "ipban");

        /**
         * Parses {@code warn | kick | mute <time|perm> | ban <time|perm> | ipban <time|perm>}.
         *
         * @throws IllegalArgumentException with message {@link #BAD_KIND} or {@link #BAD_TIME}
         */
        static Step parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(BAD_KIND);
            }
            String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (parts.length > 2) {
                throw new IllegalArgumentException(BAD_TIME);
            }
            String kind = parts[0];
            String time = parts.length > 1 ? parts[1] : null;
            return switch (kind) {
                case "warn" -> noTime(Punishment.Type.WARN, time);
                case "kick" -> noTime(Punishment.Type.KICK, time);
                case "mute" -> new Step(Punishment.Type.MUTE, duration(time), false);
                case "ban" -> new Step(Punishment.Type.BAN, duration(time), false);
                case "ipban" -> new Step(Punishment.Type.BAN, duration(time), true);
                default -> throw new IllegalArgumentException(BAD_KIND);
            };
        }

        private static Step noTime(Punishment.Type type, String time) {
            if (time != null) {
                throw new IllegalArgumentException(BAD_TIME);
            }
            return new Step(type, null, false);
        }

        private static Duration duration(String time) {
            if (time == null || time.equals("perm") || time.equals("permanent")) {
                return null;
            }
            try {
                return Durations.parse(time);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(BAD_TIME);
            }
        }

        /** The canonical config syntax ({@code mute 1h}, {@code ban perm}); for audit details and config round trips. */
        String describe() {
            String base = type.name().toLowerCase(Locale.ROOT);
            if (ipWide) {
                base = "ip" + base;
            }
            if (type == Punishment.Type.WARN || type == Punishment.Type.KICK) {
                return base;
            }
            return base + " " + (duration == null ? "perm" : compact(duration));
        }

        /** {@code 1h}, {@code 7d}, {@code 1h30m}: the shortest form {@link Durations#parse} reads back. */
        static String compact(Duration d) {
            long s = d.toSeconds();
            StringBuilder out = new StringBuilder();
            long[] units = {604_800, 86_400, 3_600, 60, 1};
            String[] names = {"w", "d", "h", "m", "s"};
            for (int i = 0; i < units.length; i++) {
                if (s >= units[i]) {
                    out.append(s / units[i]).append(names[i]);
                    s %= units[i];
                }
            }
            return out.isEmpty() ? "0s" : out.toString();
        }
    }

    /**
     * A reason with its ladder. {@code yamlName} is an optional legacy name from the YAML; the display name normally comes
     * from {@code messages/moderation.yml → reasons.<id>}.
     */
    record Template(String id, String category, String yamlName, Material icon, List<Step> ladder) {
        /** The step for a player who already has {@code previous} punishments with this template. */
        Step stepFor(int previous) {
            return ladder.get(Math.min(Math.max(0, previous), ladder.size() - 1));
        }

        int stepIndex(int previous) {
            return Math.min(Math.max(0, previous), ladder.size() - 1);
        }
    }

    record Category(String id, String yamlName, Material icon, List<Template> templates) {
    }

    private volatile PunishConfig config = PunishConfig.EMPTY;

    PunishConfig config() {
        return config;
    }

    void use(PunishConfig newConfig) {
        this.config = newConfig;
    }

    /**
     * Safe reload: takes the parsed file only if it has no errors; otherwise the current (last good) config stays.
     *
     * @return true if the new config is now active
     */
    boolean offer(PunishConfigParser.Result result) {
        if (!result.ok()) {
            return false;
        }
        this.config = result.config();
        return true;
    }

    List<Category> categories() {
        return config.categories();
    }

    Optional<Template> template(String id) {
        for (Category c : config.categories()) {
            for (Template t : c.templates()) {
                if (t.id().equalsIgnoreCase(id)) {
                    return Optional.of(t);
                }
            }
        }
        return Optional.empty();
    }

    List<String> templateIds() {
        List<String> ids = new ArrayList<>();
        config.categories().forEach(c -> c.templates().forEach(t -> ids.add(t.id())));
        return ids;
    }

    /** Reason ids plus warn-button ids: everything {@code /p <player> <reason-id>} accepts. */
    List<String> reasonIds() {
        List<String> ids = templateIds();
        config.menu().warns().forEach(w -> ids.add(w.id()));
        return ids;
    }

    Optional<PunishConfig.WarnButton> warnButton(String id) {
        return config.menu().warns().stream().filter(w -> w.id().equalsIgnoreCase(id)).findFirst();
    }
}
