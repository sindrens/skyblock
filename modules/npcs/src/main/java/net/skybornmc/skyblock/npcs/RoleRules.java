package net.skybornmc.skyblock.npcs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * NPC roles and what clicking them does (pure, unit-tested). Parsed from {@code npcs.yml}: {@code npcs.roles}
 * ({@code "id: role"}) and {@code npcs.actions} ({@code "role: command <cmd>"} or {@code "role: help [cmd]"}).
 * Every role has a help text (owner answer 44: right-click NPCs to get things explained); an action role also runs its
 * command on a normal click.
 */
final class RoleRules {

    /** What a click does. */
    enum Type { COMMAND, HELP }

    /** A role's action; {@code command} is without slash, empty = none (help only, no [Open] button). */
    record Action(String role, Type type, String command) {

        boolean hasCommand() {
            return !command.isEmpty();
        }
    }

    /** What to do for one click. */
    enum Decision { RUN, HELP }

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");
    /** Commands: a name and plain arguments, no slash, no colon (namespaced commands) and no control characters. */
    private static final Pattern COMMAND = Pattern.compile("[a-z0-9_-]+( [A-Za-z0-9_.-]+){0,4}");

    private final Map<String, String> roles;
    private final Map<String, Action> actions;
    private final List<String> problems;

    private RoleRules(Map<String, String> roles, Map<String, Action> actions, List<String> problems) {
        this.roles = Map.copyOf(roles);
        this.actions = Map.copyOf(actions);
        this.problems = List.copyOf(problems);
    }

    static RoleRules parse(List<String> npcRoles, List<String> roleActions) {
        List<String> problems = new ArrayList<>();
        Map<String, String> roles = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : pairs("npcs.roles", npcRoles, problems).entrySet()) {
            String role = e.getValue().toLowerCase(Locale.ROOT);
            if (!ID.matcher(role).matches()) {
                problems.add("npcs.yml -> npcs.roles: '" + e.getKey() + "' has an invalid role '" + e.getValue() + "' (skipped)");
                continue;
            }
            roles.put(e.getKey(), role);
        }
        Map<String, Action> actions = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : pairs("npcs.actions", roleActions, problems).entrySet()) {
            String[] parts = e.getValue().trim().split("\\s+", 2);
            String type = parts[0].toLowerCase(Locale.ROOT);
            String command = parts.length > 1 ? parts[1].trim() : "";
            if (command.startsWith("/")) {
                command = command.substring(1).trim();
            }
            if (!command.isEmpty() && !COMMAND.matcher(command).matches()) {
                problems.add("npcs.yml -> npcs.actions: '" + e.getKey() + "' has an invalid command '" + command + "' (skipped)");
                continue;
            }
            switch (type) {
                case "command" -> {
                    if (command.isEmpty()) {
                        problems.add("npcs.yml -> npcs.actions: '" + e.getKey() + "' says command but names none (skipped)");
                        continue;
                    }
                    actions.put(e.getKey(), new Action(e.getKey(), Type.COMMAND, command));
                }
                case "help" -> actions.put(e.getKey(), new Action(e.getKey(), Type.HELP, command));
                default -> problems.add("npcs.yml -> npcs.actions: '" + e.getKey() + "' has an unknown type '" + parts[0]
                        + "' (use command or help; skipped)");
            }
        }
        return new RoleRules(roles, actions, problems);
    }

    /**
     * The role of an NPC: the {@code role} key of its build metadata when set, else {@code npcs.roles}, else the id.
     */
    String roleOf(String npcId, String metadataRole) {
        if (metadataRole != null && !metadataRole.isBlank()) {
            String role = metadataRole.trim().toLowerCase(Locale.ROOT);
            if (ID.matcher(role).matches()) {
                return role;
            }
        }
        String id = npcId.toLowerCase(Locale.ROOT);
        return roles.getOrDefault(id, id);
    }

    /** The action of a role (help-only when none is configured). */
    Action actionOf(String role) {
        Action a = actions.get(role);
        return a != null ? a : new Action(role, Type.HELP, "");
    }

    /** Run the command, or show help (help-only roles, or sneak-click when {@code sneakForHelp}). */
    static Decision decide(Action action, boolean sneaking, boolean sneakForHelp) {
        if (action.type() == Type.COMMAND && !(sneaking && sneakForHelp)) {
            return Decision.RUN;
        }
        return Decision.HELP;
    }

    List<String> problems() {
        return problems;
    }

    Map<String, Action> actions() {
        return actions;
    }

    /** {@code "key: value"} lines → map (keys lowercase); broken lines become problems. */
    static Map<String, String> pairs(String setting, List<String> lines, List<String> problems) {
        Map<String, String> out = new LinkedHashMap<>();
        if (lines == null) {
            return out;
        }
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int colon = raw.indexOf(':');
            String key = colon < 0 ? "" : raw.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : raw.substring(colon + 1).trim();
            if (!ID.matcher(key).matches() || value.isEmpty()) {
                problems.add("npcs.yml -> " + setting + ": '" + raw + "' is not 'id: value' (skipped)");
                continue;
            }
            if (out.put(key, value) != null) {
                problems.add("npcs.yml -> " + setting + ": '" + key + "' is listed twice (the last line wins)");
            }
        }
        return out;
    }
}
