package net.skybornmc.skyblock.hud;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import org.bukkit.event.HandlerList;

/**
 * HUD: the sidebar scoreboard (M1.4). Settings in {@code hud.yml}, lines/layouts in {@code messages/hud.yml}.
 * Tab list and nametags (M1.5) build on the same per-player scoreboard via {@link HudService#scoreboard}.
 */
public final class HudModule implements SkyblockModule {

    private final Core core;
    private final HudConfig config = new HudConfig();
    private HudService hud;

    public HudModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return HudService.MODULE;
    }

    @Override
    public void enable() {
        core.configs().load(config);
        core.messages(id());
        hud = new HudService(core, config);
        core.services().register(id(), HudService.class, hud);
        core.plugin().getServer().getPluginManager().registerEvents(hud, core.plugin());
        hud.start();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(id());
        core.plugin().getServer().getOnlinePlayers().forEach(hud::refresh);
    }

    @Override
    public void disable() {
        if (hud != null) {
            HandlerList.unregisterAll(hud);
            hud.stop();
        }
    }
}
