package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.audit.AuditQuery;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Durations;
import net.skybornmc.skyblock.common.command.OfflineNames;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * M1.10 staff log viewer: {@code /logs} opens a filter menu (subject, category, time range) and a paged, newest-first
 * result list; quick sub-commands jump straight to common searches. Console (and optionally Bedrock) gets chat output.
 * What a staff member may see is limited by {@code skyblock.staff.logs.category.<category>}; IPs need
 * {@code skyblock.staff.whois.ip}. Every search is itself logged ({@code staff/logs_view}).
 */
public final class LogsModule implements SkyblockModule {

    static final String ID = "logs";

    static final String PERM_USE = "skyblock.staff.logs";
    static final String PERM_CATEGORY = "skyblock.staff.logs.category.";
    static final String PERM_STAFF = "skyblock.staff.logs.staff";
    static final String PERM_EXPORT = "skyblock.staff.logs.export";
    static final String PERM_TELEPORT = "skyblock.staff.logs.teleport";
    static final String PERM_IP = "skyblock.staff.whois.ip";

    /** Entries per menu page (the 28 inner slots). */
    static final int MENU_PAGE = 28;
    private static final UUID CONSOLE_KEY = new UUID(0, 0);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    final Core core;
    final ModuleMessages msg;
    final LogsConfig config = new LogsConfig();
    final LogRender render;
    private final Map<UUID, LogFilter> lastSearch = new ConcurrentHashMap<>();
    private Executor main;

    /** One page of results plus the total number of matches. */
    record Page(List<AuditEntry> rows, long total) {
    }

    public LogsModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.render = new LogRender(this);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        core.configs().load(config);
        main = new MainThread(core.plugin());
        // Phase P 13.11: one entry by id for /p <player> --evidence <id> (moderation), with this module's visibility rules
        core.services().register(ID, net.skybornmc.skyblock.common.audit.LogLookup.class,
                new LogPunish.Lookup(core.database(), core.audit(), this::allowed));
        registerCommands();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
    }

    @Override
    public void disable() {
        lastSearch.clear();
        core.services().unregisterAll(ID);
    }

    /**
     * Punish from logs (13.11): runs {@code /punish <player> --evidence <id>} for {@code staff} (the moderation module's
     * entry point; it checks everything again). Returns false when the entry is not a chat line or it is not allowed.
     */
    boolean punishFrom(Player staff, AuditEntry entry) {
        String command = LogPunish.command(entry);
        if (command == null || !LogPunish.allowed(staff)) {
            return false;
        }
        core.audit().log(AuditCategory.STAFF, "logs_punish_open", Actor.player(staff), entry.actor().uuid().toString(),
                entry.actor().name(), null, null, Map.of("entry", entry.id()));
        return staff.performCommand(command);
    }

    // ------------------------------------------------------------------------------------------------ permissions

    /** Categories {@code sender} may see (console: all). */
    Set<AuditCategory> allowed(CommandSender sender) {
        Set<AuditCategory> out = EnumSet.noneOf(AuditCategory.class);
        for (AuditCategory c : AuditCategory.values()) {
            if (!(sender instanceof Player) || sender.hasPermission(PERM_CATEGORY + c.key())) {
                out.add(c);
            }
        }
        return out;
    }

    boolean canSeeIps(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(PERM_IP);
    }

    boolean canTeleport(CommandSender sender) {
        return sender instanceof Player && sender.hasPermission(PERM_TELEPORT);
    }

    boolean canExport(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(PERM_EXPORT);
    }

    /** Parses a configured range ({@code 7d}, {@code all}); {@link Duration#ZERO} = all time. */
    Duration parseRange(String raw) {
        if (raw == null || raw.equalsIgnoreCase("all")) {
            return Duration.ZERO;
        }
        try {
            return Durations.parse(raw);
        } catch (IllegalArgumentException e) {
            return Duration.ofDays(7);
        }
    }

    Duration defaultRange() {
        return parseRange(config.defaultRange);
    }

    // ------------------------------------------------------------------------------------------------ commands

    private void registerCommands() {
        Arg<String> player = Args.offlinePlayer("player");
        Arg<String> other = Args.offlinePlayer("other");
        Arg<String> category = Arg.word("category", (sender, raw) -> {
            if (raw.equalsIgnoreCase("all") || AuditCategory.byKey(raw).isPresent()) {
                return raw.toLowerCase(Locale.ROOT);
            }
            throw new CommandFailure(ID, "errors.unknown-category", Msg.p("input", raw), Msg.p("choices", categoryChoices(sender)));
        }, Suggest.bySender(sender -> {
            List<String> values = new ArrayList<>();
            values.add("all");
            allowed(sender).forEach(c -> values.add(c.key()));
            return values;
        }));
        Arg<Duration> time = Arg.word("time", (sender, raw) -> {
            if (raw.equalsIgnoreCase("all")) {
                return Duration.ZERO;
            }
            try {
                return Durations.parse(raw);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-duration", Msg.p("input", raw));
            }
        }, Suggest.of(() -> config.ranges));
        Arg<Integer> radius = Args.integer("radius", 1, 5000, "10", "30", "50", "100");
        Arg<String> islandTarget = Arg.word("island", (sender, raw) -> raw, Suggest.of(OfflineNames::names));
        Arg<UUID> itemUuid = Arg.word("item", (sender, raw) -> {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                throw new CommandFailure(ID, "errors.not-uuid", Msg.p("input", raw));
            }
        }, Suggest.bySender(sender -> heldItemUuid(sender).map(u -> List.of(u.toString())).orElse(List.of())));
        Arg<String> exportWhat = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(sender -> {
            List<String> values = new ArrayList<>();
            values.add("last");
            values.addAll(OfflineNames.names());
            return values;
        }));
        Arg<Integer> page = Args.integer("page", 1, 100_000, "1", "2", "3");
        Arg<World> world = Args.world("world");
        Arg<Integer> x = Args.integer("x", -30_000_000, 30_000_000, "0");
        Arg<Integer> y = Args.integer("y", -2048, 2048, "64");
        Arg<Integer> z = Args.integer("z", -30_000_000, 30_000_000, "0");

        core.commands().register(CommandSpec.command("logs")
                .description("Search the audit log: who did what, when and where")
                .category("Staff")
                .permission(PERM_USE)
                .aliases("log", "auditlog")
                .executes(ctx -> {
                    if (ctx.sender() instanceof Player p && !chatFor(p)) {
                        openHub(p, new LogFilter());
                    } else {
                        msg.send(ctx.sender(), "usage");
                    }
                })
                .sub(CommandSpec.sub("player")
                        .description("Everything a player did or had done to them")
                        .arg(player).optional(category).optional(time)
                        .example("logs player Steve economy 7d")
                        .executes(ctx -> withPlayer(ctx.sender(), ctx.get(player), (uuid, name) -> {
                            LogFilter f = new LogFilter().player(uuid, name);
                            String cat = ctx.getOr(category, "all");
                            if (!cat.equals("all")) {
                                f.categories(AuditCategory.byKey(cat).orElseThrow());
                            }
                            f.range = ctx.getOr(time, defaultRange());
                            show(ctx.sender(), f);
                        })))
                .sub(CommandSpec.sub("pay")
                        .description("Money between players (pay, trades, auction)")
                        .arg(player).optional(other)
                        .example("logs pay Steve Alex")
                        .executes(ctx -> pay(ctx.sender(), ctx.get(player), ctx.getOr(other, null))))
                .sub(CommandSpec.sub("economy")
                        .description("Money between players (pay, trades, auction)")
                        .arg(player).optional(other)
                        .example("logs economy Steve")
                        .executes(ctx -> pay(ctx.sender(), ctx.get(player), ctx.getOr(other, null))))
                .sub(CommandSpec.sub("trade")
                        .description("A player's secure trades")
                        .arg(player).optional(time)
                        .example("logs trade Steve")
                        .executes(ctx -> categorySearch(ctx.sender(), ctx.get(player), ctx.getOr(time, Duration.ZERO), false,
                                AuditCategory.TRADE)))
                .sub(CommandSpec.sub("chat")
                        .description("What a player said in chat")
                        .arg(player).optional(time)
                        .example("logs chat Steve 24h")
                        .executes(ctx -> categorySearch(ctx.sender(), ctx.get(player), ctx.getOr(time, defaultRange()), true,
                                AuditCategory.CHAT)))
                .sub(CommandSpec.sub("commands")
                        .description("Commands a player ran")
                        .arg(player).optional(time)
                        .example("logs commands Steve 24h")
                        .executes(ctx -> categorySearch(ctx.sender(), ctx.get(player), ctx.getOr(time, defaultRange()), true,
                                AuditCategory.COMMAND)))
                .sub(CommandSpec.sub("island")
                        .description("Island actions of a player or an island id")
                        .arg(islandTarget).optional(time)
                        .example("logs island Steve")
                        .executes(ctx -> island(ctx.sender(), ctx.get(islandTarget), ctx.getOr(time, Duration.ZERO))))
                .sub(CommandSpec.sub("item")
                        .description("The full life of a unique item (dupe investigations)")
                        .arg(itemUuid)
                        .example("logs item 2f1c0b6e-0000-4000-8000-000000000000")
                        .executes(ctx -> {
                            LogFilter f = new LogFilter();
                            f.subject = LogFilter.Subject.ITEM;
                            f.target = ctx.get(itemUuid).toString();
                            f.range = Duration.ZERO;
                            show(ctx.sender(), f);
                        }))
                .sub(CommandSpec.sub("near")
                        .description("Actions near you")
                        .playerOnly()
                        .optional(radius).optional(time)
                        .example("logs near 30 24h")
                        .executes(ctx -> {
                            int r = Math.min(config.nearMaxRadius, ctx.getOr(radius, config.nearDefaultRadius));
                            show(ctx.sender(), near(ctx.player(), r, ctx.getOr(time, defaultRange())));
                        }))
                .sub(CommandSpec.sub("staff")
                        .description("Everything a staff member did (accountability)")
                        .permission(PERM_STAFF)
                        .arg(player).optional(time)
                        .example("logs staff Steve 30d")
                        .executes(ctx -> withPlayer(ctx.sender(), ctx.get(player), (uuid, name) -> {
                            LogFilter f = new LogFilter().actor(uuid, name);
                            f.range = ctx.getOr(time, Duration.ofDays(30));
                            show(ctx.sender(), f);
                        })))
                .sub(CommandSpec.sub("export")
                        .description("Export a player's logs (or your last search) as CSV")
                        .permission(PERM_EXPORT)
                        .arg(exportWhat).optional(category).optional(time)
                        .example("logs export last")
                        .executes(ctx -> {
                            String what = ctx.get(exportWhat);
                            if (what.equalsIgnoreCase("last")) {
                                LogFilter last = lastSearch.get(key(ctx.sender()));
                                if (last == null) {
                                    throw new CommandFailure(ID, "errors.no-search");
                                }
                                export(ctx.sender(), last);
                                return;
                            }
                            withPlayer(ctx.sender(), what, (uuid, name) -> {
                                LogFilter f = new LogFilter().player(uuid, name);
                                String cat = ctx.getOr(category, "all");
                                if (!cat.equals("all")) {
                                    f.categories(AuditCategory.byKey(cat).orElseThrow());
                                }
                                f.range = ctx.getOr(time, Duration.ZERO);
                                export(ctx.sender(), f);
                            });
                        }))
                .sub(CommandSpec.sub("page")
                        .description("Show another page of your last search in chat")
                        .arg(page)
                        .example("logs page 2")
                        .executes(ctx -> {
                            LogFilter last = lastSearch.get(key(ctx.sender()));
                            if (last == null) {
                                throw new CommandFailure(ID, "errors.no-search");
                            }
                            chatPage(ctx.sender(), last, ctx.get(page) - 1);
                        }))
                .sub(CommandSpec.sub("teleport")
                        .description("Teleport to the place of a log entry")
                        .permission(PERM_TELEPORT)
                        .playerOnly()
                        .hidden()
                        .arg(world).arg(x).arg(y).arg(z)
                        .example("logs teleport islands 100 64 -20")
                        .executes(ctx -> teleport(ctx.player(), new Location(ctx.get(world), ctx.get(x) + 0.5, ctx.get(y),
                                ctx.get(z) + 0.5))))
        );
    }

    private String categoryChoices(CommandSender sender) {
        List<String> values = new ArrayList<>();
        values.add("all");
        allowed(sender).forEach(c -> values.add(c.key()));
        return String.join(", ", values);
    }

    private void pay(CommandSender sender, String name, String otherName) {
        withPlayer(sender, name, (uuid, resolved) -> {
            if (otherName == null) {
                LogFilter f = new LogFilter().player(uuid, resolved).categories(AuditCategory.ECONOMY, AuditCategory.TRADE,
                        AuditCategory.AUCTION);
                f.range = Duration.ZERO;
                show(sender, f);
                return;
            }
            withPlayer(sender, otherName, (otherUuid, otherResolved) -> {
                LogFilter f = new LogFilter().categories(AuditCategory.ECONOMY, AuditCategory.TRADE, AuditCategory.AUCTION);
                f.subject = LogFilter.Subject.BETWEEN;
                f.player = uuid;
                f.playerName = resolved;
                f.other = otherUuid;
                f.otherName = otherResolved;
                f.range = Duration.ZERO;
                show(sender, f);
            });
        });
    }

    private void categorySearch(CommandSender sender, String name, Duration range, boolean asActor, AuditCategory category) {
        withPlayer(sender, name, (uuid, resolved) -> {
            LogFilter f = asActor ? new LogFilter().actor(uuid, resolved) : new LogFilter().player(uuid, resolved);
            f.categories(category);
            f.range = range;
            show(sender, f);
        });
    }

    private void island(CommandSender sender, String value, Duration range) {
        String known = OfflineNames.names().stream().filter(n -> n.equalsIgnoreCase(value)).findFirst().orElse(null);
        if (known == null) {
            LogFilter f = new LogFilter().categories(AuditCategory.ISLAND);
            f.subject = LogFilter.Subject.ISLAND;
            f.target = value;
            f.range = range;
            show(sender, f);
            return;
        }
        withPlayer(sender, known, (uuid, resolved) -> {
            LogFilter f = new LogFilter().categories(AuditCategory.ISLAND);
            f.subject = LogFilter.Subject.ISLAND;
            f.player = uuid;
            f.playerName = resolved;
            f.range = range;
            show(sender, f);
        });
    }

    LogFilter near(Player player, int radius, Duration range) {
        LogFilter f = new LogFilter();
        f.subject = LogFilter.Subject.NEAR;
        Location at = player.getLocation();
        f.world = at.getWorld().getName();
        f.x = at.getBlockX();
        f.z = at.getBlockZ();
        f.radius = radius;
        f.range = range;
        return f;
    }

    /** The UUID of the unique custom item in the sender's main hand, if any. */
    Optional<UUID> heldItemUuid(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return Optional.empty();
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        return hand.isEmpty() ? Optional.empty() : ItemRegistry.uuidOf(hand);
    }

    // ------------------------------------------------------------------------------------------------ player lookup

    /** Resolves a (possibly offline) player name to uuid + correctly cased name, then runs {@code then} on the main thread. */
    void withPlayer(CommandSender sender, String name, java.util.function.BiConsumer<UUID, String> then) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            then.accept(online.getUniqueId(), online.getName());
            return;
        }
        core.players().offline(name).whenCompleteAsync((found, error) -> {
            if (error != null || found == null || found.isEmpty()) {
                msg.send(sender, "errors.unknown-player", Msg.p("input", name));
                sound(sender, "error");
                return;
            }
            PlayerData.Snapshot snapshot = found.get();
            then.accept(snapshot.uuid(), snapshot.name());
        }, main);
    }

    // ------------------------------------------------------------------------------------------------ searching

    private UUID key(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId() : CONSOLE_KEY;
    }

    private boolean chatFor(Player player) {
        return config.bedrockChatOutput && core.players().get(player.getUniqueId()).map(PlayerData::bedrock).orElse(false);
    }

    void openHub(Player player, LogFilter filter) {
        core.menus().open(player, new LogsHubMenu(this, filter));
    }

    /** Runs a search for {@code sender}: menu for players, chat for console (and Bedrock if configured). Logs the view. */
    void show(CommandSender sender, LogFilter filter) {
        if (!prepare(sender, filter)) {
            return;
        }
        if (sender instanceof Player p && !chatFor(p)) {
            core.menus().open(p, new LogResultsMenu(this, filter.copy()));
        } else {
            chatPage(sender, filter, 0);
        }
    }

    /**
     * Checks that {@code sender} may see anything of {@code filter}, remembers it as their last search and logs the view
     * ({@code staff/logs_view}). Returns {@code false} (and tells them) when nothing is visible.
     */
    boolean prepare(CommandSender sender, LogFilter filter) {
        Set<AuditCategory> allowed = allowed(sender);
        if (filter.effectiveCategories(allowed).isEmpty()) {
            msg.send(sender, "errors.no-access");
            sound(sender, "error");
            return false;
        }
        lastSearch.put(key(sender), filter.copy());
        core.audit().log(AuditCategory.STAFF, "logs_view", Actor.of(sender), filter.auditTarget(), filter.playerName, null,
                null, filter.describe(allowed));
        return true;
    }

    /** Fetches one page (async, off the main thread). */
    CompletableFuture<Page> fetch(CommandSender viewer, LogFilter filter, int page, int size) {
        AuditQuery query = filter.query(allowed(viewer), System.currentTimeMillis());
        if (query == null) {
            return CompletableFuture.completedFuture(new Page(List.of(), 0));
        }
        CompletableFuture<List<AuditEntry>> rows = core.audit().query(query.copyPage(page, size));
        CompletableFuture<Long> total = core.audit().count(query);
        return rows.thenCombine(total, Page::new);
    }

    /** Chat output of one page (console, Bedrock option, "Show in chat" button, /logs page). */
    void chatPage(CommandSender sender, LogFilter filter, int page) {
        int size = config.chatPageSize;
        msg.send(sender, "chat.loading");
        fetch(sender, filter, page, size).whenCompleteAsync((result, error) -> {
            if (error != null) {
                core.plugin().getLogger().warning("Log search failed: " + error.getMessage());
                msg.send(sender, "errors.search-failed");
                return;
            }
            lastSearch.put(key(sender), filter.copy());
            int pages = (int) Math.max(1, (result.total() + size - 1) / size);
            render.chat(sender, filter, result, Math.min(page, pages - 1), pages);
        }, main);
    }

    // ------------------------------------------------------------------------------------------------ actions

    void teleport(Player player, Location target) {
        if (!canTeleport(player)) {
            msg.send(player, "errors.no-teleport");
            return;
        }
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
            if (ok) {
                msg.send(player, "teleported", Msg.p("world", target.getWorld().getName()), Msg.p("x", target.getBlockX()),
                        Msg.p("y", target.getBlockY()), Msg.p("z", target.getBlockZ()));
                core.sounds().play(player, "teleport");
                core.audit().log(AuditCategory.STAFF, "logs_teleport", Actor.player(player), null, null, null, target, Map.of());
            }
        });
    }

    /** CSV export of everything matching {@code filter} (owner/admin), into {@code plugins/PhoenixSkyblock/<export.folder>/}. */
    void export(CommandSender sender, LogFilter filter) {
        if (!canExport(sender)) {
            msg.send(sender, "errors.no-export");
            return;
        }
        Set<AuditCategory> allowed = allowed(sender);
        AuditQuery query = filter.query(allowed, System.currentTimeMillis());
        if (query == null) {
            msg.send(sender, "errors.no-access");
            return;
        }
        boolean ips = canSeeIps(sender);
        String who = filter.playerName != null ? filter.playerName : filter.subject.name().toLowerCase(Locale.ROOT);
        File folder = new File(core.plugin().getDataFolder(), config.exportFolder);
        File file = new File(folder, "logs-" + who.replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + FILE_TIME.format(LocalDateTime.now()) + ".csv");
        int max = config.exportMaxRows;
        msg.send(sender, "export.started");
        CompletableFuture.supplyAsync(() -> writeCsv(query, file, max, ips)).whenCompleteAsync((rows, error) -> {
            if (error != null) {
                core.plugin().getLogger().warning("Log export failed: " + error.getMessage());
                msg.send(sender, "export.failed", Msg.p("reason", String.valueOf(error.getMessage())));
                sound(sender, "error");
                return;
            }
            msg.send(sender, "export.done", Msg.number("rows", rows), Msg.p("file", folder.getName() + "/" + file.getName()),
                    Msg.p("max", max));
            sound(sender, "success");
            Map<String, Object> details = new java.util.LinkedHashMap<>(filter.describe(allowed));
            details.put("file", file.getAbsolutePath());
            core.audit().log(AuditCategory.STAFF, "logs_export", Actor.of(sender), filter.auditTarget(), filter.playerName,
                    (long) rows, null, details);
        }, main);
    }

    /** Writes the CSV (blocking; runs off the main thread). */
    private int writeCsv(AuditQuery query, File file, int max, boolean ips) {
        file.getParentFile().mkdirs();
        int pageSize = core.audit().maxQueryRows();
        int rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write("id,time_utc,server,season,category,action,actor,actor_uuid,target,target_name,amount,world,x,y,z,details");
            w.newLine();
            for (int page = 0; rows < max; page++) {
                List<AuditEntry> chunk = core.audit().query(query.copyPage(page, pageSize)).join();
                for (AuditEntry e : chunk) {
                    if (rows >= max) {
                        break;
                    }
                    w.write(String.join(",", LogText.csv(e.id()), LogText.csv(LogText.utc(e.time())), LogText.csv(e.server()),
                            LogText.csv(e.season()), LogText.csv(e.category().key()), LogText.csv(e.action()),
                            LogText.csv(e.actor().name()), LogText.csv(e.actor().uuid()), LogText.csv(e.target()),
                            LogText.csv(e.targetName()), LogText.csv(e.amount()), LogText.csv(e.world()), LogText.csv(e.x()),
                            LogText.csv(e.y()), LogText.csv(e.z()), LogText.csv(LogText.json(LogText.redact(e.details(), ips)))));
                    w.newLine();
                    rows++;
                }
                if (chunk.size() < pageSize) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return rows;
    }

    void sound(CommandSender sender, String key) {
        if (sender instanceof Player p) {
            core.sounds().play(p, key);
        }
    }
}
