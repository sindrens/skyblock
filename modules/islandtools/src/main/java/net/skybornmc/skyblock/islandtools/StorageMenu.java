package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The storage of one island tool (hoe or pickaxe; owner 4 Nov 2026): loot that did not fit in the bag. Click an item to
 * take a stack, shift-click to take as much as fits; Sell all sells everything sellable at once.
 * <pre>
 *  . . . . S . . . .      4 the storage (capacity bar)
 *  . x x x x x x x .      the items (21)
 *  . x x x x x x x .
 *  . x x x x x x x .
 *  . . . . $ . . . .      40 sell all
 *  nav bar
 * </pre>
 */
final class StorageMenu extends Menu {

    static final int SLOT_INFO = 4;
    static final int SLOT_SELL = 40;
    static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final IslandToolsModule m;
    private final Kind kind;

    StorageMenu(IslandToolsModule m, Kind kind) {
        this.m = m;
        this.kind = kind;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, IslandToolsModule.ID, "item.name." + kind.id()),
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

    /** 10 dots filled by {@code used / cap} (MiniMessage, Bedrock-safe). */
    static String bar(long used, long cap) {
        int filled = cap <= 0 ? 10 : (int) Math.min(10, used * 10 / cap);
        return "<c:flame>" + "●".repeat(filled) + "</c:flame><c:locked>" + "●".repeat(10 - filled) + "</c:locked>";
    }

    @Override
    protected void build() {
        navBar();
        UUID profile = m.profile(viewer);
        long used = m.storage().used(profile, kind);
        long cap = m.storage().capacity(viewer, kind);
        set(SLOT_INFO, service.item(Material.BARREL, viewer, IslandToolsModule.ID, "storage.head",
                Msg.p("tool", m.raw("item.name." + kind.id())), Msg.p("used", Format.number(used)), Msg.p("cap", Format.number(cap)),
                Msg.mm("bar", bar(used, cap))));
        Map<String, Long> contents = m.storage().contents(profile, kind);
        List<Map.Entry<String, Long>> rows = new ArrayList<>(contents.entrySet());
        rows.sort(Map.Entry.comparingByKey());
        if (rows.isEmpty()) {
            set(22, service.item(kind == Kind.HOE ? Material.WHEAT_SEEDS : kind == Kind.ROD ? Material.COD : Material.COBBLESTONE, viewer, IslandToolsModule.ID,
                    "storage.empty." + kind.id()));
            return;
        }
        for (int i = 0; i < rows.size() && i < ITEM_SLOTS.length; i++) {
            String item = rows.get(i).getKey();
            long n = rows.get(i).getValue();
            Material mat = Material.matchMaterial(item);
            if (mat == null || !mat.isItem()) {
                continue;
            }
            ItemStack icon = new ItemStack(mat, (int) Math.max(1, Math.min(64, n)));
            ItemStack shown = service.item(icon, m.msg().component(viewer, "storage.item-name", Msg.p("item", pretty(item))),
                    service.lines(viewer, IslandToolsModule.ID, "storage.item-lore", Msg.p("amount", Format.number(n))));
            set(ITEM_SLOTS[i], MenuButton.of(shown, click -> m.storage().withdraw(click.player(), kind, item, false, ok -> refreshIfOpen()))
                    .withShift(click -> m.storage().withdraw(click.player(), kind, item, true, ok -> refreshIfOpen())));
        }
        set(SLOT_SELL, service.item(Material.GOLD_INGOT, viewer, IslandToolsModule.ID, "storage.sell"),
                click -> m.storage().sellAll(click.player(), kind, this::sold));
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
