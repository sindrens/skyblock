package net.skybornmc.skyblock.common.rank;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Locale;

/**
 * How one viewer sees a rank badge, plus the wearer's Immortal colour choice.
 *
 * @param bedrock  the viewer is a Bedrock player (legacy colour codes, private-use characters never reach them)
 * @param gradient the wearer's approved Immortal gradient, empty = the rank's own colours (ignored for other ranks)
 */
public record BadgeOptions(boolean bedrock, List<TextColor> gradient) {

    /** Plain Java text. Used for the console and for shared tab/chat text. */
    public static final BadgeOptions PLAIN = new BadgeOptions(false, List.of());

    /** The Bedrock form. */
    public static final BadgeOptions BEDROCK = new BadgeOptions(true, List.of());

    public BadgeOptions {
        gradient = gradient == null ? List.of() : List.copyOf(gradient);
    }

    public BadgeOptions withGradient(List<TextColor> colors) {
        return new BadgeOptions(bedrock, colors);
    }

    /** Same viewer without the wearer's gradient (the viewer-only part, used to compare a board's variant). */
    public BadgeOptions viewerOnly() {
        return gradient.isEmpty() ? this : withGradient(List.of());
    }

    /** Cache key: equal keys draw the same badge. */
    public String key() {
        StringBuilder sb = new StringBuilder(24);
        sb.append(bedrock ? 'b' : '-');
        for (TextColor c : gradient) {
            sb.append(String.format(Locale.ROOT, ":%06x", c.value()));
        }
        return sb.toString();
    }
}
