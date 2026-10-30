package net.skybornmc.skyblock.tools;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.key.Key;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The tool and blueprint items: custom item definitions, reading/writing the tool data on the item (persistent data),
 * name and lore (through the item framework), item model and attribute modifiers (break speed, damage: real item
 * attributes, so they work on Java and Bedrock without client tricks).
 *
 * <p>Item ids: {@code tool_<type>} for forged and blueprint tools, {@code tool_starter_<type>} for starter kit pieces
 * (they also carry {@code phoenixskyblock:starter_kit}, which the auction house refuses), {@code tool_blueprint}.</p>
 */
final class ToolItems {

    static final String PREFIX = "tool_";
    static final String STARTER_PREFIX = "tool_starter_";
    static final String BLUEPRINT_ITEM = "tool_blueprint";

    static final NamespacedKey KIND = ItemKeys.key("tool_kind");
    static final NamespacedKey TIER = ItemKeys.key("tool_tier");
    static final NamespacedKey LEVEL = ItemKeys.key("tool_level");
    static final NamespacedKey XP = ItemKeys.key("tool_xp");
    static final NamespacedKey PRESTIGE = ItemKeys.key("tool_prestige");
    static final NamespacedKey PRESTIGE_SEASON = ItemKeys.key("tool_prestige_season");
    static final NamespacedKey PERKS = ItemKeys.key("tool_perks");
    static final NamespacedKey SKIN = ItemKeys.key("tool_skin");
    static final NamespacedKey ABILITY = ItemKeys.key("tool_ability");
    static final NamespacedKey SPECIAL = ItemKeys.key("tool_special");
    static final NamespacedKey STATS = ItemKeys.key("tool_stats");
    static final NamespacedKey CREATOR = ItemKeys.key("tool_creator");
    /** Starter kit flag: the auction module refuses items with this key ({@code auction.yml → items.blocked-data-keys}). */
    static final NamespacedKey STARTER = ItemKeys.key("starter_kit");
    static final NamespacedKey BLUEPRINT = ItemKeys.key("blueprint_id");

    private static final NamespacedKey MOD_EFFICIENCY = ItemKeys.key("tool_efficiency");
    private static final NamespacedKey MOD_SPEED = ItemKeys.key("tool_speed");
    private static final NamespacedKey MOD_DAMAGE = ItemKeys.key("tool_damage");
    private static final NamespacedKey MOD_ATTACK_SPEED = ItemKeys.key("tool_attack_speed");

    static final String ABILITY_SWIFT = "swift";
    static final String ABILITY_SCHOLAR = "scholar";
    static final String ABILITY_AUTO_SMELT = "auto_smelt";
    static final String ABILITY_LIFESTEAL = "lifesteal";

    private final ToolsModule module;
    private final List<String> registered = new ArrayList<>();
    /** Perk names from the enchants module ({@code ToolService#perkNames}); null = raw id + level. */
    private volatile Function<ToolPerk, String> perkNamer;

    ToolItems(ToolsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ definitions

    void register() {
        for (ToolKind kind : ToolKind.values()) {
            define(PREFIX + kind.id(), kind);
            define(STARTER_PREFIX + kind.id(), kind);
        }
        ItemDefinition blueprint = ItemDefinition.builder(BLUEPRINT_ITEM, Material.PAPER, ItemType.MATERIAL, Rarity.RARE)
                .name(raw("blueprint-item.name-unknown"))
                .subtitle(raw("blueprint-item.subtitle"))
                .description(raw("blueprint-item.description"))
                .action(raw("blueprint-item.action-input"), raw("blueprint-item.action-text"))
                .unique().glint()
                .renderer(new BlueprintRenderer())
                .build();
        module.core.items().register(blueprint);
        registered.add(BLUEPRINT_ITEM);
    }

    void unregister() {
        registered.forEach(module.core.items()::unregister);
        registered.clear();
    }

    private void define(String id, ToolKind kind) {
        Material material = module.catalog().tier(kind, 1).map(ToolCatalog.TierDef::material).orElse(Material.WOODEN_PICKAXE);
        module.core.items().register(ItemDefinition.builder(id, material, ItemType.TOOL, Rarity.COMMON)
                .name(raw("item.name-unknown"))
                .unique().unbreakable()
                .renderer(new ToolRenderer())
                .build());
        registered.add(id);
    }

    void perkNamer(Function<ToolPerk, String> namer) {
        this.perkNamer = namer;
    }

    // ------------------------------------------------------------------ reading and writing

    static boolean isToolId(String id) {
        return id != null && id.startsWith(PREFIX) && !id.equals(BLUEPRINT_ITEM);
    }

    /** The tool data on a custom tool item, or empty. */
    static Optional<ToolData> read(ItemStack stack) {
        Optional<String> id = ItemRegistry.idOf(stack);
        if (id.isEmpty() || !isToolId(id.get())) {
            return Optional.empty();
        }
        Optional<UUID> uuid = ItemRegistry.uuidOf(stack);
        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
        Optional<ToolKind> kind = ToolKind.byId(pdc.get(KIND, PersistentDataType.STRING));
        if (uuid.isEmpty() || kind.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ToolData(uuid.get(), kind.get(),
                pdc.getOrDefault(TIER, PersistentDataType.INTEGER, 1),
                pdc.getOrDefault(LEVEL, PersistentDataType.INTEGER, 1),
                pdc.getOrDefault(XP, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(PRESTIGE, PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(PRESTIGE_SEASON, PersistentDataType.INTEGER, 0),
                ToolCodec.perks(pdc.get(PERKS, PersistentDataType.STRING)),
                pdc.get(SKIN, PersistentDataType.STRING),
                pdc.get(ABILITY, PersistentDataType.STRING),
                pdc.has(STARTER),
                ToolCodec.stats(pdc.get(STATS, PersistentDataType.STRING))));
    }

    /** Writes the data fields (no render). Cheap: call it on every XP gain. */
    static void writeProgress(ItemStack stack, ToolData data) {
        stack.editPersistentDataContainer(pdc -> {
            pdc.set(LEVEL, PersistentDataType.INTEGER, data.level());
            pdc.set(XP, PersistentDataType.DOUBLE, data.xp());
            pdc.set(STATS, PersistentDataType.STRING, ToolCodec.stats(data.stats()));
        });
    }

    static void writeAll(PersistentDataContainer pdc, ToolData data) {
        pdc.set(KIND, PersistentDataType.STRING, data.kind().id());
        pdc.set(TIER, PersistentDataType.INTEGER, data.tier());
        pdc.set(LEVEL, PersistentDataType.INTEGER, data.level());
        pdc.set(XP, PersistentDataType.DOUBLE, data.xp());
        pdc.set(PRESTIGE, PersistentDataType.INTEGER, data.prestige());
        pdc.set(PRESTIGE_SEASON, PersistentDataType.INTEGER, data.prestigeSeason());
        pdc.set(PERKS, PersistentDataType.STRING, ToolCodec.perks(data.perks()));
        pdc.set(STATS, PersistentDataType.STRING, ToolCodec.stats(data.stats()));
        setOrRemove(pdc, SKIN, data.skin());
        setOrRemove(pdc, ABILITY, data.ability());
        if (data.starter()) {
            pdc.set(STARTER, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(STARTER);
        }
    }

    private static void setOrRemove(PersistentDataContainer pdc, NamespacedKey key, String value) {
        if (value == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, value);
        }
    }

    /** A new tool item. */
    ItemStack create(ToolData data, String source, String creator, String special) {
        String id = (data.starter() ? STARTER_PREFIX : PREFIX) + data.kind().id();
        ItemStack stack = module.core.items().create(id, 1, source, pdc -> {
            pdc.set(ItemKeys.UUID, PersistentDataType.STRING, data.id().toString());
            writeAll(pdc, data);
            if (creator != null) {
                pdc.set(CREATOR, PersistentDataType.STRING, creator);
            }
            if (special != null) {
                pdc.set(SPECIAL, PersistentDataType.STRING, special);
            }
        });
        return apply(stack, data);
    }

    /**
     * Writes all data, fixes the item id (starter or not), material, model, attributes and re-renders. Returns the stack
     * to put back into the inventory (a new one when the material changed, e.g. after a Forge upgrade).
     */
    ItemStack apply(ItemStack stack, ToolData data) {
        stack.editPersistentDataContainer(pdc -> {
            writeAll(pdc, data);
            pdc.set(ItemKeys.ID, PersistentDataType.STRING, (data.starter() ? STARTER_PREFIX : PREFIX) + data.kind().id());
        });
        Optional<ToolCatalog.TierDef> tier = module.catalog().tier(data.kind(), data.tier());
        ItemStack out = stack;
        if (tier.isPresent() && stack.getType() != tier.get().material()) {
            out = stack.withType(tier.get().material());
        }
        String model = module.catalog().skin(data.skin()).map(s -> s.modelFor(data.kind())).orElse(null);
        if (model == null) {
            model = tier.map(ToolCatalog.TierDef::model).orElse(null);
        }
        Key key = model == null ? null : NamespacedKey.fromString(model);
        if (key != null) {
            out.setData(DataComponentTypes.ITEM_MODEL, key);
        } else {
            out.resetData(DataComponentTypes.ITEM_MODEL);
        }
        applyAttributes(out, data, tier.orElse(null));
        module.core.items().render(out);
        return out;
    }

    /** Level-up: new attributes (speed) and lore, same material. */
    void refresh(ItemStack stack, ToolData data) {
        applyAttributes(stack, data, module.catalog().tier(data.kind(), data.tier()).orElse(null));
        module.core.items().render(stack);
    }

    private void applyAttributes(ItemStack stack, ToolData data, ToolCatalog.TierDef tier) {
        if (tier == null || data.kind() == ToolKind.ROD) {
            return;
        }
        ItemAttributeModifiers.Builder mods = ItemAttributeModifiers.itemAttributes();
        EquipmentSlotGroup hand = EquipmentSlotGroup.MAINHAND;
        if (data.kind() != ToolKind.SWORD) {
            if (tier.efficiency() > 0) {
                mods.addModifier(Attribute.MINING_EFFICIENCY, new AttributeModifier(MOD_EFFICIENCY, tier.efficiency(),
                        AttributeModifier.Operation.ADD_NUMBER, hand), hand);
            }
            double speed = speedBonus(data);
            if (speed > 0) {
                mods.addModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(MOD_SPEED, speed,
                        AttributeModifier.Operation.ADD_NUMBER, hand), hand);
            }
        }
        if (tier.damage() != 0) {
            mods.addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(MOD_DAMAGE, tier.damage(),
                    AttributeModifier.Operation.ADD_NUMBER, hand), hand);
        }
        if (tier.attackSpeed() != 0) {
            mods.addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(MOD_ATTACK_SPEED, tier.attackSpeed(),
                    AttributeModifier.Operation.ADD_NUMBER, hand), hand);
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, mods.build());
    }

    /**
     * Break speed bonus as a fraction (0.12 = +12%): tool level plus the Swift ability plus the speed family of the
     * {@link net.skybornmc.skyblock.common.bonus.BonusService} for this tool (enchants such as Swiftness, capped there).
     */
    double speedBonus(ToolData data) {
        double bonus = data.level() * module.config().speedPerLevel / 100.0;
        if (ABILITY_SWIFT.equals(data.ability())) {
            bonus += module.catalog().ability(ABILITY_SWIFT).map(ToolCatalog.AbilityDef::value).orElse(0.0);
        }
        bonus += familyBonus(net.skybornmc.skyblock.common.bonus.BonusFamily.SPEED, data) / 100.0;
        return Math.max(0, bonus);
    }

    /**
     * Drop multiplier for Depths drops (M4.9): 1 + per-tier + per-level fortune + the extra-drops family for this tool
     * (enchants such as Prosperity, as an expected value, capped in the BonusService).
     */
    double fortune(ToolData data) {
        return 1 + (data.tier() - 1) * module.config().fortunePerTier / 100.0 + data.level() * module.config().fortunePerLevel / 100.0
                + familyBonus(net.skybornmc.skyblock.common.bonus.BonusFamily.EXTRA_DROPS, data) / 100.0;
    }

    /** The capped family bonus (percent) that sources such as enchants give this tool by itself (no player). */
    double familyBonus(net.skybornmc.skyblock.common.bonus.BonusFamily family, ToolData data) {
        return module.core.services().optional(net.skybornmc.skyblock.common.bonus.BonusService.class)
                .map(b -> b.capped(family, net.skybornmc.skyblock.common.bonus.BonusContext.ofTool(data.info(module.config().slotLevels()))))
                .orElse(0.0);
    }

    /**
     * A copy for menus: same look, without the unique id (so it can never count as a second copy of the real item, and
     * can never be redeemed or forged).
     */
    static ItemStack displayCopy(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.editPersistentDataContainer(pdc -> {
            pdc.remove(ItemKeys.UUID);
            pdc.remove(ItemKeys.ID);
        });
        return copy;
    }

    // ------------------------------------------------------------------ blueprint items

    ItemStack createBlueprint(ToolCatalog.BlueprintDef def, String source) {
        return module.core.items().create(BLUEPRINT_ITEM, 1, source, pdc -> pdc.set(BLUEPRINT, PersistentDataType.STRING, def.id()));
    }

    /** The blueprint id and unique id of a blueprint item, or empty. */
    static Optional<Map.Entry<String, UUID>> readBlueprint(ItemStack stack) {
        if (!ItemRegistry.is(stack, BLUEPRINT_ITEM)) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(BLUEPRINT, PersistentDataType.STRING);
        Optional<UUID> uuid = ItemRegistry.uuidOf(stack);
        if (id == null || uuid.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Map.entry(id, uuid.get()));
    }

    // ------------------------------------------------------------------ text helpers

    String raw(String key) {
        String raw = module.core.messages().raw(ToolsModule.ID, key);
        return raw == null ? "<red>[missing: tools:" + key + "]</red>" : raw;
    }

    /** A message text or the fallback when the key is missing (names of config-added tiers, skins …). */
    String rawOr(String key, String fallback) {
        String raw = module.core.messages().raw(ToolsModule.ID, key);
        return raw == null ? LoreBuilder.escape(fallback) : raw;
    }

    net.kyori.adventure.text.Component kindPlural(org.bukkit.entity.Player viewer, ToolKind kind) {
        return module.msg().component(viewer, "kind-plural." + kind.id());
    }

    net.kyori.adventure.text.Component kindComponent(org.bukkit.entity.Player viewer, ToolKind kind) {
        return module.msg().component(viewer, "kind." + kind.id());
    }

    String kindName(ToolKind kind) {
        return rawOr("kind." + kind.id(), kind.id());
    }

    String tierName(ToolKind kind, int tier) {
        return module.catalog().tier(kind, tier).map(d -> rawOr("tier." + kind.id() + "." + d.id(), d.id()))
                .orElse(LoreBuilder.escape(kind.id() + " " + tier));
    }

    String skinName(String skin) {
        return rawOr("skin." + skin + ".name", skin);
    }

    String abilityName(String ability) {
        return rawOr("ability." + ability + ".name", ability);
    }

    String blueprintName(String blueprint) {
        return rawOr("blueprint." + blueprint + ".name", blueprint);
    }

    String perkName(ToolPerk perk) {
        Function<ToolPerk, String> namer = perkNamer;
        if (namer != null) {
            try {
                String name = namer.apply(perk);
                if (name != null) {
                    return name;
                }
            } catch (RuntimeException ignored) {
                // fall back to the raw id
            }
        }
        return perk.id() + " " + roman(perk.level());
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    Rarity rarity(ToolData data, ItemStack stack) {
        String special = stack.getPersistentDataContainer().get(SPECIAL, PersistentDataType.STRING);
        Optional<ToolCatalog.BlueprintDef> bp = module.catalog().blueprint(special);
        Rarity tier = module.catalog().tier(data.kind(), data.tier()).map(ToolCatalog.TierDef::rarity).orElse(Rarity.COMMON);
        if (bp.isPresent() && bp.get().rarity().ordinal() > tier.ordinal()) {
            return bp.get().rarity();
        }
        return tier;
    }

    /** The plain tool name (no colors): skin + tier/blueprint name, for messages. */
    String displayName(ToolData data, ItemStack stack) {
        String special = stack == null ? null : stack.getPersistentDataContainer().get(SPECIAL, PersistentDataType.STRING);
        String base = special != null && module.catalog().blueprint(special).isPresent()
                ? rawOr("blueprint." + special + ".tool-name", special) : tierName(data.kind(), data.tier());
        if (data.skin() != null) {
            base = LoreBuilder.fill(raw("item.skinned-name"), Map.of("skin", skinName(data.skin()), "tool", base));
        }
        return base;
    }

    // ------------------------------------------------------------------ renderers

    private final class ToolRenderer implements ItemDefinition.Renderer {

        @Override
        public String name(ItemStack stack, ItemDefinition definition) {
            Optional<ToolData> data = read(stack);
            if (data.isEmpty()) {
                return definition.name();
            }
            ToolData d = data.get();
            String stars = d.prestige() > 0 ? LoreBuilder.fill(raw("item.stars"), Map.of("stars", "★".repeat(Math.min(5, d.prestige())))) : "";
            return LoreBuilder.fill(raw("item.name"), Map.of("rarity", ToolItems.this.rarity(d, stack).id(), "name", displayName(d, stack), "stars", stars));
        }

        @Override
        public Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return read(stack).map(d -> ToolItems.this.rarity(d, stack)).orElse(definition.rarity());
        }

        @Override
        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
            Optional<ToolData> data = read(stack);
            if (data.isEmpty()) {
                return;
            }
            ToolData d = data.get();
            ToolCurve curve = module.curve();
            List<Integer> slotLevels = module.config().slotLevels();
            lore.subtitle(LoreBuilder.fill(raw("item.subtitle"), Map.of("kind", kindName(d.kind()), "tier", String.valueOf(d.tier()))));
            lore.description(raw("item.description." + d.kind().id()).replaceAll("<[^>]+>", ""));
            if (d.level() >= curve.maxLevel()) {
                lore.line(LoreBuilder.fill(raw("item.max-level"), Map.of("level", String.valueOf(d.level()))));
            } else {
                lore.level(d.level(), curve.progress(d.xp()));
            }
            if (d.prestige() > 0) {
                lore.line(LoreBuilder.fill(raw("item.prestige"), Map.of("count", String.valueOf(d.prestige()))));
            }
            if (d.ability() != null) {
                lore.line(LoreBuilder.fill(raw("item.ability"), Map.of("ability", abilityName(d.ability()))));
            }
            if (d.skin() != null) {
                lore.line(LoreBuilder.fill(raw("item.skin"), Map.of("skin", skinName(d.skin()))));
            }
            if (d.starter()) {
                lore.line(raw("item.starter"));
            }
            history(lore, d);
            int speed = (int) Math.round(speedBonus(d) * 100);
            if (d.kind() != ToolKind.SWORD && d.kind() != ToolKind.ROD) {
                lore.stat("pillar:" + d.kind().skill().id(), plain("item.stat.speed"), "+" + speed + "%");
            }
            if (d.kind() == ToolKind.PICKAXE || d.kind() == ToolKind.AXE) {
                int fortune = (int) Math.round((fortune(d) - 1) * 100);
                lore.stat("pillar:" + d.kind().skill().id(), plain("item.stat.fortune"), "+" + fortune + "%");
            }
            module.catalog().tier(d.kind(), d.tier()).filter(t -> t.damage() != 0)
                    .ifPresent(t -> lore.stat("pillar:combat", plain("item.stat.damage"), trim(1 + t.damage())));
            int slots = d.slots(slotLevels);
            int used = d.perks().stream().mapToInt(ToolPerk::size).sum();
            if (slots > 0) {
                for (ToolPerk perk : d.perks()) {
                    lore.enchant(perkName(perk));
                }
                lore.perkSlots(used, slots);
            }
            int next = d.prestige() > 0 ? -1 : ToolSlots.nextUnlock(d.level(), slotLevels);
            if (next > 0) {
                lore.line(LoreBuilder.fill(raw("item.next-slot"), Map.of("level", String.valueOf(next))));
            }
            if (module.config().shiftRightClick) {
                lore.action(plain("item.action-input"), plain("item.action-text"));
            }
        }

        private void history(LoreBuilder lore, ToolData d) {
            for (String stat : List.of(ToolData.STAT_BLOCKS, ToolData.STAT_LOGS, ToolData.STAT_KILLS)) {
                long value = d.stat(stat);
                if (value > 0) {
                    lore.line(LoreBuilder.fill(raw("item.history." + stat), Map.of("value",
                            LoreBuilder.escape(net.skybornmc.skyblock.common.util.Format.number(value)))));
                }
            }
        }
    }

    private final class BlueprintRenderer implements ItemDefinition.Renderer {

        @Override
        public String name(ItemStack stack, ItemDefinition definition) {
            return readBlueprint(stack).flatMap(e -> module.catalog().blueprint(e.getKey()))
                    .map(def -> LoreBuilder.fill(raw("blueprint-item.name"), Map.of("rarity", def.rarity().id(), "name", blueprintName(def.id()))))
                    .orElse(definition.name());
        }

        @Override
        public Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return readBlueprint(stack).flatMap(e -> module.catalog().blueprint(e.getKey())).map(ToolCatalog.BlueprintDef::rarity)
                    .orElse(definition.rarity());
        }

        @Override
        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
            Optional<ToolCatalog.BlueprintDef> def = readBlueprint(stack).flatMap(e -> module.catalog().blueprint(e.getKey()));
            if (def.isEmpty()) {
                return;
            }
            ToolCatalog.BlueprintDef b = def.get();
            lore.line(LoreBuilder.fill(raw("blueprint-item.makes"), Map.of("tool", rawOr("blueprint." + b.id() + ".tool-name", b.id()),
                    "tier", String.valueOf(b.tier()))));
            lore.line(LoreBuilder.fill(raw("blueprint-item.ability"), Map.of("ability", abilityName(b.ability()))));
            if (!b.dropWing().isBlank()) {
                lore.line(LoreBuilder.fill(raw("blueprint-item.source"), Map.of("wing", rawOr("wing." + b.dropWing(), b.dropWing()),
                        "tier", String.valueOf(b.dropMinTier()))));
            }
        }
    }

    /** A message as plain text (for lore labels the lore builder escapes). */
    private String plain(String key) {
        return raw(key).replaceAll("<[^>]+>", "");
    }

    static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
