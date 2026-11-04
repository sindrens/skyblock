package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owner 4 Nov 2026 ("island = profile"): the real amounts a profile wipe takes, for the confirm screens of leave, disband
 * and "replace my island" (messages {@code wipe-loss.*}). Money and shards are read from the database before the menu
 * opens; items, tools and skill levels from the online player.
 */
final class WipeLosses {

    /** What this profile loses; {@code null} amounts could not be read. */
    record Loss(Long money, Long shards, long items, long tools, long skillLevels) {
    }

    private final IslandsModule module;
    private final Core core;

    WipeLosses(IslandsModule module) {
        this.module = module;
        this.core = module.core();
    }

    /** Reads the amounts of the player's active profile; completes on any thread. */
    CompletableFuture<Loss> read(Player player, UUID profileKey) {
        long items = count(player.getInventory().getContents()) + count(player.getEnderChest().getContents());
        long tools = tools(player);
        long skills = skillLevels(player);
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        CompletableFuture<Long> money = economy.isEmpty() ? CompletableFuture.completedFuture(null)
                : economy.get().balance(AccountRef.player(profileKey), Currency.MONEY).handle((v, e) -> e == null ? v : null);
        CompletableFuture<Long> shards = economy.isEmpty() ? CompletableFuture.completedFuture(null)
                : economy.get().balance(AccountRef.player(profileKey), Currency.SHARDS).handle((v, e) -> e == null ? v : null);
        return money.thenCombine(shards, (m, s) -> new Loss(m, s, items, tools, skills));
    }

    /** The "| You lose" and "| You keep" sections for the middle item of a confirm screen. */
    List<Component> lines(Player viewer, Loss loss) {
        List<Component> lines = new ArrayList<>(module.lines(viewer, "wipe-loss.top"));
        lines.addAll(module.lines(viewer, "wipe-loss.money", Msg.p("money", money(loss.money()))));
        lines.addAll(module.lines(viewer, "wipe-loss.shards", Msg.p("shards", loss.shards() == null ? module.raw("wipe-loss.unknown")
                : Format.number(loss.shards()))));
        lines.addAll(module.lines(viewer, "wipe-loss.items", Msg.p("items", Format.number(loss.items()))));
        lines.addAll(module.lines(viewer, "wipe-loss.tools", Msg.p("tools", Format.number(loss.tools()))));
        lines.addAll(module.lines(viewer, "wipe-loss.skills", Msg.p("skills", Format.number(loss.skillLevels()))));
        lines.addAll(module.lines(viewer, "wipe-loss.more"));
        lines.addAll(module.lines(viewer, "wipe-loss.kept"));
        return lines;
    }

    private String money(Long value) {
        if (value == null) {
            return module.raw("wipe-loss.unknown");
        }
        return core.services().optional(EconomyService.class).map(e -> e.format(Currency.MONEY, value)).orElse(Format.money(value));
    }

    private static long count(ItemStack[] stacks) {
        long n = 0;
        for (ItemStack stack : stacks) {
            n += stack == null || stack.getType().isAir() ? 0 : stack.getAmount();
        }
        return n;
    }

    private long tools(Player player) {
        Optional<ToolService> service = core.services().optional(ToolService.class);
        if (service.isEmpty()) {
            return 0;
        }
        long n = 0;
        for (ItemStack[] stacks : List.of(player.getInventory().getContents(), player.getEnderChest().getContents())) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.getType().isAir() && service.get().isTool(stack)) {
                    n++;
                }
            }
        }
        return n;
    }

    private long skillLevels(Player player) {
        Optional<SkillService> service = core.services().optional(SkillService.class);
        if (service.isEmpty()) {
            return 0;
        }
        long n = 0;
        for (SkillType skill : SkillType.values()) {
            if (service.get().available(skill)) {
                n += Math.max(0, service.get().level(player, skill));
            }
        }
        return n;
    }
}
