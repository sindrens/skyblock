package net.skybornmc.skyblock.logs;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns audit entries into menu items, chat lines and hover cards (all texts from {@code messages/logs.yml}). */
final class LogRender {

    private final LogsModule module;

    LogRender(LogsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------------------------------------ labels

    Component text(Audience viewer, String key, TagResolver... placeholders) {
        return module.msg.component(viewer, key, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    private String raw(String key) {
        return module.core.messages().raw(LogsModule.ID, key);
    }

    /** "paid", "unbanned", … ({@code actions.<action>} in the messages file) or the humanized action. */
    String action(String action) {
        String label = raw("actions." + action);
        return label != null ? label : LogText.humanize(action);
    }

    Component category(Audience viewer, AuditCategory category) {
        return text(viewer, "categories." + category.key());
    }

    /** "Last 7 days", "All time". */
    Component range(Audience viewer, Duration range) {
        return range == null || range.isZero()
                ? text(viewer, "ranges.all")
                : text(viewer, "ranges.last", Msg.p("range", Format.duration(range)));
    }

    /** Readable subject of a filter ("Steve", "Steve & Alex", "Near 120, -40"). */
    Component subject(Audience viewer, LogFilter f) {
        return switch (f.subject) {
            case ALL -> text(viewer, "subjects.all");
            case PLAYER -> text(viewer, "subjects.player", Msg.p("name", f.playerName));
            case ACTOR -> text(viewer, "subjects.actor", Msg.p("name", f.playerName));
            case BETWEEN -> text(viewer, "subjects.between", Msg.p("name", f.playerName), Msg.p("other", f.otherName));
            case ITEM -> text(viewer, "subjects.item", Msg.p("id", LogText.shorten(f.target, 13)));
            case ISLAND -> f.playerName != null
                    ? text(viewer, "subjects.island-of", Msg.p("name", f.playerName))
                    : text(viewer, "subjects.island", Msg.p("id", f.target));
            case NEAR -> text(viewer, "subjects.near", Msg.p("x", f.x), Msg.p("z", f.z), Msg.p("radius", f.radius),
                    Msg.p("world", f.world));
        };
    }

    /** The categories line of a filter ("All you can see" or "Economy, Trade"). */
    Component categories(Audience viewer, LogFilter f) {
        if (f.categories.isEmpty()) {
            return text(viewer, "categories.all");
        }
        List<Component> names = f.categories.stream().map(c -> category(viewer, c)).toList();
        return Component.join(JoinConfiguration.separator(text(viewer, "categories.separator")), names);
    }

    Material icon(AuditCategory category) {
        Map<AuditCategory, Material> icons = new EnumMap<>(AuditCategory.class);
        for (String entry : module.config.icons) {
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            AuditCategory c = AuditCategory.byKey(entry.substring(0, colon)).orElse(null);
            Material m = Material.matchMaterial(entry.substring(colon + 1).trim());
            if (c != null && m != null && m.isItem()) {
                icons.put(c, m);
            }
        }
        return icons.getOrDefault(category, Material.PAPER);
    }

    // ------------------------------------------------------------------------------------------------ entry parts

    private String targetLabel(AuditEntry e) {
        if (e.targetName() != null) {
            return e.targetName();
        }
        if (e.target() == null) {
            return null;
        }
        return e.target().length() == 36 ? e.target().substring(0, 8) : e.target();
    }

    /** Money for economy categories (unless another currency is named), plain number otherwise. */
    String amount(AuditEntry e) {
        if (e.amount() == null) {
            return null;
        }
        Object currency = e.details().get("currency");
        boolean money = (e.category() == AuditCategory.ECONOMY || e.category() == AuditCategory.TRADE
                || e.category() == AuditCategory.AUCTION) && (currency == null || "money".equalsIgnoreCase(String.valueOf(currency)));
        return money ? Format.money(e.amount()) : Format.number(e.amount()) + (currency != null ? " " + currency : "");
    }

    private TagResolver[] placeholders(AuditEntry e, long now) {
        String target = targetLabel(e);
        String amount = amount(e);
        String main = LogText.mainText(e.details());
        return new TagResolver[]{
                Msg.p("ago", LogText.ago(e.time(), now)),
                Msg.p("utc", LogText.utc(e.time())),
                Msg.p("actor", e.actor().name()),
                Msg.p("action", action(e.action())),
                Msg.p("target", target == null ? "" : target),
                Msg.p("amount", amount == null ? "" : amount),
                Msg.p("text", main == null ? "" : LogText.shorten(main, module.config.loreWidth)),
                Msg.p("world", e.world() == null ? "" : e.world()),
                Msg.p("x", e.x() == null ? "" : e.x()),
                Msg.p("y", e.y() == null ? "" : e.y()),
                Msg.p("z", e.z() == null ? "" : e.z()),
                Msg.p("id", e.id()),
                Msg.p("server", e.server()),
                Msg.p("season", e.season()),
                Msg.p("raw-action", e.action())};
    }

    // ------------------------------------------------------------------------------------------------ menu item

    ItemStack item(Player viewer, AuditEntry e, long now) {
        TagResolver[] p = placeholders(e, now);
        Component name = text(viewer, "entry.name", p);
        List<Component> lore = new ArrayList<>();
        lore.add(text(viewer, "entry.time", p));
        lore.add(category(viewer, e.category()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(text(viewer, targetLabel(e) == null ? "entry.who-solo" : "entry.who", p));
        if (e.amount() != null) {
            lore.add(text(viewer, "entry.amount", p));
        }
        if (LogText.mainText(e.details()) != null) {
            lore.add(text(viewer, "entry.text", p));
        }
        if (e.hasLocation()) {
            lore.add(text(viewer, "entry.location", p));
        }
        Map<String, Object> details = LogText.redact(e.details(), module.canSeeIps(viewer));
        List<String> lines = LogText.detailLines(details, module.config.loreDetailLines, module.config.loreWidth);
        if (!lines.isEmpty() && module.config.loreDetailLines > 0) {
            lore.add(Component.empty());
            lore.add(text(viewer, "entry.details-header", p));
            for (String line : lines) {
                lore.add(text(viewer, "entry.detail", Msg.p("line", line)));
            }
        }
        lore.add(Component.empty());
        lore.add(text(viewer, "entry.meta", p));
        boolean teleport = e.hasLocation() && module.canTeleport(viewer);
        lore.add(text(viewer, teleport ? "entry.click-teleport" : "entry.click-chat"));
        if (teleport) {
            lore.add(text(viewer, "entry.right-click-chat"));
        }
        return module.core.menus().item(icon(e.category()), name, lore);
    }

    // ------------------------------------------------------------------------------------------------ chat

    /** The hover card of an entry: header, who, amount, place and pretty-printed details. */
    Component hover(CommandSender viewer, AuditEntry e, long now) {
        TagResolver[] p = placeholders(e, now);
        List<Component> lines = new ArrayList<>();
        lines.add(text(viewer, "hover.header", with(p, Msg.c("category", category(viewer, e.category())))));
        lines.add(text(viewer, targetLabel(e) == null ? "entry.who-solo" : "entry.who", p));
        if (e.amount() != null) {
            lines.add(text(viewer, "entry.amount", p));
        }
        if (e.hasLocation()) {
            lines.add(text(viewer, "entry.location", p));
        }
        Map<String, Object> details = LogText.redact(e.details(), module.canSeeIps(viewer));
        List<String> pretty = LogText.prettyLines(details, module.config.loreWidth + 18);
        if (!pretty.isEmpty()) {
            lines.add(text(viewer, "entry.details-header", p));
            for (String line : pretty) {
                lines.add(text(viewer, "hover.json", Msg.p("line", line)));
            }
        }
        lines.add(text(viewer, "entry.meta", p));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** One readable line with the hover card; coordinates are clickable for staff who may teleport. */
    Component chatLine(CommandSender viewer, AuditEntry e, long now) {
        TagResolver[] p = placeholders(e, now);
        Component target = targetLabel(e) == null ? Component.empty() : text(viewer, "chat.target", p);
        Component amount = e.amount() == null ? Component.empty() : text(viewer, "chat.amount", p);
        Component main = LogText.mainText(e.details()) == null ? Component.empty() : text(viewer, "chat.text", p);
        Component line = text(viewer, "chat.line", with(p, Msg.c("target-part", target), Msg.c("amount-part", amount),
                Msg.c("text-part", main))).hoverEvent(HoverEvent.showText(hover(viewer, e, now)));
        if (e.hasLocation()) {
            Component where = text(viewer, "chat.location", p);
            if (module.canTeleport(viewer)) {
                where = where.clickEvent(ClickEvent.runCommand("/logs teleport " + e.world() + " " + e.x() + " " + e.y() + " " + e.z()))
                        .hoverEvent(HoverEvent.showText(text(viewer, "chat.location-hover", p)));
            }
            line = line.append(where);
        }
        return line;
    }

    /** A page of results in chat with header and clickable page navigation. */
    void chat(CommandSender viewer, LogFilter filter, LogsModule.Page page, int index, int pages) {
        long now = System.currentTimeMillis();
        viewer.sendMessage(text(viewer, "chat.header", Msg.c("subject", subject(viewer, filter)),
                Msg.c("categories", categories(viewer, filter)), Msg.c("range", range(viewer, filter.range)),
                Msg.number("total", page.total())));
        if (page.rows().isEmpty()) {
            viewer.sendMessage(text(viewer, "chat.empty"));
            return;
        }
        for (AuditEntry e : page.rows()) {
            viewer.sendMessage(chatLine(viewer, e, now));
        }
        Component previous = index > 0
                ? text(viewer, "chat.previous").clickEvent(ClickEvent.runCommand("/logs page " + index))
                : text(viewer, "chat.previous-off");
        Component next = index + 1 < pages
                ? text(viewer, "chat.next").clickEvent(ClickEvent.runCommand("/logs page " + (index + 2)))
                : text(viewer, "chat.next-off");
        viewer.sendMessage(text(viewer, "chat.footer", Msg.c("previous", previous), Msg.c("next", next),
                Msg.p("page", index + 1), Msg.p("pages", pages)));
    }

    private static TagResolver[] with(TagResolver[] base, TagResolver... extra) {
        TagResolver[] all = java.util.Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }
}
