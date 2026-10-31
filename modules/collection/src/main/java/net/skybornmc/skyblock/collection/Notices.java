package net.skybornmc.skyblock.collection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player sees on a new find and on a milestone (UI_STYLE §6.5 toast: a short screen title, one chat line, one
 * sound). Nothing is announced to other players (the plan has no rare-find broadcast).
 */
final class Notices {

    private final CollectionModule m;

    Notices(CollectionModule module) {
        this.m = module;
    }

    void find(Player p, ProfileLog log, String entryId) {
        Catalog.Entry e = m.catalog().entry(entryId);
        Catalog.Page page = e == null ? null : m.catalog().page(e.page());
        if (page == null) {
            return;
        }
        Component item = m.entryName(p, e);
        if (m.settings.noticeTitle) {
            m.msg.title(p, "found.screen", Msg.c("item", item));
        }
        if (m.settings.noticeChat) {
            m.msg.send(p, "found.chat", Msg.c("item", item), Msg.c("page", m.pageName(p, page.id())),
                    Msg.number("found", Catalog.found(page, log.foundIds())), Msg.number("total", page.entries().size()));
        }
        m.sound(p, m.settings.findSound);
    }

    void milestones(Player p, List<String> ids) {
        String best = null;
        for (String id : ids) {
            int percent = Milestones.percentOf(id);
            if (Milestones.ALL.equals(id)) {
                if (m.settings.noticeChat) {
                    m.msg.send(p, "milestone.all.chat", Msg.c("rewards", rewards(p, id)));
                }
                best = id;
            } else if (percent > 0) {
                String page = id.substring(0, id.lastIndexOf(':'));
                Component pageName = m.pageName(p, page);
                if (m.settings.noticeChat) {
                    if (percent == Milestones.FULL) {
                        m.msg.send(p, "milestone.page-done.chat", Msg.c("page", pageName), Msg.c("rewards", rewards(p, id)),
                                Msg.p("points", points()));
                    } else {
                        m.msg.send(p, "milestone.chat", Msg.c("page", pageName), Msg.number("percent", percent), Msg.p("points", points()));
                    }
                }
                if (best == null || (!Milestones.ALL.equals(best) && percent >= Milestones.percentOf(best))) {
                    best = id;
                }
            }
        }
        if (best != null && m.settings.noticeTitle) {
            if (Milestones.ALL.equals(best)) {
                m.msg.title(p, "milestone.all.screen");
            } else {
                String page = best.substring(0, best.lastIndexOf(':'));
                int percent = Milestones.percentOf(best);
                m.msg.title(p, percent == Milestones.FULL ? "milestone.page-done.screen" : "milestone.screen",
                        Msg.c("page", m.pageName(p, page)), Msg.number("percent", percent));
            }
        }
        m.sound(p, m.settings.milestoneSound);
    }

    private String points() {
        double v = m.settings.levelPoints;
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** "Title Deep Miner, 1x Blaze Key" for a milestone. */
    Component rewards(Player p, String milestone) {
        List<Component> parts = new ArrayList<>();
        for (Milestones.Grant g : Milestones.grants(milestone, m.rewards())) {
            parts.add(m.grantText(p, g));
        }
        if (parts.isEmpty()) {
            return m.msg.component(p, "reward.none");
        }
        return Component.join(JoinConfiguration.separator(m.msg.component(p, "reward.separator")), parts);
    }
}
