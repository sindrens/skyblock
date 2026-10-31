package net.skybornmc.skyblock.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Player-facing texts of quests (all in messages/quests.yml): names, lore lines, action bar steps, the done toast. */
final class QuestText {

    private static final int BAR = 10;

    private final QuestsModule m;

    QuestText(QuestsModule module) {
        this.m = module;
    }

    String money(long amount) {
        return m.economy().map(e -> e.format(Currency.MONEY, amount)).orElse("$" + Format.number(amount));
    }

    String crystals(long amount) {
        return m.economy().map(e -> e.format(Currency.CRYSTALS, amount)).orElse(Format.number(amount));
    }

    /** The target as shown: money for sell quests, a plain number otherwise. */
    String amount(Quest q, long value) {
        return q.type == QuestType.SELL ? money(value) : Format.number(value);
    }

    TagResolver[] placeholders(Quest q) {
        return new TagResolver[]{Msg.p("target", amount(q, q.target)), Msg.p("tier", q.tierFrom),
                Msg.p("progress", amount(q, q.progress)), Msg.number("percent", QuestMath.percent(q.progress, q.target))};
    }

    /** "Mine 380 blocks" (the one-form when the target is 1 and the file has it). */
    Component name(Player viewer, Quest q) {
        String key = "quest." + q.type.id() + (q.target == 1 && m.msg.has("quest." + q.type.id() + ".name-one") ? ".name-one" : ".name");
        return m.msg.component(viewer, key, placeholders(q));
    }

    /** Short plain name for the action bar and the scoreboard placeholder ("Mine blocks"). */
    Component shortName(Player viewer, Quest q) {
        return m.msg.component(viewer, "quest." + q.type.id() + ".short", placeholders(q));
    }

    /** 10 dots, flame for done, gray for open (Bedrock-safe ●). */
    String bar(Quest q) {
        int filled = (int) Math.min(BAR, Math.floor(QuestMath.percent(q.progress, q.target) / (100.0 / BAR)));
        return m.raw("bar.filled", "●").repeat(filled) + m.raw("bar.empty", "●").repeat(BAR - filled);
    }

    /** Lore of a quest item: where, progress, rewards. */
    List<Component> lore(Player viewer, QuestKind kind, Quest q) {
        List<Component> lore = new ArrayList<>();
        TagResolver[] ph = placeholders(q);
        if (q.type.depths()) {
            lore.add(m.msg.component(viewer, "quest." + q.type.id() + ".where", ph));
        }
        if (q.done()) {
            lore.addAll(m.core.menus().lines(viewer, QuestsModule.ID, "menu.quest.done-lore"));
        } else {
            lore.add(m.msg.component(viewer, "menu.quest.progress", ph));
            lore.add(m.msg.component(viewer, "menu.quest.bar", Msg.mm("bar", bar(q)), Msg.number("percent", QuestMath.percent(q.progress, q.target))));
        }
        lore.add(Component.empty());
        lore.add(m.msg.component(viewer, kind == QuestKind.ISLAND ? "menu.quest.rewards-island" : "menu.quest.rewards"));
        lore.addAll(rewardLines(viewer, q));
        return lore;
    }

    List<Component> rewardLines(Player viewer, Quest q) {
        List<Component> lines = new ArrayList<>();
        if (q.rewardMoney > 0) {
            lines.add(m.msg.component(viewer, "reward.money", Msg.p("amount", money(q.rewardMoney))));
        }
        if (q.rewardCrystals > 0) {
            lines.add(m.msg.component(viewer, "reward.crystals", Msg.p("amount", crystals(q.rewardCrystals))));
        }
        if (q.rewardXp > 0) {
            lines.add(m.msg.component(viewer, m.passXpLive() ? "reward.pass-xp" : "reward.pass-xp-soon", Msg.number("amount", q.rewardXp)));
        }
        return lines;
    }

    /** The bonus key's name ("Cinder Key"). */
    String crateName(String crate) {
        String c = crate == null ? "" : crate.trim().toLowerCase(java.util.Locale.ROOT);
        return m.raw("crates." + c, c.isEmpty() ? "" : Character.toUpperCase(c.charAt(0)) + c.substring(1));
    }

    // ------------------------------------------------------------------ notices

    /** Action bar at every step (25 / 50 / 75%). */
    void step(Player player, QuestKind kind, Quest q, int percent) {
        m.msg.actionBar(player, kind == QuestKind.ISLAND ? "step.island" : "step.personal", Msg.c("quest", shortName(player, q)),
                Msg.number("percent", percent), Msg.p("progress", amount(q, q.progress)), Msg.p("target", amount(q, q.target)));
    }

    /** The toast of a finished quest: title, chat lines with the rewards, sound; island missions tell every online member. */
    void done(QuestSet set, Quest q, Payouts.Outcome outcome) {
        if (set.kind == QuestKind.ISLAND) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!set.owner.equals(m.countingIsland(p))) {
                    continue;
                }
                boolean helped = outcome.paidHelpers.contains(m.profileKey(p));
                m.msg.title(p, "done.island-screen", Msg.c("quest", name(p, q)));
                m.msg.send(p, helped ? "done.island" : "done.island-not-helped", Msg.c("quest", name(p, q)),
                        Msg.number("helpers", outcome.paidHelpers.size()), Msg.number("min", Math.round(m.settings.helperMinPercent)));
                if (helped) {
                    rewardLines(p, q).forEach(p::sendMessage);
                }
                m.sound(p, m.settings.soundDone);
            }
            return;
        }
        Player p = m.receiver(set.owner);
        if (p == null) {
            return;
        }
        m.msg.title(p, "done.screen", Msg.c("quest", name(p, q)));
        m.msg.send(p, set.kind == QuestKind.DAILY ? "done.daily" : "done.weekly", Msg.c("quest", name(p, q)),
                Msg.number("done", set.doneCount() + (q.done() ? 0 : 1)), Msg.number("total", set.quests.size()));
        rewardLines(p, q).forEach(p::sendMessage);
        if (outcome.bonus) {
            String crate = set.kind == QuestKind.DAILY ? m.settings.dailyBonusCrate : m.settings.weeklyBonusCrate;
            m.msg.send(p, set.kind == QuestKind.DAILY ? "done.all-daily" : "done.all-weekly", Msg.p("crate", crateName(crate)),
                    Msg.number("total", set.quests.size()));
        }
        m.sound(p, m.settings.soundDone);
    }

    void swapped(Player player, Quest fresh) {
        m.msg.send(player, "swap.swapped", Msg.c("quest", name(player, fresh)));
        m.sound(player, "success");
    }

    /** Last line per player, for placeholder reads off the main thread (some scoreboard plugins read async). */
    private final java.util.Map<UUID, String> lastLines = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The scoreboard / placeholder line of the open quest closest to done ("Mine blocks 230/400"), or empty. Computed on
     * the main thread; other threads get the last computed line.
     */
    String nextLine(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            return lastLines.getOrDefault(player.getUniqueId(), "");
        }
        String line = computeNextLine(player);
        if (line.isEmpty()) {
            lastLines.remove(player.getUniqueId());
        } else {
            lastLines.put(player.getUniqueId(), line);
        }
        return line;
    }

    void forget(UUID player) {
        lastLines.remove(player);
    }

    private String computeNextLine(Player player) {
        UUID profile = m.profileKey(player);
        Quest best = null;
        for (QuestKind kind : List.of(QuestKind.DAILY, QuestKind.WEEKLY)) {
            QuestSet set = m.engine().set(profile, kind).orElse(null);
            if (set == null) {
                continue;
            }
            for (Quest q : set.quests) {
                if (!q.done() && (best == null || QuestMath.percent(q.progress, q.target) > QuestMath.percent(best.progress, best.target))) {
                    best = q;
                }
            }
        }
        if (best == null) {
            return "";
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                m.msg.component(player, "placeholder.next", Msg.c("quest", shortName(player, best)),
                        Msg.p("progress", amount(best, best.progress)), Msg.p("target", amount(best, best.target))));
    }
}
