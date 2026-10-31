package net.skybornmc.skyblock.common.pack;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The Phoenix resource pack (RP1–RP6), published by the pack module: {@code core.services().optional(PackService.class)}.
 * Without the pack module everyone counts as a no-pack viewer, so every caller renders its plain fallback.
 *
 * <p>Rule (PACK_BUILD_PROMPT must-have 4): glyphs, badge fonts, menu backgrounds and pack icon models go only to
 * viewers for whom {@link #hasPack} is true. Everyone else gets plain text and vanilla items, never a missing texture.
 */
public interface PackService {

    /** Where a player stands with the pack. */
    enum Status {
        /** Not sent yet, or still downloading. */
        PENDING,
        /** Loaded on the client: render pack content for this viewer. */
        LOADED,
        /** Declined the prompt (kicked when the pack is required). */
        DECLINED,
        /** Download or apply failed (kicked when {@code kick-on-fail}). */
        FAILED,
        /** Bedrock player (Floodgate): never sent; text fallback and Geyser mappings. */
        BEDROCK,
        /** The pack is turned off in pack.yml. */
        DISABLED
    }

    Status status(UUID player);

    /** True when this viewer should get pack content (fonts, backgrounds, icon models). Any thread. */
    default boolean hasPack(Player viewer) {
        return viewer != null && status(viewer.getUniqueId()) == Status.LOADED;
    }

    /** The ids of the pack the server is sending right now (parsed from the bundled {@code pack-ids.yml}). */
    PackIds ids();
}
