package net.skybornmc.skyblock.common.frame;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * Builds the text of a framed chest title (GUI-FONT 6.2). Pure: the result is a list of runs (frame font or title
 * font) that {@link FrameGlyphs} turns into Adventure components. The cursor {@code c} is in window pixels and starts
 * at {@code title_origin_x} (where vanilla draws the title):
 * <ol>
 *   <li>back to the window's left edge, the panel glyph, back to 0;</li>
 *   <li>with tiles: one cell glyph per filled container slot, in slot order, at x = 7 + 18 x column;</li>
 *   <li>with a plaque that fits: the plaque glyph centred on the window, then the noun in the lettering font;</li>
 *   <li>without one: back to the title origin, and the caller appends the plain title (it shows on the panel's top band).</li>
 * </ol>
 */
public final class PanelTitle {

    /** Which font a run uses. */
    public enum Font {
        FRAME, TITLE
    }

    /** One run of text in one font. */
    public record Run(Font font, String text) {
    }

    /** The runs; {@code plaque} false means the caller appends the plain title after them. */
    public record Built(List<Run> runs, boolean plaque) {
    }

    private PanelTitle() {
    }

    public static Built build(FrameMetrics m, PanelType type, BitSet filled, boolean tiles, Optional<PlaqueFit.Fit> plaque,
                              String plaqueText) {
        StringBuilder frame = new StringBuilder();
        int c = m.titleOriginX();
        frame.append(Space.move(m, -c));
        c = 0;
        FrameMetrics.Panel panel = m.panel(type);
        frame.append(panel.cp());
        c += panel.advance();
        frame.append(Space.move(m, -c));
        c = 0;
        if (tiles && filled != null) {
            int slots = type.rows() * 9;
            for (int slot = filled.nextSetBit(0); slot >= 0 && slot < slots; slot = filled.nextSetBit(slot + 1)) {
                int x = m.cellX0() + m.cellStep() * (slot % 9);
                frame.append(Space.move(m, x - c));
                frame.append(m.cell(slot / 9));
                c = x + m.cellAdvance();
            }
        }
        List<Run> runs = new ArrayList<>();
        if (plaque != null && plaque.isPresent() && plaqueText != null && !plaqueText.isBlank()) {
            PlaqueFit.Fit fit = plaque.get();
            FrameMetrics.Plaque glyph = m.plaque(fit.size());
            frame.append(Space.move(m, fit.plaqueX() - c));
            frame.append(glyph.cp());
            c = fit.plaqueX() + glyph.advance();
            frame.append(Space.move(m, fit.textX() - c));
            runs.add(new Run(Font.FRAME, frame.toString()));
            runs.add(new Run(Font.TITLE, plaqueText));
            return new Built(List.copyOf(runs), true);
        }
        frame.append(Space.move(m, m.titleOriginX() - c));
        runs.add(new Run(Font.FRAME, frame.toString()));
        return new Built(List.copyOf(runs), false);
    }
}
