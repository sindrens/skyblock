package net.skybornmc.skyblock.perks;

import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Owner 7 Nov 2026 (join when full): players with {@link #PERM} (Firewing and up) still join a full server, up to
 * {@code join-full.extra-percent} over the limit; everyone else gets a kick message that names the store. LuckPerms loaded
 * the joining player at pre-login, so their permission is known here.
 */
final class JoinFull implements Listener {

    static final String PERM = "skyblock.perks.joinfull";

    private final Core core;
    private final PerksConfig config;
    private final ModuleMessages msg;

    JoinFull(Core core, PerksConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    /** Pure: the most players when ranks fill the extra room (at least one more than the limit). */
    static int limit(int max, int extraPercent) {
        return max + Math.max(1, (int) Math.ceil(max * Math.max(0, extraPercent) / 100.0));
    }

    @EventHandler
    public void onFull(PlayerServerFullCheckEvent event) {
        if (!config.joinFullEnabled || event.isAllowed()) {
            return;
        }
        UUID id = event.getPlayerProfile().getId();
        if (id != null && core.ranks().hasPermission(id, PERM)
                && Bukkit.getOnlinePlayers().size() < limit(Bukkit.getMaxPlayers(), config.joinFullExtraPercent)) {
            event.allow(true);
            return;
        }
        event.deny(msg.component(Bukkit.getConsoleSender(), "join-full.kick"));
    }
}
