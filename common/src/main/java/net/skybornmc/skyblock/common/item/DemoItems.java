package net.skybornmc.skyblock.common.item;

import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Demo definitions that show the lore template for each special item (UI_STYLE §4.3). The real versions move to their
 * modules (tools → Phase 4, notes → Phase 3, tags → Phase 1, keys → Phase 10) and reuse these PDC keys.
 * Texts come from {@code messages/items.yml → demo.*}.
 */
public final class DemoItems {

    public static final NamespacedKey TOOL_LEVEL = ItemKeys.key("tool_level");
    public static final NamespacedKey TOOL_PROGRESS = ItemKeys.key("tool_progress");
    public static final NamespacedKey TOOL_BLOCKS = ItemKeys.key("tool_blocks");
    public static final NamespacedKey TOOL_PERKS_USED = ItemKeys.key("tool_perks_used");
    public static final NamespacedKey TOOL_PERKS_TOTAL = ItemKeys.key("tool_perks_total");
    public static final NamespacedKey NOTE_AMOUNT = ItemKeys.key("note_amount");
    public static final NamespacedKey NOTE_SIGNER = ItemKeys.key("note_signer");
    public static final NamespacedKey TAG_TEXT = ItemKeys.key("tag_text");

    private DemoItems() {
    }

    public static void register(ItemRegistry registry, Messages messages) {
        java.util.function.Function<String, String> t = key -> {
            String raw = messages.raw(ItemRegistry.MESSAGES, "demo." + key);
            return raw == null ? "[missing: items:demo." + key + "]" : raw;
        };

        registry.register(ItemDefinition.builder("tool_iron_pickaxe", Material.IRON_PICKAXE, ItemType.TOOL, Rarity.RARE)
                .name(t.apply("tool.name"))
                .subtitle(t.apply("tool.subtitle"))
                .description(t.apply("tool.description"))
                .unique().unbreakable()
                .action(t.apply("tool.action-1-input"), t.apply("tool.action-1-text"))
                .action(t.apply("tool.action-2-input"), t.apply("tool.action-2-text"))
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public void lore(ItemStack stack, ItemDefinition def, LoreBuilder lore) {
                        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
                        int level = pdc.getOrDefault(TOOL_LEVEL, PersistentDataType.INTEGER, 1);
                        double progress = pdc.getOrDefault(TOOL_PROGRESS, PersistentDataType.DOUBLE, 0.0);
                        long blocks = pdc.getOrDefault(TOOL_BLOCKS, PersistentDataType.LONG, 0L);
                        lore.level(level, progress);
                        lore.line(LoreBuilder.fill(t.apply("tool.blocks-mined"), Map.of("blocks", LoreBuilder.escape(Format.number(blocks)))));
                        lore.stat("pillar:mining", t.apply("tool.stat-speed"), "+" + (level / 2) + "%");
                        lore.stat("cur:tokens", t.apply("tool.stat-tokens"), "+" + (level / 5) + "%");
                        lore.perkSlots(pdc.getOrDefault(TOOL_PERKS_USED, PersistentDataType.INTEGER, 0),
                                pdc.getOrDefault(TOOL_PERKS_TOTAL, PersistentDataType.INTEGER, 1));
                    }
                })
                .build());

        registry.register(ItemDefinition.builder("money_note", Material.PAPER, ItemType.NOTE, Rarity.COMMON)
                .name(t.apply("note.name"))
                .description(t.apply("note.description"))
                .unique().glint()
                .action(t.apply("note.action-input"), t.apply("note.action-text"))
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition def) {
                        long amount = stack.getPersistentDataContainer().getOrDefault(NOTE_AMOUNT, PersistentDataType.LONG, 0L);
                        return LoreBuilder.fill(def.name(), Map.of("amount", LoreBuilder.escape(Format.money(amount))));
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition def, LoreBuilder lore) {
                        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
                        lore.line(LoreBuilder.fill(t.apply("note.signed-by"), Map.of(
                                "player", LoreBuilder.escape(pdc.getOrDefault(NOTE_SIGNER, PersistentDataType.STRING, "?")))));
                        lore.line(LoreBuilder.fill(t.apply("note.id"), Map.of(
                                "id", LoreBuilder.escape(ItemRegistry.uuidOf(stack).map(u -> u.toString().substring(0, 8)).orElse("?")))));
                    }
                })
                .build());

        registry.register(ItemDefinition.builder("tag_item", Material.NAME_TAG, ItemType.COSMETIC, Rarity.EPIC)
                .name(t.apply("tag.name"))
                .description(t.apply("tag.description"))
                .unique()
                .action(t.apply("tag.action-input"), t.apply("tag.action-text"))
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition def) {
                        return LoreBuilder.fill(def.name(), Map.of("tag", LoreBuilder.escape(tagOf(stack))));
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition def, LoreBuilder lore) {
                        lore.line(LoreBuilder.fill(t.apply("tag.preview"), Map.of("tag", LoreBuilder.escape(tagOf(stack)))));
                        lore.line(t.apply("tag.tradeable"));
                    }
                })
                .build());

        registry.register(ItemDefinition.builder("crate_key_rare", Material.TRIPWIRE_HOOK, ItemType.CRATE_KEY, Rarity.RARE)
                .name(t.apply("key.name"))
                .description(t.apply("key.description"))
                .glint().maxStack(64)
                .action(t.apply("key.action-input"), t.apply("key.action-text"))
                .build());
    }

    private static String tagOf(ItemStack stack) {
        return stack.getPersistentDataContainer().getOrDefault(TAG_TEXT, PersistentDataType.STRING, "[Phoenix]");
    }
}
