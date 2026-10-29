package net.skybornmc.skyblock.moderation;

import org.bukkit.Material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared builders for the moderation unit tests (no server). */
final class TestData {

    static final long DAY = Duration.ofDays(1).toMillis();
    static final long HOUR = Duration.ofHours(1).toMillis();
    static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** Icons for tests: any non-legacy item name (isItem() needs a running server). */
    static final PunishConfigParser.Icons ICONS = new PunishConfigParser.Icons() {
        @Override
        public Material resolve(String id) {
            Material m = Material.matchMaterial(id);
            return m == null || m.isLegacy() || m.name().endsWith("AIR") ? null : m;
        }

        @Override
        public Collection<String> names() {
            List<String> out = new ArrayList<>();
            for (Material m : Material.values()) {
                if (!m.isLegacy()) {
                    out.add(m.name());
                }
            }
            return out;
        }
    };

    private TestData() {
    }

    /** The owner's default rules: 2 warns → mute 1h, repeat within 7d → mute 1d, warns expire after 30d. */
    static WarnRules.Settings rules(WarnRules.Mode mode, boolean reset) {
        return new WarnRules.Settings(true, mode, Duration.ofDays(30), reset, Duration.ofHours(24),
                List.of(new WarnRules.Rule(2, Templates.Step.parse("mute 1h"))), Duration.ofDays(7), Templates.Step.parse("mute 1d"));
    }

    static Punishment warn(long id, long created) {
        return punishment(id, Punishment.Type.WARN, created, null, null, null);
    }

    static Punishment mute(long id, long created, Long expires, String source) {
        return punishment(id, Punishment.Type.MUTE, created, expires, null, source);
    }

    static Punishment punishment(long id, Punishment.Type type, long created, Long expires, Long removedAt, String source) {
        return new Punishment(id, type, STEVE, "Steve", null, false, "x", null, null, ALEX, "Alex", created, expires, false,
                removedAt, removedAt == null ? null : ALEX, removedAt == null ? null : "Alex",
                removedAt == null ? null : "mistake", source);
    }

    static Suggestion suggestion(long id, Integer ruleAt, boolean repeat, long created, Suggestion.Status status, Long handledAt) {
        return new Suggestion(id, STEVE, "Steve", Suggestion.KIND_WARN_RULE, ruleAt, repeat, repeat ? "mute 1d" : "mute 1h",
                "auto", "auto:warn-rule:" + (repeat ? "repeat" : ruleAt), 2, created,
                status == Suggestion.Status.PENDING ? created + 24 * HOUR : null, status, handledAt, null, null, null, null);
    }

    /** A staff actor with every type, the given max length. */
    static PunishDecision.Actor staff(Duration max) {
        return new PunishDecision.Actor("Alex", false, Set.of(Punishment.Type.values()), true, true, max, false);
    }
}
