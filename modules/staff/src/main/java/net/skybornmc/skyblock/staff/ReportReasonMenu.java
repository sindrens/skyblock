package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/** {@code /report <player>}: pick a reason; the last reason ("Other") asks for a short text in chat. */
final class ReportReasonMenu extends Menu {

    private static final int[] SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private static final Material[] ICONS = {Material.DIAMOND_SWORD, Material.DIAMOND_ORE, Material.TNT, Material.PAPER,
            Material.OAK_SIGN, Material.REDSTONE, Material.BRICKS, Material.NAME_TAG, Material.WRITABLE_BOOK};

    private final ReportService reports;
    private final UUID target;
    private final String targetName;

    ReportReasonMenu(ReportService reports, UUID target, String targetName) {
        this.reports = reports;
        this.target = target;
        this.targetName = targetName;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, StaffFeature.MODULE, "menu.report.title", Msg.p("player", targetName)));
    }

    @Override
    public String messageModule() {
        return StaffFeature.MODULE;
    }

    @Override
    public String infoKey() {
        return "menu.report.info";
    }

    @Override
    public String helpTopic() {
        return "community";
    }

    @Override
    protected void build() {
        frame();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(target));
            head.setItemMeta(meta);
        }
        set(13, service.item(head, service.text(viewer, StaffFeature.MODULE, "menu.report.target.name", Msg.p("player", targetName)),
                service.lines(viewer, StaffFeature.MODULE, "menu.report.target.lore", Msg.p("player", targetName))));
        List<String> reasons = reports.reasons();
        for (int i = 0; i < reasons.size() && i < SLOTS.length; i++) {
            String reason = reasons.get(i);
            boolean other = i == reasons.size() - 1;
            Material icon = other ? Material.WRITABLE_BOOK : ICONS[Math.min(i, ICONS.length - 2)];
            String key = other ? "menu.report.other" : "menu.report.reason";
            set(SLOTS[i], service.item(icon, viewer, StaffFeature.MODULE, key, Msg.p("reason", reason), Msg.p("player", targetName)),
                    click -> {
                        Player p = click.player();
                        if (other) {
                            service.prompt(p, StaffFeature.MODULE, "report.prompt", reports.detailsMax(),
                                    text -> reports.submit(p, target, targetName, reason, text),
                                    () -> { }, Msg.p("player", targetName));
                        } else {
                            p.closeInventory();
                            reports.submit(p, target, targetName, reason, "");
                        }
                    });
        }
    }
}
