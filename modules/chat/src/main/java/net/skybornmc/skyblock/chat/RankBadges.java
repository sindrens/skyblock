package net.skybornmc.skyblock.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.pack.BadgeGradients;
import net.skybornmc.skyblock.common.pack.BadgeOptions;
import net.skybornmc.skyblock.common.pack.BadgeService;
import net.skybornmc.skyblock.common.pack.BadgeSettings;
import net.skybornmc.skyblock.common.pack.Badges;
import net.skybornmc.skyblock.common.pack.PackIds;
import net.skybornmc.skyblock.common.pack.PackService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The {@link BadgeService}: rank badges per viewer from the pack's {@code pack-ids.yml}, the viewer's /settings and the
 * wearer's Immortal colour. Without the pack module ({@link PackService}) it is switched off and every surface keeps
 * the LuckPerms prefix text.
 */
final class RankBadges implements BadgeService {

    private static final int CACHE_MAX = 1024;

    private final Core core;
    private final Supplier<ChatConfig> config;
    private final Map<String, Component> cache = new ConcurrentHashMap<>();
    private volatile Map<String, List<TextColor>> gradients = Map.of();

    RankBadges(Core core, Supplier<ChatConfig> config) {
        this.core = core;
        this.config = config;
        reload();
    }

    /** Re-reads the approved gradients (chat.yml) and drops cached badges. */
    void reload() {
        gradients = BadgeGradients.parse(config.get().immortalGradients);
        cache.clear();
    }

    private PackService pack() {
        return core.services().optional(PackService.class).orElse(null);
    }

    private PlayerSettings settings() {
        return core.services().optional(PlayerSettings.class).orElse(null);
    }

    @Override
    public boolean enabled() {
        return pack() != null;
    }

    @Override
    public PackIds ids() {
        PackService p = pack();
        return p == null ? null : p.ids();
    }

    @Override
    public BadgeOptions options(Audience viewer) {
        if (!(viewer instanceof Player p)) {
            return BadgeOptions.PLAIN;
        }
        boolean bedrock = core.bedrock().isBedrock(p.getUniqueId());
        if (bedrock) {
            return new BadgeOptions(false, true, false, false, false, List.of());
        }
        PackService pack = pack();
        PlayerSettings s = settings();
        return new BadgeOptions(pack != null && pack.hasPack(p), false,
                s != null && s.get(p.getUniqueId(), BadgeSettings.REDUCED_MOTION),
                s != null && s.get(p.getUniqueId(), BadgeSettings.SIMPLE_BADGES),
                s != null && s.get(p.getUniqueId(), BadgeSettings.LARGE_BADGES), List.of());
    }

    @Override
    public Component badge(Player player, Audience viewer) {
        String group = core.ranks().primaryGroup(player);
        return badge(group, core.ranks().groupDisplayName(player), options(viewer).withGradient(gradient(group, player)));
    }

    @Override
    public Component badge(String group, String displayName, BadgeOptions options) {
        PackIds ids = ids();
        if (ids == null) {
            return Component.empty();
        }
        String key = group + '\u0000' + displayName + '\u0000' + options.key();
        Component cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (cache.size() > CACHE_MAX) {
            cache.clear();
        }
        Component built = Badges.render(ids, group, displayName, options);
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
        PackIds ids = ids();
        if (ids == null) {
            return Optional.empty();
        }
        String group = core.ranks().primaryGroup(player);
        return Optional.of(Badges.nameColor(ids, group, BadgeOptions.PLAIN.withGradient(gradient(group, player))));
    }

    @Override
    public List<String> gradientIds() {
        return List.copyOf(gradients.keySet());
    }

    /** The rank colour used by the rank provider; null while badges are off (the provider's own color applies). */
    TextColor colorOrNull(Player player) {
        return color(player).orElse(null);
    }
}
