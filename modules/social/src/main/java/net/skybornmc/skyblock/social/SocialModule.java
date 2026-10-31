package net.skybornmc.skyblock.social;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.social.IgnoreService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Social features: private messages ({@code /msg}, {@code /r}), {@code /ignore}, {@code /socialspy}, {@code /afk},
 * {@code /list}, {@code /ping}, {@code /playtime}, {@code /seen}, {@code /whois}, {@code /broadcast} and link commands.
 * Texts: {@code messages/social.yml}; settings: {@code social.yml}.
 */
public final class SocialModule implements SkyblockModule, Listener, IgnoreService {

    static final String ID = "social";

    // Player permissions (default true)
    static final String PERM_MSG = "skyblock.social.msg";
    static final String PERM_IGNORE = "skyblock.social.ignore";
    static final String PERM_AFK = "skyblock.social.afk";
    static final String PERM_LIST = "skyblock.social.list";
    static final String PERM_PING = "skyblock.social.ping";
    static final String PERM_PLAYTIME = "skyblock.social.playtime";
    static final String PERM_SEEN = "skyblock.social.seen";
    static final String PERM_LINKS = "skyblock.social.links";
    // Staff permissions (default op)
    static final String PERM_SPY = "skyblock.staff.socialspy";
    static final String PERM_WHOIS = "skyblock.staff.whois";
    static final String PERM_WHOIS_IP = "skyblock.staff.whois.ip";
    static final String PERM_SEEN_DETAILS = "skyblock.staff.seen.details";
    static final String PERM_BROADCAST = "skyblock.staff.broadcast";
    static final String PERM_UNIGNORABLE = "skyblock.staff.unignorable";
    static final String PERM_VANISH_SEE = "skyblock.staff.vanish.see";

    private static final UUID CONSOLE_ID = new UUID(0, 0);
    private static final String SETTING_SPY = "socialspy";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final Core core;
    private final CommandService commands;
    private final ModuleMessages msg;
    private final SocialConfig config = new SocialConfig();
    private final AfkTracker afk = new AfkTracker();
    /** QOL3: "sender>target" → until when the AFK notice is not repeated. Cleared for a player when they quit. */
    private final Map<String, Long> afkNotices = new java.util.concurrent.ConcurrentHashMap<>();
    private final RateLimiter dmLimiter = new RateLimiter();
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();
    /** R3-32: who each account still in the tutorial messaged first (they may answer). */
    private final NewPlayerDms newPlayerDms = new NewPlayerDms();
    private SocialStore store;
    private Executor main;
    private Scheduler.Task afkTask;
    private volatile PlayerSettings settings;

    static final String SETTING_PMS = "private-messages";
    static final String SETTING_AFK_BROADCAST = "afk-broadcast";
    /** Owner answer 317: hide the public chat lines of players you ignore (for you only). */
    static final String SETTING_HIDE_IGNORED = "hide-ignored-chat";

    public SocialModule(Core core) {
        this.core = core;
        this.commands = core.commands();
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        // STAFF-R3: the /rules privacy lines got the report-DM line; an unedited live list is upgraded.
        core.messages().retire(ID, RetiredTexts.all());
        core.messages().load(ID);
        store = new SocialStore(core.database());
        main = new MainThread(core.plugin());
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        registerPlaceholders();
        registerCommands();
        // ANS3 (owner answers 316/317): one ignore list for tpa, trades, island invites and chat.
        core.services().register(ID, IgnoreService.class, this);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            afk.active(p.getUniqueId(), now);
            loadIgnores(p.getUniqueId());
        }
        // PERF8: the idle scan runs off the main thread (the tracker is thread-safe); the main thread only gets a task
        // (social:auto-afk) when someone is due, and checks each of them again there.
        afkTask = core.scheduler().asyncRepeat(200L, 200L, this::checkAutoAfk);
        // The menus module (which publishes PlayerSettings) enables after us; register our toggles one tick later.
        core.scheduler().later("social:settings", 1L, this::registerSettings);
    }

    private void registerSettings() {
        PlayerSettings service = core.services().optional(PlayerSettings.class).orElse(null);
        if (service == null) {
            return;
        }
        service.register(new SettingToggle(ID, SETTING_PMS, SettingCategory.CHAT, Material.WRITABLE_BOOK, true, null,
                "navigation", "settings.toggles." + SETTING_PMS, false));
        service.register(new SettingToggle(ID, SETTING_AFK_BROADCAST, SettingCategory.SOCIAL, Material.RED_BED, true, null,
                "navigation", "settings.toggles." + SETTING_AFK_BROADCAST, false));
        service.register(new SettingToggle(ID, SETTING_HIDE_IGNORED, SettingCategory.CHAT, Material.GRAY_DYE,
                config.hideIgnoredChatDefault, null, ID, "settings." + SETTING_HIDE_IGNORED, false));
        settings = service;
    }

    /** A /settings value, or the default while the settings service is not available. */
    private boolean setting(UUID player, String id, boolean fallback) {
        PlayerSettings s = settings;
        return s == null ? fallback : s.get(player, id);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
    }

    @Override
    public void disable() {
        PlayerSettings s = settings;
        if (s != null) {
            s.unregisterAll(ID);
            settings = null;
        }
        if (afkTask != null) {
            afkTask.cancel();
        }
        HandlerList.unregisterAll(this);
    }

    // ================================================================== commands

    private void registerCommands() {
        Arg<Player> target = visiblePlayer("player");
        Arg<String> message = Args.text("message", "Hey!", "Want to team up?", "gg");

        commands.register(CommandSpec.command("msg")
                .description("Send a private message")
                .category("Social")
                .permission(PERM_MSG)
                .aliases("w", "tell", "whisper", "m", "pm")
                .arg(target).arg(message)
                .example("msg Steve Want to team up?")
                .executes(ctx -> sendDm(ctx.sender(), ctx.get(target), ctx.get(message))));

        Arg<String> replyText = Args.text("message", "Thanks!", "On my way", "gg");
        commands.register(CommandSpec.command("r")
                .description("Reply to your last private message")
                .category("Social")
                .permission(PERM_MSG)
                .aliases("reply")
                .arg(replyText)
                .example("r Thanks!")
                .executes(ctx -> reply(ctx.sender(), ctx.get(replyText))));

        Arg<String> ignoreTarget = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(sender -> {
            List<String> values = new ArrayList<>();
            values.add("list");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && !hiddenFrom(p, sender)) {
                    values.add(p.getName());
                }
            }
            return values;
        }));
        commands.register(CommandSpec.command("ignore")
                .description("Ignore a player (messages, tpa, trades, invites), or see your ignore list")
                .category("Social")
                .permission(PERM_IGNORE)
                .playerOnly()
                .arg(ignoreTarget)
                .example("ignore list")
                .executes(ctx -> {
                    String value = ctx.get(ignoreTarget);
                    if (value.equalsIgnoreCase("list")) {
                        ignoreList(ctx.player());
                    } else {
                        ignore(ctx.player(), value);
                    }
                }));

        Arg<String> unignoreTarget = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(this::ignoredNames));
        commands.register(CommandSpec.command("unignore")
                .description("Stop ignoring a player")
                .category("Social")
                .permission(PERM_IGNORE)
                .playerOnly()
                .arg(unignoreTarget)
                .example("unignore Steve")
                .executes(ctx -> unignore(ctx.player(), ctx.get(unignoreTarget))));

        commands.register(CommandSpec.command("socialspy")
                .description("See every private message (staff)")
                .category("Staff")
                .permission(PERM_SPY)
                .playerOnly()
                .executes(ctx -> toggleSpy(ctx.player())));

        commands.register(CommandSpec.command("afk")
                .description("Mark yourself as away (or back)")
                .category("Social")
                .permission(PERM_AFK)
                .playerOnly()
                .cooldown(Duration.ofSeconds(3))
                .executes(ctx -> {
                    Player p = ctx.player();
                    setAfk(p, !afk.isAfk(p.getUniqueId()));
                }));

        commands.register(CommandSpec.command("list")
                .description("See who is online, by rank")
                .category("Social")
                .permission(PERM_LIST)
                .aliases("online", "who")
                .executes(ctx -> list(ctx.sender())));

        Arg<Player> pingTarget = visiblePlayer("player");
        commands.register(CommandSpec.command("ping")
                .description("Show your (or another player's) connection delay")
                .category("Social")
                .permission(PERM_PING)
                .optional(pingTarget)
                .example("ping Steve")
                .executes(ctx -> ping(ctx.sender(), ctx.getOr(pingTarget, null))));

        Arg<String> playtimeTarget = Args.offlinePlayer("player");
        commands.register(CommandSpec.command("playtime")
                .description("Show total play time")
                .category("Social")
                .permission(PERM_PLAYTIME)
                .aliases("pt")
                .optional(playtimeTarget)
                .example("playtime Steve")
                .executes(ctx -> playtime(ctx.sender(), ctx.getOr(playtimeTarget, null))));

        Arg<String> seenTarget = Args.offlinePlayer("player");
        commands.register(CommandSpec.command("seen")
                .description("When a player was last online")
                .category("Social")
                .permission(PERM_SEEN)
                .arg(seenTarget)
                .example("seen Steve")
                .executes(ctx -> seen(ctx.sender(), ctx.get(seenTarget))));

        Arg<String> whoisTarget = Args.offlinePlayer("player");
        commands.register(CommandSpec.command("whois")
                .description("Everything staff need to know about a player")
                .category("Staff")
                .permission(PERM_WHOIS)
                .arg(whoisTarget)
                .example("whois Steve")
                .executes(ctx -> whois(ctx.sender(), ctx.get(whoisTarget))));

        Arg<String> broadcastText = Args.text("message", "<c:flame>Server restart in 5 minutes", "Double XP is live!");
        commands.register(CommandSpec.command("broadcast")
                .description("Announce something to everyone (MiniMessage allowed)")
                .category("Staff")
                .permission(PERM_BROADCAST)
                .aliases("bc", "announce")
                .arg(broadcastText)
                .example("broadcast <c:flame>Double XP is live!")
                .executes(ctx -> broadcast(ctx.sender(), ctx.get(broadcastText))));

        link("discord", "Get the Discord invite", () -> config.discordUrl);
        link("store", "Open the store", () -> config.storeUrl, "buy");
        // /vote moved to the rewards module (M10.5): the vote menu with every site, the streak and the vote party.
        link("website", "Open the website", () -> config.websiteUrl, "web");
        commands.register(CommandSpec.command("rules")
                .description("Read the server rules")
                .category("Info")
                .permission(PERM_LINKS)
                .executes(ctx -> sendLink(ctx.sender(), "rules", "rules", config.websiteUrl)));
    }

    /**
     * GUI-5: the link line (clickable on Java). Bedrock can't open chat links, so Bedrock players also get a popup with
     * the address in the item name; a click sends it once more as plain text.
     */
    private void sendLink(org.bukkit.command.CommandSender sender, String name, String key, String url) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, key, pre("url", url));
            return;
        }
        TagResolver[] tags = {pre("url", url), Msg.p("link", url)};
        core.menus().offers().offer(player, net.skybornmc.skyblock.common.menu.Offer.builder(msg.module(), "links.popup")
                .id("link:" + name)
                .title("links.popup.titles." + name)
                .icon(org.bukkit.Material.BOOK)
                .placeholders(tags)
                .onInfoClick(p -> msg.send(p, "links.plain", Msg.p("link", url)))
                .chat(p -> msg.send(p, key, tags))
                .build());
    }

    private void link(String name, String description, java.util.function.Supplier<String> url, String... aliases) {
        commands.register(CommandSpec.command(name)
                .description(description)
                .category("Info")
                .permission(PERM_LINKS)
                .aliases(aliases)
                .executes(ctx -> sendLink(ctx.sender(), name, "links." + name, url.get())));
    }

    /** An online player the sender can see (vanished staff count as offline for players without vanish.see). */
    private Arg<Player> visiblePlayer(String name) {
        return Arg.word(name, (sender, raw) -> {
            Player p = Bukkit.getPlayerExact(raw);
            if (p == null || hiddenFrom(p, sender)) {
                throw CommandFailure.of("player-offline", Msg.p("input", raw));
            }
            return p;
        }, Suggest.bySender(sender -> Bukkit.getOnlinePlayers().stream()
                .filter(p -> !hiddenFrom(p, sender)).map(Player::getName).toList()));
    }

    /** True if {@code viewer} should not know {@code target} is online. */
    private boolean hiddenFrom(Player target, CommandSender viewer) {
        if (viewer instanceof Player v) {
            if (v.equals(target)) {
                return false;
            }
            // VIS: vanish only; a player hidden by the AFK pool or the Show players choice can still be messaged.
            if (!net.skybornmc.skyblock.common.visibility.Visibility.knows(v, target)) {
                return true;
            }
        }
        return isVanished(target) && !viewer.hasPermission(PERM_VANISH_SEE);
    }

    /** Reads the {@code vanished} placeholder if another module (staff mode/vanish) registered it. */
    private boolean isVanished(Player player) {
        return core.placeholders().has("vanished")
                && core.placeholders().text(player, "vanished").map("true"::equalsIgnoreCase).orElse(false);
    }

    private static UUID idOf(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId() : CONSOLE_ID;
    }

    private static Location loc(CommandSender sender) {
        return sender instanceof Player p ? p.getLocation() : null;
    }

    /**
     * Placeholder replaced before parsing, so it also works inside tag arguments (click/hover). Only for safe values:
     * player names, UUIDs and links from our own config. Quotes and tag brackets are stripped anyway.
     */
    static TagResolver pre(String name, Object value) {
        String safe = String.valueOf(value).replace("'", "").replace("<", "").replace(">", "").replace("\\", "");
        return TagResolver.resolver(name, net.kyori.adventure.text.minimessage.tag.Tag.preProcessParsed(safe));
    }

    private CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(ID, key, placeholders);
    }

    // ================================================================== private messages

    private void sendDm(CommandSender sender, CommandSender target, String text) {
        if (target.equals(sender)) {
            throw fail("dm.self");
        }
        // Muted players (moderation module, placeholder "muted") can't send private messages.
        if (sender instanceof Player self && core.placeholders().has("muted")
                && core.placeholders().text(self, "muted").map("true"::equalsIgnoreCase).orElse(false)) {
            throw fail("dm.muted");
        }
        if (target instanceof Player t && !sender.hasPermission(PERM_UNIGNORABLE)
                && !setting(t.getUniqueId(), SETTING_PMS, true)) {
            throw fail("dm.disabled", Msg.p("player", t.getName()));
        }
        // R3-32: an account still in the tutorial only gets DMs from staff and players it messaged first. The sender is
        // not told why (no hint that the player is new).
        if (target instanceof Player t && !newPlayerDms.allows(config.dmNewPlayerProtection, inTutorial(t),
                !(sender instanceof Player) || sender.hasPermission(PERM_UNIGNORABLE), t.getUniqueId(), idOf(sender))) {
            throw fail("dm.not-yet", Msg.p("player", t.getName()));
        }
        if (text.length() > config.dmMaxLength) {
            throw fail("dm.too-long", Msg.number("max", config.dmMaxLength));
        }
        // R3-32 / answer 305: links, IP addresses and phone numbers are refused like in chat; the swear list is not used.
        if (config.dmContactFilter && sender instanceof Player author) {
            Optional<net.skybornmc.skyblock.common.text.TextFilter> filter =
                    core.services().optional(net.skybornmc.skyblock.common.text.TextFilter.class);
            if (filter.isPresent()) {
                net.skybornmc.skyblock.common.text.TextFilter.Result result = filter.get().checkContact(author, text);
                if (result.isBlocked()) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("message", text);
                    details.put("reason", result.blocked());
                    core.audit().log(AuditCategory.DM, "dm_blocked", Actor.of(sender),
                            target instanceof Player t ? t.getUniqueId().toString() : "CONSOLE", target.getName(), null,
                            loc(sender), details);
                    throw fail("dm.blocked-" + result.blocked());
                }
            }
        }
        if (sender instanceof Player p && !p.hasPermission("skyblock.cooldown.bypass")) {
            long wait = dmLimiter.tryAcquire(p.getUniqueId(), System.currentTimeMillis(), config.dmRateMessages,
                    config.dmRateSeconds * 1000L);
            if (wait > 0) {
                throw fail("dm.slow-down", Msg.p("time", Format.duration(Duration.ofMillis(wait + 999))));
            }
        }
        boolean blocked = target instanceof Player t && !sender.hasPermission(PERM_UNIGNORABLE)
                && ignores.getOrDefault(t.getUniqueId(), Set.of()).contains(idOf(sender));
        String time = DATE.withZone(Format.zone()).format(Instant.now());

        msg.send(sender, "dm.sent", pre("target", target.getName()), Msg.p("message", text), Msg.p("time", time));
        if (!blocked) {
            msg.send(target, "dm.received", pre("sender", sender.getName()), Msg.p("message", text), Msg.p("time", time));
            if (target instanceof Player t) {
                core.sounds().play(t, "notify");
            }
            lastPartner.put(idOf(target), idOf(sender));
            if (target instanceof Player t) {
                tellIfAfk(sender, t);
            }
            if (sender instanceof Player self && target instanceof Player t && inTutorial(self)) {
                newPlayerDms.messaged(self.getUniqueId(), t.getUniqueId());
            }
        }
        lastPartner.put(idOf(sender), idOf(target));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.equals(sender) || staff.equals(target) || !staff.hasPermission(PERM_SPY)) {
                continue;
            }
            boolean spying = core.players().get(staff.getUniqueId())
                    .map(d -> "true".equals(d.setting(SETTING_SPY, "false"))).orElse(false);
            if (spying) {
                msg.send(staff, blocked ? "spy.line-blocked" : "spy.line", Msg.p("sender", sender.getName()),
                        Msg.p("target", target.getName()), Msg.p("message", text));
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", text);
        if (blocked) {
            details.put("blocked", "ignored");
        }
        core.audit().log(AuditCategory.DM, "dm", Actor.of(sender),
                target instanceof Player t ? t.getUniqueId().toString() : "CONSOLE", target.getName(), null, loc(sender), details);
    }

    /**
     * QOL3 (QoL #22, AFK part): the sender learns why there is no answer ("Steve is AFK (12m)"). Only for delivered
     * messages (an ignored sender learns nothing), at most once per {@code dm.afk-notice-seconds} per pair.
     */
    private void tellIfAfk(CommandSender sender, Player target) {
        long now = System.currentTimeMillis();
        long idle = afk.afkFor(target.getUniqueId(), now);
        if (idle < 0 || config.dmAfkNoticeSeconds <= 0) {
            return;
        }
        String pair = idOf(sender) + ">" + target.getUniqueId();
        Long until = afkNotices.get(pair);
        if (until != null && now < until) {
            return;
        }
        afkNotices.put(pair, now + config.dmAfkNoticeSeconds * 1000L);
        msg.send(sender, "dm.target-afk", Msg.p("player", target.getName()),
                Msg.p("time", Format.duration(Duration.ofMillis(Math.max(60_000L, idle)))));
    }

    /**
     * R3-32: true while the account has not finished the tutorial (common TutorialService). Unknown (no tutorial module,
     * state not loaded) counts as finished, so old players are never cut off.
     */
    private boolean inTutorial(Player player) {
        return core.services().optional(net.skybornmc.skyblock.common.tutorial.TutorialService.class)
                .flatMap(t -> t.state(player.getUniqueId()))
                .map(state -> !state.complete()).orElse(false);
    }

    private void reply(CommandSender sender, String text) {
        UUID partner = lastPartner.get(idOf(sender));
        if (partner == null) {
            throw fail("dm.no-reply");
        }
        CommandSender target = partner.equals(CONSOLE_ID) ? Bukkit.getConsoleSender() : Bukkit.getPlayer(partner);
        if (target == null || (target instanceof Player t && hiddenFrom(t, sender))) {
            throw fail("dm.reply-offline");
        }
        sendDm(sender, target, text);
    }

    private void toggleSpy(Player player) {
        PlayerData data = core.players().require(player);
        boolean now = !"true".equals(data.setting(SETTING_SPY, "false"));
        data.setSetting(SETTING_SPY, String.valueOf(now));
        msg.send(player, now ? "spy.enabled" : "spy.disabled");
        core.audit().log(AuditCategory.STAFF, now ? "socialspy_on" : "socialspy_off", Actor.player(player), Map.of());
    }

    // ================================================================== ignore

    @Override
    public boolean isIgnoring(UUID viewer, UUID sender) {
        return viewer != null && sender != null && ignores.getOrDefault(viewer, Set.of()).contains(sender);
    }

    @Override
    public boolean hidesChat(UUID viewer, UUID sender) {
        return isIgnoring(viewer, sender) && setting(viewer, SETTING_HIDE_IGNORED, config.hideIgnoredChatDefault);
    }

    /**
     * Owner answer 317: public chat lines of an ignored player are not shown to the players who ignore them and have
     * {@code /settings → Hide ignored chat} on. NORMAL: after the chat checks (LOW), before the chat module renders
     * (HIGH) and plays mention sounds, so an ignored player's mention makes no sound either. Staff are never hidden.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPublicChat(AsyncChatEvent event) {
        Player source = event.getPlayer();
        if (source.hasPermission(PERM_UNIGNORABLE)) {
            return;
        }
        UUID id = source.getUniqueId();
        event.viewers().removeIf(audience -> audience instanceof Player viewer && !viewer.getUniqueId().equals(id)
                && hidesChat(viewer.getUniqueId(), id));
    }

    private void loadIgnores(UUID uuid) {
        store.ignores(uuid).thenAcceptAsync(set -> {
            if (Bukkit.getPlayer(uuid) != null) {
                ignores.put(uuid, ConcurrentHashMap.newKeySet());
                ignores.get(uuid).addAll(set);
            }
        }, main).exceptionally(e -> {
            core.plugin().getLogger().warning("Could not load ignore list of " + uuid + ": " + e.getMessage());
            return null;
        });
    }

    private void ignore(Player player, String name) {
        if (name.equalsIgnoreCase(player.getName())) {
            throw fail("ignore.self");
        }
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(player, "ignore.unknown", Msg.p("player", name));
                return;
            }
            UUID id = found.get().uuid();
            Player online = Bukkit.getPlayer(id);
            if (online != null && online.hasPermission(PERM_UNIGNORABLE)) {
                msg.send(player, "ignore.staff", Msg.p("player", found.get().name()));
                return;
            }
            Set<UUID> set = ignores.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            if (!set.add(id)) {
                msg.send(player, "ignore.already", Msg.p("player", found.get().name()));
                return;
            }
            store.addIgnore(player.getUniqueId(), id);
            msg.send(player, "ignore.added", pre("player", found.get().name()));
            core.audit().logPlayer(AuditCategory.CHAT, "ignore", Actor.player(player), id, found.get().name(), null, null, Map.of());
        }, main);
    }

    private void unignore(Player player, String name) {
        resolve(name).thenAcceptAsync(found -> {
            Set<UUID> set = ignores.getOrDefault(player.getUniqueId(), Set.of());
            if (found.isEmpty() || !set.contains(found.get().uuid())) {
                msg.send(player, "ignore.not-ignored", Msg.p("player", name));
                return;
            }
            set.remove(found.get().uuid());
            store.removeIgnore(player.getUniqueId(), found.get().uuid());
            msg.send(player, "ignore.removed", Msg.p("player", found.get().name()));
            core.audit().logPlayer(AuditCategory.CHAT, "unignore", Actor.player(player), found.get().uuid(), found.get().name(),
                    null, null, Map.of());
        }, main);
    }

    private void ignoreList(Player player) {
        Set<UUID> set = Set.copyOf(ignores.getOrDefault(player.getUniqueId(), Set.of()));
        if (set.isEmpty()) {
            msg.send(player, "ignore.list-empty");
            return;
        }
        List<CompletableFuture<String>> names = set.stream().map(this::nameOf).toList();
        CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)).thenRunAsync(() -> {
            List<String> sorted = names.stream().map(CompletableFuture::join).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            msg.send(player, "ignore.list", Msg.number("count", sorted.size()), Msg.p("players", String.join(", ", sorted)));
        }, main);
    }

    /** Names on the sender's ignore list (online names only; offline ones still work when typed). */
    private List<String> ignoredNames(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (UUID id : ignores.getOrDefault(p.getUniqueId(), Set.of())) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                out.add(online.getName());
            } else {
                core.players().get(id).map(PlayerData::name).ifPresent(out::add);
            }
        }
        return out;
    }

    // ================================================================== AFK

    private void registerPlaceholders() {
        core.placeholders().registerText(ID, "afk", p -> p != null && afk.isAfk(p.getUniqueId()) ? "true" : "false");
        core.placeholders().register(ID, "afk_tag", new Placeholder() {
            @Override
            public Component component(Player p) {
                return p != null && afk.isAfk(p.getUniqueId()) ? msg.component(p, "afk.tag") : Component.empty();
            }
        });
    }

    private void setAfk(Player player, boolean value) {
        if (!afk.set(player.getUniqueId(), value, System.currentTimeMillis())) {
            return;
        }
        announceAfk(player, value);
    }

    private void announceAfk(Player player, boolean nowAfk) {
        if (config.afkBroadcast) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!hiddenFrom(player, p) && (p.equals(player) || setting(p.getUniqueId(), SETTING_AFK_BROADCAST, true))) {
                    msg.send(p, nowAfk ? "afk.now-afk" : "afk.back", Msg.p("player", player.getName()));
                }
            }
            msg.send(Bukkit.getConsoleSender(), nowAfk ? "afk.now-afk" : "afk.back", Msg.p("player", player.getName()));
        } else {
            msg.send(player, nowAfk ? "afk.you-afk" : "afk.you-back");
        }
    }

    /** Any thread: finds the players idle long enough; marks them AFK on the main thread. */
    private void checkAutoAfk() {
        if (config.afkAutoMinutes <= 0) {
            return;
        }
        long idle = config.afkAutoMinutes * 60_000L;
        java.util.Set<UUID> due = afk.dueForAuto(System.currentTimeMillis(), idle);
        if (due.isEmpty() || !core.plugin().isEnabled()) {
            return;
        }
        core.scheduler().ensureMain("social:auto-afk", () -> {
            long now = System.currentTimeMillis();
            for (UUID id : due) {
                Player p = Bukkit.getPlayer(id);
                // Checked again here: activity between the scan and this tick keeps them active, as before.
                if (p != null && afk.stillDue(id, now, idle) && afk.set(id, true, now)) {
                    announceAfk(p, true);
                }
            }
        });
    }

    private void activity(Player player) {
        if (afk.active(player.getUniqueId(), System.currentTimeMillis())) {
            core.scheduler().ensureMain("social:afk-back", () -> {
                if (player.isOnline()) {
                    announceAfk(player, false);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Walking (block change) or really looking around; tiny jitters (AFK pools, knockback) don't count.
        if (event.hasChangedBlock() || Math.abs(from.getYaw() - to.getYaw()) > 15 || Math.abs(from.getPitch() - to.getPitch()) > 10) {
            activity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String lower = event.getMessage().toLowerCase(Locale.ROOT);
        if (!lower.equals("/afk") && !lower.startsWith("/afk ")) {
            activity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        activity(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        afk.active(p.getUniqueId(), System.currentTimeMillis());
        loadIgnores(p.getUniqueId());
        if (p.getAddress() != null) {
            store.recordIp(p.getUniqueId(), p.getAddress().getAddress().getHostAddress()).exceptionally(e -> {
                core.plugin().getLogger().warning("Could not record address of " + p.getName() + ": " + e.getMessage());
                return null;
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        afk.remove(id);
        ignores.remove(id);
        dmLimiter.forget(id);
        lastPartner.remove(id);
        newPlayerDms.forget(id);
        String key = id.toString();
        afkNotices.keySet().removeIf(pair -> pair.startsWith(key) || pair.endsWith(key));
    }

    // ================================================================== info commands

    private void list(CommandSender sender) {
        record Entry(Player player, String group, String display, int weight, net.kyori.adventure.text.format.TextColor color) {
        }
        List<Entry> visible = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hiddenFrom(p, sender)) {
                visible.add(new Entry(p, core.ranks().primaryGroup(p), core.ranks().groupDisplayName(p), core.ranks().weight(p),
                        core.ranks().rankColor(p)));
            }
        }
        msg.send(sender, "list.header", Msg.number("online", visible.size()), Msg.number("max", Bukkit.getMaxPlayers()));
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        visible.stream()
                .sorted(Comparator.comparingInt(Entry::weight).reversed()
                        .thenComparing(e -> e.player().getName(), String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> groups.computeIfAbsent(e.group(), g -> new ArrayList<>()).add(e));
        groups.forEach((group, entries) -> {
            Entry first = entries.getFirst();
            String color = first.color() == null ? "#FFFFFF" : first.color().asHexString();
            List<String> names = new ArrayList<>();
            for (Entry e : entries) {
                String afkTag = afk.isAfk(e.player().getUniqueId()) ? msg.has("list.afk-suffix")
                        ? core.messages().raw(ID, "list.afk-suffix") : "" : "";
                names.add("<click:suggest_command:'/msg " + e.player().getName() + " '><hover:show_text:'<c:text>Click to message "
                        + e.player().getName() + "'><" + color + ">" + e.player().getName() + "</" + color + "></hover></click>"
                        + afkTag);
            }
            msg.send(sender, "list.group", Msg.mm("rank", "<" + color + ">" + escape(first.display()) + "</" + color + ">"),
                    Msg.number("count", entries.size()), Msg.mm("players", String.join("<c:muted>, </c:muted>", names)));
        });
    }

    /** Escapes MiniMessage tags in a name we put into a parsed placeholder. */
    private static String escape(String text) {
        return text == null ? "" : text.replace("<", "\\<");
    }

    private void ping(CommandSender sender, Player target) {
        Player who = target;
        if (who == null) {
            if (!(sender instanceof Player p)) {
                throw CommandFailure.of("players-only");
            }
            who = p;
        }
        int ping = who.getPing();
        String key = ping < 80 ? "ping.good" : ping < 180 ? "ping.ok" : "ping.bad";
        msg.send(sender, who.equals(sender) ? key + "-self" : key + "-other", Msg.p("player", who.getName()), Msg.number("ping", ping));
    }

    private void playtime(CommandSender sender, String name) {
        if (name == null) {
            if (!(sender instanceof Player p)) {
                throw CommandFailure.of("players-only");
            }
            long ms = core.players().require(p).playtimeMillis();
            msg.send(sender, "playtime.self", Msg.p("time", Format.duration(Duration.ofMillis(ms))));
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && !hiddenFrom(online, sender)) {
            long ms = core.players().get(online.getUniqueId()).map(PlayerData::playtimeMillis).orElse(0L);
            msg.send(sender, "playtime.other", Msg.p("player", online.getName()), Msg.p("time", Format.duration(Duration.ofMillis(ms))));
            return;
        }
        core.players().offline(name).thenAcceptAsync(snap -> {
            if (snap.isEmpty()) {
                msg.send(sender, "unknown-player", Msg.p("player", name));
                return;
            }
            msg.send(sender, "playtime.other", Msg.p("player", snap.get().name()),
                    Msg.p("time", Format.duration(Duration.ofMillis(snap.get().playtimeMillis()))));
        }, main);
    }

    private void seen(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        boolean details = sender.hasPermission(PERM_SEEN_DETAILS);
        if (online != null && !hiddenFrom(online, sender)) {
            PlayerData data = core.players().get(online.getUniqueId()).orElse(null);
            msg.send(sender, "seen.online", Msg.p("player", online.getName()),
                    Msg.p("first", data == null ? "?" : DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(data.firstSeen()))));
            if (details) {
                msg.send(sender, "seen.details", pre("uuid", online.getUniqueId()),
                        Msg.p("platform", core.bedrock().isBedrock(online.getUniqueId()) ? "Bedrock" : "Java"));
            }
            return;
        }
        core.players().offline(name).thenAcceptAsync(snap -> {
            if (snap.isEmpty()) {
                msg.send(sender, "unknown-player", Msg.p("player", name));
                return;
            }
            PlayerData.Snapshot s = snap.get();
            long ago = Math.max(0, System.currentTimeMillis() - s.lastSeen());
            msg.send(sender, "seen.offline", Msg.p("player", s.name()), Msg.p("ago", Format.duration(Duration.ofMillis(ago))),
                    Msg.p("last", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(s.lastSeen()))),
                    Msg.p("first", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(s.firstSeen()))));
            if (details) {
                msg.send(sender, "seen.details", pre("uuid", s.uuid()), Msg.p("platform", s.bedrock() ? "Bedrock" : "Java"));
            }
        }, main);
    }

    private void whois(CommandSender sender, String name) {
        core.players().offline(name).thenComposeAsync(snap -> {
            if (snap.isEmpty()) {
                msg.send(sender, "unknown-player", Msg.p("player", name));
                return CompletableFuture.completedFuture(null);
            }
            PlayerData.Snapshot s = snap.get();
            boolean showIp = sender.hasPermission(PERM_WHOIS_IP);
            CompletableFuture<List<net.skybornmc.skyblock.common.player.PlayerRepository.NameRecord>> history =
                    core.players().nameHistory(s.uuid());
            CompletableFuture<List<SocialStore.IpRecord>> ips = showIp ? store.ips(s.uuid())
                    : CompletableFuture.completedFuture(List.of());
            CompletableFuture<List<SocialStore.AltRecord>> alts = showIp ? store.alts(s.uuid())
                    : CompletableFuture.completedFuture(List.of());
            return CompletableFuture.allOf(history, ips, alts)
                    .thenRunAsync(() -> sendWhois(sender, s, history.join(), ips.join(), alts.join(), showIp), main);
        }, main).exceptionally(e -> {
            msg.send(sender, "whois.error");
            core.plugin().getLogger().warning("/whois " + name + " failed: " + e.getMessage());
            return null;
        });
        core.audit().log(AuditCategory.STAFF, "whois", Actor.of(sender), null, name, null, loc(sender), Map.of());
    }

    private void sendWhois(CommandSender sender, PlayerData.Snapshot s,
                           List<net.skybornmc.skyblock.common.player.PlayerRepository.NameRecord> history,
                           List<SocialStore.IpRecord> ips, List<SocialStore.AltRecord> alts, boolean showIp) {
        Player online = Bukkit.getPlayer(s.uuid());
        boolean isOnline = online != null && !hiddenFrom(online, sender);
        long playtime = isOnline ? core.players().get(s.uuid()).map(PlayerData::playtimeMillis).orElse(s.playtimeMillis())
                : s.playtimeMillis();
        String rank = isOnline ? core.ranks().groupDisplayName(online) : "-";
        String platform = s.bedrock() ? "Bedrock" + (isOnline ? core.bedrock().device(s.uuid()).map(d -> " (" + d + ")").orElse("") : "")
                : "Java";
        msg.send(sender, "whois.header", Msg.p("player", s.name()));
        msg.send(sender, "whois.uuid", pre("uuid", s.uuid()));
        msg.send(sender, "whois.status", Msg.p("status", isOnline ? "online" : "offline"),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(0, System.currentTimeMillis() - s.lastSeen())))));
        msg.send(sender, "whois.rank", Msg.p("rank", rank), Msg.p("platform", platform));
        msg.send(sender, "whois.times", Msg.p("first", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(s.firstSeen()))),
                Msg.p("last", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(s.lastSeen()))),
                Msg.p("playtime", Format.duration(Duration.ofMillis(playtime))));
        if (isOnline) {
            Location l = online.getLocation();
            msg.send(sender, "whois.location", Msg.p("world", l.getWorld().getName()), Msg.number("x", l.getBlockX()),
                    Msg.number("y", l.getBlockY()), Msg.number("z", l.getBlockZ()),
                    Msg.p("gamemode", online.getGameMode().name().toLowerCase(Locale.ROOT)), Msg.number("ping", online.getPing()));
        }
        List<String> names = history.stream().map(h -> h.name()).distinct().toList();
        msg.send(sender, "whois.names", Msg.p("names", names.isEmpty() ? s.name() : String.join(", ", names)));
        if (showIp) {
            msg.send(sender, "whois.ips", Msg.p("ips", ips.isEmpty() ? "-" : String.join(", ", ips.stream().map(SocialStore.IpRecord::ip).toList())));
            if (alts.isEmpty()) {
                msg.send(sender, "whois.no-alts");
            } else {
                Map<UUID, String> unique = new LinkedHashMap<>();
                alts.forEach(a -> unique.putIfAbsent(a.uuid(), a.name() == null ? a.uuid().toString() : a.name()));
                msg.send(sender, "whois.alts", Msg.number("count", unique.size()), Msg.p("alts", String.join(", ", unique.values())));
            }
        }
    }

    private void broadcast(CommandSender sender, String text) {
        // Staff-only command, so the text is trusted MiniMessage (colors, gradients, style tags).
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "broadcast.chat", Msg.mm("message", text), Msg.p("sender", sender.getName()));
            if (config.broadcastTitle) {
                p.showTitle(Title.title(msg.component(p, "broadcast.title"),
                        core.messages().parse(p, text), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4),
                                Duration.ofMillis(800))));
            }
            core.sounds().play(p, "notify");
        }
        msg.send(Bukkit.getConsoleSender(), "broadcast.chat", Msg.mm("message", text), Msg.p("sender", sender.getName()));
        core.audit().log(AuditCategory.STAFF, "broadcast", Actor.of(sender), null, null, null, loc(sender), Map.of("message", text));
    }

    // ================================================================== lookups

    private record Found(UUID uuid, String name) {
    }

    /** Online player or offline snapshot by name. */
    private CompletableFuture<Optional<Found>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Found(online.getUniqueId(), online.getName())));
        }
        return core.players().offline(name).thenApply(s -> s.map(snap -> new Found(snap.uuid(), snap.name())));
    }

    private CompletableFuture<String> nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getName());
        }
        return core.players().offline(uuid).thenApply(s -> s.map(PlayerData.Snapshot::name).orElse(uuid.toString()));
    }
}
