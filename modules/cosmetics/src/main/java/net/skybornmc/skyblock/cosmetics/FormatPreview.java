package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.text.LegacyColors;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

/**
 * Renders how a player looks in chat and tab with the chat module's formats ({@code chat.yml → format.*}), for the
 * live previews in {@code /tags} and the display editor. Reads the formats through the config manager, so it never
 * depends on the chat module's classes; {@code overrides} lets the editor preview a value before saving it.
 */
final class FormatPreview {

    static final String CHAT_FILE = "chat.yml";
    static final TextColor DEFAULT_NAME = TextColor.color(0xC9C9C9);

    private final Core core;
    private final CosmeticsService cosmetics;

    FormatPreview(Core core, CosmeticsService cosmetics) {
        this.core = core;
        this.cosmetics = cosmetics;
    }

    Optional<ModuleConfig> chatConfig() {
        return Optional.ofNullable(core.configs().loaded().get(CHAT_FILE));
    }

    /** Current value of a chat.yml setting (or its override), or {@code fallback} if the chat module isn't loaded. */
    String value(String path, Map<String, String> overrides, String fallback) {
        if (overrides.containsKey(path)) {
            return overrides.get(path);
        }
        return chatConfig().flatMap(c -> core.configs().describe(c).stream().filter(i -> i.path().equals(path)).findFirst())
                .map(SettingInfo::value).map(ConfigManager::display).orElse(fallback);
    }

    Component chatLine(Player player, Component message, Map<String, String> overrides) {
        return core.messages().parse(player, value("format.chat", overrides, "<title><rank><name><tag> <c:muted>»</c:muted> <c:text><message>"),
                Msg.c("title", part(player, value("format.title-part", overrides, "<value> "), cosmetics.placeholder(player, Cosmetics.Kind.TITLE))),
                Msg.c("tag", part(player, value("format.tag-part", overrides, " <value>"), cosmetics.placeholder(player, Cosmetics.Kind.TAG))),
                Msg.c("rank", rank(player)),
                Msg.c("name", name(player)),
                Msg.c("message", message));
    }

    Component tabLine(Player player, Map<String, String> overrides) {
        return core.messages().parse(player, value("format.tab", overrides, "<title><rank><name><tag><afk>"),
                Msg.c("rank", rank(player)),
                Msg.c("name", name(player)),
                Msg.c("title", part(player, value("format.tab-title-part", overrides, "<value> "), cosmetics.placeholder(player, Cosmetics.Kind.TITLE))),
                Msg.c("tag", part(player, value("format.tab-tag-part", overrides, " <value>"), cosmetics.placeholder(player, Cosmetics.Kind.TAG))),
                Msg.c("afk", Component.empty()));
    }

    /** The AFK marker as it would look (for the editor). */
    Component afk(Player player, Map<String, String> overrides) {
        return core.messages().parse(player, value("format.afk-part", overrides, " <c:muted>[AFK]</c:muted>"));
    }

    private Component part(Player viewer, String template, Component value) {
        if (template == null || template.isEmpty() || PlainTextComponentSerializer.plainText().serialize(value).isEmpty()) {
            return Component.empty();
        }
        return core.messages().parse(viewer, template, Msg.c("value", value));
    }

    private Component rank(Player player) {
        String prefix = core.ranks().prefix(player);
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        if (LegacyColors.hasCodes(prefix) || prefix.indexOf('<') < 0) {
            return LegacyColors.toComponent(prefix);
        }
        return core.messages().parse(player, prefix);
    }

    private Component name(Player player) {
        TextColor color = core.ranks().rankColor(player);
        color = color == null ? DEFAULT_NAME : color;
        Component display = player.displayName();
        String plain = PlainTextComponentSerializer.plainText().serialize(display);
        if (plain.isEmpty() || plain.equals(player.getName())) {
            return Component.text(player.getName(), color);
        }
        return Component.text().color(color).append(display).build();
    }
}
