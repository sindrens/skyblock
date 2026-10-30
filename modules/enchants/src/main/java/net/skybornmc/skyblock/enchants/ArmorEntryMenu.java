package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.ArmorSlots;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;
import net.skybornmc.skyblock.common.wardrobe.SetEntryView;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One owned set's pieces and their armor enchant slots (E5, EN12), one row per piece:
 * <pre>
 *  row 2:  [Helm]      [slot 1] [slot 2]             [set: level, enchants]
 *  row 3:  [Cuirass]   [slot 1] [slot 2] [mythic]
 *  row 4:  [Greaves]   [slot 1] [slot 2]
 *  row 5:  [Boots]     [slot 1] [slot 2]
 *  row 6:  ←  ?  X  help
 * </pre>
 * Empty slot: pick a book. Filled slot: upgrade, combine, remove or shatter. Locked slot: shows the set level that opens
 * it. Dormant enchants (slot not open yet or above the set level gate) show gray. The entry is read again on every
 * refresh, so a change elsewhere never shows stale data. Click-only (Bedrock).
 */
final class ArmorEntryMenu extends Menu {

    static final int[] PIECE_SLOTS = {10, 19, 28, 37};
    static final int[] FIRST_ENCHANT_SLOTS = {12, 21, 30, 39};
    static final int SLOT_INFO_ENTRY = 16;

    private final EnchantsModule module;
    private final UUID entryId;

    ArmorEntryMenu(EnchantsModule module, UUID entryId) {
        this.module = module;
        this.entryId = entryId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.mode.armor"));
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.armor-entry.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        WardrobeService w = module.wardrobe();
        Optional<SetEntryView> found = w == null ? Optional.empty() : w.entry(viewer, entryId);
        if (found.isEmpty()) {
            set(22, service.item(Material.BARRIER, viewer, EnchantsModule.ID, "menu.armor-entry.gone"));
            return;
        }
        SetEntryView entry = found.get();
        ArmorRules rules = module.armorRules();
        set(SLOT_INFO_ENTRY, service.item(Material.ARMOR_STAND, module.menus().text(viewer, "menu.armor-entry.set.name",
                        Msg.mm("set", "<rarity:" + entry.rarity().id() + ">" + LoreBuilder.escape(entry.name()))),
                module.menus().lines(viewer, "menu.armor-entry.set.lore", Msg.p("level", entry.level()),
                        Msg.p("second", rules.slots().secondSlotLevel()), Msg.p("mythic", rules.slots().mythicSlotLevel()))));
        for (ArmorPiece piece : ArmorPiece.values()) {
            int row = piece.ordinal();
            ItemStack icon = w.pieceIcon(viewer, entryId, piece);
            set(PIECE_SLOTS[row], icon == null ? new ItemStack(Material.LEATHER_CHESTPLATE) : icon);
            List<Integer> slots = rules.slots().slots(piece);
            for (int i = 0; i < slots.size(); i++) {
                slotButton(FIRST_ENCHANT_SLOTS[row] + i, entry, piece, slots.get(i), rules);
            }
        }
    }

    private void slotButton(int menuSlot, SetEntryView entry, ArmorPiece piece, int slot, ArmorRules rules) {
        List<ToolPerk> enchants = entry.enchants(piece);
        boolean mythic = ArmorSlots.mythic(slot);
        switch (rules.slotState(piece, enchants, entry.level(), slot)) {
            case FILLED -> {
                ToolPerk perk = PieceEnchants.at(enchants, slot).orElseThrow();
                Optional<EnchantDef> def = module.registry().get(perk.id());
                boolean working = rules.working(piece, perk, entry.level(), module.registry()::get);
                List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.armor-entry.filled.lore", Msg.p("slot", slot),
                        Msg.p("invested", Format.number(perk.invested()))));
                def.ifPresent(d -> lore.addAll(module.menus().wrapped(viewer, module.text().effect(d, Math.min(perk.level(), d.maxLevel())))));
                if (!working) {
                    lore.addAll(module.menus().lines(viewer, "menu.armor-entry.dormant", Msg.p("level", def.map(d ->
                            Math.max(rules.slots().unlockLevel(slot), rules.slots().requiredLevel(d.maxLevel(), perk.level()))).orElse(0))));
                }
                lore.addAll(module.menus().lines(viewer, "menu.armor-entry.filled.click"));
                Component name = def.map(d -> module.menus().text(viewer, "menu.slots.filled.name",
                                Msg.mm("enchant", (mythic ? "<sym:star> " : "") + module.menus().coloredName(d, perk.level()))))
                        .orElseGet(() -> module.menus().text(viewer, "menu.slots.filled.unknown", Msg.p("enchant", perk.id())));
                set(menuSlot, service.item(working ? Material.ENCHANTED_BOOK : Material.BOOK, name, lore), click -> {
                    if (def.isEmpty()) {
                        service.sound(viewer, "error");
                        return;
                    }
                    openChild(new ArmorEnchantMenu(module, entryId, piece, perk.id()));
                });
            }
            case EMPTY -> set(menuSlot, service.item(mythic ? Material.NETHER_STAR : Material.BOOK, viewer, EnchantsModule.ID,
                    mythic ? "menu.armor-entry.mythic-empty" : "menu.armor-entry.empty", Msg.p("slot", slot)),
                    click -> openChild(new ArmorBookPickMenu(module, entryId, piece, slot)));
            case LOCKED -> set(menuSlot, service.item(mythic ? Material.GRAY_DYE : Material.GRAY_STAINED_GLASS_PANE, viewer, EnchantsModule.ID,
                    mythic ? "menu.armor-entry.mythic-locked" : "menu.armor-entry.locked", Msg.p("slot", slot),
                    Msg.p("level", rules.slots().unlockLevel(slot)), Msg.p("have", entry.level())), click -> service.sound(viewer, "error"));
        }
    }
}
