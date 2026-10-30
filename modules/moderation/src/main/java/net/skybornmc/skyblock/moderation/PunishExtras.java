package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.audit.AuditQuery;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.discord.DiscordSink;
import net.skybornmc.skyblock.common.moderation.ReportLink;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * P3 side effects of a punishment (spec 12.4 evidence, 12.6 acknowledgement, 12.9 report link, 12.11 Discord log),
 * called by {@link PunishmentService} after a punishment was applied, pardoned or undone. Each part is independent and
 * can never stop or delay the punishment itself: failures are logged, not thrown.
 */
final class PunishExtras {

    static final String DISCORD_PREFIX = "discord.";

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentStore store;
    private final Supplier<PunishConfig.Extras> extras;
    private final Supplier<WarnRules.Settings> warnRules;
    private final Supplier<Optional<DiscordSink>> discord;
    private final Executor main;
    private final AckTracker acks = new AckTracker();

    PunishExtras(Core core, ModuleMessages msg, PunishmentStore store, Supplier<PunishConfig.Extras> extras,
                 Supplier<WarnRules.Settings> warnRules, Supplier<Optional<DiscordSink>> discord) {
        this.core = core;
        this.msg = msg;
        this.store = store;
        this.extras = extras;
        this.warnRules = warnRules;
        this.discord = discord;
        this.main = new MainThread(core.plugin());
    }

    AckTracker acks() {
        return acks;
    }

    private String text(Player viewer, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.component(viewer == null ? Bukkit.getConsoleSender() : viewer, key, tags));
    }

    // ================================================================== hooks

    /** A punishment was applied (main thread): evidence for mutes/warns, Discord log. */
    void applied(Punishment p) {
        applied(p, null, true);
    }

    /**
     * Same, with the {@code /logs} chat line chosen as evidence ({@code null} = the player's last lines; spec 13.11) and
     * whether to post this punishment's own Discord embed ({@code false} inside a bulk action, spec 13.8).
     */
    void applied(Punishment p, Long logEntry, boolean discord) {
        try {
            if (logEntry != null) {
                captureFromLog(p, logEntry);
            } else {
                captureEvidence(p);
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Evidence capture failed for #" + p.id(), e);
        }
        if (discord) {
            postDiscord(() -> PunishEmbeds.applied(p, extras.get().discord(), this::discordText));
        }
    }

    /** A bulk action finished (spec 13.8): one Discord embed with the count. */
    void bulkApplied(String group, String reason, String staff, List<Punishment> applied, int requested, int skipped) {
        if (applied.isEmpty() && requested == 0) {
            return;
        }
        postDiscord(() -> PunishEmbeds.bulk(group, reason, staff, applied, requested, skipped, System.currentTimeMillis(),
                extras.get().discord(), this::discordText));
    }

    /** [Undo all] of a bulk action (spec 13.8): one Discord embed. */
    void bulkUndone(String group, String staff, List<Punishment> undone) {
        if (undone.isEmpty()) {
            return;
        }
        postDiscord(() -> PunishEmbeds.bulkUndone(group, staff, undone, System.currentTimeMillis(), extras.get().discord(),
                this::discordText));
    }

    /** A raid lockdown started or ended (spec 13.9): one embed to the punishment log (off unless Discord is enabled). */
    void lockdownPosted(Lockdown.Active a, String event, String by) {
        postDiscord(() -> PunishEmbeds.lockdown(a, event, by, System.currentTimeMillis(), extras.get().discord(),
                this::discordText));
    }

    /** A new appeal or a decision (spec 13.10 step 1): to the appeals webhook, only when switched on. */
    void appealPosted(Supplier<DiscordSink.Embed> embed) {
        try {
            PunishConfig.Appeals cfg = extras.get().appeal();
            Optional<DiscordSink> sink = discord.get();
            if (cfg.discordEnabled() && sink.isPresent() && sink.get().enabled(cfg.discordChannel())) {
                sink.get().post(cfg.discordChannel(), embed.get());
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Discord appeal log failed", e);
        }
    }

    /** Punishments were pardoned with a reason (main thread). */
    void pardoned(List<Punishment> list, String targetName, String by, String reason) {
        if (list.isEmpty()) {
            return;
        }
        postDiscord(() -> PunishEmbeds.pardoned(list, targetName, by, reason, System.currentTimeMillis(),
                extras.get().discord(), this::discordText));
    }

    /** A punishment was undone with [Undo] (main thread). */
    void undone(Punishment p, String by, String reason) {
        postDiscord(() -> PunishEmbeds.undone(p, by, reason, System.currentTimeMillis(), extras.get().discord(), this::discordText));
    }

    // ================================================================== 12.11 Discord

    private void postDiscord(Supplier<DiscordSink.Embed> embed) {
        try {
            String channel = extras.get().discord().channel();
            Optional<DiscordSink> sink = discord.get();
            if (sink.isPresent() && sink.get().enabled(channel)) {
                sink.get().post(channel, embed.get());
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Discord log failed", e);
        }
    }

    /** Labels for {@link PunishEmbeds} from {@code messages/moderation.yml → discord.*}. */
    String discordText(String key) {
        if (key.startsWith(PunishEmbeds.RULE_COUNT)) {
            return text(null, DISCORD_PREFIX + "rule-count", Msg.p("count", key.substring(PunishEmbeds.RULE_COUNT.length())));
        }
        return text(null, DISCORD_PREFIX + key);
    }

    // ================================================================== 12.4 evidence

    /**
     * Copies the target's last chat lines (and some lines of others for context) from the audit log into
     * {@code punishment_evidence}. Async; never blocks the punishment. Mutes and warns only.
     */
    void captureEvidence(Punishment p) {
        PunishConfig.Evidence cfg = extras.get().evidence();
        if (!cfg.enabled() || p.target() == null || (p.type() != Punishment.Type.MUTE && p.type() != Punishment.Type.WARN)) {
            return;
        }
        capture(p, p.created() + 1000, null);
    }

    /**
     * spec 13.11: the evidence is the chosen {@code /logs} chat line and the lines before it (the player's last
     * {@code chat-lines} up to that line, {@code context-lines} of others), for every type: staff picked it on purpose.
     * The entry was checked when the menu opened; it is read again here (any category: the check already ran).
     */
    void captureFromLog(Punishment p, long entryId) {
        PunishConfig.Evidence cfg = extras.get().evidence();
        Optional<net.skybornmc.skyblock.common.audit.LogLookup> lookup =
                core.services().optional(net.skybornmc.skyblock.common.audit.LogLookup.class);
        if (!cfg.enabled() || p.target() == null || lookup.isEmpty()) {
            return;
        }
        lookup.get().entry(null, entryId).thenAccept(entry -> {
            if (entry.isPresent() && LogEvidence.check(entry.get(), p.target()).isEmpty()) {
                capture(p, entry.get().time() + 1, entryId);
            } else {
                core.plugin().getLogger().warning("Log entry #" + entryId + " is gone; no evidence saved for #" + p.id());
            }
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Could not read log entry #" + entryId + " for #" + p.id(), e);
            return null;
        });
    }

    /** Copies the chat lines before {@code until} (exclusive) into {@code punishment_evidence}. Async. */
    private void capture(Punishment p, long until, Long logEntry) {
        PunishConfig.Evidence cfg = extras.get().evidence();
        String ipHidden = text(null, "evidence.ip-hidden");
        String blocked = text(null, "evidence.blocked");
        java.time.ZoneId zone = Format.zone();
        core.scheduler().async(() -> {
            try {
                core.audit().flush(); // the newest chat lines may still be queued
            } catch (RuntimeException e) {
                // keep going with what is written
            }
            AuditQuery mine = AuditQuery.create().actor(p.target()).categories(AuditCategory.CHAT)
                    .actions("chat", "chat_blocked").until(Instant.ofEpochMilli(until)).page(0, cfg.chatLines());
            AuditQuery around = AuditQuery.create().categories(AuditCategory.CHAT).actions("chat")
                    .until(Instant.ofEpochMilli(until)).page(0, cfg.chatLines() + cfg.contextLines() * 3);
            core.audit().query(mine).thenCombine(core.audit().query(around), (a, b) -> {
                List<Evidence.Line> lines = new ArrayList<>();
                java.util.Set<Long> seen = new java.util.HashSet<>();
                for (List<AuditEntry> list : List.of(a, b)) {
                    for (AuditEntry e : list) {
                        if (seen.add(e.id())) {
                            Object message = e.details().get("message");
                            lines.add(new Evidence.Line(e.time(), e.actor().uuid(), e.actor().name(),
                                    message == null ? null : String.valueOf(message), "chat_blocked".equals(e.action())));
                        }
                    }
                }
                return Evidence.build(lines, p.target(), until, cfg, zone, ipHidden, blocked);
            }).thenCompose(lines -> lines.isEmpty() ? CompletableFuture.completedFuture(0)
                    : store.saveEvidence(p.id(), p.created(), lines).thenApply(v -> lines.size()))
                    .thenAccept(count -> {
                        if (count > 0) {
                            core.audit().log(AuditCategory.PUNISHMENT, "evidence_saved", Actor.system("evidence"),
                                    p.target().toString(), p.targetName(), null, null, logEntry == null
                                            ? Map.of("id", p.id(), "lines", count)
                                            : Map.of("id", p.id(), "lines", count, "log_entry", logEntry));
                        }
                    })
                    .exceptionally(e -> {
                        core.plugin().getLogger().log(Level.WARNING, "Could not save evidence for #" + p.id(), e);
                        return null;
                    });
        });
    }

    /** Deletes evidence older than {@code evidence.retention} (spec 12.4 privacy). Async. */
    void purgeEvidence() {
        Duration retention = extras.get().evidence().retention();
        long before = System.currentTimeMillis() - retention.toMillis();
        store.purgeEvidence(before).thenAccept(n -> {
            if (n > 0) {
                core.audit().log(AuditCategory.PUNISHMENT, "evidence_purged", Actor.system("evidence"),
                        Map.of("rows", n, "retention", PunishConfig.text(retention)));
                core.plugin().getLogger().info("Deleted " + n + " evidence snapshot(s) older than " + PunishConfig.text(retention) + ".");
            }
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Evidence purge failed", e);
            return null;
        });
    }

    // ================================================================== 12.6 acknowledgement

    /** The clickable [I understand] (works in chat for Java; Bedrock also gets the menu and /ack). */
    Component ackButton(Player player) {
        PunishConfig.Ack cfg = extras.get().ack();
        Duration life = cfg.repeat().multipliedBy(Math.max(1, cfg.maxRepeats() + 1)).plus(Duration.ofMinutes(30));
        return msg.component(player, "ack.button")
                .hoverEvent(HoverEvent.showText(msg.component(player, "ack.hover")))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker && clicker.getUniqueId().equals(player.getUniqueId())) {
                        acknowledge(clicker);
                    }
                }, options -> options.uses(ClickCallback.UNLIMITED_USES).lifetime(life)));
    }

    /** Sends the acknowledgement line ({@code key}) with [I understand] and the rules link; Bedrock gets the menu too. */
    void prompt(Player player, String key, boolean menu) {
        String link = extras.get().rulesLink();
        msg.send(player, key, Msg.c("ack", ackButton(player)), PunishmentService.pre("rules", link));
        if (menu && extras.get().ack().bedrockMenu() && core.bedrock().isBedrock(player.getUniqueId())) {
            core.menus().open(player, new AckMenu(this));
        }
    }

    /** A warning reached an online player (main thread): ask for the acknowledgement and start the reminders. */
    void warned(Player player, Punishment p) {
        acks.warned(player.getUniqueId(), System.currentTimeMillis(), extras.get().ack().repeat());
        prompt(player, "ack.prompt", true);
    }

    /** Every second: reminders that are due (main thread). */
    void tick() {
        PunishConfig.Ack cfg = extras.get().ack();
        for (UUID id : acks.due(System.currentTimeMillis(), cfg.repeat(), cfg.maxRepeats())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                prompt(player, "ack.reminder", false);
            }
        }
    }

    /** On join: owe an acknowledgement for unacknowledged active warnings, and remind once (spec 12.6). */
    void joined(Player player) {
        WarnRules.Settings rules = warnRules.get();
        long since = rules.warnExpire() == null ? Long.MIN_VALUE : System.currentTimeMillis() - rules.warnExpire().toMillis();
        UUID id = player.getUniqueId();
        store.joinAck(id, since).thenAcceptAsync(r -> {
            if (!player.isOnline() || r.owing().isEmpty()) {
                return;
            }
            acks.owe(id);
            if (!r.remindNow().isEmpty()) {
                prompt(player, "ack.join", true);
            }
        }, main).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Acknowledgement check failed for " + player.getName(), e);
            return null;
        });
    }

    /** [I understand] / /ack / the Bedrock button: acknowledges every open warning (main thread in and out). */
    CompletableFuture<List<Long>> acknowledge(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        return store.acknowledge(id, now).thenApplyAsync(ids -> {
            acks.acknowledged(id);
            if (ids.isEmpty()) {
                msg.send(player, "ack.nothing");
                return ids;
            }
            msg.send(player, "ack.done", Msg.number("count", ids.size()));
            core.sounds().play(player, "success");
            core.audit().logPlayer(AuditCategory.PUNISHMENT, "warn_ack", Actor.player(player), id, player.getName(), null,
                    player.getLocation(), Map.of("ids", ids));
            return ids;
        }, main).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Acknowledgement failed for " + player.getName(), e);
            core.scheduler().ensureMain("moderation:ack", () -> msg.send(player, "error.failed"));
            return List.of();
        });
    }

    /** {@code block-until-ack} (off by default): true when this player may not chat/use commands yet. */
    boolean blocks(Player player) {
        return extras.get().ack().blockUntilAck() && acks.pending(player.getUniqueId());
    }

    void forget(UUID player) {
        acks.forget(player);
    }

    // ================================================================== 12.9 report link

    /**
     * After {@code sender} applied {@code p}: close their claimed reports about the target ({@code close-reports}) and
     * mention open unclaimed ones with a [Open /reports player] button. Needs the staff module ({@link ReportLink}).
     */
    void linkReports(CommandSender sender, Punishment p) {
        if (!(sender instanceof Player staff) || p.target() == null) {
            return;
        }
        Optional<ReportLink> link = core.services().optional(ReportLink.class);
        if (link.isEmpty()) {
            return;
        }
        CompletableFuture<ReportLink.Result> f = extras.get().closeReports()
                ? link.get().punished(p.target(), p.targetName(), staff.getUniqueId(), staff.getName(), p.id())
                : link.get().count(p.target(), staff.getUniqueId());
        f.thenAccept(r -> core.scheduler().ensureMain("moderation:reports", () -> {
            if (!staff.isOnline()) {
                return;
            }
            if (!r.closed().isEmpty()) {
                msg.send(staff, "reports.closed", Msg.number("count", r.closed().size()), Msg.p("player", p.targetName()),
                        Msg.p("ids", String.join(", ", r.closed().stream().map(id -> "#" + id).toList())));
            }
            if (r.openUnclaimed() > 0) {
                msg.send(staff, "reports.open", Msg.number("count", r.openUnclaimed()), Msg.p("player", p.targetName()),
                        PunishmentService.pre("target", p.targetName()));
            }
        })).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Report link failed for #" + p.id(), e);
            return null;
        });
    }

    // ================================================================== audit helper

    static Map<String, Object> details(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return out;
    }
}
