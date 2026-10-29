package net.skybornmc.skyblock.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** The {@code /menu} hub layout from {@code menu.yml}: one entry per feature, texts in {@code messages/navigation.yml}. */
public record HubLayout(List<Entry> entries) {

    /** {@code auto}: active when its command exists on the server, otherwise "Coming soon". */
    public enum Status { AUTO, ACTIVE, COMING_SOON }

    /**
     * @param id         also the message key {@code hub.entries.<id>.name/.lore}
     * @param command    run on click (without /); its first word decides {@link Status#AUTO}
     * @param permission optional: without it the entry shows as locked ("Coming soon" style is not used for that)
     */
    public record Entry(String id, int slot, Material icon, String command, Status status, String permission) {

        /** True if the entry is clickable right now for {@code player}. */
        public boolean active() {
            return switch (status) {
                case ACTIVE -> true;
                case COMING_SOON -> false;
                case AUTO -> commandExists(command);
            };
        }

        public boolean allowed(Player player) {
            return permission == null || permission.isBlank() || player.hasPermission(permission);
        }
    }

    /** Slots the frame owns in a 6-row menu (edges, 45-53) and the profile head; entries can't use them. */
    static final Set<Integer> RESERVED = reserved();

    static HubLayout parse(YamlConfiguration yaml, Consumer<String> problem) {
        return parse(yaml, "entries", problem);
    }

    /** Parses one section of entries ({@code entries} = /menu, {@code staff-entries} = /staff). */
    static HubLayout parse(YamlConfiguration yaml, String sectionName, Consumer<String> problem) {
        List<Entry> entries = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        ConfigurationSection section = yaml.getConfigurationSection(sectionName);
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection e = section.getConfigurationSection(id);
                if (e == null) {
                    continue;
                }
                int slot = e.getInt("slot", -1);
                if (slot < 0 || slot >= 54 || RESERVED.contains(slot)) {
                    problem.accept("menu.yml → " + sectionName + "." + id + ".slot: " + slot + " is not an inner slot of the menu (skipped)");
                    continue;
                }
                if (!used.add(slot)) {
                    problem.accept("menu.yml → " + sectionName + "." + id + ".slot: slot " + slot + " is already used (skipped)");
                    continue;
                }
                String iconName = e.getString("icon", "book");
                Material icon = Material.matchMaterial(iconName);
                if (icon == null || !icon.isItem()) {
                    problem.accept("menu.yml → " + sectionName + "." + id + ".icon: unknown item '" + iconName + "' (using book)");
                    icon = Material.BOOK;
                }
                String status = e.getString("status", "auto").toLowerCase(Locale.ROOT).replace('_', '-');
                entries.add(new Entry(id, slot, icon, e.getString("command", ""), switch (status) {
                    case "active" -> Status.ACTIVE;
                    case "coming-soon" -> Status.COMING_SOON;
                    default -> Status.AUTO;
                }, e.getString("permission")));
            }
        }
        return new HubLayout(List.copyOf(entries));
    }

    static boolean commandExists(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String label = command.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return Bukkit.getCommandMap().getCommand(label) != null;
    }

    private static Set<Integer> reserved() {
        Set<Integer> set = new HashSet<>();
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                set.add(slot);
            }
        }
        return Set.copyOf(set);
    }
}
