package net.skybornmc.skyblock.common.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.skybornmc.skyblock.common.hook.RankProvider;
import net.skybornmc.skyblock.common.text.BedrockCheck;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Core placeholders available from the start. Modules add their own (e.g. {@code balance}, {@code island_level}).
 *
 * <table>
 *   <tr><td>player</td><td>account name</td></tr>
 *   <tr><td>displayname</td><td>display name (nicknames later), rank colored</td></tr>
 *   <tr><td>rank</td><td>group display name, rank colored</td></tr>
 *   <tr><td>rank_prefix</td><td>LuckPerms prefix ({@code &} codes or MiniMessage)</td></tr>
 *   <tr><td>rank_color</td><td>rank color as hex ({@code #FF8A1F})</td></tr>
 *   <tr><td>online / max_players</td><td>player counts</td></tr>
 *   <tr><td>playtime</td><td>total playtime ({@code 12h 5m})</td></tr>
 *   <tr><td>ping</td><td>ping in ms</td></tr>
 *   <tr><td>world</td><td>current world name</td></tr>
 *   <tr><td>bedrock</td><td>{@code true}/{@code false}</td></tr>
 * </table>
 */
public final class BuiltinPlaceholders {

    public static final String OWNER = "core";

    private BuiltinPlaceholders() {
    }

    /**
     * @param playtimeMs total playtime in milliseconds for an online player (e.g. from the player service), or
     *                   {@code null} to fall back to the vanilla statistic
     */
    public static void register(PlaceholderRegistry registry, Server server, RankProvider ranks, BedrockCheck bedrock,
                                ToLongFunction<UUID> playtimeMs) {
        registry.registerText(OWNER, "player", p -> p == null ? "" : p.getName());
        registry.register(OWNER, "displayname", p -> p == null ? Component.empty()
                : p.displayName().colorIfAbsent(ranks.rankColor(p)));
        registry.register(OWNER, "rank", new Placeholder() {
            @Override
            public Component component(Player p) {
                return p == null ? Component.empty() : Component.text(ranks.groupDisplayName(p), ranks.rankColor(p));
            }

            @Override
            public String text(Player p) {
                return p == null ? "" : ranks.groupDisplayName(p);
            }
        });
        registry.register(OWNER, "rank_prefix", new Placeholder() {
            @Override
            public Component component(Player p) {
                return p == null ? Component.empty() : prefixComponent(ranks.prefix(p));
            }

            @Override
            public String text(Player p) {
                return p == null ? "" : ranks.prefix(p);
            }
        });
        registry.registerText(OWNER, "rank_color", p -> p == null ? "" : ranks.rankColor(p).asHexString());
        registry.registerText(OWNER, "online", p -> Format.number(server.getOnlinePlayers().size()));
        registry.registerText(OWNER, "max_players", p -> Format.number(server.getMaxPlayers()));
        registry.registerText(OWNER, "playtime", p -> {
            if (p == null) {
                return "";
            }
            long ms = playtimeMs != null ? playtimeMs.applyAsLong(p.getUniqueId())
                    : p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 50L;
            return Format.duration(Duration.ofMillis(ms));
        });
        registry.registerText(OWNER, "ping", p -> p == null ? "" : String.valueOf(p.getPing()));
        registry.registerText(OWNER, "world", p -> p == null ? "" : p.getWorld().getName());
        registry.registerText(OWNER, "bedrock", p -> p == null ? "false" : String.valueOf(bedrock.isBedrock(p.getUniqueId())));
    }

    /** LuckPerms prefixes may use legacy {@code &} codes (our server/permissions/base.txt) or MiniMessage. */
    static Component prefixComponent(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Component.empty();
        }
        if (prefix.indexOf('<') >= 0 && prefix.indexOf('&') < 0) {
            return MiniMessage.miniMessage().deserialize(prefix);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
    }
}
