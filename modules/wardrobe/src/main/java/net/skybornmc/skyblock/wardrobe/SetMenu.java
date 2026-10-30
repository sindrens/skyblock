package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Set detail (WARDROBE §6.2), for a set or for a merged entry:
 * <pre>
 *  4      the set
 *  10-13  Helm · Cuirass · Greaves · Boots (click to wear / take off)    15 Wear all · 16 Take all off
 *  20     bonuses (2 / 4 pieces)   22 set level (bar, multiplier, next, earns from)   24 enchants (E5)
 *  29     Use a Wardrobe Tome      31 Buy / Claim / owned / locked / crate     33 Redeem token (when you carry one)
 *  38     Merge (max level)        42 Trail on/off (trail level)
 * </pre>
 * Click-only (Bedrock-safe); every action that costs something goes through the 3-row confirm.
 */
final class SetMenu extends Menu {

    private static final int[] PIECE_SLOTS = {10, 11, 12, 13};

    private final WardrobeModule module;
    private final SetDef def;
    /** The merged entry shown, or null for a plain set (its own entry, if owned). */
    private final UUID mergedEntry;

    SetMenu(WardrobeModule module, SetDef def) {
        this(module, def, null);
    }

    SetMenu(WardrobeModule module, SetDef def, UUID mergedEntry) {
        this.module = module;
        this.def = def;
        this.mergedEntry = mergedEntry;
    }

    @Override
    public int rows() {
        return 6;
    }

    private Session session() {
        return module.equipment().sessionOrNull(viewer.getUniqueId());
    }

    /** The entry this screen is about (owned set or the merged entry), if any. */
    private Optional<WardrobeStore.Entry> entry() {
        Session s = session();
        if (s == null) {
            return Optional.empty();
        }
        return mergedEntry != null ? s.entry(mergedEntry) : s.owned(def.id());
    }

    /** The other set of a merged entry. */
    private Optional<SetDef> second(WardrobeStore.Entry e) {
        if (!e.isMerged()) {
            return Optional.empty();
        }
        return module.registry().get(e.setId().equals(def.id()) ? e.mergedSetId() : e.setId());
    }

    /** The set whose look the pieces use. */
    private SetDef look() {
        return entry().flatMap(e -> module.registry().get(e.look())).orElse(def);
    }

    @Override
    public Component title() {
        String name = entry().filter(WardrobeStore.Entry::isMerged).map(e -> module.text().entryName(module.registry(), e)).orElse(def.name());
        return service.title(viewer, module.menus().text(viewer, "menu.title"), Component.text(name));
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
        Session s = session();
        WardrobeMenus m = module.menus();
        Optional<WardrobeStore.Entry> entry = entry();
        if (mergedEntry != null && entry.isEmpty()) {
            // The merged entry is gone (staff removed it): show the plain set instead.
            set(22, service.item(Material.BARRIER, viewer, WardrobeModule.ID, "menu.set.gone"));
            return;
        }
        boolean merged = entry.map(WardrobeStore.Entry::isMerged).orElse(false);
        Offers.Offer offer = merged ? null : module.shop().offer(viewer, s, def);
        SetDef look = look();

        List<Component> head = new ArrayList<>();
        head.add(m.text(viewer, "menu.set.subtitle", Msg.p("activity", module.text().plain("activity-name." + def.activity())),
                Msg.p("tier", WardrobeText.roman(def.tier()))));
        head.add(Component.empty());
        if (merged) {
            head.addAll(m.mergedStatus(viewer, entry.get(), s));
        } else {
            head.addAll(m.status(viewer, def, offer, s));
        }
        Component title = module.core.messages().parse(viewer, merged ? module.text().entryColored(module.registry(), entry.get()) : WardrobeText.colored(def));
        set(4, service.item(module.items().icon(look, ArmorPiece.CHEST), title, head));

        // Pieces
        for (ArmorPiece piece : ArmorPiece.values()) {
            int slot = PIECE_SLOTS[piece.ordinal()];
            Component name = module.core.messages().parse(viewer, "<rarity:" + look.rarity().id() + ">"
                    + net.skybornmc.skyblock.common.item.LoreBuilder.escape(module.text().pieceName(look, piece)));
            ItemStack icon = module.items().icon(look, piece);
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
        int level = entry.map(WardrobeStore.Entry::level).orElse(0);
        double mult = module.bonusMultiplier(level);
        List<Component> bonus = new ArrayList<>(m.lines(viewer, "menu.set.bonuses.lore"));
        bonusLines(bonus, def, mult);
        entry.flatMap(this::second).ifPresent(other -> {
            bonus.add(m.text(viewer, "menu.set.bonuses.merged", Msg.p("set", other.shortName())));
            bonusLines(bonus, other, mult);
        });
        if (s != null && s.warzone) {
            bonus.addAll(m.lines(viewer, "menu.set.bonuses.warzone"));
        }
        set(20, service.item(Material.NETHER_STAR, m.text(viewer, "menu.set.bonuses.name"), bonus));
        set(22, m.levelItem(viewer, entry.orElse(null), def));
        enchantsButton(s, entry.orElse(null));

        // Actions
        if (!merged) {
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
        entry.ifPresent(e -> {
            tome(e);
            merge(e);
            trail(e);
        });
    }

    private void bonusLines(List<Component> out, SetDef set, double mult) {
        WardrobeMenus m = module.menus();
        out.add(m.text(viewer, "menu.set.bonuses.two"));
        set.two().forEach(b -> out.add(m.text(viewer, "menu.set.bonuses.line", Msg.mm("text", module.text().bonus(b.text(), b.value() * mult)))));
        out.add(m.text(viewer, "menu.set.bonuses.four"));
        set.four().forEach(b -> out.add(m.text(viewer, "menu.set.bonuses.line", Msg.mm("text", module.text().bonus(b.text(), b.value() * mult)))));
    }

    /** 29: Use a Wardrobe Tome (only when carrying one and the set is below the tome cap). */
    private void tome(WardrobeStore.Entry e) {
        if (!module.config().levelsEnabled || Tomes.tomeSlot(viewer.getInventory()) < 0) {
            return;
        }
        WardrobeMenus m = module.menus();
        int cap = module.config().tomeMaxLevel;
        SetLevels levels = module.config().levels();
        if (!levels.tomeUsable(e.xp(), cap, e.isMerged())) {
            set(29, service.item(Material.BOOK, viewer, WardrobeModule.ID, "menu.set.tome-capped", Msg.p("level", cap)));
            return;
        }
        int slot = Tomes.tomeSlot(viewer.getInventory());
        long xp = WardrobeItems.tomeXp(viewer.getInventory().getItem(slot));
        double after = levels.tome(e.xp(), xp, cap, e.isMerged());
        int toLevel = levels.level(after, e.isMerged());
        boolean wasted = e.xp() + xp > after + 1e-6;
        set(29, service.item(Material.BOOK, viewer, WardrobeModule.ID, "menu.set.tome", Msg.p("xp", WardrobeText.number(xp)),
                Msg.p("level", toLevel), Msg.p("cap", cap)), click -> {
            String name = module.text().entryName(module.registry(), e);
            openChild(new ConfirmMenu(v -> m.text(v, "confirm.tome.crumb"), null, v -> {
                List<Component> lines = new ArrayList<>(m.lines(v, "confirm.tome.details", Msg.p("set", name),
                        Msg.p("xp", WardrobeText.number(xp)), Msg.p("level", toLevel)));
                if (wasted) {
                    lines.addAll(m.lines(v, "confirm.tome.capped", Msg.p("cap", cap)));
                }
                return lines;
            }, p -> module.tomes().use(p, e.entryId()), p -> {
            }));
        });
    }

    /** 38: Merge (owned plain set; clickable only at the max level of a tier I-III set). */
    private void merge(WardrobeStore.Entry e) {
        if (e.isMerged() || !module.merging().enabled()) {
            return;
        }
        WardrobeMenus m = module.menus();
        Optional<MergeRules.Refusal> why = module.merging().canStart(e);
        if (why.isPresent()) {
            set(38, service.item(Material.GRAY_DYE, viewer, WardrobeModule.ID, "menu.set.merge-locked",
                    Msg.mm("why", module.text().raw("merge.why." + why.get().key())), Msg.p("level", module.config().maxLevel)));
            return;
        }
        set(38, service.item(Material.ANVIL, viewer, WardrobeModule.ID, "menu.set.merge"), click -> openChild(new MergePickMenu(module, e)));
    }

    /** 42: trail toggle (from the trail level). */
    private void trail(WardrobeStore.Entry e) {
        if (!module.config().levelsEnabled || e.level() < module.config().trailLevel) {
            return;
        }
        boolean on = module.leveling().trailOn(viewer);
        set(42, service.item(on ? Material.BLAZE_POWDER : Material.GUNPOWDER, viewer, WardrobeModule.ID,
                on ? "menu.set.trail-on" : "menu.set.trail-off"), click -> {
            module.leveling().toggleTrail(viewer);
            refresh();
        });
    }

    private void action(Offers.Offer offer) {
        WardrobeMenus m = module.menus();
        Session s = session();
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

    private Player v() {
        return viewer;
    }

    private static Material icon(Offers.Kind kind) {
        return switch (kind) {
            case OWNED -> Material.LIME_DYE;
            case CRATE_ONLY -> Material.TRIPWIRE_HOOK;
            default -> Material.GRAY_DYE;
        };
    }

    /**
     * Slot 24 (E5): the armor enchants per piece of this entry; a click opens the Enchanter's Armor screen for it. Not owned:
     * explains that enchants go on owned sets. Enchants module off: shown as closed.
     */
    private void enchantsButton(Session s, WardrobeStore.Entry entry) {
        WardrobeMenus m = module.menus();
        var enchants = module.core.services().optional(net.skybornmc.skyblock.common.enchant.EnchantService.class).orElse(null);
        if (enchants == null) {
            set(24, service.item(Material.BOOK, viewer, WardrobeModule.ID, "menu.set.enchants-off"));
            return;
        }
        if (entry == null || s == null) {
            set(24, service.item(Material.BOOK, viewer, WardrobeModule.ID, "menu.set.enchants-not-owned"));
            return;
        }
        List<Component> lore = new ArrayList<>(m.lines(viewer, "menu.set.enchants.lore"));
        for (ArmorPiece piece : ArmorPiece.values()) {
            var list = s.enchants(entry.entryId(), piece);
            String names = list.isEmpty() ? module.text().plain("menu.set.enchants.none")
                    : String.join(", ", list.stream().map(p -> enchants.displayName(p.id(), p.level())).toList());
            lore.add(m.text(viewer, "menu.set.enchants.piece", Msg.p("piece", module.text().plain("piece-name." + piece.key())),
                    Msg.p("enchants", names)));
        }
        lore.addAll(m.lines(viewer, "menu.set.enchants.click"));
        UUID id = entry.entryId();
        set(24, service.item(Material.ENCHANTED_BOOK, m.text(viewer, "menu.set.enchants.name"), lore),
                click -> enchants.openArmor(viewer, id, this));
    }
}
