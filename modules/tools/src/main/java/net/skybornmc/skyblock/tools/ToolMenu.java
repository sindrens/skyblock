package net.skybornmc.skyblock.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The tool menu (shift + right-click a tool, or {@code /tool}): level and XP, stats, the five perk slots (filled, empty,
 * locked with the level that opens it, the prestige slot), history, skin, prestige and a shortcut to the Forge.
 * The tool is found again by its unique id on every refresh, so moving it never shows stale data.
 * <pre>
 *  row 2:      [level]     [tool]     [stats]
 *  row 4:   [slot1][slot2][slot3][slot4][★ slot5]
 *  row 5:   [history]     [skin]     [prestige]
 *  row 6:   ←  [forge]  ?  X  help
 * </pre>
 */
final class ToolMenu extends Menu {

    static final int SLOT_LEVEL = 11;
    static final int SLOT_TOOL = 13;
    static final int SLOT_STATS = 15;
    static final int[] PERK_SLOTS = {29, 30, 31, 32, 33};
    static final int SLOT_HISTORY = 38;
    static final int SLOT_SKIN = 40;
    static final int SLOT_PRESTIGE = 42;
    static final int SLOT_FORGE = 47;

    private final ToolsModule module;
    private final UUID toolId;

    ToolMenu(ToolsModule module, UUID toolId) {
        this.module = module;
        this.toolId = toolId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, ToolsModule.ID, "tool-menu.title");
    }

    @Override
    public String messageModule() {
        return ToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "tool-menu.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
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
        int slot = ToolTracker.find(viewer.getInventory(), toolId);
        Optional<ToolData> found = slot < 0 ? Optional.empty() : ToolItems.read(viewer.getInventory().getItem(slot));
        if (found.isEmpty()) {
            set(SLOT_TOOL, service.item(Material.BARRIER, viewer, ToolsModule.ID, "tool-menu.gone"));
            return;
        }
        ToolData tool = found.get();
        ItemStack stack = viewer.getInventory().getItem(slot);
        ToolCurve curve = module.curve();
        List<Integer> slotLevels = module.config().slotLevels();

        set(SLOT_TOOL, ToolItems.displayCopy(stack));
        set(SLOT_LEVEL, levelItem(tool, curve, slotLevels));
        set(SLOT_STATS, statsItem(tool));
        int unlocked = tool.slots(slotLevels);
        for (int i = 0; i < PERK_SLOTS.length; i++) {
            set(PERK_SLOTS[i], perkItem(tool, i + 1, unlocked, slotLevels));
        }
        set(SLOT_HISTORY, historyItem(tool, stack));
        set(SLOT_SKIN, service.item(Material.PAINTING, viewer, ToolsModule.ID, "tool-menu.skin",
                Msg.mm("skin", tool.skin() == null ? module.items().raw("tool-menu.no-skin") : module.items().skinName(tool.skin()))),
                click -> module.openSkins(viewer, toolId, this));
        prestigeButton(tool, curve);
        set(SLOT_FORGE, service.item(Material.SMITHING_TABLE, viewer, ToolsModule.ID, "tool-menu.forge"),
                click -> openChild(new ForgeMenu(module, tool.kind())));
    }

    private ItemStack levelItem(ToolData tool, ToolCurve curve, List<Integer> slotLevels) {
        List<Component> lore = new ArrayList<>();
        if (tool.level() >= curve.maxLevel()) {
            lore.addAll(lines("tool-menu.level.max"));
        } else {
            double progress = curve.progress(tool.xp());
            int filled = (int) Math.round(progress * 10);
            String bar = module.items().raw("tool-menu.bar.filled").repeat(filled) + module.items().raw("tool-menu.bar.empty").repeat(10 - filled);
            lore.addAll(lines("tool-menu.level.progress", Msg.mm("bar", bar), Msg.p("percent", (int) Math.floor(progress * 100))));
            lore.addAll(lines("tool-menu.level.xp", Msg.p("into", Format.number((long) curve.intoLevel(tool.xp()))),
                    Msg.p("step", Format.number((long) Math.ceil(curve.step(tool.level())))), Msg.p("next", tool.level() + 1)));
        }
        int next = tool.prestige() > 0 ? -1 : ToolSlots.nextUnlock(tool.level(), slotLevels);
        if (next > 0) {
            lore.addAll(lines("tool-menu.level.next-slot", Msg.p("level", next)));
        }
        lore.add(Component.empty());
        lore.addAll(lines("tool-menu.level.how." + tool.kind().id()));
        return service.item(Material.EXPERIENCE_BOTTLE, text("tool-menu.level.name", Msg.p("level", tool.level()), Msg.p("max", curve.maxLevel())), lore);
    }

    private ItemStack statsItem(ToolData tool) {
        List<Component> lore = new ArrayList<>(lines("tool-menu.stats.tier", Msg.p("tier", tool.tier()),
                Msg.mm("tier-name", module.items().tierName(tool.kind(), tool.tier()))));
        if (tool.kind() != net.skybornmc.skyblock.common.tools.ToolKind.SWORD && tool.kind() != net.skybornmc.skyblock.common.tools.ToolKind.ROD) {
            lore.addAll(lines("tool-menu.stats.speed", Msg.p("speed", Math.round(module.items().speedBonus(tool) * 100))));
        }
        if (tool.kind() == net.skybornmc.skyblock.common.tools.ToolKind.PICKAXE || tool.kind() == net.skybornmc.skyblock.common.tools.ToolKind.AXE) {
            lore.addAll(lines("tool-menu.stats.fortune", Msg.p("fortune", Math.round((module.items().fortune(tool) - 1) * 100))));
        }
        module.catalog().tier(tool.kind(), tool.tier()).filter(t -> t.damage() != 0)
                .ifPresent(t -> lore.addAll(lines("tool-menu.stats.damage", Msg.p("damage", ToolItems.trim(1 + t.damage())))));
        if (tool.ability() != null) {
            lore.add(Component.empty());
            lore.addAll(lines("tool-menu.stats.ability", Msg.mm("ability", module.items().abilityName(tool.ability()))));
            lore.addAll(lines("ability." + tool.ability() + ".description"));
        }
        return service.item(Material.COMPARATOR, text("tool-menu.stats.name"), lore);
    }

    private ItemStack perkItem(ToolData tool, int slot, int unlocked, List<Integer> slotLevels) {
        Optional<ToolPerk> perk = tool.perks().stream().filter(p -> slot >= p.slot() && slot <= p.lastSlot()).findFirst();
        boolean prestigeSlot = slot == ToolSlots.PRESTIGE_SLOT;
        if (perk.isPresent()) {
            return service.item(Material.ENCHANTED_BOOK, text("tool-menu.perk.filled.name", Msg.p("slot", slot),
                    Msg.p("perk", module.items().perkName(perk.get()))), lines("tool-menu.perk.filled.lore"));
        }
        if (slot <= unlocked) {
            return service.item(prestigeSlot ? Material.NETHER_STAR : Material.BOOK, viewer, ToolsModule.ID,
                    prestigeSlot ? "tool-menu.perk.prestige-empty" : "tool-menu.perk.empty", Msg.p("slot", slot));
        }
        if (prestigeSlot) {
            return service.item(Material.GRAY_DYE, viewer, ToolsModule.ID, "tool-menu.perk.prestige-locked",
                    Msg.p("max", module.curve().maxLevel()));
        }
        return service.item(Material.GRAY_STAINED_GLASS_PANE, viewer, ToolsModule.ID, "tool-menu.perk.locked",
                Msg.p("slot", slot), Msg.p("level", ToolSlots.unlockLevel(slot, slotLevels)), Msg.p("have", tool.level()));
    }

    private ItemStack historyItem(ToolData tool, ItemStack stack) {
        String creator = stack.getPersistentDataContainer().getOrDefault(ToolItems.CREATOR, PersistentDataType.STRING, "?");
        List<Component> lore = new ArrayList<>(lines("tool-menu.history.lore",
                Msg.p("blocks", Format.number(tool.stat(ToolData.STAT_BLOCKS))),
                Msg.p("logs", Format.number(tool.stat(ToolData.STAT_LOGS))),
                Msg.p("kills", Format.number(tool.stat(ToolData.STAT_KILLS))),
                Msg.p("xp", Format.number(tool.stat(ToolData.STAT_XP))),
                Msg.p("forged", Format.number(tool.stat(ToolData.STAT_FORGED))),
                Msg.p("prestige", tool.prestige()),
                Msg.p("creator", creator)));
        return service.item(Material.WRITABLE_BOOK, text("tool-menu.history.name"), lore);
    }

    private void prestigeButton(ToolData tool, ToolCurve curve) {
        ToolProgress.PrestigeCheck check = ToolProgress.canPrestige(tool, curve, module.config().seasonId,
                module.config().prestigePerSeason, module.config().prestigeEnabled);
        String state = check.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        ItemStack item = service.item(check == ToolProgress.PrestigeCheck.OK ? Material.NETHER_STAR : Material.FIREWORK_STAR,
                viewer, ToolsModule.ID, "tool-menu.prestige." + state, Msg.p("max", curve.maxLevel()), Msg.p("level", tool.level()));
        if (check != ToolProgress.PrestigeCheck.OK) {
            set(SLOT_PRESTIGE, item, click -> service.sound(viewer, "error"));
            return;
        }
        set(SLOT_PRESTIGE, item, click -> openChild(new ConfirmMenu(v -> text("prestige.confirm.crumb"), ToolItems.displayCopy(viewer.getInventory().getItem(
                Math.max(0, ToolTracker.find(viewer.getInventory(), toolId)))),
                v -> lines("prestige.confirm.details", Msg.p("max", curve.maxLevel())),
                p -> module.prestige(p, toolId), p -> { })));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ToolsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ToolsModule.ID, key, placeholders);
    }
}
