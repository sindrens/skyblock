package net.skybornmc.skyblock.common.hook;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * LuckPerms-backed {@link RankProvider}. Only loaded when LuckPerms is enabled; reads LuckPerms' own data (cheap).
 *
 * <p><b>Rank changes show within a second</b> ({@code lp user X parent set/add}):</p>
 * <ul>
 *   <li>The primary group is read from the player's own parent nodes on every call (heaviest group wins, like
 *       LuckPerms' default {@code parents-by-weight}), instead of the primary group stored in the cached meta snapshot.
 *       That snapshot is built lazily and can be built from a half-finished command ({@code parent set} first removes
 *       every parent, then adds the new one, firing an event for each step), which kept the old/"default" rank on the
 *       scoreboard until something else invalidated it.</li>
 *   <li>Listeners are told twice per burst of recalculation events, coalesced per player
 *       ({@link RankChangeDebouncer}): on the next tick and again {@value RankChangeDebouncer#SETTLE_TICKS} ticks later,
 *       so the final state is always picked up.</li>
 * </ul>
 */
final class LuckPermsRanks implements RankProvider {

    private final Plugin plugin;
    private final LuckPerms lp;
    private final PlayerAdapter<Player> adapter;
    private final List<Consumer<UUID>> listeners = new CopyOnWriteArrayList<>();
    private final RankChangeDebouncer debouncer;
    private EventSubscription<UserDataRecalculateEvent> subscription;

    private LuckPermsRanks(Plugin plugin, LuckPerms lp) {
        this.plugin = plugin;
        this.lp = lp;
        this.adapter = lp.getPlayerAdapter(Player.class);
        this.debouncer = new RankChangeDebouncer((delay, task) -> {
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(1L, delay));
            }
        }, this::notifyListeners);
    }

    static LuckPermsRanks create(Plugin plugin) {
        LuckPermsRanks ranks = new LuckPermsRanks(plugin, LuckPermsProvider.get());
        ranks.subscription = ranks.lp.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, ranks::onRecalculate);
        return ranks;
    }

    private void onRecalculate(UserDataRecalculateEvent event) {
        if (plugin.isEnabled()) {
            debouncer.changed(event.getUser().getUniqueId());
        }
    }

    private void notifyListeners(UUID uuid) {
        if (plugin.getServer().getPlayer(uuid) == null) {
            return;
        }
        for (Consumer<UUID> listener : listeners) {
            try {
                listener.accept(uuid);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Rank change listener failed: " + e);
            }
        }
    }

    private CachedMetaData metaData(Player player) {
        return adapter.getMetaData(player);
    }

    @Override
    public String primaryGroup(Player player) {
        String cached = metaData(player).getPrimaryGroup();
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return cached == null ? "default" : cached;
        }
        QueryOptions query = adapter.getQueryOptions(player);
        List<GroupWeight> parents = new ArrayList<>(2);
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (!node.getValue() || node.hasExpired() || !query.satisfies(node.getContexts())) {
                continue;
            }
            Group group = lp.getGroupManager().getGroup(node.getGroupName());
            if (group != null) {
                parents.add(new GroupWeight(group.getName(), group.getWeight().orElse(0)));
            }
        }
        return RankProvider.pickPrimary(parents, cached);
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
