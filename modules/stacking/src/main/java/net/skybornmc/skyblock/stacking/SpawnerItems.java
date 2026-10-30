package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;

/**
 * Spawner items (owner S26, SK3–SK7): custom items {@code spawner_1}..{@code spawner_5} (one per tier, so the footer shows
 * the tier's rarity) with the mob type in the item's persistent data. Not unique: spawners of the same type stack in the
 * inventory. A plain vanilla spawner item never becomes a stacked spawner.
 */
final class SpawnerItems {

    static final String PREFIX = "spawner_";
    private static final Rarity[] RARITY = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY};

    private final StackingModule module;

    SpawnerItems(StackingModule module) {
        this.module = module;
    }

    static String id(int tier) {
        return PREFIX + tier;
    }

    private String raw(String key) {
        String raw = module.core().messages().raw(StackingModule.ID, key);
        return raw == null ? "<red>[missing: stacking:" + key + "]</red>" : raw;
    }

    void register() {
        for (int tier = 1; tier <= SpawnerTiers.TIERS; tier++) {
            String id = id(tier);
            if (module.core().items().get(id).isPresent()) {
                continue;
            }
            int t = tier;
            module.core().items().register(ItemDefinition.builder(id, Material.SPAWNER, ItemType.SPAWNER, RARITY[tier - 1])
                    .name(raw("spawner.item.name-unknown"))
                    .subtitle(LoreBuilder.fill(raw("spawner.item.subtitle"), Map.of("tier", String.valueOf(tier))))
                    .description(raw("spawner.item.description"))
                    .action(raw("spawner.item.action-place-input"), raw("spawner.item.action-place"))
                    .action(raw("spawner.item.action-menu-input"), raw("spawner.item.action-menu"))
                    .renderer(new ItemDefinition.Renderer() {
                        @Override
                        public String name(ItemStack stack, ItemDefinition definition) {
                            return typeOf(stack).map(type -> LoreBuilder.fill(raw("spawner.item.name"), Map.of(
                                    "rarity", RARITY[t - 1].id(), "mob", mobTag(type)))).orElse(definition.name());
                        }

                        @Override
                        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                            typeOf(stack).ifPresent(type -> {
                                lore.line(LoreBuilder.fill(raw("spawner.item.mob"), Map.of("mob", mobTag(type))));
                                lore.line(LoreBuilder.fill(raw("spawner.item.tier"), Map.of("tier", String.valueOf(t))));
                            });
                        }
                    })
                    .build());
        }
    }

    void unregister() {
        for (int tier = 1; tier <= SpawnerTiers.TIERS; tier++) {
            module.core().items().unregister(id(tier));
        }
    }

    /** The translated mob name as a MiniMessage tag (Bedrock gets it translated by Geyser). */
    static String mobTag(String type) {
        return "<lang:entity.minecraft." + SpawnerTiers.normalize(type).replaceAll("[^a-z0-9_]", "") + ">";
    }

    /** The mob type of a spawner item, empty for anything else. */
    static Optional<String> typeOf(ItemStack stack) {
        Optional<String> id = ItemRegistry.idOf(stack);
        if (id.isEmpty() || !id.get().startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getPersistentDataContainer().get(StackKeys.SPAWNER_TYPE, PersistentDataType.STRING))
                .map(SpawnerTiers::normalize);
    }

    /** New spawner items of {@code type} (1-64; callers split bigger amounts). Empty if the type is not allowed. */
    Optional<ItemStack> create(String type, int amount, String source) {
        String id = SpawnerTiers.normalize(type);
        return module.tiers().tier(id).map(tier -> module.core().items().create(id(tier), Math.max(1, Math.min(64, amount)), source,
                pdc -> pdc.set(StackKeys.SPAWNER_TYPE, PersistentDataType.STRING, id)));
    }
}
