package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;

/**
 * The muted "Click me" last line of a clickable hologram (A5-R1-01), pure: only holograms that get a click box
 * ({@code hologram-help.<id>} exists and {@code holograms.clickable} is on) get it; boards, portals and signs stay as
 * they are. The line count grows by one so {@code Layout.scale} still fits the board.
 */
final class ClickHint {

    private ClickHint() {
    }

    static Component append(Component text, Component hint, boolean clickable) {
        return clickable ? text.append(Component.newline()).append(hint) : text;
    }

    static int lines(int lines, boolean clickable) {
        return clickable ? lines + 1 : lines;
    }
}
