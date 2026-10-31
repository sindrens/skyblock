package net.skybornmc.skyblock.pack;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.pack.PackService;
import net.skybornmc.skyblock.common.pack.PackStatusEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The icon pack (I7 v3, pack v2 phase 1; build spec GUI-P1-PACK). Hosts the bundled {@code pack/phoenix-icons.zip}
 * with a tiny web server ({@link PackHost}) and offers it to every Java player <b>after</b> they are in the world, as a
 * non-vanilla-required pack; a decline or failure on that first offer ends the connection with our own friendly text
 * (D5). Bedrock players are never sent anything. The host is self-checked every {@code host.check-seconds}: while it
 * is down the pack is not sent and nobody is kicked (fail-open, one staff alert per outage). Statuses are memory only.
 * Publishes {@link PackService}; menus ask it through {@code core.menus().icons()}.
 */
public final class PackModule implements SkyblockModule, Listener, PackService {

    static final String ID = "pack";
    static final String PERM_STATUS = "skyblock.pack.status";
    static final String PERM_RELOAD = "skyblock.pack.reload";
    static final String PERM_ALERTS = "skyblock.pack.alerts";
    /** The bundled zip (built by the packZip Gradle task from resourcepack/pack). */
    static final String RESOURCE = "pack/phoenix-icons.zip";
    /** One stable pack id: a new hash under the same id replaces the client's old copy. */
    static final UUID PACK_ID = PackUrls.packId("icons");

    /** What one reload produced, swapped atomically so a running join never sees half a reload. */
    private record Active(ResourcePackInfo info, URI uri, URI checkUri, String sha1, Component prompt) {
    }

    private final Core core;
    private final PackConfig config = new PackConfig();
    private final Map<UUID, Status> statuses = new ConcurrentHashMap<>();
    /** Players whose current offer is enforced (the first offer at join); late offers never kick. */
    private final Set<UUID> enforced = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Scheduler.Task> timeouts = new ConcurrentHashMap<>();
    private final PackWaiters waiters = new PackWaiters();
    private final HostHealth health = new HostHealth(2);
    private final HostCheck check = new HostCheck();
    private ModuleMessages msg;
    private PackHost host;
    private byte[] data;
    private volatile Active active;
    /** Bumped on every apply(): a self-check started before a reload is ignored when it ends after it. */
    private volatile int generation;
    private Scheduler.Task checkTask;
    private Scheduler.Task waitTicker;
    /** How many players the last reload offered the pack to (for the /pack reload reply). */
    private int lastResent;

    public PackModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        msg = core.messages(ID);
        data = readBundled();
        apply();
        core.services().register(ID, PackService.class, this);
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        registerCommands();
        waitTicker = core.scheduler().repeat("pack-wait", 20L, 20L, this::tickWaiters);
        // Players already online (a module reload): they are not prompted now; a later reload or recovery offers it.
        for (Player p : Bukkit.getOnlinePlayers()) {
            joinedAt.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
            setStatus(p, initialStatus(p.getUniqueId()));
        }
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        apply();
        if (!config.enabled) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                cancelTimeout(p.getUniqueId());
                enforced.remove(p.getUniqueId());
                setStatus(p, core.bedrock().isBedrock(p.getUniqueId()) ? Status.BEDROCK : Status.DISABLED);
            }
        }
        lastResent = resendAfterReload();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (waitTicker != null) {
            waitTicker.cancel();
            waitTicker = null;
        }
        timeouts.values().forEach(Scheduler.Task::cancel);
        timeouts.clear();
        stopHost();
        statuses.clear();
        enforced.clear();
        joinedAt.clear();
        waiters.clear();
        active = null;
        generation++;
    }

    // ------------------------------------------------------------------ PackService

    @Override
    public Status status(UUID player) {
        Status s = statuses.get(player);
        return s != null ? s : initialStatus(player);
    }

    /** The status of a player the module has not handled (yet): no offer happened for them. */
    private Status initialStatus(UUID player) {
        if (core.bedrock().isBedrock(player)) {
            return Status.BEDROCK;
        }
        return config.enabled ? Status.SKIPPED : Status.DISABLED;
    }

    @Override
    public void afterAnswer(Player player, Runnable action) {
        if (!Bukkit.isPrimaryThread()) {
            core.scheduler().main("pack-after-answer", () -> afterAnswer(player, action));
            return;
        }
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (status(id) != Status.PENDING || config.joinWaitSeconds <= 0) {
            runSafely(action);
            return;
        }
        long deadline = joinedAt.getOrDefault(id, System.currentTimeMillis()) + config.joinWaitSeconds * 1000L;
        waiters.add(id, action, deadline);
    }

    @Override
    public boolean healthy() {
        return health.healthy();
    }

    @Override
    public boolean required() {
        return config.required;
    }

    // ------------------------------------------------------------------ hosting and settings

    private byte[] readBundled() throws IOException {
        try (InputStream in = core.plugin().getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException(RESOURCE + " is missing from the jar (the packZip task did not run)");
            }
            return in.readAllBytes();
        }
    }

    /**
     * Applies pack.yml: hosts the zip (or uses {@code url}), builds the offer and (re)starts the self-check. A broken
     * setting turns the offer off (nobody is kicked) and is logged. The health state is kept when the URL and file did
     * not change, so a reload of texts does not make the pack optional for a minute.
     */
    private synchronized void apply() {
        int gen = ++generation;
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        health.failuresToDown(config.hostCheckFailures);
        if (!config.enabled) {
            active = null;
            stopHost();
            health.reset();
            core.plugin().getLogger().info("Pack: disabled in pack.yml; every icon shows its plain base item.");
            return;
        }
        Active before = active;
        try {
            String sha1 = PackFiles.sha1(data);
            String file = PackFiles.fileName(sha1);
            Path copy = core.plugin().getDataFolder().toPath().resolve("pack").resolve(file);
            byte[] bytes = data;
            core.scheduler().async(() -> {
                try {
                    PackFiles.extract(bytes, copy);
                } catch (IOException e) {
                    core.plugin().getLogger().log(Level.WARNING, "Pack: could not write the copy " + copy + " (hosting from memory is unaffected)", e);
                }
            });
            boolean selfHost = config.url == null || config.url.isBlank();
            // "<file>" in url / host.check-url = the current file name (it changes with every new pack hash).
            URI uri = PackUrls.url(withFile(config.url, file), config.hostPublicHost, config.hostPort, file);
            URI checkUri = config.hostCheckUrl == null || config.hostCheckUrl.isBlank()
                    ? uri : PackUrls.url(withFile(config.hostCheckUrl, file), config.hostPublicHost, config.hostPort, file);
            if (selfHost) {
                if (host != null && !host.matches(config.hostBind, config.hostPort)) {
                    stopHost();
                }
                if (host == null) {
                    host = new PackHost(config.hostBind, config.hostPort);
                }
                host.serve(new PackHost.Served(file, data));
                if (!host.running()) {
                    host.start();
                }
            } else {
                stopHost();
            }
            Component prompt = MiniMessage.miniMessage().deserialize(config.prompt == null ? "" : config.prompt);
            Active now = new Active(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1), uri, checkUri, sha1, prompt);
            if (before == null || !before.checkUri().equals(checkUri) || !before.sha1().equals(sha1)) {
                health.reset();
            }
            active = now;
            core.plugin().getLogger().info("Pack: offering " + file + " (" + data.length + " bytes) from " + uri
                    + (selfHost ? " (hosted here on TCP " + config.hostPort + ")" : " (external URL)")
                    + "; self-check " + checkUri + " every " + config.hostCheckSeconds + "s.");
            long period = config.hostCheckSeconds * 20L;
            checkTask = core.scheduler().asyncRepeat(0L, period, () -> runCheck(gen));
        } catch (IOException | RuntimeException e) {
            active = null;
            stopHost();
            health.reset();
            core.plugin().getLogger().severe("Pack: not offered (nobody is kicked, icons show their base items): " + e.getMessage());
        }
    }

    private static String withFile(String url, String file) {
        return url == null ? "" : url.replace("<file>", file).replace("{file}", file);
    }

    private void stopHost() {
        if (host != null) {
            host.stop();
            host = null;
        }
    }

    /** Async: one self-check; transitions hop to the main thread. */
    private void runCheck(int gen) {
        Active a = active;
        if (a == null || gen != generation) {
            return;
        }
        HostCheck.Result result = check.run(a.checkUri(), a.sha1());
        if (gen != generation) {
            return;
        }
        switch (health.record(result.ok())) {
            case WENT_DOWN -> core.scheduler().main("pack-host-down", () -> hostDown(result.reason()));
            case CAME_UP -> {
                boolean recovered = health.recoveredFromOutage();
                core.scheduler().main("pack-host-up", () -> hostUp(recovered));
            }
            case STAYED -> {
            }
        }
    }

    private void hostDown(String reason) {
        core.plugin().getLogger().warning("Pack: the icon pack host can't be reached (" + reason + "); the pack is not "
                + "sent or required until it is back.");
        core.audit().log(AuditCategory.CONFIG, "pack_host_down", Actor.system(ID), Map.of("reason", reason));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(PERM_ALERTS)) {
                msg.send(p, "alert.host-down", Msg.p("reason", reason));
            }
        }
    }

    /**
     * The host passed a check after start, reload or an outage: players who were not sent the pack meanwhile get it now
     * (never enforced). Staff hear about it only after a reported outage.
     */
    private void hostUp(boolean recovered) {
        if (recovered) {
            core.plugin().getLogger().info("Pack: the icon pack host is back.");
            core.audit().log(AuditCategory.CONFIG, "pack_host_up", Actor.system(ID), Map.of());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission(PERM_ALERTS)) {
                    msg.send(p, "alert.host-up");
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Status s = status(p.getUniqueId());
            if (s == Status.SKIPPED || s == Status.FAILED) {
                offer(p, false);
            }
        }
    }

    // ------------------------------------------------------------------ join, answers, kick

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        joinedAt.put(id, System.currentTimeMillis());
        PackJoinPolicy.Decision decision = PackJoinPolicy.decide(core.bedrock().isBedrock(id),
                config.enabled && active != null, health.healthy(), true);
        switch (decision) {
            case SKIP_BEDROCK -> setStatus(player, Status.BEDROCK);
            case SKIP_DISABLED -> setStatus(player, Status.DISABLED);
            case SKIP_HOST_DOWN -> setStatus(player, Status.SKIPPED);
            case SEND -> offer(player, true);
        }
    }

    /** Sends the pack (not vanilla-required: we enforce it ourselves, D5) and arms the answer timeout. */
    private boolean offer(Player player, boolean enforce) {
        Active a = active;
        UUID id = player.getUniqueId();
        if (a == null || !config.enabled || core.bedrock().isBedrock(id)) {
            return false;
        }
        cancelTimeout(id);
        if (enforce) {
            enforced.add(id);
        } else {
            enforced.remove(id);
        }
        setStatus(player, Status.PENDING);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(a.info())
                .required(false)
                .prompt(a.prompt())
                .replace(true)
                .callback((uuid, st, audience) -> PackStatuses.terminalWhilePlaying(st).ifPresent(s ->
                        core.scheduler().ensureMain("pack-answer", () -> answer(player, s))))
                .build());
        timeouts.put(id, core.scheduler().later("pack-timeout", config.timeoutSeconds * 20L, () -> {
            timeouts.remove(id);
            if (player.isOnline() && status(id) == Status.PENDING) {
                core.plugin().getLogger().info("Pack: " + player.getName() + " gave no answer in " + config.timeoutSeconds + "s; counted as failed.");
                answer(player, Status.FAILED);
            }
        }));
        return true;
    }

    /** Covers the same answers as the request callback (whichever arrives first wins; the second is ignored). */
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID()) || event.getStatus() == PlayerResourcePackStatusEvent.Status.DISCARDED) {
            return;
        }
        PackStatuses.terminalByName(event.getStatus().name()).ifPresent(s -> answer(event.getPlayer(), s));
    }

    /** A final answer for the pending offer (main thread). Kicks only an enforced first offer while the host is healthy. */
    private void answer(Player player, Status result) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || status(id) != Status.PENDING) {
            return;
        }
        cancelTimeout(id);
        boolean enforce = enforced.remove(id);
        var kick = PackStatuses.kickKey(result, config.enabled && config.required, enforce, health.healthy());
        if (kick.isPresent()) {
            waiters.drop(id); // the welcome must not start for a player we are about to kick
        }
        setStatus(player, result);
        kick.ifPresent(key -> {
            core.plugin().getLogger().info("Pack: " + player.getName() + " " + result.name().toLowerCase(Locale.ROOT) + " the icon pack; kicked with " + key + ".");
            player.saveData();
            player.kick(msg.component(player, key));
        });
    }

    /** Sets a status; fires {@link PackStatusEvent} on a real change and releases the waiters once not pending. */
    private void setStatus(Player player, Status now) {
        UUID id = player.getUniqueId();
        Status old = statuses.put(id, now);
        if (old != now) {
            try {
                Bukkit.getPluginManager().callEvent(new PackStatusEvent(player, old, now));
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "A PackStatusEvent listener failed", e);
            }
        }
        if (now != Status.PENDING) {
            for (Runnable action : waiters.complete(id)) {
                runSafely(action);
            }
        }
    }

    private void tickWaiters() {
        for (Map.Entry<UUID, List<Runnable>> e : waiters.expired(System.currentTimeMillis()).entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                continue;
            }
            e.getValue().forEach(this::runSafely);
        }
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Pack: a join action waiting for the pack answer failed", e);
        }
    }

    private void cancelTimeout(UUID id) {
        Scheduler.Task t = timeouts.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelTimeout(id);
        waiters.drop(id);
        statuses.remove(id);
        enforced.remove(id);
        joinedAt.remove(id);
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        Arg<Player> target = Args.onlinePlayer("player");
        core.commands().register(CommandSpec.command("pack")
                .description("The icon pack: status and reload (staff)")
                .category("Staff")
                .example("pack status")
                .sub(CommandSpec.sub("status")
                        .description("Pack status of everyone online, or of one player")
                        .permission(PERM_STATUS)
                        .optional(target)
                        .example("pack status")
                        .example("pack status Steve")
                        .executes(ctx -> {
                            if (ctx.has(target)) {
                                showStatus(ctx.sender(), ctx.get(target));
                            } else {
                                showAll(ctx.sender());
                            }
                        }))
                .sub(CommandSpec.sub("reload")
                        .description("Reload pack.yml, re-host the pack and offer it again to everyone online")
                        .permission(PERM_RELOAD)
                        .example("pack reload")
                        .executes(ctx -> {
                            reload();
                            msg.send(ctx.sender(), "command.reloaded", Msg.p("count", lastResent));
                        })));
    }

    /** {@code /pack reload}: every online Java player is offered the pack again (never enforced). */
    private int resendAfterReload() {
        if (!config.enabled || active == null || !health.healthy()) {
            return 0; // a host that is still being checked offers it to the skipped players when it comes up
        }
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (status(p.getUniqueId()) != Status.BEDROCK && offer(p, false)) {
                msg.send(p, "reload.notice");
                count++;
            }
        }
        return count;
    }

    private void showStatus(CommandSender sender, Player player) {
        Status s = status(player.getUniqueId());
        String key = s.name().toLowerCase(Locale.ROOT);
        msg.send(sender, "command.status-one", Msg.p("player", player.getName()), Msg.p("status", key),
                Msg.c("info", msg.component(sender, "status." + key)));
    }

    private void showAll(CommandSender sender) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Player p : Bukkit.getOnlinePlayers()) {
            counts.merge(status(p.getUniqueId()), 1, Integer::sum);
        }
        int loaded = counts.getOrDefault(Status.LOADED, 0);
        int pending = counts.getOrDefault(Status.PENDING, 0);
        int bedrock = counts.getOrDefault(Status.BEDROCK, 0);
        int other = Bukkit.getOnlinePlayers().size() - loaded - pending - bedrock;
        msg.send(sender, "command.status-all", Msg.p("loaded", loaded), Msg.p("pending", pending),
                Msg.p("bedrock", bedrock), Msg.p("other", other),
                Msg.c("health", msg.component(sender, health.healthy() ? "health.up" : "health.down")));
    }
}
