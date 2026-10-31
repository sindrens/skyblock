package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.HelpEntry;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * QOL3 "Did you mean" (QoL #9a): a mistyped command ({@code /hoem}) answers {@code Unknown command /hoem. Did you mean
 * /home?} instead of the vanilla dead end. Only commands this player may run are suggested (the command framework's own
 * permission-filtered list), so staff commands never leak. Java: a click puts the corrected command in the chat box;
 * Bedrock reads the same line. {@code essentials.yml → command-hints.*}.
 */
final class CommandHints implements Listener {

    private final Core core;
    private final EssentialsConfig config;
    private final ModuleMessages msg;

    CommandHints(Core core, EssentialsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUnknown(UnknownCommandEvent event) {
        if (!config.commandHintsEnabled || !(event.getSender() instanceof Player player)) {
            return;
        }
        String line = event.getCommandLine() == null ? "" : event.getCommandLine().strip();
        String typed = label(line);
        if (typed.isEmpty()) {
            return;
        }
        Optional<String> match = suggest(typed, labels(core.commands().helpIndex(player)), config.commandHintsMaxDistance);
        if (match.isEmpty()) {
            event.message(msg.component(player, "command-hints.unknown", Msg.p("command", typed)));
            return;
        }
        String rest = line.indexOf(' ') < 0 ? "" : line.substring(line.indexOf(' ')).stripTrailing();
        String suggestion = "/" + match.get() + rest;
        Component text = msg.component(player, "command-hints.did-you-mean", Msg.p("command", typed),
                Msg.p("suggestion", "/" + match.get()));
        if (!core.messages().isBedrock(player)) {
            text = text.clickEvent(ClickEvent.suggestCommand(suggestion))
                    .hoverEvent(HoverEvent.showText(msg.component(player, "command-hints.hover", Msg.p("suggestion", suggestion))));
        }
        event.message(text);
    }

    // ------------------------------------------------------------------ pure rules (unit-tested)

    /** The command word of a typed line, without the slash and a {@code plugin:} prefix, lower case. */
    static String label(String line) {
        String word = line.startsWith("/") ? line.substring(1) : line;
        int space = word.indexOf(' ');
        if (space >= 0) {
            word = word.substring(0, space);
        }
        int colon = word.indexOf(':');
        if (colon >= 0) {
            word = word.substring(colon + 1);
        }
        return word.toLowerCase(Locale.ROOT);
    }

    /** The top-level command words (and their aliases) of the entries a player may use. */
    static Set<String> labels(Collection<HelpEntry> entries) {
        Set<String> out = new TreeSet<>();
        for (HelpEntry entry : entries) {
            String path = entry.path();
            int space = path.indexOf(' ');
            out.add((space < 0 ? path : path.substring(0, space)).toLowerCase(Locale.ROOT));
            if (space < 0) {
                for (String alias : entry.aliases()) {
                    out.add(alias.toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    /**
     * The closest known command within {@code maxDistance} edits (a swapped pair of letters counts as one); ties go to
     * the shorter, then the alphabetically first word. Words under 2 letters are never matched (too many hits).
     */
    static Optional<String> suggest(String typed, Collection<String> labels, int maxDistance) {
        if (typed == null || typed.length() < 2) {
            return Optional.empty();
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String label : labels) {
            if (label.length() < 2 || label.equals(typed)) {
                continue;
            }
            // a 2-letter typo may only be 1 edit away; otherwise "/ab" would match half the commands
            int limit = Math.min(maxDistance, Math.max(1, Math.min(typed.length(), label.length()) - 1));
            int d = distance(typed, label, limit);
            if (d > limit) {
                continue;
            }
            if (d < bestDistance || (d == bestDistance && (label.length() < best.length()
                    || (label.length() == best.length() && label.compareTo(best) < 0)))) {
                best = label;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Edit distance with swaps (optimal string alignment); stops early and returns {@code limit + 1} when above it. */
    static int distance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > limit) {
                return limit + 1;
            }
        }
        return d[a.length()][b.length()];
    }
}
