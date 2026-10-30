package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Admins only (owner answer 251): the kick backups of one player, newest first ({@code /isadmin restore <player>}).
 * Each entry shows the island, role, money and whether items were captured; a click on an open one asks to confirm and
 * restores it ({@link KickBackups}). Chest menu only (Bedrock-safe).
 */
final class KickBackupsMenu extends PagedMenu<KickBackupStore.Backup> {

    private final IslandsModule module;
    private final KickBackups backups;
    private final String playerName;
    private final List<KickBackupStore.Backup> list;

    KickBackupsMenu(IslandsModule module, KickBackups backups, String playerName, List<KickBackupStore.Backup> list) {
        this.module = module;
        this.backups = backups;
        this.playerName = playerName;
        this.list = List.copyOf(list);
    }

    @Override
    protected List<KickBackupStore.Backup> entries() {
        return list;
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("admin.crumb"), text("admin.restore.menu-crumb", Msg.p("player", playerName)));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "admin.restore.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected String emptyKey() {
        return "admin.restore.empty";
    }

    @Override
    protected MenuButton entry(KickBackupStore.Backup b) {
        var formatter = module.config().dateFormatter();
        String state = b.open() ? "open" : b.itemsOpen() ? "items" : "restored";
        TagResolver[] p = {Msg.p("island", b.islandName()), Msg.p("role", module.raw("role." + MemberStore.key(b.role()))),
                Msg.p("money", b.money() == null ? "-" : Format.number(b.money())),
                Msg.p("items", module.raw(b.itemsCaptured() ? "admin.restore.items-yes" : "admin.restore.items-no")),
                Msg.p("by", b.kickedBy()), Msg.p("date", formatter.format(Instant.ofEpochMilli(b.createdAt()))),
                Msg.p("state", module.raw("admin.restore.state." + state)), Msg.p("id", b.id())};
        List<Component> lore = new ArrayList<>(lines("admin.restore.entry.lore", p));
        if (!"restored".equals(state)) {
            lore.addAll(lines("admin.restore.entry.click"));
        }
        Material icon = switch (state) {
            case "open" -> Material.TOTEM_OF_UNDYING;
            case "items" -> Material.CHEST;
            default -> Material.GRAY_DYE;
        };
        ItemStack item = service.item(new ItemStack(icon), text("admin.restore.entry.name", p), lore);
        if ("restored".equals(state)) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> backups.confirmRestore(click.player(), b));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
