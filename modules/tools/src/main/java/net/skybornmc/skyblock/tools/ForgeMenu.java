package net.skybornmc.skyblock.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /forge} (Blacksmith): tabs per tool type on top, the type's tiers in the middle (owned ✔ / craftable /
 * locked ✘ with progress: tool, skill, materials, money), Starter kit (Quartermaster) and Blueprints at the bottom.
 * <pre>
 *  R O [⛏][🪓][⚔][hoe][rod] O R
 *  B  t1 t2 t3 t4 t5 t6 t7  B
 *  B  t8 t9 ...             B
 *  ...
 *  ← [kit] ? X help [bp]
 * </pre>
 */
final class ForgeMenu extends Menu {

    static final int[] TAB_SLOTS = {2, 3, 4, 5, 6};
    static final int[] TIER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int SLOT_STARTER = 47;
    static final int SLOT_BLUEPRINTS = 51;
    static final Map<ToolKind, Material> ICONS = Map.of(ToolKind.PICKAXE, Material.IRON_PICKAXE, ToolKind.AXE, Material.IRON_AXE,
            ToolKind.SWORD, Material.IRON_SWORD, ToolKind.HOE, Material.IRON_HOE, ToolKind.ROD, Material.FISHING_ROD);

    private final ToolsModule module;
    private ToolKind kind;

    ForgeMenu(ToolsModule module, ToolKind kind) {
        this.module = module;
        this.kind = module.forgeKind(kind);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("forge.menu.title"), module.items().kindPlural(viewer, kind));
    }

    @Override
    public String messageModule() {
        return ToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "forge.menu.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    @Override
    public int tickInterval() {
        return 40; // money and materials change while it is open
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        if (!module.forgeOpen(kind)) {
            kind = module.forgeKind(kind); // farming was switched off while the menu was open
        }
        List<ToolKind> kinds = new ArrayList<>(module.forgeKinds());
        for (int i = 0; i < kinds.size() && i < TAB_SLOTS.length; i++) {
            ToolKind k = kinds.get(i);
            boolean selected = k == kind;
            ItemStack tab = service.item(new ItemStack(ICONS.get(k)), text(selected ? "forge.menu.tab-selected" : "forge.menu.tab",
                    Msg.c("kind", module.items().kindPlural(viewer, k))), lines(selected ? "forge.menu.tab-selected-lore" : "forge.menu.tab-lore"));
            if (selected) {
                tab.editMeta(m -> m.setEnchantmentGlintOverride(true));
            }
            set(TAB_SLOTS[i], tab, click -> {
                if (kind != k) {
                    kind = k;
                    service.open(viewer, this);
                }
            });
        }
        List<ToolCatalog.TierDef> tiers = module.catalog().tiers(kind);
        for (int i = 0; i < tiers.size() && i < TIER_SLOTS.length; i++) {
            ToolCatalog.TierDef def = tiers.get(i);
            set(TIER_SLOTS[i], tierItem(def), click -> clicked(def));
        }
        set(SLOT_STARTER, service.item(Material.CHEST, viewer, ToolsModule.ID, "forge.menu.starter",
                Msg.p("hours", module.config().starterCooldownHours)), click -> {
            click.player().closeInventory();
            module.forge().claimStarter(click.player());
        });
        set(SLOT_BLUEPRINTS, service.item(Material.PAPER, viewer, ToolsModule.ID, "forge.menu.blueprints",
                Msg.p("count", Forge.blueprints(viewer.getInventory()).size())), click -> openChild(new BlueprintMenu(module)));
    }

    private void clicked(ToolCatalog.TierDef def) {
        Forge forge = module.forge();
        Forge.State state = forge.state(viewer, def);
        if (def.tier() == 1 && state != Forge.State.OWNED) {
            viewer.closeInventory();
            forge.claimStarter(viewer);
            return;
        }
        if (state != Forge.State.CRAFTABLE) {
            service.sound(viewer, "error");
            module.msg().send(viewer, "forge.state." + key(state));
            return;
        }
        Optional<Map.Entry<Integer, ToolData>> base = Forge.baseTool(viewer.getInventory(), def.kind(), def.tier());
        if (base.isEmpty()) {
            return;
        }
        ToolData tool = base.get().getValue();
        ItemStack shown = viewer.getInventory().getItem(base.get().getKey());
        String from = module.items().displayName(tool, shown);
        openChild(new ConfirmMenu(v -> text("forge.confirm.crumb"), ToolItems.displayCopy(shown),
                v -> lines("forge.confirm.details", Msg.mm("tool", from), Msg.mm("to", module.items().tierName(def.kind(), def.tier())),
                        Msg.p("level", tool.level()), Msg.p("money", forge.formatMoney(def.recipe().money())),
                        Msg.p("materials", forge.describe(def.recipe().materials()))),
                p -> forge.forge(p, def.kind(), def.tier()), p -> { }));
    }

    static String key(Forge.State state) {
        return state.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private ItemStack tierItem(ToolCatalog.TierDef def) {
        Forge forge = module.forge();
        Forge.State state = forge.state(viewer, def);
        List<Component> lore = new ArrayList<>(lines("forge.tier.subtitle", Msg.p("tier", def.tier()), Msg.c("kind", module.items().kindComponent(viewer, def.kind()))));
        lore.add(Component.empty());
        if (def.kind() == ToolKind.SWORD) {
            lore.addAll(lines("forge.tier.damage", Msg.p("damage", ToolItems.trim(1 + def.damage()))));
        } else if (def.efficiency() > 0) {
            lore.addAll(lines("forge.tier.efficiency", Msg.p("efficiency", ToolItems.trim(def.efficiency()))));
        }
        if (def.kind() == ToolKind.HOE && def.tier() > 1) {
            lore.addAll(lines("forge.tier.crop-drops", Msg.p("percent", ToolItems.trim((def.tier() - 1) * module.config().fortunePerTier))));
        }
        if (def.tier() == 1) {
            lore.addAll(lines("forge.tier.starter"));
        } else {
            boolean hasTool = Forge.baseTool(viewer.getInventory(), def.kind(), def.tier()).isPresent() || state == Forge.State.OWNED;
            lore.addAll(lines(hasTool ? "forge.tier.needs-tool-ok" : "forge.tier.needs-tool",
                    Msg.mm("tool", module.items().tierName(def.kind(), def.tier() - 1))));
            int skill = forge.skillLevel(viewer, def.kind());
            if (def.skillLevel() > 0) {
                lore.addAll(lines(skill >= def.skillLevel() ? "forge.tier.skill-ok" : "forge.tier.skill",
                        Msg.c("skill", module.msg().component(viewer, "skill." + def.kind().skill().id())),
                        Msg.p("level", def.skillLevel()), Msg.p("have", skill == Integer.MAX_VALUE ? def.skillLevel() : skill)));
            }
            Map<String, Integer> have = forge.count(viewer, def.recipe().materials().keySet());
            def.recipe().materials().forEach((id, need) -> {
                int got = have.getOrDefault(id, 0);
                lore.addAll(lines(got >= need ? "forge.tier.material-ok" : "forge.tier.material",
                        Msg.p("amount", net.skybornmc.skyblock.common.util.Format.number(need)), Msg.p("item", Forge.materialName(id)),
                        Msg.p("have", net.skybornmc.skyblock.common.util.Format.number(Math.min(got, need)))));
            });
            if (def.recipe().money() > 0) {
                lore.addAll(lines(forge.money(viewer) >= def.recipe().money() ? "forge.tier.money-ok" : "forge.tier.money",
                        Msg.p("money", forge.formatMoney(def.recipe().money()))));
            }
            lore.add(Component.empty());
            lore.addAll(lines("forge.tier.keeps"));
        }
        boolean starter = def.tier() == 1 && state != Forge.State.OWNED;
        lore.add(Component.empty());
        lore.addAll(lines(starter ? "forge.tier.state.starter" : "forge.tier.state." + key(state)));
        ItemStack icon = new ItemStack(def.material());
        if (def.model() != null) {
            net.kyori.adventure.key.Key model = org.bukkit.NamespacedKey.fromString(def.model());
            if (model != null) {
                icon.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, model);
            }
        }
        ItemStack item = service.item(icon, text("forge.tier.name",
                Msg.mm("tool", "<rarity:" + def.rarity().id() + ">" + module.items().tierName(def.kind(), def.tier())),
                Msg.c("mark", text(starter ? "forge.tier.mark.starter" : "forge.tier.mark." + key(state)))), lore);
        if (state == Forge.State.OWNED || state == Forge.State.CRAFTABLE) {
            item.editMeta(m -> m.setEnchantmentGlintOverride(state == Forge.State.CRAFTABLE));
        }
        return item;
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ToolsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ToolsModule.ID, key, placeholders);
    }
}
