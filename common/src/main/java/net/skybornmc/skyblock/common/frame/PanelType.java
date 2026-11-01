package net.skybornmc.skyblock.common.frame;

import java.util.Locale;
import java.util.Optional;

/** The three framed chest sizes (FORMAT section 3, GUI-FONT 6.1). Menus with 1, 2 or 5 rows are never framed. */
public enum PanelType {
    P3(3), P4(4), P6(6);

    private final int rows;

    PanelType(int rows) {
        this.rows = rows;
    }

    public int rows() {
        return rows;
    }

    /** Key in {@code frame-metrics.json} ({@code p3}, {@code p4}, {@code p6}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The panel of a chest with {@code rows} rows; empty for 1, 2 and 5 rows. */
    public static Optional<PanelType> forRows(int rows) {
        for (PanelType type : values()) {
            if (type.rows == rows) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
