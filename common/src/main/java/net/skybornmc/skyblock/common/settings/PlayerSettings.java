package net.skybornmc.skyblock.common.settings;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Per-player on/off settings ({@code /settings}). Published as a service by the menus module:
 * {@code core.services().optional(PlayerSettings.class)}.
 *
 * <p>Modules register their own toggles (and read them) instead of inventing their own storage. Values live in the
 * player's profile ({@code players.settings}, key {@code setting.<id>}); only values that differ from the default are
 * stored. Reads never touch the database.</p>
 *
 * <pre>{@code
 * settings.register(new SettingToggle("social", "private-messages", SettingCategory.CHAT, Material.WRITABLE_BOOK,
 *         true, null, "social", "settings.private-messages", false));
 * if (!settings.get(target, "private-messages")) { ...refuse the DM... }
 * }</pre>
 */
public interface PlayerSettings {

    /**
     * Adds a toggle. Registering an id that exists as a "coming soon" placeholder replaces it (the implementing module
     * takes it over); registering over an active toggle throws.
     */
    void register(SettingToggle toggle);

    /** Removes every toggle of {@code owner} (called when that module disables). */
    void unregisterAll(String owner);

    /** All toggles in menu order (category, then registration order). */
    List<SettingToggle> toggles();

    Optional<SettingToggle> toggle(String id);

    /** The player's value, or the toggle's default (also for offline players and unknown ids: false). */
    boolean get(UUID player, String id);

    default boolean get(Player player, String id) {
        return get(player.getUniqueId(), id);
    }

    /** Changes a value (online players only), fires listeners and writes the audit log. Returns false if not possible. */
    boolean set(Player player, String id, boolean value);

    /** Called on the main thread after a player's value changed: {@code (player, newValue)}. */
    void onChange(String id, BiConsumer<Player, Boolean> listener);
}
