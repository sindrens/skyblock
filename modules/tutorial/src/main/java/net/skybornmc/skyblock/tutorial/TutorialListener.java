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
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
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

    /** A camera that never ended (crash, restart) gives game mode, position and flight back before anything else. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinRestore(PlayerJoinEvent event) {
        module.cameras().restoreOnJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        engine().load(event.getPlayer());
    }

    /** Quit or kick during a camera: everything is given back before the player data is saved. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitRestore(PlayerQuitEvent event) {
        module.cameras().stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        engine().quit(event.getPlayer());
    }

    // ------------------------------------------------------------------ cameras (TU3)

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        module.cameras().died(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("tutorial-camera-respawn", 1, () -> {
            if (player.isOnline()) {
                module.cameras().respawned(player);
            }
        });
    }

    /** Sneak = skip (Bedrock cut-and-fade; Java spectators also send it). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && module.cameras().running(event.getPlayer().getUniqueId())) {
            module.cameras().sneak(event.getPlayer());
        }
    }

    /** Java: sneaking leaves the camera entity = skip. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        module.cameras().stoppedSpectating(event.getPlayer(), event.getSpectatorTarget());
    }

    /** A command during a camera ends it first (the player is put back, then the command runs normally). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (module.cameras().running(event.getPlayer().getUniqueId())) {
            module.cameras().skip(event.getPlayer());
        }
    }

    /** Cut-and-fade: walking or flying away from the shot skips the camera (TUT-FIX: never a frozen player). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() && module.cameras().running(event.getPlayer().getUniqueId())) {
            module.cameras().moved(event.getPlayer(), event.getTo());
        }
    }

    /** TUT-FIX: any move key, jump or sneak during a camera skips it (also while spectating, where moves send nothing). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInput(PlayerInputEvent event) {
        if (module.cameras().running(event.getPlayer().getUniqueId())) {
            module.cameras().input(event.getPlayer(), event.getInput());
        }
    }

    /** TUT-FIX: a teleport by anything else during a camera ends it at that destination. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (module.cameras().running(event.getPlayer().getUniqueId())) {
            module.cameras().teleported(event.getPlayer(), event);
        }
    }

    /** Pip and the camera entity can't be clicked, fed, leashed or hurt. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (ours(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEntity(EntityDamageEvent event) {
        if (ours(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean ours(Entity e) {
        return module.pip().isPip(e) || module.cameras().isCameraEntity(e);
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
        if (p.state.complete()) {
            // TQ31: a new island on a 2nd or 3rd profile gets the island camera, but no tutorial.
            if (event.firstTime() && module.config().cameraWelcomeNewProfile) {
                core.scheduler().later("tutorial-welcome-camera", 40, () -> {
                    if (player.isOnline() && module.targets().onOwnIsland(player, false)) {
                        module.cameras().play(player, Cameras.ISLAND, null);
                    }
                });
            }
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

    /**
     * TUT-FIX: the remembered starter chest of the player's current island counts; without one (the old point belonged
     * to an older island, a barrel, no chest found at paste) any chest, barrel or shulker box on the own island counts.
     * Only while the chest step is not done yet (an early open counts too, TQ21).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Progress p = running(player);
        if (p == null || p.isDone(Steps.CHEST)) {
            return;
        }
        List<ChestRules.Spot> blocks = blocks(event.getInventory().getHolder(false));
        if (blocks.isEmpty() || !ChestRules.counts(blocks, known(player, p), ownIsland(player, blocks.getFirst()))) {
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
        if (p == null || !p.chestOpened || p.isDone(Steps.CHEST)) {
            return;
        }
        boolean container = !blocks(event.getInventory().getHolder(false)).isEmpty();
        boolean left = !container || containsTool(event.getInventory().getContents());
        core.scheduler().later("tutorial-chest-check", 1, () -> {
            checkChest(player, left);
            Progress now = running(player);
            if (now != null && !now.isDone(Steps.CHEST) && Steps.CHEST.equals(now.step) && player.isOnline()) {
                module.msg().actionBar(player, "steps.chest.take-tool");
            }
        });
    }

    /** The chest re-check after the starter tools arrived (the container may still hold them). */
    void checkChest(Player player) {
        checkChest(player, true);
    }

    private void checkChest(Player player, boolean containerHasTool) {
        Progress p = running(player);
        if (p == null || p.isDone(Steps.CHEST)) {
            return;
        }
        if (ChestRules.done(p.chestOpened, needTool() != null, hasTool(player), containerHasTool)) {
            engine().signal(player, Steps.CHEST);
        }
    }

    /** True when the remembered starter chest of this island is loaded and no longer a storage block. */
    private boolean chestGone(Player player, Progress p) {
        Optional<Location> at = module.targets().chest(player, p);
        if (at.isEmpty() || !at.get().isChunkLoaded()) {
            return false;
        }
        BlockState state = at.get().getBlock().getState(false);
        return !(state instanceof Chest || state instanceof Barrel || state instanceof ShulkerBox);
    }

    /** The blocks of a container holder (both halves of a double chest); empty for menus and entity inventories. */
    private static List<ChestRules.Spot> blocks(InventoryHolder holder) {
        return switch (holder) {
            case DoubleChest dc -> {
                List<ChestRules.Spot> out = new ArrayList<>(2);
                for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(false), dc.getRightSide(false)}) {
                    if (side instanceof BlockState state) {
                        out.add(spot(state.getLocation()));
                    }
                }
                yield out;
            }
            case Chest box -> List.of(spot(box.getLocation()));
            case Barrel box -> List.of(spot(box.getLocation()));
            case ShulkerBox box -> List.of(spot(box.getLocation()));
            case null, default -> List.of();
        };
    }

    private static ChestRules.Spot spot(Location l) {
        return new ChestRules.Spot(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    private ChestRules.Spot known(Player player, Progress p) {
        return module.targets().chest(player, p).map(TutorialListener::spot).orElse(null);
    }

    /** True/false on an island the player is (not) a member of; {@code null} without the islands module. */
    private Boolean ownIsland(Player player, ChestRules.Spot spot) {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(spot.world());
        if (islands.isEmpty() || w == null) {
            return islands.isEmpty() ? null : false;
        }
        UUID key = ProfileService.of(core.services()).profileKey(player);
        Location loc = new Location(w, spot.x(), spot.y(), spot.z());
        return islands.get().islandAt(loc).map(info -> islands.get().isMember(info.id(), key)).orElse(false);
    }

    /** The tool kind the chest step needs, or {@code null} when opening is enough. */
    private String needTool() {
        String need = module.config().chestTool == null ? "" : module.config().chestTool.strip().toLowerCase(Locale.ROOT);
        return need.isEmpty() ? null : need;
    }

    /** True if these items hold the needed tool kind. */
    boolean containsTool(ItemStack[] items) {
        String need = needTool();
        if (need == null) {
            return false;
        }
        Optional<ToolService> tools = core.services().optional(ToolService.class);
        ToolKind kind = ToolKind.byId(need).orElse(null);
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (tools.isPresent()) {
                if (kind != null && tools.get().read(stack).map(t -> t.kind() == kind).orElse(false)) {
                    return true;
                }
            } else if (stack.getType().getKey().getKey().endsWith("_" + need) || stack.getType().getKey().getKey().equals(need)) {
                return true;
            }
        }
        return false;
    }

    /** {@code steps.chest.need-tool} in the inventory (a starter tool of that kind; any tool of it without the tools module). */
    boolean hasTool(Player player) {
        return needTool() == null || containsTool(player.getInventory().getContents());
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
        module.optional().menuOpened(player, event.menuId());
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
        } else if (Steps.CHEST.equals(p.step)) {
            // TUT-FIX, once a second: the tool taken some other way (shift-click, a hopper) still finishes the step, and a
            // starter chest that is gone (broken, its items picked up) no longer blocks it.
            long now = System.currentTimeMillis();
            if (now - p.lastChestCheck < 1000) {
                return;
            }
            p.lastChestCheck = now;
            if (!p.chestOpened && chestGone(player, p)) {
                p.chestOpened = true;
                checkChest(player, false);
            } else if (p.chestOpened) {
                checkChest(player, true);
            }
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

    /** TQ27: the spawn Guide NPC opens {@code /tutorial} (sneak-click keeps its help text). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuideNpc(NpcClickEvent event) {
        if (!module.config().enabled || !module.config().guideNpc || event.help() || event.role() == null) {
            return;
        }
        if (module.config().guideRoles.stream().anyMatch(r -> r.strip().equalsIgnoreCase(event.role()))) {
            event.setCancelled(true);
            core.sounds().play(event.getPlayer(), "click");
            engine().openMenu(event.getPlayer());
        }
    }

    /** Help step, NPC lines for the current step, optional steps (also counts the Guide click, which is cancelled). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpc(NpcClickEvent event) {
        Player player = event.getPlayer();
        module.optional().npcClicked(player, event.npcId(), event.role());
        Progress p = running(player);
        if (p == null) {
            return;
        }
        String step = p.step;
        String line = "npc-lines." + step + "." + event.role();
        if (module.config().npcLines && step != null && event.role() != null && module.msg().has(line)) {
            module.msg().send(player, "npc-lines.format", net.skybornmc.skyblock.common.text.Msg.c("line", module.msg().component(player, line)));
        }
        var roles = module.config().helpRoles;
        if (roles.isEmpty() || roles.stream().anyMatch(r -> r.strip().equalsIgnoreCase(event.role()))) {
            engine().signal(player, Steps.HELP);
        }
    }

    // ------------------------------------------------------------------ optional: break (O1 generator)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        module.optional().blockBroken(event.getPlayer(), event.getBlock());
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
