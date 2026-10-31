package net.skybornmc.skyblock.common.npc;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Live texts for build holograms that another module writes (the crate yard's key board lists the crate odds from
 * {@code crates.yml}). Published by the npcs module ({@code core.services().optional(HologramTexts.class)}); the npcs
 * module still places, scales, removes and makes the hologram clickable, the owner only supplies its lines. A text
 * replaces the hologram's own text from {@code messages/npcs.yml}; clearing it brings that text back. Main thread.
 */
public interface HologramTexts {

    /**
     * Replaces the text of the build hologram {@code hologramId} with {@code lines} (one component per line). The hologram
     * is respawned with the new text if it is shown right now. Empty lines = same as {@link #clear(String, String)}.
     */
    void set(String owner, String hologramId, List<Component> lines);

    /** Gives the hologram its own text back (only if {@code owner} set it). */
    void clear(String owner, String hologramId);

    /** Removes every text of {@code owner} (call when that module disables). */
    void clearAll(String owner);
}
