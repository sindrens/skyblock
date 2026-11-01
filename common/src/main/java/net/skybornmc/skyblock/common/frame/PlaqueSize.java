package net.skybornmc.skyblock.common.frame;

import java.util.Locale;

/** Plaque widths (GUI-FONT D2): small 80, medium 112, large 144 px (inner 64 / 96 / 128). */
public enum PlaqueSize {
    S, M, L;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
