package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tab completion of the {@code /p <player> <reason> [flags]} flags: offers each flag not typed yet, after what is typed. */
final class Flags {

    /**
     * The flags of {@code /p}: {@code -s} silent, {@code -ip} IP-wide, {@code -force} skip the duplicate check,
     * {@code --evidence <log id>} a {@code /logs} chat line as evidence (spec 13.11).
     */
    static final List<String> ALL = List.of("-s", "-ip", "-force", LogEvidence.FLAG);

    /** The flags {@code sender} may use (tab completion only offers those); permission check per flag. */
    static List<String> allowed(java.util.function.Predicate<String> hasPermission) {
        List<String> out = new ArrayList<>();
        if (hasPermission.test(ModerationModule.PERM_SILENT)) {
            out.add("-s");
        }
        if (hasPermission.test(ModerationModule.PERM_IPBAN)) {
            out.add("-ip");
        }
        if (hasPermission.test(ModerationModule.PERM_FORCE)) {
            out.add("-force");
        }
        out.add(LogEvidence.FLAG); // everyone who may use /p (the log entry itself is checked against /logs access)
        return out;
    }

    private Flags() {
    }

    /**
     * Completions for the whole flags text typed so far, e.g. {@code ""} → {@code -s, -ip}; {@code "-s "} →
     * {@code -s -ip}; {@code "-i"} → {@code -ip}.
     */
    static List<String> suggestions(String typed) {
        return suggestions(typed, ALL);
    }

    /** Same, offering only {@code flags}. */
    static List<String> suggestions(String typed, List<String> flags) {
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
        if (!used.isEmpty() && used.getLast().equals(LogEvidence.FLAG)) {
            return out; // the log entry id comes next: no suggestions for ids (spec 13.11)
        }
        for (String flag : flags) {
            if (!used.contains(flag) && flag.startsWith(current)) {
                out.add(done + flag);
            }
        }
        return out;
    }
}
