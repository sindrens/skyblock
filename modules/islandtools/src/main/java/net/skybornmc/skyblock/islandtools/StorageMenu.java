package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** T9: the hoe storage. Click an item to take a stack (or all of it); Sell all sells everything sellable at once. */
final class StorageMenu extends Menu {

    static final int SLOT_INFO = 4;
    static final int SLOT_SELL = 40;
    static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final IslandToolsModule m;

    StorageMenu(IslandToolsModule m) {
        this.m = m;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, IslandToolsModule.ID, "menu.crumb"),
                service.text(viewer, IslandToolsModule.ID, "storage.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "storage.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    @Override
    protected void build() {
        navBar();
        UUID profile = m.profile(viewer);
        set(SLOT_INFO, service.item(Material.BARREL, viewer, IslandToolsModule.ID, "storage.head",
                Msg.p("used", Format.number(m.storage().used(profile))), Msg.p("cap", Format.number(m.storage().capacity(viewer)))));
        Map<String, Long> contents = m.storage().contents(profile);
        List<Map.Entry<String, Long>> rows = new ArrayList<>(contents.entrySet().stream().filter(e -> e.getValue() > 0).toList());
        rows.sort(Map.Entry.comparingByKey());
        if (rows.isEmpty()) {
            set(22, service.item(Material.WHEAT_SEEDS, viewer, IslandToolsModule.ID, "storage.empty"));
            return;
        }
        for (int i = 0; i < rows.size() && i < ITEM_SLOTS.length; i++) {
            String item = rows.get(i).getKey();
            long n = rows.get(i).getValue();
            Material mat = Material.matchMaterial(item);
            if (mat == null) {
                continue;
            }
            ItemStack icon = new ItemStack(mat, (int) Math.max(1, Math.min(64, n)));
            set(ITEM_SLOTS[i], service.item(icon, m.msg().component(viewer, "storage.item-name", Msg.p("item", pretty(item))),
                    service.lines(viewer, IslandToolsModule.ID, "storage.item-lore", Msg.p("amount", Format.number(n)))),
                    click -> m.storage().withdraw(click.player(), item, mat.getMaxStackSize(), ok -> refreshIfOpen()));
        }
        set(SLOT_SELL, service.item(Material.GOLD_INGOT, viewer, IslandToolsModule.ID, "storage.sell"),
                click -> m.storage().sellAll(click.player(), this::sold));
    }

    private void sold(SellResult result) {
        if (result == null) {
            m.msg().send(viewer, "storage.nothing");
        } else if (result.status() == SellResult.Status.SOLD) {
            m.core().sounds().play(viewer, "money");
        } else {
            m.msg().send(viewer, "storage.sell-failed");
        }
        refreshIfOpen();
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            if (!w.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }
}
