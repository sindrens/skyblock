package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enchant guide (ENCHANTS §4.6): every enchant by tool tab (tabs in the top row): name, rarity, max level, the number per
 * level, its effect, slot cost, conflicts, where it drops and whether you found it this season. Mythics show as
 * {@code ???} until you have seen one.
 */
final class GuideMenu extends PagedMenu<EnchantDef> {

    /** Tabs: one per tool type plus armor. */
    enum Tab {
        PICKAXE(Material.DIAMOND_PICKAXE, EnumSet.of(EnchantTarget.PICKAXE)),
        AXE(Material.DIAMOND_AXE, EnumSet.of(EnchantTarget.AXE)),
        HOE(Material.DIAMOND_HOE, EnumSet.of(EnchantTarget.HOE)),
        SWORD(Material.DIAMOND_SWORD, EnumSet.of(EnchantTarget.SWORD)),
        ROD(Material.FISHING_ROD, EnumSet.of(EnchantTarget.ROD)),
        ARMOR(Material.DIAMOND_CHESTPLATE, EnumSet.of(EnchantTarget.HELMET, EnchantTarget.CHESTPLATE, EnchantTarget.LEGGINGS, EnchantTarget.BOOTS));

        final Material icon;
        final Set<EnchantTarget> targets;

        Tab(Material icon, Set<EnchantTarget> targets) {
            this.icon = icon;
            this.targets = targets;
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final int FIRST_TAB_SLOT = 2;

    private final EnchantsModule module;
    private final Set<String> seen;
    private Tab tab = Tab.PICKAXE;

    GuideMenu(EnchantsModule module, Set<String> seen) {
        this.module = module;
        this.seen = Set.copyOf(seen);
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.guide.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected String emptyKey() {
        return "menu.guide.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.guide.crumb"), module.menus().text(viewer, "menu.guide.tab." + tab.key()));
    }

    @Override
    protected void decorate() {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            ItemStack icon = service.item(t.icon, viewer, EnchantsModule.ID, t == tab ? "menu.guide.tab-selected" : "menu.guide.tab-other",
                    Msg.mm("tab", module.text().raw("menu.guide.tab." + t.key())));
            if (t == tab) {
                icon.setData(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            set(FIRST_TAB_SLOT + i, icon, click -> {
                if (tab != t) {
                    tab = t;
                    page(0);
                    service.open(viewer, this);
                }
            });
        }
    }

    @Override
    protected List<EnchantDef> entries() {
        List<EnchantDef> out = new ArrayList<>();
        for (EnchantDef d : module.registry().all()) {
            if (d.targets().stream().anyMatch(tab.targets::contains)) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    protected MenuButton entry(EnchantDef def) {
        if (def.mythic() && !seen.contains(def.id())) {
            return MenuButton.display(service.item(Material.GRAY_DYE, viewer, EnchantsModule.ID, "menu.guide.secret"));
        }
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.guide.head",
                Msg.mm("rarity", "<rarity:" + def.rarity().id() + ">" + module.text().plain("rarity." + def.rarity().id())),
                Msg.p("max", EnchantText.roman(def.maxLevel()))));
        List<Double> values = def.mainValues();
        if (def.maxLevel() > 1 && !values.isEmpty()) {
            lore.add(module.menus().text(viewer, "menu.guide.per-level",
                    Msg.p("values", values.stream().map(EnchantText::number).collect(Collectors.joining(" / ")))));
        }
        lore.addAll(module.menus().wrapped(viewer, module.text().effect(def, def.maxLevel())));
        lore.add(def.mythic() ? module.menus().text(viewer, "menu.guide.prestige-slot")
                : module.menus().text(viewer, "menu.guide.slot-cost", Msg.p("slots", def.slotCost())));
        List<String> conflicts = new ArrayList<>();
        for (EnchantDef other : module.registry().all()) {
            if (def.conflictsWith(other) && !(def.mythic() && other.mythic())) {
                conflicts.add(module.text().name(other.id()));
            }
        }
        if (!conflicts.isEmpty()) {
            lore.add(module.menus().text(viewer, "menu.guide.conflicts", Msg.p("names", String.join(", ", conflicts))));
        }
        if (def.mythic()) {
            lore.add(module.menus().text(viewer, "menu.guide.drops-special"));
        } else {
            String pools = def.pools().stream().sorted().map(p -> module.text().plain("pool." + p.id())).collect(Collectors.joining(", "));
            lore.add(module.menus().text(viewer, "menu.guide.drops", Msg.p("pools", pools), Msg.p("tier", def.minTier())));
        }
        if (def.armorOnly()) {
            lore.add(module.menus().text(viewer, "menu.guide.armor"));
        }
        if (!def.enabled()) {
            lore.add(module.menus().text(viewer, "menu.guide.disabled"));
        }
        lore.add(module.menus().text(viewer, seen.contains(def.id()) ? "menu.guide.found" : "menu.guide.not-found"));
        Component name = module.menus().text(viewer, "menu.guide.name", Msg.mm("enchant", module.menus().coloredName(def, 0)));
        return MenuButton.display(service.item(Material.ENCHANTED_BOOK, name, lore));
    }
}
