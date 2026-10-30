package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.SetEntryView;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * E5 apply, step 3: your books for one slot of a set piece; only books that fit are clickable, the others say why in red
 * ({@code ✘ Boots only}, {@code ✘ Needs set Lv 30}, {@code ✘ Mythic slot only} ...). Then the confirm dialog.
 */
final class ArmorBookPickMenu extends PagedMenu<EnchantOps.HeldBook> {

    private final EnchantsModule module;
    private final UUID entryId;
    private final ArmorPiece piece;
    private final int slot;

    ArmorBookPickMenu(EnchantsModule module, UUID entryId, ArmorPiece piece, int slot) {
        this.module = module;
        this.entryId = entryId;
        this.piece = piece;
        this.slot = slot;
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.armor-books.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected String emptyKey() {
        return "menu.armor-books.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.mode.armor"), module.menus().text(viewer, "menu.books.crumb", Msg.p("slot", slot)));
    }

    private Optional<SetEntryView> entry() {
        WardrobeService w = module.wardrobe();
        return w == null ? Optional.empty() : w.entry(viewer, entryId);
    }

    private EnchantRules.Verdict verdict(SetEntryView entry, EnchantOps.HeldBook book) {
        Optional<EnchantDef> def = module.registry().get(book.book().enchant());
        if (def.isEmpty()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.DISABLED);
        }
        return module.armorRules().canApply(piece, entry.enchants(piece), entry.level(), slot, def.get(), book.book().level(),
                module.registry()::get);
    }

    @Override
    protected List<EnchantOps.HeldBook> entries() {
        Optional<SetEntryView> entry = entry();
        List<EnchantOps.HeldBook> list = new ArrayList<>(module.ops().books(viewer).stream().filter(b -> !b.book().scroll()).toList());
        if (entry.isPresent()) {
            // Books that fit first, then armor books, then the rest.
            list.sort(Comparator.comparing((EnchantOps.HeldBook b) -> !verdict(entry.get(), b).ok())
                    .thenComparing(b -> module.registry().get(b.book().enchant()).map(d -> !d.armorOnly()).orElse(true)));
        }
        return list;
    }

    @Override
    protected MenuButton entry(EnchantOps.HeldBook book) {
        Optional<SetEntryView> entry = entry();
        Optional<EnchantDef> def = module.registry().get(book.book().enchant());
        if (entry.isEmpty() || def.isEmpty()) {
            return MenuButton.display(EnchantMenus.withExtraLore(BookItems.displayCopy(book.stack()),
                    List.of(module.menus().text(viewer, entry.isEmpty() ? "why.set-gone" : "why.unknown"))));
        }
        EnchantRules.Verdict verdict = verdict(entry.get(), book);
        if (!verdict.ok()) {
            return MenuButton.of(EnchantMenus.withExtraLore(BookItems.displayCopy(book.stack()),
                    List.of(module.menus().whyArmor(viewer, verdict, def.get()))), click -> service.sound(viewer, "error"));
        }
        long fee = module.costs().applyFee(def.get(), book.book().level());
        List<Component> extra = module.menus().lines(viewer, "menu.books.fits", EnchantMenus.cost(fee));
        String name = module.menus().coloredName(def.get(), book.book().level());
        String label = module.ops().pieceLabel(entry.get(), piece);
        return MenuButton.of(EnchantMenus.withExtraLore(BookItems.displayCopy(book.stack()), extra), click -> openChild(new ConfirmMenu(
                v -> module.menus().text(v, "menu.confirm.apply.crumb"), BookItems.displayCopy(book.stack()),
                v -> module.menus().lines(v, "menu.confirm.apply.details", Msg.mm("enchant", name), Msg.p("tool", label), Msg.p("slot", slot),
                        EnchantMenus.cost(fee)),
                p -> module.ops().applyArmor(p, entryId, piece, slot, book.book().id()), p -> { })));
    }
}
