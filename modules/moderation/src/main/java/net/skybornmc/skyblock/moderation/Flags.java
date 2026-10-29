package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tab completion of the {@code /p <player> <reason> [flags]} flags: offers each flag not typed yet, after what is typed. */
final class Flags {

    /** The flags of {@code /p}: {@code -s} silent, {@code -ip} IP-wide. */
    static final List<String> ALL = List.of("-s", "-ip");

    private Flags() {
    }

    /**
     * Completions for the whole flags text typed so far, e.g. {@code ""} → {@code -s, -ip}; {@code "-s "} →
     * {@code -s -ip}; {@code "-i"} → {@code -ip}.
     */
    static List<String> suggestions(String typed) {
        String text = typed == null ? "" : typed;
        int cut = text.lastIndexOf(' ') + 1;
        String done = text.substring(0, cut);
        String current = text.substring(cut).toLowerCase(Locale.ROOT);
        List<String> used = new ArrayList<>();
        for (String word : done.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                used.add(word.toLowerCase(Locale.ROOT));
            }
        }
        List<String> out = new ArrayList<>();
        for (String flag : ALL) {
            if (!used.contains(flag) && flag.startsWith(current)) {
                out.add(done + flag);
            }
        }
        return out;
    }
}
