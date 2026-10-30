package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The essentials toggles in {@code /settings} (F1): {@code join-messages} (see other players' join/leave lines) and
 * {@code tpa-requests} (receive {@code /tpa} and {@code /tpahere}); QUICK1: {@code drop-guard} ({@link DropGuard}). Texts are the shared ones in
 * {@code messages/navigation.yml → settings.toggles.<id>}. Values are read on every use, so a change applies at once.
 */
final class EssentialsSettings {

    static final String JOIN_MESSAGES = "join-messages";
    static final String TPA_REQUESTS = "tpa-requests";
    /** QUICK1: ask again before dropping a custom/unique item ({@link DropGuard}). */
    static final String DROP_GUARD = "drop-guard";

    private final Core core;
    private volatile PlayerSettings service;

    EssentialsSettings(Core core) {
        this.core = core;
    }

    /** Called one tick after enable: the menus module (which publishes PlayerSettings) enables after essentials. */
    void register() {
        PlayerSettings s = core.services().optional(PlayerSettings.class).orElse(null);
        if (s == null) {
            return;
        }
        try {
            s.register(new SettingToggle(EssentialsModule.ID, JOIN_MESSAGES, SettingCategory.CHAT, Material.OAK_DOOR, true, null,
                    "navigation", "settings.toggles." + JOIN_MESSAGES, false));
            s.register(new SettingToggle(EssentialsModule.ID, TPA_REQUESTS, SettingCategory.SOCIAL, Material.ENDER_PEARL, true, null,
                    "navigation", "settings.toggles." + TPA_REQUESTS, false));
            s.register(new SettingToggle(EssentialsModule.ID, DROP_GUARD, SettingCategory.GAMEPLAY, Material.SHIELD, true, null,
                    "navigation", "settings.toggles." + DROP_GUARD, false));
            service = s;
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("Essentials: could not register the /settings toggles: " + e.getMessage());
        }
    }

    void unregister() {
        PlayerSettings s = service;
        service = null;
        if (s != null) {
            s.unregisterAll(EssentialsModule.ID);
        }
    }

    /** The player's value, or the default (on) while the settings service is not available. */
    boolean get(UUID player, String id) {
        PlayerSettings s = service;
        return s == null || s.get(player, id);
    }

    boolean wantsJoinMessages(UUID viewer) {
        return get(viewer, JOIN_MESSAGES);
    }

    boolean acceptsTpa(UUID target) {
        return get(target, TPA_REQUESTS);
    }

    /** The viewers that see a join/leave line (pure, for tests): everyone who did not turn join messages off. */
    static <T> List<T> recipients(Collection<? extends T> online, Predicate<? super T> wantsJoinMessages) {
        List<T> out = new ArrayList<>(online.size());
        for (T viewer : online) {
            if (wantsJoinMessages.test(viewer)) {
                out.add(viewer);
            }
        }
        return out;
    }
}
