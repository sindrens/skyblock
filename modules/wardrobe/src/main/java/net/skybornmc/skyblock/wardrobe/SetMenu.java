package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Set detail (WARDROBE §6.2):
 * <pre>
 *  4      the set
 *  10-13  Helm · Cuirass · Greaves · Boots (click to wear / take off)    15 Wear all · 16 Take all off
 *  20     bonuses (2 / 4 pieces)   22 set level   24 enchants (E5)
 *  31     Buy / Claim / owned / locked / crate     33 Redeem token (when you carry one)
 * </pre>
 */
final class SetMenu extends Menu {

    private static final int[] PIECE_SLOTS = {10, 11, 12, 13};

    private final WardrobeModule module;
    private final SetDef def;

    SetMenu(WardrobeModule module, SetDef def) {
        this.module = module;
        this.def = def;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.title"), Component.text(def.name()));
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.set.info";
    }

    @Override
    public String helpTopic() {
        return "wardrobe";
    }

    @Override
    protected void build() {
        frame();
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        Offers.Offer offer = module.shop().offer(viewer, s, def);
        Optional<WardrobeStore.Entry> entry = s == null ? Optional.empty() : s.owned(def.id());
        WardrobeMenus m = module.menus();

        List<Component> head = new ArrayList<>();
        head.add(m.text(viewer, "menu.set.subtitle", Msg.p("activity", module.text().plain("activity-name." + def.activity())),
                Msg.p("tier", WardrobeText.roman(def.tier()))));
        head.add(Component.empty());
        head.addAll(m.status(viewer, def, offer, s));
        set(4, service.item(module.items().icon(def, ArmorPiece.CHEST), module.core.messages().parse(viewer, WardrobeText.colored(def)), head));

        // Pieces
        for (ArmorPiece piece : ArmorPiece.values()) {
            int slot = PIECE_SLOTS[piece.ordinal()];
            Component name = module.core.messages().parse(viewer, "<rarity:" + def.rarity().id() + ">"
                    + net.skybornmc.skyblock.common.item.LoreBuilder.escape(module.text().pieceName(def, piece)));
            ItemStack icon = module.items().icon(def, piece);
            if (entry.isEmpty()) {
                set(slot, service.item(icon, name, m.lines(viewer, "menu.set.piece.not-owned")));
                continue;
            }
            WardrobeStore.Equipped worn = s.equipped.get(piece);
            boolean mine = worn != null && worn.entryId().equals(entry.get().entryId());
            set(slot, service.item(icon, name, m.lines(viewer, mine ? "menu.set.piece.worn" : "menu.set.piece.wear")), click -> {
                if (mine) {
                    module.equipment().takeOff(viewer, List.of(piece));
                } else {
                    module.equipment().wear(viewer, entry.get(), List.of(piece));
                }
            });
        }
        if (entry.isPresent()) {
            set(15, service.item(Material.LIME_DYE, viewer, WardrobeModule.ID, "menu.set.wear-all"),
                    click -> module.equipment().wear(viewer, entry.get(), Arrays.asList(ArmorPiece.values())));
            List<ArmorPiece> mineWorn = s.equipped.values().stream().filter(q -> q.entryId().equals(entry.get().entryId()))
                    .map(WardrobeStore.Equipped::piece).toList();
            if (!mineWorn.isEmpty()) {
                set(16, service.item(Material.RED_DYE, viewer, WardrobeModule.ID, "menu.set.take-off-all"),
                        click -> module.equipment().takeOff(viewer, mineWorn));
            }
        }

        // Bonuses, level, enchants
        double mult = module.bonusMultiplier(entry.map(WardrobeStore.Entry::level).orElse(0));
        List<Component> bonus = new ArrayList<>(m.lines(viewer, "menu.set.bonuses.lore"));
        bonus.add(m.text(viewer, "menu.set.bonuses.two"));
        def.two().forEach(b -> bonus.add(m.text(viewer, "menu.set.bonuses.line", Msg.mm("text", module.text().bonus(b.text(), b.value() * mult)))));
        bonus.add(m.text(viewer, "menu.set.bonuses.four"));
        def.four().forEach(b -> bonus.add(m.text(viewer, "menu.set.bonuses.line", Msg.mm("text", module.text().bonus(b.text(), b.value() * mult)))));
        set(20, service.item(Material.NETHER_STAR, m.text(viewer, "menu.set.bonuses.name"), bonus));
        set(22, service.item(Material.EXPERIENCE_BOTTLE, viewer, WardrobeModule.ID,
                module.config().levelsEnabled ? "menu.set.level" : "menu.set.level-off",
                Msg.p("level", entry.map(WardrobeStore.Entry::level).orElse(0)), Msg.p("multiplier", WardrobeText.number(mult))));
        set(24, service.item(Material.ENCHANTED_BOOK, viewer, WardrobeModule.ID, "menu.set.enchants"));

        // Action
        action(offer);
        int tokenSlot = Shop.tokenSlot(viewer.getInventory(), def.id());
        if (tokenSlot >= 0 && entry.isEmpty()) {
            set(33, service.item(Material.ARMOR_STAND, viewer, WardrobeModule.ID, "menu.set.redeem"), click -> {
                int now = Shop.tokenSlot(viewer.getInventory(), def.id());
                if (now < 0) {
                    module.fail(viewer, "token-gone");
                    return;
                }
                module.shop().redeem(viewer, now);
            });
        }
    }

    private void action(Offers.Offer offer) {
        WardrobeMenus m = module.menus();
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        switch (offer.kind()) {
            case BUY -> {
                String price = module.shop().price(offer.currency(), offer.price());
                set(31, service.item(Material.EMERALD, viewer, WardrobeModule.ID, "menu.set.buy", Msg.p("price", price)), click -> {
                    Menu confirm = new ConfirmMenu(v -> m.text(v, "confirm.buy.crumb"),
                            service.item(module.items().icon(def, ArmorPiece.CHEST), module.core.messages().parse(v(), WardrobeText.colored(def)), List.of()),
                            v -> m.lines(v, "confirm.buy.details", Msg.p("set", def.name()), Msg.p("price", price)),
                            p -> module.shop().buy(p, def), p -> {
                            });
                    openChild(confirm);
                });
            }
            case CLAIM -> set(31, service.item(Material.EMERALD, viewer, WardrobeModule.ID, "menu.set.claim"),
                    click -> module.shop().claim(viewer, def));
            default -> set(31, service.item(icon(offer.kind()), module.menus().text(viewer, "menu.set.state.name"),
                    module.menus().status(viewer, def, offer, s)));
        }
    }

    private org.bukkit.entity.Player v() {
        return viewer;
    }

    private static Material icon(Offers.Kind kind) {
        return switch (kind) {
            case OWNED -> Material.LIME_DYE;
            case CRATE_ONLY -> Material.TRIPWIRE_HOOK;
            default -> Material.GRAY_DYE;
        };
    }
}
