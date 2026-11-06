package net.skybornmc.skyblock.common.rank;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Rank badges for every surface (chat, hover, nametag, scoreboard, previews), published by the chat module:
 * {@code core.services().optional(BadgeService.class)}. Without it (chat module off) callers keep today's text from the
 * rank provider. One place decides the badge and the rank colour, so a rank looks the same everywhere. The looks come
 * from chat.yml {@code ranks.looks} ({@link RankLooks}).
 */
public interface BadgeService {

    /** How {@code viewer} sees badges (Java or Bedrock). Any thread. */
    BadgeOptions options(Audience viewer);

    /** The badge of {@code player} for {@code viewer}, no trailing space. Reads the rank provider (any thread). */
    Component badge(Player player, Audience viewer);

    /** A badge for a group id and display name (tab entries from a snapshot, {@code /rank preview}). Cached. */
    Component badge(String group, String displayName, BadgeOptions options);

    /** The wearer's Immortal gradient from their setting (only for {@link Badges#CHOICE_GROUP}), else empty. */
    List<TextColor> gradient(String group, Player player);

    /** The id of the wearer's chosen gradient ("" unless the group may choose). Part of the rank fingerprint. */
    String gradientChoice(String group, Player player);

    /** The rank colour of a player: the one colour behind the badge. */
    Optional<TextColor> color(Player player);

    /**
     * The colour of the player's NAME next to the rank word (chat, tab, nameplate, /list, sidebar): white by default
     * (Ranks V2, owner EP-Q14), the rank colour when the chat module's {@code ranks.name-color} is blank.
     */
    default Optional<TextColor> nameColor(Player player) {
        return color(player);
    }

    /** Ids of the approved gradients, default first. */
    List<String> gradientIds();
}
