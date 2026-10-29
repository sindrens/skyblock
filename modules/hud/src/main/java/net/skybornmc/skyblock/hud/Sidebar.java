package net.skybornmc.skyblock.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's sidebar on their own scoreboard. Each line is a team prefix on an invisible fixed entry, so a changed
 * line is a single team update: no flicker, works on Java and Bedrock (Geyser), no packets needed.
 */
final class Sidebar {

    private static final String OBJECTIVE = "phx_sidebar";
    private static final String TEAM_PREFIX = "phx_line_";

    private final Scoreboard board;
    private final Objective objective;
    private final List<Component> lines = new ArrayList<>();
    private Component title = Component.empty();
    private boolean shown = true;

    Sidebar(Scoreboard board) {
        this.board = board;
        Objective existing = board.getObjective(OBJECTIVE);
        this.objective = existing != null ? existing : board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty());
        objective.numberFormat(NumberFormat.blank());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    Scoreboard board() {
        return board;
    }

    void show(boolean show) {
        if (show != shown) {
            shown = show;
            objective.setDisplaySlot(show ? DisplaySlot.SIDEBAR : null);
        }
    }

    boolean shown() {
        return shown;
    }

    void title(Component newTitle) {
        if (!newTitle.equals(title)) {
            title = newTitle;
            objective.displayName(newTitle);
        }
    }

    /** Applies the new lines: only changed lines are sent; lines are added/removed at the bottom when the count changes. */
    void lines(List<Component> newLines) {
        int oldSize = lines.size();
        int newSize = newLines.size();
        for (int i : SidebarLines.changed(lines, newLines)) {
            team(i).prefix(newLines.get(i));
            lines.set(i, newLines.get(i));
        }
        for (int i = oldSize; i < newSize; i++) {
            Team team = team(i);
            team.prefix(newLines.get(i));
            lines.add(newLines.get(i));
        }
        for (int i = newSize; i < oldSize; i++) {
            String entry = SidebarLines.entry(i);
            board.resetScores(entry);
            Team team = board.getTeam(TEAM_PREFIX + i);
            if (team != null) {
                team.unregister();
            }
        }
        while (lines.size() > newSize) {
            lines.removeLast();
        }
        if (oldSize != newSize) {
            // Scores decide the order (highest on top): line 0 gets the highest score.
            for (int i = 0; i < newSize; i++) {
                objective.getScore(SidebarLines.entry(i)).setScore(newSize - i);
            }
        }
    }

    private Team team(int index) {
        String name = TEAM_PREFIX + index;
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.addEntry(SidebarLines.entry(index));
        }
        return team;
    }

    void remove() {
        objective.unregister();
        for (int i = 0; i < lines.size(); i++) {
            Team team = board.getTeam(TEAM_PREFIX + i);
            if (team != null) {
                team.unregister();
            }
        }
        lines.clear();
    }
}
