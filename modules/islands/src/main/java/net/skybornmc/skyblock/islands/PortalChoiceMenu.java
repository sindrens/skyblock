package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListLayout;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.Comparator;
import java.util.List;

/**
 * TUTORIAL TQ19: the island portal with pending invites. Instead of creating an island at once the player picks:
 * <b>Create my own island</b> or <b>Join &lt;island&gt;</b> (one button per invite, newest first). Closing the menu
 * does nothing (walk into the portal again). Bedrock-safe.
 *
 * <p>GUI-4c: every invite is shown (was max 3, the rest silently hidden): the choices are laid out like a pick list
 * ({@link PickListLayout}: 3 rows up to 6 invites, then 4-6 rows), Create first. The "?" explains the choice.</p>
 */
final class PortalChoiceMenu extends Menu {

    /** Create + invites on one screen (the 6-row pick list's 28 slots). */
    static final int MAX_CHOICES = PickListLayout.PER_PAGE;

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
        this.invites = invites.stream().sorted(Comparator.comparingLong(Invites.Invite::createdAt).reversed()).limit(MAX_CHOICES - 1L).toList();
        this.create = create;
    }

    @Override
    public int rows() {
        return PickListLayout.rows(1 + invites.size());
    }

    @Override
    public String infoKey() {
        return "portal-choice.info";
    }

    @Override
    public String helpTopic() {
        return "island";
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
        int shown = 1 + invites.size();
        set(PickListLayout.slot(0, shown), service.item(Material.GRASS_BLOCK, viewer, IslandsModule.ID, "portal-choice.create"), click -> {
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
            set(PickListLayout.slot(i + 1, shown), service.item(Material.OAK_DOOR, viewer, IslandsModule.ID, "portal-choice.join", Msg.p("island", name),
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
