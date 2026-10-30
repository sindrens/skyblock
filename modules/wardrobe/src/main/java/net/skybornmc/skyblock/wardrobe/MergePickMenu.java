package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Merge, step 1 (WARDROBE §5.3): pick the second set. Every other owned set is listed; only valid partners are
 * clickable, the others say why in red ({@code ✘ Needs Lv 50}, {@code ✘ Both boost crystals} …). Click-only.
 */
final class MergePickMenu extends PagedMenu<WardrobeStore.Entry> {

    private final WardrobeModule module;
    private final WardrobeStore.Entry first;

    MergePickMenu(WardrobeModule module, WardrobeStore.Entry first) {
        this.module = module;
        this.first = first;
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.merge.info";
    }

    @Override
    public String helpTopic() {
        return "wardrobe";
    }

    @Override
    protected String emptyKey() {
        return "menu.merge.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.merge.title"));
    }

    @Override
    protected List<WardrobeStore.Entry> entries() {
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        if (s == null) {
            return List.of();
        }
        List<WardrobeStore.Entry> out = new ArrayList<>();
        for (WardrobeStore.Entry e : s.entries) {
            if (!e.entryId().equals(first.entryId())) {
                out.add(e);
            }
        }
        // Valid partners first, then by level (highest first).
        out.sort(Comparator.comparing((WardrobeStore.Entry e) -> module.merging().check(first, e).isPresent())
                .thenComparing(Comparator.comparingInt(WardrobeStore.Entry::level).reversed()));
        return out;
    }

    @Override
    protected MenuButton entry(WardrobeStore.Entry e) {
        SetDef look = module.registry().get(e.look()).or(() -> module.registry().get(e.setId())).orElse(null);
        Component name = module.core.messages().parse(viewer, module.text().entryColored(module.registry(), e));
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.merge.entry", Msg.p("level", e.level()),
                Msg.p("multiplier", WardrobeText.number(module.bonusMultiplier(e.level())))));
        Optional<MergeRules.Refusal> why = module.merging().check(first, e);
        var icon = look == null ? new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_CHESTPLATE) : module.items().icon(look, ArmorPiece.CHEST);
        if (why.isPresent()) {
            lore.addAll(module.menus().lines(viewer, "menu.merge.refused", Msg.mm("why", module.text().raw("merge.why." + why.get().key()))));
            return MenuButton.of(service.item(icon, name, lore), null);
        }
        lore.addAll(module.menus().lines(viewer, "menu.merge.fee", Msg.p("fee", module.merging().feeText(module.merging().fee(first, e)))));
        lore.addAll(module.menus().lines(viewer, "menu.merge.click"));
        return MenuButton.of(service.item(icon, name, lore), click -> openChild(new MergeLookMenu(module, first, e)));
    }
}
