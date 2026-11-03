package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.profile.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owner 3 Nov 2026 ("it will automatically switch tools to island tool when on island and depth when in depth"): Depths
 * tools exist only in the Depths worlds ({@code tools.yml world-tools.depths-worlds}). Leaving the Depths puts them in
 * the {@code tool_stash} table with their slot; entering it again puts them back in the same slots. A player who arrives
 * in the Depths without any Depths tool gets the starter kit (the Quartermaster's claim, with its cooldown).
 *
 * <p>Dupe safety: a tool leaves the inventory first and is written second; a failed write gives it straight back.
 * Restoring reads the rows, puts the tools back, saves the player and only then deletes the rows; a tool whose id is
 * already in the inventory is never given twice (covers a crash between any two of those writes). One run per player at
 * a time.</p>
 */
final class WorldTools implements Listener {

    private final ToolsModule module;
    private final Set<UUID> busy = new HashSet<>();

    WorldTools(ToolsModule module) {
        this.module = module;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, module.core.plugin());
    }

    void stop() {
        HandlerList.unregisterAll(this);
    }

    /** Pure: true when {@code world} is one of the Depths worlds (ignoring case). */
    static boolean depthsWorld(List<String> depthsWorlds, String world) {
        return world != null && depthsWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        place(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // After the profile's inventory is loaded (profiles module) and the join teleports are done.
        module.core.scheduler().later("tools-world-join", 40, () -> {
            if (player.isOnline()) {
                place(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }

    /** Puts the tools of the player's current world in place (stash or restore). Main thread. */
    void place(Player player) {
        if (!module.config().worldToolsEnabled || player.hasPermission("skyblock.tools.world-bypass")) {
            return;
        }
        if (depthsWorld(module.config().worldToolsDepthsWorlds, player.getWorld().getName())) {
            restore(player);
        } else {
            stash(player);
        }
    }

    private UUID profile(Player player) {
        return ProfileService.of(module.core.services()).profileKey(player);
    }

    // ------------------------------------------------------------------ out of the Depths

    private record Stashed(UUID toolId, int slot, ItemStack item) {
    }

    private void stash(Player player) {
        PlayerInventory inv = player.getInventory();
        List<Stashed> out = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            var data = ToolItems.read(s);
            if (data.isPresent()) {
                out.add(new Stashed(data.get().id(), i, s.clone()));
            }
        }
        if (out.isEmpty() || !busy.add(player.getUniqueId())) {
            return;
        }
        for (Stashed s : out) {
            inv.setItem(s.slot(), null); // out first: nothing can be dropped or traded while the write runs
        }
        UUID profile = profile(player);
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        module.core.database().transaction(c -> {
            for (Stashed s : out) {
                Sql.update(c, "DELETE FROM tool_stash WHERE profile_key = ? AND tool_id = ?", profile, s.toolId());
                Sql.update(c, "INSERT INTO tool_stash (profile_key, tool_id, slot, item, stashed_at) VALUES (?, ?, ?, ?, ?)",
                        profile, s.toolId(), s.slot(), s.item().serializeAsBytes(), now);
            }
            return null;
        }).whenComplete((ok, error) -> module.core.scheduler().main("tools-world-stash", () -> {
            busy.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: could not put away the Depths tools of " + player.getName()
                        + " (given back)", Database.cause(error));
                giveBack(p != null ? p : player, out);
                return;
            }
            module.core.audit().logPlayer(AuditCategory.ITEM, "tools_put_away", Actor.SYSTEM, id, player.getName(), (long) out.size(),
                    null, Map.of("profile", profile.toString()));
            if (p != null && !out.isEmpty()) {
                module.msg().actionBar(p, "world-tools.put-away", net.skybornmc.skyblock.common.text.Msg.number("count", out.size()));
            }
        }));
    }

    private void giveBack(Player player, List<Stashed> items) {
        if (player == null || !player.isOnline()) {
            return; // still in the database? No: the write failed, so the player file (not saved yet) still has them
        }
        for (Stashed s : items) {
            putInSlot(player, s.slot(), s.item());
        }
    }

    // ------------------------------------------------------------------ into the Depths

    private void restore(Player player) {
        if (!busy.add(player.getUniqueId())) {
            return;
        }
        UUID profile = profile(player);
        UUID id = player.getUniqueId();
        module.core.database().async(c -> rows(c, profile)).whenComplete((rows, error) -> module.core.scheduler().main("tools-world-restore", () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !depthsWorld(module.config().worldToolsDepthsWorlds, p.getWorld().getName())) {
                busy.remove(id);
                return;
            }
            if (error != null) {
                busy.remove(id);
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: could not read the put-away tools of " + p.getName(), Database.cause(error));
                return;
            }
            Set<UUID> carried = new HashSet<>();
            for (ItemStack s : p.getInventory().getContents()) {
                ToolItems.read(s).ifPresent(d -> carried.add(d.id()));
            }
            List<UUID> done = new ArrayList<>();
            for (Stashed s : rows) {
                if (!carried.contains(s.toolId())) {
                    putInSlot(p, s.slot(), s.item());
                    carried.add(s.toolId());
                }
                done.add(s.toolId());
            }
            if (done.isEmpty()) {
                busy.remove(id);
                autoStarter(p, carried);
                return;
            }
            // Saved first, rows deleted second: a crash in between leaves a row whose tool is carried (skipped next time).
            net.skybornmc.skyblock.common.player.PlayerSaves.save(p, "Depths tools given back");
            module.core.database().run(c -> {
                for (UUID tool : done) {
                    Sql.update(c, "DELETE FROM tool_stash WHERE profile_key = ? AND tool_id = ?", profile, tool);
                }
            }).whenComplete((v, e) -> module.core.scheduler().main("tools-world-restored", () -> busy.remove(id)));
            module.msg().actionBar(p, "world-tools.given-back", net.skybornmc.skyblock.common.text.Msg.number("count", done.size()));
        }));
    }

    /** A player in the Depths without any Depths tool gets the starter kit (the Quartermaster's claim and cooldown). */
    private void autoStarter(Player player, Set<UUID> carried) {
        if (module.config().worldToolsAutoStarter && carried.isEmpty()) {
            module.forge().claimStarter(player);
        }
    }

    private static List<Stashed> rows(Connection c, UUID profile) throws SQLException {
        return Sql.query(c, "SELECT tool_id, slot, item FROM tool_stash WHERE profile_key = ? ORDER BY stashed_at",
                rs -> new Stashed(Sql.uuid(rs, "tool_id"), rs.getInt("slot"), ItemStack.deserializeBytes(rs.getBytes("item"))), profile);
    }

    private void putInSlot(Player player, int slot, ItemStack item) {
        PlayerInventory inv = player.getInventory();
        if (slot >= 0 && slot < inv.getSize() && (inv.getItem(slot) == null || inv.getItem(slot).isEmpty())) {
            inv.setItem(slot, item);
            return;
        }
        module.forge().give(player, item);
    }
}
