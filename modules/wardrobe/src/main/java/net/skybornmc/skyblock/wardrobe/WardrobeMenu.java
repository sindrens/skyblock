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
 *  46 filter (one click cycles) · 47 worn now · 51 Wardrobe Chest · 52 loadouts (W3)
 * </pre>
 */
final class WardrobeMenu extends PagedMenu<SetDef> {

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
    protected List<SetDef> entries() {
        Session s = session();
        String f = FILTERS.get(filter);
        List<SetDef> out = new ArrayList<>();
        for (SetDef d : module.registry().all()) {
            boolean owned = s != null && s.ownedIds().contains(d.id());
            if (f.equals("all") || (f.equals("owned") && owned) || f.equals(d.group())) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparingInt((SetDef d) -> GROUP_ORDER.indexOf(d.group()))
                .thenComparing(d -> s == null || !s.ownedIds().contains(d.id()))
                .thenComparingInt(SetDef::tier));
        return out;
    }

    @Override
    protected MenuButton entry(SetDef def) {
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

    @Override
    protected void decorate() {
        set(46, service.item(Material.HOPPER, viewer, WardrobeModule.ID, "menu.main.filter",
                Msg.p("filter", module.text().plain("filter." + FILTERS.get(filter)))), click -> {
            filter = (filter + 1) % FILTERS.size();
            page(0);
            service.open(viewer, this);
        });
        List<Component> worn = new ArrayList<>(module.menus().lines(viewer, "menu.main.worn.lore"));
        worn.addAll(module.menus().wornLines(viewer));
        set(47, service.item(Material.ARMOR_STAND, module.menus().text(viewer, "menu.main.worn.name"), worn));
        set(51, service.item(Material.CHEST, viewer, WardrobeModule.ID, "menu.main.chest",
                Msg.p("size", module.chest().capacity(viewer))), click -> module.menus().openChest(viewer, this));
        set(52, service.item(Material.GRAY_DYE, viewer, WardrobeModule.ID, "menu.main.loadouts"));
    }
}
