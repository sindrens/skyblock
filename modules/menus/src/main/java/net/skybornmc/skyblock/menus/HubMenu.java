package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /menu}: the hub. Every feature has a fixed place (layout in {@code menu.yml}); features that aren't built yet
 * keep their icon and slot, shown in the locked "Coming soon" look, so the layout never changes as features arrive.
 */
final class HubMenu extends net.skybornmc.skyblock.common.menu.Menu {

    static final int SLOT_PROFILE = 4;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final Nav nav;

    HubMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "hub.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "hub.info";
    }

    @Override
    public String helpTopic() {
        return "menu";
    }

    /** The bottom of every navigation stack: no Back here, and slot 50 is Help & guides. */
    @Override
    public boolean isMainMenu() {
        return true;
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_PROFILE, profile(), click -> openChild(new SettingsMenu(nav)));
        for (HubLayout.Entry entry : nav.hub.entries()) {
            Component name = nav.text(viewer, "hub.entries." + entry.id() + ".name");
            List<Component> description = nav.lines(viewer, "hub.entries." + entry.id() + ".lore");
            if (!entry.active()) {
                set(entry.slot(), nav.comingSoon(viewer, entry.icon(), name, description));
                continue;
            }
            if (!entry.allowed(viewer)) {
                List<Component> lore = new ArrayList<>(description);
                lore.addAll(nav.lines(viewer, "hub.locked"));
                set(entry.slot(), nav.item(entry.icon(), name, lore));
                continue;
            }
            List<Component> lore = new ArrayList<>(description);
            lore.addAll(nav.lines(viewer, "hub.action"));
            set(entry.slot(), nav.item(entry.icon(), name, lore), click -> open(click.player(), entry));
        }
    }

    private void open(Player player, HubLayout.Entry entry) {
        // Built-in screens open as children; everything else runs its command while the hub is still open, so the
        // screen it opens gets Back to the hub (GUI-1). If the command opens nothing, the hub closes (chat output).
        switch (entry.command().strip().toLowerCase(Locale.ROOT)) {
            case "settings" -> openChild(new SettingsMenu(nav));
            case "help" -> openChild(new HelpMenu(nav));
            case HubWarpsMigration.WARPS -> openChild(new WarpsMenu(nav));
            default -> service.navigate(player, this, () -> player.performCommand(entry.command().strip()));
        }
    }

    private ItemStack profile() {
        long firstSeen = nav.core.players().get(viewer.getUniqueId()).map(d -> d.firstSeen()).orElse(System.currentTimeMillis());
        long playtime = nav.core.players().get(viewer.getUniqueId()).map(d -> d.playtimeMillis()).orElse(0L);
        ItemStack head = nav.item(Material.PLAYER_HEAD,
                nav.text(viewer, "hub.profile.name"),
                nav.lines(viewer, "hub.profile.lore",
                        Msg.p("playtime", Format.duration(java.time.Duration.ofMillis(playtime))),
                        Msg.p("joined", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(firstSeen)))));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        return head;
    }
}
