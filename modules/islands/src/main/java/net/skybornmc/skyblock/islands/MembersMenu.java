package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.menu.PlayerPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /is members} (M2.3): one head per member (leader, mods, members by join date, then coop players) with role,
 * join date, inviter and online state. Clicking a head opens the actions the viewer may use on that player
 * ({@link MemberActionsMenu}; one plain click, Bedrock-safe). Top: island summary. Bottom row: Invites (46, ISL2),
 * Invite (47, leader/admin/mod), Permissions (51, the matrix) and Settings (52, ISL2). Live: refreshes every 2 s so roles
 * and online states stay current.
 */
final class MembersMenu extends PagedMenu<MembersMenu.Entry> {

    /** One listed player. */
    record Entry(UUID uuid, Role role) {
    }

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_INVITE = 47;
    static final int SLOT_PERMISSIONS = 51;
    static final int SLOT_INVITES = 46;
    static final int SLOT_SETTINGS = 52;

    private final IslandsModule module;
    private final IslandManager islands;
    private final Island island;

    MembersMenu(IslandsModule module, IslandManager islands, Island island) {
        this.module = module;
        this.islands = islands;
        this.island = island;
    }

    /** Leader first, then admins, mods, members (each by join date), then coop players. */
    static List<Entry> order(Map<UUID, Role> members, Iterable<UUID> coops, java.util.function.ToLongFunction<UUID> joined) {
        List<Entry> list = new ArrayList<>();
        members.forEach((uuid, role) -> list.add(new Entry(uuid, role)));
        list.sort(Comparator.comparingInt((Entry e) -> -MemberRules.rank(e.role())).thenComparingLong(e -> joined.applyAsLong(e.uuid())));
        for (UUID coop : coops) {
            if (!members.containsKey(coop)) {
                list.add(new Entry(coop, Role.COOP));
            }
        }
        return list;
    }

    @Override
    protected List<Entry> entries() {
        return order(island.members(), island.coops(), island::joinedAt);
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("menu.crumb"), text("members.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "members.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected String emptyKey() {
        return "members.empty";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        if (islands.byId(island.id()) != island || island.roleOrNull(islands.key(viewer)) == null
                || island.roleOrNull(islands.key(viewer)) == Role.COOP) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    private Role viewerRole() {
        return island.roleOrNull(islands.key(viewer));
    }

    @Override
    protected void decorate() {
        int slots = islands.slots(island);
        set(SLOT_SUMMARY, service.item(new ItemStack(Material.GRASS_BLOCK), text("members.summary.name", Msg.p("island", islands.displayName(island))),
                lines(island.team() ? "members.summary.lore-team" : "members.summary.lore-solo",
                        Msg.p("members", island.memberCount()), Msg.p("slots", slots), Msg.p("coops", island.coops().size()),
                        Msg.p("max-coops", module.config().maxCoops), Msg.p("max-slots", module.config().maxSlots))));
        MemberManager members = islands.memberManager();
        if (MemberRules.canInvite(viewerRole())) {
            boolean full = island.memberCount() >= slots;
            set(SLOT_INVITE, service.item(new ItemStack(full ? Material.GRAY_DYE : Material.WRITABLE_BOOK),
                    text("members.invite.name"), lines(full ? "members.invite.lore-full" : "members.invite.lore",
                            Msg.p("members", island.memberCount()), Msg.p("slots", slots))), click -> {
                if (island.memberCount() >= islands.slots(island)) {
                    islands.fail(viewer, "invite.full", Msg.p("slots", islands.slots(island)));
                    return;
                }
                openChild(new PlayerPickerMenu(v -> text("members.invite.crumb"),
                        p -> !p.equals(viewer) && islands.islandOfPlayer(islands.key(p)).isEmpty(),
                        (v, picked) -> {
                            v.closeInventory();
                            members.invite(v, picked, null);
                        }));
            });
        }
        int pending = members.invites().pendingFrom(island.id(), System.currentTimeMillis())
                + members.invites().of(islands.key(viewer), System.currentTimeMillis()).size();
        set(SLOT_INVITES, service.item(new ItemStack(Material.PAPER), text("members.invites.name"),
                lines("members.invites.lore", Msg.p("count", pending))), click -> openChild(new InvitesMenu(module, islands, members)));
        set(SLOT_SETTINGS, service.item(new ItemStack(Material.REPEATER), text("members.settings.name"),
                lines("members.settings.lore")), click -> module.islandSettings().openFor(viewer, island, this));
        set(SLOT_PERMISSIONS, service.item(new ItemStack(Material.COMPARATOR), text("members.permissions.name"),
                lines(MemberRules.canEditPerms(viewerRole(), PermRole.MEMBER) ? "members.permissions.lore-edit" : "members.permissions.lore-view")),
                click -> openChild(new PermissionsMenu(module, islands, island)));
    }

    @Override
    protected MenuButton entry(Entry entry) {
        String name = islands.name(entry.uuid());
        boolean online = islands.online(entry.uuid()) != null;
        TagResolver[] p = {Msg.p("player", name), Msg.p("role", module.raw("role." + MemberStore.key(entry.role())))};
        List<Component> lore = new ArrayList<>(lines("members.entry.role", p));
        if (entry.role() != Role.COOP) {
            String date = module.config().dateFormatter().format(Instant.ofEpochMilli(island.joinedAt(entry.uuid())));
            lore.addAll(lines("members.entry.joined", Msg.p("date", date)));
            UUID inviter = island.invitedBy(entry.uuid());
            if (inviter != null) {
                lore.addAll(lines("members.entry.invited-by", Msg.p("inviter", islands.name(inviter))));
            }
        } else {
            lore.addAll(lines("members.entry.coop"));
        }
        lore.addAll(lines(online ? "members.entry.online" : "members.entry.offline"));
        boolean actions = MemberActionsMenu.hasActions(viewerRole(), entry.role(), entry.uuid().equals(islands.key(viewer)));
        if (actions) {
            lore.add(Component.empty());
            lore.addAll(lines("members.entry.click"));
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(islands.person(entry.uuid()))));
        ItemStack item = service.item(head, text(entry.role() == Role.LEADER ? "members.entry.name-leader" : "members.entry.name", p), lore);
        if (!actions) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> openChild(new MemberActionsMenu(module, islands, island, entry.uuid())));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
