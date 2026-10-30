package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;
import net.skybornmc.skyblock.common.wardrobe.SetEntryView;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One armor enchant on a set piece (E5): upgrade with crystals or with a book of the same enchant and level (ENCHANTS
 * §4.3, §4.4), remove with a Disenchant Scroll (comes off as a book) or shatter it for a crystal share (§4.5).
 * <pre>
 *  row 2:                   [enchant now]
 *  row 4:  [crystals]  [book]          [scroll]  [shatter]
 * </pre>
 */
final class ArmorEnchantMenu extends Menu {

    static final int SLOT_INFO = 13;
    static final int SLOT_CRYSTALS = 28;
    static final int SLOT_BOOK = 29;
    static final int SLOT_SCROLL = 33;
    static final int SLOT_SHATTER = 34;

    private final EnchantsModule module;
    private final UUID entryId;
    private final ArmorPiece piece;
    private final String enchant;

    ArmorEnchantMenu(EnchantsModule module, UUID entryId, ArmorPiece piece, String enchant) {
        this.module = module;
        this.entryId = entryId;
        this.piece = piece;
        this.enchant = enchant;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.mode.armor"), Component.text(module.text().name(enchant)));
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.armor-enchant.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected void build() {
        frame();
        WardrobeService w = module.wardrobe();
        Optional<SetEntryView> entry = w == null ? Optional.empty() : w.entry(viewer, entryId);
        Optional<ToolPerk> perk = entry.flatMap(e -> PieceEnchants.find(e.enchants(piece), enchant));
        Optional<EnchantDef> found = module.registry().get(enchant);
        if (entry.isEmpty() || perk.isEmpty() || found.isEmpty()) {
            set(SLOT_INFO, service.item(Material.BARRIER, viewer, EnchantsModule.ID, "menu.armor-entry.gone"));
            return;
        }
        EnchantDef def = found.get();
        int level = perk.get().level();
        int setLevel = entry.get().level();
        ArmorRules rules = module.armorRules();
        String now = module.menus().coloredName(def, level);
        List<Component> info = new ArrayList<>(module.menus().lines(viewer, "menu.armor-enchant.now",
                Msg.p("piece", module.ops().pieceLabel(entry.get(), piece)), Msg.p("max", EnchantText.roman(def.maxLevel())),
                Msg.p("gate", rules.gate(def, setLevel) <= 0 ? "-" : EnchantText.roman(rules.gate(def, setLevel))),
                Msg.p("invested", Format.number(perk.get().invested()))));
        info.addAll(module.menus().wrapped(viewer, module.text().effect(def, Math.min(level, def.maxLevel()))));
        if (!rules.working(piece, perk.get(), setLevel, module.registry()::get)) {
            info.addAll(module.menus().lines(viewer, "menu.armor-entry.dormant", Msg.p("level",
                    Math.max(rules.slots().unlockLevel(perk.get().slot()), rules.slots().requiredLevel(def.maxLevel(), level)))));
        }
        set(SLOT_INFO, service.item(Material.ENCHANTED_BOOK, module.menus().text(viewer, "menu.upgrade.name", Msg.mm("enchant", now)), info));
        upgradeButtons(entry.get(), perk.get(), def, rules);
        removeButtons(perk.get(), now);
    }

    private void upgradeButtons(SetEntryView entry, ToolPerk perk, EnchantDef def, ArmorRules rules) {
        int level = perk.level();
        EnchantRules.Verdict verdict = rules.canUpgrade(piece, perk, entry.level(), def);
        String next = module.menus().coloredName(def, Math.min(def.maxLevel(), level + 1));
        if (!verdict.ok()) {
            List<Component> lore = List.of(module.menus().whyArmor(viewer, verdict, def));
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
                        p -> module.ops().upgradeArmor(p, entryId, piece, enchant), p -> { })));

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
                        p -> module.ops().combineOnArmor(p, entryId, piece, enchant, bookId), p -> { })));
    }

    private void removeButtons(ToolPerk perk, String name) {
        List<EnchantOps.HeldBook> scrolls = module.ops().books(viewer).stream().filter(b -> b.book().scroll()).toList();
        if (scrolls.isEmpty()) {
            set(SLOT_SCROLL, service.item(Material.PAPER, viewer, EnchantsModule.ID, "menu.remove.no-scroll"),
                    click -> service.sound(viewer, "error"));
        } else {
            UUID scrollId = scrolls.getFirst().book().id();
            set(SLOT_SCROLL, service.item(Material.PAPER, viewer, EnchantsModule.ID, "menu.remove.scroll", Msg.mm("enchant", name),
                            Msg.p("scrolls", scrolls.size())),
                    click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.remove.crumb"), null,
                            v -> module.menus().lines(v, "menu.confirm.remove.details", Msg.mm("enchant", name)),
                            p -> module.ops().removeArmor(p, entryId, piece, enchant, scrollId), p -> { })));
        }
        long refund = module.costs().shatterRefund(perk.invested());
        set(SLOT_SHATTER, service.item(Material.TNT, viewer, EnchantsModule.ID, "menu.remove.shatter", Msg.mm("enchant", name),
                        Msg.p("refund", Format.number(refund))),
                click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.shatter.crumb"), null,
                        v -> module.menus().lines(v, "menu.confirm.shatter.details", Msg.mm("enchant", name), Msg.p("refund", Format.number(refund))),
                        p -> module.ops().shatterArmor(p, entryId, piece, enchant), p -> { })));
    }
}
