package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Staff tools through the Wardrobe button in {@code /inspect} (WD25, no new command): view a player's sets and chest;
 * admins give a set, give a Set Token or a Wardrobe Tome, set a set's level or remove a set. Everything audited (category staff). Works on the target's
 * active profile (offline: last active).
 */
final class StaffActions {

    private final WardrobeModule module;

    StaffActions(WardrobeModule module) {
        this.module = module;
    }

    void open(Player staff, UUID target, String targetName, Menu parent) {
        if (!staff.hasPermission(WardrobeModule.PERM_STAFF)) {
            module.fail(staff, "no-permission");
            return;
        }
        UUID staffId = staff.getUniqueId();
        ProfileService profiles = ProfileService.of(module.core.services());
        profiles.profileKeyAsync(target).thenCompose(profile -> module.core.database().async(c -> new Object[]{profile,
                module.store().activeEntries(c, profile), module.store().storageCount(c, profile)})).whenComplete((data, error) ->
                module.core.scheduler().main("wardrobe-staff", () -> {
                    Player p = Bukkit.getPlayer(staffId);
                    if (p == null) {
                        return;
                    }
                    if (error != null) {
                        module.core.plugin().getLogger().log(Level.WARNING, "Wardrobe: staff view failed", Database.cause(error));
                        module.fail(p, "error");
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<WardrobeStore.Entry> entries = (List<WardrobeStore.Entry>) data[1];
                    StaffMenu menu = new StaffMenu(module, target, targetName, (UUID) data[0], entries, (Integer) data[2]);
                    if (parent != null) {
                        menu.parent(parent);
                    }
                    module.core.menus().open(p, menu);
                    module.core.audit().logPlayer(AuditCategory.STAFF, "wardrobe_view", Actor.player(p), target, targetName, null, null, Map.of());
                }));
    }

    private void reopen(Player staff, StaffMenu menu) {
        open(staff, menu.target, menu.targetName, menu.parent());
    }

    /** Reloads the target's session when they are online on that profile. */
    private void refreshTarget(UUID target, UUID profile) {
        Player t = Bukkit.getPlayer(target);
        if (t != null && ProfileService.of(module.core.services()).profileKey(t).equals(profile)) {
            module.equipment().load(t);
        }
    }

    void remove(Player staff, StaffMenu menu, WardrobeStore.Entry entry) {
        if (!staff.hasPermission(WardrobeModule.PERM_ADMIN)) {
            module.fail(staff, "no-permission");
            return;
        }
        UUID staffId = staff.getUniqueId();
        module.ops().removeSet(entry.entryId(), staffId).thenAccept(r -> module.core.scheduler().main("wardrobe-staff-remove", () -> {
            Player p = Bukkit.getPlayer(staffId);
            if (r.ok()) {
                module.core.audit().logPlayer(AuditCategory.STAFF, "wardrobe_set_removed", p == null ? Actor.system("wardrobe") : Actor.player(p),
                        menu.target, menu.targetName, null, null,
                        Map.of("set", entry.setId(), "entry", entry.entryId().toString(), "profile", menu.profile.toString()));
                refreshTarget(menu.target, menu.profile);
            }
            if (p != null) {
                if (r.ok()) {
                    module.msg().send(p, "staff.removed", Msg.p("set", entry.setId()), Msg.p("player", menu.targetName));
                    reopen(p, menu);
                } else {
                    module.fail(p, r.status() == WardrobeOps.Status.GONE ? "staff.gone" : "error");
                }
            }
        }));
    }

    void giveSet(Player staff, StaffMenu menu, SetDef def) {
        if (!staff.hasPermission(WardrobeModule.PERM_ADMIN)) {
            module.fail(staff, "no-permission");
            return;
        }
        UUID staffId = staff.getUniqueId();
        module.ops().giveSet(menu.profile, def.id(), staffId, module.config().seasonId).thenAccept(r -> module.core.scheduler().main("wardrobe-staff-give", () -> {
            Player p = Bukkit.getPlayer(staffId);
            if (r.ok()) {
                module.core.audit().logPlayer(AuditCategory.STAFF, "wardrobe_set_given", p == null ? Actor.system("wardrobe") : Actor.player(p),
                        menu.target, menu.targetName, null, null,
                        Map.of("set", def.id(), "entry", r.entry().entryId().toString(), "profile", menu.profile.toString()));
                refreshTarget(menu.target, menu.profile);
            }
            if (p != null) {
                switch (r.status()) {
                    case OK -> {
                        module.msg().send(p, "staff.given", Msg.p("set", def.name()), Msg.p("player", menu.targetName));
                        reopen(p, menu);
                    }
                    case OWNED -> module.fail(p, "staff.owned", Msg.p("player", menu.targetName));
                    default -> module.fail(p, "error");
                }
            }
        }));
    }

    /** Sets an entry's level (staff test tool, WD25). Audited. */
    void setLevel(Player staff, StaffMenu menu, WardrobeStore.Entry entry, int level) {
        if (!staff.hasPermission(WardrobeModule.PERM_ADMIN)) {
            module.fail(staff, "no-permission");
            return;
        }
        UUID staffId = staff.getUniqueId();
        module.ops().setLevel(entry.entryId(), menu.profile, level, module.config().levels(), staffId)
                .thenAccept(r -> module.core.scheduler().main("wardrobe-staff-level", () -> {
                    Player p = Bukkit.getPlayer(staffId);
                    if (r.ok()) {
                        module.core.audit().logPlayer(AuditCategory.STAFF, "wardrobe_level_set", p == null ? Actor.system("wardrobe") : Actor.player(p),
                                menu.target, menu.targetName, null, null, Map.of("set", entry.setId(), "entry", entry.entryId().toString(),
                                        "from", entry.level(), "to", r.entry().level(), "profile", menu.profile.toString()));
                        refreshTarget(menu.target, menu.profile);
                    }
                    if (p != null) {
                        if (r.ok()) {
                            module.msg().send(p, "staff.level-set", Msg.p("set", module.text().entryName(module.registry(), entry)),
                                    Msg.p("player", menu.targetName), Msg.p("level", r.entry().level()));
                            reopen(p, menu);
                        } else {
                            module.fail(p, r.status() == WardrobeOps.Status.GONE ? "staff.gone" : "error");
                        }
                    }
                }));
    }

    /** Gives a new Wardrobe Tome to the inspected player (must be online with a free slot). */
    void giveTome(Player staff, StaffMenu menu, String kind) {
        if (!staff.hasPermission(WardrobeModule.PERM_ADMIN)) {
            module.fail(staff, "no-permission");
            return;
        }
        Player target = Bukkit.getPlayer(menu.target);
        if (target == null) {
            module.fail(staff, "staff.offline", Msg.p("player", menu.targetName));
            return;
        }
        if (noRoom(target)) {
            module.fail(staff, "staff.target-full", Msg.p("player", menu.targetName));
            return;
        }
        UUID staffId = staff.getUniqueId();
        module.tomes().create(kind, "staff:" + staff.getName(), menu.target, Actor.player(staff)).whenComplete((item, error) -> {
            Player p = Bukkit.getPlayer(staffId);
            Player t = Bukkit.getPlayer(menu.target);
            if (error != null) {
                if (p != null) {
                    module.fail(p, "error");
                }
                return;
            }
            UUID id = net.skybornmc.skyblock.common.item.ItemRegistry.uuidOf(item).orElseThrow();
            if (t == null || noRoom(t)) {
                module.ops().voidTome(id, "not_delivered");
                if (p != null) {
                    module.fail(p, "staff.target-full", Msg.p("player", menu.targetName));
                }
                return;
            }
            net.skybornmc.skyblock.common.box.ItemBox.give(module.core.services(), t, net.skybornmc.skyblock.common.box.BoxSource.STAFF, item);
            module.core.audit().logPlayer(AuditCategory.STAFF, "wardrobe_tome_given", p == null ? Actor.system("wardrobe") : Actor.player(p),
                    menu.target, menu.targetName, null, null, Map.of("kind", kind, "tome", id.toString()));
            module.msg().send(t, "tome-received");
            if (p != null) {
                module.msg().send(p, "staff.tome-given", Msg.p("player", menu.targetName));
            }
        });
    }

    /** Gives a new Set Token to the inspected player (must be online with a free slot). */
    void giveToken(Player staff, StaffMenu menu, SetDef def) {
        if (!staff.hasPermission(WardrobeModule.PERM_ADMIN)) {
            module.fail(staff, "no-permission");
            return;
        }
        Player target = Bukkit.getPlayer(menu.target);
        if (target == null) {
            module.fail(staff, "staff.offline", Msg.p("player", menu.targetName));
            return;
        }
        if (noRoom(target)) {
            module.fail(staff, "staff.target-full", Msg.p("player", menu.targetName));
            return;
        }
        UUID staffId = staff.getUniqueId();
        module.shop().createToken(def.id(), "staff:" + staff.getName(), menu.target, Actor.player(staff)).whenComplete((item, error) -> {
            Player p = Bukkit.getPlayer(staffId);
            Player t = Bukkit.getPlayer(menu.target);
            if (error != null) {
                if (p != null) {
                    module.fail(p, "error");
                }
                return;
            }
            UUID id = net.skybornmc.skyblock.common.item.ItemRegistry.uuidOf(item).orElseThrow();
            if (t == null || noRoom(t)) {
                module.ops().voidToken(id, "not_delivered");
                if (p != null) {
                    module.fail(p, "staff.target-full", Msg.p("player", menu.targetName));
                }
                return;
            }
            ItemStack give = item;
            net.skybornmc.skyblock.common.box.ItemBox.give(module.core.services(), t, net.skybornmc.skyblock.common.box.BoxSource.STAFF, give);
            module.core.audit().logPlayer(AuditCategory.STAFF, "wardrobe_token_given", p == null ? Actor.system("wardrobe") : Actor.player(p),
                    menu.target, menu.targetName, null, null, Map.of("set", def.id(), "token", id.toString()));
            module.msg().send(t, "token-received", Msg.p("set", def.name()));
            if (p != null) {
                module.msg().send(p, "staff.token-given", Msg.p("set", def.name()), Msg.p("player", menu.targetName));
            }
        });
    }

    /** No free slot and no /box to take the rest (owner answer 7: with the box module the item waits in /box instead). */
    private boolean noRoom(Player target) {
        return Equipment.freeSlots(target.getInventory()) < 1 && net.skybornmc.skyblock.common.box.ItemBox.of(module.core.services()).isEmpty();
    }
}
