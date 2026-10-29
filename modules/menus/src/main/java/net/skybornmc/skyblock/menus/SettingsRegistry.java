package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/** {@link PlayerSettings} backed by the player profile's settings map. */
public final class SettingsRegistry implements PlayerSettings {

    private final Core core;
    private final Map<String, SettingToggle> toggles = new LinkedHashMap<>();
    private final Map<String, List<BiConsumer<Player, Boolean>>> listeners = new LinkedHashMap<>();

    public SettingsRegistry(Core core) {
        this.core = core;
    }

    @Override
    public synchronized void register(SettingToggle toggle) {
        SettingToggle existing = toggles.get(toggle.id());
        if (existing != null && !existing.comingSoon() && !existing.owner().equals(toggle.owner())) {
            throw new IllegalStateException("Setting '" + toggle.id() + "' is already registered by '" + existing.owner() + "'");
        }
        toggles.put(toggle.id(), toggle);
    }

    @Override
    public synchronized void unregisterAll(String owner) {
        toggles.values().removeIf(t -> t.owner().equals(owner));
    }

    @Override
    public synchronized List<SettingToggle> toggles() {
        List<SettingToggle> list = new ArrayList<>(toggles.values());
        list.sort(Comparator.comparing(SettingToggle::category)); // stable: keeps registration order inside a category
        return List.copyOf(list);
    }

    /** Toggles of one category the player may see. */
    public List<SettingToggle> visible(Player player, SettingCategory category) {
        return toggles().stream()
                .filter(t -> t.category() == category)
                .filter(t -> t.permission() == null || player.hasPermission(t.permission()))
                .toList();
    }

    @Override
    public synchronized Optional<SettingToggle> toggle(String id) {
        return Optional.ofNullable(toggles.get(id));
    }

    @Override
    public boolean get(UUID player, String id) {
        Optional<SettingToggle> toggle = toggle(id);
        if (toggle.isEmpty()) {
            return false;
        }
        boolean fallback = toggle.get().defaultValue();
        return core.players().get(player)
                .map(data -> parse(data.setting(toggle.get().storageKey(), null), fallback))
                .orElse(fallback);
    }

    @Override
    public boolean set(Player player, String id, boolean value) {
        Optional<SettingToggle> toggle = toggle(id);
        Optional<PlayerData> data = core.players().get(player.getUniqueId());
        if (toggle.isEmpty() || toggle.get().comingSoon() || data.isEmpty()
                || (toggle.get().permission() != null && !player.hasPermission(toggle.get().permission()))) {
            return false;
        }
        boolean old = get(player.getUniqueId(), id);
        if (old == value) {
            return true;
        }
        // Only differences from the default are stored, so changing a default later reaches everyone who never touched it.
        data.get().setSetting(toggle.get().storageKey(), value == toggle.get().defaultValue() ? null : Boolean.toString(value));
        core.audit().logPlayer(AuditCategory.COMMAND, "setting_change", Actor.player(player), player.getUniqueId(),
                player.getName(), null, null, Map.of("setting", id, "value", value));
        List<BiConsumer<Player, Boolean>> list;
        synchronized (this) {
            list = List.copyOf(listeners.getOrDefault(id, List.of()));
        }
        for (BiConsumer<Player, Boolean> listener : list) {
            try {
                listener.accept(player, value);
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Setting listener for '" + id + "' failed", e);
            }
        }
        return true;
    }

    @Override
    public synchronized void onChange(String id, BiConsumer<Player, Boolean> listener) {
        listeners.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    static boolean parse(String stored, boolean fallback) {
        if (stored == null) {
            return fallback;
        }
        return switch (stored) {
            case "true" -> true;
            case "false" -> false;
            default -> fallback;
        };
    }
}
