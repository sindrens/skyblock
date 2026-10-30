package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.command.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Parses and validates {@code punishments.yml} (spec §6.2). Pure: no server needed, so the build test parses the tracked
 * file and the bundled default. Any error rejects the whole file ({@link Result#ok()} false) and the caller keeps the
 * previous working config; warnings never reject.
 */
final class PunishConfigParser {

    static final int MAX_SLOT = 53;
    private static final String ID_PATTERN = "[a-z0-9-]+";
    private static final Set<String> OFF = Set.of("perm", "permanent", "never");

    /** Resolves icon ids; {@code names()} feeds "did you mean". */
    interface Icons {
        /** The item for {@code id}, or {@code null} if it is not an item. */
        Material resolve(String id);

        Collection<String> names();
    }

    /** Result of a parse: {@code config} is {@code null} when there are errors. */
    record Result(PunishConfig config, List<ConfigProblem> errors, List<ConfigProblem> warnings) {
        boolean ok() {
            return errors.isEmpty() && config != null;
        }
    }

    private final Icons icons;
    private final List<ConfigProblem> errors = new ArrayList<>();
    private final List<ConfigProblem> warnings = new ArrayList<>();
    private YamlLines lines;

    private PunishConfigParser(Icons icons) {
        this.icons = icons;
    }

    static Result parse(String text, Icons icons) {
        return new PunishConfigParser(icons).run(text);
    }

    // ------------------------------------------------------------------ problems

    private void error(String key, String path, String... kv) {
        errors.add(problem(key, path, kv));
    }

    private void warn(String key, String path, String... kv) {
        warnings.add(problem(key, path, kv));
    }

    private ConfigProblem problem(String key, String path, String... kv) {
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            args.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return new ConfigProblem(key, path, lines == null ? 0 : lines.line(path), Map.copyOf(args));
    }

    // ------------------------------------------------------------------ parse

    private Result run(String text) {
        lines = YamlLines.index(text);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage()).lines().map(String::strip).filter(l -> !l.isEmpty())
                    .findFirst().orElse("");
            int line = yamlErrorLine(String.valueOf(e.getMessage()));
            errors.add(new ConfigProblem("config.yaml-invalid", "", line, Map.of("detail", detail)));
            return new Result(null, List.copyOf(errors), List.copyOf(warnings));
        }
        List<Templates.Category> categories = categories(yaml);
        Map<String, Templates.Template> byId = new LinkedHashMap<>();
        categories.forEach(c -> c.templates().forEach(t -> byId.put(t.id(), t)));
        PunishConfig.MenuLayout menu = menu(yaml, byId);
        WarnRules.Settings rules = warnRules(yaml);
        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors), List.copyOf(warnings));
        }
        return new Result(new PunishConfig(List.copyOf(categories), menu, rules), List.of(), List.copyOf(warnings));
    }

    /** SnakeYAML says "line 12, column 3"; keep the line for the message. */
    private static int yamlErrorLine(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("line (\\d+)").matcher(message);
        int line = 0;
        while (m.find()) {
            line = Integer.parseInt(m.group(1));
        }
        return line;
    }

    // ------------------------------------------------------------------ categories

    /** Lower-case reason id → path (to catch warn buttons reusing a reason id). */
    private Map<String, String> reasonIds = Map.of();

    private List<Templates.Category> categories(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("categories");
        List<Templates.Category> out = new ArrayList<>();
        if (root == null) {
            error("config.missing-section", "categories", "section", "categories");
            return out;
        }
        Map<String, String> seen = new HashMap<>();
        for (String catId : root.getKeys(false)) {
            String catPath = "categories." + catId;
            ConfigurationSection cat = root.getConfigurationSection(catId);
            if (cat == null) {
                error("config.not-a-section", catPath, "value", catId);
                continue;
            }
            checkId(catId, catPath);
            Material catIcon = icon(cat.getString("icon"), catPath + ".icon", Material.BOOK);
            List<Templates.Template> templates = new ArrayList<>();
            ConfigurationSection reasons = cat.getConfigurationSection("reasons");
            if (reasons == null) {
                error("config.missing-section", catPath + ".reasons", "section", catPath + ".reasons");
            } else {
                for (String id : reasons.getKeys(false)) {
                    String path = catPath + ".reasons." + id;
                    ConfigurationSection t = reasons.getConfigurationSection(id);
                    if (t == null) {
                        error("config.not-a-section", path, "value", id);
                        continue;
                    }
                    checkId(id, path);
                    String other = seen.putIfAbsent(id.toLowerCase(Locale.ROOT), path);
                    if (other != null) {
                        error("config.duplicate-id", path, "value", id, "other", other);
                    }
                    List<Templates.Step> ladder = ladder(t, path + ".ladder");
                    Material icon = icon(t.getString("icon"), path + ".icon", Material.PAPER);
                    if (!ladder.isEmpty()) {
                        templates.add(new Templates.Template(id, catId, t.getString("name"), icon, List.copyOf(ladder)));
                    }
                }
            }
            out.add(new Templates.Category(catId, cat.getString("name"), catIcon, List.copyOf(templates)));
        }
        reasonIds = seen;
        return out;
    }

    private void checkId(String id, String path) {
        if (!id.matches(ID_PATTERN)) {
            error("config.bad-id", path, "value", id);
        }
    }

    private List<Templates.Step> ladder(ConfigurationSection t, String path) {
        List<Templates.Step> ladder = new ArrayList<>();
        if (!t.isList("ladder")) {
            error("config.empty-ladder", path);
            return ladder;
        }
        List<String> raw = t.getStringList("ladder");
        if (raw.isEmpty()) {
            error("config.empty-ladder", path);
        }
        for (String step : raw) {
            Templates.Step parsed = step(step, path, false);
            if (parsed != null) {
                ladder.add(parsed);
            }
        }
        return ladder;
    }

    /** Parses a step; {@code banOrMuteOnly} for rule actions (a rule may never warn or kick: no loops). */
    private Templates.Step step(String raw, String path, boolean banOrMuteOnly) {
        try {
            Templates.Step step = Templates.Step.parse(raw);
            if (banOrMuteOnly && step.type() != Punishment.Type.MUTE && step.type() != Punishment.Type.BAN) {
                error("config.bad-rule-action", path, "value", raw);
                return null;
            }
            return step;
        } catch (IllegalArgumentException e) {
            String value = raw == null ? "" : raw.strip();
            if (Templates.Step.BAD_KIND.equals(e.getMessage())) {
                String[] parts = value.split("\\s+", 2);
                String kind = Hints.closest(parts[0].toLowerCase(Locale.ROOT), Templates.Step.KINDS);
                String suggestion = kind == null ? "" : kind + (parts.length > 1 ? " " + parts[1] : "");
                error("config.bad-step", path, "value", value, "suggestion", suggestion);
            } else {
                error("config.bad-step-time", path, "value", value);
            }
            return null;
        }
    }

    private Material icon(String raw, String path, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material m = icons.resolve(raw.strip());
        if (m == null) {
            error("config.bad-icon", path, "value", raw, "suggestion",
                    nullToEmpty(Hints.closest(raw.strip().toUpperCase(Locale.ROOT), icons.names())));
            return fallback;
        }
        return m;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ menu

    private PunishConfig.MenuLayout menu(YamlConfiguration yaml, Map<String, Templates.Template> templates) {
        ConfigurationSection menu = yaml.getConfigurationSection("menu");
        PunishConfig.MenuLayout d = PunishConfig.MenuLayout.EMPTY;
        if (menu == null) {
            warn("config.missing-optional", "menu", "section", "menu");
            return d;
        }
        Map<Integer, String> used = new TreeMap<>();
        boolean confirm = bool(menu, "confirm", "menu.confirm", d.confirm());
        boolean keepOpen = bool(menu, "keep-open", "menu.keep-open", d.keepOpen());
        Material filler = icon(menu.getString("filler"), "menu.filler", d.filler());
        Material divider = icon(menu.getString("divider"), "menu.divider", d.divider());
        Material counterIcon = icon(menu.getString("warn-counter-icon"), "menu.warn-counter-icon", d.warnCounterIcon());
        Material unavailable = icon(menu.getString("unavailable-icon"), "menu.unavailable-icon", d.unavailableIcon());
        int summary = slot(menu, "summary-slot", "menu.summary-slot", d.summarySlot(), "summary", used);
        int counter = slot(menu, "warn-counter-slot", "menu.warn-counter-slot", d.warnCounterSlot(), "warn-counter", used);

        Map<String, PunishConfig.Button> buttons = new LinkedHashMap<>();
        ConfigurationSection b = menu.getConfigurationSection("buttons");
        if (b != null) {
            for (String id : b.getKeys(false)) {
                String path = "menu.buttons." + id;
                if (!PunishConfig.BUTTONS.contains(id)) {
                    error("config.unknown-button", path, "value", id, "suggestion",
                            nullToEmpty(Hints.closest(id, PunishConfig.BUTTONS)));
                    continue;
                }
                int s = slotValue(b, id, path, "button " + id, used);
                // null icon = the button's built-in default (see PunishMenus.PunishMenu#button)
                Material icon = icon(b.isConfigurationSection(id) ? b.getString(id + ".icon") : null, path + ".icon", null);
                if (s >= 0) {
                    buttons.put(id, new PunishConfig.Button(id, s, icon));
                }
            }
        }

        Map<String, Integer> reasonSlots = new LinkedHashMap<>();
        Map<String, String> quarterOf = new LinkedHashMap<>();
        for (String quarter : PunishConfig.QUARTERS) {
            ConfigurationSection q = menu.getConfigurationSection(quarter);
            if (q == null) {
                continue;
            }
            for (String id : q.getKeys(false)) {
                String path = "menu." + quarter + "." + id;
                Templates.Template t = templates.get(id);
                if (t == null) {
                    error("config.unknown-reason", path, "value", id, "suggestion",
                            nullToEmpty(Hints.closest(id, templates.keySet())));
                    continue;
                }
                if (reasonSlots.containsKey(id)) {
                    error("config.reason-placed-twice", path, "value", id, "other", "menu." + quarterOf.get(id) + "." + id);
                    continue;
                }
                int s = slotValue(q, id, path, "reason " + id, used);
                if (s >= 0) {
                    reasonSlots.put(id, s);
                    quarterOf.put(id, quarter);
                }
            }
        }

        List<PunishConfig.WarnButton> warns = new ArrayList<>();
        ConfigurationSection w = menu.getConfigurationSection("warns");
        if (w != null) {
            Set<String> ids = new HashSet<>();
            for (String id : w.getKeys(false)) {
                String path = "menu.warns." + id;
                checkId(id, path);
                String clash = reasonIds.get(id.toLowerCase(Locale.ROOT));
                if (clash != null || !ids.add(id.toLowerCase(Locale.ROOT))) {
                    error("config.duplicate-id", path, "value", id, "other", clash == null ? path : clash);
                    continue;
                }
                int s = slotValue(w, id, path, "warn button " + id, used);
                Material icon = icon(w.isConfigurationSection(id) ? w.getString(id + ".icon") : null, path + ".icon",
                        Material.PAPER);
                if (s >= 0) {
                    warns.add(new PunishConfig.WarnButton(id, s, icon));
                }
            }
        }

        List<Integer> dividers = new ArrayList<>();
        if (menu.isList("divider-slots")) {
            List<?> raw = menu.getList("divider-slots");
            for (int i = 0; i < raw.size(); i++) {
                String path = "menu.divider-slots";
                Object v = raw.get(i);
                if (!(v instanceof Integer n) || n < 0 || n > MAX_SLOT) {
                    error("config.bad-slot", path, "value", String.valueOf(v));
                    continue;
                }
                String other = used.putIfAbsent(n, "divider");
                if (other != null) {
                    error("config.duplicate-slot", path, "value", String.valueOf(n), "other", other);
                    continue;
                }
                dividers.add(n);
            }
        }
        return new PunishConfig.MenuLayout(confirm, keepOpen, filler, divider, List.copyOf(dividers), summary, counter,
                counterIcon, unavailable, Map.copyOf(buttons), java.util.Collections.unmodifiableMap(reasonSlots),
                Map.copyOf(quarterOf), List.copyOf(warns));
    }

    private boolean bool(ConfigurationSection s, String key, String path, boolean fallback) {
        if (!s.contains(key)) {
            return fallback;
        }
        if (!s.isBoolean(key)) {
            error("config.bad-boolean", path, "value", String.valueOf(s.get(key)));
            return fallback;
        }
        return s.getBoolean(key);
    }

    /** A plain slot key ({@code summary-slot: 4}). */
    private int slot(ConfigurationSection s, String key, String path, int fallback, String owner, Map<Integer, String> used) {
        if (!s.contains(key)) {
            String other = used.putIfAbsent(fallback, owner);
            if (other != null) {
                error("config.duplicate-slot", path, "value", String.valueOf(fallback), "other", other);
            }
            return fallback;
        }
        int v = slotValue(s, key, path, owner, used);
        return v < 0 ? fallback : v;
    }

    /** {@code key: 9} or {@code key: { slot: 9, ... }}; -1 on error. Records the slot owner. */
    private int slotValue(ConfigurationSection s, String key, String path, String owner, Map<Integer, String> used) {
        Object raw = s.isConfigurationSection(key) ? s.get(key + ".slot") : s.get(key);
        String slotPath = s.isConfigurationSection(key) ? path + ".slot" : path;
        if (!(raw instanceof Integer n)) {
            error("config.bad-slot", slotPath, "value", String.valueOf(raw));
            return -1;
        }
        if (n < 0 || n > MAX_SLOT) {
            error("config.bad-slot", slotPath, "value", String.valueOf(n));
            return -1;
        }
        String other = used.putIfAbsent(n, owner);
        if (other != null) {
            error("config.duplicate-slot", slotPath, "value", String.valueOf(n), "other", other);
            return -1;
        }
        return n;
    }

    // ------------------------------------------------------------------ warn rules

    private WarnRules.Settings warnRules(YamlConfiguration yaml) {
        ConfigurationSection w = yaml.getConfigurationSection("warn-rules");
        WarnRules.Settings d = WarnRules.Settings.DISABLED;
        if (w == null) {
            warn("config.missing-optional", "warn-rules", "section", "warn-rules");
            return d;
        }
        boolean enabled = bool(w, "enabled", "warn-rules.enabled", true);
        WarnRules.Mode mode = WarnRules.Mode.NOTIFY;
        String rawMode = w.getString("mode", "notify");
        try {
            mode = WarnRules.Mode.valueOf(rawMode.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            error("config.bad-mode", "warn-rules.mode", "value", rawMode, "suggestion",
                    nullToEmpty(Hints.closest(rawMode.toLowerCase(Locale.ROOT), List.of("notify", "auto"))));
        }
        Duration warnExpire = duration(w, "warn-expire", "warn-rules.warn-expire", Duration.ofDays(30), true);
        boolean reset = bool(w, "reset-after-trigger", "warn-rules.reset-after-trigger", true);
        Duration pendingExpire = duration(w, "pending-expire", "warn-rules.pending-expire", Duration.ofHours(24), true);

        List<WarnRules.Rule> rules = new ArrayList<>();
        Set<Integer> ats = new HashSet<>();
        if (w.contains("rules") && !w.isList("rules")) {
            error("config.bad-rules", "warn-rules.rules");
        }
        List<Map<?, ?>> list = w.getMapList("rules");
        for (int i = 0; i < list.size(); i++) {
            Map<?, ?> rule = list.get(i);
            String path = "warn-rules.rules." + i;
            Object at = rule.get("at");
            if (!(at instanceof Integer n) || n < 1) {
                error("config.bad-rule-at", path + ".at", "value", String.valueOf(at));
                continue;
            }
            if (!ats.add(n)) {
                error("config.duplicate-rule-at", path + ".at", "value", String.valueOf(n));
                continue;
            }
            Object action = rule.get("action");
            Templates.Step step = step(action == null ? null : String.valueOf(action), path + ".action", true);
            if (step != null) {
                rules.add(new WarnRules.Rule(n, step));
            }
        }
        rules.sort(java.util.Comparator.comparingInt(WarnRules.Rule::at));

        Duration repeatWindow = null;
        Templates.Step repeatAction = null;
        boolean hasWindow = w.contains("repeat-window") && !String.valueOf(w.get("repeat-window")).isBlank();
        boolean hasAction = w.contains("repeat-action") && !String.valueOf(w.get("repeat-action")).isBlank();
        if (hasWindow) {
            repeatWindow = duration(w, "repeat-window", "warn-rules.repeat-window", null, false);
        }
        if (hasAction) {
            repeatAction = step(w.getString("repeat-action"), "warn-rules.repeat-action", true);
        }
        if (hasWindow != hasAction) {
            warn("config.repeat-incomplete", "warn-rules." + (hasWindow ? "repeat-action" : "repeat-window"));
        }
        return new WarnRules.Settings(enabled, mode, warnExpire, reset, pendingExpire, List.copyOf(rules), repeatWindow,
                repeatAction);
    }

    /** A duration key; {@code perm} allowed only if {@code permAllowed} (returns {@code null} = never). */
    private Duration duration(ConfigurationSection s, String key, String path, Duration fallback, boolean permAllowed) {
        if (!s.contains(key)) {
            return fallback;
        }
        String raw = String.valueOf(s.get(key)).strip();
        if (permAllowed && OFF.contains(raw.toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            return Durations.parse(raw);
        } catch (IllegalArgumentException e) {
            error("config.bad-duration", path, "value", raw);
            return fallback;
        }
    }

    // ------------------------------------------------------------------ hints

    /** "Did you mean": the closest option within a small edit distance. */
    static final class Hints {

        private Hints() {
        }

        static String closest(String input, Collection<String> options) {
            if (input == null || input.isEmpty()) {
                return null;
            }
            String best = null;
            int bestDistance = Integer.MAX_VALUE;
            int limit = Math.max(1, Math.min(3, input.length() / 3));
            for (String option : options) {
                int d = distance(input.toLowerCase(Locale.ROOT), option.toLowerCase(Locale.ROOT), limit);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = option;
                }
            }
            return bestDistance <= limit && bestDistance > 0 ? best : null;
        }

        /** Levenshtein distance, giving up (returns limit + 1) once it can't be within {@code limit}. */
        static int distance(String a, String b, int limit) {
            if (Math.abs(a.length() - b.length()) > limit) {
                return limit + 1;
            }
            int[] prev = new int[b.length() + 1];
            int[] cur = new int[b.length() + 1];
            for (int j = 0; j <= b.length(); j++) {
                prev[j] = j;
            }
            for (int i = 1; i <= a.length(); i++) {
                cur[0] = i;
                int rowMin = cur[0];
                for (int j = 1; j <= b.length(); j++) {
                    int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                    cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                    rowMin = Math.min(rowMin, cur[j]);
                }
                if (rowMin > limit) {
                    return limit + 1;
                }
                int[] t = prev;
                prev = cur;
                cur = t;
            }
            return prev[b.length()];
        }
    }
}
