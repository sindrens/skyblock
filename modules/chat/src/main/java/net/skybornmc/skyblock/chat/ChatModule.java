package net.skybornmc.skyblock.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.chat.ChatChannel;
import net.skybornmc.skyblock.common.chat.ChatChannels;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.pack.BadgeOptions;
import net.skybornmc.skyblock.common.pack.BadgeService;
import net.skybornmc.skyblock.common.pack.BadgeSettings;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.LegacyColors;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Chat (M1.3) + tab list and nametags (M1.5). Chat line {@code [Title] [RANK] Name » message} with a hover card,
 * {@code &} colors by permission tier, mentions, {@code [item]}, chat protection, slow mode / lock / clear and
 * staff chat. Everything is audited (category chat). M2.5: channel chat ({@link ChatChannel}, island chat) goes through
 * the same checks and is published as {@link ChatChannels} for one-shot messages.
 */
public final class ChatModule implements SkyblockModule, Listener {

    static final String ID = "chat";

    static final String PERM_COLOR = "skyblock.chat.color";
    static final String PERM_FORMAT = "skyblock.chat.format";
    static final String PERM_HEX = "skyblock.chat.hex";
    static final String PERM_LINKS = "skyblock.chat.links";
    static final String PERM_ITEM = "skyblock.chat.item";
    static final String PERM_BYPASS = "skyblock.chat.bypass";
    static final String PERM_MANAGE = "skyblock.staff.chat.manage";
    static final String PERM_STAFF_CHAT = "skyblock.staff.chat";
    static final String PERM_TAB_STAFF = "skyblock.staff.tab";
    static final String PERM_RANK_PREVIEW = "skyblock.rank.preview";

    // /settings toggles (PlayerSettings service from the menus module; ids match its "coming soon" placeholders)
    static final String SETTING_MENTIONS = "mentions-sound";
    static final String SETTING_COLORS = "chat-colors";
    static final String SETTING_TIMESTAMPS = "chat-timestamps";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private static final Pattern ITEM_TOKEN = Pattern.compile("(?i)\\[(?:item|i)]");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final Core core;
    private final CommandService commands;
    private final ModuleMessages msg;
    private final ChatConfig config = new ChatConfig();
    private TextSurfaces surfaces;
    private final RankBadges badges;
    private final Display display;
    private final TabService tab;

    private final Map<UUID, ArrayBlockingQueue<Long>> recent = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKey = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastKeyAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastItemShow = new ConcurrentHashMap<>();
    private final Set<UUID> staffChatToggled = ConcurrentHashMap.newKeySet();
    private volatile boolean locked;
    private volatile int slowSeconds;
    private volatile PlayerSettings settings;

    public ChatModule(Core core) {
        this.core = core;
        this.commands = core.commands();
        this.msg = core.messages(ID);
        this.badges = new RankBadges(core, () -> config);
        this.display = new Display(core, () -> config, badges);
        this.tab = new TabService(core, display, () -> config, msg, badges);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        loadConfig();
        badges.reload();
        // Rank badges (pack module present): one service, and one source for the rank color on every surface.
        core.services().register(ID, BadgeService.class, badges);
        core.ranks().colorSource(badges::colorOrNull);
        var pm = core.plugin().getServer().getPluginManager();
        pm.registerEvents(this, core.plugin());
        pm.registerEvents(tab, core.plugin());
        // Owner answer 304: signs, books and anvil names use the chat filter too.
        surfaces = new TextSurfaces(core, msg, () -> config);
        pm.registerEvents(surfaces, core.plugin());
        registerPlaceholders();
        registerCommands();
        // The same content filter for text written outside the chat (island notes, owner N8).
        core.services().register(ID, net.skybornmc.skyblock.common.text.TextFilter.class, (author, text) -> {
            ChatFilter.Result result = ChatFilter.check(text, config.rules(), author != null && author.hasPermission(PERM_LINKS));
            return new net.skybornmc.skyblock.common.text.TextFilter.Result(result.text(), result.blocked());
        });
        // M2.5: one-shot messages to a channel (island chat) with the same checks as chat.
        core.services().register(ID, ChatChannels.class, this::sendToChannel);
        tab.start();
        // The menus module (which publishes PlayerSettings) enables after us; register our toggles one tick later.
        core.scheduler().later("chat:settings", 1L, this::registerSettings);
    }

    private void registerSettings() {
        PlayerSettings service = core.services().optional(PlayerSettings.class).orElse(null);
        if (service == null) {
            return;
        }
        service.register(new SettingToggle(ID, SETTING_MENTIONS, SettingCategory.CHAT, Material.BELL, true, null,
                "navigation", "settings.toggles." + SETTING_MENTIONS, false));
        service.register(new SettingToggle(ID, SETTING_COLORS, SettingCategory.CHAT, Material.RED_DYE, true, null,
                "navigation", "settings.toggles." + SETTING_COLORS, false));
        service.register(new SettingToggle(ID, SETTING_TIMESTAMPS, SettingCategory.CHAT, Material.CLOCK, false, null,
                "navigation", "settings.toggles." + SETTING_TIMESTAMPS, false));
        // Rank badges (DISPLAY): reduced motion, simple badges, bigger text, and the Immortal colour picker.
        BadgeSettings.toggles(ID, badges.gradientIds()).forEach(service::register);
        settings = service;
    }

    /** A /settings value, or the default while the settings service is not available. */
    private boolean setting(UUID player, String id, boolean fallback) {
        PlayerSettings s = settings;
        return s == null ? fallback : s.get(player, id);
    }

    @Override
    public void reload() {
        loadConfig();
        badges.reload();
        if (settings != null) {
            registerSettings(); // a changed gradient list becomes the picker's choices
        }
        core.messages().load(ID);
        tab.reloaded();
    }

    /** Loads chat.yml and moves formats that still have an old default to the new one (owner edits are kept). */
    private void loadConfig() {
        core.configs().load(config);
        Map<String, String> upgrades = ChatConfig.upgrades(config.changedDefaultValues());
        if (upgrades.isEmpty()) {
            return;
        }
        try {
            upgrades.forEach((path, value) -> core.configs().set(config, path, value));
            core.configs().save(config);
            core.plugin().getLogger().info("chat.yml: new default formats (titles before, tags after the name): "
                    + String.join(", ", upgrades.keySet()));
        } catch (java.io.IOException | IllegalArgumentException e) {
            core.plugin().getLogger().warning("chat.yml: could not save the new default formats: " + e.getMessage());
        }
    }

    @Override
    public void disable() {
        core.ranks().colorSource(null);
        PlayerSettings s = settings;
        if (s != null) {
            s.unregisterAll(ID);
            settings = null;
        }
        tab.stop();
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(tab);
        if (surfaces != null) {
            HandlerList.unregisterAll(surfaces);
        }
    }

    // ================================================================== placeholders (for join messages, menus, scoreboard)

    private void registerPlaceholders() {
        core.placeholders().register(ID, "rank_name", new Placeholder() {
            @Override
            public Component component(Player p) {
                return p == null ? Component.empty() : display.name(p);
            }
        });
        core.placeholders().register(ID, "rank_display", new Placeholder() {
            @Override
            public Component component(Player p) {
                return p == null ? Component.empty() : display.rank(p, p);
            }
        });
    }

    // ================================================================== commands

    /** Group ids for {@code /rank preview}: the pack's badge styles plus every group LuckPerms has loaded. */
    private List<String> previewGroups() {
        java.util.Set<String> ids = new java.util.TreeSet<>();
        net.skybornmc.skyblock.common.pack.PackIds pack = badges.ids();
        if (pack != null) {
            ids.addAll(pack.badges().keySet());
        }
        ids.addAll(core.ranks().groupIds());
        return List.copyOf(ids);
    }

    /**
     * {@code /rank preview <group> [name]}: the badge as players see it, in every form, then the Bedrock line. The pack
     * forms only go to a sender who has the pack (Java without it, and Bedrock, would see boxes or controller icons).
     */
    private void rankPreview(CommandSender sender, String group, String name) {
        net.skybornmc.skyblock.common.pack.PackIds pack = badges.ids();
        if (pack == null) {
            msg.send(sender, "rank-preview.unavailable");
            return;
        }
        String shown = name == null || name.isBlank() ? core.ranks().groupDisplayName(group) : name;
        boolean hasPack = badges.options(sender).pack();
        msg.send(sender, "rank-preview.header", Msg.p("group", group), Msg.p("name", shown));
        if (hasPack) {
            var base = new BadgeOptions(true, false, false, false, false, java.util.List.of());
            msg.send(sender, "rank-preview.shine", Msg.c("badge", badges.badge(group, shown, base)));
            msg.send(sender, "rank-preview.still", Msg.c("badge", badges.badge(group, shown,
                    new BadgeOptions(true, false, true, false, false, java.util.List.of()))));
            msg.send(sender, "rank-preview.large", Msg.c("badge", badges.badge(group, shown,
                    new BadgeOptions(true, false, false, false, true, java.util.List.of()))));
        } else {
            msg.send(sender, "rank-preview.no-pack");
        }
        msg.send(sender, "rank-preview.simple", Msg.c("badge", badges.badge(group, shown,
                new BadgeOptions(true, false, false, true, false, java.util.List.of()))));
        msg.send(sender, "rank-preview.java", Msg.c("badge", badges.badge(group, shown, BadgeOptions.PLAIN)));
        msg.send(sender, "rank-preview.bedrock", Msg.c("badge", badges.badge(group, shown,
                new BadgeOptions(false, true, false, false, false, java.util.List.of()))));
    }

    private void registerCommands() {
        Arg<String> previewGroup = Args.oneOf("group", this::previewGroups);
        Arg<String> previewName = Args.text("name", "Hatchling", "Baby Phoenix", "Immortal");
        commands.register(CommandSpec.command("rank")
                .description("Rank tools for staff")
                .category("Staff")
                .permission(PERM_RANK_PREVIEW)
                .sub(CommandSpec.sub("preview")
                        .description("Preview a rank badge exactly as players see it (pack, still, simple, Bedrock)")
                        .arg(previewGroup)
                        .optional(previewName)
                        .example("rank preview immortal Phoenix King")
                        .executes(ctx -> rankPreview(ctx.sender(), ctx.get(previewGroup),
                                ctx.has(previewName) ? ctx.get(previewName) : null))));
        Arg<Integer> seconds = Args.integer("seconds", 0, 300, "0", "3", "5", "10", "30");
        commands.register(CommandSpec.command("chat")
                .description("Chat moderation: clear, lock, slow mode")
                .category("Staff")
                .permission(PERM_MANAGE)
                .sub(CommandSpec.sub("clear").description("Clear the chat for everyone").executes(ctx -> clear(ctx.sender())))
                .sub(CommandSpec.sub("lock").description("Lock or unlock the chat").executes(ctx -> toggleLock(ctx.sender())))
                .sub(CommandSpec.sub("slow").description("Slow mode: seconds between messages (0 = off)").arg(seconds)
                        .example("chat slow 5").executes(ctx -> slow(ctx.sender(), ctx.get(seconds)))));
        commands.register(CommandSpec.command("clearchat")
                .description("Clear the chat for everyone")
                .category("Staff")
                .permission(PERM_MANAGE)
                .aliases("cc")
                .executes(ctx -> clear(ctx.sender())));
        commands.register(CommandSpec.command("mutechat")
                .description("Lock or unlock the chat")
                .category("Staff")
                .permission(PERM_MANAGE)
                .aliases("lockchat")
                .executes(ctx -> toggleLock(ctx.sender())));

        Arg<String> text = Args.text("message", "Anyone free to help?", "Heads up:");
        commands.register(CommandSpec.command("sc")
                .description("Staff chat (without text: toggle staff chat mode)")
                .category("Staff")
                .permission(PERM_STAFF_CHAT)
                .aliases("staffchat")
                .optional(text)
                .example("sc Anyone free to help?")
                .executes(ctx -> {
                    if (ctx.has(text)) {
                        staffChat(ctx.sender(), ctx.get(text));
                    } else {
                        Player p = ctx.player();
                        boolean on = !staffChatToggled.remove(p.getUniqueId());
                        if (on) {
                            staffChatToggled.add(p.getUniqueId());
                        }
                        msg.send(p, on ? "staffchat.toggle-on" : "staffchat.toggle-off");
                    }
                }));
    }

    private void clear(CommandSender sender) {
        Component blank = Component.text(" ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission(PERM_MANAGE)) {
                for (int i = 0; i < 120; i++) {
                    p.sendMessage(blank);
                }
            }
            msg.send(p, "moderation.cleared", Msg.p("staff", sender.getName()));
        }
        audit(sender, "chat_clear", Map.of());
    }

    private void toggleLock(CommandSender sender) {
        locked = !locked;
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, locked ? "moderation.locked" : "moderation.unlocked", Msg.p("staff", sender.getName()));
        }
        if (!(sender instanceof Player)) {
            msg.send(sender, locked ? "moderation.locked" : "moderation.unlocked", Msg.p("staff", sender.getName()));
        }
        audit(sender, locked ? "chat_lock" : "chat_unlock", Map.of());
    }

    private void slow(CommandSender sender, int seconds) {
        slowSeconds = seconds;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (seconds == 0) {
                msg.send(p, "moderation.slow-off", Msg.p("staff", sender.getName()));
            } else {
                msg.send(p, "moderation.slow-on", Msg.p("staff", sender.getName()), Msg.number("seconds", seconds));
            }
        }
        audit(sender, "chat_slow", Map.of("seconds", seconds));
    }

    private void audit(CommandSender sender, String action, Map<String, Object> details) {
        core.audit().log(AuditCategory.STAFF, action, Actor.of(sender), null, null, null,
                sender instanceof Player p ? p.getLocation() : null, details);
    }

    private void staffChat(CommandSender sender, String text) {
        Component name = sender instanceof Player p ? display.name(p) : Component.text(sender.getName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(PERM_STAFF_CHAT)) {
                msg.send(p, "staffchat.line", Msg.c("name", name), Msg.p("message", text));
            }
        }
        msg.send(Bukkit.getConsoleSender(), "staffchat.line", Msg.c("name", name), Msg.p("message", text));
        core.audit().log(AuditCategory.CHAT, "staff_chat", Actor.of(sender), null, null, null,
                sender instanceof Player p ? p.getLocation() : null, Map.of("message", text));
    }

    // ================================================================== chat pipeline

    /** Step 1 (LOW): staff chat mode, channel chat (island chat), lock, slow mode, rate limit, duplicates, filters, colors, [item]. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChatCheck(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (staffChatToggled.contains(id) && player.hasPermission(PERM_STAFF_CHAT)) {
            event.setCancelled(true);
            core.scheduler().ensureMain("chat:staff", () -> staffChat(player, raw));
            return;
        }
        // M2.5: a channel (island chat toggle mode) takes the message; mute, freeze and prompts (LOWEST) already ran.
        ChatChannel channel = capturingChannel(player);
        Checked checked = check(player, raw, channel != null);
        if (checked.blocked() != null) {
            event.setCancelled(true);
            refuse(player, raw, checked.blocked());
            return;
        }
        if (channel != null) {
            event.setCancelled(true);
            core.scheduler().ensureMain("chat:channel", () -> deliver(player, channel, checked, raw));
            return;
        }
        event.message(checked.message());
        core.audit().log(AuditCategory.CHAT, "chat", Actor.player(player), null, null, null, player.getLocation(),
                details(raw, checked.changes()));
    }

    /** Outcome of the chat checks: a block reason, or the message to show. */
    private record Checked(String blocked, Component message, String text, List<String> changes) {
        static Checked refused(String reason) {
            return new Checked(reason, null, null, List.of());
        }
    }

    /** Gate (lock, slow, new players, rate, duplicates), filter, colors and [item] for public or channel chat. */
    private Checked check(Player player, String raw, boolean channel) {
        UUID id = player.getUniqueId();
        boolean bypass = player.hasPermission(PERM_BYPASS);
        long now = System.currentTimeMillis();
        if (!bypass) {
            String reason = gate(player, raw, now, channel);
            if (reason != null) {
                return Checked.refused(reason);
            }
        }
        ChatFilter.Result result = bypass ? new ChatFilter.Result(raw, null, List.of())
                : ChatFilter.check(raw, config.rules(), player.hasPermission(PERM_LINKS));
        if (result.isBlocked()) {
            return Checked.refused(result.blocked());
        }
        if (!channel) {
            lastMessageAt.put(id, now); // slow mode is about public chat
        }
        lastKey.put(id, ChatFilter.duplicateKey(result.text()));
        lastKeyAt.put(id, now);
        Component message = showItem(player, colorize(player, result.text()));
        return new Checked(null, message, result.text(), result.changes());
    }

    private static Map<String, Object> details(String raw, List<String> changes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", raw);
        if (!changes.isEmpty()) {
            details.put("changed", changes);
        }
        return details;
    }

    /** The channel that takes this player's chat right now (island chat toggle), or null. */
    private ChatChannel capturingChannel(Player player) {
        ChatChannel channel = core.services().optional(ChatChannel.class).orElse(null);
        try {
            return channel != null && channel.captures(player) ? channel : null;
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("Chat channel " + channel.id() + " failed: " + e.getMessage());
            return null;
        }
    }

    /** Hands a checked message to its channel (main thread) and audits it like public chat, with the channel id. */
    private boolean deliver(Player player, ChatChannel channel, Checked checked, String raw) {
        if (!player.isOnline()) {
            return false;
        }
        Map<String, Object> extra = channel.deliver(player, checked.message(), checked.text());
        if (extra == null) {
            return false;
        }
        Map<String, Object> details = details(raw, checked.changes());
        details.put("channel", channel.id());
        details.putAll(extra);
        core.audit().log(AuditCategory.CHAT, "chat", Actor.player(player), null, null, null, player.getLocation(), details);
        return true;
    }

    /** {@link ChatChannels}: a one-shot channel message (e.g. {@code /is chat <text>}), main thread. */
    private boolean sendToChannel(Player player, ChatChannel channel, String text) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            return false;
        }
        // Mute lives in the moderation module (placeholder "muted"); chat events are blocked there, commands here.
        if (core.placeholders().has("muted") && core.placeholders().text(player, "muted").map("true"::equalsIgnoreCase).orElse(false)) {
            refuse(player, raw, "muted");
            return false;
        }
        Checked checked = check(player, raw, true);
        if (checked.blocked() != null) {
            refuse(player, raw, checked.blocked());
            return false;
        }
        return deliver(player, channel, checked, raw);
    }

    /** Lock, slow mode, new-player delay, rate limit and duplicates; returns a reason key or null. */
    private String gate(Player player, String raw, long now, boolean channel) {
        UUID id = player.getUniqueId();
        if (locked && gateApplies("locked", channel, config.channelsRespectLock, config.channelsRespectSlow)) {
            return "locked";
        }
        if (config.newPlayerSeconds > 0) {
            PlayerData data = core.players().get(id).orElse(null);
            if (data != null && now - data.firstSeen() < config.newPlayerSeconds * 1000L
                    && data.playtimeMillis() < config.newPlayerSeconds * 1000L) {
                return "new-player";
            }
        }
        if (slowSeconds > 0 && gateApplies("slow", channel, config.channelsRespectLock, config.channelsRespectSlow)) {
            Long last = lastMessageAt.get(id);
            if (last != null && now - last < slowSeconds * 1000L) {
                return "slow";
            }
        }
        ArrayBlockingQueue<Long> queue = recent.computeIfAbsent(id, k -> new ArrayBlockingQueue<>(50));
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peek() >= config.rateSeconds * 1000L) {
                queue.poll();
            }
            if (queue.size() >= config.rateMessages) {
                return "rate";
            }
            if (!queue.offer(now)) {
                queue.poll();
                queue.offer(now);
            }
        }
        if (config.duplicateSeconds > 0) {
            String key = ChatFilter.duplicateKey(raw);
            Long at = lastKeyAt.get(id);
            if (!key.isEmpty() && key.equals(lastKey.get(id)) && at != null && now - at < config.duplicateSeconds * 1000L) {
                return "duplicate";
            }
        }
        return null;
    }

    /**
     * Whether a gate applies to a message (pure): public chat gets every gate; channel chat (island chat) skips the
     * staff chat lock and slow mode unless {@code channels.respect-lock} / {@code channels.respect-slow-mode} say so (both
     * are about public chat). Rate limit, duplicates, new-player delay and the filter always apply.
     */
    static boolean gateApplies(String reason, boolean channel, boolean respectLock, boolean respectSlow) {
        if (!channel) {
            return true;
        }
        return switch (reason) {
            case "locked" -> respectLock;
            case "slow" -> respectSlow;
            default -> true;
        };
    }

    /** Tells the player why the message was refused and audits it ({@code chat_blocked}); word hits alert staff. */
    private void refuse(Player player, String raw, String reason) {
        long wait = 0;
        if (reason.equals("slow")) {
            wait = slowSeconds * 1000L - (System.currentTimeMillis() - lastMessageAt.getOrDefault(player.getUniqueId(), 0L));
        } else if (reason.equals("new-player")) {
            long first = core.players().get(player.getUniqueId()).map(PlayerData::firstSeen).orElse(0L);
            wait = config.newPlayerSeconds * 1000L - (System.currentTimeMillis() - first);
        }
        msg.send(player, "blocked." + reason, Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, wait)))));
        core.audit().log(AuditCategory.CHAT, "chat_blocked", Actor.player(player), null, null, null, player.getLocation(),
                Map.of("message", raw, "reason", reason));
        if (reason.equals("word")) {
            core.scheduler().ensureMain("chat:alert", () -> alertStaff(player, raw));
        }
    }

    private void alertStaff(Player player, String raw) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("skyblock.staff.notify")) {
                msg.send(staff, "staff-alert", Msg.p("player", player.getName()), Msg.p("message", raw));
            }
        }
    }

    /** {@code &} colors by tier: classic ({@code skyblock.chat.color}), formats ({@code .format}), hex ({@code .hex}). */
    private Component colorize(Player player, String text) {
        boolean classic = player.hasPermission(PERM_COLOR);
        boolean format = player.hasPermission(PERM_FORMAT);
        boolean hex = player.hasPermission(PERM_HEX);
        if ((classic || format || hex) && LegacyColors.hasCodes(text)) {
            return LegacyColors.toComponent(ChatColors.filter(text, classic, format, hex));
        }
        return Component.text(text);
    }

    /** Replaces {@code [item]} / {@code [i]} with the held item (hover shows its tooltip). */
    private Component showItem(Player player, Component message) {
        if (!config.itemShow || !player.hasPermission(PERM_ITEM)
                || !ITEM_TOKEN.matcher(PlainTextComponentSerializer.plainText().serialize(message)).find()) {
            return message;
        }
        long now = System.currentTimeMillis();
        Long last = lastItemShow.get(player.getUniqueId());
        if (last != null && now - last < config.itemShowCooldown * 1000L && !player.hasPermission(PERM_BYPASS)) {
            return message;
        }
        ItemStack held;
        try {
            held = core.scheduler().supplyMain("chat:item", () -> player.getInventory().getItemInMainHand().clone())
                    .get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            return message;
        }
        if (held == null || held.getType() == Material.AIR) {
            return message;
        }
        lastItemShow.put(player.getUniqueId(), now);
        Component item = msg.component(player, "item-show", Msg.c("item", held.displayName()),
                Msg.number("amount", held.getAmount()));
        return message.replaceText(TextReplacementConfig.builder().match(ITEM_TOKEN).once().replacement(item).build());
    }

    /** Step 2 (HIGH): per-viewer rendering (Bedrock colors, mention highlight for that viewer). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatRender(AsyncChatEvent event) {
        Player source = event.getPlayer();
        // QUICK1 (PERFORMANCE P-05): the name with its hover card is built once per viewer kind (Java / Bedrock) per
        // message, not once per viewer. The renderer runs once per viewer, possibly from several threads.
        record Shared(Component name, Component rank) {
        }
        Map<String, Shared> shared = new java.util.concurrent.ConcurrentHashMap<>(4);
        event.renderer((player, sourceDisplayName, message, viewer) -> {
            // One variant per kind of viewer (Java/Bedrock, pack, badge settings), see BadgeService#options.
            String variant = badges.enabled() ? badges.options(viewer).key()
                    : String.valueOf(viewer instanceof Player v && core.bedrock().isBedrock(v.getUniqueId()));
            Shared parts = shared.computeIfAbsent(variant, b -> new Shared(display.name(player)
                    .hoverEvent(HoverEvent.showText(hover(viewer, player)))
                    .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " ")), display.rank(viewer, player)));
            Component name = parts.name();
            Component body = message;
            Player target = viewer instanceof Player t ? t : null;
            if (target != null && !setting(target.getUniqueId(), SETTING_COLORS, true)) {
                body = stripColors(body);
            }
            if (config.mentions && target != null && !target.equals(player)) {
                body = highlight(target, body);
            }
            Component line = core.messages().parse(viewer, config.chatFormat, display.chatParts(viewer, player, name, parts.rank(), body));
            if (target != null && setting(target.getUniqueId(), SETTING_TIMESTAMPS, false)) {
                line = msg.component(viewer, "timestamp", Msg.p("time", CLOCK.withZone(Format.zone()).format(Instant.now()))).append(line);
            }
            return line;
        });
        if (config.mentions) {
            String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
            List<Player> mentioned = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(source) && mentions(plain, p.getName()) && wantsMentions(p) && event.viewers().contains(p)) {
                    mentioned.add(p);
                }
            }
            if (!mentioned.isEmpty()) {
                core.scheduler().ensureMain("chat:mention", () -> mentioned.forEach(p -> core.sounds().play(p, "notify")));
            }
        }
    }

    private boolean wantsMentions(Player p) {
        return setting(p.getUniqueId(), SETTING_MENTIONS, true);
    }

    /** Removes colors and decorations (keeps hover/click, e.g. [item] tooltips) for players who turned chat colors off. */
    static Component stripColors(Component component) {
        net.kyori.adventure.text.format.Style style = net.kyori.adventure.text.format.Style.style()
                .hoverEvent(component.hoverEvent()).clickEvent(component.clickEvent()).build();
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) {
            children.add(stripColors(child));
        }
        return component.style(style).children(children);
    }

    static boolean mentions(String text, String name) {
        return mentionPattern(name).matcher(text).find();
    }

    /** QUICK1 (P-05): mention patterns are compiled once per name (dropped on quit), not per message and viewer. */
    private static final Map<String, Pattern> MENTION_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bounded: names of players who never quit cleanly can't grow the map forever. */
    static final int MENTION_PATTERN_LIMIT = 4096;

    static Pattern mentionPattern(String name) {
        Pattern cached = MENTION_PATTERNS.get(name);
        if (cached != null) {
            return cached;
        }
        if (MENTION_PATTERNS.size() >= MENTION_PATTERN_LIMIT) {
            MENTION_PATTERNS.clear();
        }
        return MENTION_PATTERNS.computeIfAbsent(name,
                n -> Pattern.compile("(?i)(?<![\\w])@?" + Pattern.quote(n) + "(?![\\w])"));
    }

    static void forgetMentionPattern(String name) {
        MENTION_PATTERNS.remove(name);
    }

    private Component highlight(Player target, Component body) {
        Pattern pattern = mentionPattern(target.getName());
        Component styled = msg.component(target, "mention", Msg.p("name", target.getName()));
        return body.replaceText(TextReplacementConfig.builder().match(pattern).replacement(styled).build());
    }

    /** Hover card on a name: rank, island (when the islands module provides it), playtime, first join, Discord. */
    private Component hover(Audience viewer, Player player) {
        PlayerData data = core.players().get(player.getUniqueId()).orElse(null);
        String island = core.placeholders().has("island_name")
                ? core.placeholders().text(player, "island_name").orElse("")
                + core.placeholders().text(player, "island_level").map(l -> l.isEmpty() ? "" : " (Lv " + l + ")").orElse("")
                : "";
        return msg.component(viewer, "hover",
                Msg.c("name", display.name(player)),
                Msg.c("rank", display.rank(viewer, player).decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET)),
                Msg.p("rank_name", core.ranks().groupDisplayName(player)),
                Msg.p("real_name", player.getName()),
                Msg.mm("nick_line", display.hasNick(player) ? msg.has("hover-nick")
                        ? core.messages().raw(ID, "hover-nick").replace("<real_name>", player.getName()) : "" : ""),
                Msg.p("island", island.isBlank() ? "-" : island),
                Msg.p("playtime", data == null ? "-" : Format.duration(Duration.ofMillis(data.playtimeMillis()))),
                Msg.p("first", data == null ? "-" : DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(data.firstSeen()))),
                Msg.mm("discord", data != null && data.discordId() != null ? "<c:success><sym:check></c:success>"
                        : "<c:error><sym:cross></c:error>"),
                Msg.number("ping", player.getPing()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        forgetMentionPattern(event.getPlayer().getName());
        recent.remove(id);
        lastKey.remove(id);
        lastKeyAt.remove(id);
        lastMessageAt.remove(id);
        lastItemShow.remove(id);
        staffChatToggled.remove(id);
    }
}
