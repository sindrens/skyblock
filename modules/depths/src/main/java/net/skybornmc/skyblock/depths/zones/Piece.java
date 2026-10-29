package net.skybornmc.skyblock.depths.zones;

/**
 * What a placeholder build puts at one position. Mapped to a block through the structure's palette
 * ({@link #paletteKey()}, with fallbacks) at build time, so the shapes stay pure and testable.
 */
public enum Piece {
    AIR(null),
    FLOOR("floor"),
    INLAY("inlay"),
    BASE("base"),
    WALL("wall"),
    TRIM("trim"),
    LIGHT("light"),
    PAD("pad"),
    FIRE("fire"),
    GROUND("ground"),
    LEAVES("leaves"),
    ARENA("arena"),
    /** The field's (first) resource block: ore or log. */
    RESOURCE(null),
    /** Invisible light source ({@code minecraft:light}) so fields are bright at midnight. */
    LIGHT_BLOCK(null),
    GATE_MINE("gate-mine"),
    GATE_FOREST("gate-forest"),
    GATE_COMBAT("gate-combat");

    private final String paletteKey;

    Piece(String paletteKey) {
        this.paletteKey = paletteKey;
    }

    /** The palette role, or {@code null} for pieces with a fixed block. */
    public String paletteKey() {
        return paletteKey;
    }

    /** Role used when the palette has no entry for this one. */
    public String fallbackKey() {
        return switch (this) {
            case INLAY, PAD, GATE_MINE, GATE_FOREST, GATE_COMBAT -> "trim";
            case GROUND, ARENA, BASE -> "floor";
            default -> null;
        };
    }

    /** Last-resort block when neither the role nor its fallback is in the palette. */
    public String defaultBlock() {
        return switch (this) {
            case AIR -> "air";
            case LIGHT_BLOCK -> "light[level=15]";
            case FIRE -> "campfire";
            case LIGHT -> "lantern";
            case LEAVES -> "oak_leaves[persistent=true]";
            case WALL -> "stone_bricks";
            case TRIM, PAD, INLAY, GATE_MINE, GATE_FOREST, GATE_COMBAT -> "polished_blackstone";
            default -> "stone";
        };
    }

    public static Piece gate(Aspect aspect) {
        return switch (aspect) {
            case MINE -> GATE_MINE;
            case FOREST -> GATE_FOREST;
            case COMBAT -> GATE_COMBAT;
        };
    }
}
