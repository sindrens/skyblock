package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /wardrobe} (WARDROBE §6.1): every set, owned or not, grouped by activity (owned first in each group).
 * <pre>
 *  content: the 16 sets (lock, price or "Lv · ×" and pieces worn)
 *  46 filter (opens a pick list, GUI-4/G9) · 47 worn now · 51 Wardrobe Chest · top row 4 loadouts (/loadout)
 * </pre>
 */
final class WardrobeMenu extends PagedMenu<WardrobeMenu.Row> {

    /** A list entry: a set, or a merged entry (shown right after its first set). */
    record Row(SetDef def, WardrobeStore.Entry merged) {
    }


    static final List<String> FILTERS = List.of("all", "owned", "mining", "foraging", "farming", "combat", "fishing", "conquest", "other");
    static final List<String> GROUP_ORDER = List.of("mining", "foraging", "farming", "combat", "fishing", "conquest", "other");

    private final WardrobeModule module;
    private int filter;

    WardrobeMenu(WardrobeModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.main.info";
    }

    @Override
    public String helpTopic() {
        return "wardrobe";
    }

    @Override
    protected String emptyKey() {
        return "menu.main.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.title"));
    }

    private Session session() {
        return module.equipment().sessionOrNull(viewer.getUniqueId());
    }

    @Override
    protected List<Row> entries() {
        Session s = session();
        String f = FILTERS.get(filter);
        List<SetDef> sets = new ArrayList<>();
        for (SetDef d : module.registry().all()) {
            boolean owned = s != null && s.ownedIds().contains(d.id());
            if (f.equals("all") || (f.equals("owned") && owned) || f.equals(d.group())) {
                sets.add(d);
            }
        }
        sets.sort(Comparator.comparingInt((SetDef d) -> GROUP_ORDER.indexOf(d.group()))
                .thenComparing(d -> s == null || !s.ownedIds().contains(d.id()))
                .thenComparingInt(SetDef::tier));
        List<Row> out = new ArrayList<>();
        for (SetDef d : sets) {
            out.add(new Row(d, null));
            if (s != null) {
                for (WardrobeStore.Entry m : s.mergedEntries()) {
                    if (m.setId().equals(d.id())) {
                        out.add(new Row(d, m));
                    }
                }
            }
        }
        return out;
    }

    @Override
    protected MenuButton entry(Row row) {
        if (row.merged() != null) {
            return merged(row);
        }
        SetDef def = row.def();
        Session s = session();
        Offers.Offer offer = module.shop().offer(viewer, s, def);
        List<Component> lore = new ArrayList<>();
        lore.add(module.menus().text(viewer, "menu.set.subtitle", Msg.p("activity", module.text().plain("activity-name." + def.activity())),
                Msg.p("tier", WardrobeText.roman(def.tier()))));
        lore.add(Component.empty());
        lore.addAll(module.menus().status(viewer, def, offer, s));
        lore.add(Component.empty());
        lore.addAll(module.menus().lines(viewer, "menu.main.click"));
        return MenuButton.of(service.item(module.items().icon(def, ArmorPiece.CHEST),
                module.core.messages().parse(viewer, WardrobeText.colored(def)), lore),
                click -> openChild(new SetMenu(module, def)));
    }

    private MenuButton merged(Row row) {
        WardrobeStore.Entry e = row.merged();
        SetDef look = module.registry().get(e.look()).orElse(row.def());
        List<Component> lore = new ArrayList<>();
        lore.add(module.menus().text(viewer, "menu.set.subtitle", Msg.p("activity", module.text().plain("activity-name." + row.def().activity())),
                Msg.p("tier", WardrobeText.roman(row.def().tier()))));
        lore.add(Component.empty());
        lore.addAll(module.menus().mergedStatus(viewer, e, session()));
        lore.add(Component.empty());
        lore.addAll(module.menus().lines(viewer, "menu.main.click"));
        return MenuButton.of(service.item(module.items().icon(look, ArmorPiece.CHEST),
                module.core.messages().parse(viewer, module.text().entryColored(module.registry(), e)), lore),
                click -> openChild(new SetMenu(module, row.def(), e.entryId())));
    }

    /** A small list of the filters; one click picks (GUI-4/G9: no forward-only cycling). */
    private void pickFilter() {
        List<net.skybornmc.skyblock.common.menu.PickListMenu.Option<Integer>> options = new ArrayList<>();
        for (int i = 0; i < FILTERS.size(); i++) {
            options.add(new net.skybornmc.skyblock.common.menu.PickListMenu.Option<>(i, new org.bukkit.inventory.ItemStack(Material.HOPPER),
                    service.text(viewer, WardrobeModule.ID, "menu.main.filter.pick",
                            Msg.p("filter", module.text().plain("filter." + FILTERS.get(i)))), List.of(), i == filter));
        }
        openChild(new net.skybornmc.skyblock.common.menu.PickListMenu<>(List.of(module.menus().text(viewer, "menu.title"),
                service.text(viewer, WardrobeModule.ID, "menu.main.filter.crumb")), options, i -> {
            filter = i;
            page(0);
        }));
    }

    @Override
    protected void decorate() {
        set(46, service.item(Material.HOPPER, viewer, WardrobeModule.ID, "menu.main.filter",
                Msg.p("filter", module.text().plain("filter." + FILTERS.get(filter)))), click -> pickFilter());
        List<Component> worn = new ArrayList<>(module.menus().lines(viewer, "menu.main.worn.lore"));
        worn.addAll(module.menus().wornLines(viewer));
        set(47, service.item(Material.ARMOR_STAND, module.menus().text(viewer, "menu.main.worn.name"), worn));
        set(51, service.item(Material.CHEST, viewer, WardrobeModule.ID, "menu.main.chest",
                Msg.p("size", module.chest().capacity(viewer))), click -> module.menus().openChest(viewer, this));
        if (module.config().loadoutEnabled) {
            Session s = session();
            // GUI-1: 52 is Previous page now (Back stays on 45), so Loadouts moved to the top row.
            set(4, service.item(Material.ARMOR_STAND, viewer, WardrobeModule.ID, "menu.main.loadouts",
                    Msg.p("count", s == null ? 0 : s.loadouts.size()), Msg.p("slots", module.loadouts().slots(viewer, s))),
                    click -> module.loadouts().open(viewer, this));
        }
    }
}
