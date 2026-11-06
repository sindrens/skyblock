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
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Owner 4 Nov 2026: the island tool items are HANDLES. The item carries only the tool kind and the island id; level, XP,
 * perks and prestige live in the island's data ({@link ToolStore.Prog}), and the name and lore are rendered from it (and
 * re-rendered whenever it changes). A handle of another island stops working and is removed. Diamond, unbreakable, bound
 * to the holder's profile (never traded).
 */
final class ToolItems {

    static final String PREFIX = "island_";
    static final NamespacedKey KIND = ItemKeys.key("island_tool_kind");
    static final NamespacedKey ISLAND = ItemKeys.key("island_tool_island");

    private final IslandToolsModule m;

    ToolItems(IslandToolsModule m) {
        this.m = m;
    }

    void register() {
        for (Kind kind : Kind.values()) {
            m.core().items().register(ItemDefinition.builder(PREFIX + kind.id(), Material.matchMaterial(ToolRules.material(kind)),
                            ItemType.TOOL, Rarity.EPIC)
                    .name(m.raw("item.name." + kind.id()))
                    .description(m.raw("item.description." + kind.id()))
                    .unbreakable()
                    .renderer(new ItemDefinition.Renderer() {
                        @Override
                        public String name(ItemStack stack, ItemDefinition def) {
                            ToolStore.Prog prog = m.prog(islandOf(stack), kind);
                            String stars = prog == null ? "" : ToolRules.stars(prog.prestige(), m.config().maxStars);
                            return m.raw(stars.isEmpty() ? "item.plain-name" : "item.star-name").replace("{tool}", m.raw("item.name." + kind.id()))
                                    .replace("{stars}", stars).replace("{rarity}", rarity(stack, def).id());
                        }

                        @Override
                        public void lore(ItemStack stack, ItemDefinition def, LoreBuilder lore) {
                            renderLore(kind, m.prog(islandOf(stack), kind), lore);
                        }

                        @Override
                        public Rarity rarity(ItemStack stack, ItemDefinition def) {
                            ToolStore.Prog prog = m.prog(islandOf(stack), kind);
                            return prog != null && prog.prestige() > 0 ? Rarity.LEGENDARY : Rarity.EPIC;
                        }
                    })
                    .build());
        }
    }

    /**
     * Owner 4 Nov 2026 mockup: bold name in the rarity colour (stars after it), muted subtitle "Tool · Prestige ★★",
     * one short description, the level line, "| Stats", "| Perks" (bought perks and the next locked one), the action
     * line and the rarity footer (added by the item registry).
     */
    void renderLore(Kind kind, ToolStore.Prog prog, LoreBuilder lore) {
        if (prog == null) {
            lore.subtitle(m.raw("item.subtitle"));
            lore.line(m.raw("item.loading"));
            lore.action(m.raw("item.action-input"), m.raw("item.action"));
            return;
        }
        String stars = ToolRules.stars(prog.prestige(), m.config().maxStars);
        lore.subtitle(stars.isEmpty() ? m.raw("item.subtitle") : m.raw("item.subtitle-prestige").replace("{stars}", stars));
        ToolRules.Level lv = m.level(prog);
        lore.level(lv.level(), lv.progress());
        IslandToolsConfig c = m.config();
        if (kind == Kind.HOE && c.speedLevel > 0) {
            int speed = c.speedLevel + (c.swiftnessPrestige > 0 && prog.prestige() >= c.swiftnessPrestige ? 1 : 0);
            lore.stat("", m.raw("item.stat.speed"), IslandToolsModule.roman(speed));
        }
        for (Upgrades.Def u : m.upgrades().of(kind)) {
            int n = prog.level(u.id());
            switch (u.effect()) {
                case ENCHANT -> lore.stat("", m.raw("upgrade." + u.id() + ".name"), n == 0 ? "-" : IslandToolsModule.roman(n));
                case SHARDS -> lore.stat("", m.raw("upgrade." + u.id() + ".name"), "+" + ToolMenu.pct(n * c.shardFinderPerLevel));
                case DROPS -> lore.stat("", m.raw("upgrade." + u.id() + ".name"), "+" + ToolMenu.pct(n * c.dropsPerLevel));
                default -> { }
            }
        }
        LoreBuilder.Section perks = lore.section(m.raw("item.perks-header"));
        int shown = 0;
        Upgrades.Def nextLocked = null;
        for (Upgrades.Def u : m.upgrades().of(kind)) {
            if (u.effect() == Upgrades.Effect.ENCHANT || u.effect() == Upgrades.Effect.SHARDS || u.effect() == Upgrades.Effect.DROPS) {
                continue; // already under Stats
            }
            int n = prog.level(u.id());
            if (n > 0 && shown < 5) {
                perks.rowMini(m.raw("upgrade." + u.id() + ".name"), m.levelText(u, n));
                shown++;
            } else if (n == 0 && lv.level() < u.unlock() && (nextLocked == null || u.unlock() < nextLocked.unlock())) {
                nextLocked = u;
            }
        }
        if (nextLocked != null) {
            perks.text(m.raw("item.perk-locked").replace("{perk}", m.raw("upgrade." + nextLocked.id() + ".name"))
                    .replace("{level}", String.valueOf(nextLocked.unlock())));
        }
        perks.done();
        lore.hint(m.raw("item.hint"));
        lore.action(m.raw("item.action-input"), m.raw("item.action"));
    }

    void unregister() {
        for (Kind kind : Kind.values()) {
            m.core().items().unregister(PREFIX + kind.id());
        }
    }

    /** A fresh handle of {@code island}'s {@code kind} for {@code profile} (enchantments from the island's perks). */
    ItemStack create(Kind kind, UUID island, UUID profile) {
        ItemStack stack = m.core().items().create(PREFIX + kind.id(), 1, "island_tool", pdc -> {
            pdc.set(KIND, PersistentDataType.STRING, kind.id());
            pdc.set(ISLAND, PersistentDataType.STRING, island.toString());
        });
        applyEnchants(stack, kind, m.prog(island, kind));
        BoundItems.mark(stack, profile);
        m.core().items().retexture(stack, "item/island_" + kind.id() + "_diamond");
        m.core().items().render(stack);
        return stack;
    }

    /** Puts the island's enchant-like perks (Efficiency, Fortune ...) on the item as vanilla enchantments. */
    void applyEnchants(ItemStack stack, Kind kind, ToolStore.Prog prog) {
        for (Enchantment e : java.util.List.copyOf(stack.getEnchantments().keySet())) {
            stack.removeEnchantment(e);
        }
        if (prog == null) {
            return;
        }
        for (Upgrades.Def u : m.upgrades().of(kind)) {
            int lv = prog.level(u.id());
            if (u.effect() == Upgrades.Effect.ENCHANT && lv > 0) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(u.arg()));
                if (e != null) {
                    stack.addUnsafeEnchantment(e, lv);
                }
            }
        }
    }

    // ------------------------------------------------------------------ reading

    /** Any island tool item, also the old ones (axe, sword, items from before the handles). */
    static boolean isTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta() && stack.getPersistentDataContainer().has(KIND, PersistentDataType.STRING);
    }

    /** The raw kind id on the item ({@code hoe}, {@code pickaxe}, or an old {@code axe}/{@code sword}), or null. */
    static String rawKind(ItemStack stack) {
        return isTool(stack) ? stack.getPersistentDataContainer().get(KIND, PersistentDataType.STRING) : null;
    }

    static Kind kindOf(ItemStack stack) {
        String raw = rawKind(stack);
        return raw == null ? null : Kind.of(raw).orElse(null);
    }

    /** The island of a handle, or null (an old item from before the handles, or a broken tag). */
    static UUID islandOf(ItemStack stack) {
        if (!isTool(stack)) {
            return null;
        }
        String raw = stack.getPersistentDataContainer().get(ISLAND, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
