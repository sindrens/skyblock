package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * A chat "card" (docs/UI_STYLE.md 3.3, plan PLAN_TUTORIAL_CHAT section 2): one chat message of centred lines
 * <pre>
 *                (empty line)
 *                    SHOP                 bold gradient title
 *         Need blocks for your island?    1-3 body lines, highlighted words in the text
 *     Buy BUILDING MATERIALS instantly with
 *                 » /shop «               clickable action with a hover text
 *                (empty line)
 * </pre>
 * Lines are centred by pixel width ({@link ChatWidth}) for Java players; Bedrock players get the same lines left-aligned
 * (their font has other widths). Build the parts with the viewer's {@code Messages#parse} so colors match the viewer.
 */
public final class ChatCard {

    /** The empty line above and below a card (a single space: some clients drop fully empty lines). */
    private static final Component BLANK = Component.text(" ");

    private ChatCard() {
    }

    /**
     * The whole card as one message.
     *
     * @param title  title line, made bold unless it sets bold itself
     * @param body   body lines (empty components are skipped)
     * @param action the action line ({@code » /shop «}), or null for none
     * @param click  click of the action line (run a command or open a link), or null
     * @param hover  hover text of the action line, or null
     * @param center true for Java players (centred), false for Bedrock (left-aligned)
     */
    public static Component render(Component title, List<Component> body, Component action, ClickEvent click,
                                   Component hover, boolean center) {
        List<Component> lines = new ArrayList<>();
        lines.add(BLANK);
        if (title != null && !isBlank(title)) {
            lines.add(line(title.decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.TRUE), center));
        }
        if (body != null) {
            for (Component b : body) {
                if (b != null && !isBlank(b)) {
                    lines.add(line(b, center));
                }
            }
        }
        if (action != null && !isBlank(action)) {
            Component a = action;
            if (click != null) {
                a = a.clickEvent(click);
            }
            if (hover != null && !isBlank(hover)) {
                a = a.hoverEvent(HoverEvent.showText(hover));
            }
            lines.add(line(a, center));
        }
        lines.add(BLANK);
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** One line, centred (a run of plain spaces in front) or as it is. */
    static Component line(Component content, boolean center) {
        if (!center) {
            return content;
        }
        String pad = ChatWidth.pad(width(content));
        return pad.isEmpty() ? content : Component.text().append(Component.text(pad)).append(content).build();
    }

    /** Centres one line for a Java chat box (the same rule as card lines). */
    public static Component center(Component content) {
        return line(content, true);
    }

    /** Pixel width of a component in the default font: text of every child, bold where the style (inherited) is bold. */
    public static int width(Component component) {
        return component == null ? 0 : width(component, false);
    }

    private static int width(Component component, boolean parentBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.TRUE || (state == TextDecoration.State.NOT_SET && parentBold);
        int total;
        if (component instanceof TextComponent text) {
            total = ChatWidth.width(text.content(), bold);
        } else {
            total = ChatWidth.width(PlainTextComponentSerializer.plainText().serialize(component.children(List.of())), bold);
        }
        for (Component child : component.children()) {
            total += width(child, bold);
        }
        return total;
    }

    private static boolean isBlank(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).isBlank();
    }
}
