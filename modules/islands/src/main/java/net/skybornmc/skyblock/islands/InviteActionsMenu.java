package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

/**
 * Accept or deny one received invite (ISL2, opened from {@link InvitesMenu}; 3 rows, big buttons, Bedrock-safe):
 * <pre>
 *  R O B B B B B O R
 *  B . ✔ . ☺ . ✘ . B     11 accept, 13 the invite, 15 deny
 *  ← O B B ✘ B B O R     18 Back, 22 Close (framework)
 * </pre>
 * Accepting re-checks everything in the database (slots, join limits, one island per player).
 */
final class InviteActionsMenu extends Menu {

    static final int SLOT_ACCEPT = 11;
    static final int SLOT_INFO_ITEM = 13;
    static final int SLOT_DENY = 15;

    private final IslandsModule module;
    private final IslandManager islands;
    private final MemberManager members;
    private final Invites.Invite invite;

    InviteActionsMenu(IslandsModule module, IslandManager islands, MemberManager members, Invites.Invite invite) {
        this.module = module;
        this.islands = islands;
        this.members = members;
        this.invite = invite;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("invites.crumb"), text("invites.actions.crumb", p()));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    private TagResolver[] p() {
        Island island = islands.byId(invite.islandId());
        return new TagResolver[]{Msg.p("island", island == null ? "?" : islands.displayName(island)),
                Msg.p("player", islands.name(invite.inviter()))};
    }

    @Override
    protected void build() {
        frame();
        TagResolver[] p = p();
        set(SLOT_INFO_ITEM, service.item(Material.GRASS_BLOCK, viewer, IslandsModule.ID, "invites.actions.info", p));
        set(SLOT_ACCEPT, service.item(Material.LIME_CONCRETE, viewer, IslandsModule.ID, "invites.actions.accept", p), click -> {
            viewer.closeInventory();
            members.accept(viewer, invite);
        });
        set(SLOT_DENY, service.item(Material.RED_CONCRETE, viewer, IslandsModule.ID, "invites.actions.deny", p), click -> {
            members.deny(viewer, invite);
            back();
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }
}
