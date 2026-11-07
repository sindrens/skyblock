package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.economy.BankDepositEvent;
import net.skybornmc.skyblock.common.grind.GrindBreakEvent;
import net.skybornmc.skyblock.common.menu.MenuOpenedEvent;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import net.skybornmc.skyblock.common.npc.NpcDirectory;
import net.skybornmc.skyblock.common.sell.ItemsSoldEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns real events into tutorial {@link Signal}s (owner: steps finish from what players do, never from typed commands)
 * and runs the NPC dialogs. Audit-row steps (shop buy, upgrades, invites, daily) come from {@link AuditSignals}.
 */
final class TutorialListener implements Listener {

    private final TutorialModule m;
    /** player|role → when that NPC last said an idle line to the player. */
    private final Map<String, Long> idleAt = new HashMap<>();

    TutorialListener(TutorialModule m) {
        this.m = m;
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        m.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        idleAt.keySet().removeIf(k -> k.startsWith(id + "|"));
        m.unload(event.getPlayer());
    }

    // ------------------------------------------------------------------ talking NPCs

    /**
     * Runs before the NPC's own action: a click on the NPC of an open dialog steers it (finish line / skip); a click on
     * the NPC of an open step plays the step's dialog instead of the action, and the step's command runs when it ends.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onNpc(NpcClickEvent event) {
        Player p = event.getPlayer();
        if (m.dialogs().steers(p, event.npcId())) {
            event.setCancelled(true);
            m.dialogs().click(p);
            return;
        }
        if (event.help() || event.role() == null) {
            return;
        }
        Signal signal = Signal.of(StepType.NPC, event.role().toLowerCase(Locale.ROOT), 1);
        List<Catalog.Step> steps = m.listening(p, signal);
        if (steps.isEmpty()) {
            return;
        }
        Catalog.Step step = steps.getFirst();
        if (step.dialog().isEmpty()) {
            m.signal(p, signal); // nothing to say: the NPC's normal action runs as well
            return;
        }
        event.setCancelled(true);
        m.dialogs().start(p, event.npcId(), where(event.npcId(), p), npcName(event.role()), step.dialog(), false, true, completed -> {
            if (!completed || !p.isOnline()) {
                return; // walked away: the step stays open, the next click starts again
            }
            m.signal(p, signal);
            if (!step.command().isEmpty() && m.commandExists(step.command())) {
                p.performCommand(step.command());
            }
        });
    }

    /** Outside a step: one idle line (once a minute per NPC), while the NPC's normal action opens as usual. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcIdle(NpcClickEvent event) {
        Player p = event.getPlayer();
        if (!m.config().idleLines || event.help() || event.role() == null || m.dialogs().active(p)) {
            return;
        }
        List<String> lines = m.catalog().idle(event.role());
        if (lines.isEmpty()) {
            return;
        }
        String key = p.getUniqueId() + "|" + event.role().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long last = idleAt.get(key);
        if (last != null && now - last < m.config().idleCooldownSeconds * 1000L) {
            return;
        }
        idleAt.put(key, now);
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        m.dialogs().start(p, event.npcId(), where(event.npcId(), p), npcName(event.role()), List.of(line), true, true, null);
    }

    private Location where(String npcId, Player p) {
        return m.core().services().optional(NpcDirectory.class).flatMap(d -> d.npcLocation(npcId)).orElse(p.getLocation());
    }

    /** The NPC's name (MiniMessage) from messages/npcs.yml roles.<role>.name; the role in title case otherwise. */
    String npcName(String role) {
        String raw = m.core().messages().raw("npcs", "roles." + role.toLowerCase(Locale.ROOT) + ".name");
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        String r = role.replace('_', ' ');
        return r.isEmpty() ? "?" : Character.toUpperCase(r.charAt(0)) + r.substring(1);
    }

    // ------------------------------------------------------------------ signals

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenu(MenuOpenedEvent event) {
        m.signal(event.getPlayer(), Signal.of(StepType.MENU, event.menuId(), 1));
    }

    /** Quest line v2: the island hoe or pickaxe reached a level. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onToolLevel(net.skybornmc.skyblock.common.tools.ToolLevelUpEvent event) {
        m.signal(event.getPlayer(), Signal.of(StepType.TOOL_LEVEL, event.getTool().kind().name().toLowerCase(java.util.Locale.ROOT),
                event.getTool().level()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrind(GrindBreakEvent event) {
        m.signal(event.getPlayer(), new Signal(StepType.GRIND, event.area(), 1,
                Map.of("block", event.block().name(), "drop", event.drop().name())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSold(ItemsSoldEvent event) {
        if (event.items() > 0) {
            m.signal(event.getPlayer(), Signal.of(StepType.SELL, event.source(), 1));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeposit(BankDepositEvent event) {
        m.signal(event.getPlayer(), Signal.of(StepType.BANK, event.island() ? "island" : "personal", event.amount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (m.onOwnIsland(p)) {
            m.signal(p, Signal.of(StepType.PLANT, event.getBlockPlaced().getType().name(), 1));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder(false);
        if ((holder instanceof Chest || holder instanceof DoubleChest) && m.onOwnIsland(p)) {
            m.signal(p, Signal.of(StepType.CHEST));
        }
    }

    /** Kindle (7 Nov 2026): the island level went up; every online member of that island hears it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandLevel(net.skybornmc.skyblock.common.progression.IslandLevelUpEvent event) {
        if (!event.rose()) {
            return;
        }
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (event.islandId().equals(m.islandOf(p))) {
                m.signal(p, Signal.of(StepType.ISLAND_LEVEL, "island", event.newLevel()));
            }
        }
    }

    /** Kindle: ripe crops and generator blocks broken on your own island (cheap checks first: most breaks need nothing). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandBreak(org.bukkit.event.block.BlockBreakEvent event) {
        org.bukkit.block.Block b = event.getBlock();
        String what = islandBreak(b);
        if (what == null) {
            return;
        }
        Player p = event.getPlayer();
        Signal signal = Signal.of(StepType.ISLAND_BREAK, what, 1);
        if (m.listening(p, signal).isEmpty() || !m.onOwnIsland(p)
                || ("ore".equals(what) && net.skybornmc.skyblock.common.world.ChunkMarks.marked(b))) { // a placed block does not count
            return;
        }
        m.signal(p, signal);
    }

    /** {@code crop} for a ripe crop, {@code ore} for a generator block (cobblestone, stone, ores), else null. */
    static String islandBreak(org.bukkit.block.Block b) {
        Material type = b.getType();
        if (b.getBlockData() instanceof org.bukkit.block.data.Ageable age && type != Material.SUGAR_CANE && type != Material.CACTUS
                && type != Material.BAMBOO && type != Material.KELP) {
            return age.getAge() >= age.getMaximumAge() ? "crop" : null;
        }
        if (type == Material.MELON || type == Material.PUMPKIN) {
            return "crop";
        }
        String id = type.name();
        boolean ore = type == Material.COBBLESTONE || type == Material.STONE || id.endsWith("_ORE") || type == Material.GILDED_BLACKSTONE;
        return ore ? "ore" : null;
    }

    /** Cobblestone or stone forming near a player standing on their own island: the generator works. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Material type = event.getNewState().getType();
        if (type != Material.COBBLESTONE && type != Material.STONE && type != Material.BASALT) {
            return;
        }
        for (Player p : event.getBlock().getLocation().getNearbyPlayers(10)) {
            if (m.onOwnIsland(p)) {
                m.signal(p, Signal.of(StepType.GENERATOR));
            }
        }
    }
}
