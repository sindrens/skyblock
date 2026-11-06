package net.skybornmc.skyblock.common.frame;

import java.util.List;

/**
 * Cursor moves with the space glyphs of {@code phoenix:frame} (GUI-FONT section 3): the binary decomposition of
 * {@code dx}, largest step first (at most 8 glyphs while |dx| is below 256; larger moves repeat the 128 step). Pure.
 */
public final class Space {

    private Space() {
    }

    /** The space glyphs that move the text cursor by {@code dx} pixels; {@code ""} for 0. */
    public static String move(FrameMetrics metrics, int dx) {
        if (dx == 0) {
            return "";
        }
        List<FrameMetrics.SpaceGlyph> glyphs = dx < 0 ? metrics.negative() : metrics.positive();
        int left = Math.abs(dx);
        StringBuilder out = new StringBuilder();
        for (int i = glyphs.size() - 1; i >= 0; i--) {
            int step = Math.abs(glyphs.get(i).step());
            while (left >= step && step > 0) {
                out.append(glyphs.get(i).cp());
                left -= step;
            }
        }
        if (left != 0) {
            throw new IllegalStateException("space glyphs cannot make a move of " + dx);
        }
        return out.toString();
    }
}
