package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Who gets which staff alert (spec 14.9): the live side of {@link NotifyRules}. The switches of every staff member are
 * cached in memory (loaded at start, a staff member's own row again on join), so routing an alert never touches the
 * database. Changes are written async, audited, and take effect at once.
 */
final class StaffNotify {

    private final Core core;
    private final NotifyStore store;
    private final Supplier<NotifyRules.Settings> settings;
    private final Map<UUID, Map<NotifyRules.Type, Boolean>> cache = new ConcurrentHashMap<>();

    StaffNotify(Core core, NotifyStore store, Supplier<NotifyRules.Settings> settings) {
        this.core = core;
        this.store = store;
        this.settings = settings;
    }

    NotifyStore store() {
        return store;
    }

    NotifyRules.Settings settings() {
        NotifyRules.Settings s = settings.get();
        return s == null ? NotifyRules.Settings.DEFAULT : s;
    }

    /** Loads every stored switch (tiny table). Until it is loaded everything counts as on. */
    void start() {
        store.loadAll().thenAccept(all -> all.forEach((id, map) -> cache.put(id, new ConcurrentHashMap<>(map))))
                .exceptionally(e -> {
                    core.plugin().getLogger().warning("Could not load staff notification settings: " + e.getMessage());
                    return null;
                });
    }

    /** Refreshes one staff member's switches (on join: another server or a database edit may have changed them). */
    void joined(Player player) {
        if (!player.hasPermission(ModerationModule.PERM_NOTIFY)) {
            return;
        }
        UUID id = player.getUniqueId();
        store.load(id).thenAccept(map -> cache.put(id, new ConcurrentHashMap<>(map))).exceptionally(e -> null);
    }

    /** The stored switch, or {@code null} (nothing stored = on). */
    Boolean stored(UUID staff, NotifyRules.Type type) {
        Map<NotifyRules.Type, Boolean> map = cache.get(staff);
        return map == null ? null : map.get(type);
    }

    /** Whether {@code player} gets alerts of {@code type} right now (permission + their switch + always-on types). */
    boolean wants(Player player, NotifyRules.Type type) {
        return NotifyRules.receives(type, player.hasPermission(type.permission()), stored(player.getUniqueId(), type), settings());
    }

    /** Online staff who get alerts of {@code type}. */
    List<Player> recipients(NotifyRules.Type type) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (wants(p, type)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Everything a staff member could switch (types they have the permission for), with the current value. */
    Map<NotifyRules.Type, Boolean> view(Player player) {
        Map<NotifyRules.Type, Boolean> out = new EnumMap<>(NotifyRules.Type.class);
        for (NotifyRules.Type t : NotifyRules.Type.values()) {
            if (player.hasPermission(t.permission())) {
                out.put(t, wants(player, t));
            }
        }
        return out;
    }

    /**
     * Switches {@code type} for {@code player} (main thread in; the future completes on the database thread). Refused
     * without the type's permission or when turning off an always-on type. Audited ({@code notify_on} / {@code notify_off}).
     */
    CompletableFuture<NotifyRules.Change> set(Player player, NotifyRules.Type type, boolean on) {
        NotifyRules.Change check = NotifyRules.canChange(type, player.hasPermission(type.permission()), on, settings());
        if (check != NotifyRules.Change.OK) {
            return CompletableFuture.completedFuture(check);
        }
        UUID id = player.getUniqueId();
        cache.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(type, on);
        return store.set(id, type, on, System.currentTimeMillis()).thenApply(before -> {
            core.audit().log(AuditCategory.STAFF, on ? "notify_on" : "notify_off", Actor.of(player),
                    Map.of("type", type.id(), "before", before == null ? "default" : String.valueOf(before)));
            return NotifyRules.Change.OK;
        });
    }
}
