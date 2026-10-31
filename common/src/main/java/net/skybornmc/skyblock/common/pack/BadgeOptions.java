package net.skybornmc.skyblock.common.pack;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Locale;

/**
 * How one viewer sees a rank badge, plus the wearer's Immortal colour choice.
 *
 * @param pack          the viewer has the Phoenix pack loaded (badge fonts are allowed)
 * @param bedrock       the viewer is a Bedrock player (private-use characters must never reach them)
 * @param reducedMotion "Reduced motion": the still font instead of the shine font
 * @param simple        "Simple rank badges": plain coloured {@code [Name]} text
 * @param large         "Bigger rank text": the {@code _large} font variants
 * @param gradient      the wearer's approved Immortal gradient, empty = the rank's own colours (ignored for other ranks)
 */
public record BadgeOptions(boolean pack, boolean bedrock, boolean reducedMotion, boolean simple, boolean large,
                           List<TextColor> gradient) {

    /** Plain text for everybody: no pack, Java. Used for the console and for shared tab/chat text. */
    public static final BadgeOptions PLAIN = new BadgeOptions(false, false, false, false, false, List.of());

    public BadgeOptions {
        gradient = gradient == null ? List.of() : List.copyOf(gradient);
        if (bedrock) {
            pack = false; // a Bedrock player never gets pack content
        }
    }

    public BadgeOptions withGradient(List<TextColor> colors) {
        return new BadgeOptions(pack, bedrock, reducedMotion, simple, large, colors);
    }

    /** Same viewer without the wearer's gradient (the viewer-only part, used to compare a board's variant). */
    public BadgeOptions viewerOnly() {
        return gradient.isEmpty() ? this : withGradient(List.of());
    }

    /** True when the badge is drawn in the badge font for this viewer. */
    public boolean fontBadge() {
        return pack && !bedrock && !simple;
    }

    /** Cache key: equal keys draw the same badge. */
    public String key() {
        StringBuilder sb = new StringBuilder(24);
        sb.append(pack ? 'p' : '-').append(bedrock ? 'b' : '-').append(reducedMotion ? 'r' : '-')
                .append(simple ? 's' : '-').append(large ? 'l' : '-');
        for (TextColor c : gradient) {
            sb.append(String.format(Locale.ROOT, ":%06x", c.value()));
        }
        return sb.toString();
    }
}
