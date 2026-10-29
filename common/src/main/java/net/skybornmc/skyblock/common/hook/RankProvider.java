package net.skybornmc.skyblock.common.hook;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Rank information for chat, tab, nametags and placeholders. Backed by LuckPerms when it is installed (softdepend),
 * otherwise every player is "default" with no prefix.
 *
 * <p>Rank color comes from the group meta key {@code color} ({@code lp group mod meta set color "#FF8A1F"}, or a
 * legacy name like {@code gold}); without it the first color code of the prefix is used, then white.</p>
 */
public interface RankProvider {

    /** Primary group id, e.g. {@code mod}. */
    String primaryGroup(Player player);

    /** Group display name ({@code lp group mod setdisplayname Mod}), falling back to the capitalized group id. */
    String groupDisplayName(Player player);

    /** Prefix as stored in LuckPerms (legacy {@code &} codes or MiniMessage), empty if none. */
    String prefix(Player player);

    String suffix(Player player);

    /** Group weight (higher = more important; used to sort tab). */
    int weight(Player player);

    TextColor rankColor(Player player);

    /** Raw meta value of the player (inherited from groups), or null. */
    String meta(Player player, String key);

    /**
     * Calls {@code listener} on the main thread whenever a player's groups/permissions/meta are recalculated
     * (rank bought, promoted, prefix edited), with that player's UUID.
     */
    void onRankChange(Consumer<UUID> listener);

    /** True when backed by LuckPerms. */
    boolean available();

    /** Unsubscribes event listeners (plugin disable). */
    default void close() {
    }

    /** LuckPerms when enabled, otherwise {@link #NONE}. */
    static RankProvider create(Plugin plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                return LuckPermsRanks.create(plugin);
            } catch (Throwable t) { // NoClassDefFoundError if the API is missing
                plugin.getLogger().warning("LuckPerms found but its API could not be used, ranks are disabled: " + t);
            }
        }
        return NONE;
    }

    /** No rank plugin: everyone is default. */
    RankProvider NONE = new RankProvider() {
        @Override
        public String primaryGroup(Player player) {
            return "default";
        }

        @Override
        public String groupDisplayName(Player player) {
            return "Default";
        }

        @Override
        public String prefix(Player player) {
            return "";
        }

        @Override
        public String suffix(Player player) {
            return "";
        }

        @Override
        public int weight(Player player) {
            return 0;
        }

        @Override
        public TextColor rankColor(Player player) {
            return NamedTextColor.WHITE;
        }

        @Override
        public String meta(Player player, String key) {
            return null;
        }

        @Override
        public void onRankChange(Consumer<UUID> listener) {
        }

        @Override
        public boolean available() {
            return false;
        }
    };

    /** A group a player is directly in, with its LuckPerms weight. */
    record GroupWeight(String group, int weight) {
    }

    /**
     * The display rank: the heaviest group the player is directly in (LuckPerms' default
     * {@code primary-group-calculation: parents-by-weight}); on a tie the first one wins, like LuckPerms. Falls back to
     * {@code fallback} (LuckPerms' cached primary group), then {@code default}, when the player has no groups.
     */
    static String pickPrimary(Iterable<GroupWeight> parents, String fallback) {
        GroupWeight best = null;
        for (GroupWeight g : parents) {
            if (g != null && g.group() != null && (best == null || g.weight() > best.weight())) {
                best = g;
            }
        }
        if (best != null) {
            return best.group();
        }
        return fallback == null || fallback.isBlank() ? "default" : fallback;
    }

    /**
     * Parses a rank color: {@code #RRGGBB}, a legacy name ({@code gold}) or a legacy code ({@code &6}).
     * Returns null when it can't be parsed.
     */
    static TextColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            return TextColor.fromHexString(s);
        }
        if ((s.charAt(0) == '&' || s.charAt(0) == '§') && s.length() >= 2) {
            return legacy(s.charAt(1));
        }
        return NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
    }

    /** The first color in a legacy-coded prefix ({@code "&c&lADMIN"} → red, {@code "&#FF8A1F"} → hex), or null. */
    static TextColor firstLegacyColor(String prefix) {
        if (prefix == null) {
            return null;
        }
        for (int i = 0; i < prefix.length() - 1; i++) {
            char c = prefix.charAt(i);
            if (c == '&' || c == '§') {
                char code = prefix.charAt(i + 1);
                if (code == '#' && i + 8 <= prefix.length()) {
                    TextColor hex = TextColor.fromHexString(prefix.substring(i + 1, i + 8));
                    if (hex != null) {
                        return hex;
                    }
                }
                TextColor color = legacy(code);
                if (color != null) {
                    return color;
                }
            }
        }
        return null;
    }

    /** Legacy color code character to color, or null for formats/unknown codes. */
    static TextColor legacy(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }
}
