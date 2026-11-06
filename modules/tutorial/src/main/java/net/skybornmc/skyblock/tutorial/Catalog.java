package net.skybornmc.skyblock.tutorial;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The chapters, steps, dialog and idle lines of {@code tutorial.yml} (pure, unit-tested). Broken entries are skipped with a
 * warning, so one typo never stops the whole tutorial.
 */
final class Catalog {

    static final String FILE = "tutorial.yml";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");

    /**
     * One step.
     *
     * @param place   where a click on the step takes the player ({@code spawn}, {@code farm}, {@code mine}, {@code island});
     *                empty = nowhere
     * @param command command (no slash) that teaches the step; when it is not registered on this server the step counts as
     *                done (the feature is off); empty = always available
     * @param dialog  what the NPC says (NPC steps), 1-4 lines; {@code /word} and {@code {/two words}} become clickable
     */
    record Step(String chapter, String id, StepType type, String target, long amount, String text, String hint, String place,
                String command, List<String> dialog, String icon) {

        String key() {
            return chapter + "." + id;
        }

        /** True when the signal counts for this step (type and target). */
        boolean matches(Signal s) {
            if (s.type() != type) {
                return false;
            }
            return switch (type) {
                case NPC, PLACE, TOOL_LEVEL -> target.equalsIgnoreCase(s.target());
                case MENU, PLANT -> anyOf(target, s.target());
                case GRIND -> grind(s);
                case AUDIT -> audit(s);
                case ISLAND, SELL, BANK, CHEST, GENERATOR -> true;
            };
        }

        private boolean grind(Signal s) {
            String[] parts = target.split(":", 2);
            if (!parts[0].equalsIgnoreCase(s.target())) {
                return false;
            }
            if (parts.length < 2 || parts[1].isBlank()) {
                return true;
            }
            return anyOf(parts[1], s.details().getOrDefault("block", "")) || anyOf(parts[1], s.details().getOrDefault("drop", ""));
        }

        private boolean audit(Signal s) {
            String[] parts = target.split(":", 3);
            if (parts.length < 2) {
                return false;
            }
            String[] got = s.target().split(":", 2);
            if (got.length < 2 || !parts[0].equalsIgnoreCase(got[0]) || !anyOf(parts[1], got[1])) {
                return false;
            }
            if (parts.length == 3 && !parts[2].isBlank()) {
                String[] kv = parts[2].split("=", 2);
                String have = s.details().get(kv[0].strip());
                return kv.length == 2 && have != null && kv[1].strip().equalsIgnoreCase(have);
            }
            return true;
        }

        private static boolean anyOf(String alternatives, String value) {
            if (value == null || value.isEmpty()) {
                return false;
            }
            for (String a : alternatives.split("\\|")) {
                if (a.strip().equalsIgnoreCase(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * One chapter.
     *
     * @param rewards reward lines ({@code money 500}, {@code shards 100}, {@code key ember 1}, see {@link Rewards})
     */
    record Chapter(String id, String name, String icon, List<String> rewards, List<Step> steps) {

        Optional<Step> step(String id) {
            for (Step s : steps) {
                if (s.id().equalsIgnoreCase(id)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    private final List<Chapter> chapters;
    private final Map<String, List<String>> idle;

    Catalog(List<Chapter> chapters, Map<String, List<String>> idle) {
        this.chapters = List.copyOf(chapters);
        this.idle = Map.copyOf(idle);
    }

    static Catalog empty() {
        return new Catalog(List.of(), Map.of());
    }

    List<Chapter> chapters() {
        return chapters;
    }

    Optional<Chapter> chapter(String id) {
        for (Chapter c : chapters) {
            if (c.id().equalsIgnoreCase(id)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    int index(Chapter chapter) {
        return chapters.indexOf(chapter);
    }

    /** Every step key ({@code chapter.step}) in order. */
    List<String> stepKeys() {
        List<String> out = new ArrayList<>();
        chapters.forEach(c -> c.steps().forEach(s -> out.add(s.key())));
        return out;
    }

    Optional<Step> step(String key) {
        int dot = key == null ? -1 : key.indexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return chapter(key.substring(0, dot)).flatMap(c -> c.step(key.substring(dot + 1)));
    }

    /** Idle lines of a role (said when the NPC is clicked outside a step); empty when it has none. */
    List<String> idle(String role) {
        return role == null ? List.of() : idle.getOrDefault(role.toLowerCase(Locale.ROOT), List.of());
    }

    Map<String, List<String>> idleRoles() {
        return idle;
    }

    // ------------------------------------------------------------------ parsing

    static Catalog load(ConfigurationSection root, Consumer<String> warn) {
        List<Chapter> chapters = new ArrayList<>();
        ConfigurationSection cs = root == null ? null : root.getConfigurationSection("chapters");
        if (cs == null) {
            warn.accept(FILE + ": no 'chapters' section; the tutorial has nothing to show");
        } else {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(id);
                if (c == null || !ID.matcher(id).matches()) {
                    warn.accept(FILE + " -> chapters." + id + ": not a chapter (ids: a-z, 0-9, _), skipped");
                    continue;
                }
                List<Step> steps = steps(id, c.getConfigurationSection("steps"), warn);
                if (steps.isEmpty()) {
                    warn.accept(FILE + " -> chapters." + id + ": no valid steps, skipped");
                    continue;
                }
                chapters.add(new Chapter(id, c.getString("name", id), c.getString("icon", "book"),
                        List.copyOf(c.getStringList("rewards")), steps));
            }
        }
        Map<String, List<String>> idle = new LinkedHashMap<>();
        ConfigurationSection is = root == null ? null : root.getConfigurationSection("idle");
        if (is != null) {
            for (String role : is.getKeys(false)) {
                List<String> lines = is.getStringList(role).stream().filter(l -> l != null && !l.isBlank()).toList();
                if (!lines.isEmpty()) {
                    idle.put(role.toLowerCase(Locale.ROOT), lines);
                }
            }
        }
        return new Catalog(chapters, idle);
    }

    private static List<Step> steps(String chapter, ConfigurationSection ss, Consumer<String> warn) {
        if (ss == null) {
            return List.of();
        }
        List<Step> out = new ArrayList<>();
        for (String id : ss.getKeys(false)) {
            ConfigurationSection s = ss.getConfigurationSection(id);
            String where = FILE + " -> chapters." + chapter + ".steps." + id;
            if (s == null || !ID.matcher(id).matches()) {
                warn.accept(where + ": not a step (ids: a-z, 0-9, _), skipped");
                continue;
            }
            Optional<StepType> type = StepType.of(s.getString("type"));
            if (type.isEmpty()) {
                warn.accept(where + ": unknown type '" + s.getString("type") + "', skipped");
                continue;
            }
            String target = s.getString("target", "").strip();
            if (type.get().needsTarget() && target.isEmpty()) {
                warn.accept(where + ": type " + type.get().id() + " needs a target, skipped");
                continue;
            }
            if (type.get() == StepType.AUDIT && target.split(":").length < 2) {
                warn.accept(where + ": audit targets look like 'category:action', skipped");
                continue;
            }
            long amount = Math.max(1, s.getLong("amount", 1));
            String text = s.getString("text", id);
            List<String> dialog = s.getStringList("dialog").stream().filter(l -> l != null && !l.isBlank()).limit(4).toList();
            String command = s.getString("command", "").strip();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            out.add(new Step(chapter, id, type.get(), target, amount, text, s.getString("hint", ""),
                    s.getString("place", "").strip().toLowerCase(Locale.ROOT), command, dialog, s.getString("icon", "")));
        }
        return Collections.unmodifiableList(out);
    }
}
