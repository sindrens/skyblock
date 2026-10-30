package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Combine books (ENCHANTS §4.4, EN11): your books grouped by enchant and level; two equal books make one book a level
 * higher for the combine fee. A book onto an applied enchant is under Upgrade (shown in the top row).
 */
final class CombineMenu extends PagedMenu<CombineMenu.Group> {

    /** All books of one enchant at one level. */
    record Group(String enchant, int level, List<EnchantOps.HeldBook> books) {
    }

    private final EnchantsModule module;

    CombineMenu(EnchantsModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.combine.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected String emptyKey() {
        return "menu.combine.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.mode.combine"));
    }

    @Override
    protected void decorate() {
        set(4, service.item(Material.EXPERIENCE_BOTTLE, viewer, EnchantsModule.ID, "menu.combine.hint"),
                click -> openChild(new ToolPickMenu(module, SlotMenu.Mode.UPGRADE)));
    }

    @Override
    protected List<Group> entries() {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (EnchantOps.HeldBook b : module.ops().books(viewer)) {
            if (b.book().scroll()) {
                continue;
            }
            String key = b.book().enchant() + ":" + b.book().level();
            groups.computeIfAbsent(key, k -> new Group(b.book().enchant(), b.book().level(), new ArrayList<>())).books().add(b);
        }
        List<Group> list = new ArrayList<>(groups.values());
        list.sort((a, b) -> Boolean.compare(b.books().size() >= 2, a.books().size() >= 2));
        return list;
    }

    @Override
    protected MenuButton entry(Group group) {
        EnchantOps.HeldBook first = group.books().getFirst();
        Optional<EnchantDef> def = module.registry().get(group.enchant());
        List<Component> extra = new ArrayList<>(module.menus().lines(viewer, "menu.combine.count", Msg.p("count", group.books().size())));
        if (def.isEmpty()) {
            extra.add(module.menus().text(viewer, "why.unknown"));
            return MenuButton.display(EnchantMenus.withExtraLore(BookItems.displayCopy(first.stack()), extra));
        }
        EnchantRules.Verdict verdict = EnchantRules.canCombineBooks(def.get(), group.enchant(), group.level(), group.enchant(), group.level());
        if (!verdict.ok() || group.books().size() < 2) {
            extra.add(verdict.ok() ? module.menus().text(viewer, "why.needs-pair", Msg.mm("book", module.menus().coloredName(def.get(), group.level())))
                    : module.menus().why(viewer, verdict, def.get(), null));
            return MenuButton.of(EnchantMenus.withExtraLore(BookItems.displayCopy(first.stack()), extra), click -> service.sound(viewer, "error"));
        }
        int next = group.level() + 1;
        long fee = module.costs().combineFee(def.get(), next);
        String from = module.menus().coloredName(def.get(), group.level());
        String to = module.menus().coloredName(def.get(), next);
        extra.addAll(module.menus().lines(viewer, "menu.combine.ready", Msg.mm("next", to), EnchantMenus.cost(fee)));
        var a = group.books().get(0).book().id();
        var b = group.books().get(1).book().id();
        return MenuButton.of(EnchantMenus.withExtraLore(BookItems.displayCopy(first.stack()), extra), click -> openChild(new ConfirmMenu(
                v -> module.menus().text(v, "menu.confirm.combine.crumb"), BookItems.displayCopy(first.stack()),
                v -> module.menus().lines(v, "menu.confirm.combine.details", Msg.mm("book", from), Msg.mm("next", to), EnchantMenus.cost(fee)),
                p -> module.ops().combineBooks(p, a, b), p -> { })));
    }
}
