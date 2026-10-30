package net.skybornmc.skyblock.npcs;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;

/**
 * The entity set of one point: an NPC (body + name text), a hologram (text + optional click box) or a leaderboard
 * (text + optional click box). One set per point, spawned only while its chunk's entities are loaded, never saved
 * ({@code persistent = false}). Main thread.
 */
final class Placed {

    enum Kind { NPC, HOLOGRAM, BOARD }

    final Kind kind;
    /** Unique reference ({@link Spots.Npc#ref()} / {@link Spots.Text#ref()}); texts are looked up by {@link #pointId()}. */
    final String id;
    final String world;
    final int chunkX;
    final int chunkZ;
    /** Set for {@link Kind#NPC}. */
    final Spots.Npc npc;
    /** Set for holograms and boards. */
    final Spots.Text text;

    LivingEntity body;
    TextDisplay label;
    Interaction click;
    /** Where the NPC stands (resolved at spawn: on top of a solid pad block), or the text position. */
    Location location;
    float lastYaw;
    float lastPitch;
    boolean lookingAway = true;

    Placed(Kind kind, Spots.Npc npc, Spots.Text text) {
        this.kind = kind;
        this.npc = npc;
        this.text = text;
        this.id = npc != null ? npc.ref() : text.ref();
        this.world = npc != null ? npc.world() : text.world();
        double x = npc != null ? npc.x() : text.x();
        double z = npc != null ? npc.z() : text.z();
        this.chunkX = ((int) Math.floor(x)) >> 4;
        this.chunkZ = ((int) Math.floor(z)) >> 4;
    }

    /** The builder's id (message keys use it). */
    String pointId() {
        return npc != null ? npc.id() : text.id();
    }

    /** Lookup key across kinds, e.g. {@code npc:bank}. */
    String key() {
        return key(kind, id);
    }

    static String key(Kind kind, String id) {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id;
    }

    long chunkKey() {
        return chunkKey(chunkX, chunkZ);
    }

    static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    boolean spawned() {
        Entity main = kind == Kind.NPC ? body : label;
        return main != null && main.isValid();
    }

    boolean in(World w) {
        return w != null && w.getName().equals(world);
    }

    /** The position shown to staff (the NPC's feet or the text), computed from the point when not spawned yet. */
    Location where(World w) {
        if (location != null) {
            return location.clone();
        }
        if (npc != null) {
            return new Location(w, Layout.centre(npc.x()), npc.y(), Layout.centre(npc.z()), npc.yaw(), 0f);
        }
        return new Location(w, Layout.centre(text.x()), text.y(), Layout.centre(text.z()));
    }

    void remove() {
        if (body != null) {
            body.remove();
        }
        if (label != null) {
            label.remove();
        }
        if (click != null) {
            click.remove();
        }
        body = null;
        label = null;
        click = null;
        lookingAway = true;
    }
}
