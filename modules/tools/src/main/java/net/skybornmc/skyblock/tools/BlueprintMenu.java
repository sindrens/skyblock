package net.skybornmc.skyblock.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Forge → Blueprints: every blueprint item in your inventory as a button (no dragging, Bedrock-safe) with what it makes,
 * its ability and the recipe; click → confirm → craft (one blueprint, money and materials for a new special tool).
 */
final class BlueprintMenu extends PagedMenu<Map.Entry<UUID, String>> {

    private final ToolsModule module;

    BlueprintMenu(ToolsModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return ToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "blueprint.menu.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    @Override
    protected String emptyKey() {
        return "blueprint.menu.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("forge.menu.title"), text("blueprint.menu.title"));
    }

    @Override
    protected List<Map.Entry<UUID, String>> entries() {
        return new ArrayList<>(Forge.blueprints(viewer.getInventory()).entrySet());
    }

    @Override
    protected MenuButton entry(Map.Entry<UUID, String> value) {
        int slot = Forge.blueprintSlot(viewer.getInventory(), value.getKey());
        ItemStack shown = slot < 0 ? null : viewer.getInventory().getItem(slot);
        Optional<ToolCatalog.BlueprintDef> def = module.catalog().blueprint(value.getValue());
        if (shown == null || def.isEmpty()) {
            return MenuButton.display(service.item(org.bukkit.Material.PAPER, viewer, ToolsModule.ID, "blueprint.menu.unknown"));
        }
        ToolCatalog.BlueprintDef b = def.get();
        Forge forge = module.forge();
        Map<String, Integer> have = forge.count(viewer, b.recipe().materials().keySet());
        List<Component> lore = new ArrayList<>(lines("blueprint.menu.makes", Msg.mm("tool", module.items().rawOr("blueprint." + b.id() + ".tool-name", b.id())),
                Msg.p("tier", b.tier()), Msg.mm("ability", module.items().abilityName(b.ability()))));
        lore.addAll(lines("ability." + b.ability() + ".description"));
        lore.add(Component.empty());
        b.recipe().materials().forEach((id, need) -> {
            int got = have.getOrDefault(id, 0);
            lore.addAll(lines(got >= need ? "forge.tier.material-ok" : "forge.tier.material",
                    Msg.p("amount", net.skybornmc.skyblock.common.util.Format.number(need)), Msg.p("item", Forge.materialName(id)),
                    Msg.p("have", net.skybornmc.skyblock.common.util.Format.number(Math.min(got, need)))));
        });
        if (b.recipe().money() > 0) {
            lore.addAll(lines(forge.money(viewer) >= b.recipe().money() ? "forge.tier.money-ok" : "forge.tier.money",
                    Msg.p("money", forge.formatMoney(b.recipe().money()))));
        }
        lore.add(Component.empty());
        lore.addAll(lines("blueprint.menu.click"));
        ItemStack icon = service.item(ToolItems.displayCopy(shown), text("blueprint.menu.name", Msg.mm("blueprint",
                "<rarity:" + b.rarity().id() + ">" + module.items().blueprintName(b.id()))), lore);
        return MenuButton.of(icon, click -> openChild(new ConfirmMenu(v -> text("blueprint.confirm.crumb"), ToolItems.displayCopy(shown),
                v -> lines("blueprint.confirm.details", Msg.mm("tool", module.items().rawOr("blueprint." + b.id() + ".tool-name", b.id())),
                        Msg.p("money", forge.formatMoney(b.recipe().money())), Msg.p("materials", forge.describe(b.recipe().materials()))),
                p -> forge.craftBlueprint(p, value.getKey()), p -> { })));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ToolsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ToolsModule.ID, key, placeholders);
    }
}
