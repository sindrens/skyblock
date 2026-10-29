package net.skybornmc.skyblock.motd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Turns MOTD templates into components: fills in the animation phase and centers lines in the server list.
 * Pure logic (no server access), so it is unit-tested.
 */
public final class MotdRenderer {

    /** Usable width of one MOTD line in the vanilla server list, in GUI pixels. */
    static final int LINE_WIDTH = 270;
    private static final int SPACE_WIDTH = 4;

    private final MiniMessage mm = MiniMessage.miniMessage();

    /** Animation phase (-1..1) for a moment in time, quantized to {@code frames} steps per cycle. */
    public static double phaseAt(long epochMillis, long cycleMillis, int frames) {
        if (cycleMillis <= 0 || frames <= 0) {
            return 0;
        }
        long frame = (epochMillis % cycleMillis) * frames / cycleMillis;
        return (frame / (double) frames) * 2.0 - 1.0;
    }

    /** Builds the 2-line MOTD component. */
    public Component render(String line1, String line2, double phase, boolean center) {
        Component first = line(line1, phase, center);
        Component second = line(line2, phase, center);
        return first.append(Component.newline()).append(second);
    }

    Component line(String template, double phase, boolean center) {
        String withPhase = template.replace("{phase}", String.format(Locale.ROOT, "%.2f", phase));
        Component component = mm.deserialize(withPhase);
        if (!center) {
            return component;
        }
        int padding = Math.max(0, (LINE_WIDTH - width(component)) / 2 / SPACE_WIDTH);
        return Component.text(" ".repeat(padding)).append(component);
    }

    /** Approximate rendered width in pixels using the default Minecraft font (bold adds 1px per character). */
    static int width(Component component) {
        int[] total = {0};
        Deque<Boolean> bold = new ArrayDeque<>();
        bold.push(false);
        ComponentFlattener.basic().flatten(component, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                TextDecoration.State state = style.decoration(TextDecoration.BOLD);
                bold.push(state == TextDecoration.State.NOT_SET ? bold.peek() : state == TextDecoration.State.TRUE);
            }

            @Override
            public void component(String text) {
                boolean isBold = Boolean.TRUE.equals(bold.peek());
                text.codePoints().forEach(cp -> total[0] += charWidth(cp) + (isBold ? 1 : 0));
            }

            @Override
            public void popStyle(Style style) {
                if (bold.size() > 1) {
                    bold.pop();
                }
            }
        });
        return total[0];
    }

    /** Glyph width + 1px spacing for the default font. */
    static int charWidth(int cp) {
        return switch (cp) {
            case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
            case 'l', '`' -> 3;
            case ' ', 'I', 't', '[', ']', '"' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '*', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
