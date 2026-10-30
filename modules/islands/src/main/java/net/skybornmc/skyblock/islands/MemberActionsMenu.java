package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/**
 * What the viewer can do to one player of their island (opened from {@link MembersMenu}, 3 rows, big buttons):
 * <pre>
 *  R O B B B B B O R
 *  B ☺ . ▲ ▼ ★ . ✘ B     10 head, 12 promote, 13 demote, 14 transfer, 16 kick / uncoop / leave (self)
 *  R O B B ← B B O R     22 back
 * </pre>
 * Only the buttons the viewer may use are shown; every action re-checks the rules when it runs.
 */
final class MemberActionsMenu extends Menu {

    static final int SLOT_HEAD = 10;
    static final int SLOT_PROMOTE = 12;
    static final int SLOT_DEMOTE = 13;
    static final int SLOT_TRANSFER = 14;
    static final int SLOT_REMOVE = 16;
    static final int SLOT_BACK_SMALL = 22;

    private final IslandsModule module;
    private final IslandManager islands;
    private final Island island;
    private final UUID target;

    MemberActionsMenu(IslandsModule module, IslandManager islands, Island island, UUID target) {
        this.module = module;
        this.islands = islands;
        this.island = island;
        this.target = target;
    }

    /** True if {@code viewer} (role) has at least one action on a player with {@code target} role. */
    static boolean hasActions(Role viewer, Role target, boolean self) {
        if (self) {
            return MemberRules.canLeave(viewer);
        }
        if (target == Role.COOP) {
            return MemberRules.canCoop(viewer);
        }
        return MemberRules.canKick(viewer, target) || MemberRules.canPromote(viewer, target) || MemberRules.canDemote(viewer, target)
                || MemberRules.canTransfer(viewer, target);
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("members.crumb"), text("actions.crumb", Msg.p("player", islands.name(target))));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    protected void build() {
        frame();
        String name = islands.name(target);
        Role viewerRole = island.roleOrNull(islands.key(viewer));
        Role role = island.roleOrNull(target);
        boolean self = target.equals(islands.key(viewer));
        TagResolver[] p = {Msg.p("player", name), Msg.p("role", role == null ? "-" : module.raw("role." + MemberStore.key(role)))};
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(islands.person(target))));
        set(SLOT_HEAD, service.item(head, text("actions.head.name", p), lines("actions.head.lore", p)));
        MemberManager members = islands.memberManager();
        if (role == null) {
            set(13, service.item(Material.BARRIER, viewer, IslandsModule.ID, "actions.gone", p));
        } else if (self) {
            if (MemberRules.canLeave(viewerRole)) {
                set(SLOT_REMOVE, service.item(Material.OAK_DOOR, viewer, IslandsModule.ID, "actions.leave", p),
                        click -> members.leave(viewer, this));
            }
        } else if (role == Role.COOP) {
            if (MemberRules.canCoop(viewerRole)) {
                set(SLOT_REMOVE, service.item(Material.SHEARS, viewer, IslandsModule.ID, "actions.uncoop", p), click -> {
                    members.uncoop(viewer, name);
                    back();
                });
            }
        } else {
            if (MemberRules.canPromote(viewerRole, role)) {
                set(SLOT_PROMOTE, service.item(Material.LIME_DYE, viewer, IslandsModule.ID,
                        role == Role.MOD ? "actions.promote-admin" : "actions.promote", p), click -> {
                    members.promote(viewer, name);
                    back();
                });
            }
            if (MemberRules.canDemote(viewerRole, role)) {
                set(SLOT_DEMOTE, service.item(Material.ORANGE_DYE, viewer, IslandsModule.ID,
                        role == Role.ADMIN ? "actions.demote-admin" : "actions.demote", p), click -> {
                    members.demote(viewer, name);
                    back();
                });
            }
            if (MemberRules.canTransfer(viewerRole, role)) {
                set(SLOT_TRANSFER, service.item(Material.GOLDEN_HELMET, viewer, IslandsModule.ID, "actions.transfer", p),
                        click -> members.transfer(viewer, name, this));
            }
            if (MemberRules.canKick(viewerRole, role)) {
                set(SLOT_REMOVE, service.item(Material.IRON_DOOR, viewer, IslandsModule.ID, "actions.kick", p),
                        click -> members.kick(viewer, name, this));
            }
        }
        set(SLOT_BACK_SMALL, service.item(Material.ARROW, viewer, IslandsModule.ID, "actions.back"), click -> back());
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
