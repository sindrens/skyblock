package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /vanish}: hidden from everyone without {@code skyblock.staff.vanish.see}, silent join/quit, no item pickup,
 * mobs ignore you. Stays on across rejoins (player setting {@code vanished}). Placeholder {@code <ph:vanished>}.
 * Sets the common {@code vanished} metadata so other plugins (and our tab/scoreboard later) can respect it.
 */
final class VanishService extends CommandGroup implements Listener {

    static final String SETTING = "vanished";
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    VanishService(Core core, EssentialsConfig config, ModuleMessages msg) {
        super(core, config, msg);
    }

    @Override
    void register() {
        var target = player("player");
        core.commands().register(CommandSpec.command("vanish")
                .description("Toggle vanish (invisible to players)").category("Staff")
                .permission(Perms.VANISH).optional(target).aliases("v")
                .executes(ctx -> toggle(ctx.sender(), ctx.getOr(target, null))));
        core.placeholders().registerText(MODULE, "vanished", p -> p != null && isVanished(p) ? "true" : "false");
    }

    boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    private void toggle(CommandSender sender, Player given) {
        Player target = targetOrSelf(sender, given, Perms.VANISH_OTHERS);
        boolean enable = !isVanished(target);
        set(target, enable);
        String key = enable ? "vanish.enabled" : "vanish.disabled";
        if (same(sender, target)) {
            msg.send(target, key);
        } else {
            msg.send(sender, key + "-other", Msg.p("player", target.getName()));
            notifyTarget(sender, target, key + "-notify");
        }
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(Perms.NOTIFY) && !p.equals(sender) && !p.equals(target))
                .forEach(p -> msg.send(p, enable ? "vanish.staff-on" : "vanish.staff-off", Msg.p("player", target.getName())));
        audit(enable ? "vanish_on" : "vanish_off", sender, target, Map.of());
    }

    // The "vanished" metadata is the de-facto convention other plugins check, so we keep using it despite the deprecation.
    @SuppressWarnings("deprecation")
    void set(Player player, boolean enable) {
        if (enable) {
            vanished.add(player.getUniqueId());
            player.setMetadata(SETTING, new FixedMetadataValue(core.plugin(), true));
            player.setCanPickupItems(false);
            player.setSleepingIgnored(true);
            if (config.vanishNightVision) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player) && !other.hasPermission(Perms.VANISH_SEE)) {
                    other.hidePlayer(core.plugin(), player);
                }
            }
        } else {
            vanished.remove(player.getUniqueId());
            player.removeMetadata(SETTING, core.plugin());
            player.setCanPickupItems(true);
            player.setSleepingIgnored(false);
            if (config.vanishNightVision) {
                PotionEffect nv = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
                if (nv != null && nv.isInfinite()) {
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(core.plugin(), player);
            }
        }
        core.players().get(player.getUniqueId()).ifPresent(d -> d.setSetting(SETTING, enable ? "true" : null));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        // Hide already-vanished staff from the newcomer.
        if (!joined.hasPermission(Perms.VANISH_SEE)) {
            for (UUID id : vanished) {
                Player v = Bukkit.getPlayer(id);
                if (v != null) {
                    joined.hidePlayer(core.plugin(), v);
                }
            }
        }
        boolean stay = joined.hasPermission(Perms.VANISH) && core.players().get(joined.getUniqueId())
                .map(d -> "true".equals(d.setting(SETTING, null))).orElse(false);
        if (stay) {
            set(joined, true);
            event.joinMessage(null);
            msg.send(joined, "vanish.still");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.remove(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && vanished.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Un-vanishes everyone visibly on shutdown (their setting stays, so they vanish again on rejoin). */
    void shutdown() {
        for (UUID id : Set.copyOf(vanished)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.removeMetadata(SETTING, core.plugin());
                for (Player other : Bukkit.getOnlinePlayers()) {
                    other.showPlayer(core.plugin(), p);
                }
            }
        }
        vanished.clear();
    }
}
