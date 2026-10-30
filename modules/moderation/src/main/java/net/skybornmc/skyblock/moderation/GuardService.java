package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The guard of spec 14.1 on the server: the sliding window of what each staff member applied, the stored blocks, locks
 * and allowances ({@code punish_guard}), the alert to everyone with {@code skyblock.staff.notify.guard} (always on, 14.9)
 * and Discord, and {@code /pguard allow|lock|unlock|status}. The decision itself is {@link Guard#decide} (pure); the
 * pipeline asks it through {@link #check} without changing anything (menu previews run the same pipeline). A block is
 * set when a real attempt is refused ({@link #triggered}), an alert when an applied punishment goes over a limit
 * ({@link #recorded}). Every block, allow, lock and unlock is audited.
 */
final class GuardService implements PunishDecision.GuardView {

    static final String PERM_GUARD = "skyblock.admin.punish.guard";
    /** Never limited (the owner). Locks still apply. */
    static final String PERM_EXEMPT = "skyblock.admin.punish.guard.exempt";

    private final Core core;
    private final ModuleMessages msg;
    private final GuardStore store;
    private final Supplier<Guard.Settings> settings;
    private final Supplier<List<String>> rankTiers;
    private final MainThread main;
    private PunishmentService service;
    private PunishExtras extras;

    private final Map<UUID, Guard.Entry> entries = new ConcurrentHashMap<>();
    /** Last known guard tier per staff member (set whenever an actor is built on the main thread). */
    private final Map<UUID, String> tiers = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    /** When the last alert for a staff member went out (at most one per window). */
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();
    private final Guard.Window window = new Guard.Window();

    GuardService(Core core, ModuleMessages msg, GuardStore store, Supplier<Guard.Settings> settings,
                 Supplier<List<String>> rankTiers) {
        this.core = core;
        this.msg = msg;
        this.store = store;
        this.settings = settings;
        this.rankTiers = rankTiers;
        this.main = new MainThread(core.plugin());
    }

    void wire(PunishmentService service, PunishExtras extras) {
        this.service = service;
        this.extras = extras;
    }

    Guard.Settings settings() {
        Guard.Settings s = settings.get();
        return s == null ? Guard.Settings.DEFAULT : s;
    }

    /** Loads every stored block, lock and allowance. */
    CompletableFuture<Void> start() {
        return store.all().thenAccept(list -> {
            entries.clear();
            list.forEach(e -> {
                entries.put(e.staff(), e);
                names.put(e.staff(), e.staffName());
            });
        }).exceptionally(e -> {
            core.plugin().getLogger().warning("Moderation: could not load the guard state: " + e.getMessage());
            return null;
        });
    }

    // ================================================================== decision

    /** The stored entry, an ended allowance counted as no entry. */
    Guard.Entry entry(UUID staff, long now) {
        Guard.Entry e = staff == null ? null : entries.get(staff);
        if (e != null && e.state() == Guard.State.ALLOWED && !e.allowedAt(now)) {
            return null;
        }
        return e;
    }

    @Override
    public Guard.Decision check(UUID staff, String tier, Guard.Kind kind, long now) {
        Guard.Settings s = settings();
        return Guard.decide(s, tier, entry(staff, now), window.counts(staff, s.window(), now), kind, now);
    }

    /**
     * The guard tier of {@code sender} ({@code null} = never limited: console, {@link #PERM_EXEMPT}). Main thread (reads
     * permissions); remembered for {@link #recorded}.
     */
    String tierOf(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return null;
        }
        if (p.hasPermission(PERM_EXEMPT)) {
            tiers.remove(p.getUniqueId());
            return null;
        }
        List<String> held = new ArrayList<>();
        for (String name : rankTiers.get()) {
            if (p.hasPermission("skyblock.staff.punish.max." + name)) {
                held.add(name);
            }
        }
        String tier = Guard.tierOf(settings(), held);
        if (tier == null) {
            tiers.remove(p.getUniqueId());
        } else {
            tiers.put(p.getUniqueId(), tier);
        }
        names.put(p.getUniqueId(), p.getName());
        return tier;
    }

    /**
     * Something was applied by {@code staff} (any thread: the store calls this right after the insert; sanctions after
     * theirs). Counts it, and alerts once per window when it went over a limit (alert mode, or an allowed period).
     */
    void recorded(UUID staff, String staffName, Guard.Kind kind, long at) {
        if (staff == null) {
            return;
        }
        Guard.Settings s = settings();
        window.record(staff, kind, at);
        if (staffName != null) {
            names.putIfAbsent(staff, staffName);
        }
        String tier = tiers.get(staff);
        if (!s.enabled() || tier == null) {
            return;
        }
        Guard.Hit hit = Guard.exceeded(s.tier(tier), window.counts(staff, s.window(), at), kind);
        if (hit == null || !alertDue(staff, at, s.window())) {
            return;
        }
        String name = names.getOrDefault(staff, staffName == null ? "?" : staffName);
        core.scheduler().ensureMain("moderation:guard-alert", () -> {
            alert(name, hit, "alert");
            core.audit().log(AuditCategory.PUNISHMENT, "guard_alert", Actor.system("guard"), staff.toString(), name, null,
                    null, Map.of("limit", hit.limit().id(), "count", hit.count(), "max", hit.max(), "tier", tier));
        });
    }

    private boolean alertDue(UUID staff, long now, Duration w) {
        Long last = lastAlert.get(staff);
        if (last != null && now - last < w.toMillis()) {
            return false;
        }
        lastAlert.put(staff, now);
        return true;
    }

    /**
     * A real attempt was refused by the guard stage (called from {@link PunishmentService#tell}, main thread). With
     * {@code trigger = block} the staff member is blocked from now on (stored), admins are alerted and it is audited. A
     * person already blocked or locked gets no second alert.
     */
    void triggered(CommandSender sender, PunishDecision.Verdict v) {
        if (!(sender instanceof Player staff) || !"block".equals(v.args().get("trigger"))) {
            return;
        }
        long now = System.currentTimeMillis();
        Guard.Entry existing = entry(staff.getUniqueId(), now);
        if (existing != null && existing.state() != Guard.State.ALLOWED) {
            return;
        }
        Guard.Hit hit;
        try {
            hit = new Guard.Hit(Guard.Limit.valueOf(v.args().get("limit").toUpperCase(java.util.Locale.ROOT)),
                    Integer.parseInt(v.args().get("count")), Integer.parseInt(v.args().get("max")));
        } catch (RuntimeException e) {
            hit = null;
        }
        Guard.Entry entry = new Guard.Entry(staff.getUniqueId(), staff.getName(), Guard.State.BLOCKED, null,
                Guard.hitText(hit), "guard", now);
        entries.put(staff.getUniqueId(), entry);
        store.save(entry).exceptionally(e -> {
            core.plugin().getLogger().warning("Moderation: could not store a guard block: " + e.getMessage());
            return null;
        });
        lastAlert.put(staff.getUniqueId(), now);
        alert(staff.getName(), hit, "blocked");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tier", String.valueOf(tiers.get(staff.getUniqueId())));
        if (hit != null) {
            details.put("limit", hit.limit().id());
            details.put("count", hit.count());
            details.put("max", hit.max());
        }
        core.audit().log(AuditCategory.PUNISHMENT, "guard_block", Actor.system("guard"), staff.getUniqueId().toString(),
                staff.getName(), null, staff.getLocation(), details);
    }

    /** The alert line (type guard: always on for everyone with its permission) + Discord. Main thread. */
    private void alert(String staffName, Guard.Hit hit, String event) {
        Guard.Settings s = settings();
        TagResolver tags = TagResolver.resolver(Msg.p("staff", staffName), PunishmentService.pre("target", staffName),
                Msg.p("limit", hit == null ? "-" : service.text(null, "guard.limits." + hit.limit().id())),
                Msg.p("count", hit == null ? "?" : String.valueOf(hit.count())),
                Msg.p("max", hit == null ? "?" : String.valueOf(hit.max())),
                Msg.p("window", Format.duration(s.window())), PunishmentService.pre("allow", Templates.Step.compact(s.allowDefault())),
                PunishmentService.pre("rollback", Templates.Step.compact(s.alertRollback())));
        service.notifyStaff(NotifyRules.Type.GUARD, null, "guard.alert-" + event, tags);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (service.wants(p, NotifyRules.Type.GUARD)) {
                core.sounds().play(p, "notify");
            }
        }
        if (extras != null) {
            extras.guardPosted(staffName, event, hit, s.window());
        }
    }

    // ================================================================== sanctions (spec 14.1: max-sanctions)

    /**
     * The guard check for a sanction by {@code staff} (main thread, right before it is applied): a message key when
     * refused (and a block is set when this one goes over the limit), else {@code null}.
     */
    String sanctionProblem(Player staff) {
        long now = System.currentTimeMillis();
        Guard.Decision d = check(staff.getUniqueId(), tierOf(staff), Guard.Kind.SANCTION, now);
        return switch (d.verdict()) {
            case LOCKED -> "guard.locked";
            case BLOCKED -> "guard.blocked";
            case BLOCK_NOW -> {
                triggered(staff, new PunishDecision.Verdict(PunishDecision.Outcome.BLOCKED, "guard", "guard.blocked",
                        Map.of("trigger", "block", "limit", d.hit().limit().id(), "count", String.valueOf(d.hit().count()),
                                "max", String.valueOf(d.hit().max())), null));
                yield "guard.blocked";
            }
            case OK, ALERT -> null;
        };
    }

    /** Locked or blocked right now (read only: menus grey out the sanction buttons). */
    String stateProblem(UUID staff) {
        Guard.Entry e = entry(staff, System.currentTimeMillis());
        if (e == null) {
            return null;
        }
        return switch (e.state()) {
            case LOCKED -> "guard.locked";
            case BLOCKED -> "guard.blocked";
            case ALLOWED -> null;
        };
    }

    // ================================================================== /pguard

    private record Who(UUID uuid, String name) {
    }

    private CompletableFuture<Optional<Who>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Who(online.getUniqueId(), online.getName())));
        }
        return core.players().offline(name).thenApply(s -> s.map(snap -> new Who(snap.uuid(), snap.name())));
    }

    /** Names for tab completion: stored entries first. */
    List<String> entryNames() {
        return entries.values().stream().map(Guard.Entry::staffName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** {@code /pguard allow <staff> [time]}: lifts a block; for that time limits only alert. Not for a locked person. */
    void allow(CommandSender sender, String name, Duration length) {
        Duration d = length == null ? settings().allowDefault() : length;
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            Who who = found.get();
            long now = System.currentTimeMillis();
            Guard.Entry before = entry(who.uuid(), now);
            if (before != null && before.state() == Guard.State.LOCKED) {
                msg.send(sender, "guard.allow-locked", Msg.p("staff", who.name()), PunishmentService.pre("target", who.name()));
                return;
            }
            Guard.Entry e = new Guard.Entry(who.uuid(), who.name(), Guard.State.ALLOWED, now + d.toMillis(), null,
                    sender.getName(), now);
            entries.put(who.uuid(), e);
            window.clear(who.uuid()); // a fresh start: the period counts from now
            lastAlert.remove(who.uuid());
            store.save(e).thenRunAsync(() -> {
                service.notifyStaff(NotifyRules.Type.GUARD, sender, "guard.allowed", Msg.p("staff", who.name()),
                        Msg.p("by", sender.getName()), Msg.p("length", Format.duration(d)));
                core.audit().log(AuditCategory.PUNISHMENT, "guard_allow", Actor.of(sender), who.uuid().toString(), who.name(),
                        d.toMillis(), sender instanceof Player p ? p.getLocation() : null,
                        Map.of("was", before == null ? "normal" : before.state().name().toLowerCase(java.util.Locale.ROOT),
                                "until", e.until()));
                success(sender);
            }, main).exceptionally(ex -> failed(sender, ex));
        }, main);
    }

    /** {@code /pguard lock <staff>}: refuses every punishment of that person at once (no LuckPerms change needed). */
    void lock(CommandSender sender, String name) {
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            Who who = found.get();
            if (sender instanceof Player p && p.getUniqueId().equals(who.uuid())) {
                msg.send(sender, "guard.self");
                return;
            }
            long now = System.currentTimeMillis();
            Guard.Entry before = entry(who.uuid(), now);
            if (before != null && before.state() == Guard.State.LOCKED) {
                msg.send(sender, "guard.already-locked", Msg.p("staff", who.name()));
                return;
            }
            Guard.Entry e = new Guard.Entry(who.uuid(), who.name(), Guard.State.LOCKED, null, null, sender.getName(), now);
            entries.put(who.uuid(), e);
            store.save(e).thenRunAsync(() -> {
                service.notifyStaff(NotifyRules.Type.GUARD, sender, "guard.locked-notify", Msg.p("staff", who.name()),
                        Msg.p("by", sender.getName()), PunishmentService.pre("target", who.name()));
                Player target = Bukkit.getPlayer(who.uuid());
                if (target != null) {
                    msg.send(target, "guard.you-locked");
                }
                core.audit().log(AuditCategory.PUNISHMENT, "guard_lock", Actor.of(sender), who.uuid().toString(), who.name(),
                        null, sender instanceof Player p ? p.getLocation() : null,
                        Map.of("was", before == null ? "normal" : before.state().name().toLowerCase(java.util.Locale.ROOT)));
                success(sender);
            }, main).exceptionally(ex -> failed(sender, ex));
        }, main);
    }

    /** {@code /pguard unlock <staff>}: back to normal (also clears a block or an allowance). */
    void unlock(CommandSender sender, String name) {
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            Who who = found.get();
            Guard.Entry before = entries.remove(who.uuid());
            if (before == null) {
                msg.send(sender, "guard.not-limited", Msg.p("staff", who.name()));
                return;
            }
            window.clear(who.uuid());
            store.delete(who.uuid()).thenRunAsync(() -> {
                service.notifyStaff(NotifyRules.Type.GUARD, sender, "guard.unlocked", Msg.p("staff", who.name()),
                        Msg.p("by", sender.getName()));
                Player target = Bukkit.getPlayer(who.uuid());
                if (target != null && before.state() != Guard.State.ALLOWED) {
                    msg.send(target, "guard.you-unlocked");
                }
                core.audit().log(AuditCategory.PUNISHMENT, "guard_unlock", Actor.of(sender), who.uuid().toString(), who.name(),
                        null, sender instanceof Player p ? p.getLocation() : null,
                        Map.of("was", before.state().name().toLowerCase(java.util.Locale.ROOT)));
                success(sender);
            }, main).exceptionally(ex -> failed(sender, ex));
        }, main);
    }

    /** {@code /pguard status}: every blocked, locked or allowed staff member, and who is near a limit right now. */
    void status(CommandSender sender) {
        long now = System.currentTimeMillis();
        Guard.Settings s = settings();
        msg.send(sender, s.enabled() ? "guard.status.header" : "guard.status.header-off",
                Msg.p("window", Format.duration(s.window())));
        List<Guard.Entry> list = new ArrayList<>(entries.values());
        list.removeIf(e -> e.state() == Guard.State.ALLOWED && !e.allowedAt(now));
        list.sort(Comparator.comparing(Guard.Entry::state).thenComparing(Guard.Entry::staffName, String.CASE_INSENSITIVE_ORDER));
        for (Guard.Entry e : list) {
            String key = "guard.status." + e.state().name().toLowerCase(java.util.Locale.ROOT);
            msg.send(sender, key, Msg.p("staff", e.staffName()), PunishmentService.pre("target", e.staffName()),
                    Msg.p("by", e.by()), Msg.p("hit", e.hit() == null ? "-" : e.hit()),
                    Msg.p("left", e.until() == null ? "-" : Format.duration(Duration.ofMillis(Math.max(1000, e.until() - now)))),
                    Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, now - e.updated())))));
        }
        int active = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String tier = tiers.get(p.getUniqueId());
            Guard.Counts c = window.counts(p.getUniqueId(), s.window(), now);
            if (tier == null || c.total() == 0) {
                continue;
            }
            Guard.Tier t = s.tier(tier);
            active++;
            msg.send(sender, "guard.status.counts", Msg.p("staff", p.getName()), Msg.p("tier", tier),
                    Msg.p("bans", c.bans() + "/" + (t == null ? "-" : t.maxBans())),
                    Msg.p("mutes", c.mutes() + "/" + (t == null ? "-" : t.maxMutes())),
                    Msg.p("sanctions", c.sanctions() + "/" + (t == null ? "-" : t.maxSanctions())),
                    Msg.p("total", c.total() + "/" + (t == null ? "-" : t.maxTotal())));
        }
        if (list.isEmpty() && active == 0) {
            msg.send(sender, "guard.status.empty");
        }
    }

    private void success(CommandSender sender) {
        if (sender instanceof Player p) {
            core.sounds().play(p, "success");
        }
    }

    private Void failed(CommandSender sender, Throwable e) {
        core.plugin().getLogger().warning("Moderation: guard change failed: " + e.getMessage());
        core.scheduler().ensureMain("moderation:guard-error", () -> msg.send(sender, "error.failed"));
        return null;
    }
}
