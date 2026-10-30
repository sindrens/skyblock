package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.chat.ChatChannel;
import net.skybornmc.skyblock.common.chat.ChatChannels;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.TextFilter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Island chat (M2.5): {@code /is chat} toggles the mode (normal chat goes to the island), {@code /is chat <text>} sends one
 * message. Members only (leader, admins, mods, members; coop players and visitors don't hear it). Lines are colored by
 * role; the leader picks the role colors from {@code chat.palette} ({@link ChatColorsMenu}, owner I11).
 *
 * <p>Routing: published as a {@link ChatChannel} service; the chat module hands every toggled message (after mute, freeze
 * and menu prompts, with its rate limit, duplicates, word/link filter and {@code &} colors) to {@link #deliver} and audits
 * it as chat with {@code channel: island}. One-shot messages go through the chat module's {@link ChatChannels}. Without
 * the chat module, one-shots still work (mute check, the {@link TextFilter} if any, plain text, audited here); toggle mode
 * then has nothing to capture. Staff with {@value #PERM_SPY} and the "Island chat spy" setting see every island's chat.</p>
 */
final class IslandChat implements ChatChannel {

    static final String CHANNEL = "island";
    static final String PERM_SPY = "skyblock.island.chat.spy";
    static final String SETTING_SPY = "island-chat-spy";

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandChatStore store;
    private final Core core;
    private final ModuleMessages msg;
    /** Players (account UUIDs) whose normal chat goes to their island; read on the async chat thread. */
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();
    /** Role colors each island's leader changed (main thread). */
    private final Map<UUID, Map<Role, String>> colors = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Executor main;
    private List<String> palette = List.of();
    private PlayerSettings settings;

    IslandChat(IslandsModule module, IslandManager islands, IslandChatStore store) {
        this.module = module;
        this.islands = islands;
        this.store = store;
        this.core = module.core();
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
        islands.onRemoved(island -> colors.remove(island.id()));
    }

    /** Parses {@code chat.palette} (enable, reload); problems go to the console. */
    void loadConfig() {
        IslandChatRules.Palette parsed = IslandChatRules.palette(module.config().chatPalette);
        parsed.problems().forEach(problem -> core.plugin().getLogger().warning("islands.yml: " + problem));
        palette = parsed.colors();
        for (Role role : IslandChatRules.COLOR_ROLES) {
            if (IslandChatRules.parse(module.config().chatColor(role)) == null) {
                core.plugin().getLogger().warning("islands.yml: chat.role-colors." + MemberStore.key(role) + " is not a color; white is used");
            }
        }
    }

    /** Loads the saved role colors (enable). */
    void load() {
        try {
            colors.clear();
            colors.putAll(core.database().blocking(IslandChatStore::loadAll));
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("Islands: island chat colors could not be loaded (defaults are used): " + e.getMessage());
        }
    }

    /** Registers the staff spy toggle in /settings (one tick after enable: the menus module may enable later). */
    void hookSettings() {
        core.services().optional(PlayerSettings.class).ifPresent(service -> {
            service.register(new SettingToggle(IslandsModule.ID, SETTING_SPY, SettingCategory.CHAT, Material.SPYGLASS, false,
                    PERM_SPY, IslandsModule.ID, "settings." + SETTING_SPY, false));
            settings = service;
        });
    }

    List<String> palette() {
        return palette;
    }

    boolean isToggled(Player player) {
        return toggled.contains(player.getUniqueId());
    }

    /** Forget a player's toggle (quit, profile switch: the island changes with the profile). */
    boolean forget(UUID player) {
        busy.remove(player);
        return toggled.remove(player);
    }

    // ------------------------------------------------------------------ /is chat [message]

    void command(Player player, String text) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            toggled.remove(player.getUniqueId());
            islands.fail(player, "no-island");
            return;
        }
        if (text == null || text.isBlank()) {
            toggle(player);
            return;
        }
        send(player, text);
    }

    /** Switches toggle mode (from the command or the island menu). */
    void toggle(Player player) {
        if (toggled.remove(player.getUniqueId())) {
            msg.send(player, "chat.toggle-off");
        } else {
            if (islands.islandOfPlayer(islands.key(player)).isEmpty()) {
                islands.fail(player, "no-island");
                return;
            }
            toggled.add(player.getUniqueId());
            msg.send(player, "chat.toggle-on");
        }
        core.sounds().play(player, "click");
    }

    /** One message to the island: through the chat module's checks when it runs, else a minimal fallback. */
    void send(Player player, String text) {
        String limited = IslandChatRules.limit(text, module.config().chatMaxLength);
        if (limited.isEmpty()) {
            return;
        }
        Optional<ChatChannels> chat = core.services().optional(ChatChannels.class);
        if (chat.isPresent()) {
            chat.get().send(player, this, limited);
            return;
        }
        if (core.placeholders().has("muted") && core.placeholders().text(player, "muted").map("true"::equalsIgnoreCase).orElse(false)) {
            islands.fail(player, "chat.muted");
            return;
        }
        String checked = limited;
        Optional<TextFilter> filter = core.services().optional(TextFilter.class);
        if (filter.isPresent()) {
            TextFilter.Result result = filter.get().check(player, limited);
            if (result.isBlocked()) {
                islands.fail(player, "chat.filtered");
                return;
            }
            checked = result.text();
        }
        Map<String, Object> extra = deliver(player, Component.text(checked), checked);
        if (extra != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("message", limited);
            details.put("channel", CHANNEL);
            details.putAll(extra);
            core.audit().log(AuditCategory.CHAT, "chat", Actor.player(player), null, null, null, player.getLocation(), details);
        }
    }

    // ------------------------------------------------------------------ ChatChannel

    @Override
    public String id() {
        return CHANNEL;
    }

    @Override
    public boolean captures(Player player) {
        return toggled.contains(player.getUniqueId());
    }

    @Override
    public Map<String, Object> deliver(Player sender, Component message, String plain) {
        Island island = islands.islandOfPlayer(islands.key(sender)).orElse(null);
        Role role = island == null ? null : island.roleOrNull(islands.key(sender));
        if (island == null || !IslandChatRules.chats(role)) {
            if (toggled.remove(sender.getUniqueId())) {
                islands.fail(sender, "chat.off-no-island");
            } else {
                islands.fail(sender, "no-island");
            }
            return null;
        }
        String islandName = islands.displayName(island);
        TextColor color = color(island, role);
        TagResolver[] parts = {
                Msg.c("role", Component.text(module.raw("role." + MemberStore.key(role)), color)),
                Msg.c("name", Component.text(sender.getName(), color)),
                Msg.c("message", message),
                Msg.p("island", islandName)};
        Set<Player> recipients = new LinkedHashSet<>();
        recipients.add(sender);
        for (UUID member : List.copyOf(island.members().keySet())) {
            Player online = islands.online(member);
            if (online != null) {
                recipients.add(online);
            }
        }
        for (Player recipient : recipients) {
            msg.send(recipient, "chat.line", parts);
        }
        PlayerSettings s = settings;
        List<Player> spies = IslandChatRules.spies(List.copyOf(Bukkit.getOnlinePlayers()), recipients,
                p -> p.hasPermission(PERM_SPY) && s != null && s.get(p.getUniqueId(), SETTING_SPY));
        for (Player spy : spies) {
            msg.send(spy, "chat.spy-line", parts);
        }
        if (module.config().chatConsole) {
            msg.send(Bukkit.getConsoleSender(), "chat.spy-line", parts);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("island", island.id().toString());
        details.put("island_name", islandName);
        details.put("recipients", recipients.size());
        return details;
    }

    // ------------------------------------------------------------------ role colors (owner I11)

    /** The color a role shows on {@code island}. */
    TextColor color(Island island, Role role) {
        TextColor color = IslandChatRules.parse(colorName(island, role));
        return color == null ? TextColor.color(0xFFFFFF) : color;
    }

    /** The palette name of a role's color on {@code island}. */
    String colorName(Island island, Role role) {
        Map<Role, String> own = colors.get(island.id());
        return IslandChatRules.effective(own == null ? null : own.get(role), palette, module.config().chatColor(role));
    }

    /** Leader only: moves {@code role} to the next palette color (saved atomically, logged, members told). */
    void cycleColor(Player player, Island island, Role role, Runnable done) {
        UUID key = islands.key(player);
        if (!IslandChatRules.canPickColors(island.roleOrNull(key))) {
            islands.fail(player, "chat.colors.leader-only");
            done.run();
            return;
        }
        if (palette.isEmpty()) {
            islands.fail(player, "chat.colors.no-palette");
            done.run();
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            done.run();
            return;
        }
        String from = colorName(island, role);
        String next = IslandChatRules.next(palette, from);
        String stored = next.equals(IslandChatRules.normalize(module.config().chatColor(role))) ? null : next;
        store.save(island.id(), key, role, stored).whenCompleteAsync((result, error) -> {
            busy.remove(player.getUniqueId());
            if (error != null) {
                islands.databaseError(player, "chat color", error);
                done.run();
                return;
            }
            if (result != IslandChatStore.SaveResult.SAVED || islands.byId(island.id()) != island) {
                islands.fail(player, "chat.colors.leader-only");
                done.run();
                return;
            }
            Map<Role, String> own = colors.computeIfAbsent(island.id(), k -> new EnumMap<>(Role.class));
            if (stored == null) {
                own.remove(role);
            } else {
                own.put(role, stored);
            }
            core.audit().log(AuditCategory.ISLAND, "island_chat_color", Actor.player(player), island.id().toString(),
                    islands.displayName(island), null, null, Map.of("role", MemberStore.key(role), "from", from, "to", next));
            String roleName = module.raw("role." + MemberStore.key(role));
            msg.send(player, "chat.colors.changed", Msg.c("role", Component.text(roleName, color(island, role))));
            core.sounds().play(player, "success");
            done.run();
        }, main);
    }
}
