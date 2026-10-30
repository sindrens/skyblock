package net.skybornmc.skyblock.wardrobe;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The wardrobe's items (WARDROBE §4.2, §2.4):
 * <ul>
 *   <li><b>Set pieces</b>: real armor that is only a view of a wardrobe row. Data: entry id, set id, piece, profile key
 *   and a nonce that changes on every equip. Vanilla look (base material, leather dye, armor trim), armor points as
 *   attribute modifiers, unbreakable, vanilla tooltips hidden; lore generated from sets.yml.</li>
 *   <li><b>Set Tokens</b>: unique, database-backed items (like enchant books), tradeable until redeemed.</li>
 * </ul>
 */
final class WardrobeItems {

    static final String PIECE_ID = "wardrobe_piece";
    static final String TOKEN_ID = "set_token";

    static final NamespacedKey SET = ItemKeys.key("wardrobe_set");
    static final NamespacedKey MERGED = ItemKeys.key("wardrobe_merged");
    static final NamespacedKey ENTRY = ItemKeys.key("wardrobe_entry");
    static final NamespacedKey PIECE = ItemKeys.key("wardrobe_piece");
    static final NamespacedKey PROFILE = ItemKeys.key("wardrobe_profile");
    static final NamespacedKey NONCE = ItemKeys.key("wardrobe_nonce");
    static final NamespacedKey LEVEL = ItemKeys.key("wardrobe_level");
    static final NamespacedKey TOME_XP = ItemKeys.key("wardrobe_tome_xp");
    static final NamespacedKey TOME_KIND = ItemKeys.key("wardrobe_tome_kind");
    static final String TOME_ID = "wardrobe_tome";

    /** What a worn piece says about itself. */
    record PieceTag(UUID entry, String setId, ArmorPiece piece, UUID profile, UUID nonce) {
    }

    private final WardrobeModule module;

    WardrobeItems(WardrobeModule module) {
        this.module = module;
    }

    void register() {
        ItemRegistry items = module.core.items();
        items.register(ItemDefinition.builder(PIECE_ID, Material.LEATHER_CHESTPLATE, ItemType.SET_PIECE, Rarity.UNCOMMON)
                .name(module.text().raw("piece.fallback-name")).unbreakable().renderer(new PieceRenderer()).build());
        Material token = Material.matchMaterial(module.config().tokenMaterial);
        if (token == null || !token.isItem() || token.isAir()) {
            module.core.plugin().getLogger().warning("wardrobe.yml → tokens.material '" + module.config().tokenMaterial
                    + "' is not an item; using ARMOR_STAND");
            token = Material.ARMOR_STAND;
        }
        items.register(ItemDefinition.builder(TOKEN_ID, token, ItemType.SET_PIECE, Rarity.RARE)
                .name(module.text().raw("token.fallback-name")).unique().glint().renderer(new TokenRenderer()).build());
        Material tome = Material.matchMaterial(module.config().tomeMaterial);
        if (tome == null || !tome.isItem() || tome.isAir()) {
            module.core.plugin().getLogger().warning("wardrobe.yml → tomes.material '" + module.config().tomeMaterial
                    + "' is not an item; using BOOK");
            tome = Material.BOOK;
        }
        items.register(ItemDefinition.builder(TOME_ID, tome, ItemType.CONSUMABLE, Rarity.EPIC)
                .name(module.text().raw("tome.fallback-name")).unique().glint().renderer(new TomeRenderer()).build());
    }

    void unregister() {
        module.core.items().unregister(PIECE_ID);
        module.core.items().unregister(TOKEN_ID);
        module.core.items().unregister(TOME_ID);
    }

    // ------------------------------------------------------------------ pieces

    /**
     * A new worn piece for an entry (the caller writes the nonce to the database first). {@code def} is the look set; a
     * merged entry also carries its other set. From set level 10 the trim uses the upgraded material, from level 50 the
     * piece glints; in the warzone the armor points are the normalized ones (WD18).
     */
    ItemStack piece(SetDef def, WardrobeStore.Entry entry, ArmorPiece piece, UUID profile, UUID nonce, boolean warzone) {
        String other = entry.isMerged() ? (def.id().equals(entry.mergedSetId()) ? entry.setId() : entry.mergedSetId()) : null;
        int level = levelsShown(entry);
        ItemStack base = module.core.items().create(PIECE_ID, 1, null, pdc -> {
            pdc.set(SET, PersistentDataType.STRING, def.id());
            if (other != null) {
                pdc.set(MERGED, PersistentDataType.STRING, other);
            }
            pdc.set(ENTRY, PersistentDataType.STRING, entry.entryId().toString());
            pdc.set(PIECE, PersistentDataType.STRING, piece.key());
            pdc.set(PROFILE, PersistentDataType.STRING, profile.toString());
            pdc.set(NONCE, PersistentDataType.STRING, nonce.toString());
            pdc.set(LEVEL, PersistentDataType.INTEGER, level);
        });
        ItemStack stack = base.withType(material(def.look().base(), piece));
        boolean levels = module.config().levelsEnabled;
        look(stack, def, levels && level >= module.config().trimUpgradeLevel);
        if (warzone) {
            armor(stack, module.config().warzoneArmorPoints(), module.config().warzoneToughness, module.config().warzoneKnockback, piece);
        } else {
            armor(stack, module.config().armor(def.tier()), module.config().toughness(def.tier()), module.config().knockback(def.tier()), piece);
        }
        if (levels && level >= module.config().glintLevel) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.setData(DataComponentTypes.UNBREAKABLE);
        hideTooltips(stack);
        module.core.items().render(stack);
        return stack;
    }

    /** The tag of a wardrobe piece, or empty for anything else (or a broken piece, which is then deleted as invalid). */
    static Optional<PieceTag> tag(ItemStack stack) {
        if (!ItemRegistry.is(stack, PIECE_ID)) {
            return Optional.empty();
        }
        var pdc = stack.getPersistentDataContainer();
        return Optional.of(new PieceTag(uuid(pdc.get(ENTRY, PersistentDataType.STRING)), pdc.get(SET, PersistentDataType.STRING),
                ArmorPiece.byKey(pdc.get(PIECE, PersistentDataType.STRING)).orElse(null),
                uuid(pdc.get(PROFILE, PersistentDataType.STRING)), uuid(pdc.get(NONCE, PersistentDataType.STRING))));
    }

    static boolean isPiece(ItemStack stack) {
        return ItemRegistry.is(stack, PIECE_ID);
    }

    static Material material(SetDef.Base base, ArmorPiece piece) {
        String suffix = switch (piece) {
            case HEAD -> "HELMET";
            case CHEST -> "CHESTPLATE";
            case LEGS -> "LEGGINGS";
            case FEET -> "BOOTS";
        };
        Material m = Material.matchMaterial(base.name() + "_" + suffix);
        return m == null ? Material.LEATHER_CHESTPLATE : m;
    }

    /** The level a piece shows (the entry's level; the fixed state while levels are off). */
    private int levelsShown(WardrobeStore.Entry entry) {
        return Math.max(0, entry.level());
    }

    void look(ItemStack stack, SetDef def) {
        look(stack, def, false);
    }

    /** Dye and trim of the set's look (unknown trim ids are skipped); {@code upgraded} uses the level-10 trim material. */
    void look(ItemStack stack, SetDef def, boolean upgraded) {
        SetDef.Look look = def.look();
        if (look.base() == SetDef.Base.LEATHER && look.dye() >= 0) {
            stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(look.dye())));
        }
        String trimMaterial = upgraded && look.upgradedTrimMaterial() != null && !look.upgradedTrimMaterial().isEmpty()
                ? look.upgradedTrimMaterial() : look.trimMaterial();
        if (!look.trimPattern().isEmpty() && !trimMaterial.isEmpty()) {
            try {
                TrimPattern pattern = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN)
                        .get(NamespacedKey.minecraft(look.trimPattern()));
                TrimMaterial material = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL)
                        .get(NamespacedKey.minecraft(trimMaterial));
                if (pattern != null && material != null) {
                    stack.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(new ArmorTrim(material, pattern)).build());
                }
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().fine("Wardrobe: no trim for " + def.id() + ": " + e.getMessage());
            }
        }
    }

    /** Armor, toughness and knockback resistance for the piece's slot (wardrobe.yml → armor.tier-N or warzone.*). */
    private void armor(ItemStack stack, List<Double> armor, double toughness, double knockback, ArmorPiece piece) {
        double points = piece.ordinal() < armor.size() ? armor.get(piece.ordinal()) : 0;
        EquipmentSlotGroup group = piece.slot().getGroup();
        String k = piece.key();
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.itemAttributes();
        if (points > 0) {
            b.addModifier(Attribute.ARMOR, new AttributeModifier(ItemKeys.key("wardrobe_armor_" + k), points,
                    AttributeModifier.Operation.ADD_NUMBER, group), group);
        }
        if (toughness > 0) {
            b.addModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(ItemKeys.key("wardrobe_toughness_" + k), toughness,
                    AttributeModifier.Operation.ADD_NUMBER, group), group);
        }
        if (knockback > 0) {
            b.addModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(ItemKeys.key("wardrobe_knockback_" + k), knockback,
                    AttributeModifier.Operation.ADD_NUMBER, group), group);
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, b.build());
    }

    private static void hideTooltips(ItemStack stack) {
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(
                DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS, DataComponentTypes.UNBREAKABLE,
                DataComponentTypes.DYED_COLOR, DataComponentTypes.TRIM).build());
    }

    /** A chestplate in the set's look for menus (no wardrobe data, so it is never a piece). */
    ItemStack icon(SetDef def, ArmorPiece piece) {
        ItemStack stack = ItemStack.of(material(def.look().base(), piece));
        look(stack, def);
        hideTooltips(stack);
        return stack;
    }

    // ------------------------------------------------------------------ tokens

    /** A new token item (random unique id; the caller writes the database row with {@link ItemRegistry#uuidOf}). */
    ItemStack token(String setId, String source) {
        return module.core.items().create(TOKEN_ID, 1, source, pdc -> pdc.set(SET, PersistentDataType.STRING, setId));
    }

    /** Set id of a token item, or empty. */
    static Optional<String> tokenSet(ItemStack stack) {
        if (!ItemRegistry.is(stack, TOKEN_ID)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getPersistentDataContainer().get(SET, PersistentDataType.STRING));
    }

    static boolean isToken(ItemStack stack) {
        return ItemRegistry.is(stack, TOKEN_ID);
    }

    // ------------------------------------------------------------------ tomes (W2)

    /** A new tome item (random unique id; the caller writes the database row with {@link ItemRegistry#uuidOf}). */
    ItemStack tome(String kind, long xp, String source) {
        return module.core.items().create(TOME_ID, 1, source, pdc -> {
            pdc.set(TOME_KIND, PersistentDataType.STRING, kind);
            pdc.set(TOME_XP, PersistentDataType.LONG, xp);
        });
    }

    static boolean isTome(ItemStack stack) {
        return ItemRegistry.is(stack, TOME_ID);
    }

    /** Set Tokens and Wardrobe Tomes: unique wardrobe items that are never placed, crafted, enchanted or dispensed. */
    static boolean isUnique(ItemStack stack) {
        return isToken(stack) || isTome(stack);
    }

    /** XP a tome item shows (the database row is the truth). */
    static long tomeXp(ItemStack stack) {
        if (!isTome(stack)) {
            return 0;
        }
        Long xp = stack.getPersistentDataContainer().get(TOME_XP, PersistentDataType.LONG);
        return xp == null ? 0 : xp;
    }

    // ------------------------------------------------------------------ renderers

    private final class PieceRenderer implements ItemDefinition.Renderer {

        private Optional<SetDef> def(ItemStack stack) {
            return Optional.ofNullable(stack.getPersistentDataContainer().get(SET, PersistentDataType.STRING))
                    .flatMap(id -> module.registry().get(id));
        }

        @Override
        public String name(ItemStack stack, ItemDefinition definition) {
            Optional<SetDef> def = def(stack);
            Optional<ArmorPiece> piece = ArmorPiece.byKey(stack.getPersistentDataContainer().get(PIECE, PersistentDataType.STRING));
            if (def.isEmpty() || piece.isEmpty()) {
                return definition.name();
            }
            return "<rarity:" + def.get().rarity().id() + ">" + LoreBuilder.escape(module.text().pieceName(def.get(), piece.get()));
        }

        @Override
        public Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return def(stack).map(SetDef::rarity).orElse(definition.rarity());
        }

        @Override
        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
            Optional<SetDef> found = def(stack);
            if (found.isEmpty()) {
                lore.description(module.text().plain("piece.unknown"));
                return;
            }
            SetDef def = found.get();
            WardrobeText text = module.text();
            lore.subtitle(LoreBuilder.fill(text.raw("piece.subtitle"), Map.of("tier", WardrobeText.roman(def.tier()))));
            lore.line(LoreBuilder.fill(text.raw("piece.set"), Map.of("set", WardrobeText.colored(def))));
            Integer level = stack.getPersistentDataContainer().get(LEVEL, PersistentDataType.INTEGER);
            int lv = level == null ? 0 : level;
            double multiplier = module.bonusMultiplier(lv);
            if (module.config().levelsEnabled) {
                lore.line(LoreBuilder.fill(text.raw("piece.level"), Map.of("level", String.valueOf(lv),
                        "multiplier", WardrobeText.number(multiplier))));
            }
            bonusLines(lore, def, multiplier);
            String merged = stack.getPersistentDataContainer().get(MERGED, PersistentDataType.STRING);
            module.registry().get(merged).ifPresent(m -> {
                lore.line(LoreBuilder.fill(text.raw("piece.merged"), Map.of("set", LoreBuilder.escape(m.shortName()))));
                bonusLines(lore, m, multiplier);
            });
            lore.action(text.plain("piece.action-input"), text.plain("piece.action-text"));
        }
    }

    /** "2 pieces" / "4 pieces" sections with the bonus values at a multiplier. */
    void bonusLines(LoreBuilder lore, SetDef def, double multiplier) {
        WardrobeText text = module.text();
        lore.line(text.raw("bonus.two-header"));
        for (SetDef.Bonus b : def.two()) {
            lore.line(LoreBuilder.fill(text.raw("bonus.line"), Map.of("text", text.bonus(b.text(), b.value() * multiplier))));
        }
        lore.line(text.raw("bonus.four-header"));
        for (SetDef.Bonus b : def.four()) {
            lore.line(LoreBuilder.fill(text.raw("bonus.line"), Map.of("text", text.bonus(b.text(), b.value() * multiplier))));
        }
    }

    private final class TokenRenderer implements ItemDefinition.Renderer {

        private Optional<SetDef> def(ItemStack stack) {
            return tokenSet(stack).flatMap(id -> module.registry().get(id));
        }

        @Override
        public String name(ItemStack stack, ItemDefinition definition) {
            return def(stack).map(d -> LoreBuilder.fill(module.text().raw("token.name"),
                    Map.of("rarity", d.rarity().id(), "set", LoreBuilder.escape(d.name())))).orElse(definition.name());
        }

        @Override
        public Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return def(stack).map(SetDef::rarity).orElse(definition.rarity());
        }

        @Override
        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
            Optional<SetDef> found = def(stack);
            WardrobeText text = module.text();
            if (found.isEmpty()) {
                lore.description(text.plain("token.unknown"));
                return;
            }
            lore.subtitle(LoreBuilder.fill(text.raw("token.subtitle"), Map.of("tier", WardrobeText.roman(found.get().tier()))));
            lore.description(text.plain("token.description"));
            bonusLines(lore, found.get(), module.bonusMultiplier(0));
            lore.line(text.raw("token.tradeable"));
            lore.action(text.plain("token.action-input"), text.plain("token.action-text"));
        }
    }

    private final class TomeRenderer implements ItemDefinition.Renderer {

        private String kind(ItemStack stack) {
            String k = stack.getPersistentDataContainer().get(TOME_KIND, PersistentDataType.STRING);
            return Tomes.GREATER.equals(k) ? Tomes.GREATER : Tomes.NORMAL;
        }

        @Override
        public String name(ItemStack stack, ItemDefinition definition) {
            return module.text().raw("tome.name." + kind(stack));
        }

        @Override
        public Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return Tomes.GREATER.equals(kind(stack)) ? Rarity.LEGENDARY : Rarity.EPIC;
        }

        @Override
        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
            WardrobeText text = module.text();
            lore.subtitle(text.raw("tome.subtitle"));
            lore.description(LoreBuilder.fill(text.plain("tome.description"), Map.of(
                    "xp", WardrobeText.number(tomeXp(stack)), "level", String.valueOf(module.config().tomeMaxLevel))));
            lore.action(text.plain("tome.action-input"), text.plain("tome.action-text"));
        }
    }

    private static UUID uuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String key(ArmorPiece piece) {
        return piece.name().toLowerCase(Locale.ROOT);
    }
}
