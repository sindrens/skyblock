package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.npc.HologramTexts;
import net.skybornmc.skyblock.common.npc.NpcDirectory;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.LatestLoad;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * NPCs, holograms and leaderboard holograms at the points of the applied builds (NPCHOLO). Positions: the worlds
 * module's {@link BuildPoints} ({@code build-points.yml}); settings: {@code npcs.yml}; texts: {@code messages/npcs.yml}.
 * NPC bodies are Paper mannequins (or villagers without AI), never saved, respawned when their chunk loads and removed on
 * disable. Clicking an NPC runs its role's action or shows its help (owner answer 44); holograms with a help text get a
 * click box. Publishes {@link NpcDirectory} for the tutorial. Staff: {@code /skyblock npcs} (list, tp, respawn).
 */
public final class NpcsModule implements SkyblockModule {

    static final String ID = "npcs";
    /** Staff tools: {@code /skyblock npcs} (list menu, teleport, respawn). */
    public static final String PERM_ADMIN = "skyblock.admin.npcs";

    /** A text and its line count (for fitting it into a board). */
    record TextContent(Component text, int lines) {
    }

    private final Core core;
    private final NpcsConfig config = new NpcsConfig();
    private ModuleMessages msg;
    private volatile RoleRules roles = RoleRules.parse(List.of(), List.of());
    private volatile Spots spots = Spots.empty();
    private volatile Map<String, String> skins = Map.of();
    private volatile Map<String, Material> held = Map.of();
    /** PERF9: static hologram texts by point ref and NPC name texts by NPC ref, built off the main thread (NpcPlan). */
    private volatile Map<String, TextContent> hologramTexts = Map.of();
    private volatile Map<String, Component> labels = Map.of();
    /** PERF9: made once in enable() so npcs:start only hands work to the loader (no lambda bootstrap in that tick). */
    private java.util.function.Supplier<Planned> planner;
    private java.util.function.Consumer<Planned> applier;
    private java.util.function.BiConsumer<Boolean, Throwable> placeFailed;
    private Placer placer;
    private LatestLoad<Planned> loader;

    /**
     * PERF10: a plan plus the task that writes its log lines, made on the loader thread (so the apply tick doesn't link
     * that lambda for the first time in this JVM).
     */
    private record Planned(NpcPlan plan, Runnable log) {
    }
    private Leaderboards boards;
    private NpcListener listener;
    private HelpDialog help;
    /**
     * M10-3: live hologram texts other modules write ({@link HologramTexts}), by hologram id. Kept across a disable/enable
     * of this module so the owners' texts survive it; an owner clears its own texts when it disables.
     */
    private final Map<String, LiveText> liveTexts = new java.util.concurrent.ConcurrentHashMap<>();

    /** One live text: who set it and its lines. */
    private record LiveText(String owner, List<Component> lines) {
    }

    public NpcsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("worlds");
    }

    @Override
    public void enable() {
        core.configs().load(config);
        msg = core.messages(ID);
        placer = new Placer(this);
        boards = new Leaderboards(this);
        help = new HelpDialog(this);
        listener = new NpcListener(this);
        Bukkit.getPluginManager().registerEvents(placer, core.plugin());
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        core.services().register(ID, NpcDirectory.class, new Directory());
        core.services().register(ID, HologramTexts.class, new Texts());
        loader = new LatestLoad<>(r -> core.scheduler().async(r), r -> core.scheduler().main("npcs:apply", r));
        planner = this::computePlan;
        applier = this::apply;
        placeFailed = (applied, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "[npcs] could not place the NPCs and holograms", error);
            }
        };
        // One tick later: worlds are loaded and services of modules enabled after this one exist. PERF9: this tick only
        // starts the loader; reading the settings and build points happens on the loader thread.
        core.scheduler().later("npcs:start", 1, this::place);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        if (placer != null) {
            place();
        }
    }

    @Override
    public void disable() {
        if (placer == null) {
            return;
        }
        loader.cancel();
        boards.stop();
        placer.stop();
        HandlerList.unregisterAll(placer);
        HandlerList.unregisterAll(listener);
        core.services().unregisterAll(ID);
        placer = null;
    }

    /**
     * Reads the rules and points again and respawns everything. PERF3: the parsing runs off the main thread
     * ({@link NpcPlan}), the result is applied on it ({@code npcs:apply}), and the spawns are spread over the next ticks
     * ({@code npcs:spawn}, {@link SpawnQueue}). Completes on the main thread once applied (false when a newer call won).
     * PERF9: the settings snapshot and the build points are read on the loader thread too (a reload meanwhile starts a
     * newer load, which wins), so this only hands the work over.
     */
    private CompletableFuture<Boolean> place() {
        if (placer == null || loader == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> done = loader.load(planner, applier);
        done.whenComplete(placeFailed);
        return done;
    }

    /**
     * Loader thread: the plan with everything that needs no world access (rules, points, tracked sets, per-chunk map,
     * hologram and name texts). Thread-safe reads only: config fields (replaced whole on reload), the service registry,
     * messages.
     */
    private Planned computePlan() {
        NpcPlan.Inputs inputs = NpcPlan.Inputs.of(config);
        BuildPoints points = core.services().optional(BuildPoints.class).orElse(null);
        NpcPlan plan = NpcPlan.compute(inputs, points, NpcsModule::heldMaterial, this::hasHologramText, new NpcPlan.Texts() {
            @Override
            public TextContent hologram(Spots.Text t) {
                return staticHologramContent(t);
            }

            @Override
            public Component label(RoleRules rules, Spots.Npc npc) {
                return npcLabel(rules, npc);
            }
        });
        // PERF9: the lines were built with the plan; writing them to the console happens off the main thread.
        List<String> lines = plan.logLines();
        int problems = plan.problems().size();
        Runnable log = () -> {
            var logger = core.plugin().getLogger();
            for (int i = 0; i < lines.size(); i++) {
                if (i < problems) {
                    logger.warning(lines.get(i));
                } else {
                    logger.info(lines.get(i));
                }
            }
        };
        return new Planned(plan, log);
    }

    /**
     * Main thread: takes over a parsed plan and (re)places everything. PERF10: no lambda or method reference is created
     * in this tick any more (each one linked for the first time in this JVM costs the main thread a hidden class spin):
     * the parts are timed by hand, the log task comes with the plan, and the task bodies are fields made at enable.
     */
    private void apply(Planned planned) {
        if (placer == null) {
            return;
        }
        NpcPlan plan = planned.plan();
        roles = plan.roles();
        skins = plan.skins();
        held = plan.held();
        spots = plan.spots();
        hologramTexts = plan.hologramTexts();
        labels = plan.labels();
        // PERF8: every part of the apply is timed on its own (nested inside npcs:apply): npcs:apply-boards, and inside
        // npcs:apply-place: npcs:apply-retire, npcs:apply-track, npcs:apply-queue. The left-over walk is npcs:purge (now in
        // the first npcs:spawn tick).
        var timer = core.scheduler().timer();
        long start = System.nanoTime();
        boards.start(plan.sources());
        long boardsDone = System.nanoTime();
        timer.record("npcs:apply-boards", boardsDone - start);
        boolean look = config.npcsEnabled && config.lookAtPlayers && !plan.spots().npcs().isEmpty();
        placer.start(plan.index(), look);
        timer.record("npcs:apply-place", System.nanoTime() - boardsDone);
        core.scheduler().async(planned.log());
    }

    /** A held-item name to an item, null when it is not one (thread-safe: enum and frozen registry lookups). */
    private static Material heldMaterial(String name) {
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() || m.isAir() ? null : m;
    }

    /** Same test as {@link #hologramContent} returning non-null, without building the text (any thread). */
    private boolean hasHologramText(Spots.Text t) {
        if (liveTexts.containsKey(t.id())) {
            return true;
        }
        String raw = raw("holograms." + t.id());
        return (raw != null && !raw.isBlank()) || !t.fallbackText().isBlank();
    }

    // ------------------------------------------------------------------ shared helpers

    Core core() {
        return core;
    }

    NpcsConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    RoleRules roles() {
        return roles;
    }

    Placer placer() {
        return placer;
    }

    HelpDialog help() {
        return help;
    }

    Map<String, String> skins() {
        return skins;
    }

    /** A per-NPC value, else the role's value, else null. */
    <T> T mapped(Map<String, T> map, String npcId, String role) {
        T v = map.get(npcId);
        return v != null ? v : map.get(role);
    }

    Material heldItem(String npcId, String role) {
        return mapped(held, npcId, role);
    }

    /** Ids of the leaderboards from the builds (immutable, any thread). */
    List<String> boardIds() {
        return spots.boards().stream().map(Spots.Text::id).toList();
    }

    /** A message for no particular viewer (holograms are the same for everyone; Java colors, Geyser converts). */
    Component text(String key, TagResolver... placeholders) {
        return msg.component(Audience.empty(), key, placeholders);
    }

    /** A message as plain text ({@code ""} when missing). */
    String plain(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? "" : MiniMessage.miniMessage().stripTags(raw);
    }

    private String raw(String key) {
        return core.messages().raw(ID, key);
    }

    /** {@code roles.<role>.name}-style name of an NPC (MiniMessage from our file): per id, per role, else the id. */
    String npcNameRaw(String npcId, String role) {
        String byId = raw("npc-names." + npcId);
        if (byId != null && !byId.isBlank()) {
            return byId;
        }
        String byRole = raw("roles." + role + ".name");
        if (byRole != null && !byRole.isBlank()) {
            return byRole;
        }
        return pretty(npcId);
    }

    /** {@code black_market} → {@code Black Market}. */
    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("[_-]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return MiniMessage.miniMessage().escapeTags(sb.toString());
    }

    /** The name text above an NPC: name + one short hint line (PERF9: built with the plan, else here). */
    Component npcLabel(Spots.Npc npc) {
        Component built = labels.get(npc.ref());
        return built != null ? built : npcLabel(roles, npc);
    }

    /** The name text above an NPC with these role rules (any thread: message parsing only). */
    private Component npcLabel(RoleRules roles, Spots.Npc npc) {
        RoleRules.Action action = roles.actionOf(npc.role());
        Component hint = text(action.type() == RoleRules.Type.COMMAND ? "npc.hint-action" : "npc.hint-help");
        return text("npc.label", Msg.mm("name", npcNameRaw(npc.id(), npc.role())), Msg.c("hint", hint));
    }

    /**
     * A hologram's text: a live text (M10-3 {@link HologramTexts}), else {@code holograms.<id>}, else the metadata's text;
     * {@code null} = nothing to show. PERF9: the static text comes from the plan when it was built there.
     */
    TextContent hologramContent(Spots.Text t) {
        LiveText live = liveTexts.get(t.id());
        if (live != null) {
            return new TextContent(Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), live.lines()),
                    live.lines().size());
        }
        TextContent built = hologramTexts.get(t.ref());
        return built != null ? built : staticHologramContent(t);
    }

    /** A hologram's own text without live texts (any thread: message parsing only); null when it has none. */
    private TextContent staticHologramContent(Spots.Text t) {
        String raw = raw("holograms." + t.id());
        if (raw != null && !raw.isBlank()) {
            return new TextContent(text("holograms." + t.id()), Layout.lineCount(raw));
        }
        if (!t.fallbackText().isBlank()) {
            return new TextContent(text("hologram-fallback", Msg.p("text", Layout.bedrockSafe(t.fallbackText()))), 1);
        }
        return null;
    }

    /** A leaderboard's text: title, exactly {@code size} places (or "Coming soon"), footer. */
    TextContent boardContent(Spots.Text t) {
        String titleRaw = raw("leaderboard.titles." + t.id());
        TagResolver title = titleRaw != null && !titleRaw.isBlank() ? Msg.mm("title", titleRaw) : Msg.mm("title", pretty(t.id()));
        List<Component> lines = new ArrayList<>();
        lines.add(text("leaderboard.title", title));
        int count = Layout.lineCount(raw("leaderboard.title"));
        if (boards.sourceOf(t.id()) instanceof BoardSource.None) {
            lines.add(text("leaderboard.coming-soon"));
            count += Layout.lineCount(raw("leaderboard.coming-soon"));
        } else {
            for (Leaderboards.Line line : Leaderboards.lines(boards.rowsOf(t.id()), config.size)) {
                Leaderboards.Row row = line.row();
                lines.add(text(line.key(), Msg.number("place", line.place()),
                        Msg.p("player", row == null ? "" : row.player()),
                        Msg.c("value", row == null ? Component.empty() : row.value())));
            }
            count += config.size;
            lines.add(text("leaderboard.footer", Msg.number("minutes", config.refreshMinutes)));
            count += Layout.lineCount(raw("leaderboard.footer"));
        }
        return new TextContent(Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines), count);
    }

    /** True when a hologram or board has a help text (gets a click box). */
    boolean hasHelp(Placed p) {
        return core.messages().has(ID, "hologram-help." + p.pointId());
    }

    /** New rows for a source: re-sets the text of every placed board that shows it. */
    void boardUpdated(String sourceKey) {
        if (placer == null) {
            return;
        }
        for (Placed p : placer.all()) {
            if (p.kind == Placed.Kind.BOARD && p.spawned() && boards.sourceOf(p.pointId()).key().equals(sourceKey)) {
                placer.setText(p, boardContent(p.text).text());
            }
        }
    }

    // ------------------------------------------------------------------ staff tools (/skyblock npcs)

    /** Ids for {@code /skyblock npcs tp <id>} tab completion: NPCs, holograms and boards. */
    public List<String> ids() {
        if (placer == null) {
            return List.of();
        }
        return placer.all().stream().map(p -> p.id).distinct().sorted().toList();
    }

    /** {@code /skyblock npcs [list]}: the menu for players, a chat list for the console. */
    public void list(CommandSender sender) {
        if (placer == null) {
            return;
        }
        if (sender instanceof Player player) {
            core.menus().open(player, new NpcListMenu(this));
            return;
        }
        msg.send(sender, "admin.list.header", Msg.number("npcs", spots.npcs().size()),
                Msg.number("holograms", spots.holograms().size()), Msg.number("boards", spots.boards().size()));
        for (Placed p : placer.all()) {
            World w = Bukkit.getWorld(p.world);
            Location at = p.where(w);
            msg.send(sender, "admin.list.line", Msg.p("kind", plain("admin.kind." + p.kind.name().toLowerCase(Locale.ROOT))),
                    Msg.p("id", p.id), Msg.p("world", p.world), Msg.number("x", at.getBlockX()), Msg.number("y", at.getBlockY()),
                    Msg.number("z", at.getBlockZ()),
                    Msg.p("state", plain(p.spawned() ? "admin.state.spawned" : "admin.state.unloaded")));
        }
    }

    /** {@code /skyblock npcs tp <id>}: teleports staff in front of an NPC / hologram / board. */
    public void teleport(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "admin.players-only");
            return;
        }
        if (placer == null) {
            return;
        }
        Placed p = null;
        for (Placed.Kind kind : Placed.Kind.values()) {
            p = placer.get(kind, id.toLowerCase(Locale.ROOT)).orElse(null);
            if (p != null) {
                break;
            }
        }
        if (p == null) {
            msg.send(sender, "admin.tp.unknown", Msg.p("id", id));
            return;
        }
        teleport(player, p);
    }

    /** Teleports {@code player} 2 blocks in front of a placed set, facing it. Audited (staff). */
    void teleport(Player player, Placed p) {
        World w = Bukkit.getWorld(p.world);
        if (w == null) {
            msg.send(player, "admin.tp.no-world", Msg.p("world", p.world));
            return;
        }
        Location at = p.where(w);
        float facing = p.npc != null ? p.npc.yaw() : (p.text.faced() ? p.text.yaw() : at.getYaw());
        double[] dir = Layout.direction(facing);
        Location target = at.clone().add(dir[0] * 2.5, 0, dir[1] * 2.5);
        if (p.npc == null) {
            target.setY(standY(w, target, Layout.bottomY(p.text.y(), p.text.height())));
        }
        target.setYaw(facing + 180f);
        target.setPitch(0f);
        String id = p.id;
        player.teleportAsync(target).thenAccept(ok -> {
            if (ok) {
                msg.send(player, "admin.tp.done", Msg.p("id", id));
            }
        });
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", p.kind.name().toLowerCase(Locale.ROOT));
        details.put("id", id);
        core.audit().log(AuditCategory.STAFF, "npc_teleport", Actor.of(player), id, null, target, details);
    }

    /**
     * Feet height for staff in front of a board: the first floor at most 16 blocks below {@code fromY} (signposts hang
     * high above the road); {@code fromY} itself when the chunk isn't loaded or there is no floor.
     */
    private static double standY(World w, Location at, double fromY) {
        if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return fromY;
        }
        int top = (int) Math.floor(fromY);
        for (int y = top; y > top - 16 && y > w.getMinHeight(); y--) {
            if (w.getBlockAt(at.getBlockX(), y - 1, at.getBlockZ()).getType().isSolid()
                    && !w.getBlockAt(at.getBlockX(), y, at.getBlockZ()).getType().isSolid()
                    && !w.getBlockAt(at.getBlockX(), y + 1, at.getBlockZ()).getType().isSolid()) {
                return y;
            }
        }
        return fromY;
    }

    /** {@code /skyblock npcs respawn}: re-reads the build points and settings and respawns everything. Audited. */
    public void respawn(CommandSender sender) {
        if (placer == null) {
            return;
        }
        place().whenComplete((applied, error) -> {
            if (error != null || placer == null) {
                return; // logged by place()
            }
            boards.refresh();
            msg.send(sender, "admin.respawned", Msg.number("total", spots.total()));
            core.audit().log(AuditCategory.CONFIG, "npcs_respawn", Actor.of(sender), ID, (long) spots.total(), null, null);
        });
    }

    // ------------------------------------------------------------------ HologramTexts (M10-3)

    /** Live hologram texts of other modules (the crate yard's key board). Main thread. */
    private final class Texts implements HologramTexts {

        @Override
        public void set(String owner, String hologramId, List<Component> lines) {
            if (lines == null || lines.isEmpty()) {
                clear(owner, hologramId);
                return;
            }
            liveTexts.put(hologramId, new LiveText(owner, List.copyOf(lines)));
            changed(hologramId);
        }

        @Override
        public void clear(String owner, String hologramId) {
            LiveText live = liveTexts.get(hologramId);
            if (live != null && live.owner().equals(owner)) {
                liveTexts.remove(hologramId);
                changed(hologramId);
            }
        }

        @Override
        public void clearAll(String owner) {
            List<String> ids = liveTexts.entrySet().stream().filter(e -> e.getValue().owner().equals(owner)).map(Map.Entry::getKey)
                    .toList();
            ids.forEach(id -> clear(owner, id));
        }

        private void changed(String hologramId) {
            if (placer != null) {
                placer.respawnHolograms(hologramId);
            }
        }
    }

    // ------------------------------------------------------------------ NpcDirectory

    /** The service for the tutorial and quests (main thread). */
    private final class Directory implements NpcDirectory {

        @Override
        public List<NpcInfo> npcs() {
            List<NpcInfo> out = new ArrayList<>();
            for (Spots.Npc n : spots.npcs()) {
                World w = Bukkit.getWorld(n.world());
                if (w != null) {
                    out.add(new NpcInfo(n.id(), n.role(), location(n, w)));
                }
            }
            return out;
        }

        @Override
        public java.util.Optional<Location> npcLocation(String npcId) {
            return first(n -> n.id().equalsIgnoreCase(npcId)).map(NpcInfo::location);
        }

        @Override
        public java.util.Optional<NpcInfo> npcByRole(String role) {
            return first(n -> n.role().equalsIgnoreCase(role));
        }

        /**
         * PERF4: the first NPC in a loaded world matching {@code test}, same order as {@link #npcs()}, without building
         * a location for every NPC (the tutorial guides ask for the Forge/help NPC every few ticks).
         */
        private java.util.Optional<NpcInfo> first(java.util.function.Predicate<Spots.Npc> test) {
            for (Spots.Npc n : spots.npcs()) {
                if (!test.test(n)) {
                    continue;
                }
                World w = Bukkit.getWorld(n.world());
                if (w != null) {
                    return java.util.Optional.of(new NpcInfo(n.id(), n.role(), location(n, w)));
                }
            }
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Location> hologramLocation(String hologramId) {
            if (placer == null) {
                return java.util.Optional.empty();
            }
            String id = hologramId.toLowerCase(Locale.ROOT);
            return placer.get(Placed.Kind.HOLOGRAM, id).or(() -> placer.get(Placed.Kind.BOARD, id))
                    .map(p -> p.where(Bukkit.getWorld(p.world)));
        }

        @Override
        public java.util.Optional<String> npcId(org.bukkit.entity.Entity entity) {
            if (placer == null) {
                return java.util.Optional.empty();
            }
            Placed p = placer.find(entity);
            return p != null && p.kind == Placed.Kind.NPC ? java.util.Optional.of(p.pointId()) : java.util.Optional.empty();
        }

        private Location location(Spots.Npc n, World w) {
            if (placer != null) {
                Placed p = placer.get(Placed.Kind.NPC, n.ref()).orElse(null);
                if (p != null && p.location != null) {
                    return p.location.clone();
                }
            }
            return new Location(w, Layout.centre(n.x()), n.y(), Layout.centre(n.z()), n.yaw(), 0f);
        }
    }
}
