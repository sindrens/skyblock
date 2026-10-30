package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /is invites} (ISL2, owner O17): the invites you received (click: accept or deny) and the invites your island
 * sent (click: cancel; leader, admin and mod). Received first, newest first; refreshes every 2 s so expired invites
 * disappear. One plain click per action (Bedrock-safe).
 */
final class InvitesMenu extends PagedMenu<InvitesMenu.Entry> {

    /** One listed invite: {@code sent} = sent by the viewer's island. */
    record Entry(Invites.Invite invite, boolean sent) {
    }

    private final IslandsModule module;
    private final IslandManager islands;
    private final MemberManager members;

    InvitesMenu(IslandsModule module, IslandManager islands, MemberManager members) {
        this.module = module;
        this.islands = islands;
        this.members = members;
    }

    /** Received invites (newest first), then the ones the island sent (newest first). Pure, for tests. */
    static List<Entry> order(List<Invites.Invite> received, List<Invites.Invite> sent) {
        List<Entry> list = new ArrayList<>();
        received.stream().sorted(Comparator.comparingLong(Invites.Invite::createdAt).reversed())
                .forEach(invite -> list.add(new Entry(invite, false)));
        sent.stream().sorted(Comparator.comparingLong(Invites.Invite::createdAt).reversed())
                .forEach(invite -> list.add(new Entry(invite, true)));
        return list;
    }

    private Optional<Island> own() {
        return islands.islandOfPlayer(islands.key(viewer));
    }

    @Override
    protected List<Entry> entries() {
        long now = System.currentTimeMillis();
        List<Invites.Invite> sent = own().map(island -> members.invites().from(island.id(), now)).orElse(List.of());
        return order(members.invites().of(islands.key(viewer), now), sent);
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("menu.crumb"), text("invites.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "invites.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected String emptyKey() {
        return "invites.empty";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected MenuButton entry(Entry entry) {
        Invites.Invite invite = entry.invite();
        long left = Math.max(0, invite.expiresAt() - System.currentTimeMillis());
        String time = Format.duration(Duration.ofMillis(left));
        Island island = islands.byId(invite.islandId());
        String islandName = island == null ? "?" : islands.displayName(island);
        UUID shown = entry.sent() ? invite.target() : invite.inviter();
        TagResolver[] p = {Msg.p("player", islands.name(shown)), Msg.p("island", islandName), Msg.p("time", time),
                Msg.p("inviter", islands.name(invite.inviter())),
                Msg.p("members", island == null ? 0 : island.memberCount()), Msg.p("slots", island == null ? 0 : islands.slots(island))};
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(islands.person(shown))));
        if (!entry.sent()) {
            ItemStack item = service.item(head, text("invites.received.name", p), lines("invites.received.lore", p));
            return MenuButton.of(item, click -> openChild(new InviteActionsMenu(module, islands, members, invite)));
        }
        boolean canCancel = own().map(own -> MemberRules.canInvite(own.roleOrNull(islands.key(viewer)))).orElse(false);
        List<Component> lore = new ArrayList<>(lines("invites.sent.lore", p));
        lore.addAll(lines(canCancel ? "invites.sent.click" : "invites.sent.locked"));
        ItemStack item = service.item(head, text("invites.sent.name", p), lore);
        if (!canCancel) {
            return MenuButton.display(item);
        }
        String target = islands.name(invite.target());
        return MenuButton.of(item, click -> openChild(new ConfirmMenu(
                v -> text("invites.cancel.crumb"),
                service.item(Material.BARRIER, viewer, IslandsModule.ID, "invites.cancel.shown", Msg.p("player", target)),
                v -> lines("invites.cancel.details", Msg.p("player", target)),
                v -> {
                    members.cancelInvite(v, invite);
                    v.closeInventory();
                },
                null)));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
