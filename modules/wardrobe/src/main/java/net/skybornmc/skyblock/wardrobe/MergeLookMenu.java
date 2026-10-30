package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;

import java.util.Optional;

/**
 * Merge, step 2 (WARDROBE §5.3): pick which set's look the merged pieces use, then the 3-row confirm that lists exactly
 * what happens ("combine A Lv 50 and B Lv 50 into one set at Lv 0 · cost ◆ 25,000 · can't be undone").
 * <pre>
 *  11 look of the first set     15 look of the second set
 * </pre>
 */
final class MergeLookMenu extends Menu {

    private final WardrobeModule module;
    private final WardrobeStore.Entry first;
    private final WardrobeStore.Entry second;

    MergeLookMenu(WardrobeModule module, WardrobeStore.Entry first, WardrobeStore.Entry second) {
        this.module = module;
        this.first = first;
        this.second = second;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.merge.look-title"));
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    protected void build() {
        frame();
        button(11, first);
        button(15, second);
        set(13, service.item(Material.ANVIL, viewer, WardrobeModule.ID, "menu.merge.look-info"));
    }

    private void button(int slot, WardrobeStore.Entry lookOf) {
        Optional<SetDef> def = module.registry().get(lookOf.setId());
        if (def.isEmpty()) {
            return;
        }
        WardrobeMenus m = module.menus();
        set(slot, service.item(module.items().icon(def.get(), ArmorPiece.CHEST), module.core.messages().parse(viewer, WardrobeText.colored(def.get())),
                m.lines(viewer, "menu.merge.look-pick", Msg.p("set", def.get().shortName()))), click -> {
            String a = module.text().entryName(module.registry(), first);
            String b = module.text().entryName(module.registry(), second);
            String fee = module.merging().feeText(module.merging().fee(first, second));
            Session session = module.equipment().sessionOrNull(viewer.getUniqueId());
            WardrobeStore.Entry other = def.get().id().equals(first.setId()) ? second : first;
            int books = session == null ? 0 : session.enchants(other.entryId()).values().stream().mapToInt(java.util.List::size).sum();
            openChild(new ConfirmMenu(v -> m.text(v, "confirm.merge.crumb"), module.items().icon(def.get(), ArmorPiece.CHEST),
                    v -> {
                        java.util.List<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>(m.lines(v, "confirm.merge.details",
                                Msg.p("a", a), Msg.p("b", b), Msg.p("level_a", first.level()), Msg.p("level_b", second.level()),
                                Msg.p("fee", fee), Msg.p("look", def.get().shortName())));
                        if (books > 0) {
                            lines.addAll(m.lines(v, "confirm.merge.enchants", Msg.p("books", books)));
                        }
                        return lines;
                    },
                    p -> module.merging().merge(p, first.entryId(), second.entryId(), def.get().id()), p -> {
            }));
        });
    }

}
