package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * {@code /dungeon} (M4.6): opens {@link DungeonMenu} on the wing the player stands in (Mine otherwise) and teleports
 * to unlocked tiers through {@link DungeonTeleport}.
 */
public final class Dungeon {

    static final String MODULE = "depths";

    private final Core core;
    private final DepthsConfig config;
    private final ZoneService zones;
    private final TierGates gates;
    private final ModuleMessages msg;
    private final DungeonTeleport teleport;
    /** Satchel button (M4.4): the service for the fill shown on the button and the menu to open, or null. */
    private net.skybornmc.skyblock.common.satchel.SatchelService satchel;
    private java.util.function.Supplier<net.skybornmc.skyblock.common.menu.Menu> satchelMenu;

    public Dungeon(Core core, DepthsConfig config, ZoneService zones, TierGates gates, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.zones = zones;
        this.gates = gates;
        this.msg = msg;
        this.teleport = new DungeonTeleport(core, config, zones, gates, msg);
    }

    public void start() {
        core.plugin().getServer().getPluginManager().registerEvents(teleport, core.plugin());
    }

    public void stop() {
        HandlerList.unregisterAll(teleport);
        teleport.stop();
    }

    /** Opens the menu on the player's current wing (Mine when they are not in a zone). */
    public void open(Player player) {
        String place = gates.place(player.getUniqueId());
        Aspect tab = place == null ? Aspect.MINE : zones.zone(place).map(Zone::aspect).orElse(Aspect.MINE);
        core.menus().open(player, new DungeonMenu(this, tab));
    }

    /** Adds the Satchel button to the menu. */
    public void satchel(net.skybornmc.skyblock.common.satchel.SatchelService service,
                        java.util.function.Supplier<net.skybornmc.skyblock.common.menu.Menu> menu) {
        this.satchel = service;
        this.satchelMenu = menu;
    }

    net.skybornmc.skyblock.common.satchel.SatchelService satchelService() {
        return satchel;
    }

    net.skybornmc.skyblock.common.menu.Menu satchelMenu() {
        return satchelMenu == null ? null : satchelMenu.get();
    }

    void teleport(Player player, Zone zone) {
        teleport.teleport(player, zone);
    }

    DepthsConfig config() {
        return config;
    }

    ZoneService zones() {
        return zones;
    }

    TierGates gates() {
        return gates;
    }

    ModuleMessages messages() {
        return msg;
    }
}
