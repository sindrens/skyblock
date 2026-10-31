package net.skybornmc.skyblock.common.pack;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Rank badges for every surface (chat, hover, nametag, scoreboard, previews), published by the chat module:
 * {@code core.services().optional(BadgeService.class)}. Without it (no pack module) callers keep today's text from the
 * rank provider. One place decides the badge and the rank colour, so a rank looks the same everywhere.
 */
public interface BadgeService {

    /** True when the pack module is present, so badges are drawn from {@code pack-ids.yml}. */
    boolean enabled();

    PackIds ids();

    /** How {@code viewer} sees badges (pack loaded, Bedrock, the three /settings switches). Any thread. */
    BadgeOptions options(Audience viewer);

    /** The badge of {@code player} for {@code viewer}, no trailing space. Reads the rank provider (any thread). */
    Component badge(Player player, Audience viewer);

    /** A badge for a group id and display name (tab entries from a snapshot, {@code /rank preview}). Cached. */
    Component badge(String group, String displayName, BadgeOptions options);

    /** The wearer's Immortal gradient from their setting (only for {@link Badges#CHOICE_GROUP}), else empty. */
    List<TextColor> gradient(String group, Player player);

    /** The id of the wearer's chosen gradient ("" unless the group may choose). Part of the rank fingerprint. */
    String gradientChoice(String group, Player player);

    /** The rank colour of a player: the one colour behind the badge. Empty when badges are off. */
    Optional<TextColor> color(Player player);

    /** Ids of the approved gradients, default first. */
    List<String> gradientIds();
}
