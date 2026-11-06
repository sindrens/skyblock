package net.skybornmc.skyblock.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.rank.BadgeGradients;
import net.skybornmc.skyblock.common.rank.BadgeOptions;
import net.skybornmc.skyblock.common.rank.BadgeService;
import net.skybornmc.skyblock.common.rank.BadgeSettings;
import net.skybornmc.skyblock.common.rank.Badges;
import net.skybornmc.skyblock.common.rank.RankLooks;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The {@link BadgeService}: rank badges per viewer as plain coloured {@code [Name]} text, from chat.yml
 * {@code ranks.looks} ({@link RankLooks}), the viewer's client (Java or Bedrock) and the wearer's Immortal colour.
 */
final class RankBadges implements BadgeService {

    private static final int CACHE_MAX = 1024;

    private final Core core;
    private final Supplier<ChatConfig> config;
    private final Map<String, Component> cache = new ConcurrentHashMap<>();
    private volatile Map<String, List<TextColor>> gradients = Map.of();
    private volatile RankLooks looks = RankLooks.defaults();

    RankBadges(Core core, Supplier<ChatConfig> config) {
        this.core = core;
        this.config = config;
        reload();
    }

    /** Re-reads the approved gradients (chat.yml) and drops cached badges. */
    void reload() {
        gradients = BadgeGradients.parse(config.get().immortalGradients);
        looks = RankLooks.parse(config.get().rankLooks, problem -> core.plugin().getLogger().warning("chat.yml " + problem));
        cache.clear();
    }

    private PlayerSettings settings() {
        return core.services().optional(PlayerSettings.class).orElse(null);
    }

    @Override
    public BadgeOptions options(Audience viewer) {
        if (!(viewer instanceof Player p)) {
            return BadgeOptions.PLAIN;
        }
        return core.bedrock().isBedrock(p.getUniqueId()) ? BadgeOptions.BEDROCK : BadgeOptions.PLAIN;
    }

    @Override
    public Component badge(Player player, Audience viewer) {
        String group = core.ranks().primaryGroup(player);
        return badge(group, core.ranks().groupDisplayName(player), options(viewer).withGradient(gradient(group, player)));
    }

    @Override
    public Component badge(String group, String displayName, BadgeOptions options) {
        String key = group + '\u0000' + displayName + '\u0000' + options.key();
        Component cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (cache.size() > CACHE_MAX) {
            cache.clear();
        }
        Component built = Badges.render(looks, group, displayName, options);
        cache.put(key, built);
        return built;
    }

    @Override
    public List<TextColor> gradient(String group, Player player) {
        return gradientById(gradientChoice(group, player));
    }

    /** The approved gradient with this id, empty for "" or an id that is no longer approved. */
    List<TextColor> gradientById(String id) {
        return id == null || id.isEmpty() ? List.of() : gradients.getOrDefault(id, List.of());
    }

    @Override
    public String gradientChoice(String group, Player player) {
        if (!Badges.CHOICE_GROUP.equals(group) || gradients.isEmpty()) {
            return "";
        }
        PlayerSettings s = settings();
        String choice = s == null ? "" : s.choice(player.getUniqueId(), BadgeSettings.RANK_COLOR);
        return gradients.containsKey(choice) ? choice : gradients.keySet().iterator().next();
    }

    @Override
    public Optional<TextColor> color(Player player) {
        String group = core.ranks().primaryGroup(player);
        return Optional.of(Badges.nameColor(looks, group, BadgeOptions.PLAIN.withGradient(gradient(group, player))));
    }

    @Override
    public Optional<TextColor> nameColor(Player player) {
        TextColor fixed = Display.nameColor(config.get().rankNameColor);
        return fixed != null ? Optional.of(fixed) : color(player);
    }

    @Override
    public List<String> gradientIds() {
        return List.copyOf(gradients.keySet());
    }

    /** The rank looks from chat.yml {@code ranks.looks}. */
    RankLooks looks() {
        return looks;
    }

    /** The rank colour used by the rank provider (every group has a look: its own line or the {@code default} line). */
    TextColor colorOrNull(Player player) {
        return color(player).orElse(null);
    }
}
