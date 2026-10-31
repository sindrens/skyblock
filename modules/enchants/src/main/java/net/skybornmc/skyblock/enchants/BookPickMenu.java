package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Step 3 of apply (ENCHANTS §4.2): your books; only books that fit are clickable, the others show why in red
 * ({@code ✘ Conflicts with Vein Rend}, {@code ✘ Needs tool Lv 34}, {@code ✘ Pickaxe only} …). Step 4 is the confirm dialog.
 */
final class BookPickMenu extends PagedMenu<EnchantOps.HeldBook> {

    private final EnchantsModule module;
    private final UUID toolId;
    private final int slot;

    BookPickMenu(EnchantsModule module, UUID toolId, int slot) {
        this.module = module;
        this.toolId = toolId;
        this.slot = slot;
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.books.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected String emptyKey() {
        return "menu.books.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.mode.apply"), module.menus().text(viewer, "menu.books.crumb", Msg.p("slot", slot)));
    }

    /** GUI-4b: the tool being enchanted on the summary slot (the slot screen may have been skipped). */
    @Override
    protected void decorate() {
        module.ops().tool(viewer, toolId).ifPresent(held -> set(4, EnchantMenus.withExtraLore(EnchantMenus.toolCopy(held.stack()),
                module.menus().lines(viewer, "menu.books.tool", Msg.p("slot", slot)))));
    }

    private Optional<ToolInfo> tool() {
        return module.ops().tool(viewer, toolId).map(EnchantOps.HeldTool::info);
    }

    private EnchantRules.Verdict verdict(ToolInfo tool, EnchantOps.HeldBook book) {
        Optional<EnchantDef> def = module.registry().get(book.book().enchant());
        if (def.isEmpty()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.DISABLED);
        }
        return module.rules().canApply(tool, slot, def.get(), book.book().level(), module.registry()::get);
    }

    @Override
    protected List<EnchantOps.HeldBook> entries() {
        Optional<ToolInfo> tool = tool();
        List<EnchantOps.HeldBook> list = new ArrayList<>(module.ops().books(viewer).stream().filter(b -> !b.book().scroll()).toList());
        if (tool.isPresent()) {
            list.sort(Comparator.comparing((EnchantOps.HeldBook b) -> !verdict(tool.get(), b).ok()));
        }
        return list;
    }

    @Override
    protected MenuButton entry(EnchantOps.HeldBook book) {
        Optional<ToolInfo> tool = tool();
        Optional<EnchantDef> def = module.registry().get(book.book().enchant());
        if (tool.isEmpty() || def.isEmpty()) {
            return MenuButton.display(EnchantMenus.withExtraLore(BookItems.displayCopy(book.stack()),
                    List.of(module.menus().text(viewer, tool.isEmpty() ? "why.tool-gone" : "why.unknown"))));
        }
        EnchantRules.Verdict verdict = verdict(tool.get(), book);
        if (!verdict.ok()) {
            return MenuButton.of(EnchantMenus.withExtraLore(BookItems.displayCopy(book.stack()),
                    List.of(module.menus().why(viewer, verdict, def.get(), tool.get()))), click -> service.sound(viewer, "error"));
        }
        long fee = module.costs().applyFee(def.get(), book.book().level());
        List<Component> extra = module.menus().lines(viewer, "menu.books.fits", EnchantMenus.cost(fee));
        String name = module.menus().coloredName(def.get(), book.book().level());
        return MenuButton.of(EnchantMenus.withExtraLore(BookItems.displayCopy(book.stack()), extra), click -> openChild(new ConfirmMenu(
                v -> module.menus().text(v, "menu.confirm.apply.crumb"), BookItems.displayCopy(book.stack()),
                v -> module.menus().lines(v, "menu.confirm.apply.details", Msg.mm("enchant", name),
                        Msg.p("tool", module.ops().tool(v, toolId).map(t -> plain(t.stack())).orElse("?")), Msg.p("slot", slot),
                        EnchantMenus.cost(fee)),
                p -> module.ops().apply(p, toolId, slot, book.book().id()), p -> { })));
    }

    static String plain(org.bukkit.inventory.ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(stack.effectiveName());
    }
}
