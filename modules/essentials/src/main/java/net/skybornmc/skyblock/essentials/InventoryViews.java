package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /invsee} and {@code /ecsee}: opens the target's live inventory / ender chest. View-only unless the staff
 * member has the {@code .edit} permission; when edited, every changed slot is written to the audit log on close.
 */
final class InventoryViews extends CommandGroup implements Listener {

    private record Session(UUID target, String targetName, String kind, Inventory inventory, ItemStack[] before, boolean editable) {
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    InventoryViews(Core core, EssentialsConfig config, ModuleMessages msg) {
        super(core, config, msg);
    }

    @Override
    void register() {
        var invTarget = player("player");
        core.commands().register(CommandSpec.command("invsee")
                .description("Look into a player's inventory").category("Staff")
                .permission(Perms.INVSEE).playerOnly().arg(invTarget).aliases("inv").example("invsee Steve")
                .executes(ctx -> open(ctx.player(), ctx.get(invTarget), "inventory")));
        var ecTarget = player("player");
        core.commands().register(CommandSpec.command("ecsee")
                .description("Look into a player's ender chest").category("Staff")
                .permission(Perms.ECSEE).playerOnly().arg(ecTarget).aliases("endersee").example("ecsee Steve")
                .executes(ctx -> open(ctx.player(), ctx.get(ecTarget), "enderchest")));
    }

    private void open(Player staff, Player target, String kind) {
        boolean ender = kind.equals("enderchest");
        if (!ender && staff.equals(target)) {
            throw fail("invsee.self");
        }
        boolean editable = staff.hasPermission(ender ? Perms.ECSEE_EDIT : Perms.INVSEE_EDIT);
        Inventory inventory = ender ? target.getEnderChest() : target.getInventory();
        staff.openInventory(inventory);
        sessions.put(staff.getUniqueId(), new Session(target.getUniqueId(), target.getName(), kind, inventory,
                copy(inventory.getContents()), editable));
        msg.send(staff, editable ? "invsee.opened-edit" : "invsee.opened-view",
                Msg.p("player", target.getName()), Msg.p("kind", ender ? "ender chest" : "inventory"));
        audit(ender ? "ecsee" : "invsee", staff, target, Map.of("editable", editable));
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            out[i] = contents[i] == null ? null : contents[i].clone();
        }
        return out;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Session s = sessions.get(event.getWhoClicked().getUniqueId());
        if (s != null && !s.editable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        Session s = sessions.get(event.getWhoClicked().getUniqueId());
        if (s != null && !s.editable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session s = sessions.remove(event.getPlayer().getUniqueId());
        if (s == null || !s.editable()) {
            return;
        }
        List<String> changes = diff(s.before(), s.inventory().getContents());
        if (!changes.isEmpty() && event.getPlayer() instanceof Player staff) {
            Player target = Bukkit.getPlayer(s.target());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("kind", s.kind());
            details.put("changes", changes);
            details.put("target_name", s.targetName());
            audit(s.kind().equals("enderchest") ? "ecsee_edit" : "invsee_edit", staff, target, details);
            msg.send(staff, "invsee.logged", Msg.p("count", changes.size()), Msg.p("player", s.targetName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Close views of staff looking at a player who leaves (the inventory object would go stale).
        UUID left = event.getPlayer().getUniqueId();
        for (Map.Entry<UUID, Session> e : Map.copyOf(sessions).entrySet()) {
            if (e.getValue().target().equals(left)) {
                Player staff = Bukkit.getPlayer(e.getKey());
                if (staff != null) {
                    staff.closeInventory();
                    msg.send(staff, "invsee.target-left", Msg.p("player", e.getValue().targetName()));
                }
            }
        }
    }

    /** {@code "slot 3: diamond x64 -> air"} lines for every slot that changed. */
    static List<String> diff(ItemStack[] before, ItemStack[] after) {
        List<String> out = new ArrayList<>();
        int n = Math.max(before.length, after.length);
        for (int i = 0; i < n; i++) {
            ItemStack a = i < before.length ? before[i] : null;
            ItemStack b = i < after.length ? after[i] : null;
            if (!same(a, b)) {
                out.add("slot " + i + ": " + describe(a) + " -> " + describe(b));
            }
        }
        return out;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        boolean ae = a == null || a.isEmpty();
        boolean be = b == null || b.isEmpty();
        if (ae || be) {
            return ae == be;
        }
        return a.equals(b);
    }

    private static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "air";
        }
        return stack.getType().getKey().getKey() + " x" + stack.getAmount() + (stack.hasItemMeta() ? " (custom)" : "");
    }

    void shutdown() {
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.closeInventory();
            }
        }
        sessions.clear();
    }
}
