package net.skybornmc.skyblock.common.hook;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** LuckPerms-backed {@link RankProvider}. Only loaded when LuckPerms is enabled; reads LuckPerms' own caches (cheap). */
final class LuckPermsRanks implements RankProvider {

    private final Plugin plugin;
    private final LuckPerms lp;
    private final List<Consumer<UUID>> listeners = new CopyOnWriteArrayList<>();
    private EventSubscription<UserDataRecalculateEvent> subscription;

    private LuckPermsRanks(Plugin plugin, LuckPerms lp) {
        this.plugin = plugin;
        this.lp = lp;
    }

    static LuckPermsRanks create(Plugin plugin) {
        LuckPermsRanks ranks = new LuckPermsRanks(plugin, LuckPermsProvider.get());
        ranks.subscription = ranks.lp.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, ranks::onRecalculate);
        return ranks;
    }

    private void onRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> listeners.forEach(listener -> {
            try {
                listener.accept(uuid);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Rank change listener failed: " + e);
            }
        }));
    }

    private CachedMetaData metaData(Player player) {
        return lp.getPlayerAdapter(Player.class).getMetaData(player);
    }

    @Override
    public String primaryGroup(Player player) {
        String group = metaData(player).getPrimaryGroup();
        return group == null ? "default" : group;
    }

    @Override
    public String groupDisplayName(Player player) {
        String id = primaryGroup(player);
        Group group = lp.getGroupManager().getGroup(id);
        String display = group == null ? null : group.getDisplayName();
        return display == null || display.isBlank() ? capitalize(id) : display;
    }

    @Override
    public String prefix(Player player) {
        String prefix = metaData(player).getPrefix();
        return prefix == null ? "" : prefix;
    }

    @Override
    public String suffix(Player player) {
        String suffix = metaData(player).getSuffix();
        return suffix == null ? "" : suffix;
    }

    @Override
    public int weight(Player player) {
        Group group = lp.getGroupManager().getGroup(primaryGroup(player));
        return group == null ? 0 : group.getWeight().orElse(0);
    }

    @Override
    public TextColor rankColor(Player player) {
        TextColor color = RankProvider.parseColor(metaData(player).getMetaValue("color"));
        if (color == null) {
            color = RankProvider.firstLegacyColor(prefix(player));
        }
        return color == null ? NamedTextColor.WHITE : color;
    }

    @Override
    public String meta(Player player, String key) {
        return metaData(player).getMetaValue(key);
    }

    @Override
    public void onRankChange(Consumer<UUID> listener) {
        listeners.add(listener);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
        }
        listeners.clear();
    }

    private static String capitalize(String id) {
        return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
