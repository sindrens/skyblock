package net.skybornmc.skyblock.pack;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.pack.PackIds;
import net.skybornmc.skyblock.common.pack.PackService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Phoenix resource pack (RP1-RP3). Hosts the bundled zip itself ({@link PackHost}) and sends it in the
 * <b>configuration phase</b> ({@link AsyncPlayerConnectionConfigureEvent}), before the world loads, as a required pack
 * with a prompt. The async event thread waits for the client's final answer; a decline or a failure ends the connection
 * with a friendly message. Floodgate (Bedrock) players are skipped. Publishes {@link PackService}.
 */
public final class PackModule implements SkyblockModule, Listener, PackService {

    static final String ID = "pack";
    static final String PERM_RELOAD = "skyblock.pack.reload";
    static final String PERM_STATUS = "skyblock.pack.status";

    /** The settings that matter for sending, captured per reload so a running join never sees half a reload. */
    private record Active(PackConfig.Variant variant, ResourcePackInfo info, Component prompt, boolean required,
                          boolean kickOnFail, int timeoutSeconds) {
    }

    private final Core core;
    private final PackConfig config = new PackConfig();
    private final Map<UUID, Status> statuses = new ConcurrentHashMap<>();
    private ModuleMessages msg;
    private PackIds ids;
    private PackHost host;
    private volatile Active active;
    private volatile boolean enabled;

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
        ids = PackIds.load(core.plugin().getClass().getClassLoader());
        apply();
        core.services().register(ID, PackService.class, this);
        core.sounds().setPackViewers(this::hasPack);
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        registerCommands();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        apply();
        resendToOnline();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        core.sounds().setPackViewers(null);
        stopHost();
        statuses.clear();
        active = null;
        enabled = false;
    }

    // ------------------------------------------------------------------ PackService

    @Override
    public Status status(UUID player) {
        if (!enabled) {
            return Status.DISABLED;
        }
        Status s = statuses.get(player);
        if (s != null) {
            return s;
        }
        return core.bedrock().isBedrock(player) ? Status.BEDROCK : Status.PENDING;
    }

    @Override
    public PackIds ids() {
        return ids;
    }

    // ------------------------------------------------------------------ hosting and settings

    /** Reads the config: extracts and hosts the chosen zip and builds the request info. Failure turns the pack off. */
    private synchronized void apply() {
        if (!config.enabled) {
            enabled = false;
            active = null;
            stopHost();
            statuses.clear();
            core.plugin().getLogger().info("Pack: disabled in pack.yml.");
            return;
        }
        String variant = config.variant.name().toLowerCase(Locale.ROOT);
        PackIds.Zip zip = ids.zips().get(variant);
        if (zip == null) {
            fail("pack-ids.yml has no '" + variant + "' zip.");
            return;
        }
        try {
            Path target = core.plugin().getDataFolder().toPath().resolve("pack").resolve(zip.file());
            byte[] data;
            try (InputStream in = core.plugin().getClass().getClassLoader().getResourceAsStream("pack/" + zip.file())) {
                if (in == null) {
                    throw new IOException("pack/" + zip.file() + " is missing from the jar");
                }
                data = PackFiles.extract(in, target, zip.sha1());
            }
            boolean selfHost = config.url == null || config.url.isBlank();
            URI uri;
            try {
                uri = PackUrls.url(config.url, config.hostPublicHost, config.hostPort, zip.file());
            } catch (IllegalArgumentException e) {
                throw new IOException("pack.yml: " + e.getMessage());
            }
            if (selfHost) {
                if (host != null && !host.matches(config.hostBind, config.hostPort)) {
                    stopHost(); // bind or port changed
                }
                if (host == null) {
                    host = new PackHost(config.hostBind, config.hostPort);
                }
                host.serve(new PackHost.Served(zip.file(), data));
                if (!host.running()) {
                    host.start();
                }
            } else {
                stopHost();
            }
            Component prompt = MiniMessage.miniMessage().deserialize(config.prompt == null ? "" : config.prompt);
            active = new Active(config.variant, ResourcePackInfo.resourcePackInfo(PackUrls.packId(variant), uri, zip.sha1()),
                    prompt, config.required, config.kickOnFail, config.timeoutSeconds);
            enabled = true;
            core.plugin().getLogger().info("Pack: sending " + variant + " pack " + zip.file() + " from " + uri
                    + (selfHost ? " (hosted here on port " + config.hostPort + ")" : " (external URL)"));
        } catch (IOException | RuntimeException e) {
            fail(e.getMessage());
        }
    }

    private void fail(String why) {
        enabled = false;
        active = null;
        stopHost();
        core.plugin().getLogger().severe("Pack: not sent, players get the plain-text fallback: " + why);
    }

    private void stopHost() {
        if (host != null) {
            host.stop();
            host = null;
        }
    }

    // ------------------------------------------------------------------ configuration phase

    /**
     * Sends the pack while the client is still configuring (Paper 26.3
     * {@code AsyncPlayerConnectionConfigureEvent}, its {@code PlayerConfigurationConnection#getAudience()} accepts
     * {@code sendResourcePacks}). This is an async thread, so blocking until the client answers is fine and holds the
     * connection in the configuration phase: the player never enters the world without the answer.
     */
    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        Active a = active;
        PlayerConfigurationConnection conn = event.getConnection();
        if (!enabled || a == null || conn.getProfile().getId() == null) {
            return;
        }
        UUID player = conn.getProfile().getId();
        String name = conn.getProfile().getName();
        if (isBedrock(player, name)) {
            statuses.put(player, Status.BEDROCK);
            return;
        }
        statuses.put(player, Status.PENDING);
        CompletableFuture<Status> done = new CompletableFuture<>();
        Audience audience = conn.getAudience();
        audience.sendResourcePacks(request(a, (uuid, st, aud) ->
                PackStatuses.terminal(st).ifPresent(done::complete)));

        Status result = Status.FAILED;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(a.timeoutSeconds());
        try {
            while (true) {
                long left = deadline - System.nanoTime();
                if (left <= 0 || !conn.isConnected()) {
                    break;
                }
                try {
                    result = done.get(Math.min(left, TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    // keep waiting until the deadline or the client leaves
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            result = Status.FAILED;
        }
        if (!done.isDone()) {
            core.plugin().getLogger().info("Pack: " + name + " gave no answer in " + a.timeoutSeconds() + "s (or left); counted as failed.");
        }
        final Status finalResult = result;
        statuses.put(player, finalResult);
        PackStatuses.kickKey(finalResult, a.required(), a.kickOnFail()).ifPresent(key -> {
            core.plugin().getLogger().info("Pack: " + name + " " + finalResult + ", disconnecting.");
            conn.disconnect(msg.component(audience, key));
        });
        if (!conn.isConnected()) {
            statuses.remove(player); // never reaches the game: no quit event will clean up
        }
    }

    private boolean isBedrock(UUID id, String name) {
        if (core.bedrock().isBedrock(id)) {
            return true;
        }
        String prefix = core.bedrock().usernamePrefix();
        return name != null && prefix != null && !prefix.isEmpty() && name.startsWith(prefix);
    }

    private ResourcePackRequest request(Active a, net.kyori.adventure.resource.ResourcePackCallback callback) {
        return ResourcePackRequest.resourcePackRequest()
                .packs(a.info())
                .required(a.required())
                .prompt(a.prompt())
                .replace(true)
                .callback(callback)
                .build();
    }

    // ------------------------------------------------------------------ play phase (reload, late answers)

    /** Records the answer of a player who is already in the game (after {@code /pack reload}). */
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Active a = active;
        if (a == null || !a.info().id().equals(event.getID())) {
            return;
        }
        PackStatuses.terminalByName(event.getStatus().name())
                .filter(s -> event.getStatus() != PlayerResourcePackStatusEvent.Status.DISCARDED)
                .ifPresent(s -> statuses.put(event.getPlayer().getUniqueId(), s));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        statuses.remove(event.getPlayer().getUniqueId());
    }

    /** Sends the current pack to every online Java player (after a reload). Not required again: they are already in. */
    private void resendToOnline() {
        Active a = active;
        if (!enabled || a == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isBedrock(p.getUniqueId(), p.getName())) {
                statuses.put(p.getUniqueId(), Status.BEDROCK);
                continue;
            }
            UUID id = p.getUniqueId();
            statuses.put(id, Status.PENDING);
            p.sendResourcePacks(request(a, (uuid, st, aud) ->
                    PackStatuses.terminalWhilePlaying(st).ifPresent(s -> statuses.put(id, s))));
        }
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        Arg<Player> target = Args.onlinePlayer("player");
        core.commands().register(CommandSpec.command("pack")
                .description("The Phoenix resource pack (staff)")
                .category("Staff")
                .example("pack status Steve")
                .sub(CommandSpec.sub("reload")
                        .description("Reload pack.yml and resend the pack to everyone online")
                        .permission(PERM_RELOAD)
                        .example("pack reload")
                        .executes(ctx -> {
                            reload();
                            msg.send(ctx.sender(), "command.reloaded", Msg.p("variant", config.variant.name().toLowerCase(Locale.ROOT)),
                                    Msg.p("count", Bukkit.getOnlinePlayers().size()));
                        }))
                .sub(CommandSpec.sub("status")
                        .description("Show a player's resource pack status")
                        .permission(PERM_STATUS)
                        .arg(target)
                        .example("pack status Steve")
                        .executes(ctx -> showStatus(ctx.sender(), ctx.get(target)))));
    }

    private void showStatus(CommandSender sender, Player player) {
        Status s = status(player.getUniqueId());
        msg.send(sender, "command.status", Msg.p("player", player.getName()), Msg.p("status", s.name()),
                Msg.c("info", msg.component(sender, "status." + s.name().toLowerCase(Locale.ROOT))));
    }
}
