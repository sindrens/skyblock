package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

/**
 * The island tool items: one definition per kind ({@code island_pickaxe} ...), material by tier, vanilla enchantments from
 * the ENCHANT upgrades, unbreakable, bound to the profile (T6: the starter island tools never trade). The item carries
 * the row's item id: only that copy works (a new copy from the menu makes older ones useless).
 */
final class ToolItems {

    static final String PREFIX = "island_";
    static final NamespacedKey KIND = ItemKeys.key("island_tool_kind");
    static final NamespacedKey COPY = ItemKeys.key("island_tool_copy");
    static final NamespacedKey OWNER = ItemKeys.key("island_tool_owner");
    static final NamespacedKey TIER = ItemKeys.key("island_tool_tier");
    static final NamespacedKey LEVELS = ItemKeys.key("island_tool_levels");

    private final IslandToolsModule m;

    ToolItems(IslandToolsModule m) {
        this.m = m;
    }

    void register() {
        for (Kind kind : Kind.values()) {
            m.core().items().register(ItemDefinition.builder(PREFIX + kind.id(), Material.matchMaterial(ToolRules.material(kind, 0)),
                            ItemType.TOOL, Rarity.COMMON)
                    .name(m.raw("item.name." + kind.id()))
                    .description(m.raw("item.description." + kind.id()))
                    .unbreakable()
                    .renderer(new ItemDefinition.Renderer() {
                        @Override
                        public String name(ItemStack stack, ItemDefinition def) {
                            int tier = tierOf(stack);
                            return m.raw("item.tiered-name").replace("{tier}", m.raw("tier." + ToolRules.TIERS.get(tier)))
                                    .replace("{tool}", m.raw("item.name." + kind.id()));
                        }

                        @Override
                        public void lore(ItemStack stack, ItemDefinition def, LoreBuilder lore) {
                            Map<String, Integer> levels = Upgrades.decode(stack.getPersistentDataContainer().get(LEVELS, PersistentDataType.STRING));
                            for (Upgrades.Def u : m.upgrades().of(kind)) {
                                int lv = levels.getOrDefault(u.id(), 0);
                                if (lv > 0) {
                                    lore.line(m.raw("item.upgrade-line").replace("{name}", m.raw("upgrade." + u.id() + ".name"))
                                            .replace("{level}", String.valueOf(lv)).replace("{max}", String.valueOf(u.max())));
                                }
                            }
                            lore.line(m.raw("item.only-island"));
                            lore.action(m.raw("item.action-input"), m.raw("item.action"));
                        }

                        @Override
                        public Rarity rarity(ItemStack stack, ItemDefinition def) {
                            return switch (tierOf(stack)) {
                                case 0, 1 -> Rarity.COMMON;
                                case 2 -> Rarity.UNCOMMON;
                                case 3 -> Rarity.RARE;
                                case 4 -> Rarity.EPIC;
                                default -> Rarity.LEGENDARY;
                            };
                        }
                    })
                    .build());
        }
    }

    void unregister() {
        for (Kind kind : Kind.values()) {
            m.core().items().unregister(PREFIX + kind.id());
        }
    }

    /** A fresh stack for a record (tier material, enchantments, bound, texture). */
    ItemStack create(ToolStore.Rec r) {
        ItemStack stack = m.core().items().create(PREFIX + r.kind().id(), 1, "island_tool", pdc -> write(pdc, r));
        Material mat = Material.matchMaterial(ToolRules.material(r.kind(), r.tier()));
        if (mat != null && stack.getType() != mat) {
            stack = stack.withType(mat);
        }
        for (Upgrades.Def u : m.upgrades().of(r.kind())) {
            int lv = r.level(u.id());
            if (u.effect() == Upgrades.Effect.ENCHANT && lv > 0) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(u.arg()));
                if (e != null) {
                    stack.addUnsafeEnchantment(e, lv);
                }
            }
        }
        BoundItems.mark(stack, r.profile());
        m.core().items().retexture(stack, "item/island_" + r.kind().id() + "_" + ToolRules.TIERS.get(r.tier()));
        m.core().items().render(stack);
        return stack;
    }

    private static void write(PersistentDataContainer pdc, ToolStore.Rec r) {
        pdc.set(KIND, PersistentDataType.STRING, r.kind().id());
        pdc.set(COPY, PersistentDataType.STRING, r.itemId());
        pdc.set(OWNER, PersistentDataType.STRING, r.profile().toString());
        pdc.set(TIER, PersistentDataType.INTEGER, r.tier());
        pdc.set(LEVELS, PersistentDataType.STRING, r.upgrades());
    }

    // ------------------------------------------------------------------ reading

    static boolean isTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta() && stack.getPersistentDataContainer().has(KIND, PersistentDataType.STRING);
    }

    static Kind kindOf(ItemStack stack) {
        return isTool(stack) ? Kind.of(stack.getPersistentDataContainer().get(KIND, PersistentDataType.STRING)).orElse(null) : null;
    }

    static String copyOf(ItemStack stack) {
        return isTool(stack) ? stack.getPersistentDataContainer().get(COPY, PersistentDataType.STRING) : null;
    }

    static UUID ownerOf(ItemStack stack) {
        if (!isTool(stack)) {
            return null;
        }
        try {
            return UUID.fromString(stack.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static int tierOf(ItemStack stack) {
        Integer t = isTool(stack) ? stack.getPersistentDataContainer().get(TIER, PersistentDataType.INTEGER) : null;
        return t == null ? 0 : Math.max(0, Math.min(ToolRules.TIERS.size() - 1, t));
    }
}
