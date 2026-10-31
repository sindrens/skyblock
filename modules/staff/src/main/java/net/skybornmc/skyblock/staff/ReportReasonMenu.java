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
    /** Icons of the default reasons by name (lower case); a reason the owner added gets {@link #FALLBACK}. */
    private static final java.util.Map<String, Material> ICONS = java.util.Map.ofEntries(
            java.util.Map.entry("hacking", Material.DIAMOND_SWORD), java.util.Map.entry("x-ray", Material.DIAMOND_ORE),
            java.util.Map.entry("toxicity", Material.TNT), java.util.Map.entry("spam", Material.PAPER),
            java.util.Map.entry("advertising", Material.OAK_SIGN), java.util.Map.entry("scamming", Material.GOLD_INGOT),
            java.util.Map.entry("threats", Material.SKELETON_SKULL), java.util.Map.entry("griefing", Material.FLINT_AND_STEEL),
            java.util.Map.entry("exploiting", Material.REDSTONE), java.util.Map.entry("inappropriate build", Material.BRICKS),
            java.util.Map.entry("inappropriate name", Material.NAME_TAG));
    private static final Material FALLBACK = Material.PAPER;

    /** The icon of a reason. Package-private for tests. */
    static Material icon(String reason) {
        return reason == null ? FALLBACK : ICONS.getOrDefault(reason.strip().toLowerCase(java.util.Locale.ROOT), FALLBACK);
    }

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
            Material icon = other ? Material.WRITABLE_BOOK : icon(reason);
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
