package net.skybornmc.skyblock.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.LegacyColors;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * "How this player looks": rank prefix, rank-colored name (nickname aware), title, tag and AFK marker, plus format
 * rendering. Chat, tab and nametags all use this, so a player looks the same everywhere.
 *
 * <p>Hooks for later features: a module that registers the placeholders {@code title} and {@code tag} (M1.11
 * Tags &amp; Titles) automatically shows up in chat and tab; {@code afk} comes from the social module.</p>
 */
final class Display {

    static final TextColor DEFAULT_NAME = TextColor.color(0xC9C9C9);

    private final Core core;
    private final Supplier<ChatConfig> config;

    Display(Core core, Supplier<ChatConfig> config) {
        this.core = core;
        this.config = config;
    }

    /** Parsed legacy prefixes ({@code "&c&lADMIN &c"} → Component); a handful of ranks, so this stays tiny. */
    private final java.util.Map<String, Component> legacyPrefixes = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Snapshot of the rank parts that change how a player looks (PERF4). The tab list reads LuckPerms off the main
     * thread into this record and only compares/uses the snapshot on the main thread.
     */
    record RankView(String prefix, TextColor color, int weight) {
        RankView {
            prefix = prefix == null ? "" : prefix;
            color = color == null ? DEFAULT_NAME : color;
        }
    }

    /** Reads the rank parts from the rank provider (LuckPerms). Thread-safe; the tab list calls it asynchronously. */
    RankView rankView(Player player) {
        return new RankView(core.ranks().prefix(player), color(player), core.ranks().weight(player));
    }

    /** LuckPerms prefix ({@code &} codes like Essentials, or MiniMessage), empty if the rank has none. */
    Component rank(Audience viewer, Player player) {
        return rankPrefix(viewer, core.ranks().prefix(player));
    }

    /** {@link #rank(Audience, Player)} from an already-read prefix. */
    Component rankPrefix(Audience viewer, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        if (LegacyColors.hasCodes(prefix) || prefix.indexOf('<') < 0) {
            if (legacyPrefixes.size() > 256) {
                legacyPrefixes.clear();
            }
            return legacyPrefixes.computeIfAbsent(prefix, LegacyColors::toComponent);
        }
        return core.messages().parse(viewer, prefix);
    }

    /**
     * Cheap fingerprint of everything that changes how a player looks (no parsing, no components built): prefix, rank
     * color, weight, display name, AFK, title, tag. When it is unchanged, nothing needs to be rebuilt or resent.
     */
    String key(Player player) {
        return key(player, rankView(player));
    }

    /** {@link #key(Player)} with the rank parts from a snapshot (no rank provider lookups). */
    String key(Player player, RankView rank) {
        String nick = hasNick(player) ? PlainTextComponentSerializer.plainText().serialize(player.displayName())
                + player.displayName().hashCode() : "";
        return key(rank, nick, afk(player), hookText(player, "title"), hookText(player, "tag"));
    }

    /** The fingerprint itself (pure, unit-tested). */
    static String key(RankView rank, String nick, boolean afk, String title, String tag) {
        return rank.prefix() + '\u0000' + rank.color().value() + '\u0000' + rank.weight() + '\u0000' + nick + '\u0000'
                + afk + '\u0000' + title + '\u0000' + tag;
    }

    private String hookText(Player player, String key) {
        return core.placeholders().has(key) ? core.placeholders().text(player, key).orElse("") : "";
    }

    /** Forgets cached prefix components (reload / LuckPerms prefix edits). */
    void clearCache() {
        legacyPrefixes.clear();
    }

    /** The rank's name color: hex {@code color} meta, else the prefix color, else gray. */
    TextColor color(Player player) {
        TextColor color = core.ranks().rankColor(player);
        return color == null ? DEFAULT_NAME : color;
    }

    /** Name in the rank color; a nickname (perks module) keeps its own colors on top of the rank color. */
    Component name(Player player) {
        return name(player, color(player));
    }

    /** {@link #name(Player)} with an already-read rank color. */
    Component name(Player player, TextColor color) {
        Component display = player.displayName();
        String plain = PlainTextComponentSerializer.plainText().serialize(display);
        if (plain.isEmpty() || plain.equals(player.getName())) {
            return Component.text(player.getName(), color);
        }
        return Component.text().color(color).append(display).build();
    }

    boolean hasNick(Player player) {
        return !PlainTextComponentSerializer.plainText().serialize(player.displayName()).equals(player.getName());
    }

    /** Placeholder value from another module (e.g. {@code title}, {@code tag}), empty if not registered. */
    Component hook(Player player, String key) {
        if (!core.placeholders().has(key)) {
            return Component.empty();
        }
        return core.placeholders().component(player, key).orElse(Component.empty());
    }

    boolean afk(Player player) {
        return core.placeholders().has("afk")
                && core.placeholders().text(player, "afk").map("true"::equalsIgnoreCase).orElse(false);
    }

    /** Wraps a part ({@code <value> }) around a component, or empty if the component or template is empty. */
    Component part(Audience viewer, String template, Component value) {
        if (template == null || template.isEmpty() || Component.empty().equals(value)
                || PlainTextComponentSerializer.plainText().serialize(value).isEmpty()) {
            return Component.empty();
        }
        return core.messages().parse(viewer, template, Msg.c("value", value));
    }

    /** The tab entry of {@code player} (viewer-independent; Geyser rounds colors for Bedrock). */
    Component tabEntry(Player player) {
        return tabEntry(player, rankView(player));
    }

    /** {@link #tabEntry(Player)} with the rank parts from a snapshot. */
    Component tabEntry(Player player, RankView rank) {
        ChatConfig c = config.get();
        Audience viewer = Audience.empty();
        return core.messages().parse(viewer, c.tabFormat,
                Msg.c("rank", rankPrefix(viewer, rank.prefix())),
                Msg.c("name", name(player, rank.color())),
                Msg.c("title", part(viewer, c.tabTitlePart, hook(player, "title"))),
                Msg.c("tag", part(viewer, c.tabTagPart, hook(player, "tag"))),
                Msg.c("afk", afk(player) ? core.messages().parse(viewer, c.afkPart) : Component.empty()));
    }

    /** Nearest classic color of the rank color (nametag name color supports only these). */
    NamedTextColor teamColor(Player player) {
        return NamedTextColor.nearestTo(color(player));
    }

    /** {@link #teamColor(Player)} from a snapshot. */
    static NamedTextColor teamColor(RankView rank) {
        return NamedTextColor.nearestTo(rank.color());
    }

    Component afkSuffix(Player player) {
        return afk(player) ? core.messages().parse(Audience.empty(), config.get().afkPart) : Component.empty();
    }

    /** Chat-line placeholders for one viewer. */
    TagResolver chatParts(Audience viewer, Player player, Component name, Component message) {
        ChatConfig c = config.get();
        return TagResolver.resolver(
                Msg.c("title", part(viewer, c.titlePart, hook(player, "title"))),
                Msg.c("tag", part(viewer, c.tagPart, hook(player, "tag"))),
                Msg.c("rank", rank(viewer, player)),
                Msg.c("name", name),
                Msg.c("message", message));
    }
}
