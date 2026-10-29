package net.skybornmc.skyblock.moderation;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure helpers for finding banned names (case-insensitive) and tab-completing them. */
public final class BanTargets {

    private BanTargets() {
    }

    /** The banned name matching {@code input} ignoring case, if any. */
    public static Optional<String> match(Collection<String> bannedNames, String input) {
        return bannedNames.stream().filter(name -> name.equalsIgnoreCase(input)).findFirst();
    }

    /** Banned names starting with what the staff member typed, sorted, max 50 (keeps tab-complete light). */
    public static List<String> suggestions(Collection<String> bannedNames, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return bannedNames.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(50)
                .toList();
    }

    /** True if the input looks like an IPv4 or IPv6 address (so it is looked up in the IP ban list). */
    public static boolean looksLikeIp(String input) {
        return input.matches("\\d{1,3}(\\.\\d{1,3}){3}") || (input.contains(":") && input.matches("[0-9a-fA-F:]+"));
    }
}
