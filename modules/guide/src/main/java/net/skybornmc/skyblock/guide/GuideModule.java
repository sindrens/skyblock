package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
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
 * challenges, quests, milestones, upgrades, masteries, the island level, the Depths and help, and tells members when an
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
        core.commands().extend("is", guideCommand());
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
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
        return CommandSpec.sub("guide").description("Talk to your island guide Kindle: challenges, quests, upgrades, the Depths, help")
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
    public void onRemoved(IslandRemovedEvent event) {
        npcs.forget(event.islandId());
        store.delete(event.islandId());
    }

}
