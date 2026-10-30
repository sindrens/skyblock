package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.depths.DepthsZones;
import net.skybornmc.skyblock.common.economy.BankDepositEvent;
import net.skybornmc.skyblock.common.island.IslandJoinedEvent;
import net.skybornmc.skyblock.common.island.IslandPortalEnteredEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.MenuOpenedEvent;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.sell.ItemsSoldEvent;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.skills.SkillXpGainEvent;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns game events into tutorial "done" signals (TUTORIAL.md §3 "Done when"): portal, starter chest, crops, menus,
 * Depths zone, mining XP, sales, Forge, NPC clicks and bank deposits. Every handler is cheap and returns at once for
 * players without a running tutorial. Main thread.
 */
final class TutorialListener implements Listener {

    private final TutorialModule module;
    private final Core core;

    TutorialListener(TutorialModule module) {
        this.module = module;
        this.core = module.core();
    }

    private TutorialEngine engine() {
        return module.engine();
    }

    private Progress running(Player player) {
        Progress p = engine().progress(player.getUniqueId());
        return p != null && p.state == State.ACTIVE ? p : null;
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        engine().load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        engine().quit(event.getPlayer());
    }

    // ------------------------------------------------------------------ 1 portal

    /** PORTALS: the island portal created (or found) the island. Starts the tutorial when it never ran (TUTORIAL §8). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPortal(IslandPortalEnteredEvent event) {
        Player player = event.getPlayer();
        Progress p = engine().progress(player.getUniqueId());
        if (p == null || !module.config().enabled) {
            return;
        }
        if (p.state == State.NEW) {
            if (player.hasPermission(TutorialEngine.PERM_BYPASS)) {
                if (event.firstTime()) {
                    module.msg().send(player, "start.staff-offer");
                }
                return;
            }
            engine().start(player);
        }
        engine().signal(player, Steps.PORTAL);
    }

    // ------------------------------------------------------------------ 3 chest (or the Starter pack for team joiners)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || running(player) == null) {
            return;
        }
        Progress p = running(player);
        if (p.isDone(Steps.CHEST) || !isStarterChest(player, p, event.getInventory().getHolder(false))) {
            return;
        }
        p.chestOpened = true;
        module.msg().actionBar(player, "steps.chest.opened");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Progress p = running(player);
        if (p != null && p.chestOpened && !p.isDone(Steps.CHEST)) {
            core.scheduler().later("tutorial-chest-check", 1, () -> checkChest(player));
        }
    }

    void checkChest(Player player) {
        Progress p = running(player);
        if (p == null || !p.chestOpened || p.isDone(Steps.CHEST)) {
            return;
        }
        if (hasTool(player)) {
            engine().signal(player, Steps.CHEST);
        }
    }

    private boolean isStarterChest(Player player, Progress p, InventoryHolder holder) {
        Location loc = switch (holder) {
            case Chest chest -> chest.getLocation();
            case DoubleChest dc -> dc.getLocation();
            case null, default -> null;
        };
        if (loc == null) {
            return false;
        }
        Optional<Location> known = module.targets().chest(p);
        if (known.isPresent()) {
            Location k = known.get();
            if (holder instanceof DoubleChest dc) {
                return near(dc.getLeftSide(), k) || near(dc.getRightSide(), k);
            }
            return k.getWorld().equals(loc.getWorld()) && k.getBlockX() == loc.getBlockX() && k.getBlockY() == loc.getBlockY()
                    && k.getBlockZ() == loc.getBlockZ();
        }
        // Unknown chest point: any chest on the player's own island counts (only members open containers there).
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        UUID key = ProfileService.of(core.services()).profileKey(player);
        return islands.map(i -> i.islandAt(loc).map(info -> i.isMember(info.id(), key)).orElse(false)).orElse(true);
    }

    private static boolean near(InventoryHolder side, Location k) {
        if (!(side instanceof BlockState state)) {
            return false;
        }
        Location l = state.getLocation();
        return l.getWorld().equals(k.getWorld()) && l.getBlockX() == k.getBlockX() && l.getBlockY() == k.getBlockY() && l.getBlockZ() == k.getBlockZ();
    }

    /** {@code steps.chest.need-tool} in the inventory (a starter tool of that kind; any tool of it without the tools module). */
    boolean hasTool(Player player) {
        String need = module.config().chestTool == null ? "" : module.config().chestTool.strip().toLowerCase(Locale.ROOT);
        if (need.isEmpty()) {
            return true;
        }
        Optional<ToolService> tools = core.services().optional(ToolService.class);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (tools.isPresent()) {
                if (tools.get().read(stack).map(t -> t.kind() == ToolKind.byId(need).orElse(null)).orElse(false)) {
                    return true;
                }
            } else if (stack.getType().getKey().getKey().endsWith("_" + need) || stack.getType().getKey().getKey().equals(need)) {
                return true;
            }
        }
        return false;
    }

    /** §6.1: a new player who joins a team gets the Starter pack; the chest step is done with it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinedIsland(IslandJoinedEvent event) {
        Player player = event.getPlayer();
        Progress p = engine().progress(player.getUniqueId());
        if (p == null || p.state.complete()) {
            return;
        }
        if (module.starter().givePack(player) && p.state == State.ACTIVE) {
            engine().signal(player, Steps.PORTAL);
            engine().signal(player, Steps.ARRIVE);
            engine().signal(player, Steps.CHEST);
        }
    }

    // ------------------------------------------------------------------ 4 crop

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Progress p = running(player);
        if (p == null || p.isDone(Steps.CROP)) {
            return;
        }
        String block = event.getBlockPlaced().getType().getKey().getKey();
        if (module.config().cropBlocks.stream().noneMatch(b -> b.strip().equalsIgnoreCase(block))) {
            return;
        }
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        UUID key = ProfileService.of(core.services()).profileKey(player);
        boolean own = islands.map(i -> i.islandAt(event.getBlockPlaced().getLocation()).map(info -> i.isMember(info.id(), key))
                .orElse(false)).orElse(true);
        if (own) {
            engine().count(player, Steps.CROP, 1, module.config().cropCount);
        }
    }

    // ------------------------------------------------------------------ 5 menu, 9 forge

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenu(MenuOpenedEvent event) {
        Player player = event.getPlayer();
        if (running(player) == null) {
            return;
        }
        String id = event.menuId();
        if (module.config().menuIds.stream().anyMatch(m -> m.strip().equalsIgnoreCase(id))) {
            engine().signal(player, Steps.MENU);
        }
        if (module.config().forgeMenuIds.stream().anyMatch(m -> m.strip().equalsIgnoreCase(id))) {
            engine().signal(player, Steps.FORGE);
        }
    }

    // ------------------------------------------------------------------ 6 depths (checked by the guide tick)

    /** Called every guide tick for running players: the Depths zone check and the chest re-check. */
    void tick(Player player, Progress p) {
        if (Steps.DEPTHS.equals(p.step)) {
            checkZone(player);
        }
    }

    void checkZone(Player player) {
        Progress p = running(player);
        if (p == null || p.isDone(Steps.DEPTHS)) {
            return;
        }
        Optional<DepthsZones> zones = core.services().optional(DepthsZones.class);
        if (zones.isPresent() && zones.get().zoneAt(player.getLocation()).map(z -> z.equalsIgnoreCase(module.config().depthsZone)).orElse(false)) {
            engine().signal(player, Steps.DEPTHS);
        }
    }

    // ------------------------------------------------------------------ 7 mine

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXp(SkillXpGainEvent event) {
        Player player = event.getPlayer();
        Progress p = running(player);
        if (p == null || p.isDone(Steps.MINE) || event.getSkill() != SkillType.MINING) {
            return;
        }
        boolean depths = core.services().optional(DepthsZones.class).map(d -> d.inDepths(player.getLocation())).orElse(false);
        if (depths) {
            engine().count(player, Steps.MINE, 1, module.config().mineCount);
        }
    }

    // ------------------------------------------------------------------ 8 sell

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSold(ItemsSoldEvent event) {
        Player player = event.getPlayer();
        if (running(player) == null) {
            return;
        }
        var sources = module.config().sellSources;
        if (sources.isEmpty() || sources.stream().anyMatch(s -> s.strip().equalsIgnoreCase(event.source()))) {
            engine().signal(player, Steps.SELL);
        }
    }

    // ------------------------------------------------------------------ 10 help

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpc(NpcClickEvent event) {
        Player player = event.getPlayer();
        if (running(player) == null) {
            return;
        }
        var roles = module.config().helpRoles;
        if (roles.isEmpty() || roles.stream().anyMatch(r -> r.strip().equalsIgnoreCase(event.role()))) {
            engine().signal(player, Steps.HELP);
        }
    }

    // ------------------------------------------------------------------ 11 bank

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeposit(BankDepositEvent event) {
        Player player = event.getPlayer();
        if (running(player) == null || (event.island() && !module.config().bankCountsIsland)) {
            return;
        }
        engine().signal(player, Steps.BANK);
    }
}
