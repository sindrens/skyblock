package net.skybornmc.skyblock.depths.engine;

/**
 * Who a player sees while in The Depths (M4.3 hook; the setting and quick-toggle item come with the zones). Stored per
 * player through {@link Depths#visibility}; nothing hides players yet.
 */
public enum Visibility {
    /** Every other player. */
    EVERYONE,
    /** Only members of the same island (needs the islands module; until then nobody shares an island). */
    ISLAND,
    /** Nobody. */
    NONE
}
