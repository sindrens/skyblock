package net.skybornmc.skyblock.common.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.skybornmc.skyblock.common.text.PrivateUse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a rank badge as text (I7 phase 0: no resource pack). Pure and thread-safe.
 *
 * <p>The <b>name</b> is the LuckPerms group display name, the <b>look</b> comes from the group id
 * ({@link RankLooks#look}); an unknown group gets the {@code default} look. Two forms:</p>
 * <ul>
 *   <li><b>Java</b>: {@code [Name]} in the rank colour (a per-character gradient for gradient ranks such as
 *       Immortal);</li>
 *   <li><b>Bedrock</b>: {@code [Name]} with the look's legacy colour code(s).</li>
 * </ul>
 */
public final class Badges {

    /** The group whose members may pick their own gradient. */
    public static final String CHOICE_GROUP = "immortal";

    private Badges() {
    }

    /** The badge of {@code group} with {@code displayName}, as seen by a viewer with {@code o}. */
    public static Component render(RankLooks looks, String group, String displayName, BadgeOptions o) {
        RankLooks.Look look = looks.look(group);
        String name = cleanName(displayName, group);
        if (o.bedrock()) {
            return bedrock(look, name);
        }
        return simple(look, group, name, o);
    }

    /** The colours that draw this badge: the wearer's approved gradient for {@link #CHOICE_GROUP}, else the rank's own. */
    public static List<TextColor> colors(RankLooks.Look look, String group, BadgeOptions o) {
        if (CHOICE_GROUP.equals(group) && !o.gradient().isEmpty()) {
            return o.gradient();
        }
        return look.colors();
    }

    /** The one colour that stands for the rank (name in chat, tab, nametag, scoreboard): the middle of the gradient. */
    public static TextColor nameColor(RankLooks looks, String group, BadgeOptions o) {
        List<TextColor> colors = colors(looks.look(group), group, o);
        return colors.get(colors.size() / 2);
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

    static Component simple(RankLooks.Look look, String group, String name, BadgeOptions o) {
        List<TextColor> colors = colors(look, group, o);
        String text = '[' + name + ']';
        // A look whose legacy code is bold (&l: staff, Immortal, Firecrown) is bold on Java too (owner: "&e&l for owner").
        boolean bold = look.bedrock() != null && look.bedrock().toLowerCase(Locale.ROOT).contains("&l");
        if (colors.size() == 1) {
            Component one = Component.text(text, colors.getFirst());
            return bold ? one.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD) : one;
        }
        List<TextColor> per = gradient(colors, text.length());
        TextComponent.Builder out = Component.text();
        for (int i = 0; i < text.length(); i++) {
            out.append(Component.text(text.charAt(i), per.get(i)));
        }
        return bold ? out.build().decorate(net.kyori.adventure.text.format.TextDecoration.BOLD) : out.build();
    }

    static Component bedrock(RankLooks.Look look, String name) {
        List<String> segments = look.bedrockSegments();
        String text = '[' + name + ']';
        StringBuilder legacy = new StringBuilder();
        int m = segments.size();
        for (int k = 0; k < m; k++) {
            int from = text.length() * k / m;
            int to = text.length() * (k + 1) / m;
            if (to > from) {
                legacy.append(segments.get(k)).append(text, from, to);
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
