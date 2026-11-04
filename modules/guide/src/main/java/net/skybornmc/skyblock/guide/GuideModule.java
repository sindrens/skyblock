package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.progression.IslandLevelUpEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The island guide Kindle (docs/planning/ISLAND_GUIDE.md, owner 2 Nov 2026): one helper NPC per island that opens
 * his quests and finds, the island level, upgrades, tasks, milestones, island tools, /farm, /mine and the shared next
 * step (plan 4 Nov C §2), and tells members when an
 * island level-up brought something new. {@code /is guide} opens him anywhere, {@code /is guide move} moves him.
 */
public final class GuideModule implements SkyblockModule, Listener {

    public static final String ID = "guide";
    static final String PERM = "skyblock.island.guide";
    static final String PERM_MOVE = "skyblock.island.guide.move";
    private static final long CLICK_GAP_MS = 400;

    private final Core core;
    private final GuideConfig config = new GuideConfig();
    private ModuleMessages msg;
    private GuideStore store;
    private GuideNpcs npcs;
    private GuideQuests quests;
    /** player → rotation in which Kindle last spoke to them. */
    private final Map<UUID, Long> spokeIn = new ConcurrentHashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public GuideModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("islands");
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        msg = core.messages(ID);
        store = new GuideStore(core.database());
        npcs = new GuideNpcs(this);
        quests = new GuideQuests(this);
        quests.catalog(loadCatalog());
        store.all().whenComplete((spots, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Guide: could not read moved guide spots (they stand at the home)", error);
            } else {
                Bukkit.getScheduler().runTask(core.plugin(), () -> npcs.spots(spots));
            }
        });
        Bukkit.getPluginManager().registerEvents(npcs, core.plugin());
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        long check = 20L * config.checkSeconds;
        tasks.add(core.scheduler().repeat("guide:sweep", 40L, check, npcs::sweep));
        tasks.add(core.scheduler().repeat("guide:look", 20L, 10L, npcs::look));
        tasks.add(core.scheduler().repeat("guide:talk", 60L, 40L, this::talkNearby));
        tasks.add(core.scheduler().repeat("guide:prune", 72_000L, 72_000L, () -> {
            quests.prune();
            store.purge(System.currentTimeMillis() - 30L * 24 * 3_600_000L);
        }));
        core.commands().extend("is", guideCommand());
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        quests.catalog(loadCatalog());
        npcs.despawnAll(); // respawned with the new look on the next sweep
    }

    @Override
    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        if (npcs != null) {
            npcs.despawnAll();
            HandlerList.unregisterAll(npcs);
        }
        HandlerList.unregisterAll(this);
    }

    // ------------------------------------------------------------------ access

    Core core() {
        return core;
    }

    GuideConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    GuideStore store() {
        return store;
    }

    GuideQuests quests() {
        return quests;
    }

    /** guide-quests.yml from the data folder (copied from the jar on first start; new sections are never merged in). */
    private GuideCatalog loadCatalog() {
        java.io.File file = new java.io.File(core.plugin().getDataFolder(), GuideCatalog.FILE);
        if (!file.exists()) {
            core.plugin().saveResource(GuideCatalog.FILE, false);
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        java.util.function.Predicate<String> item = id -> {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(id);
            return mat != null && mat.isItem() && !mat.isAir();
        };
        java.util.function.Predicate<String> crate = id -> core.services().optional(net.skybornmc.skyblock.common.crate.CrateKeys.class)
                .map(k -> k.exists(id)).orElse(true);
        GuideCatalog c = GuideCatalog.load(yaml, item, crate, w -> core.plugin().getLogger().warning(w));
        core.plugin().getLogger().info("Guide: " + c.all(GuideCatalog.Kind.STORY).size() + " story quests, "
                + c.all(GuideCatalog.Kind.ERRAND).size() + " errands, " + c.all(GuideCatalog.Kind.TRADE).size() + " finds.");
        return c;
    }

    /**
     * Owner GD-2: Kindle speaks to a member who comes close, once per rotation: "you have everything for my quest",
     * "could you bring me ...", or "I have new quests for you" (never a timer).
     */
    private void talkNearby() {
        if (!config.enabled || config.talkRadius <= 0) {
            return;
        }
        IslandService islands = islands().orElse(null);
        if (islands == null) {
            return;
        }
        long period = quests.period();
        double r2 = config.talkRadius * config.talkRadius;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!islands.isIslandWorld(p.getWorld())) {
                continue;
            }
            Long last = spokeIn.get(p.getUniqueId());
            if (last != null && last == period) {
                continue;
            }
            Optional<IslandService.IslandInfo> here = islands.islandAt(p.getLocation());
            if (here.isEmpty() || !islands.isMember(here.get().id(), p.getUniqueId())) {
                continue;
            }
            UUID island = here.get().id();
            Optional<Location> spot = npcs.spot(island);
            if (spot.isEmpty() || spot.get().getWorld() != p.getWorld() || spot.get().distanceSquared(p.getLocation()) > r2) {
                continue;
            }
            GuideCatalog.Entry story = quests.currentStory(island, null);
            if (!quests.storyLoaded(island)) {
                continue; // loading: speak on the next pass
            }
            spokeIn.put(p.getUniqueId(), period);
            if (story != null && story.level() <= quests.islandLevel(island) && quests.has(p, story)) {
                msg.send(p, "talk.story-ready");
            } else if (story != null && story.level() <= quests.islandLevel(island)) {
                Map.Entry<String, Integer> first = story.cost().entrySet().iterator().next();
                msg.send(p, "talk.story-ask", Msg.number("amount", first.getValue()), Msg.p("item", GuideTasksMenu.pretty(first.getKey())));
            } else {
                msg.send(p, "talk.new-quests");
            }
            core.sounds().play(p, "notify");
        }
    }

    /** The guide book (owner GD-2): how to play, in a written book (Java and Bedrock). */
    void openBook(Player p) {
        List<Component> pages = new ArrayList<>();
        for (int i = 1; i <= 50 && msg.has("book.page-" + i); i++) {
            pages.add(msg.component(p, "book.page-" + i));
        }
        if (pages.isEmpty()) {
            return;
        }
        p.openBook(net.kyori.adventure.inventory.Book.book(msg.component(p, "book.title"), msg.component(p, "book.author"), pages));
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    // ------------------------------------------------------------------ talking

    /** A click on a guide: members get his menu, visitors a short line. */
    void talk(Player player, UUID island) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_GAP_MS) {
            return;
        }
        IslandService islands = islands().orElse(null);
        if (islands == null) {
            return;
        }
        if (!player.hasPermission(PERM)) {
            msg.send(player, "no-permission");
            return;
        }
        if (!islands.isMember(island, player.getUniqueId())) {
            String name = islands.island(island).map(IslandService.IslandInfo::name).orElse("?");
            msg.send(player, "npc.visitor", Msg.p("island", name));
            core.sounds().play(player, "click");
            return;
        }
        open(player, island);
    }

    void open(Player player, UUID island) {
        boolean news = npcs.hasNews(island);
        npcs.clearNews(island);
        core.menus().open(player, new GuideMenu(this, island, news));
    }

    // ------------------------------------------------------------------ command

    private CommandSpec guideCommand() {
        return CommandSpec.sub("guide").description("Talk to your island guide Kindle: island level, upgrades, tasks, tools and your next step")
                .permission(PERM).playerOnly().example("is guide")
                .executes(ctx -> {
                    Player p = ctx.player();
                    UUID island = ownIsland(p);
                    open(p, island);
                })
                .sub(CommandSpec.sub("move").description("Move your island guide to where you stand (leader and admins)")
                        .permission(PERM_MOVE).playerOnly().example("is guide move")
                        .executes(ctx -> move(ctx.player())));
    }

    private UUID ownIsland(Player p) {
        return islands().flatMap(i -> i.islandOf(p.getUniqueId())).map(IslandService.IslandInfo::id)
                .orElseThrow(() -> new CommandFailure(ID, "no-island"));
    }

    private void move(Player p) {
        IslandService islands = islands().orElseThrow(() -> new CommandFailure(ID, "no-island"));
        IslandService.IslandInfo info = islands.islandOf(p.getUniqueId()).orElseThrow(() -> new CommandFailure(ID, "no-island"));
        if (!GuideRules.mayMove(islands.role(info.id(), p.getUniqueId()).orElse(null))) {
            throw new CommandFailure(ID, "move.not-allowed");
        }
        Location at = p.getLocation();
        if (!islands.isIslandWorld(at.getWorld()) || !GuideRules.inside(info.bounds(), at.getX(), at.getZ())) {
            throw new CommandFailure(ID, "move.outside");
        }
        GuideStore.Spot spot = new GuideStore.Spot(at.getWorld().getName(), at.getBlockX() + 0.5, at.getBlockY(), at.getBlockZ() + 0.5,
                at.getYaw());
        npcs.move(info.id(), spot);
        store.save(info.id(), spot, p.getUniqueId()).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Guide: could not save the moved spot of island " + info.id(), e);
            return null;
        });
        core.audit().log(AuditCategory.ISLAND, "guide_move", Actor.player(p), info.id().toString(), null, null, at,
                Map.of("x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ()));
        msg.send(p, "move.done");
        core.sounds().play(p, "success");
    }

    // ------------------------------------------------------------------ events

    /** Owner idea: when the island grows, Kindle has something new (new challenge tiers, upgrades). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(IslandLevelUpEvent event) {
        if (!event.rose() || !config.announceLevelUps) {
            return;
        }
        npcs.markNews(event.islandId());
        IslandService islands = islands().orElse(null);
        if (islands == null) {
            return;
        }
        for (UUID member : islands.members(event.islandId()).keySet()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                msg.send(p, "news.level-up", Msg.number("level", event.newLevel()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoved(IslandRemovedEvent event) {
        npcs.forget(event.islandId());
        quests.forget(event.islandId());
        store.delete(event.islandId());
    }

}
