package net.skybornmc.skyblock.achievements;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The unlock toast (UI_STYLE §3/§6.5, UX2 format): one screen title for the best unlock of a batch, one chat line per
 * unlock (at most {@code toast.max-lines}, then "+N more"), one sound.
 */
final class Toasts {

    private final AchievementsModule module;

    Toasts(AchievementsModule module) {
        this.module = module;
    }

    /** Name placeholders of one tier: {@code <name>} (MiniMessage from achievements.yml) and {@code <tier>} (" II" or ""). */
    static TagResolver[] placeholders(AchievementDef def, int tier) {
        return new TagResolver[]{Msg.mm("name", def.name()), Msg.p("tier", tierSuffix(def, tier))};
    }

    /** {@link #placeholders} plus {@code <points>} (boost points of that tier). */
    private TagResolver[] withPoints(String name, Tiered def, int tier) {
        return new TagResolver[]{Msg.mm("name", name), Msg.p("tier", tierSuffix(def, tier)),
                Msg.p("points", BoostMath.tierPoints(module.config().perTier(), tier))};
    }

    /** " II" for multi-tier achievements, nothing for a one-step one. */
    static String tierSuffix(Tiered def, int tier) {
        return def.tiers() > 1 ? " " + ProgressMath.roman(tier) : "";
    }

    void show(Player player, List<Progression.Unlock> unlocks) {
        if (unlocks.isEmpty()) {
            return;
        }
        AchievementsConfig config = module.config();
        if (config.toastTitle) {
            Progression.Unlock best = unlocks.stream().filter(Progression.Unlock::last).reduce((a, b) -> b).orElse(unlocks.getLast());
            module.msg().title(player, best.last() ? "unlock.screen-done" : "unlock.screen", placeholders(best.def(), best.tier()));
        }
        if (config.toastChat) {
            int shown = Math.min(unlocks.size(), config.toastMaxLines);
            for (int i = 0; i < shown; i++) {
                Progression.Unlock u = unlocks.get(i);
                sendWithHover(player, u.last() ? "unlock.chat-done" : "unlock.chat", u.def().description(), u.def().goal(u.tier()),
                        withPoints(u.def().name(), u.def(), u.tier()));
            }
            if (unlocks.size() > shown) {
                module.msg().send(player, "unlock.more", Msg.p("count", unlocks.size() - shown));
            }
        }
        if (!config.toastSound.isBlank()) {
            module.core().sounds().play(player, config.toastSound);
        }
    }

    /**
     * Feedback 5 Nov 2026 ("hovering the achievement line should show what it was for"): the chat line shows the
     * achievement's description and the goal of the tier when hovered.
     */
    private void sendWithHover(Player player, String key, List<String> description, long goal, TagResolver... placeholders) {
        net.kyori.adventure.text.Component hover = net.kyori.adventure.text.Component.empty();
        boolean first = true;
        for (String line : description) {
            if (!first) {
                hover = hover.append(net.kyori.adventure.text.Component.newline());
            }
            hover = hover.append(module.core().messages().parse(player, line));
            first = false;
        }
        if (!first) {
            hover = hover.append(net.kyori.adventure.text.Component.newline());
        }
        hover = hover.append(module.msg().component(player, "unlock.hover-goal", Msg.number("goal", goal)));
        player.sendMessage(module.msg().component(player, key, placeholders).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover)));
    }

    /** Island tiers (AC3): the same toast with island texts, shown to every online member. */
    void showIsland(Player player, List<IslandProgression.Unlock> unlocks) {
        if (unlocks.isEmpty()) {
            return;
        }
        AchievementsConfig config = module.config();
        if (config.toastTitle) {
            IslandProgression.Unlock best = unlocks.stream().filter(IslandProgression.Unlock::last).reduce((a, b) -> b).orElse(unlocks.getLast());
            module.msg().title(player, best.last() ? "unlock.island-screen-done" : "unlock.island-screen",
                    withPoints(best.def().name(), best.def(), best.tier()));
        }
        if (config.toastChat) {
            int shown = Math.min(unlocks.size(), config.toastMaxLines);
            for (int i = 0; i < shown; i++) {
                IslandProgression.Unlock u = unlocks.get(i);
                sendWithHover(player, u.last() ? "unlock.island-chat-done" : "unlock.island-chat", u.def().description(), u.def().goal(u.tier()),
                        withPoints(u.def().name(), u.def(), u.tier()));
            }
            if (unlocks.size() > shown) {
                module.msg().send(player, "unlock.more", Msg.p("count", unlocks.size() - shown));
            }
        }
        if (!config.toastSound.isBlank()) {
            module.core().sounds().play(player, config.toastSound);
        }
    }
}
