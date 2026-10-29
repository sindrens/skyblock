package net.skybornmc.skyblock.chat;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Tab list (header/footer, entry names) and nametags.
 *
 * <ul>
 *   <li>Every player gets their own scoreboard team named {@code pxs<inverted weight><name>}: the client sorts tab
 *       entries by team name, so ranks are sorted by weight (owner first), then alphabetically.</li>
 *   <li>Team prefix = rank prefix and team color = nearest classic rank color: that is the nametag above the head
 *       (works on Bedrock through Geyser).</li>
 *   <li>Teams are mirrored onto every scoreboard an online player uses (main + per-player sidebars from M1.4).</li>
 *   <li>Refreshed on join, on LuckPerms rank change and every {@code tab.update-ticks}; only changed values are sent.</li>
 * </ul>
 */
final class TabService implements Listener {

    static final String TEAM_PREFIX = "pxs";
    private static final int MAX_WEIGHT = 999;

    private final Core core;
    private final Display display;
    private final Supplier<ChatConfig> config;
    private final ModuleMessages msg;
    private final Map<UUID, String> teamOf = new ConcurrentHashMap<>();
    private final Map<UUID, Component> lastListName = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastState = new ConcurrentHashMap<>();
    private Scheduler.Task task;

    TabService(Core core, Display display, Supplier<ChatConfig> config, ModuleMessages msg) {
        this.core = core;
        this.display = display;
        this.config = config;
        this.msg = msg;
    }

    void start() {
        core.ranks().onRankChange(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && task != null) {
                lastState.remove(uuid);
                update(p);
            }
        });
        restartTask();
        Bukkit.getOnlinePlayers().forEach(this::update);
    }

    void restartTask() {
        if (task != null) {
            task.cancel();
        }
        long period = config.get().tabUpdateTicks;
        task = core.scheduler().repeat("chat:tab", period, period, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Scoreboard board : boards()) {
            for (Team team : Set.copyOf(board.getTeams())) {
                if (team.getName().startsWith(TEAM_PREFIX)) {
                    team.unregister();
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(null);
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
        teamOf.clear();
        lastListName.clear();
        lastState.clear();
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
            sendHeaderFooter(p);
        }
    }

    /** Team + list name of one player; cheap when nothing changed. */
    void update(Player player) {
        int state = Objects.hash(display.rank(net.kyori.adventure.audience.Audience.empty(), player), display.color(player),
                player.displayName(), display.afk(player), display.hook(player, "title"), display.hook(player, "tag"),
                core.ranks().weight(player), config.get().tabFormat, config.get().nametagPrefix);
        Integer previous = lastState.put(player.getUniqueId(), state);
        Set<Scoreboard> boards = boards();
        boolean boardsChanged = boards.stream().anyMatch(b -> b.getEntryTeam(player.getName()) == null);
        if (previous != null && previous == state && !boardsChanged) {
            return;
        }
        Component listName = display.tabEntry(player);
        if (!listName.equals(lastListName.put(player.getUniqueId(), listName))) {
            player.playerListName(listName);
        }
        String name = teamName(core.ranks().weight(player), player.getName());
        String old = teamOf.put(player.getUniqueId(), name);
        for (Scoreboard board : boards) {
            if (old != null && !old.equals(name)) {
                Team stale = board.getTeam(old);
                if (stale != null) {
                    stale.unregister();
                }
            }
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            team.prefix(config.get().nametagPrefix ? display.rank(net.kyori.adventure.audience.Audience.empty(), player)
                    : Component.empty());
            team.suffix(display.afkSuffix(player));
            team.color(display.teamColor(player));
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }
    }

    void sendHeaderFooter(Player viewer) {
        int online = (int) Bukkit.getOnlinePlayers().stream().filter(viewer::canSee).count();
        String season = core.placeholders().has("season_display")
                ? core.placeholders().text(viewer, "season_display").orElse(config.get().season) : config.get().season;
        var placeholders = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                Msg.number("online", online), Msg.number("max", Bukkit.getMaxPlayers()), Msg.p("season", season),
                Msg.number("ping", viewer.getPing()),
                Msg.p("tps", String.format(Locale.ROOT, "%.1f", Math.min(20.0, Bukkit.getTPS()[0])))};
        Component header = msg.component(viewer, "tab.header", placeholders);
        Component footer = msg.component(viewer, "tab.footer", placeholders);
        if (viewer.hasPermission(ChatModule.PERM_TAB_STAFF)) {
            footer = footer.append(Component.newline()).append(msg.component(viewer, "tab.footer-staff", placeholders));
        }
        viewer.sendPlayerListHeaderAndFooter(header, footer);
    }

    /** {@code pxs} + (999 − weight, 3 digits) + lowercase name: sorts owner → default, then A → Z. */
    static String teamName(int weight, String playerName) {
        int inverted = MAX_WEIGHT - Math.max(0, Math.min(MAX_WEIGHT, weight));
        return TEAM_PREFIX + String.format(Locale.ROOT, "%03d", inverted) + playerName.toLowerCase(Locale.ROOT);
    }

    private Set<Scoreboard> boards() {
        Set<Scoreboard> boards = new LinkedHashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player p : Bukkit.getOnlinePlayers()) {
            boards.add(p.getScoreboard());
        }
        return boards;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // One tick later: LuckPerms data and the nickname are applied by then.
        core.scheduler().later("chat:tab-join", 2L, () -> {
            if (p.isOnline()) {
                lastState.remove(p.getUniqueId());
                Bukkit.getOnlinePlayers().forEach(this::update); // a new sidebar scoreboard needs everyone's teams
                Bukkit.getOnlinePlayers().forEach(this::sendHeaderFooter);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        String team = teamOf.remove(p.getUniqueId());
        lastListName.remove(p.getUniqueId());
        lastState.remove(p.getUniqueId());
        if (team != null) {
            for (Scoreboard board : boards()) {
                Team t = board.getTeam(team);
                if (t != null) {
                    t.unregister();
                }
            }
        }
    }
}
