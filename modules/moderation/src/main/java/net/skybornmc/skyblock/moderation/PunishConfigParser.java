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
        PunishConfig.Extras extras = extras(yaml);
        Map<Integer, RuleBook.Rule> ruleMap = rules(yaml);
        List<Templates.Category> categories = categories(yaml, extras.offenceExpire());
        Map<String, Templates.Template> byId = new LinkedHashMap<>();
        categories.forEach(c -> c.templates().forEach(t -> byId.put(t.id(), t)));
        PunishConfig.MenuLayout menu = menu(yaml, byId);
        WarnRules.Settings rules = warnRules(yaml);
        extras.chatSuggestions().forEach((name, cs) -> {
            if (!byId.containsKey(cs.reason())) {
                error("config.unknown-reason", "extras.chat-suggestions." + name + ".reason", "value", cs.reason(),
                        "suggestion", nullToEmpty(Hints.closest(cs.reason(), byId.keySet())));
            }
        });
        // P9, spec 14.5: the false-report reason must exist while the penalty is on (else nothing could be suggested).
        // Only checked when the section is written (an older file without it keeps loading; the penalty then says off).
        if (yaml.contains("extras.false-reports") && extras.falseReports().enabled()
                && !byId.containsKey(extras.falseReports().reason())) {
            error("config.unknown-reason", "extras.false-reports.reason", "value", extras.falseReports().reason(),
                    "suggestion", nullToEmpty(Hints.closest(extras.falseReports().reason(), byId.keySet())));
        }
        // only a never-auto list written in the file is checked (the built-in default may name reasons a file removed)
        for (String id : yaml.contains("extras.never-auto") ? extras.neverAuto() : List.<String>of()) {
            if (!byId.containsKey(id)) {
                error("config.unknown-reason", "extras.never-auto", "value", id, "suggestion",
                        nullToEmpty(Hints.closest(id, byId.keySet())));
            }
        }
        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors), List.copyOf(warnings));
        }
        return new Result(new PunishConfig(List.copyOf(categories), menu, rules, extras, new RuleBook(ruleMap, ruleRefs)),
                List.of(), List.copyOf(warnings));
    }

    // ------------------------------------------------------------------ rule numbers (P8, spec 14.2)

    /** Rule numbers that exist (filled by {@link #rules} before the reasons are read). */
    private Set<Integer> ruleNumbers = Set.of();
    /** Reason id / warn-button id → rule numbers (only references to existing rules). */
    private final Map<String, List<Integer>> ruleRefs = new LinkedHashMap<>();

    /**
     * {@code rules:} (top level, optional): {@code 3: { link: "https://..." }} or {@code 3: "https://..."}. Numbers 1-999.
     * Titles are texts (messages {@code rules.titles.<n>}); a {@code title:} here is only a fallback.
     */
    private Map<Integer, RuleBook.Rule> rules(YamlConfiguration yaml) {
        Map<Integer, RuleBook.Rule> out = new TreeMap<>();
        if (!yaml.contains("rules")) {
            ruleNumbers = Set.of();
            return out;
        }
        ConfigurationSection root = yaml.getConfigurationSection("rules");
        if (root == null) {
            error("config.not-a-section", "rules", "value", "rules");
            ruleNumbers = Set.of();
            return out;
        }
        for (String key : root.getKeys(false)) {
            String path = "rules." + key;
            int number;
            try {
                number = Integer.parseInt(key.strip());
            } catch (NumberFormatException e) {
                number = -1;
            }
            if (number < 1 || number > RuleBook.MAX_NUMBER) {
                error("config.bad-rule-number", path, "value", key, "max", String.valueOf(RuleBook.MAX_NUMBER));
                continue;
            }
            if (out.containsKey(number)) {
                error("config.duplicate-id", path, "value", key, "other", "rules." + number);
                continue;
            }
            String link = null;
            String title = null;
            if (root.isConfigurationSection(key)) {
                ConfigurationSection r = root.getConfigurationSection(key);
                for (String k : r.getKeys(false)) {
                    if (!k.equals("link") && !k.equals("title")) {
                        error("config.unknown-key", path + "." + k, "value", k, "suggestion",
                                nullToEmpty(Hints.closest(k, List.of("link", "title"))));
                    }
                }
                link = r.contains("link") ? String.valueOf(r.get("link")).strip() : null;
                title = r.contains("title") ? String.valueOf(r.get("title")).strip() : null;
            } else if (root.get(key) != null) {
                link = String.valueOf(root.get(key)).strip();
            }
            if (link != null && (link.isEmpty() || !validLink(link))) {
                error("config.bad-link", path + ".link", "value", link);
                link = null;
            }
            out.put(number, new RuleBook.Rule(number, link, title == null || title.isEmpty() ? null : title));
        }
        ruleNumbers = Set.copyOf(out.keySet());
        return out;
    }

    /** A link usable inside a click tag: no quotes, angle brackets or spaces. */
    private static boolean validLink(String link) {
        return !(link.contains("'") || link.contains("\"") || link.contains("<") || link.contains(">") || link.contains(" "));
    }

    /**
     * {@code rule: 3} or {@code rule: [1, 3]} of a reason / warn button (spec 14.2). Only numbers listed under
     * {@code rules:} are allowed: an unknown number is an error with the file, line and reason id. Missing = no rule.
     */
    private void ruleRef(ConfigurationSection s, String id, String path) {
        if (s == null || !s.contains("rule")) {
            return;
        }
        String p = path + ".rule";
        List<Object> raw = s.isList("rule") ? new ArrayList<>(s.getList("rule")) : List.of(s.get("rule"));
        List<Integer> numbers = new ArrayList<>();
        for (Object v : raw) {
            Integer n = v instanceof Integer i ? i : null;
            if (n == null && v instanceof String str) {
                try {
                    n = Integer.parseInt(str.strip());
                } catch (NumberFormatException ignored) {
                    n = null;
                }
            }
            if (n == null) {
                error("config.bad-rule-ref", p, "value", String.valueOf(v), "id", id);
                continue;
            }
            if (!ruleNumbers.contains(n)) {
                error("config.unknown-rule", p, "value", String.valueOf(n), "id", id, "rules",
                        ruleNumbers.isEmpty() ? "-" : ruleNumbers.stream().sorted().map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(", ")));
                continue;
            }
            if (!numbers.contains(n)) {
                numbers.add(n);
            }
        }
        if (raw.isEmpty()) {
            error("config.bad-rule-ref", p, "value", "[]", "id", id);
        }
        if (!numbers.isEmpty()) {
            ruleRefs.put(id.toLowerCase(Locale.ROOT), List.copyOf(numbers));
        }
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

    private List<Templates.Category> categories(YamlConfiguration yaml, Duration defaultOffenceExpire) {
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
            // spec 13.7: per category switch (default on); a reason may override it with its own probation: key
            boolean catProbation = bool(cat, "probation", catPath + ".probation", true);
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
                    // spec 12.3: per reason, e.g. offence-expire: perm for cheating, 180d for a slow-burning reason
                    Duration expire = duration(t, "offence-expire", path + ".offence-expire", defaultOffenceExpire, true);
                    boolean probation = bool(t, "probation", path + ".probation", catProbation);
                    // spec 13.10: appealable: false (alt abuse, RMT) = no /appeal for punishments of this reason
                    boolean appealable = bool(t, "appealable", path + ".appealable", true);
                    ruleRef(t, id, path); // P8, spec 14.2
                    if (!ladder.isEmpty()) {
                        templates.add(new Templates.Template(id, catId, t.getString("name"), icon, List.copyOf(ladder), expire,
                                probation, appealable));
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
                int weight = w.isConfigurationSection(id) ? weight(w.getConfigurationSection(id), path + ".weight") : Weights.ONE;
                if (w.isConfigurationSection(id)) {
                    ruleRef(w.getConfigurationSection(id), id, path); // P8, spec 14.2
                }
                if (s >= 0) {
                    warns.add(new PunishConfig.WarnButton(id, s, icon, weight));
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

    /** {@code weight: 0.5} of a warn button (spec 12.7), in tenths; missing = 1. */
    private int weight(ConfigurationSection s, String path) {
        if (s == null || !s.contains("weight")) {
            return Weights.ONE;
        }
        String raw = String.valueOf(s.get("weight")).strip();
        try {
            return Weights.parse(raw);
        } catch (IllegalArgumentException e) {
            error("config.bad-weight", path, "value", raw);
            return Weights.ONE;
        }
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

    // ------------------------------------------------------------------ extras (spec 12.13, P2 keys)

    private PunishConfig.Extras extras(YamlConfiguration yaml) {
        PunishConfig.Extras d = PunishConfig.Extras.DEFAULT;
        ConfigurationSection x = yaml.getConfigurationSection("extras");
        if (x == null) {
            warn("config.missing-optional", "extras", "section", "extras");
            return d;
        }
        Duration undo = positive(x, "undo-window", "extras.undo-window", d.undoWindow());
        Duration dup = positive(x, "duplicate-window", "extras.duplicate-window", d.duplicateWindow());
        Duration confirm = positive(x, "duplicate-confirm", "extras.duplicate-confirm", d.duplicateConfirm());
        Duration expire = duration(x, "offence-expire", "extras.offence-expire", d.offenceExpire(), true);
        java.time.ZoneId zone = d.statsZone();
        Duration cache = d.statsCache();
        PunishStats.Period period = d.statsPeriod();
        int top = d.statsTop();
        ConfigurationSection stats = x.getConfigurationSection("stats");
        if (stats != null) {
            if (stats.contains("timezone")) {
                String raw = String.valueOf(stats.get("timezone")).strip();
                try {
                    zone = java.time.ZoneId.of(raw);
                } catch (java.time.DateTimeException e) {
                    error("config.bad-zone", "extras.stats.timezone", "value", raw);
                }
            }
            cache = positive(stats, "cache", "extras.stats.cache", cache);
            if (stats.contains("default-period")) {
                String raw = String.valueOf(stats.get("default-period")).strip();
                java.util.Optional<PunishStats.Period> p = PunishStats.Period.parse(raw);
                if (p.isEmpty()) {
                    error("config.bad-period", "extras.stats.default-period", "value", raw, "suggestion",
                            nullToEmpty(Hints.closest(raw.toLowerCase(Locale.ROOT), PunishStats.Period.ids())));
                } else {
                    period = p.get();
                }
            }
            if (stats.contains("top")) {
                Object raw = stats.get("top");
                if (!(raw instanceof Integer n) || n < 1 || n > 50) {
                    error("config.bad-number", "extras.stats.top", "value", String.valueOf(raw), "min", "1", "max", "50");
                } else {
                    top = n;
                }
            }
        }
        if (confirm.compareTo(dup) > 0) {
            warn("config.confirm-longer", "extras.duplicate-confirm");
        }
        boolean closeReports = bool(x, "close-reports", "extras.close-reports", d.closeReports());
        String rulesLink = x.contains("rules-link") ? String.valueOf(x.get("rules-link")).strip() : d.rulesLink();
        if (rulesLink.contains("'") || rulesLink.contains("<") || rulesLink.contains(">") || rulesLink.contains(" ")) {
            error("config.bad-link", "extras.rules-link", "value", rulesLink);
            rulesLink = d.rulesLink();
        }
        return new PunishConfig.Extras(undo, dup, confirm, expire, zone, cache, period, top, closeReports,
                evidence(x.getConfigurationSection("evidence")), notes(x.getConfigurationSection("notes")),
                ack(x.getConfigurationSection("ack")), rulesLink, discord(x.getConfigurationSection("discord")),
                playerView(x.getConfigurationSection("player-view")), chatSuggestions(x.getConfigurationSection("chat-suggestions")),
                approval(x.getConfigurationSection("approval")), watchlist(x.getConfigurationSection("watchlist")),
                probation(x.getConfigurationSection("probation")), muteEnded(x.getConfigurationSection("mute-ended")),
                bulk(x.getConfigurationSection("bulk")), appeal(x.getConfigurationSection("appeal")),
                neverAuto(x), lockdown(x.getConfigurationSection("lockdown")), raid(x.getConfigurationSection("raid")),
                network(x.getConfigurationSection("network")), sanctions(x.getConfigurationSection("sanctions")),
                training(x.getConfigurationSection("training")),
                notify(x.getConfigurationSection("notify"), x.getConfigurationSection("discord")),
                guard(x.getConfigurationSection("guard")), rollback(x.getConfigurationSection("rollback")),
                falseReports(x.getConfigurationSection("false-reports")), purge(x.getConfigurationSection("purge")));
    }

    // ------------------------------------------------------------------ extras (P9: spec 14.1, 14.5, 14.10)

    /** Hard bounds of the guard window (a typo like 5h would make the guard useless or block everyone). */
    static final Duration GUARD_WINDOW_MIN = Duration.ofMinutes(1);
    static final Duration GUARD_WINDOW_MAX = Duration.ofHours(1);
    /** The longest period /prollback may ever cover (spec: max-period 7d by default; never more than 30d). */
    static final Duration ROLLBACK_MAX = Duration.ofDays(30);

    private Guard.Settings guard(ConfigurationSection s) {
        Guard.Settings d = Guard.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.guard.";
        Duration window = capped(s, "window", p + "window", d.window(), GUARD_WINDOW_MAX);
        if (window.compareTo(GUARD_WINDOW_MIN) < 0) {
            error("config.duration-too-short", p + "window", "value", String.valueOf(s.get("window")),
                    "min", PunishConfig.text(GUARD_WINDOW_MIN));
            window = GUARD_WINDOW_MIN;
        }
        Map<String, Guard.Tier> tiers = d.tiers();
        if (s.contains("tiers")) {
            ConfigurationSection t = s.getConfigurationSection("tiers");
            if (t == null) {
                error("config.not-a-section", p + "tiers", "value", "tiers");
            } else {
                Map<String, Guard.Tier> parsed = new LinkedHashMap<>();
                for (String name : t.getKeys(false)) {
                    String tp = p + "tiers." + name;
                    ConfigurationSection one = t.getConfigurationSection(name);
                    if (one == null) {
                        error("config.not-a-section", tp, "value", name);
                        continue;
                    }
                    if (!name.matches(ID_PATTERN)) {
                        error("config.bad-id", tp, "value", name);
                        continue;
                    }
                    Guard.Mode mode = Guard.Mode.BLOCK;
                    if (one.contains("mode")) {
                        String raw = String.valueOf(one.get("mode")).strip();
                        java.util.Optional<Guard.Mode> m = Guard.Mode.parse(raw);
                        if (m.isEmpty()) {
                            error("config.unknown-key", tp + ".mode", "value", raw, "suggestion",
                                    nullToEmpty(Hints.closest(raw.toLowerCase(Locale.ROOT), List.of("block", "alert"))));
                        } else {
                            mode = m.get();
                        }
                    }
                    parsed.put(name, new Guard.Tier(mode, number(one, "max-bans", tp + ".max-bans", 10, -1, 10_000),
                            number(one, "max-mutes", tp + ".max-mutes", 25, -1, 10_000),
                            number(one, "max-sanctions", tp + ".max-sanctions", 3, -1, 10_000),
                            number(one, "max-total", tp + ".max-total", 40, -1, 10_000)));
                }
                tiers = parsed;
            }
        }
        String defaultTier = s.contains("default-tier") ? String.valueOf(s.get("default-tier")).strip() : d.defaultTier();
        if (!tiers.containsKey(defaultTier)) {
            error("config.unknown-key", p + "default-tier", "value", defaultTier, "suggestion",
                    nullToEmpty(Hints.closest(defaultTier, tiers.keySet())));
            defaultTier = tiers.isEmpty() ? d.defaultTier() : tiers.keySet().iterator().next();
        }
        return new Guard.Settings(bool(s, "enabled", p + "enabled", d.enabled()), window, tiers, defaultTier,
                capped(s, "allow-default", p + "allow-default", d.allowDefault(), Duration.ofDays(1)),
                capped(s, "alert-rollback", p + "alert-rollback", d.alertRollback(), ROLLBACK_MAX));
    }

    private RollbackRules.Settings rollback(ConfigurationSection s) {
        RollbackRules.Settings d = RollbackRules.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.rollback.";
        Duration max = capped(s, "max-period", p + "max-period", d.maxPeriod(), ROLLBACK_MAX);
        Duration min = positive(s, "min-period", p + "min-period", d.minPeriod());
        if (min.compareTo(max) > 0) {
            error("config.duration-too-long", p + "min-period", "value", String.valueOf(s.get("min-period")),
                    "max", PunishConfig.text(max));
            min = d.minPeriod().compareTo(max) > 0 ? max : d.minPeriod();
        }
        return new RollbackRules.Settings(min, max,
                capped(s, "confirm-window", p + "confirm-window", d.confirmWindow(), Duration.ofMinutes(10)));
    }

    private FalseReportRules.Settings falseReports(ConfigurationSection s) {
        FalseReportRules.Settings d = FalseReportRules.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.false-reports.";
        String reason = s.contains("reason") ? String.valueOf(s.get("reason")).strip() : d.reason();
        if (!reason.matches(ID_PATTERN)) {
            error("config.bad-id", p + "reason", "value", reason);
            reason = d.reason();
        }
        return new FalseReportRules.Settings(bool(s, "enabled", p + "enabled", d.enabled()),
                number(s, "threshold", p + "threshold", d.threshold(), 1, 100),
                capped(s, "window", p + "window", d.window(), Duration.ofDays(365)),
                capped(s, "cap", p + "cap", d.cap(), Duration.ofDays(90)), reason);
    }

    private PurgeRules.Settings purge(ConfigurationSection s) {
        PurgeRules.Settings d = PurgeRules.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.purge.";
        Duration keep = d.keepLedger();
        if (s.contains("keep-ledger")) {
            String raw = String.valueOf(s.get("keep-ledger")).strip();
            if (raw.equals("0")) {
                keep = Duration.ZERO; // spec: 0 = keep nothing that is not in force
            } else {
                keep = capped(s, "keep-ledger", p + "keep-ledger", d.keepLedger(), Duration.ofDays(3650));
            }
        }
        return new PurgeRules.Settings(keep, capped(s, "confirm-window", p + "confirm-window", d.confirmWindow(),
                Duration.ofMinutes(10)));
    }

    // ------------------------------------------------------------------ extras (P8: spec 14.6, 14.9)

    private Training.Settings training(ConfigurationSection s) {
        Training.Settings d = Training.Settings.DEFAULT;
        if (s == null || !s.contains("requests-for")) {
            return d;
        }
        String p = "extras.training.requests-for";
        if (!s.isList("requests-for")) {
            error("config.bad-list", p);
            return d;
        }
        java.util.Set<Training.Kind> kinds = java.util.EnumSet.noneOf(Training.Kind.class);
        for (String raw : s.getStringList("requests-for")) {
            java.util.Optional<Training.Kind> k = Training.Kind.parse(raw);
            if (k.isEmpty()) {
                String v = raw == null ? "" : raw.strip();
                error("config.unknown-key", p, "value", v, "suggestion",
                        nullToEmpty(Hints.closest(v.toLowerCase(Locale.ROOT), Training.Kind.ids())));
            } else {
                kinds.add(k.get());
            }
        }
        return new Training.Settings(kinds);
    }

    /** {@code extras.notify.always} and {@code extras.discord.channels} (type → webhook name, off by default). */
    private NotifyRules.Settings notify(ConfigurationSection s, ConfigurationSection discord) {
        NotifyRules.Settings d = NotifyRules.Settings.DEFAULT;
        java.util.Set<NotifyRules.Type> always = d.always();
        if (s != null && s.contains("always")) {
            String p = "extras.notify.always";
            if (!s.isList("always")) {
                error("config.bad-list", p);
            } else {
                java.util.Set<NotifyRules.Type> parsed = java.util.EnumSet.noneOf(NotifyRules.Type.class);
                for (String raw : s.getStringList("always")) {
                    java.util.Optional<NotifyRules.Type> t = NotifyRules.Type.parse(raw);
                    if (t.isEmpty()) {
                        String v = raw == null ? "" : raw.strip();
                        error("config.unknown-key", p, "value", v, "suggestion",
                                nullToEmpty(Hints.closest(v.toLowerCase(Locale.ROOT), NotifyRules.Type.ids())));
                    } else {
                        parsed.add(t.get());
                    }
                }
                always = parsed;
            }
        }
        Map<NotifyRules.Type, String> channels = new java.util.EnumMap<>(NotifyRules.Type.class);
        ConfigurationSection c = discord == null ? null : discord.getConfigurationSection("channels");
        if (discord != null && discord.contains("channels") && c == null) {
            error("config.not-a-section", "extras.discord.channels", "value", "channels");
        }
        if (c != null) {
            for (String key : c.getKeys(false)) {
                String path = "extras.discord.channels." + key;
                java.util.Optional<NotifyRules.Type> t = NotifyRules.Type.parse(key);
                if (t.isEmpty()) {
                    error("config.unknown-key", path, "value", key, "suggestion",
                            nullToEmpty(Hints.closest(key.toLowerCase(Locale.ROOT), NotifyRules.Type.ids())));
                    continue;
                }
                String name = String.valueOf(c.get(key)).strip();
                if (name.startsWith("http")) {
                    error("config.webhook-in-config", path); // spec 14.9: webhook URLs are secrets, never here
                    continue;
                }
                if (!name.matches(ID_PATTERN)) {
                    error("config.bad-id", path, "value", name);
                    continue;
                }
                channels.put(t.get(), name);
            }
        }
        return new NotifyRules.Settings(always, channels);
    }

    // ------------------------------------------------------------------ extras (P7: spec 13.1)

    private SanctionRules.Settings sanctions(ConfigurationSection s) {
        SanctionRules.Settings d = SanctionRules.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.sanctions.";
        List<Duration> durations = d.durations();
        if (s.contains("durations")) {
            if (!s.isList("durations")) {
                error("config.bad-list", p + "durations");
            } else {
                List<Duration> out = new ArrayList<>();
                for (String raw : s.getStringList("durations")) {
                    String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
                    if (OFF.contains(v)) {
                        out.add(null);
                        continue;
                    }
                    try {
                        Duration len = net.skybornmc.skyblock.common.command.Durations.parse(v);
                        if (len.isZero() || len.isNegative()) {
                            throw new IllegalArgumentException(v);
                        }
                        out.add(len);
                    } catch (IllegalArgumentException e) {
                        error("config.bad-step-time", p + "durations", "value", v);
                    }
                }
                if (out.isEmpty() || out.size() > 7) {
                    error("config.bad-number", p + "durations", "value", String.valueOf(out.size()), "min", "1", "max", "7");
                } else {
                    durations = java.util.Collections.unmodifiableList(out);
                }
            }
        }
        List<String> reasons = idList(s, "reasons", p, d.reasons(), 7);
        java.util.Set<net.skybornmc.skyblock.common.moderation.SanctionService.Use> freezeBlocks = uses(s, "freeze-blocks", p, d.freezeBlocks());
        java.util.Set<net.skybornmc.skyblock.common.moderation.SanctionService.Use> tradeBlocks = uses(s, "trade-blocks", p, d.tradeBlocks());
        java.util.Set<net.skybornmc.skyblock.common.economy.Currency> currencies = d.freezeCurrencies();
        if (s.contains("freeze-currencies")) {
            if (!s.isList("freeze-currencies")) {
                error("config.bad-list", p + "freeze-currencies");
            } else {
                java.util.Set<net.skybornmc.skyblock.common.economy.Currency> out =
                        java.util.EnumSet.noneOf(net.skybornmc.skyblock.common.economy.Currency.class);
                for (String raw : s.getStringList("freeze-currencies")) {
                    java.util.Optional<net.skybornmc.skyblock.common.economy.Currency> c =
                            net.skybornmc.skyblock.common.economy.Currency.byKey(raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT));
                    if (c.isEmpty()) {
                        error("config.unknown-key", p + "freeze-currencies", "value", String.valueOf(raw), "suggestion", "money");
                    } else {
                        out.add(c.get());
                    }
                }
                currencies = java.util.Set.copyOf(out);
            }
        }
        java.util.Set<String> exempt = reasonSet(s, "freeze-exempt-reasons", p, d.freezeExempt());
        java.util.Set<String> receive = reasonSet(s, "freeze-receive-reasons", p, d.freezeReceive());
        java.util.Set<String> trade = reasonSet(s, "trade-reasons", p, d.tradeReasons());
        long moneyMax = d.moneyMax();
        if (s.contains("money-max")) {
            Object raw = s.get("money-max");
            if (!(raw instanceof Number n) || n.longValue() < 1) {
                error("config.bad-number", p + "money-max", "value", String.valueOf(raw), "min", "1", "max", String.valueOf(Long.MAX_VALUE));
            } else {
                moneyMax = n.longValue();
            }
        }
        boolean dupe = d.dupeSuggest();
        String dupeReason = d.dupeReason();
        ConfigurationSection ds = s.getConfigurationSection("dupe-suggestion");
        if (ds != null) {
            dupe = bool(ds, "enabled", p + "dupe-suggestion.enabled", dupe);
            if (ds.contains("reason")) {
                String raw = String.valueOf(ds.get("reason")).strip();
                if (!raw.matches(ID_PATTERN)) {
                    error("config.bad-id", p + "dupe-suggestion.reason", "value", raw);
                } else {
                    dupeReason = raw;
                }
            }
        }
        return new SanctionRules.Settings(bool(s, "enabled", p + "enabled", d.enabled()), durations, reasons, freezeBlocks,
                currencies, exempt, receive, tradeBlocks, trade, moneyMax, dupe, dupeReason);
    }

    private List<String> idList(ConfigurationSection s, String key, String p, List<String> fallback, int max) {
        if (!s.contains(key)) {
            return fallback;
        }
        if (!s.isList(key)) {
            error("config.bad-list", p + key);
            return fallback;
        }
        List<String> ids = new ArrayList<>();
        for (String raw : s.getStringList(key)) {
            String clean = raw == null ? "" : raw.strip();
            if (!clean.matches(ID_PATTERN)) {
                error("config.bad-id", p + key, "value", clean);
            } else if (!ids.contains(clean)) {
                ids.add(clean);
            }
        }
        if (ids.isEmpty() || ids.size() > max) {
            error("config.bad-number", p + key, "value", String.valueOf(ids.size()), "min", "1", "max", String.valueOf(max));
            return fallback;
        }
        return List.copyOf(ids);
    }

    private java.util.Set<net.skybornmc.skyblock.common.moderation.SanctionService.Use> uses(ConfigurationSection s, String key,
            String p, java.util.Set<net.skybornmc.skyblock.common.moderation.SanctionService.Use> fallback) {
        if (!s.contains(key)) {
            return fallback;
        }
        if (!s.isList(key)) {
            error("config.bad-list", p + key);
            return fallback;
        }
        java.util.Set<net.skybornmc.skyblock.common.moderation.SanctionService.Use> out =
                java.util.EnumSet.noneOf(net.skybornmc.skyblock.common.moderation.SanctionService.Use.class);
        List<String> names = java.util.Arrays.stream(net.skybornmc.skyblock.common.moderation.SanctionService.Use.values())
                .map(net.skybornmc.skyblock.common.moderation.SanctionService.Use::id).toList();
        for (String raw : s.getStringList(key)) {
            java.util.Optional<net.skybornmc.skyblock.common.moderation.SanctionService.Use> u =
                    net.skybornmc.skyblock.common.moderation.SanctionService.Use.parse(raw);
            if (u.isEmpty()) {
                String v = raw == null ? "" : raw.strip();
                error("config.unknown-key", p + key, "value", v, "suggestion",
                        nullToEmpty(Hints.closest(v.toLowerCase(Locale.ROOT), names)));
            } else {
                out.add(u.get());
            }
        }
        return java.util.Set.copyOf(out);
    }

    private java.util.Set<String> reasonSet(ConfigurationSection s, String key, String p, java.util.Set<String> fallback) {
        if (!s.contains(key)) {
            return fallback;
        }
        if (!s.isList(key)) {
            error("config.bad-list", p + key);
            return fallback;
        }
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String raw : s.getStringList(key)) {
            String v = raw == null ? "" : raw.strip();
            if (!net.skybornmc.skyblock.common.economy.TxReason.isValid(v)) {
                error("config.bad-id", p + key, "value", v);
            } else {
                out.add(v);
            }
        }
        return java.util.Set.copyOf(out);
    }

    // ------------------------------------------------------------------ extras (P6: spec 13.3, 13.9, 13.12)

    /** {@code never-auto: [hacking, xray, macro]}: reason ids (checked against the reasons in {@link #run}). */
    private List<String> neverAuto(ConfigurationSection x) {
        if (!x.contains("never-auto")) {
            return PunishConfig.Extras.NEVER_AUTO;
        }
        if (!x.isList("never-auto")) {
            error("config.bad-list", "extras.never-auto");
            return PunishConfig.Extras.NEVER_AUTO;
        }
        List<String> out = new ArrayList<>();
        for (String raw : x.getStringList("never-auto")) {
            String id = raw == null ? "" : raw.strip();
            if (!id.matches(ID_PATTERN)) {
                error("config.bad-id", "extras.never-auto", "value", id);
            } else if (!out.contains(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    private Lockdown.Settings lockdown(ConfigurationSection s) {
        Lockdown.Settings d = Lockdown.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.lockdown.";
        Duration playtime = positive(s, "new-player-playtime", p + "new-player-playtime", d.newPlayerPlaytime());
        Duration age = positive(s, "new-player-age", p + "new-player-age", d.newPlayerAge());
        // Safety caps: a chat lockdown never longer than 6h, a join lockdown never longer than 1h (spec: 60m / 15m).
        Duration chatMax = capped(s, "chat-max", p + "chat-max", d.chatMax(), Duration.ofHours(6));
        Duration joinMax = capped(s, "join-max", p + "join-max", d.joinMax(), Duration.ofHours(1));
        Duration chatDefault = capped(s, "chat-default", p + "chat-default", d.chatDefault(), chatMax);
        Duration joinDefault = capped(s, "join-default", p + "join-default", d.joinDefault(), joinMax);
        Duration reminder = positive(s, "staff-reminder", p + "staff-reminder", d.staffReminder());
        if (reminder.compareTo(Duration.ofSeconds(10)) < 0) {
            error("config.bad-duration", p + "staff-reminder", "value", String.valueOf(s.get("staff-reminder")));
            reminder = d.staffReminder();
        }
        List<String> blocked = d.blockedCommands();
        if (s.contains("blocked-commands")) {
            if (!s.isList("blocked-commands")) {
                error("config.bad-list", p + "blocked-commands");
            } else {
                List<String> out = new ArrayList<>();
                for (String raw : s.getStringList("blocked-commands")) {
                    String label = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
                    if (label.startsWith("/")) {
                        label = label.substring(1);
                    }
                    if (!label.matches("[a-z0-9_-]+")) {
                        error("config.bad-id", p + "blocked-commands", "value", label);
                    } else if (!out.contains(label)) {
                        out.add(label);
                    }
                }
                blocked = List.copyOf(out);
            }
        }
        return new Lockdown.Settings(playtime, age, chatDefault, chatMax, joinDefault, joinMax, reminder, blocked);
    }

    /** A positive duration that may not be longer than {@code max}. */
    private Duration capped(ConfigurationSection s, String key, String path, Duration fallback, Duration max) {
        Duration d = positive(s, key, path, fallback);
        if (d.compareTo(max) > 0) {
            error("config.duration-too-long", path, "value", String.valueOf(s.get(key)), "max", PunishConfig.text(max));
            return fallback.compareTo(max) > 0 ? max : fallback;
        }
        return d;
    }

    private Lockdown.Raid raid(ConfigurationSection s) {
        Lockdown.Raid d = Lockdown.Raid.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.raid.";
        Duration window = capped(s, "window", p + "window", d.window(), Duration.ofMinutes(10));
        // spec: an automatic lockdown is at most 5 minutes, at most once per 30 minutes
        Duration autoDuration = capped(s, "auto-duration", p + "auto-duration", d.autoDuration(), Lockdown.AUTO_MAX);
        Duration autoCooldown = positive(s, "auto-cooldown", p + "auto-cooldown", d.autoCooldown());
        if (autoCooldown.compareTo(Lockdown.AUTO_COOLDOWN_MIN) < 0) {
            error("config.duration-too-short", p + "auto-cooldown", "value", String.valueOf(s.get("auto-cooldown")),
                    "min", PunishConfig.text(Lockdown.AUTO_COOLDOWN_MIN));
            autoCooldown = Lockdown.AUTO_COOLDOWN_MIN;
        }
        return new Lockdown.Raid(bool(s, "detect", p + "detect", d.detect()),
                number(s, "accounts", p + "accounts", d.accounts(), 2, 1000), window,
                bool(s, "auto-lock", p + "auto-lock", d.autoLock()), autoDuration, autoCooldown);
    }

    private NetworkScope.Settings network(ConfigurationSection s) {
        NetworkScope.Settings d = NetworkScope.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.network.";
        String serverId = s.contains("server-id") ? String.valueOf(s.get("server-id")).strip() : d.serverId();
        if (!serverId.matches(ID_PATTERN) || serverId.length() > 32) {
            error("config.bad-id", p + "server-id", "value", serverId);
            serverId = d.serverId();
        }
        String scope = d.defaultScope();
        if (s.contains("default-scope")) {
            String raw = String.valueOf(s.get("default-scope")).strip();
            java.util.Optional<String> parsed = NetworkScope.parseScope(raw);
            if (parsed.isEmpty()) {
                error("config.unknown-key", p + "default-scope", "value", raw, "suggestion",
                        nullToEmpty(Hints.closest(raw.toLowerCase(Locale.ROOT), NetworkScope.SCOPES)));
            } else {
                scope = parsed.get();
            }
        }
        return new NetworkScope.Settings(bool(s, "enabled", p + "enabled", d.enabled()), serverId, scope);
    }

    // ------------------------------------------------------------------ extras (P5: spec 13.8, 13.10)

    private PunishConfig.Bulk bulk(ConfigurationSection s) {
        PunishConfig.Bulk d = PunishConfig.Bulk.DEFAULT;
        if (s == null) {
            return d;
        }
        return new PunishConfig.Bulk(number(s, "max", "extras.bulk.max", d.max(), 2, 50));
    }

    private PunishConfig.Appeals appeal(ConfigurationSection s) {
        PunishConfig.Appeals d = PunishConfig.Appeals.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.appeal.";
        java.util.Set<Punishment.Type> types = d.types();
        if (s.contains("appealable-types")) {
            if (!s.isList("appealable-types")) {
                error("config.bad-list", p + "appealable-types");
            } else {
                java.util.Set<Punishment.Type> parsed = java.util.EnumSet.noneOf(Punishment.Type.class);
                for (String raw : s.getStringList("appealable-types")) {
                    String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
                    if (!APPEAL_TYPES.contains(value)) {
                        error("config.unknown-key", p + "appealable-types", "value", value, "suggestion",
                                nullToEmpty(Hints.closest(value, APPEAL_TYPES)));
                        continue;
                    }
                    parsed.add(Punishment.Type.valueOf(value.toUpperCase(Locale.ROOT)));
                }
                types = java.util.Set.copyOf(parsed);
            }
        }
        int min = number(s, "min-length", p + "min-length", d.minLength(), 1, 200);
        int max = number(s, "max-length", p + "max-length", d.maxLength(), 20, 600);
        if (min > max) {
            error("config.bad-number", p + "min-length", "value", String.valueOf(min), "min", "1", "max", String.valueOf(max));
            min = Math.min(d.minLength(), max);
        }
        List<String> deny = ids(s, "deny-reasons", p + "deny-reasons", d.denyReasons());
        boolean discordOn = d.discordEnabled();
        String channel = d.discordChannel();
        ConfigurationSection ds = s.getConfigurationSection("discord");
        if (ds != null) {
            if (ds.contains("webhook") || ds.contains("url") || ds.contains("webhook-url")) {
                error("config.webhook-in-config", p + "discord." + (ds.contains("webhook") ? "webhook" : ds.contains("url") ? "url" : "webhook-url"));
            }
            discordOn = bool(ds, "enabled", p + "discord.enabled", discordOn);
            String raw = ds.contains("channel") ? String.valueOf(ds.get("channel")).strip() : channel;
            if (!raw.matches(ID_PATTERN)) {
                error("config.bad-id", p + "discord.channel", "value", raw);
            } else {
                channel = raw;
            }
        }
        return new PunishConfig.Appeals(bool(s, "enabled", p + "enabled", d.enabled()), types,
                positive(s, "cooldown", p + "cooldown", d.cooldown()), min, max,
                positive(s, "reminder", p + "reminder", d.reminder()),
                number(s, "reduce-max-days", p + "reduce-max-days", d.reduceMaxDays(), 1, 3650), deny, discordOn, channel);
    }

    /** Types an appeal can be about (kicks are over at once, so never). */
    private static final List<String> APPEAL_TYPES = List.of("mute", "ban", "warn");

    /** A list of up to 6 ids (deny reason buttons). */
    private List<String> ids(ConfigurationSection s, String key, String path, List<String> fallback) {
        if (!s.contains(key)) {
            return fallback;
        }
        if (!s.isList(key)) {
            error("config.bad-list", path);
            return fallback;
        }
        List<String> ids = new ArrayList<>();
        for (String id : s.getStringList(key)) {
            String clean = id == null ? "" : id.strip();
            if (!clean.matches(ID_PATTERN)) {
                error("config.bad-id", path, "value", clean);
            } else if (!ids.contains(clean)) {
                ids.add(clean);
            }
        }
        if (ids.size() > 6) {
            error("config.bad-number", path, "value", String.valueOf(ids.size()), "min", "0", "max", "6");
            ids = ids.subList(0, 6);
        }
        return List.copyOf(ids);
    }

    // ------------------------------------------------------------------ extras (P4: spec 13.4, 13.5, 13.7, 13.13)

    private Approval.Settings approval(ConfigurationSection s) {
        Approval.Settings d = Approval.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.approval.";
        boolean enabled = bool(s, "enabled", p + "enabled", d.enabled());
        Duration expire = positive(s, "expire", p + "expire", d.expire());
        boolean self = bool(s, "self-approve", p + "self-approve", d.selfApprove());
        List<Approval.Rule> rules = d.rules();
        if (s.contains("rules")) {
            if (!s.isList("rules")) {
                error("config.bad-approval-rules", p + "rules");
            } else {
                List<Approval.Rule> parsed = new ArrayList<>();
                for (String raw : s.getStringList("rules")) {
                    try {
                        parsed.add(Approval.Rule.parse(raw));
                    } catch (IllegalArgumentException e) {
                        String value = raw == null ? "" : raw.strip();
                        String kind = value.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                        error("config.bad-approval-rule", p + "rules", "value", value, "suggestion",
                                Approval.Rule.BAD_KIND.equals(e.getMessage())
                                        ? nullToEmpty(Hints.closest(kind, Approval.Rule.KINDS)) : "");
                    }
                }
                rules = List.copyOf(parsed);
            }
        }
        List<String> deny = d.denyReasons();
        if (s.contains("deny-reasons")) {
            if (!s.isList("deny-reasons")) {
                error("config.bad-list", p + "deny-reasons");
            } else {
                List<String> ids = new ArrayList<>();
                for (String id : s.getStringList("deny-reasons")) {
                    String clean = id == null ? "" : id.strip();
                    if (!clean.matches(ID_PATTERN)) {
                        error("config.bad-id", p + "deny-reasons", "value", clean);
                    } else if (!ids.contains(clean)) {
                        ids.add(clean);
                    }
                }
                if (ids.size() > 6) {
                    error("config.bad-number", p + "deny-reasons", "value", String.valueOf(ids.size()), "min", "0", "max", "6");
                    ids = ids.subList(0, 6);
                }
                deny = List.copyOf(ids);
            }
        }
        return new Approval.Settings(enabled, expire, self, rules, deny);
    }

    private PunishConfig.WatchSettings watchlist(ConfigurationSection s) {
        PunishConfig.WatchSettings d = PunishConfig.WatchSettings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.watchlist.";
        Duration expire = duration(s, "expire", p + "expire", d.expire(), true);
        if (expire != null && (expire.isZero() || expire.isNegative())) {
            error("config.bad-duration", p + "expire", "value", String.valueOf(s.get("expire")));
            expire = d.expire();
        }
        return new PunishConfig.WatchSettings(expire, number(s, "reason-max-length", p + "reason-max-length",
                d.reasonMaxLength(), 10, 200));
    }

    private Probation.Settings probation(ConfigurationSection s) {
        Probation.Settings d = Probation.Settings.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.probation.";
        return new Probation.Settings(bool(s, "enabled", p + "enabled", d.enabled()),
                positive(s, "duration", p + "duration", d.duration()),
                number(s, "step-bonus", p + "step-bonus", d.stepBonus(), 0, 5));
    }

    private PunishConfig.MuteEnded muteEnded(ConfigurationSection s) {
        PunishConfig.MuteEnded d = PunishConfig.MuteEnded.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.mute-ended.";
        String sound = s.contains("sound") ? String.valueOf(s.get("sound")).strip() : d.sound();
        if (!sound.isEmpty() && !sound.matches(ID_PATTERN)) {
            error("config.bad-id", p + "sound", "value", sound);
            sound = d.sound();
        }
        return new PunishConfig.MuteEnded(bool(s, "enabled", p + "enabled", d.enabled()), sound);
    }

    // ------------------------------------------------------------------ extras (P3: spec 12.4-12.12)

    private PunishConfig.Evidence evidence(ConfigurationSection s) {
        PunishConfig.Evidence d = PunishConfig.Evidence.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.evidence.";
        return new PunishConfig.Evidence(bool(s, "enabled", p + "enabled", d.enabled()),
                number(s, "chat-lines", p + "chat-lines", d.chatLines(), 1, 30),
                number(s, "context-lines", p + "context-lines", d.contextLines(), 0, 30),
                positive(s, "retention", p + "retention", d.retention()),
                number(s, "max-line-length", p + "max-line-length", d.maxLineLength(), 20, 256),
                number(s, "max-chars", p + "max-chars", d.maxChars(), 200, 60000));
    }

    private PunishConfig.Notes notes(ConfigurationSection s) {
        PunishConfig.Notes d = PunishConfig.Notes.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.notes.";
        return new PunishConfig.Notes(number(s, "max-length", p + "max-length", d.maxLength(), 10, 255),
                number(s, "shown", p + "shown", d.shown(), 0, 10), number(s, "cut", p + "cut", d.cut(), 10, 100));
    }

    private PunishConfig.Ack ack(ConfigurationSection s) {
        PunishConfig.Ack d = PunishConfig.Ack.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.ack.";
        return new PunishConfig.Ack(positive(s, "repeat", p + "repeat", d.repeat()),
                number(s, "max-repeats", p + "max-repeats", d.maxRepeats(), 0, 20),
                bool(s, "block-until-ack", p + "block-until-ack", d.blockUntilAck()),
                bool(s, "bedrock-menu", p + "bedrock-menu", d.bedrockMenu()));
    }

    private PunishConfig.Discord discord(ConfigurationSection s) {
        PunishConfig.Discord d = PunishConfig.Discord.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.discord.";
        if (s.contains("webhook") || s.contains("url") || s.contains("webhook-url")) {
            // spec 12.11: the URL is a secret and never belongs in this tracked file
            error("config.webhook-in-config", p + (s.contains("webhook") ? "webhook" : s.contains("url") ? "url" : "webhook-url"));
        }
        String channel = s.contains("channel") ? String.valueOf(s.get("channel")).strip() : d.channel();
        if (!channel.matches(ID_PATTERN)) {
            error("config.bad-id", p + "channel", "value", channel);
            channel = d.channel();
        }
        Map<String, Integer> colors = new LinkedHashMap<>(d.colors());
        ConfigurationSection c = s.getConfigurationSection("colors");
        if (c != null) {
            for (String kind : c.getKeys(false)) {
                String path = p + "colors." + kind;
                if (!PunishConfig.Discord.KINDS.contains(kind)) {
                    error("config.unknown-key", path, "value", kind, "suggestion",
                            nullToEmpty(Hints.closest(kind, PunishConfig.Discord.KINDS)));
                    continue;
                }
                String raw = String.valueOf(c.get(kind)).strip();
                Integer rgb = color(raw);
                if (rgb == null) {
                    error("config.bad-color", path, "value", raw);
                } else {
                    colors.put(kind, rgb);
                }
            }
        }
        return new PunishConfig.Discord(bool(s, "enabled", p + "enabled", d.enabled()), channel,
                number(s, "queue-size", p + "queue-size", d.queueSize(), 1, 1000),
                number(s, "max-attempts", p + "max-attempts", d.maxAttempts(), 1, 10), Map.copyOf(colors));
    }

    /** {@code "#e74c3c"} → 0xE74C3C, or {@code null}. */
    static Integer color(String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return Integer.parseInt(hex, 16);
    }

    private PunishConfig.PlayerView playerView(ConfigurationSection s) {
        PunishConfig.PlayerView d = PunishConfig.PlayerView.DEFAULT;
        if (s == null) {
            return d;
        }
        String p = "extras.player-view.";
        return new PunishConfig.PlayerView(bool(s, "show-staff", p + "show-staff", d.showStaff()),
                number(s, "last-entries", p + "last-entries", d.lastEntries(), 1, 20));
    }

    private Map<String, PunishConfig.ChatSuggestion> chatSuggestions(ConfigurationSection s) {
        if (s == null) {
            return Map.of();
        }
        Map<String, PunishConfig.ChatSuggestion> out = new LinkedHashMap<>();
        for (String name : s.getKeys(false)) {
            String path = "extras.chat-suggestions." + name;
            ConfigurationSection c = s.getConfigurationSection(name);
            if (c == null) {
                error("config.not-a-section", path, "value", name);
                continue;
            }
            checkId(name, path);
            int hits = number(c, "hits", path + ".hits", 3, 1, 100);
            Duration within = positive(c, "within", path + ".within", Duration.ofSeconds(10));
            String reason = c.contains("reason") ? String.valueOf(c.get("reason")).strip() : "";
            if (reason.isEmpty()) {
                error("config.missing-key", path + ".reason", "key", "reason");
                continue;
            }
            out.put(name, new PunishConfig.ChatSuggestion(hits, within, reason));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** A whole number key between {@code min} and {@code max}. */
    private int number(ConfigurationSection s, String key, String path, int fallback, int min, int max) {
        if (!s.contains(key)) {
            return fallback;
        }
        Object raw = s.get(key);
        if (!(raw instanceof Integer n) || n < min || n > max) {
            error("config.bad-number", path, "value", String.valueOf(raw), "min", String.valueOf(min), "max", String.valueOf(max));
            return fallback;
        }
        return n;
    }

    /** A duration that must be more than zero ({@code perm} not allowed). */
    private Duration positive(ConfigurationSection s, String key, String path, Duration fallback) {
        Duration d = duration(s, key, path, fallback, false);
        if (d == null || d.isZero() || d.isNegative()) {
            error("config.bad-duration", path, "value", String.valueOf(s.get(key)));
            return fallback;
        }
        return d;
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
