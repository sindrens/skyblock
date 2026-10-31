package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.production.IslandGatherEvent;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Mining-pool books from island generator ores (ENCHANTS §3.1, PHASE-7 M7.2): the production module's
 * {@link IslandGatherEvent} counts as a Depths ore action of the ore's tier at {@code drops.island-generator-percent} (25%)
 * of one action, so chance, pity and first finds move at a quarter of the Depths pace. Only blocks the generator really
 * made, only with a custom pickaxe. No gathering or area effects here: area enchants never break real island blocks (PD9).
 */
final class IslandBooks implements Listener {

    private final EnchantsModule module;
    private final BookDrops books;

    IslandBooks(EnchantsModule module, BookDrops books) {
        this.module = module;
        this.books = books;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGather(IslandGatherEvent event) {
        double share = module.config().dropIslandGeneratorPercent / 100.0;
        if (!event.generatorMade() || event.tier() <= 0 || !(share > 0)) {
            return;
        }
        ToolInfo tool = module.tool(event.getPlayer().getInventory().getItemInMainHand());
        if (tool == null || tool.kind() != ToolKind.PICKAXE) {
            return; // books need the wing's custom tool, like in The Depths
        }
        books.action(event.getPlayer(), Pool.MINING, event.tier(), share, event.block().getLocation());
    }
}
