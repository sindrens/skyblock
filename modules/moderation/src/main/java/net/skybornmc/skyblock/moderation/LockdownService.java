package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Raid lockdown (spec 13.9), the server side of {@link Lockdown}: {@code /lockdown chat|join|off|status}, the Bedrock-safe
 * {@code /lockdown} menu, the chat/command/sign/book gate and the login gate, raid detection with its staff alert, the
 * optional automatic chat lockdown, the staff reminder while one is active, audit + Discord for every start and stop.
 * In memory only: {@link #shutdown()} (server stop or module disable) ends everything, so a restart always starts clean.
 */
final class LockdownService {

    static final String PERM_LOCKDOWN = "skyblock.staff.lockdown";
    static final String PERM_BYPASS = "skyblock.lockdown.bypass";

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentService service;
    private final PunishExtras extras;
    private final Supplier<PunishConfig.Extras> config;
    private final Lockdown.State state = new Lockdown.State();
    private final Lockdown.RaidDetector detector = new Lockdown.RaidDetector();
    private Scheduler.Task task;
    private long lastReminder;
    /** Logins refused by the running join lockdown (one audit total at its end instead of one row per bot). */
    private final java.util.concurrent.atomic.AtomicInteger refused = new java.util.concurrent.atomic.AtomicInteger();

    LockdownService(Core core, ModuleMessages msg, PunishmentService service, PunishExtras extras,
                    Supplier<PunishConfig.Extras> config) {
        this.core = core;
        this.msg = msg;
        this.service = service;
        this.extras = extras;
        this.config = config;
    }

    Lockdown.Settings settings() {
        return config.get().lockdown();
    }

    Lockdown.Raid raid() {
        return config.get().raid();
    }

    Lockdown.State state() {
        return state;
    }

    void start() {
        task = core.scheduler().repeat("moderation:lockdown", 20L, 20L, this::tick);
    }

    /** Server stop / module disable: every lockdown ends (spec: cleared on restart), audited. */
    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        long now = System.currentTimeMillis();
        for (Lockdown.Active a : state.stopAll(now)) {
            audit("lockdown_end", Actor.SYSTEM, a, "restart");
            extras.lockdownPosted(a, "restart", null);
        }
    }

    // ================================================================== start / stop

    /**
     * Starts a lockdown ({@code requested == null} = the default length). Returns the message key of a problem (the length
     * is over the max), else {@code null}. Main thread.
     */
    String begin(CommandSender sender, Lockdown.Kind kind, Duration requested) {
        Lockdown.Length len = Lockdown.length(kind, requested, settings());
        if (!len.ok()) {
            return len.error();
        }
        long now = System.currentTimeMillis();
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        Lockdown.Active a = new Lockdown.Active(kind, now, now + len.duration().toMillis(), by, sender.getName(), false);
        Optional<Lockdown.Active> replaced = state.start(a, now);
        if (kind == Lockdown.Kind.JOIN && replaced.isEmpty()) {
            refused.set(0);
        }
        announce(sender, replaced.isPresent() ? "lockdown.changed" : "lockdown.started", a, now);
        Map<String, Object> details = details(a);
        replaced.ifPresent(r -> details.put("replaced_until", r.until()));
        core.audit().log(AuditCategory.STAFF, "lockdown_start", Actor.of(sender), details);
        extras.lockdownPosted(a, "start", sender.getName());
        if (sender instanceof Player p) {
            core.sounds().play(p, "success");
        }
        return null;
    }

    /** Tells {@code to} why {@link #begin} refused (the key it returned), with the kind and its max. */
    void tellProblem(CommandSender to, Lockdown.Kind kind, String key) {
        Player viewer = to instanceof Player p ? p : null;
        msg.send(to, key, Msg.p("kind", service.text(viewer, "lockdown.kinds." + kind.id())),
                Msg.p("max", PunishConfig.text(settings().maxFor(kind))));
        if (viewer != null) {
            core.sounds().play(viewer, "error");
        }
    }

    /** Ends one kind ({@code null} = everything). {@code false} when nothing was active. Main thread. */
    boolean end(CommandSender sender, Lockdown.Kind kind) {
        long now = System.currentTimeMillis();
        List<Lockdown.Active> ended = kind == null ? state.stopAll(now) : state.stop(kind, now).map(List::of).orElse(List.of());
        if (ended.isEmpty()) {
            msg.send(sender, "lockdown.none-active");
            if (sender instanceof Player p) {
                core.sounds().play(p, "error");
            }
            return false;
        }
        for (Lockdown.Active a : ended) {
            service.notifyStaff(NotifyRules.Type.RAID, sender, "lockdown.ended", parts(null, a, now), Msg.p("staff_now", sender.getName()));
            audit("lockdown_end", Actor.of(sender), a, "manual");
            extras.lockdownPosted(a, "stop", sender.getName());
        }
        if (sender instanceof Player p) {
            core.sounds().play(p, "success");
        }
        return true;
    }

    /** {@code key} to every staff member who gets raid alerts (spec 14.9), the sender and the console. */
    private void announce(CommandSender sender, String key, Lockdown.Active a, long now) {
        service.notifyStaff(NotifyRules.Type.RAID, sender, key, parts(null, a, now));
    }

    private void audit(String action, Actor actor, Lockdown.Active a, String how) {
        Map<String, Object> details = details(a);
        details.put("how", how);
        if (a.kind() == Lockdown.Kind.JOIN) {
            details.put("refused", refused.getAndSet(0));
        }
        core.audit().log(AuditCategory.STAFF, action, actor, details);
    }

    private static Map<String, Object> details(Lockdown.Active a) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kind", a.kind().id());
        d.put("length", PunishConfig.text(a.length()));
        d.put("until", a.until());
        d.put("by", a.byName());
        d.put("auto", a.auto());
        return d;
    }

    /** Placeholders of a lockdown: kind, what it does, length, time left, who, how long ago. */
    TagResolver parts(Player viewer, Lockdown.Active a, long now) {
        return TagResolver.resolver(Msg.p("kind", service.text(viewer, "lockdown.kinds." + a.kind().id())),
                Msg.p("what", service.text(viewer, "lockdown.what." + a.kind().id())),
                Msg.p("length", PunishConfig.text(a.length())), Msg.p("left", Format.duration(a.left(now))),
                Msg.p("staff", a.byName() == null ? service.text(viewer, "lockdown.system") : a.byName()),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, now - a.started())))));
    }

    // ================================================================== status

    /** {@code /lockdown status} (and {@code /lockdown} from the console). */
    void status(CommandSender sender) {
        Player viewer = sender instanceof Player p ? p : null;
        long now = System.currentTimeMillis();
        msg.send(sender, "lockdown.status-header");
        for (Lockdown.Kind kind : Lockdown.Kind.values()) {
            Optional<Lockdown.Active> a = state.live(kind, now);
            if (a.isPresent()) {
                msg.send(sender, a.get().auto() ? "lockdown.status-auto" : "lockdown.status-on", parts(viewer, a.get(), now));
            } else {
                msg.send(sender, "lockdown.status-off", Msg.p("kind", service.text(viewer, "lockdown.kinds." + kind.id())),
                        Msg.p("length", PunishConfig.text(settings().defaultFor(kind))),
                        Msg.p("max", PunishConfig.text(settings().maxFor(kind))));
            }
        }
        Lockdown.Raid raid = raid();
        if (raid.detect()) {
            msg.send(sender, raid.autoLock() ? "lockdown.status-raid-auto" : "lockdown.status-raid",
                    Msg.number("count", detector.count(now, raid.window())), Msg.p("window", PunishConfig.text(raid.window())),
                    Msg.number("accounts", raid.accounts()), Msg.p("length", PunishConfig.text(raid.autoDuration())));
        } else {
            msg.send(sender, "lockdown.status-raid-off");
        }
        msg.send(sender, "lockdown.status-new", Msg.p("playtime", PunishConfig.text(settings().newPlayerPlaytime())),
                Msg.p("age", PunishConfig.text(settings().newPlayerAge())));
    }

    /** Short status for the HUD placeholder (staff only), e.g. "Chat 3m · Join 1m"; empty when nothing is active. */
    String placeholder(Player viewer) {
        if (viewer == null || !viewer.hasPermission(ModerationModule.PERM_NOTIFY)) {
            return "";
        }
        long now = System.currentTimeMillis();
        List<String> parts = new ArrayList<>();
        for (Lockdown.Kind kind : Lockdown.Kind.values()) {
            state.live(kind, now).ifPresent(a -> parts.add(service.text(viewer, "lockdown.placeholder." + kind.id(),
                    Msg.p("left", Format.duration(a.left(now))))));
        }
        return String.join(service.text(viewer, "lockdown.placeholder.separator"), parts);
    }

    /** A staff member joined while a lockdown runs: they get the status line at once. */
    void staffJoined(Player player) {
        if (service.wants(player, NotifyRules.Type.RAID) && state.any(System.currentTimeMillis())) {
            remind(List.of(player));
        }
    }

    // ================================================================== timer: end by itself + staff reminder

    private void tick() {
        long now = System.currentTimeMillis();
        for (Lockdown.Active a : state.expire(now)) {
            service.notifyStaff(NotifyRules.Type.RAID, null, "lockdown.expired", parts(null, a, now));
            audit("lockdown_end", Actor.system("lockdown"), a, "expired");
            extras.lockdownPosted(a, "expired", null);
        }
        if (!state.any(now)) {
            lastReminder = 0;
            return;
        }
        long every = settings().staffReminder().toMillis();
        if (lastReminder == 0) {
            lastReminder = now; // the start announcement was the first line
            return;
        }
        if (now - lastReminder >= every) {
            lastReminder = now;
            remind(Bukkit.getOnlinePlayers().stream().filter(p -> service.wants(p, NotifyRules.Type.RAID))
                    .map(p -> (Player) p).toList());
        }
    }

    private void remind(List<Player> staff) {
        long now = System.currentTimeMillis();
        for (Player p : staff) {
            List<String> parts = new ArrayList<>();
            for (Lockdown.Kind kind : Lockdown.Kind.values()) {
                state.live(kind, now).ifPresent(a -> parts.add(service.text(p, "lockdown.reminder-part", parts(p, a, now))));
            }
            if (!parts.isEmpty()) {
                msg.send(p, "lockdown.reminder", Msg.p("list", String.join(", ", parts)));
            }
        }
    }

    // ================================================================== gates

    /**
     * Is this player's chat line / private message / sign / book blocked right now? Thread-safe (async chat).
     * Bypass holders (every rank above default) and staff never are; a player without loaded data is treated as not new
     * (never lock out a normal player by mistake).
     */
    boolean chatBlocked(Player player) {
        long now = System.currentTimeMillis();
        Lockdown.Active chat = state.get(Lockdown.Kind.CHAT);
        if (chat == null || !chat.live(now)) {
            return false;
        }
        // Owner answers 460: staff, builders, the free rank and donor ranks are exempt. Donor groups don't exist yet, so any
        // LuckPerms rank above default (weight > 0) counts as well as the bypass node.
        boolean exempt = player.hasPermission(PERM_BYPASS) || player.hasPermission(ModerationModule.PERM_NOTIFY)
                || Lockdown.rankExempt(core.ranks().available(), core.ranks().available() ? core.ranks().weight(player) : 0);
        Optional<PlayerData> data = core.players().get(player.getUniqueId());
        boolean isNew = data.isPresent() && Lockdown.isNew(data.get().firstSeen(), data.get().playtimeMillis(), now, settings());
        return Lockdown.chatBlocked(chat, now, exempt, isNew);
    }

    /** The "chat is limited" line (with the time left). */
    void tellChatBlocked(Player player) {
        long now = System.currentTimeMillis();
        Lockdown.Active chat = state.get(Lockdown.Kind.CHAT);
        msg.send(player, "lockdown.chat-blocked", Msg.p("left", Format.duration(chat == null ? Duration.ofSeconds(1) : chat.left(now))));
    }

    /** Is a private-message / mail command blocked for this player? */
    boolean commandBlocked(Player player, String label) {
        return Lockdown.blockedCommand(label, settings().blockedCommands()) && chatBlocked(player);
    }

    /** A join lockdown is live (the login check only then looks the account up). Thread-safe. */
    boolean joinLockActive() {
        return state.live(Lockdown.Kind.JOIN, System.currentTimeMillis()).isPresent();
    }

    /** Is a login refused? Only accounts that never joined before, only during a join lockdown. Thread-safe. */
    boolean joinBlocked(boolean knownAccount) {
        boolean blocked = Lockdown.joinBlocked(state.get(Lockdown.Kind.JOIN), System.currentTimeMillis(), knownAccount);
        if (blocked) {
            refused.incrementAndGet();
        }
        return blocked;
    }

    /** The friendly kick screen of the join lockdown. */
    Component joinScreen() {
        return msg.component(Bukkit.getConsoleSender(), "lockdown.join-screen");
    }

    // ================================================================== raid detection

    /** A never-seen account just joined (main thread). May raise the raid alert (and, if switched on, an auto lockdown). */
    void newAccountJoined(UUID id) {
        long now = System.currentTimeMillis();
        detector.record(id, now, raid()).ifPresent(count -> raidDetected(count, now));
    }

    private void raidDetected(int count, long now) {
        Lockdown.Raid raid = raid();
        Lockdown.Settings s = settings();
        Lockdown.Auto auto = Lockdown.auto(raid, s, state.get(Lockdown.Kind.CHAT), state.lastAuto(), now);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("count", count);
        details.put("window", PunishConfig.text(raid.window()));
        details.put("auto", auto.outcome().name().toLowerCase(java.util.Locale.ROOT));
        core.audit().log(AuditCategory.STAFF, "raid_detected", Actor.system("raid"), details);
        String key = switch (auto.outcome()) {
            case OFF -> "lockdown.raid.alert";
            case START -> "lockdown.raid.alert-auto";
            case ALREADY -> "lockdown.raid.alert-running";
            case COOLDOWN -> "lockdown.raid.alert-cooldown";
        };
        Duration length = auto.outcome() == Lockdown.AutoOutcome.START ? auto.duration() : s.chatDefault();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (service.wants(staff, NotifyRules.Type.RAID)) {
                msg.send(staff, key, Msg.number("count", count), Msg.p("window", PunishConfig.text(raid.window())),
                        Msg.p("length", PunishConfig.text(length)), Msg.p("cooldown", PunishConfig.text(raid.autoCooldown())),
                        Msg.c("start", startButton(staff)), Msg.c("ignore", ignoreButton(staff, count)));
                core.sounds().play(staff, "notify");
            }
        }
        msg.send(Bukkit.getConsoleSender(), key, Msg.number("count", count), Msg.p("window", PunishConfig.text(raid.window())),
                Msg.p("length", PunishConfig.text(length)), Msg.p("cooldown", PunishConfig.text(raid.autoCooldown())),
                Msg.p("start", ""), Msg.p("ignore", ""));
        if (auto.outcome() == Lockdown.AutoOutcome.START) {
            // spec: chat only, max 5m, once per 30 minutes, staff told at once (the line above + the start line)
            Lockdown.Active a = new Lockdown.Active(Lockdown.Kind.CHAT, now, now + auto.duration().toMillis(), null, null, true);
            state.start(a, now);
            service.notifyStaff(NotifyRules.Type.RAID, null, "lockdown.auto-started", parts(null, a, now));
            core.audit().log(AuditCategory.STAFF, "lockdown_start", Actor.system("raid"), details(a));
            extras.lockdownPosted(a, "start", null);
        }
    }

    /** [Start chat lockdown 10m] for one staff member (checks the permission on click). */
    private Component startButton(Player staff) {
        String length = PunishConfig.text(settings().chatDefault());
        return msg.component(staff, "lockdown.raid.start-button", Msg.p("length", length))
                .hoverEvent(HoverEvent.showText(msg.component(staff, "lockdown.raid.start-hover", Msg.p("length", length))))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    if (!clicker.hasPermission(PERM_LOCKDOWN)) {
                        msg.send(clicker, "lockdown.no-permission");
                        return;
                    }
                    if (state.live(Lockdown.Kind.CHAT, System.currentTimeMillis()).isPresent()) {
                        msg.send(clicker, "lockdown.raid.already");
                        return;
                    }
                    String problem = begin(clicker, Lockdown.Kind.CHAT, null);
                    if (problem != null) {
                        tellProblem(clicker, Lockdown.Kind.CHAT, problem);
                    }
                }, options -> options.uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(15))));
    }

    /** [Ignore]: nothing changes; audited so the decision is on record. */
    private Component ignoreButton(Player staff, int count) {
        return msg.component(staff, "lockdown.raid.ignore-button")
                .hoverEvent(HoverEvent.showText(msg.component(staff, "lockdown.raid.ignore-hover")))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        msg.send(clicker, "lockdown.raid.ignored");
                        core.audit().log(AuditCategory.STAFF, "raid_ignored", Actor.player(clicker), Map.of("count", count));
                    }
                }, options -> options.uses(1).lifetime(Duration.ofMinutes(15))));
    }

    /** Duration hints for tab completion: up to the kind's max, with its default and max. */
    List<String> lengthHints(Lockdown.Kind kind) {
        Lockdown.Settings s = settings();
        List<String> out = new ArrayList<>();
        for (String hint : List.of("1m", "3m", "5m", "10m", "15m", "30m", "60m")) {
            Duration d = net.skybornmc.skyblock.common.command.Durations.parse(hint);
            if (d.compareTo(s.maxFor(kind)) <= 0) {
                out.add(hint);
            }
        }
        for (Duration d : List.of(s.defaultFor(kind), s.maxFor(kind))) {
            String t = PunishConfig.text(d);
            if (!out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
