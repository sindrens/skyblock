package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Step 2: the tool's five perk slots as buttons (ENCHANTS §4.2): filled (enchant + level), empty ({@code ▸ Empty slot}),
 * locked ({@code Unlocks at Lv 30}), prestige ({@code ★ Prestige slot}). What a click does depends on the mode.
 * The tool is found again by its id on every refresh, so moving it never shows stale data.
 */
final class SlotMenu extends Menu {

    enum Mode {
        APPLY, UPGRADE, REMOVE;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static final int SLOT_TOOL = 13;
    static final int[] PERK_SLOTS = {29, 30, 31, 32, 33};

    private final EnchantsModule module;
    private final Mode mode;
    private final UUID toolId;

    SlotMenu(EnchantsModule module, Mode mode, UUID toolId) {
        this.module = module;
        this.mode = mode;
        this.toolId = toolId;
    }

    /**
     * GUI-4b (UX_REVIEW §2, target 5 clicks): the only slot this mode can use on a tool, so the slot screen can be
     * skipped: the one empty slot for Apply, the one filled slot for Upgrade and Remove. Empty when there are none or
     * several. {@code states} are slots 1..n in order. Pure.
     */
    static java.util.OptionalInt onlyChoice(List<EnchantRules.SlotState> states, Mode mode) {
        EnchantRules.SlotState wanted = mode == Mode.APPLY ? EnchantRules.SlotState.EMPTY : EnchantRules.SlotState.FILLED;
        int found = -1;
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i) == wanted) {
                if (found >= 0) {
                    return java.util.OptionalInt.empty();
                }
                found = i + 1;
            }
        }
        return found < 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(found);
    }

    /** The slot states of a tool, slots 1..5. */
    static List<EnchantRules.SlotState> states(EnchantsModule module, ToolInfo tool) {
        List<EnchantRules.SlotState> states = new ArrayList<>();
        for (int slot = 1; slot <= PERK_SLOTS.length; slot++) {
            states.add(module.rules().slotState(tool, slot));
        }
        return states;
    }

    /**
     * The screen after picking a tool: the book list / upgrade / remove screen straight away when only one slot fits the
     * mode (GUI-4b), otherwise this slot screen.
     */
    static Menu next(EnchantsModule module, Mode mode, ToolInfo tool) {
        java.util.OptionalInt only = onlyChoice(states(module, tool), mode);
        if (only.isPresent()) {
            int slot = only.getAsInt();
            if (mode == Mode.APPLY) {
                return new BookPickMenu(module, tool.id(), slot);
            }
            Optional<ToolPerk> perk = EnchantRules.perkAt(tool, slot);
            if (perk.isPresent()) {
                if (mode == Mode.REMOVE) {
                    return new RemoveMenu(module, tool.id(), perk.get().id());
                }
                if (module.registry().get(perk.get().id()).isPresent()) {
                    return new UpgradeMenu(module, tool.id(), perk.get().id());
                }
            }
        }
        return new SlotMenu(module, mode, tool.id());
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        // GUI-4: "Apply a book » <tool>", so this step reads differently from the tool picker before it.
        Component tool = module.ops().tool(viewer, toolId).map(h -> h.stack().effectiveName())
                .orElse(module.menus().text(viewer, "menu.title"));
        return service.title(viewer, module.menus().text(viewer, "menu.mode." + mode.key()), tool);
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.slots.info." + mode.key();
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
        Optional<EnchantOps.HeldTool> held = module.ops().tool(viewer, toolId);
        if (held.isEmpty()) {
            set(SLOT_TOOL, service.item(Material.BARRIER, viewer, EnchantsModule.ID, "menu.tool-gone"));
            return;
        }
        ToolInfo tool = held.get().info();
        set(SLOT_TOOL, EnchantMenus.toolCopy(held.get().stack()));
        for (int i = 0; i < PERK_SLOTS.length; i++) {
            slotButton(PERK_SLOTS[i], tool, i + 1);
        }
    }

    private void slotButton(int menuSlot, ToolInfo tool, int slot) {
        EnchantRules rules = module.rules();
        EnchantRules.SlotState state = rules.slotState(tool, slot);
        boolean prestige = slot == ToolSlots.PRESTIGE_SLOT;
        switch (state) {
            case FILLED, COVERED -> {
                ToolPerk perk = EnchantRules.perkAt(tool, slot).orElseThrow();
                Optional<EnchantDef> def = module.registry().get(perk.id());
                if (state == EnchantRules.SlotState.COVERED) {
                    set(menuSlot, service.item(Material.PURPLE_STAINED_GLASS_PANE, viewer, EnchantsModule.ID, "menu.slots.covered",
                            Msg.p("slot", slot), Msg.p("enchant", module.text().name(perk.id()))));
                    return;
                }
                List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.slots.filled.lore", Msg.p("slot", slot),
                        Msg.p("invested", Format.number(perk.invested()))));
                def.ifPresent(d -> lore.addAll(module.menus().wrapped(viewer, module.text().effect(d, perk.level()))));
                if (def.isPresent() && !def.get().enabled()) {
                    lore.addAll(module.menus().lines(viewer, "menu.slots.disabled"));
                }
                lore.addAll(module.menus().lines(viewer, "menu.slots.filled.click." + mode.key()));
                Component name = def.map(d -> module.menus().text(viewer, "menu.slots.filled.name",
                                Msg.mm("enchant", (prestige ? "<sym:star> " : "") + module.menus().coloredName(d, perk.level()))))
                        .orElseGet(() -> module.menus().text(viewer, "menu.slots.filled.unknown", Msg.p("enchant", perk.id())));
                ItemStack item = service.item(Material.ENCHANTED_BOOK, name, lore);
                set(menuSlot, item, click -> {
                    switch (mode) {
                        case UPGRADE -> {
                            if (def.isEmpty()) {
                                service.sound(viewer, "error");
                                return;
                            }
                            openChild(new UpgradeMenu(module, toolId, perk.id()));
                        }
                        case REMOVE -> openChild(new RemoveMenu(module, toolId, perk.id()));
                        case APPLY -> service.sound(viewer, "error");
                    }
                });
            }
            case EMPTY -> {
                ItemStack item = service.item(prestige ? Material.NETHER_STAR : Material.BOOK, viewer, EnchantsModule.ID,
                        (prestige ? "menu.slots.prestige-empty." : "menu.slots.empty.") + (mode == Mode.APPLY ? "apply" : "other"),
                        Msg.p("slot", slot));
                set(menuSlot, item, click -> {
                    if (mode == Mode.APPLY) {
                        openChild(new BookPickMenu(module, toolId, slot));
                    } else {
                        service.sound(viewer, "error");
                    }
                });
            }
            case LOCKED -> {
                if (prestige) {
                    set(menuSlot, service.item(Material.GRAY_DYE, viewer, EnchantsModule.ID, "menu.slots.prestige-locked"));
                } else {
                    set(menuSlot, service.item(Material.GRAY_STAINED_GLASS_PANE, viewer, EnchantsModule.ID, "menu.slots.locked",
                            Msg.p("slot", slot), Msg.p("level", rules.unlockLevel(slot)), Msg.p("have", tool.level())));
                }
            }
        }
    }
}
