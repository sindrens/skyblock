package net.skybornmc.skyblock.hud;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;

/**
 * HUD: the sidebar scoreboard (M1.4). Settings in {@code hud.yml}, lines/layouts in {@code messages/hud.yml}.
 * Tab list and nametags (M1.5) build on the same per-player scoreboard via {@link HudService#scoreboard}.
 */
public final class HudModule implements SkyblockModule {

    private final Core core;
    private final HudConfig config = new HudConfig();
    private HudService hud;
    /** The settings change listener can't be removed, so it is added once and always talks to the current service. */
    private boolean listening;

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
        // F1: the menus module (which publishes PlayerSettings) enables after us; hook the toggle one tick later.
        core.scheduler().later("hud:settings", 1L, this::registerSetting);
    }

    private void registerSetting() {
        PlayerSettings settings = core.services().optional(PlayerSettings.class).orElse(null);
        HudService current = hud;
        if (settings == null || current == null) {
            return;
        }
        try {
            settings.register(new SettingToggle(id(), HudService.SETTING, SettingCategory.DISPLAY, Material.ITEM_FRAME, true, null,
                    "navigation", "settings.toggles." + HudService.SETTING, false));
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("HUD: could not register the scoreboard setting: " + e.getMessage());
            return;
        }
        if (!listening) {
            listening = true;
            settings.onChange(HudService.SETTING, (player, value) -> {
                HudService live = hud;
                if (live != null) {
                    live.refresh(player); // shows/hides the sidebar right away
                }
            });
        }
        current.useSettings(settings);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(id());
        hud.reloaded();
        core.plugin().getServer().getOnlinePlayers().forEach(hud::refresh);
    }

    @Override
    public void disable() {
        if (hud != null) {
            HandlerList.unregisterAll(hud);
            hud.stop();
            hud = null;
        }
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(id()));
    }
}
