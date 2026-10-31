package net.skybornmc.skyblock.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.pack.BadgeOptions;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.TextMemo;
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
import java.util.HashSet;
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
 *   <li><b>Spread over ticks:</b> one tick task checks each player once per {@code tab.update-ticks} (round-robin,
 *       {@link TabPacing}; a new player is checked on the next tick) and works through a job queue with a time budget
 *       ({@link #BUDGET_NANOS} per tick).</li>
 *   <li><b>No LuckPerms on the main thread (PERF4):</b> the rank parts (prefix, color, weight) are read asynchronously
 *       into a {@link Display.RankView} snapshot each time a player is checked and on every rank change; the tick only
 *       compares and uses snapshots. A player without a snapshot yet is rebuilt as soon as it arrives.</li>
 *   <li><b>Teams:</b> one team per player ({@code pxs<999-weight><name>}, so the client sorts by rank weight, then A–Z).
 *       The HUD gives every player their own sidebar scoreboard, so teams are mirrored onto every board. A new board
 *       gets all teams once (queued); a changed player is applied to each board once (queued). Unchanged teams are
 *       never touched, and team setters only run when the value differs.</li>
 *   <li><b>Header/footer (PERF7):</b> each viewer's header/footer is described by a short text key (platform,
 *       visible online count, max players, season, and for staff the TPS tier/value and ping, plus the messages
 *       generation). When the key equals what the viewer already has, nothing is built or sent. Otherwise the parts
 *       come from a shared {@link TextMemo}: viewers with the same key share one built component, so it is parsed
 *       once per tick (and usually once per change) for everybody.</li>
 *   <li>Events (join, quit, LuckPerms rank change) only mark players dirty; no work happens inside the event.</li>
 * </ul>
 */
final class TabService implements Listener {

    static final String TEAM_PREFIX = "pxs";
    private static final int MAX_WEIGHT = 999;
    /** Main-thread time this service may use per tick for queued team work. */
    static final long BUDGET_NANOS = 400_000L;

    /** What a player looks like right now (rebuilt only when {@link Display#key} changes). */
    private record Entry(String key, String team, Component prefix, Component suffix, NamedTextColor color,
                         Display.RankView rank) {
    }

    /** One unit of team work: apply (or remove) one player's team on one scoreboard. */
    private record JobKey(UUID player, Scoreboard board) {
    }

    private final Core core;
    private final Display display;
    private final Supplier<ChatConfig> config;
    private final ModuleMessages msg;
    private final RankBadges badges;

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final LinkedHashMap<JobKey, Runnable> jobs = new LinkedHashMap<>();
    private final Set<Scoreboard> boards = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, Scoreboard> boardOf = new HashMap<>();
    private final ArrayDeque<UUID> checkOrder = new ArrayDeque<>();
    /**
     * Rank badges per viewer: every player has their own sidebar scoreboard (HUD), so the team prefix, which carries the
     * nametag badge, is drawn per board for the board owner's pack/settings. A board that is shared (or the main one)
     * gets the plain text form. The tab list name itself is one value for everybody (the client ignores team prefixes
     * when a list name is set), so it always shows the plain {@code [Name]} text.
     */
    private final Map<Scoreboard, BadgeOptions> variants = new WeakHashMap<>();
    private final Map<Scoreboard, UUID> boardOwner = new WeakHashMap<>();
    /** The header/footer key each viewer was last sent (PERF7: compared instead of building components every check). */
    private final Map<UUID, String> sentHeaderFooter = new HashMap<>();
    /** Built header, footer and staff footer parts by key, shared by every viewer with the same key. */
    private final TextMemo<Component> headerCache = new TextMemo<>(256);
    private Scheduler.Task task;
    private volatile boolean running;
    private List<Player> vanishedCache = List.of();
    private int ticks;
    private final TabPacing pacing = new TabPacing();
    /** Joined players checked on the next tick, before the paced round-robin. */
    private final ArrayDeque<UUID> urgent = new ArrayDeque<>();
    /** Rank snapshots read off the main thread (written on the main thread only). */
    private final Map<UUID, Display.RankView> rankViews = new HashMap<>();
    /** Players whose async rank read is in flight. */
    private final Set<UUID> rankPending = new HashSet<>();
    /** Players whose next rank read must force a rebuild (a rank change event). */
    private final Set<UUID> rankForced = new HashSet<>();
    /** Players to read asynchronously at the end of this tick. */
    private final List<Player> rankBatch = new ArrayList<>();

    TabService(Core core, Display display, Supplier<ChatConfig> config, ModuleMessages msg, RankBadges badges) {
        this.core = core;
        this.display = display;
        this.config = config;
        this.msg = msg;
        this.badges = badges;
    }

    void start() {
        running = true;
        // Forced rebuild (not just a fingerprint check): the rank provider notifies right after the change and again
        // once it settled, so the tab name, sort order and nametag follow within a second.
        core.ranks().onRankChange(this::rankChanged);
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
            entries.computeIfPresent(p.getUniqueId(), (id, e) -> new Entry("", e.team(), e.prefix(), e.suffix(), e.color(), e.rank()));
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
        variants.clear();
        boardOwner.clear();
        checkOrder.clear();
        sentHeaderFooter.clear();
        headerCache.clear();
        urgent.clear();
        rankViews.clear();
        rankPending.clear();
        rankForced.clear();
        rankBatch.clear();
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int online = Bukkit.getOnlinePlayers().size();
        if (online == 0) {
            return;
        }
        // 1. Round-robin: each player gets its fingerprint and header/footer checked once per update-ticks (PERF4:
        //    the old slice was at least one player per tick, so a lone player was re-checked 20 times a second).
        int slice = pacing.next(online, config.get().tabUpdateTicks);
        if (ticks++ % 20 == 0) {
            vanishedCache = vanishedPlayers(); // a full scan once per second, not every tick
        }
        while (!urgent.isEmpty()) {
            Player p = Bukkit.getPlayer(urgent.pollFirst());
            if (p != null) {
                check(p, online);
            }
        }
        for (int i = 0; i < slice && !checkOrder.isEmpty(); i++) {
            UUID id = checkOrder.pollFirst();
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            checkOrder.addLast(id);
            check(p, online);
        }
        flushRankBatch();
        // 2. Dirty players: rebuild their entry and queue one job per board. Timed on its own (PERF7) so the log names
        //    the part that is slow: chat:tab-check (fingerprint), chat:tab-header, chat:tab-rebuild or chat:tab-teams.
        if (!dirty.isEmpty()) {
            core.scheduler().timer().time("chat:tab-rebuild", () -> rebuildDirty(deadline));
        }
        flushRankBatch();
        // 3. Team jobs with the remaining budget.
        if (!jobs.isEmpty() && System.nanoTime() < deadline) {
            core.scheduler().timer().time("chat:tab-teams", () -> runJobs(deadline));
        }
    }

    private void rebuildDirty(long deadline) {
        Iterator<UUID> it = dirty.iterator();
        while (it.hasNext() && System.nanoTime() < deadline) {
            UUID id = it.next();
            it.remove();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                rebuild(p);
            }
        }
    }

    private void runJobs(long deadline) {
        Iterator<Map.Entry<JobKey, Runnable>> jobIt = jobs.entrySet().iterator();
        while (jobIt.hasNext() && System.nanoTime() < deadline) {
            Runnable job = jobIt.next().getValue();
            jobIt.remove();
            job.run();
        }
    }

    /** One player's turn: board, fingerprint (from the rank snapshot), header/footer, and an async rank re-read. */
    private void check(Player p, int online) {
        UUID id = p.getUniqueId();
        Scoreboard board = p.getScoreboard();
        checkVariant(p, board);
        if (boardOf.put(id, board) != board) {
            adoptBoard(board);
        }
        Display.RankView rank = rankViews.get(id);
        Entry entry = entries.get(id);
        if (rank != null) {
            boolean changed = core.scheduler().timer().time("chat:tab-check",
                    () -> entry == null || !entry.key().equals(display.key(p, rank)));
            if (changed) {
                dirty.add(id);
            }
        }
        core.scheduler().timer().time("chat:tab-header", () -> sendHeaderFooter(p, online, vanishedCache));
        requestRank(p);
    }

    /**
     * The badge variant of a player's own board (pack loaded, Bedrock, reduced motion / simple / bigger text). When it
     * changed, every team on that board is applied again (queued, budgeted). A few cheap reads per check.
     */
    private void checkVariant(Player p, Scoreboard board) {
        if (!display.badgesOn()) {
            return;
        }
        BadgeOptions options;
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            options = BadgeOptions.PLAIN;
        } else {
            UUID owner = boardOwner.putIfAbsent(board, p.getUniqueId());
            if (owner != null && !owner.equals(p.getUniqueId())) {
                return; // shared board: keeps the plain form
            }
            options = display.viewerOptions(p);
        }
        BadgeOptions old = variants.put(board, options);
        if (old != null && !old.equals(options)) {
            for (UUID other : entries.keySet()) {
                queueApply(other, board, null);
            }
        }
    }

    // ------------------------------------------------------------------ rank snapshots (off the main thread)

    /** LuckPerms says the rank changed: re-read it and force a rebuild when the snapshot arrives. */
    private void rankChanged(UUID player) {
        if (!running) {
            return;
        }
        Player p = Bukkit.getPlayer(player);
        if (p == null) {
            return;
        }
        rankForced.add(player);
        requestRank(p);
        flushRankBatch();
    }

    private void requestRank(Player p) {
        if (rankPending.add(p.getUniqueId())) {
            rankBatch.add(p);
        }
    }

    /** Reads this tick's rank snapshots in one async task and hands them back to the main thread. */
    private void flushRankBatch() {
        if (rankBatch.isEmpty()) {
            return;
        }
        List<Player> batch = List.copyOf(rankBatch);
        rankBatch.clear();
        core.scheduler().async(() -> {
            Map<UUID, Display.RankView> read = new HashMap<>();
            for (Player p : batch) {
                try {
                    read.put(p.getUniqueId(), display.rankView(p));
                } catch (RuntimeException e) {
                    // left out: retried on the player's next check
                }
            }
            core.scheduler().main("chat:tab-rank", () -> applyRanks(batch, read));
        });
    }

    private void applyRanks(List<Player> batch, Map<UUID, Display.RankView> read) {
        for (Player p : batch) {
            rankPending.remove(p.getUniqueId());
        }
        if (!running) {
            return;
        }
        for (Map.Entry<UUID, Display.RankView> e : read.entrySet()) {
            UUID id = e.getKey();
            if (!playerNames.containsKey(id)) {
                continue; // quit meanwhile
            }
            Display.RankView old = rankViews.put(id, e.getValue());
            if (rankForced.remove(id)) {
                markDirty(id);
            } else if (!e.getValue().equals(old)) {
                dirty.add(id);
            }
        }
    }

    private void track(Player p) {
        UUID id = p.getUniqueId();
        playerNames.put(id, p.getName());
        if (!checkOrder.contains(id)) {
            checkOrder.addLast(id);
        }
        if (!urgent.contains(id)) {
            urgent.addLast(id); // checked on the next tick
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
        Display.RankView rank = rankViews.get(id);
        if (rank == null) {
            requestRank(p); // rebuilt when the snapshot arrives (applyRanks marks it dirty)
            return;
        }
        String key = display.key(p, rank);
        Entry old = entries.get(id);
        if (old != null && old.key().equals(key)) {
            return;
        }
        ChatConfig c = config.get();
        Audience none = Audience.empty();
        // With badges the prefix is drawn per board in apply(); this is the text prefix for servers without the pack.
        Entry entry = new Entry(key, teamName(rank.weight(), p.getName()),
                c.nametagPrefix && !display.badgesOn() ? display.rankPrefix(none, rank.prefix()) : Component.empty(),
                display.afkSuffix(p), Display.teamColor(rank), rank);
        entries.put(id, entry);
        p.playerListName(display.tabEntry(p, rank));
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
        Component prefix = entry.prefix();
        if (display.badgesOn() && config.get().nametagPrefix && entry.rank() != null) {
            prefix = display.rankFor(Audience.empty(), entry.rank(), variants.getOrDefault(board, BadgeOptions.PLAIN));
        }
        if (!Objects.equals(team.prefix(), prefix)) {
            team.prefix(prefix);
        }
        if (!Objects.equals(team.suffix(), entry.suffix())) {
            team.suffix(entry.suffix());
        }
        // A new team has no color yet: Team#color() throws until one is set, so check hasColor() first.
        if (entry.color() != null && (!team.hasColor() || team.color() != entry.color())) {
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
            if (!v.equals(viewer) && !net.skybornmc.skyblock.common.visibility.Visibility.knows(viewer, v)) {
                visible--;
            }
        }
        boolean bedrock = core.bedrock().isBedrock(viewer.getUniqueId());
        ChatConfig c = config.get();
        String season = c.season;
        if (core.placeholders().has("season_display")) {
            season = core.placeholders().text(viewer, "season_display").orElse(season);
        }
        String staffKey = "";
        String tpsTier = null;
        String tpsValue = null;
        int ping = 0;
        if (viewer.hasPermission(ChatModule.PERM_TAB_STAFF)) {
            double tps = Math.min(20.0, Bukkit.getTPS()[0]);
            tpsTier = tpsTier(tps, c.tpsGood, c.tpsOk);
            tpsValue = String.format(Locale.ROOT, "%.1f", tps);
            ping = viewer.getPing();
            staffKey = staffKey(tpsTier, tpsValue, ping);
        }
        long generation = core.messages().generation();
        String headerKey = headerKey(bedrock, visible, Bukkit.getMaxPlayers(), season);
        String footerKey = footerKey(bedrock, staffKey);
        String sent = sentKey(generation, headerKey, footerKey);
        if (sent.equals(sentHeaderFooter.get(viewer.getUniqueId()))) {
            return; // the viewer already shows exactly this: nothing to build, nothing to send
        }
        int visibleCount = visible;
        int max = Bukkit.getMaxPlayers();
        String seasonText = season;
        Component header = headerCache.get(generation, headerKey, k -> msg.component(viewer, "tab.header",
                Msg.number("online", visibleCount), Msg.number("max", max), Msg.p("season", seasonText)));
        Component base = headerCache.get(generation, footerKey(bedrock, ""), k -> msg.component(viewer, "tab.footer"));
        Component footer = base;
        if (!staffKey.isEmpty()) {
            String tier = tpsTier;
            String value = tpsValue;
            int pingValue = ping;
            footer = headerCache.get(generation, footerKey, k -> {
                Component tpsText = msg.component(viewer, "tab.tps." + tier, Msg.p("value", value));
                TagResolver[] staff = {Msg.number("ping", pingValue), Msg.c("tps", tpsText)};
                return base.append(Component.newline()).append(msg.component(viewer, "tab.footer-staff", staff));
            });
        }
        viewer.sendPlayerListHeaderAndFooter(header, footer);
        sentHeaderFooter.put(viewer.getUniqueId(), sent);
    }

    /** Key of a header: every input of {@code tab.header} (platform, visible online, max players, season). Pure. */
    static String headerKey(boolean bedrock, int visible, int max, String season) {
        return "h|" + bedrock + '|' + visible + '|' + max + '|' + season;
    }

    /** Key of a footer: platform plus, for staff, {@link #staffKey} (empty for everybody else). Pure. */
    static String footerKey(boolean bedrock, String staffKey) {
        return "f|" + bedrock + '|' + staffKey;
    }

    /** Key of the staff line: TPS tier and shown value, ping. Pure. */
    static String staffKey(String tpsTier, String tpsValue, int ping) {
        return tpsTier + '|' + tpsValue + '|' + ping;
    }

    /** What a viewer was sent: both keys and the messages generation (a texts reload resends). Pure. */
    static String sentKey(long generation, String headerKey, String footerKey) {
        return generation + "\u0000" + headerKey + '\u0000' + footerKey;
    }

    // ------------------------------------------------------------------ events (mark only, no work)

    /** Color tier of the staff TPS line: {@code good} at or above {@code good}, {@code ok} at or above {@code ok}, else {@code bad}. */
    static String tpsTier(double tps, double good, double ok) {
        if (tps >= good) {
            return "good";
        }
        return tps >= ok ? "ok" : "bad";
    }

    /** {@code pxs} + (999 − weight, 3 digits) + lowercase name: sorts owner → default, then A → Z. */
    static String teamName(int weight, String playerName) {
        int inverted = MAX_WEIGHT - Math.max(0, Math.min(MAX_WEIGHT, weight));
        return TEAM_PREFIX + String.format(Locale.ROOT, "%03d", inverted) + playerName.toLowerCase(Locale.ROOT);
    }

    /** Something about {@code player} changed (nick, settings): re-check on the next tick. */
    void markDirty(UUID player) {
        if (running) {
            entries.computeIfPresent(player, (id, e) -> new Entry("", e.team(), e.prefix(), e.suffix(), e.color(), e.rank()));
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
        urgent.remove(id);
        rankViews.remove(id);
        rankForced.remove(id);
        sentHeaderFooter.remove(id);
        boardOwner.entrySet().removeIf(e -> id.equals(e.getValue()));
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
