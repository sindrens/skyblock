package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code /ppurge <player> preview|confirm} (spec 14.10, owner only by default): removes a player's personal data on
 * request. {@code preview} shows what is removed and kept; {@code confirm} must be typed within {@code confirm-window}
 * after a preview of the same player by the same sender, otherwise the preview is shown again. What happens is
 * {@link PurgeRules} + {@link PunishmentStore#purge}. The purge is audited without personal data (who ran it, the account
 * id, the counts); the audit log itself is never touched.
 */
final class PurgeService {

    static final String PERM = "skyblock.admin.punish.purge";

    private record Pending(UUID target, long at) {
    }

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentService service;
    private final Supplier<PunishConfig.Extras> extras;
    private final Runnable refresh;
    private final MainThread main;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    PurgeService(Core core, ModuleMessages msg, PunishmentService service, Supplier<PunishConfig.Extras> extras, Runnable refresh) {
        this.core = core;
        this.msg = msg;
        this.service = service;
        this.extras = extras;
        this.refresh = refresh;
        this.main = new MainThread(core.plugin());
    }

    private PurgeRules.Settings settings() {
        return extras.get().purge();
    }

    private static String key(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId().toString() : "console:" + sender.getName();
    }

    private CompletableFuture<Optional<UUID>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        try {
            return CompletableFuture.completedFuture(Optional.of(UUID.fromString(name))); // an already purged account by id
        } catch (IllegalArgumentException notAnId) {
            return core.players().offline(name).thenApply(s -> s.map(snap -> snap.uuid()));
        }
    }

    /** {@code /ppurge <player> [preview|confirm]}. Main thread in. */
    void run(CommandSender sender, String name, boolean confirm) {
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            UUID target = found.get();
            long now = System.currentTimeMillis();
            Pending p = pending.get(key(sender));
            if (confirm && p != null && PurgeRules.confirms(p.target(), p.at(), target, now, settings().confirmWindow())) {
                pending.remove(key(sender));
                execute(sender, name, target);
                return;
            }
            if (confirm) {
                msg.send(sender, "purge.preview-first", Msg.p("window", Format.duration(settings().confirmWindow())));
            }
            preview(sender, name, target, now);
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void preview(CommandSender sender, String name, UUID target, long now) {
        service.store().purgePreview(target, settings(), now).thenAcceptAsync(plan -> {
            pending.put(key(sender), new Pending(target, now));
            show(sender, name, plan, "purge.preview");
            msg.send(sender, "purge.confirm", PunishmentService.pre("target", name),
                    Msg.p("window", Format.duration(settings().confirmWindow())));
            core.audit().log(AuditCategory.PUNISHMENT, "purge_preview", Actor.of(sender), target.toString(),
                    PurgeRules.anonymousName(target), null, null, Map.of());
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void show(CommandSender sender, String name, PunishmentStore.PurgePlan plan, String prefix) {
        String keep = settings().keepLedger().isZero() ? service.text(null, "purge.keep-none")
                : Format.duration(settings().keepLedger());
        msg.send(sender, prefix + ".header", Msg.p("player", name), Msg.p("id", plan.target().toString()));
        msg.send(sender, prefix + ".removed", Msg.number("evidence", plan.evidence()), Msg.number("notes", plan.notes()),
                Msg.number("appeals", plan.appeals()), Msg.number("watch", plan.watch()),
                Msg.number("suggestions", plan.suggestions()), Msg.number("requests", plan.requests()));
        msg.send(sender, plan.ipTable() ? prefix + ".ips" : "purge.no-ip-table", Msg.number("ips", plan.ips()));
        msg.send(sender, "purge.discord");
        msg.send(sender, prefix + ".kept", Msg.number("active", plan.ledger().keepActive()),
                Msg.number("ledger", plan.ledger().keepLedger()), Msg.number("deleted", plan.ledger().delete()),
                Msg.p("keep", keep), Msg.number("sanctions", plan.sanctions()), Msg.number("dry", plan.dryRuns()));
        if (plan.ledger().activeBan()) {
            msg.send(sender, "purge.active-ban", Msg.p("player", name));
        }
        msg.send(sender, "purge.audit-kept");
    }

    private void execute(CommandSender sender, String name, UUID target) {
        long now = System.currentTimeMillis();
        service.store().purge(target, settings(), now).thenAcceptAsync(plan -> {
            show(sender, name, plan, "purge.done");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("account", target.toString());
            details.put("evidence", plan.evidence());
            details.put("notes", plan.notes());
            details.put("appeal_texts", plan.appeals());
            details.put("watchlist", plan.watch());
            details.put("suggestions", plan.suggestions());
            details.put("requests", plan.requests());
            details.put("ip_rows", plan.ips());
            details.put("kept_in_force", plan.ledger().keepActive());
            details.put("kept_ledger", plan.ledger().keepLedger());
            details.put("deleted_rows", plan.ledger().delete());
            // no name, no IP, no texts: the audit entry itself must not hold the personal data that was removed
            core.audit().log(AuditCategory.PUNISHMENT, "purge", Actor.of(sender), target.toString(),
                    PurgeRules.anonymousName(target), null, null, details);
            refresh.run();
            if (sender instanceof Player p) {
                core.sounds().play(p, "success");
            }
        }, main).exceptionally(e -> failed(sender, e));
    }

    private Void failed(CommandSender sender, Throwable e) {
        core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Moderation: purge failed", e);
        core.scheduler().ensureMain("moderation:purge-error", () -> msg.send(sender, "error.failed"));
        return null;
    }
}
