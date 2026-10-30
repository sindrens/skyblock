package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUICK1 drop guard (QoL #10a, owner principle "nothing is lost by accident"): custom and unique items (tools, enchant
 * books, notes, keys, tokens, spawners: anything carrying our item data) are not thrown away by one stray key press or a
 * click outside a menu. The first drop is refused with a short hint; dropping the same item again within
 * {@code drop-guard.confirm-seconds}, or sneak + drop, throws it. Switch in {@code /settings} ({@code drop-guard}).
 * Plain blocks and vanilla items are never touched; creative mode is never touched.
 */
final class DropGuard implements Listener {

    private final Core core;
    private final EssentialsConfig config;
    private final ModuleMessages msg;
    private final EssentialsSettings settings;
    /** The last refused drop per player: what it was and until when dropping it again counts as "yes". */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    record Pending(String fingerprint, long until) {
    }

    DropGuard(Core core, EssentialsConfig config, ModuleMessages msg, EssentialsSettings settings) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.settings = settings;
    }

    /** HIGH and ignoreCancelled: menus, staff mode and frozen players cancel their own drops earlier. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        if (!config.dropGuardEnabled || player.getGameMode() == GameMode.CREATIVE || !valuable(item)
                || !settings.get(player.getUniqueId(), EssentialsSettings.DROP_GUARD)) {
            return;
        }
        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(item);
        Pending last = pending.get(player.getUniqueId());
        if (allowed(player.isSneaking(), last, fingerprint, now)) {
            pending.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        pending.put(player.getUniqueId(), new Pending(fingerprint, now + config.dropGuardConfirmSeconds * 1000L));
        msg.send(player, "drop-guard.ask", Msg.c("item", item.effectiveName()),
                Msg.number("seconds", config.dropGuardConfirmSeconds));
        core.sounds().play(player, "error");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ pure rules (unit-tested)

    /** True when this drop goes through: sneaking, or the same item was refused a moment ago (second press = yes). */
    static boolean allowed(boolean sneaking, Pending last, String fingerprint, long now) {
        if (sneaking) {
            return true;
        }
        return last != null && last.fingerprint().equals(fingerprint) && now <= last.until();
    }

    /** Custom, unique or spawner items: anything with our item data (any key in our namespace) or a spawner block. */
    static boolean valuable(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (item.getType() == Material.SPAWNER || ItemRegistry.isCustom(item)) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            if (ItemKeys.NAMESPACE.equals(key.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    /** Identifies "the same item" for the second press: unique id, else custom id, else the material. */
    static String fingerprint(ItemStack item) {
        Optional<UUID> unique = ItemRegistry.uuidOf(item);
        if (unique.isPresent()) {
            return "u:" + unique.get();
        }
        return ItemRegistry.idOf(item).map(id -> "c:" + id).orElse("m:" + item.getType().getKey().getKey());
    }
}
