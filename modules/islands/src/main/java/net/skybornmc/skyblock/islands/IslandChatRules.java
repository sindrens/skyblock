package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Island chat rules (M2.5, pure): who talks and listens, the role color palette, color cycling and spies. */
final class IslandChatRules {

    /** Roles shown in the color menu, in menu order. */
    static final List<Role> COLOR_ROLES = List.of(Role.LEADER, Role.ADMIN, Role.MOD, Role.MEMBER);

    private IslandChatRules() {
    }

    /** Stored members talk and listen in island chat; coop players and visitors don't (they are not part of the team). */
    static boolean chats(Role role) {
        return role != null && role != Role.COOP;
    }

    /** Only the leader picks the role colors (owner I11). */
    static boolean canPickColors(Role role) {
        return role == Role.LEADER;
    }

    /** A color from config: a named color ({@code gold}, {@code light_purple}) or {@code #RRGGBB}; null when invalid. */
    static TextColor parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("#")) {
            return v.length() == 7 ? TextColor.fromHexString(v) : null;
        }
        return NamedTextColor.NAMES.value(v);
    }

    /** The normalized palette entry (lowercase, trimmed). */
    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Parsed palette: the valid entries in order (no duplicates) and a line per problem. */
    record Palette(List<String> colors, List<String> problems) {
    }

    static Palette palette(Collection<String> raw) {
        Set<String> colors = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        for (String entry : raw == null ? List.<String>of() : raw) {
            String color = normalize(entry);
            if (parse(color) == null) {
                problems.add("chat.palette: '" + entry + "' is not a color (use a name like gold or #RRGGBB)");
            } else if (!colors.add(color)) {
                problems.add("chat.palette: '" + entry + "' is listed twice");
            }
        }
        if (colors.isEmpty()) {
            problems.add("chat.palette is empty: role colors can't be changed");
        }
        return new Palette(List.copyOf(colors), problems);
    }

    /** The next palette color after {@code current} (wraps around; an unknown current color starts at the first one). */
    static String next(List<String> palette, String current) {
        if (palette.isEmpty()) {
            return normalize(current);
        }
        int index = palette.indexOf(normalize(current));
        return palette.get((index + 1) % palette.size());
    }

    /** The color a role shows: the island's choice if it is still valid and in the palette, else the default, else white. */
    static String effective(String chosen, List<String> palette, String fallback) {
        String c = normalize(chosen);
        if (!c.isEmpty() && parse(c) != null && palette.contains(c)) {
            return c;
        }
        String f = normalize(fallback);
        return parse(f) != null ? f : "white";
    }

    /** Staff who see a copy (spy): online, spying, and not already a recipient. */
    static <T> List<T> spies(Collection<T> online, Set<T> recipients, Predicate<T> spying) {
        List<T> list = new ArrayList<>();
        for (T player : online) {
            if (!recipients.contains(player) && spying.test(player)) {
                list.add(player);
            }
        }
        return list;
    }

    /** A one-shot message is cut to {@code max} characters (vanilla chat allows 256). */
    static String limit(String text, int max) {
        String t = text == null ? "" : net.skybornmc.skyblock.common.pack.PrivateUse.strip(text).strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** The wool item (id) that shows a palette color in the color menu; hex colors show white wool. */
    static String wool(String color) {
        return switch (normalize(color)) {
            case "black" -> "black_wool";
            case "dark_blue", "blue" -> "blue_wool";
            case "dark_green" -> "green_wool";
            case "dark_aqua" -> "cyan_wool";
            case "dark_red", "red" -> "red_wool";
            case "dark_purple" -> "purple_wool";
            case "gold" -> "orange_wool";
            case "gray" -> "light_gray_wool";
            case "dark_gray" -> "gray_wool";
            case "green" -> "lime_wool";
            case "aqua" -> "light_blue_wool";
            case "light_purple" -> "magenta_wool";
            case "yellow" -> "yellow_wool";
            default -> "white_wool";
        };
    }
}
