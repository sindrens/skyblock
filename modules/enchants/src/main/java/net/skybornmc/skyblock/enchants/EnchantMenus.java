package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Shared bits of the Enchanter menus (ENCHANTS §4, §11): opening them, problem texts ({@code ✘ Needs tool Lv 34}),
 * enchant names with rarity colors and lore helpers. Every menu is click-only (no dragging, no shift-click needed), so
 * they work the same on Bedrock.
 */
final class EnchantMenus {

    private final EnchantsModule module;

    EnchantMenus(EnchantsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ opening

    void openMain(Player player) {
        markSeenInInventory(player);
        module.core.menus().open(player, new EnchanterMenu(module));
    }

    /** E5: the Armor tab (every owned set of the active profile). */
    void openArmor(Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
        ArmorEntriesMenu menu = new ArmorEntriesMenu(module);
        if (parent != null) {
            menu.parent(parent);
        }
        module.core.menus().open(player, menu);
    }

    /** E5: the pieces and slots of one owned set (also from the wardrobe's set screen). */
    void openArmorEntry(Player player, UUID entryId, net.skybornmc.skyblock.common.menu.Menu parent) {
        if (module.wardrobe() == null) {
            module.msg().send(player, "armor.unavailable");
            return;
        }
        ArmorEntryMenu menu = new ArmorEntryMenu(module, entryId);
        if (parent != null) {
            menu.parent(parent);
        }
        module.core.menus().open(player, menu);
    }

    /** Book shop: loads today's rolls first. */
    void openShop(Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
        UUID uuid = player.getUniqueId();
        UUID profile = module.ops().profiles().profileKey(player);
        String day = EnchantOps.today();
        module.core.database().async(c -> module.store().daily(c, profile, day)).whenComplete((used, error) ->
                module.core.scheduler().main("enchants-shop", () -> {
                    Player p = org.bukkit.Bukkit.getPlayer(uuid);
                    if (p == null) {
                        return;
                    }
                    if (error != null) {
                        module.core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Enchants: could not load daily rolls",
                                net.skybornmc.skyblock.common.db.Database.cause(error));
                        module.msg().send(p, "error");
                        return;
                    }
                    ShopMenu menu = new ShopMenu(module, used);
                    if (parent != null) {
                        menu.parent(parent);
                    }
                    module.core.menus().open(p, menu);
                }));
    }

    /** Guide: loads the season's collection first. */
    void openGuide(Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
        UUID uuid = player.getUniqueId();
        int season = module.config().seasonId;
        module.core.database().async(c -> module.store().seen(c, uuid, season)).whenComplete((seen, error) ->
                module.core.scheduler().main("enchants-guide", () -> {
                    Player p = org.bukkit.Bukkit.getPlayer(uuid);
                    if (p == null) {
                        return;
                    }
                    GuideMenu menu = new GuideMenu(module, error != null ? Set.of() : seen);
                    if (parent != null) {
                        menu.parent(parent);
                    }
                    module.core.menus().open(p, menu);
                }));
    }

    /** Books in the inventory count as "found this season" (the guide's collection). */
    void markSeenInInventory(Player player) {
        List<String> ids = new ArrayList<>();
        for (EnchantOps.HeldBook b : module.ops().books(player)) {
            if (!b.book().scroll() && module.registry().get(b.book().enchant()).isPresent() && !ids.contains(b.book().enchant())) {
                ids.add(b.book().enchant());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int season = module.config().seasonId;
        long now = System.currentTimeMillis();
        module.core.database().run(c -> {
            for (String id : ids) {
                module.store().markSeen(c, uuid, season, id, now);
            }
        }).exceptionally(e -> null);
    }

    // ------------------------------------------------------------------ problems

    static String problemKey(EnchantRules.Problem problem) {
        return problem.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * E5: the message key of a problem on a set piece (under {@code rule.} / {@code why.}): set level instead of tool level,
     * "piece" instead of "tool", the mythic slot instead of the prestige slot.
     */
    static String armorProblemKey(EnchantRules.Problem problem) {
        return switch (problem) {
            case LEVEL_GATE -> "armor-level-gate";
            case SLOT_LOCKED -> "armor-slot-locked";
            case WRONG_TOOL -> "wrong-piece";
            case ALREADY_ON_TOOL -> "already-on-piece";
            case PRESTIGE_ONLY -> "mythic-slot-only";
            default -> problemKey(problem);
        };
    }

    /** The red lore line for a problem on a set piece. */
    Component whyArmor(Player viewer, EnchantRules.Verdict verdict, EnchantDef def) {
        return text(viewer, "why." + armorProblemKey(verdict.problem()), problemPlaceholders(verdict, def, null));
    }

    /** Placeholders for {@code rule.*} and {@code why.*}: other, level, tools, max. */
    TagResolver[] problemPlaceholders(EnchantRules.Verdict verdict, EnchantDef def, ToolInfo tool) {
        return new TagResolver[]{
                Msg.p("other", verdict.other() == null ? "" : module.text().name(verdict.other())),
                Msg.p("level", verdict.needLevel() < 0 ? "prestige" : String.valueOf(verdict.needLevel())),
                Msg.p("tools", def == null ? "" : module.text().targets(def)),
                Msg.p("max", def == null ? "" : EnchantText.roman(def.maxLevel()))};
    }

    /** The red lore line for a problem ({@code why.<problem>}). */
    Component why(Player viewer, EnchantRules.Verdict verdict, EnchantDef def, ToolInfo tool) {
        String key = verdict.problem() == EnchantRules.Problem.SLOT_LOCKED && verdict.needLevel() < 0
                ? "why.slot-locked-prestige" : "why." + problemKey(verdict.problem());
        return text(viewer, key, problemPlaceholders(verdict, def, tool));
    }

    // ------------------------------------------------------------------ text helpers

    Component text(Player viewer, String key, TagResolver... placeholders) {
        return module.core.menus().text(viewer, EnchantsModule.ID, key, placeholders);
    }

    List<Component> lines(Player viewer, String key, TagResolver... placeholders) {
        return module.core.menus().lines(viewer, EnchantsModule.ID, key, placeholders);
    }

    /** {@code <rarity:epic>Vein Rend II} as a MiniMessage placeholder value. */
    String coloredName(EnchantDef def, int level) {
        return "<rarity:" + def.rarity().id() + ">" + LoreBuilder.escape(level > 0 ? module.text().displayName(def.id(), level)
                : module.text().name(def.id()));
    }

    /** Plain text lines (wrapped) as lore in the text color. */
    List<Component> wrapped(Player viewer, String plain) {
        List<Component> out = new ArrayList<>();
        for (String line : EnchantText.wrap(plain, LoreBuilder.DESCRIPTION_WIDTH)) {
            out.add(text(viewer, "menu.text-line", Msg.p("text", line)));
        }
        return out;
    }

    static TagResolver cost(long crystals) {
        return Msg.p("cost", Format.number(crystals));
    }

    long crystals(Player player) {
        return module.economy() == null ? 0 : module.economy().cached(player.getUniqueId(), Currency.CRYSTALS).orElse(0);
    }

    /** A copy of a real item for a menu, with extra lore lines added under its own lore. */
    static ItemStack withExtraLore(ItemStack display, List<Component> extra) {
        ItemStack copy = display.clone();
        List<Component> lore = new ArrayList<>();
        List<Component> current = copy.lore();
        if (current != null) {
            lore.addAll(current);
        }
        if (!extra.isEmpty()) {
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.addAll(extra);
        }
        copy.lore(lore);
        return copy;
    }

    /** A copy of a tool for menus (no unique id, so it never counts as a second copy). */
    static ItemStack toolCopy(ItemStack stack) {
        return BookItems.displayCopy(stack);
    }
}
