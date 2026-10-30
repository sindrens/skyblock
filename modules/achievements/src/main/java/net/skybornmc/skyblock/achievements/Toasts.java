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

    /** " II" for multi-tier achievements, nothing for a one-step one. */
    static String tierSuffix(AchievementDef def, int tier) {
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
                module.msg().send(player, u.last() ? "unlock.chat-done" : "unlock.chat", placeholders(u.def(), u.tier()));
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
