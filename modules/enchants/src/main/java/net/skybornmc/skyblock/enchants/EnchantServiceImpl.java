package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.enchant.EnchantBook;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolService;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The published {@link EnchantService}. */
final class EnchantServiceImpl implements EnchantService {

    private final EnchantsModule module;

    EnchantServiceImpl(EnchantsModule module) {
        this.module = module;
    }

    @Override
    public Set<String> ids() {
        return module.registry().ids();
    }

    @Override
    public boolean enabled(String id) {
        return module.registry().get(id).map(EnchantDef::enabled).orElse(false);
    }

    @Override
    public int maxLevel(String id) {
        return module.registry().get(id).map(EnchantDef::maxLevel).orElse(0);
    }

    @Override
    public Rarity rarity(String id) {
        return module.registry().get(id).map(EnchantDef::rarity).orElse(Rarity.COMMON);
    }

    @Override
    public String displayName(String id, int level) {
        return module.text().displayName(id, level);
    }

    @Override
    public int level(ItemStack stack, String id) {
        return enchants(stack).getOrDefault(id, 0);
    }

    @Override
    public Map<String, Integer> enchants(ItemStack stack) {
        ToolService tools = module.tools();
        if (tools == null) {
            return Map.of();
        }
        Optional<ToolInfo> info = tools.read(stack);
        if (info.isEmpty() || info.get().perks().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ToolPerk perk : info.get().perks()) {
            if (enabled(perk.id())) {
                out.put(perk.id(), Math.min(perk.level(), maxLevel(perk.id())));
            }
        }
        return out;
    }

    @Override
    public double value(String id, String key, int level) {
        return module.registry().get(id).map(d -> d.value(key, level)).orElse(Double.NaN);
    }

    @Override
    public Optional<EnchantBook> book(ItemStack stack) {
        return BookItems.read(stack);
    }

    @Override
    public CompletableFuture<ItemStack> createBook(String enchantId, int level, String origin, UUID owner) {
        Optional<EnchantDef> def = module.registry().get(enchantId);
        if (def.isEmpty() || level < 1 || level > def.get().maxLevel()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown enchant or level: " + enchantId + " " + level));
        }
        return module.core.scheduler().supplyMain("enchants-create-book", () -> module.books().createBook(enchantId, level, origin))
                .thenCompose(stack -> insert(stack, false, enchantId, level, origin, owner));
    }

    @Override
    public CompletableFuture<ItemStack> createScroll(String origin, UUID owner) {
        return module.core.scheduler().supplyMain("enchants-create-scroll", () -> module.books().createScroll(origin))
                .thenCompose(stack -> insert(stack, true, null, 0, origin, owner));
    }

    private CompletableFuture<ItemStack> insert(ItemStack stack, boolean scroll, String enchant, int level, String origin, UUID owner) {
        UUID id = BookItems.id(stack);
        int season = module.config().seasonId;
        long now = System.currentTimeMillis();
        UUID profile = owner == null ? null : net.skybornmc.skyblock.common.profile.ProfileService.of(module.core.services()).profileKey(owner);
        return module.core.database().transaction(c -> {
            module.store().insert(c, id, scroll, enchant, level, season, origin == null ? "api" : origin, owner, profile, now, "api");
            if (!scroll && owner != null) {
                module.store().markSeen(c, owner, season, enchant, now);
            }
            return stack;
        }).thenApply(s -> {
            module.core.audit().log(AuditCategory.ITEM, "book_created", Actor.system("enchants"), id.toString(),
                    scroll ? "Disenchant Scroll" : module.text().displayName(enchant, level), null, null,
                    Map.of("kind", scroll ? "scroll" : "book", "enchant", scroll ? "" : enchant, "level", level,
                            "origin", String.valueOf(origin), "owner", String.valueOf(owner)));
            return s;
        });
    }
}
