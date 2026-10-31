package net.skybornmc.skyblock.menus;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The buttons of the /menu hub ({@code menu.yml → hub}) and the You menu ({@code menu.yml → you}), GUI_DESIGN §7.1-7.2.
 * Texts: {@code messages/navigation.yml → hub.buttons.<id>} / {@code you.buttons.<id>}. Pure parsing and rules
 * (unit-tested); the menus resolve services and commands when they open.
 *
 * <p>Owner G2: features that are not built are <b>hidden</b> on the live server. A button shows when its {@code show}
 * flag is on, the service it names is registered (the module is enabled) and its command exists. The test server can
 * show the hidden ones in the "Coming soon" look with {@code navigation.yml → hub.show-unbuilt}.</p>
 */
public record ButtonLayout(List<Button> buttons, Map<String, String> groups) {

    /** Built-in screens and actions (not server commands). */
    public static final String YOU = "you";
    public static final String TRAVEL = "travel";
    public static final String REWARDS = "rewards";
    public static final String SETTINGS = "settings";
    public static final String HELP = "help";
    /** Runs {@link Button#command()} as the player. */
    public static final String COMMAND = "command";
    static final Set<String> ACTIONS = Set.of(YOU, TRAVEL, REWARDS, SETTINGS, HELP, COMMAND);

    /** What a button looks like for a viewer. */
    public enum State { HIDDEN, COMING_SOON, ACTIVE }

    /**
     * @param icon       item id (checked when parsed; {@link Nav#material} turns it into the item)
     * @param group      hub group ({@code play}, {@code money}, {@code power}, {@code more}); null in the You menu and for the head
     * @param action     a built-in ({@link #ACTIONS}) or {@link #COMMAND}
     * @param command    the command a click runs (without /) for {@link #COMMAND}; for {@link #REWARDS} the screen it
     *                   opens when nothing is ready ({@code daily})
     * @param service    optional simple name of the common service the feature provides (e.g. {@code SellService}):
     *                   without it registered, the feature counts as not built
     * @param show       false hides the button everywhere (also on the test server)
     * @param permission optional: players without it see the button locked
     */
    public record Button(String id, int slot, String group, String icon, String action, String command, String service,
                         boolean show, String permission) {

        /** First word of the command (the label that must exist), or empty. */
        public String label() {
            return command == null || command.isBlank() ? "" : command.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        }

        /** True if every part the button needs exists: its service and its command (built-in screens need none). */
        public boolean available(Predicate<String> hasService, Predicate<String> hasCommand) {
            if (service != null && !service.isBlank() && !hasService.test(service.strip())) {
                return false;
            }
            boolean needsCommand = COMMAND.equals(action) || (REWARDS.equals(action) && !label().isEmpty());
            return !needsCommand || (!label().isEmpty() && hasCommand.test(label()));
        }

        public State state(Predicate<String> hasService, Predicate<String> hasCommand, boolean showUnbuilt) {
            return ButtonLayout.state(show, available(hasService, hasCommand), showUnbuilt);
        }

        public boolean allowed(Player player) {
            return permission == null || permission.isBlank() || player.hasPermission(permission);
        }

        /** What makes two buttons do the same thing (a built-in, or the command text). */
        String actionKey() {
            return COMMAND.equals(action) ? "command:" + String.join(" ", command.strip().toLowerCase(Locale.ROOT).split("\\s+")) : action;
        }
    }

    /** Hidden unless shown and built; unbuilt ones show as "Coming soon" only with the test-server switch. */
    static State state(boolean show, boolean available, boolean showUnbuilt) {
        if (!show) {
            return State.HIDDEN;
        }
        if (available) {
            return State.ACTIVE;
        }
        return showUnbuilt ? State.COMING_SOON : State.HIDDEN;
    }

    /** Hub slots: the 28 inner slots of the 6-row menu plus 4 (your head). */
    static final Set<Integer> HUB_SLOTS = hubSlots();
    /** 3-row menus (You, Travel): the middle row, 10-16. */
    static final Set<Integer> SMALL_SLOTS = Set.of(10, 11, 12, 13, 14, 15, 16, 20, 24);

    /** True if {@code id} is an item (server only: needs the item registry). */
    static boolean isItem(String id) {
        Material m = Material.matchMaterial(id == null ? "" : id.strip());
        return m != null && m.isItem();
    }

    /** The hub ({@code hub}, groups from {@code hub-groups}). */
    static ButtonLayout hub(YamlConfiguration yaml, Consumer<String> problem) {
        return hub(yaml, ButtonLayout::isItem, problem);
    }

    static ButtonLayout hub(YamlConfiguration yaml, Predicate<String> items, Consumer<String> problem) {
        Map<String, String> groups = groups(yaml, items, problem);
        return parse(yaml, "hub", HUB_SLOTS, groups, items, problem);
    }

    /** The You menu ({@code you}). */
    static ButtonLayout you(YamlConfiguration yaml, Consumer<String> problem) {
        return you(yaml, ButtonLayout::isItem, problem);
    }

    static ButtonLayout you(YamlConfiguration yaml, Predicate<String> items, Consumer<String> problem) {
        return parse(yaml, "you", SMALL_SLOTS, Map.of(), items, problem);
    }

    static ButtonLayout parse(YamlConfiguration yaml, String sectionName, Set<Integer> slots, Map<String, String> groups,
                              Predicate<String> items, Consumer<String> problem) {
        List<Button> out = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        Map<String, String> actions = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection(sectionName);
        if (section == null) {
            return new ButtonLayout(List.of(), java.util.Collections.unmodifiableMap(new LinkedHashMap<>(groups)));
        }
        for (String id : section.getKeys(false)) {
            String where = "menu.yml → " + sectionName + "." + id;
            ConfigurationSection e = section.getConfigurationSection(id);
            if (e == null) {
                problem.accept(where + ": needs slot, icon and command or action (skipped)");
                continue;
            }
            int slot = e.getInt("slot", -1);
            if (!slots.contains(slot)) {
                problem.accept(where + ".slot: " + slot + " is not a free slot of this menu (skipped)");
                continue;
            }
            if (!used.add(slot)) {
                problem.accept(where + ".slot: slot " + slot + " is already used (skipped)");
                continue;
            }
            String action = e.getString("action", COMMAND).strip().toLowerCase(Locale.ROOT);
            String command = e.getString("command", "").strip();
            if (command.startsWith("/")) {
                command = command.substring(1).strip();
            }
            if (!ACTIONS.contains(action)) {
                problem.accept(where + ".action: '" + action + "' is not one of " + new java.util.TreeSet<>(ACTIONS) + " (skipped)");
                used.remove(slot);
                continue;
            }
            if (COMMAND.equals(action) && command.isEmpty()) {
                problem.accept(where + ": needs a command (skipped)");
                used.remove(slot);
                continue;
            }
            String icon = e.getString("icon", "book").strip().toLowerCase(Locale.ROOT);
            if (!items.test(icon)) {
                problem.accept(where + ".icon: unknown item '" + icon + "' (using book)");
                icon = "book";
            }
            String group = e.getString("group");
            if (group != null) {
                group = group.strip().toLowerCase(Locale.ROOT);
                if (!groups.containsKey(group)) {
                    problem.accept(where + ".group: '" + group + "' is not in hub-groups (no group mark)");
                    group = null;
                }
            }
            Button button = new Button(id, slot, group, icon, action, command, e.getString("service"), e.getBoolean("show", true),
                    e.getString("permission"));
            String other = actions.putIfAbsent(button.actionKey(), id);
            if (other != null) {
                problem.accept(where + ": does the same as '" + other + "' (skipped; one button per action)");
                used.remove(slot);
                continue;
            }
            out.add(button);
        }
        return new ButtonLayout(List.copyOf(out), java.util.Collections.unmodifiableMap(new LinkedHashMap<>(groups)));
    }

    /** {@code hub-groups: <id>: {mark: <pane>}} in order. */
    static Map<String, String> groups(YamlConfiguration yaml, Predicate<String> items, Consumer<String> problem) {
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("hub-groups");
        if (section == null) {
            return out;
        }
        for (String id : section.getKeys(false)) {
            String mark = section.getString(id + ".mark", "gray_stained_glass_pane").strip().toLowerCase(Locale.ROOT);
            if (!items.test(mark)) {
                problem.accept("menu.yml → hub-groups." + id + ".mark: unknown item '" + mark + "' (using gray glass pane)");
                mark = "gray_stained_glass_pane";
            }
            out.put(id.toLowerCase(Locale.ROOT), mark);
        }
        return out;
    }

    /** Where a group's mark goes: the left edge of the row of its first button (9, 18, 27, 36), or -1. */
    static int markSlot(List<Button> visibleOfGroup) {
        int first = visibleOfGroup.stream().mapToInt(Button::slot).filter(s -> s >= 9 && s < 45).min().orElse(-1);
        return first < 0 ? -1 : (first / 9) * 9;
    }

    private static Set<Integer> hubSlots() {
        Set<Integer> set = new HashSet<>();
        for (int slot = 0; slot < 54; slot++) {
            if (!HubLayout.RESERVED.contains(slot)) {
                set.add(slot);
            }
        }
        set.add(HubMenu.SLOT_PROFILE);
        return Set.copyOf(set);
    }
}
