package net.skybornmc.skyblock.common.frame;

import java.util.Locale;
import java.util.Optional;

/**
 * The plaque noun (GUI-FONT 6.1, FORMAT 3.6): one short all-caps noun from a message file, never player text. Pure.
 */
public final class PlaqueFit {

    /** Characters the cartoon lettering has (besides A-Z and 0-9). */
    public static final String SYMBOLS = " &'-!?()/.,:";
    /** Most letters (spaces not counted) on the large plaque. */
    public static final int MAX_LETTERS = 16;

    /** Where the plaque and its text go: size, text width, plaque x and text x (window pixels, 0 = window left). */
    public record Fit(PlaqueSize size, int textWidth, int plaqueX, int textX) {
    }

    private PlaqueFit() {
    }

    /**
     * Upper-case, single spaces, trimmed; empty when the text has any other character than {@code A-Z 0-9} and
     * {@link #SYMBOLS}, or more than {@link #MAX_LETTERS} letters.
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || SYMBOLS.indexOf(c) >= 0;
            if (!ok) {
                return "";
            }
            if (c != ' ') {
                letters++;
            }
        }
        return letters > MAX_LETTERS ? "" : text;
    }

    /** Width of {@code text} in the lettering font, or -1 when a character has no glyph. */
    public static int width(FrameMetrics metrics, String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            int a = metrics.letter(text.charAt(i));
            if (a < 0) {
                return -1;
            }
            width += a;
        }
        return width;
    }

    /** The smallest plaque that holds the (sanitised) {@code text}; empty when it is blank or wider than the large one. */
    public static Optional<Fit> fit(FrameMetrics metrics, String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        int width = width(metrics, text);
        if (width < 0) {
            return Optional.empty();
        }
        for (PlaqueSize size : PlaqueSize.values()) {
            FrameMetrics.Plaque plaque = metrics.plaque(size);
            if (width <= plaque.inner()) {
                int plaqueX = (metrics.windowWidth() - plaque.width()) / 2;
                // the last letter's advance includes one empty pad column: centre the visible ink
                int textX = plaqueX + (plaque.width() - (width - 1)) / 2;
                return Optional.of(new Fit(size, width, plaqueX, textX));
            }
        }
        return Optional.empty();
    }
}
