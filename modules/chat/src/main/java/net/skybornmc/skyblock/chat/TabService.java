package net.skybornmc.skyblock.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Tab list (header/footer, entry names) and nametags, built for 300 players on the main thread.
 *
 * <h2>How it stays cheap</h2>
 * <ul>
 *   <li><b>Diff only:</b> each player has a cheap fingerprint ({@link Display#key}: prefix, color, weight, nick, AFK,
 *       title, tag). Components are only rebuilt and teams/list names only touched when it changes.</li>
 *   <li><b>Spread over ticks:</b> one tick task checks {@code ceil(online / tab.update-ticks)} players per tick
 *       (round-robin) and works through a job queue with a time budget ({@link #BUDGET_NANOS} per tick).</li>
 *   <li><b>Teams:</b> one team per player ({@code pxs<999-weight><name>}, so the client sorts by rank weight, then A–Z).
 *       The HUD gives every player their own sidebar scoreboard, so teams are mirrored onto every board. A new board
 *       gets all teams once (queued); a changed player is applied to each board once (queued). Unchanged teams are
 *       never touched, and team setters only run when the value differs.</li>
 *   <li><b>Header/footer:</b> built once per (platform, online count, season) and shared; only resent when the
 *       component a viewer would get differs from what they have.</li>
 *   <li>Events (join, quit, LuckPerms rank change) only mark players dirty; no work happens inside the event.</li>
 * </ul>
 */
final class TabService implements Listener {

    static final String TEAM_PREFIX = "pxs";
    private static final int MAX_WEIGHT = 999;
    /** Main-thread time this service may use per tick for queued team work. */
    static final long BUDGET_NANOS = 400_000L;

    /** What a player looks like right now (rebuilt only when {@link Display#key} changes). */
    private record Entry(String key, String team, Component prefix, Component suffix, NamedTextColor color) {
    }

    /** One unit of team work: apply (or remove) one player's team on one scoreboard. */
    private record JobKey(UUID player, Scoreboard board) {
    }

    private final Core core;
    private final Display display;
    private final Supplier<ChatConfig> config;
    private final ModuleMessages msg;

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final LinkedHashMap<JobKey, Runnable> jobs = new LinkedHashMap<>();
    private final Set<Scoreboard> boards = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, Scoreboard> boardOf = new HashMap<>();
    private final ArrayDeque<UUID> checkOrder = new ArrayDeque<>();
    private final Map<UUID, Component[]> sentHeaderFooter = new HashMap<>();
    private final Map<String, Component> headerCache = new HashMap<>();
    private Scheduler.Task task;
    private volatile boolean running;
    private List<Player> vanishedCache = List.of();
    private int ticks;

    TabService(Core core, Display display, Supplier<ChatConfig> config, ModuleMessages msg) {
        this.core = core;
        this.display = display;
        this.config = config;
        this.msg = msg;
    }

    void start() {
        running = true;
        // Forced rebuild (not just a fingerprint check): the rank provider notifies right after the change and again
        // once it settled, so the tab name, sort order and nametag follow within a second.
        core.ranks().onRankChange(this::markDirty);
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player p : Bukkit.getOnlinePlayers()) {
            track(p);
        }
        task = core.scheduler().repeat("chat:tab", 1L, 1L, this::tick);
    }

    /** Config reloaded: formats may have changed, so every player is rebuilt (spread over the next ticks). */
    void reloaded() {
        display.clearCache();
        headerCache.clear();
        sentHeaderFooter.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            entries.computeIfPresent(p.getUniqueId(), (id, e) -> new Entry("", e.team(), e.prefix(), e.suffix(), e.color()));
            dirty.add(p.getUniqueId());
        }
    }

    void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Scoreboard board : new ArrayList<>(boards)) {
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
        entries.clear();
        playerNames.clear();
        dirty.clear();
        jobs.clear();
        boards.clear();
        boardOf.clear();
        checkOrder.clear();
        sentHeaderFooter.clear();
        headerCache.clear();
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int online = Bukkit.getOnlinePlayers().size();
        if (online == 0) {
            return;
        }
        // 1. Round-robin: a slice of players per tick gets its fingerprint and header/footer checked.
        int slice = Math.max(1, (online + config.get().tabUpdateTicks - 1) / config.get().tabUpdateTicks);
        int visibleBase = online;
        if (ticks++ % 20 == 0) {
            vanishedCache = vanishedPlayers(); // a full scan once per second, not every tick
        }
        List<Player> vanished = vanishedCache;
        for (int i = 0; i < slice && !checkOrder.isEmpty(); i++) {
            UUID id = checkOrder.pollFirst();
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            checkOrder.addLast(id);
            Scoreboard board = p.getScoreboard();
            if (boardOf.put(id, board) != board) {
                adoptBoard(board);
            }
            Entry entry = entries.get(id);
            if (entry == null || !entry.key().equals(display.key(p))) {
                dirty.add(id);
            }
            sendHeaderFooter(p, visibleBase, vanished);
        }
        // 2. Dirty players: rebuild their entry and queue one job per board.
        Iterator<UUID> it = dirty.iterator();
        while (it.hasNext() && System.nanoTime() < deadline) {
            UUID id = it.next();
            it.remove();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                rebuild(p);
            }
        }
        // 3. Team jobs with the remaining budget.
        Iterator<Map.Entry<JobKey, Runnable>> jobIt = jobs.entrySet().iterator();
        while (jobIt.hasNext() && System.nanoTime() < deadline) {
            Runnable job = jobIt.next().getValue();
            jobIt.remove();
            job.run();
        }
    }

    private void track(Player p) {
        UUID id = p.getUniqueId();
        playerNames.put(id, p.getName());
        if (!checkOrder.contains(id)) {
            checkOrder.addFirst(id); // checked on the next tick
        }
        dirty.add(id);
        Scoreboard board = p.getScoreboard();
        boardOf.put(id, board);
        adoptBoard(board);
    }

    /** A scoreboard we haven't seen (a new player's sidebar): queue every known team onto it once. */
    private void adoptBoard(Scoreboard board) {
        if (!boards.add(board)) {
            return;
        }
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            queueApply(e.getKey(), board, null);
        }
    }

    private void rebuild(Player p) {
        UUID id = p.getUniqueId();
        String key = display.key(p);
        Entry old = entries.get(id);
        if (old != null && old.key().equals(key)) {
            return;
        }
        ChatConfig c = config.get();
        Audience none = Audience.empty();
        Entry entry = new Entry(key, teamName(core.ranks().weight(p), p.getName()),
                c.nametagPrefix ? display.rank(none, p) : Component.empty(), display.afkSuffix(p), display.teamColor(p));
        entries.put(id, entry);
        p.playerListName(display.tabEntry(p));
        String staleTeam = old != null && !old.team().equals(entry.team()) ? old.team() : null;
        for (Scoreboard board : boards) {
            queueApply(id, board, staleTeam);
        }
    }

    private void queueApply(UUID id, Scoreboard board, String staleTeam) {
        JobKey key = new JobKey(id, board);
        Runnable previous = jobs.remove(key);
        jobs.put(key, () -> {
            if (previous != null) {
                previous.run(); // keeps a pending stale-team removal
            }
            apply(id, board, staleTeam);
        });
    }

    /** Applies one player's team to one board, touching only what differs. */
    private void apply(UUID id, Scoreboard board, String staleTeam) {
        Entry entry = entries.get(id);
        String name = playerNames.get(id);
        if (staleTeam != null) {
            Team stale = board.getTeam(staleTeam);
            if (stale != null) {
                stale.unregister();
            }
        }
        if (entry == null || name == null) {
            return;
        }
        Team team = board.getTeam(entry.team());
        if (team == null) {
            team = board.registerNewTeam(entry.team());
        }
        if (!Objects.equals(team.prefix(), entry.prefix())) {
            team.prefix(entry.prefix());
        }
        if (!Objects.equals(team.suffix(), entry.suffix())) {
            team.suffix(entry.suffix());
        }
        if (team.color() != entry.color()) {
            team.color(entry.color());
        }
        if (!team.hasEntry(name)) {
            team.addEntry(name);
        }
    }

    // ------------------------------------------------------------------ header / footer

    private List<Player> vanishedPlayers() {
        if (!core.placeholders().has("vanished")) {
            return List.of();
        }
        List<Player> out = new ArrayList<>(2);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.placeholders().text(p, "vanished").map("true"::equalsIgnoreCase).orElse(false)) {
                out.add(p);
            }
        }
        return out;
    }

    private void sendHeaderFooter(Player viewer, int online, List<Player> vanished) {
        int visible = online;
        for (Player v : vanished) {
            if (!v.equals(viewer) && !viewer.canSee(v)) {
                visible--;
            }
        }
        boolean bedrock = core.bedrock().isBedrock(viewer.getUniqueId());
        String season = config.get().season;
        if (core.placeholders().has("season_display")) {
            season = core.placeholders().text(viewer, "season_display").orElse(season);
        }
        String seasonText = season;
        int visibleCount = visible;
        if (headerCache.size() > 128) {
            headerCache.clear();
        }
        Component header = headerCache.computeIfAbsent("h" + bedrock + ':' + visible + ':' + season,
                k -> msg.component(viewer, "tab.header", Msg.number("online", visibleCount),
                        Msg.number("max", Bukkit.getMaxPlayers()), Msg.p("season", seasonText)));
        Component footer = headerCache.computeIfAbsent("f" + bedrock, k -> msg.component(viewer, "tab.footer"));
        if (viewer.hasPermission(ChatModule.PERM_TAB_STAFF)) {
            TagResolver[] staff = {Msg.number("ping", viewer.getPing()),
                    Msg.p("tps", String.format(Locale.ROOT, "%.1f", Math.min(20.0, Bukkit.getTPS()[0])))};
            footer = footer.append(Component.newline()).append(msg.component(viewer, "tab.footer-staff", staff));
        }
        Component[] last = sentHeaderFooter.get(viewer.getUniqueId());
        if (last == null || !last[0].equals(header) || !last[1].equals(footer)) {
            viewer.sendPlayerListHeaderAndFooter(header, footer);
            sentHeaderFooter.put(viewer.getUniqueId(), new Component[]{header, footer});
        }
    }

    // ------------------------------------------------------------------ events (mark only, no work)

    /** {@code pxs} + (999 − weight, 3 digits) + lowercase name: sorts owner → default, then A → Z. */
    static String teamName(int weight, String playerName) {
        int inverted = MAX_WEIGHT - Math.max(0, Math.min(MAX_WEIGHT, weight));
        return TEAM_PREFIX + String.format(Locale.ROOT, "%03d", inverted) + playerName.toLowerCase(Locale.ROOT);
    }

    /** Something about {@code player} changed (nick, settings): re-check on the next tick. */
    void markDirty(UUID player) {
        if (running) {
            entries.computeIfPresent(player, (id, e) -> new Entry("", e.team(), e.prefix(), e.suffix(), e.color()));
            dirty.add(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (running) {
            track(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Entry entry = entries.remove(id);
        dirty.remove(id);
        checkOrder.remove(id);
        sentHeaderFooter.remove(id);
        Scoreboard own = boardOf.remove(id);
        if (own != null && own != Bukkit.getScoreboardManager().getMainScoreboard() && !boardOf.containsValue(own)) {
            boards.remove(own);
            jobs.keySet().removeIf(k -> k.board() == own);
        }
        if (entry != null) {
            String team = entry.team();
            for (Scoreboard board : boards) {
                JobKey key = new JobKey(id, board);
                jobs.remove(key);
                jobs.put(key, () -> {
                    Team t = board.getTeam(team);
                    if (t != null) {
                        t.unregister();
                    }
                });
            }
        }
        playerNames.remove(id);
    }
}
