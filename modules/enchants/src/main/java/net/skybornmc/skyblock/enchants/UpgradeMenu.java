package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Upgrade one applied enchant (ENCHANTS §4.3, §4.4): with crystals, or with a book of the same enchant and level for the
 * combine fee. Blocked at max level or at the tool-level gate (the gate is shown).
 * <pre>
 *  row 2:            [enchant now]
 *  row 4:  [with crystals]        [with a book]
 * </pre>
 */
final class UpgradeMenu extends Menu {

    static final int SLOT_INFO = 13;
    static final int SLOT_CRYSTALS = 29;
    static final int SLOT_BOOK = 33;

    private final EnchantsModule module;
    private final UUID toolId;
    private final String enchant;

    UpgradeMenu(EnchantsModule module, UUID toolId, String enchant) {
        this.module = module;
        this.toolId = toolId;
        this.enchant = enchant;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.mode.upgrade"), Component.text(module.text().name(enchant)));
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.upgrade.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected void build() {
        frame();
        Optional<EnchantOps.HeldTool> held = module.ops().tool(viewer, toolId);
        Optional<EnchantDef> found = module.registry().get(enchant);
        Optional<ToolPerk> perk = held.flatMap(h -> h.info().perk(enchant));
        if (held.isEmpty() || found.isEmpty() || perk.isEmpty()) {
            set(SLOT_INFO, service.item(Material.BARRIER, viewer, EnchantsModule.ID, "menu.tool-gone"));
            return;
        }
        ToolInfo tool = held.get().info();
        EnchantDef def = found.get();
        int level = perk.get().level();
        List<Component> info = new ArrayList<>(module.menus().lines(viewer, "menu.upgrade.now",
                Msg.p("max", EnchantText.roman(def.maxLevel())), Msg.p("gate", EnchantText.roman(Math.max(1, module.rules().gate(tool, def))))));
        info.addAll(module.menus().wrapped(viewer, module.text().effect(def, level)));
        set(SLOT_INFO, service.item(Material.ENCHANTED_BOOK, module.menus().text(viewer, "menu.upgrade.name",
                Msg.mm("enchant", module.menus().coloredName(def, level))), info));

        EnchantRules.Verdict verdict = module.rules().canUpgrade(tool, perk.get(), def);
        String next = module.menus().coloredName(def, Math.min(def.maxLevel(), level + 1));
        if (!verdict.ok()) {
            List<Component> lore = new ArrayList<>(List.of(module.menus().why(viewer, verdict, def, tool)));
            set(SLOT_CRYSTALS, service.item(Material.GLASS_BOTTLE, module.menus().text(viewer, "menu.upgrade.crystals.name"), lore),
                    click -> service.sound(viewer, "error"));
            set(SLOT_BOOK, service.item(Material.BOOK, module.menus().text(viewer, "menu.upgrade.book.name"), lore),
                    click -> service.sound(viewer, "error"));
            return;
        }
        long cost = module.costs().upgradeCost(def, level);
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.upgrade.crystals.lore", Msg.mm("next", next), EnchantMenus.cost(cost)));
        lore.addAll(module.menus().wrapped(viewer, module.text().effect(def, level + 1)));
        lore.addAll(module.menus().lines(viewer, "menu.click"));
        set(SLOT_CRYSTALS, service.item(Material.EXPERIENCE_BOTTLE, module.menus().text(viewer, "menu.upgrade.crystals.name"), lore),
                click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.upgrade.crumb"), null,
                        v -> module.menus().lines(v, "menu.confirm.upgrade.details", Msg.mm("next", next), EnchantMenus.cost(cost)),
                        p -> module.ops().upgrade(p, toolId, enchant), p -> { })));

        Optional<EnchantOps.HeldBook> match = module.ops().books(viewer).stream()
                .filter(b -> !b.book().scroll() && b.book().enchant().equals(enchant) && b.book().level() == level).findFirst();
        String bookName = module.menus().coloredName(def, level);
        if (match.isEmpty()) {
            set(SLOT_BOOK, service.item(Material.BOOK, module.menus().text(viewer, "menu.upgrade.book.name"),
                    module.menus().lines(viewer, "menu.upgrade.book.none", Msg.mm("book", bookName))), click -> service.sound(viewer, "error"));
            return;
        }
        long fee = module.costs().combineFee(def, level + 1);
        UUID bookId = match.get().book().id();
        set(SLOT_BOOK, service.item(Material.ENCHANTED_BOOK, module.menus().text(viewer, "menu.upgrade.book.name"),
                module.menus().lines(viewer, "menu.upgrade.book.lore", Msg.mm("book", bookName), Msg.mm("next", next), EnchantMenus.cost(fee))),
                click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.upgrade.crumb"), BookItems.displayCopy(match.get().stack()),
                        v -> module.menus().lines(v, "menu.confirm.combine-tool.details", Msg.mm("book", bookName), Msg.mm("next", next),
                                EnchantMenus.cost(fee)),
                        p -> module.ops().combineOnTool(p, toolId, enchant, bookId), p -> { })));
    }
}
