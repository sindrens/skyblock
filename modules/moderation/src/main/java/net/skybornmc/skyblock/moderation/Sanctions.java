package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.moderation.ListingSanctions;
import net.skybornmc.skyblock.common.moderation.SanctionService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Economy and item sanctions (P7, spec 13.1): freeze balance, block trading, remove money, confiscate item, and the
 * {@link SanctionService} other modules check. Account-wide: keyed by player UUID; profile keys are mapped to their
 * player. The active freeze/trade blocks live in memory (loaded at start, changed with every action), so the checks
 * never touch the database. Every action needs a reason, is audited ({@code sanction_*}), and is atomic.
 */
final class Sanctions implements SanctionService {

    static final String PERM_FREEZE = "skyblock.staff.sanction.freeze";
    static final String PERM_TRADE = "skyblock.staff.sanction.trade";
    static final String PERM_MONEY = "skyblock.staff.sanction.money";
    static final String PERM_CONFISCATE = "skyblock.staff.sanction.confiscate";
    static final String PERM_RESTORE = "skyblock.staff.sanction.restore";

    /** Where a confiscated item was taken from. */
    enum Source {
        INVENTORY, ENDER;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static String permission(Sanction.Kind kind) {
        return switch (kind) {
            case FREEZE -> PERM_FREEZE;
            case TRADE -> PERM_TRADE;
            case MONEY -> PERM_MONEY;
            case ITEM -> PERM_CONFISCATE;
        };
    }

    private final Core core;
    private final ModuleMessages msg;
    private final SanctionStore store;
    private final Supplier<PunishConfig.Extras> extras;
    private final Executor main;
    /** Active freeze/trade blocks by player UUID (copy-on-write lists). */
    private final Map<UUID, List<Sanction>> active = new ConcurrentHashMap<>();
    /** Ids for tab completion of {@code /psanction restore}. */
    private volatile List<String> restorableIds = List.of();
    private net.skybornmc.skyblock.common.util.Scheduler.Task expiryTask;

    Sanctions(Core core, ModuleMessages msg, SanctionStore store, Supplier<PunishConfig.Extras> extras) {
        this.core = core;
        this.msg = msg;
        this.store = store;
        this.extras = extras;
        this.main = new MainThread(core.plugin());
    }

    SanctionStore store() {
        return store;
    }

    SanctionRules.Settings settings() {
        return extras.get().sanctions();
    }

    /** Loads the active blocks and starts the expiry check (in memory, every second; no database polling). */
    void start() {
        try {
            for (Sanction s : core.database().blocking(c -> net.skybornmc.skyblock.common.db.Sql.query(c, "SELECT "
                    + SanctionStore.COLUMNS + " FROM sanctions WHERE state = 'ACTIVE' AND kind IN ('FREEZE', 'TRADE')",
                    SanctionStore::map))) {
                remember(s);
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Moderation: loading active sanctions failed", Database.cause(e));
        }
        expiryTask = core.scheduler().repeat("moderation:sanction-expiry", 40L, 20L, this::expireDue);
        refreshIds();
    }

    void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
    }

    void refreshIds() {
        store.restorableIds(50).thenAccept(ids -> restorableIds = ids.stream().map(String::valueOf).toList());
    }

    List<String> restorableIds() {
        return restorableIds;
    }

    private void remember(Sanction s) {
        if (s.kind().blocking() && s.state() == Sanction.State.ACTIVE) {
            active.compute(s.target(), (k, list) -> {
                List<Sanction> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
                copy.removeIf(x -> x.id() == s.id());
                copy.add(s);
                return List.copyOf(copy);
            });
        }
    }

    private void forget(Sanction s) {
        active.computeIfPresent(s.target(), (k, list) -> {
            List<Sanction> copy = new ArrayList<>(list);
            copy.removeIf(x -> x.id() == s.id());
            return copy.isEmpty() ? null : List.copyOf(copy);
        });
    }

    /** Active blocks of a player (player UUID). */
    List<Sanction> activeOf(UUID player) {
        return active.getOrDefault(player, List.of());
    }

    private UUID person(UUID playerOrProfile) {
        UUID owner = ProfileService.of(core.services()).ownerOf(playerOrProfile);
        return owner == null ? playerOrProfile : owner;
    }

    // ================================================================== SanctionService (checks)

    @Override
    public Optional<Block> block(UUID player, Use use) {
        if (player == null || use == null) {
            return Optional.empty();
        }
        return SanctionRules.blocking(activeOf(person(player)), use, settings(), System.currentTimeMillis())
                .map(s -> new Block(s.id(), s.kind() == Sanction.Kind.FREEZE ? Kind.FREEZE : Kind.TRADE, s.expires(), s.reason()));
    }

    @Override
    public boolean blocks(Transaction tx) {
        return SanctionRules.blocks(tx, this::activeOf, this::person, settings(), System.currentTimeMillis());
    }

    @Override
    public void tell(Player player, Block block, Use use) {
        msg.send(player, "sanctions.blocked." + block.kind().name().toLowerCase(Locale.ROOT),
                Msg.p("use", text(player, "sanctions.uses." + use.id())), Msg.p("reason", block.reason()),
                Msg.p("until", until(player, block.until())));
        core.sounds().play(player, "error");
    }

    @Override
    public void tellUnavailable(Player player, Use use) {
        msg.send(player, "sanctions.unavailable", Msg.p("use", text(player, "sanctions.uses." + use.id())));
        core.sounds().play(player, "error");
    }

    @Override
    public void tellOther(Player viewer, String otherName, Use use) {
        msg.send(viewer, "sanctions.blocked.other", Msg.p("player", otherName), Msg.p("use", text(viewer, "sanctions.uses." + use.id())));
        core.sounds().play(viewer, "error");
    }

    // ================================================================== texts

    String text(Player viewer, String key, TagResolver... tags) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.component(viewer == null ? Bukkit.getConsoleSender() : viewer, key, tags));
    }

    /** "12 Nov 2026, 18:00" (Europe/Oslo) or "until lifted". */
    String until(Player viewer, Long until) {
        return until == null ? text(viewer, "sanctions.until-lifted")
                : PunishmentService.DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(until));
    }

    String reasonText(Player viewer, String reasonId) {
        String key = "sanctions.reasons." + reasonId;
        return msg.has(key) ? text(viewer, key) : reasonId;
    }

    String lengthText(Player viewer, Duration length) {
        return length == null ? text(viewer, "sanctions.until-lifted") : Format.duration(length);
    }

    String kindText(Player viewer, Sanction.Kind kind) {
        return text(viewer, "sanctions.kinds." + kind.id());
    }

    String amountText(String currency, Long amount) {
        if (amount == null) {
            return "-";
        }
        Optional<EconomyService> eco = core.services().optional(EconomyService.class);
        Currency c = Currency.byKey(currency == null ? "money" : currency).orElse(Currency.MONEY);
        return eco.map(e -> e.format(c, amount)).orElseGet(() -> Format.number(amount));
    }

    /** Placeholders of a sanction for chat lines and menus. */
    TagResolver parts(Player viewer, Sanction s) {
        return TagResolver.resolver(Msg.p("sid", s.id()), Msg.p("kind", kindText(viewer, s.kind())),
                Msg.p("player", s.targetName()), Msg.p("staff", s.issuerName()), Msg.p("reason", s.reason()),
                Msg.p("until", until(viewer, s.expires())), Msg.p("amount", amountText(s.currency(), s.amount())),
                Msg.p("item", s.itemText() == null ? "-" : s.itemText()),
                Msg.p("state", text(viewer, "sanctions.states." + s.state().name().toLowerCase(Locale.ROOT))),
                Msg.p("date", PunishmentService.DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(s.created()))),
                Msg.p("handler", s.handledByName() == null ? "-" : s.handledByName()));
    }

    // ================================================================== availability and rights

    /** Freeze/trade/money need the economy module; confiscating only needs the inventory (spec 13.1). */
    boolean available(Sanction.Kind kind) {
        if (!settings().enabled()) {
            return false;
        }
        return kind == Sanction.Kind.ITEM || core.services().optional(EconomyService.class).isPresent();
    }

    /** The guard of spec 14.1 (P9; set by the module). */
    private GuardService guard;

    void guard(GuardService value) {
        this.guard = value;
    }

    /**
     * The guard at the moment of applying (spec 14.1: max-sanctions per window): refuses a locked or blocked staff
     * member, and blocks one whose sanction would go over the limit. Main thread. Returns true if refused (and told).
     */
    private boolean guardRefuses(Player staff) {
        String problem = guard == null ? null : guard.sanctionProblem(staff);
        return problem != null && refuse(staff, problem);
    }

    /** A sanction was applied: the guard counts it (spec 14.1). */
    private void guardCount(Player staff) {
        if (guard != null) {
            guard.recorded(staff.getUniqueId(), staff.getName(), Guard.Kind.SANCTION, System.currentTimeMillis());
        }
    }

    /** Why {@code staff} can't give this kind right now (message key), or {@code null}. Main thread. */
    String rightsProblem(CommandSender staff, Sanction.Kind kind) {
        if (!available(kind)) {
            return "sanctions.unavailable";
        }
        if (guard != null && staff instanceof Player p) {
            String g = guard.stateProblem(p.getUniqueId()); // 14.1: locked or blocked (read only)
            if (g != null) {
                return g;
            }
        }
        if (!staff.hasPermission(permission(kind))) {
            return "sanctions.no-permission";
        }
        // spec 14.6: sanctions have no request queue, so a trainee can't give them while "sanction" is in requests-for
        if (Training.sanctionRefused(extras.get().training(), PunishmentService.trainee(staff))) {
            return "sanctions.trainee";
        }
        // spec 13.4: a sanction kind listed in approval.rules needs an approver; sanctions are not queued (P7 default)
        if (!staff.hasPermission(ModerationModule.PERM_APPROVE) && Approval.sanctionNeedsApproval(extras.get().approval(), kind)) {
            return "sanctions.needs-approval";
        }
        return null;
    }

    private boolean refuse(Player staff, String key, TagResolver... tags) {
        msg.send(staff, key, tags);
        core.sounds().play(staff, "error");
        return true;
    }

    private static Actor actor(CommandSender sender) {
        return Actor.of(sender);
    }

    private void audit(CommandSender sender, String action, Sanction s, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", s.id());
        details.put("kind", s.kind().id());
        details.put("reason", s.reason());
        if (s.expires() != null) {
            details.put("expires", s.expires());
        }
        if (s.amount() != null) {
            details.put("amount", s.amount());
            details.put("currency", s.currency());
        }
        if (s.itemText() != null) {
            details.put("item", s.itemText());
        }
        if (s.profileKey() != null) {
            details.put("profile", s.profileKey().toString());
        }
        details.putAll(extra);
        core.audit().log(AuditCategory.PUNISHMENT, action, sender == null ? Actor.SYSTEM : actor(sender), s.target().toString(),
                s.targetName(), s.expires() == null ? null : s.expires() - s.created(),
                sender instanceof Player p ? p.getLocation() : null, details);
    }

    /** Who gets the sanction lines (P8, spec 14.9: the punishments feed; set by the module). */
    private java.util.function.Predicate<Player> notified = p -> p.hasPermission(NotifyRules.Type.PUNISHMENTS.permission());

    void notified(java.util.function.Predicate<Player> value) {
        this.notified = value;
    }

    private void notifyStaff(CommandSender sender, String key, TagResolver... tags) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (notified.test(staff) || staff.equals(sender)) {
                msg.send(staff, key, tags);
            }
        }
        if (sender != null && !(sender instanceof Player)) {
            msg.send(sender, key, tags);
        }
    }

    private void failed(CommandSender sender, Throwable e, String what) {
        core.plugin().getLogger().log(Level.SEVERE, "Moderation: sanction " + what + " failed", Database.cause(e));
        core.scheduler().ensureMain("moderation:sanction-error", () -> {
            msg.send(sender, "error.failed");
            if (sender instanceof Player p) {
                core.sounds().play(p, "error");
            }
        });
    }

    // ================================================================== freeze balance / block trading

    /**
     * Starts a freeze or trade block (main thread in and out). A trade block cancels the player's auction listings
     * (items to their collection bin, never deleted).
     */
    CompletableFuture<Optional<Sanction>> startBlock(Player staff, UUID target, String targetName, Sanction.Kind kind,
                                                     Duration length, String reasonId) {
        String problem = rightsProblem(staff, kind);
        if (problem != null) {
            refuse(staff, problem, Msg.p("kind", kindText(staff, kind)));
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (target.equals(staff.getUniqueId())) {
            refuse(staff, "error.self");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (guardRefuses(staff)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long now = System.currentTimeMillis();
        Sanction draft = new Sanction(0, kind, target, targetName, null, staff.getUniqueId(), staff.getName(),
                reasonText(null, reasonId), now, SanctionRules.expires(length, now), null, null, null, Sanction.State.ACTIVE,
                null, null, null, null);
        return store.block(draft, now).thenApplyAsync(r -> {
            if (!r.ok()) {
                refuse(staff, "sanctions.already", parts(staff, r.existing()));
                return Optional.<Sanction>empty();
            }
            Sanction s = r.created();
            remember(s);
            guardCount(staff);
            notifyStaff(staff, "sanctions.notify.started", parts(staff, s));
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                msg.send(online, "sanctions.you." + kind.id(), parts(online, s));
                core.sounds().play(online, "error");
            }
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("reason_id", reasonId);
            audit(staff, "sanction_" + kind.id(), s, extra);
            if (kind == Sanction.Kind.TRADE) {
                cancelListings(staff, s);
            }
            return Optional.of(s);
        }, main).exceptionally(e -> {
            failed(staff, e, "start");
            return Optional.empty();
        });
    }

    private void cancelListings(Player staff, Sanction s) {
        Optional<ListingSanctions> listings = core.services().optional(ListingSanctions.class);
        if (listings.isEmpty()) {
            return;
        }
        listings.get().cancelListings(s.target(), s.reason(), Actor.player(staff)).thenAcceptAsync(count -> {
            if (count > 0) {
                msg.send(staff, "sanctions.listings-cancelled", Msg.number("count", count), Msg.p("player", s.targetName()));
                audit(staff, "sanction_listings_cancelled", s, Map.of("count", count));
            }
        }, main);
    }

    /** Lifts a freeze/trade block (main thread in and out). */
    CompletableFuture<Boolean> lift(Player staff, Sanction s) {
        if (!staff.hasPermission(permission(s.kind()))) {
            refuse(staff, "sanctions.no-permission", Msg.p("kind", kindText(staff, s.kind())));
            return CompletableFuture.completedFuture(false);
        }
        long now = System.currentTimeMillis();
        String why = SanctionRules.liftProblem(s, now);
        if (why != null) {
            refuse(staff, "sanctions.lift-" + why, parts(staff, s));
            return CompletableFuture.completedFuture(false);
        }
        return store.lift(s.id(), staff.getUniqueId(), staff.getName(), text(null, "sanctions.lift-reason"), now)
                .thenApplyAsync(done -> {
                    if (done.isEmpty()) {
                        forget(s);
                        refuse(staff, "sanctions.lift-not-active", parts(staff, s));
                        return false;
                    }
                    Sanction lifted = done.get();
                    forget(lifted);
                    notifyStaff(staff, "sanctions.notify.lifted", parts(staff, lifted), Msg.p("by", staff.getName()));
                    Player online = Bukkit.getPlayer(lifted.target());
                    if (online != null) {
                        msg.send(online, "sanctions.you.lifted", parts(online, lifted));
                        core.sounds().play(online, "notify");
                    }
                    audit(staff, "sanction_lift", lifted, Map.of());
                    return true;
                }, main).exceptionally(e -> {
                    failed(staff, e, "lift");
                    return false;
                });
    }

    /** Ends blocks whose time ran out: marked EXPIRED once (audited), the player told. Main thread, in memory only. */
    private void expireDue() {
        long now = System.currentTimeMillis();
        List<Long> due = new ArrayList<>();
        for (List<Sanction> list : active.values()) {
            for (Sanction s : list) {
                if (s.expiredAt(now)) {
                    due.add(s.id());
                }
            }
        }
        if (due.isEmpty()) {
            return;
        }
        for (List<Sanction> list : List.copyOf(active.values())) {
            for (Sanction s : list) {
                if (due.contains(s.id())) {
                    forget(s); // the checks already ignore it (expires <= now); drop it from memory once
                }
            }
        }
        store.expire(due, now).thenAcceptAsync(done -> {
            for (Sanction s : done) {
                audit(null, "sanction_expired", s, Map.of());
                Player online = Bukkit.getPlayer(s.target());
                if (online != null) {
                    msg.send(online, "sanctions.you.ended", parts(online, s));
                    core.sounds().play(online, "notify");
                }
            }
        }, main).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Moderation: marking expired sanctions failed", Database.cause(e));
            return null;
        });
    }

    // ================================================================== remove money / restore money

    /**
     * Removes up to {@code requested} money from the target's active (offline: last active) profile, never more than
     * the balance. The sanction row and the balance change are one database transaction. Main thread in and out.
     */
    CompletableFuture<Optional<Sanction>> removeMoney(Player staff, UUID target, String targetName, long requested, String reasonId) {
        String problem = rightsProblem(staff, Sanction.Kind.MONEY);
        if (problem != null) {
            refuse(staff, problem, Msg.p("kind", kindText(staff, Sanction.Kind.MONEY)));
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (target.equals(staff.getUniqueId())) {
            refuse(staff, "error.self");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (guardRefuses(staff)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        EconomyService eco = core.services().optional(EconomyService.class).orElseThrow();
        ProfileService profiles = ProfileService.of(core.services());
        String reason = reasonText(null, reasonId);
        Actor actor = Actor.player(staff);
        UUID staffId = staff.getUniqueId();
        String staffName = staff.getName();
        CompletableFuture<UUID> key = Bukkit.getPlayer(target) != null
                ? CompletableFuture.completedFuture(profiles.profileKey(target)) : profiles.profileKeyAsync(target);
        return key.thenCompose(k -> attemptRemove(eco, k, target, targetName, requested, reason, actor, staffId, staffName, 2))
                .thenApplyAsync(result -> {
                    if (result.sanction() == null) {
                        refuse(staff, result.key(), Msg.p("player", targetName));
                        return Optional.<Sanction>empty();
                    }
                    Sanction s = result.sanction();
                    guardCount(staff);
                    notifyStaff(staff, "sanctions.notify.money", parts(staff, s));
                    Player online = Bukkit.getPlayer(target);
                    if (online != null) {
                        msg.send(online, "sanctions.you.money", parts(online, s));
                        core.sounds().play(online, "error");
                    }
                    audit(staff, "sanction_money", s, Map.of("requested", requested, "reason_id", reasonId));
                    refreshIds();
                    return Optional.of(s);
                }, main).exceptionally(e -> {
                    failed(staff, e, "remove money");
                    return Optional.empty();
                });
    }

    private record RemoveResult(Sanction sanction, String key) {
    }

    private CompletableFuture<RemoveResult> attemptRemove(EconomyService eco, UUID profileKey, UUID target, String targetName,
                                                          long requested, String reason, Actor actor, UUID staffId,
                                                          String staffName, int tries) {
        long max = settings().moneyMax();
        return eco.balance(AccountRef.player(profileKey), Currency.MONEY).thenCompose(balance -> {
            long take = SanctionRules.takeable(requested, balance, max);
            if (take <= 0) {
                return CompletableFuture.completedFuture(new RemoveResult(null, "sanctions.money-nothing"));
            }
            long now = System.currentTimeMillis();
            Sanction draft = new Sanction(0, Sanction.Kind.MONEY, target, targetName, profileKey, staffId, staffName, reason,
                    now, null, take, Currency.MONEY.key(), null, Sanction.State.ACTIVE, null, null, null, null);
            AtomicLong id = new AtomicLong();
            Transaction tx = Transaction.withdraw(AccountRef.player(profileKey), Currency.MONEY, take, TxReason.SANCTION_REMOVE)
                    .actor(actor).reference("sanction").idempotencyKey("sanction_remove:" + UUID.randomUUID())
                    .detail("target_name", targetName).detail("reason", reason);
            return eco.executeGuarded(tx, c -> {
                id.set(SanctionStore.insertMoney(c, draft));
                return true;
            }).thenCompose(r -> {
                if (r.ok()) {
                    return CompletableFuture.completedFuture(new RemoveResult(draft.withId(id.get()), null));
                }
                if (r.status() == TxResult.Status.INSUFFICIENT_FUNDS && tries > 1) {
                    // the balance dropped between the read and the transaction: try again with the new balance
                    return attemptRemove(eco, profileKey, target, targetName, requested, reason, actor, staffId, staffName, tries - 1);
                }
                return CompletableFuture.completedFuture(new RemoveResult(null, "sanctions.money-failed"));
            });
        });
    }

    // ================================================================== restore (money or item)

    /** {@code /psanction restore <id>}: gives back removed money or a confiscated item. Main thread in. */
    void restore(CommandSender sender, long id) {
        if (!sender.hasPermission(PERM_RESTORE)) {
            msg.send(sender, "sanctions.no-permission", Msg.p("kind", text(null, "sanctions.restore-word")));
            return;
        }
        store.get(id).thenAcceptAsync(found -> {
            Sanction s = found.orElse(null);
            String problem = SanctionRules.restoreProblem(s);
            if (problem != null) {
                msg.send(sender, "sanctions.restore-" + problem, Msg.p("sid", id),
                        s == null ? Msg.p("state", "-") : parts(sender instanceof Player p ? p : null, s));
                return;
            }
            if (s.kind() == Sanction.Kind.MONEY) {
                restoreMoney(sender, s, text(null, "sanctions.restore-reason"), null);
            } else {
                restoreItem(sender, s, text(null, "sanctions.restore-reason"), null);
            }
        }, main).exceptionally(e -> {
            failed(sender, e, "restore");
            return null;
        });
    }

    /**
     * Rollback (P9, spec 14.1): lifts the active freeze/trade blocks and gives back the held money and items of
     * {@code list} with the reason {@code why}. Each success is marked with the rollback group and audited as usual;
     * {@code done} is called once per sanction that was reversed (main thread). An item of an offline player stays held
     * (staff are told to restore it later with /psanction restore).
     */
    void rollback(CommandSender sender, List<Sanction> list, long group, String why, java.util.function.Consumer<Sanction> done) {
        java.util.function.Consumer<Sanction> mark = s -> {
            store.markRollback(s.id(), group);
            done.accept(s);
        };
        for (Sanction s : list) {
            switch (s.kind()) {
                case FREEZE, TRADE -> liftQuietly(sender, s, why, mark);
                case MONEY -> restoreMoney(sender, s, why, mark);
                case ITEM -> restoreItem(sender, s, why, mark);
            }
        }
    }

    private void liftQuietly(CommandSender sender, Sanction s, String why, java.util.function.Consumer<Sanction> done) {
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        store.lift(s.id(), by, sender.getName(), why, System.currentTimeMillis()).thenAcceptAsync(r -> {
            if (r.isEmpty()) {
                return; // ended meanwhile
            }
            Sanction lifted = r.get();
            forget(lifted);
            Player online = Bukkit.getPlayer(lifted.target());
            if (online != null) {
                msg.send(online, "sanctions.you.lifted", parts(online, lifted));
            }
            audit(sender, "sanction_lift", lifted, Map.of("rollback", true));
            done.accept(lifted);
        }, main).exceptionally(e -> {
            failed(sender, e, "rollback lift");
            return null;
        });
    }

    private void restoreMoney(CommandSender sender, Sanction s, String why, java.util.function.Consumer<Sanction> done) {
        Optional<EconomyService> eco = core.services().optional(EconomyService.class);
        if (eco.isEmpty()) {
            msg.send(sender, "sanctions.unavailable", Msg.p("kind", kindText(null, s.kind())));
            return;
        }
        ProfileService profiles = ProfileService.of(core.services());
        // the money goes back to the profile it came from; a deleted profile → the player's current profile
        CompletableFuture<UUID> key = s.profileKey() != null && profiles.isLive(s.profileKey())
                ? CompletableFuture.completedFuture(s.profileKey())
                : profiles.profileKeyAsync(s.target());
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        String byName = sender.getName();
        Currency currency = Currency.byKey(s.currency()).orElse(Currency.MONEY);
        key.thenCompose(k -> eco.get().executeGuarded(Transaction.deposit(AccountRef.player(k), currency, s.amount(),
                        TxReason.SANCTION_RESTORE).actor(actor(sender)).reference("sanction:" + s.id())
                        .idempotencyKey("sanction_restore:" + s.id()).detail("target_name", s.targetName()),
                c -> SanctionStore.claimMoneyRestore(c, s.id(), by, byName, why, System.currentTimeMillis())))
                .thenAcceptAsync(r -> {
                    if (r.status() == TxResult.Status.DUPLICATE) {
                        msg.send(sender, "sanctions.restore-already", parts(null, s));
                        return;
                    }
                    if (!r.ok()) {
                        msg.send(sender, "sanctions.money-failed", Msg.p("player", s.targetName()));
                        return;
                    }
                    Sanction restored = s.handled(Sanction.State.RESTORED, by, byName, System.currentTimeMillis(), why);
                    notifyStaff(sender, "sanctions.notify.restored", parts(null, restored), Msg.p("by", byName));
                    Player online = Bukkit.getPlayer(s.target());
                    if (online != null) {
                        msg.send(online, "sanctions.you.money-restored", parts(online, restored));
                        core.sounds().play(online, "notify");
                    }
                    audit(sender, "sanction_restore", restored, Map.of());
                    refreshIds();
                    if (done != null) {
                        done.accept(restored);
                    }
                }, main).exceptionally(e -> {
                    failed(sender, e, "restore money");
                    return null;
                });
    }

    private void restoreItem(CommandSender sender, Sanction s, String why, java.util.function.Consumer<Sanction> done) {
        Player online = Bukkit.getPlayer(s.target());
        if (online == null) {
            msg.send(sender, "sanctions.restore-offline", parts(null, s));
            return;
        }
        ProfileService profiles = ProfileService.of(core.services());
        if (profiles.isSwitching(online.getUniqueId())
                || (s.profileKey() != null && !s.profileKey().equals(profiles.profileKey(online.getUniqueId())))) {
            msg.send(sender, "sanctions.restore-profile", parts(null, s));
            return;
        }
        if (online.getInventory().firstEmpty() < 0) {
            msg.send(sender, "sanctions.restore-full", parts(null, s));
            return;
        }
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        String byName = sender.getName();
        store.claimItemRestore(s.id(), by, byName, why, System.currentTimeMillis()).thenAcceptAsync(bytes -> {
            if (bytes.isEmpty()) {
                msg.send(sender, "sanctions.restore-already", parts(null, s));
                return;
            }
            Player now = Bukkit.getPlayer(s.target());
            boolean sameProfile = now != null && (s.profileKey() == null
                    || s.profileKey().equals(profiles.profileKey(now.getUniqueId()))) && !profiles.isSwitching(now.getUniqueId());
            if (!sameProfile) {
                // left or switched while the database worked: keep holding it
                store.unclaimItemRestore(s.id());
                msg.send(sender, "sanctions.restore-offline", parts(null, s));
                return;
            }
            ItemStack item = ItemStack.deserializeBytes(bytes.get());
            net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), now, net.skybornmc.skyblock.common.box.BoxSource.SANCTION, item); // never dropped: the rest goes to /box
            Sanction restored = s.handled(Sanction.State.RESTORED, by, byName, System.currentTimeMillis(), why);
            notifyStaff(sender, "sanctions.notify.restored", parts(null, restored), Msg.p("by", byName));
            msg.send(now, "sanctions.you.item-restored", parts(now, restored));
            core.sounds().play(now, "notify");
            audit(sender, "sanction_restore", restored, Map.of());
            refreshIds();
            if (done != null) {
                done.accept(restored);
            }
        }, main).exceptionally(e -> {
            failed(sender, e, "restore item");
            return null;
        });
    }

    // ================================================================== confiscate

    /** The inventory a source means (ender chest or the player inventory). */
    static Inventory inventory(Player target, Source source) {
        return source == Source.ENDER ? target.getEnderChest() : target.getInventory();
    }

    /** "Diamond Sword x3" (plain, for history and audit). */
    static String describe(ItemStack item) {
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        return name + " x" + item.getAmount();
    }

    /**
     * Confiscates the stack in {@code slot} of the online target's inventory or ender chest (main thread in and out).
     * Order: (1) the sanction (PENDING) and the serialized item are written, (2) the slot is checked to still hold
     * exactly that stack and is emptied, (3) the sanction becomes ACTIVE (held). If the slot changed meanwhile the row
     * becomes FAILED and nothing is taken. A PENDING row is never restorable, so a crash can never duplicate the item.
     */
    CompletableFuture<Optional<Sanction>> confiscate(Player staff, Player target, Source source, int slot, ItemStack expected,
                                                     String reasonId) {
        String problem = rightsProblem(staff, Sanction.Kind.ITEM);
        if (problem != null) {
            refuse(staff, problem, Msg.p("kind", kindText(staff, Sanction.Kind.ITEM)));
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (target.equals(staff)) {
            refuse(staff, "error.self");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (guardRefuses(staff)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        ProfileService profiles = ProfileService.of(core.services());
        if (!target.isOnline() || profiles.isSwitching(target.getUniqueId())) {
            refuse(staff, "sanctions.confiscate-offline", Msg.p("player", target.getName()));
            return CompletableFuture.completedFuture(Optional.empty());
        }
        ItemStack now = inventory(target, source).getItem(slot);
        if (expected == null || expected.getType().isAir() || now == null || !now.equals(expected)) {
            refuse(staff, "sanctions.confiscate-moved", Msg.p("player", target.getName()));
            return CompletableFuture.completedFuture(Optional.empty());
        }
        ItemStack snapshot = now.clone();
        byte[] bytes = snapshot.serializeAsBytes();
        UUID profileKey = profiles.profileKey(target.getUniqueId());
        long created = System.currentTimeMillis();
        Sanction draft = new Sanction(0, Sanction.Kind.ITEM, target.getUniqueId(), target.getName(), profileKey,
                staff.getUniqueId(), staff.getName(), reasonText(null, reasonId), created, null, null, null,
                describe(snapshot), Sanction.State.PENDING, null, null, null, null);
        return store.insertPendingItem(draft, bytes, source.id(), slot).thenComposeAsync(id -> {
            boolean taken = false;
            if (target.isOnline() && !profiles.isSwitching(target.getUniqueId())
                    && profileKey.equals(profiles.profileKey(target.getUniqueId()))) {
                Inventory inv = inventory(target, source);
                ItemStack current = inv.getItem(slot);
                if (current != null && current.equals(snapshot)) {
                    inv.setItem(slot, null);
                    taken = true;
                }
            }
            boolean ok = taken;
            return store.settleItem(id, ok, System.currentTimeMillis()).thenApplyAsync(settled -> {
                if (!ok) {
                    refuse(staff, "sanctions.confiscate-moved", Msg.p("player", target.getName()));
                    audit(staff, "sanction_confiscate_failed", draft.withId(id), Map.of("source", source.id(), "slot", slot));
                    return Optional.<Sanction>empty();
                }
                Sanction s = new Sanction(id, Sanction.Kind.ITEM, draft.target(), draft.targetName(), profileKey,
                        draft.issuer(), draft.issuerName(), draft.reason(), created, null, null, null, draft.itemText(),
                        Sanction.State.ACTIVE, null, null, null, null);
                guardCount(staff);
                notifyStaff(staff, "sanctions.notify.confiscated", parts(staff, s));
                if (target.isOnline()) {
                    msg.send(target, "sanctions.you.confiscated", parts(target, s));
                    core.sounds().play(target, "error");
                }
                audit(staff, "sanction_confiscate", s, Map.of("source", source.id(), "slot", slot, "reason_id", reasonId));
                refreshIds();
                return Optional.of(s);
            }, main);
        }, main).exceptionally(e -> {
            failed(staff, e, "confiscate");
            return Optional.empty();
        });
    }

    // ================================================================== dupe detector link (spec 13.1)

    /**
     * A detected dupe creates a suggestion (spec 13.1: "Duping detected → suggested: ban 7d + freeze balance"), never
     * applied by itself. The suggested punishment is the ladder step of {@code dupe-suggestion.reason}; the details say
     * to freeze the balance too (Sanctions button of {@code /p}).
     */
    void onDupe(net.skybornmc.skyblock.common.item.DupeReport report, ApiSuggestions suggestions) {
        SanctionRules.Settings s = settings();
        if (!s.enabled() || !s.dupeSuggest() || report == null || report.playerIds() == null) {
            return;
        }
        String details = text(null, "sanctions.dupe-details", Msg.p("item", report.itemId() == null ? "?" : report.itemId()),
                Msg.number("copies", report.holders() == null ? 0 : report.holders().size()));
        for (UUID player : java.util.Set.copyOf(report.playerIds())) {
            suggestions.suggest(player, s.dupeReason(), text(null, "sanctions.dupe-source"), details, true,
                    Actor.system("dupe-detector")).exceptionally(e -> null);
        }
    }

    /** All active blocks (for tests and diagnostics). */
    Collection<List<Sanction>> allActive() {
        return active.values();
    }
}
