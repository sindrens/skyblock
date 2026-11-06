package net.skybornmc.skyblock.common.frame;

/**
 * The kill switches of the frame layer ({@code menus.yml → gui.font.*}, GUI-FONT 6.5). {@code panels} false also
 * switches the plaque and the tiles off; {@code titles} needs {@code panels}; {@code logo} is independent of the panel
 * switches (but not of {@code enabled}).
 */
public record FrameSwitches(boolean enabled, boolean panels, boolean titles, boolean tiles, boolean logo) {

    /** Everything on (the staff glyph test ignores the switches). */
    public static final FrameSwitches ALL = new FrameSwitches(true, true, true, true, true);

    /** Panels are drawn. */
    public boolean panelsOn() {
        return enabled && panels;
    }

    /** The plaque with cartoon letters is drawn (needs panels). */
    public boolean titlesOn() {
        return panelsOn() && titles;
    }

    /** Soft tiles behind filled slots are drawn (needs panels). */
    public boolean tilesOn() {
        return panelsOn() && tiles;
    }

    /** The scoreboard / tab logo glyph is offered. */
    public boolean logoOn() {
        return enabled && logo;
    }
}
