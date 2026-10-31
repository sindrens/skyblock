package net.skybornmc.skyblock.common.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a rank badge (PACK_BUILD, RESOURCE_PACK_GUI §6.2). Pure and thread-safe.
 *
 * <p>The <b>name</b> is the LuckPerms group display name, the <b>look</b> comes from the group id
 * ({@link PackIds#badge}); an unknown group gets the plain Ashborn look. Four forms:</p>
 * <ul>
 *   <li><b>pack viewer</b>: left cap, letters, right cap in a badge font, a {@link PackIds#letterGap()} between every
 *       two glyphs, shadow off. One colour, or a gradient across the letters (Immortal);</li>
 *   <li><b>simple</b> and <b>Java without the pack</b>: {@code [Name]} in the rank colour (a text gradient for gradient
 *       ranks);</li>
 *   <li><b>Bedrock</b>: {@code [Name]} with the badge's legacy colour code(s). Private-use characters never reach
 *       Bedrock players (they render as controller icons).</li>
 * </ul>
 */
public final class Badges {

    /** The group whose members may pick their own gradient. */
    public static final String CHOICE_GROUP = "immortal";

    private static final Key STILL_FONT = Key.key("phoenix", "badge");

    private Badges() {
    }

    /** The badge of {@code group} with {@code displayName}, as seen by a viewer with {@code o}. */
    public static Component render(PackIds ids, String group, String displayName, BadgeOptions o) {
        PackIds.Badge badge = ids.badge(group);
        String name = cleanName(displayName, group);
        if (badge == null) {
            return Component.text('[' + name + ']');
        }
        if (o.bedrock()) {
            return bedrock(badge, name);
        }
        if (!o.pack() || o.simple()) {
            return simple(badge, name, o);
        }
        return font(ids, badge, name, o);
    }

    /** The Bedrock / simple lines for {@code /rank preview}, without a viewer. */
    public static Component bedrockLine(PackIds ids, String group, String displayName) {
        return render(ids, group, displayName, new BadgeOptions(false, true, false, false, false, List.of()));
    }

    // ------------------------------------------------------------------ font badge

    static Component font(PackIds ids, PackIds.Badge badge, String name, BadgeOptions o) {
        List<TextColor> colors = colors(badge, o);
        String letters = glyphLetters(name);
        int n = letters.length();
        List<TextColor> letterColors = n == 0 ? List.of() : gradient(colors, n);
        TextColor capLeft = capColor(badge, letterColors, colors, true);
        TextColor capRight = capColor(badge, letterColors, colors, false);
        String gap = ids.letterGap() == null ? "" : ids.letterGap();

        List<String> glyphs = new ArrayList<>(n + 2);
        List<TextColor> glyphColors = new ArrayList<>(n + 2);
        if (!badge.capLeft().isEmpty()) {
            glyphs.add(badge.capLeft());
            glyphColors.add(capLeft);
        }
        for (int i = 0; i < n; i++) {
            glyphs.add(String.valueOf(letters.charAt(i)));
            glyphColors.add(letterColors.get(i));
        }
        if (!badge.capRight().isEmpty()) {
            glyphs.add(badge.capRight());
            glyphColors.add(capRight);
        }
        // Merge neighbours with the same colour into one text; a gap belongs to the glyph before it.
        TextComponent.Builder root = Component.text().font(Key.key(fontId(badge, o))).shadowColor(ShadowColor.none());
        StringBuilder run = new StringBuilder();
        TextColor runColor = null;
        for (int i = 0; i < glyphs.size(); i++) {
            TextColor c = glyphColors.get(i);
            if (runColor != null && !runColor.equals(c)) {
                root.append(Component.text(run.toString(), runColor));
                run.setLength(0);
            }
            runColor = c;
            run.append(glyphs.get(i));
            if (i < glyphs.size() - 1) {
                run.append(gap);
            }
        }
        if (runColor != null) {
            root.append(Component.text(run.toString(), runColor));
        }
        return root.build();
    }

    /**
     * The font for this badge and viewer: staff {@code _solid}; the shine font only when the badge shines and the
     * viewer has no reduced motion (else the still font); {@code _large} inserted for bigger text.
     */
    public static String fontId(PackIds.Badge badge, BadgeOptions o) {
        String declared = badge.font() == null ? STILL_FONT.asString() : badge.font();
        String suffix;
        if (declared.endsWith("_solid") || "staff".equals(badge.family())) {
            suffix = "_solid";
        } else if (badge.shine() && !o.reducedMotion()) {
            suffix = "_shine";
        } else {
            suffix = "";
        }
        return "phoenix:badge" + (o.large() ? "_large" : "") + suffix;
    }

    /** The colours that draw this badge: the wearer's approved gradient for {@link #CHOICE_GROUP}, else the rank's own. */
    public static List<TextColor> colors(PackIds.Badge badge, BadgeOptions o) {
        if (CHOICE_GROUP.equals(badge.group()) && !o.gradient().isEmpty()) {
            return o.gradient();
        }
        List<TextColor> own = new ArrayList<>();
        for (String hex : badge.colors()) {
            TextColor c = TextColor.fromHexString(hex.trim());
            if (c != null) {
                own.add(c);
            }
        }
        if (own.isEmpty()) {
            own.add(TextColor.color(0xFFFFFF));
        }
        return own;
    }

    /** The one colour that stands for the rank (name in chat, tab, nametag, scoreboard): the middle of the gradient. */
    public static TextColor nameColor(PackIds ids, String group, BadgeOptions o) {
        List<TextColor> colors = colors(ids.badge(group), o);
        return colors.get(colors.size() / 2);
    }

    private static TextColor capColor(PackIds.Badge badge, List<TextColor> letterColors, List<TextColor> colors, boolean left) {
        if (badge.capColor() != null && !badge.capColor().isBlank()) {
            TextColor c = TextColor.fromHexString(badge.capColor().trim());
            if (c != null) {
                return c;
            }
        }
        if (!letterColors.isEmpty()) {
            return left ? letterColors.getFirst() : letterColors.getLast();
        }
        return left ? colors.getFirst() : colors.getLast();
    }

    /** {@code n} colours spread over the stops (linear between neighbouring stops). */
    public static List<TextColor> gradient(List<TextColor> stops, int n) {
        List<TextColor> out = new ArrayList<>(n);
        if (stops.size() == 1 || n == 1) {
            for (int i = 0; i < n; i++) {
                out.add(stops.getFirst());
            }
            return out;
        }
        for (int k = 0; k < n; k++) {
            double t = (double) k / (n - 1) * (stops.size() - 1);
            int i = Math.min((int) t, stops.size() - 2);
            out.add(TextColor.lerp((float) (t - i), stops.get(i), stops.get(i + 1)));
        }
        return out;
    }

    /** The characters the badge fonts draw: A-Z, a-z, 0-9, space and {@code + - ! ' .}; anything else is left out. */
    public static String glyphLetters(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ' || c == '+'
                    || c == '-' || c == '!' || c == '\'' || c == '.') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ text forms

    static Component simple(PackIds.Badge badge, String name, BadgeOptions o) {
        List<TextColor> colors = colors(badge, o);
        String text = '[' + name + ']';
        if (colors.size() == 1) {
            return Component.text(text, colors.getFirst());
        }
        List<TextColor> per = gradient(colors, text.length());
        TextComponent.Builder out = Component.text();
        for (int i = 0; i < text.length(); i++) {
            out.append(Component.text(text.charAt(i), per.get(i)));
        }
        return out.build();
    }

    static Component bedrock(PackIds.Badge badge, String name) {
        String codes = badge.bedrock() == null || badge.bedrock().isBlank() ? "&7" : badge.bedrock();
        String[] segments = codes.split(">");
        String text = '[' + name + ']';
        StringBuilder legacy = new StringBuilder();
        int m = segments.length;
        for (int k = 0; k < m; k++) {
            int from = text.length() * k / m;
            int to = text.length() * (k + 1) / m;
            if (to > from) {
                legacy.append(segments[k]).append(text, from, to);
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy.toString());
    }

    /** A usable display name: no private-use characters (a rename can't fake icons), never blank. */
    static String cleanName(String displayName, String group) {
        String name = PrivateUse.strip(displayName == null ? "" : displayName).replace("&", "").replace("§", "").strip();
        if (name.isEmpty()) {
            String id = group == null || group.isBlank() ? "default" : group;
            name = id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
        }
        return name;
    }
}
