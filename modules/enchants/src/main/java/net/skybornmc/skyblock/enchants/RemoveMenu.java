package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Remove one applied enchant (ENCHANTS §4.5): with a Disenchant Scroll it comes off as a book at its level (so it can be
 * moved); without one it shatters for a share of the crystals put into it (red warning, confirm dialog).
 */
final class RemoveMenu extends Menu {

    static final int SLOT_INFO = 13;
    static final int SLOT_SCROLL = 29;
    static final int SLOT_SHATTER = 33;

    private final EnchantsModule module;
    private final UUID toolId;
    private final String enchant;

    RemoveMenu(EnchantsModule module, UUID toolId, String enchant) {
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
        return service.title(viewer, module.menus().text(viewer, "menu.mode.remove"), Component.text(module.text().name(enchant)));
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.remove.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected void build() {
        frame();
        Optional<ToolPerk> perk = module.ops().tool(viewer, toolId).flatMap(h -> h.info().perk(enchant));
        if (perk.isEmpty()) {
            set(SLOT_INFO, service.item(Material.BARRIER, viewer, EnchantsModule.ID, "menu.tool-gone"));
            return;
        }
        int level = perk.get().level();
        Optional<EnchantDef> def = module.registry().get(enchant);
        String name = def.map(d -> module.menus().coloredName(d, level)).orElse(net.skybornmc.skyblock.common.item.LoreBuilder.escape(enchant));
        set(SLOT_INFO, service.item(Material.ENCHANTED_BOOK, module.menus().text(viewer, "menu.remove.name", Msg.mm("enchant", name)),
                module.menus().lines(viewer, "menu.remove.now", Msg.p("invested", Format.number(perk.get().invested())))));

        List<EnchantOps.HeldBook> scrolls = module.ops().books(viewer).stream().filter(b -> b.book().scroll()).toList();
        if (def.isEmpty()) {
            set(SLOT_SCROLL, service.item(Material.PAPER, viewer, EnchantsModule.ID, "menu.remove.scroll-unknown"),
                    click -> service.sound(viewer, "error"));
        } else if (scrolls.isEmpty()) {
            set(SLOT_SCROLL, service.item(Material.PAPER, viewer, EnchantsModule.ID, "menu.remove.no-scroll"),
                    click -> service.sound(viewer, "error"));
        } else {
            UUID scrollId = scrolls.getFirst().book().id();
            set(SLOT_SCROLL, service.item(Material.PAPER, viewer, EnchantsModule.ID, "menu.remove.scroll", Msg.mm("enchant", name),
                    Msg.p("scrolls", scrolls.size())),
                    click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.remove.crumb"), null,
                            v -> module.menus().lines(v, "menu.confirm.remove.details", Msg.mm("enchant", name)),
                            p -> module.ops().remove(p, toolId, enchant, scrollId), p -> { })));
        }
        long refund = module.costs().shatterRefund(perk.get().invested());
        set(SLOT_SHATTER, service.item(Material.TNT, viewer, EnchantsModule.ID, "menu.remove.shatter", Msg.mm("enchant", name),
                Msg.p("refund", Format.number(refund))),
                click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.shatter.crumb"), null,
                        v -> module.menus().lines(v, "menu.confirm.shatter.details", Msg.mm("enchant", name), Msg.p("refund", Format.number(refund))),
                        p -> module.ops().shatter(p, toolId, enchant), p -> { })));
    }
}
