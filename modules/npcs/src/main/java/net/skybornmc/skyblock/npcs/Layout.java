package net.skybornmc.skyblock.npcs;

import java.util.Locale;

/** Placement maths for holograms and NPC heads (pure, unit-tested). Minecraft yaw: south 0, west 90, north 180, east -90. */
final class Layout {

    private Layout() {
    }

    /** Yaw of a facing word ({@code south}, {@code west}, {@code north}, {@code east}); {@code NaN} when unknown/empty. */
    static float yawOf(String facing) {
        if (facing == null) {
            return Float.NaN;
        }
        return switch (facing.trim().toLowerCase(Locale.ROOT)) {
            case "south", "s" -> 0f;
            case "west", "w" -> 90f;
            case "north", "n" -> 180f;
            case "east", "e" -> -90f;
            default -> Float.NaN;
        };
    }

    /** Unit x/z step a yaw faces (yaw 0 = +z). */
    static double[] direction(float yaw) {
        double rad = Math.toRadians(yaw);
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    /**
     * A board's size from metadata, {@code "[5, 7]"} (as stored in build-points.yml) or {@code "5x7"}: {width, height}.
     * Missing or broken = {@code fallbackW × fallbackH}.
     */
    static int[] size(String raw, int fallbackW, int fallbackH) {
        if (raw == null || raw.isBlank()) {
            return new int[]{fallbackW, fallbackH};
        }
        String[] parts = raw.replace("[", "").replace("]", "").split("[,x]");
        if (parts.length != 2) {
            return new int[]{fallbackW, fallbackH};
        }
        try {
            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());
            return w > 0 && h > 0 && w <= 64 && h <= 64 ? new int[]{w, h} : new int[]{fallbackW, fallbackH};
        } catch (NumberFormatException e) {
            return new int[]{fallbackW, fallbackH};
        }
    }

    /**
     * Bottom of the text (text displays grow upward from their position) for a board centred on block row {@code y}
     * that is {@code height} blocks tall: the board covers {@code y - (height-1)/2 .. y + height/2}.
     */
    static double bottomY(double y, int height) {
        return Math.floor(y) - Math.floor((height - 1) / 2.0);
    }

    /** Bottom of a text {@code textHeight} tall, centred vertically on a board starting at {@code boardBottom}. */
    static double textBottom(double boardBottom, int height, double textHeight) {
        return boardBottom + Math.max(0, (height - textHeight) / 2.0);
    }

    /** Text scale so {@code lines} lines fill {@code fill} of a board {@code height} blocks tall, clamped. */
    static float scale(int lines, int height, double lineHeight, double fill, double min, double max) {
        if (lines <= 0 || lineHeight <= 0) {
            return (float) Math.max(min, Math.min(max, 1.0));
        }
        double s = height * fill / (lines * lineHeight);
        return (float) Math.max(min, Math.min(max, s));
    }

    /** Number of lines of a text joined with {@code <newline>} / {@code \n}. */
    static int lineCount(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return 0;
        }
        return miniMessage.split("<newline>|<br>|\n", -1).length;
    }

    /**
     * A builder's metadata text made safe for Bedrock: arrows become the style's safe arrows ({@code »}, {@code «}),
     * other symbols outside Latin-1 that Bedrock may not have are dropped; at most 64 characters.
     */
    static String bedrockSafe(String text) {
        if (text == null) {
            return "";
        }
        String safe = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";
        StringBuilder sb = new StringBuilder();
        text.strip().codePoints().forEach(cp -> {
            if (cp == '→' || cp == '⇒' || cp == '➜') {
                sb.append('»');
            } else if (cp == '←' || cp == '⇐') {
                sb.append('«');
            } else if (cp >= 32 && (cp < 127 || (cp >= 160 && cp <= 255) || safe.indexOf(cp) >= 0)) {
                sb.appendCodePoint(cp);
            }
        });
        String out = sb.toString().strip();
        return out.length() > 64 ? out.substring(0, 64) : out;
    }

    /** Block coordinate → centre of the block; coordinates that already have a fraction are kept. */
    static double centre(double coordinate) {
        return coordinate == Math.floor(coordinate) ? coordinate + 0.5 : coordinate;
    }

    /** Yaw and pitch for an entity at the origin looking at a point {@code dx, dy, dz} away (dy from its eyes). */
    static float[] lookAt(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, Math.max(-60f, Math.min(60f, pitch))};
    }

    /** Smallest absolute difference between two angles in degrees. */
    static float angleDiff(float a, float b) {
        float d = ((a - b) % 360f + 540f) % 360f - 180f;
        return Math.abs(d);
    }
}
