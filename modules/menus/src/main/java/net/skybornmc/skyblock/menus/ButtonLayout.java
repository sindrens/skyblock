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
 * The buttons of the /menu hub ({@code menu.yml → hub}) and the You menu ({@code menu.yml → you}), G9 v3 / I9 v2.
 * Hub buttons have fixed slots; You entries are placed by {@code Centered} in file order (their {@code slot} is ignored).
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
    /** The Top screen (GUI-4c, GUI_DESIGN §7.6): island, weekly, money and skill boards. */
    public static final String TOP = "top";
    /** The Staff tile (G9): opens {@code /staff}; only players with its permission see it. */
    public static final String STAFF = "staff";
    /** Runs {@link Button#command()} as the player. */
    public static final String COMMAND = "command";
    static final Set<String> ACTIONS = Set.of(YOU, TRAVEL, REWARDS, SETTINGS, HELP, TOP, STAFF, COMMAND);
    /** The area colour names of style.yml (I9 section 8); a hub group takes one of them. */
    static final Set<String> AREAS = Set.of("money", "reward", "move", "depths", "skills", "people", "home", "danger", "neutral", "locked");
    /** Colour of a group whose {@code color} is missing or unknown. */
    static final String DEFAULT_AREA = "neutral";

    /** What a button looks like for a viewer. */
    public enum State { HIDDEN, COMING_SOON, ACTIVE }

    /**
     * @param slot       hub slot; -1 in the You menu (placed by {@code Centered})
     * @param icon       an icon key of the catalogue ({@code feature/home}) or a vanilla item id (plain item for everyone)
     * @param group      hub group ({@code play}, {@code money}, {@code power}, {@code more}); null in the You menu and for the head
     * @param action     a built-in ({@link #ACTIONS}) or {@link #COMMAND}
     * @param command    the command a click runs (without /) for {@link #COMMAND}; for {@link #REWARDS} the screen it
     *                   opens when nothing is ready ({@code daily})
     * @param service    optional simple name of the common service the feature provides (e.g. {@code SellService}):
     *                   without it registered, the feature counts as not built
     * @param show       false hides the button everywhere (also on the test server)
     * @param permission optional: players without it see the button locked (or not at all, see {@code showLocked})
     * @param showLocked false: a player without the permission does not see the button at all (the Staff tile)
     */
    public record Button(String id, int slot, String group, String icon, String action, String command, String service,
                         boolean show, String permission, boolean showLocked) {

        public Button(String id, int slot, String group, String icon, String action, String command, String service,
                      boolean show, String permission) {
            this(id, slot, group, icon, action, command, service, show, permission, true);
        }

        /** True when {@code player} must not see this button at all (no permission and {@code show-locked: false}). */
        public boolean hiddenFor(Player player) {
            return !showLocked && !allowed(player);
        }

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

    /** Hub slots: the 28 inner slots of the 6-row menu, 4 (your head) and 8 (the Staff tile corner). */
    static final Set<Integer> HUB_SLOTS = hubSlots();

    /** True if {@code id} is an item (server only: needs the item registry). */
    static boolean isItem(String id) {
        Material m = Material.matchMaterial(id == null ? "" : id.strip());
        return m != null && m.isItem();
    }

    /** The hub ({@code hub}, groups from {@code hub-groups}). */
    static ButtonLayout hub(YamlConfiguration yaml, Consumer<String> problem) {
        return hub(yaml, ButtonLayout::isItem, problem);
    }

    static ButtonLayout hub(YamlConfiguration yaml, Predicate<String> icons, Consumer<String> problem) {
        return hub(yaml, icons, AREAS, problem);
    }

    /**
     * @param icons a valid {@code icon}: a vanilla item id or an icon catalogue key
     * @param areas the area colour names a group may take ({@code style.yml → area})
     */
    static ButtonLayout hub(YamlConfiguration yaml, Predicate<String> icons, Set<String> areas, Consumer<String> problem) {
        Map<String, String> groups = groups(yaml, areas, problem);
        return parse(yaml, "hub", HUB_SLOTS, groups, icons, problem);
    }

    /** The You menu ({@code you}): no slots, placed by {@code Centered} in file order. */
    static ButtonLayout you(YamlConfiguration yaml, Consumer<String> problem) {
        return you(yaml, ButtonLayout::isItem, problem);
    }

    static ButtonLayout you(YamlConfiguration yaml, Predicate<String> icons, Consumer<String> problem) {
        return parse(yaml, "you", null, Map.of(), icons, problem);
    }

    /**
     * @param slots the allowed slots, or {@code null} for a menu that places its entries itself ({@code slot} ignored)
     */
    static ButtonLayout parse(YamlConfiguration yaml, String sectionName, Set<Integer> slots, Map<String, String> groups,
                              Predicate<String> items, Consumer<String> problem) {
        List<Button> out = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        boolean slotsIgnored = false;
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
            int slot = -1;
            if (slots == null) {
                slotsIgnored |= e.contains("slot");
            } else {
                slot = e.getInt("slot", -1);
                if (!slots.contains(slot)) {
                    problem.accept(where + ".slot: " + slot + " is not a free slot of this menu (skipped)");
                    continue;
                }
                if (!used.add(slot)) {
                    problem.accept(where + ".slot: slot " + slot + " is already used (skipped)");
                    continue;
                }
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
                problem.accept(where + ".icon: '" + icon + "' is neither an item id nor an icon key (using book)");
                icon = "book";
            }
            String group = e.getString("group");
            if (group != null) {
                group = group.strip().toLowerCase(Locale.ROOT);
                if (!groups.containsKey(group)) {
                    problem.accept(where + ".group: '" + group + "' is not in hub-groups (no group colour)");
                    group = null;
                }
            }
            Button button = new Button(id, slot, group, icon, action, command, e.getString("service"), e.getBoolean("show", true),
                    e.getString("permission"), e.getBoolean("show-locked", true));
            String other = actions.putIfAbsent(button.actionKey(), id);
            if (other != null) {
                problem.accept(where + ": does the same as '" + other + "' (skipped; one button per action)");
                used.remove(slot);
                continue;
            }
            out.add(button);
        }
        if (slotsIgnored) {
            problem.accept("menu.yml → " + sectionName + ": 'slot' is placed automatically now (centred, file order); the values are ignored");
        }
        return new ButtonLayout(List.copyOf(out), java.util.Collections.unmodifiableMap(new LinkedHashMap<>(groups)));
    }

    /**
     * {@code hub-groups: <id>: {color: <area>}} in order: group id → the area colour its names take (G9). An old
     * {@code mark:} key (the marker panes of GUI-2) is ignored with one line per file load.
     */
    static Map<String, String> groups(YamlConfiguration yaml, Set<String> areas, Consumer<String> problem) {
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("hub-groups");
        if (section == null) {
            return out;
        }
        List<String> marks = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            if (section.contains(id + ".mark")) {
                marks.add(id);
            }
            String color = section.getString(id + ".color", DEFAULT_AREA).strip().toLowerCase(Locale.ROOT);
            if (!areas.contains(color)) {
                problem.accept("menu.yml → hub-groups." + id + ".color: '" + color + "' is not an area colour of style.yml (using "
                        + DEFAULT_AREA + ")");
                color = DEFAULT_AREA;
            }
            out.put(id.toLowerCase(Locale.ROOT), color);
        }
        if (!marks.isEmpty()) {
            problem.accept("menu.yml hub-groups." + String.join("/", marks) + ".mark is no longer used (I9: no marker panes)");
        }
        return out;
    }

    private static Set<Integer> hubSlots() {
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < net.skybornmc.skyblock.common.menu.Centered.gridSize(); i++) {
            set.add(net.skybornmc.skyblock.common.menu.Centered.gridSlot(i));
        }
        set.add(HubMenu.SLOT_PROFILE);
        set.add(HubMenu.SLOT_STAFF);
        return Set.copyOf(set);
    }
}
