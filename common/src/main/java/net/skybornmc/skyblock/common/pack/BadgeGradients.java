package net.skybornmc.skyblock.common.pack;

import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The approved Immortal rank gradients, from config lines {@code id=#RRGGBB,#RRGGBB,#RRGGBB} (chat.yml
 * {@code badges.immortal-gradients}). The first valid entry is the default. Pure.
 */
public final class BadgeGradients {

    private BadgeGradients() {
    }

    /** Valid entries in order; a bad id, fewer than two colours or an unparsable colour skips the line. */
    public static Map<String, List<TextColor>> parse(List<String> lines) {
        Map<String, List<TextColor>> out = new LinkedHashMap<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String id = line.substring(0, eq).trim();
            if (!id.matches("[a-z0-9-]+") || out.containsKey(id)) {
                continue;
            }
            List<TextColor> colors = new ArrayList<>();
            for (String part : line.substring(eq + 1).split(",")) {
                TextColor c = TextColor.fromHexString(part.trim());
                if (c != null) {
                    colors.add(c);
                }
            }
            if (colors.size() >= 2) {
                out.put(id, List.copyOf(colors));
            }
        }
        return out;
    }
}
