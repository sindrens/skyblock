package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.Comparator;
import java.util.List;

/**
 * TUTORIAL TQ19: the island portal with pending invites. Instead of creating an island at once the player picks:
 * <b>Create my own island</b> or <b>Join &lt;island&gt;</b> (one button per invite, newest first, max 3). Closing the menu
 * does nothing (walk into the portal again). 3 rows, Bedrock-safe.
 * <pre>
 *  R O B B B B B O R
 *  B . C . J J J . B     11 create, 13-15 join
 *  R O B B B B B O R
 * </pre>
 */
final class PortalChoiceMenu extends Menu {

    private static final int SLOT_CREATE = 11;
    private static final int[] SLOTS_JOIN = {13, 14, 15};

    private final IslandsModule module;
    private final IslandManager islands;
    private final MemberManager members;
    private final List<Invites.Invite> invites;
    private final Runnable create;
    private boolean used;

    PortalChoiceMenu(IslandsModule module, IslandManager islands, MemberManager members, List<Invites.Invite> invites, Runnable create) {
        this.module = module;
        this.islands = islands;
        this.members = members;
        this.invites = invites.stream().sorted(Comparator.comparingLong(Invites.Invite::createdAt).reversed()).limit(SLOTS_JOIN.length).toList();
        this.create = create;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, IslandsModule.ID, "portal-choice.title"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_CREATE, service.item(Material.GRASS_BLOCK, viewer, IslandsModule.ID, "portal-choice.create"), click -> {
            if (used) {
                return;
            }
            used = true;
            click.player().closeInventory();
            create.run();
        });
        for (int i = 0; i < invites.size(); i++) {
            Invites.Invite invite = invites.get(i);
            Island island = islands.byId(invite.islandId());
            if (island == null) {
                continue;
            }
            String name = islands.displayName(island);
            set(SLOTS_JOIN[i], service.item(Material.OAK_DOOR, viewer, IslandsModule.ID, "portal-choice.join", Msg.p("island", name),
                    Msg.number("members", island.memberCount())), click -> {
                if (used) {
                    return;
                }
                used = true;
                click.player().closeInventory();
                members.acceptFromPortal(click.player(), invite);
            });
        }
    }
}
