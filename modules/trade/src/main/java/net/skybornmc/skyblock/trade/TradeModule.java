package net.skybornmc.skyblock.trade;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PlayerPickerMenu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Secure trade (M3.7): {@code /trade <player>} request, {@code /trade accept|deny [player]}, a two-sided trade window where
 * both players put in items (from their own inventory) and money, both confirm, a short countdown, then one atomic swap.
 * The dupe-safe state machine is {@link TradeSession} (memory) + {@link TradeEngine} (database); this class is the Bukkit
 * side: commands, menus, inventories, guards and delivery.
 *
 * <p>Items leave the inventory when offered and live in the database escrow until they are delivered (to the other side
 * after a completed trade, back to the owner otherwise). Delivery only happens into the inventory of the profile the
 * item belongs to, never while switching profile or in staff mode; until then it waits (delivered on join, profile switch
 * and every {@code tasks.delivery-seconds}).</p>
 */
public final class TradeModule implements SkyblockModule, Listener {

    static final String ID = "trade";

    static final String PERM_USE = "skyblock.trade.use";

    static final String SETTING_REQUESTS = "trade-requests";

    final Core core;
    final ModuleMessages msg;
    final TradeConfig config = new TradeConfig();
    private TradeEngine engine;
    private TradeItems items;
    private Executor main;
    private final TradeRequests requests = new TradeRequests();
    /** Player → their open trade (both players map to the same session). */
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    /** Players whose trade is being created in the database right now. */
    private final Set<UUID> starting = new java.util.HashSet<>();
    /** Player → last time they hit or were hit by an entity (combat rule). */
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    /** Profiles with items waiting in the escrow. */
    private final Set<UUID> pendingProfiles = ConcurrentHashMap.newKeySet();
    /** Profiles whose delivery runs right now. */
    private final Set<UUID> delivering = new java.util.HashSet<>();
    /** Profiles that got new waiting items while their delivery was running (run it again afterwards). */
    private final Set<UUID> redeliver = new java.util.HashSet<>();
    /** Offers taken from inventories whose escrow write has not finished (logged if the plugin stops in between). */
    private final Map<Long, Offer> inFlight = new ConcurrentHashMap<>();
    private long inFlightSeq;
    /** Trades whose cancel was asked for while the swap was running. */
    private final Map<Long, String> cancelAfterSwap = new HashMap<>();
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    public TradeModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("economy");
    }

    @Override
    public void enable() throws Exception {
        if (core.services().optional(EconomyService.class).isEmpty()) {
            throw new IllegalStateException("EconomyService is not available (economy module disabled?)");
        }
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        main = new MainThread(core.plugin());
        items = new TradeItems(core.items());
        engine = new TradeEngine(new TradeStore(core.database()), this::economy, core.audit(), System::currentTimeMillis,
                core.plugin().getLogger());
        // P4: never between two profiles of the same player (audited as profile_transfer_blocked, path "trade").
        engine.ownProfileCheck((a, b, actor) -> profiles().blockOwnTransfer(AuditCategory.TRADE, "trade", a, b, actor));
        registerSetting();
        registerCommands();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());

        // Crash recovery first: trades still open in the database give every item back; then load who has items waiting.
        engine.recover(System.currentTimeMillis() + 1).exceptionally(e -> {
            core.plugin().getLogger().log(Level.SEVERE, "Trade: startup recovery failed", Database.cause(e));
            return 0;
        }).thenCompose(n -> engine.store().pendingOwners()).whenComplete((owners, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Trade: could not load waiting items", Database.cause(e));
                return;
            }
            pendingProfiles.addAll(owners);
            main.execute(() -> Bukkit.getOnlinePlayers().forEach(p -> deliverPending(p.getUniqueId())));
        });
        tasks.add(core.scheduler().repeat("trade:tick", config.checkTicks, config.checkTicks, this::tick));
        long every = 20L * config.deliverySeconds;
        tasks.add(core.scheduler().repeat("trade:deliver", every, every, this::deliverySweep));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
    }

    @Override
    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        HandlerList.unregisterAll(this);
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
        // Every open trade is cancelled right now; the items wait as pending and are delivered on the next join.
        for (TradeSession s : new LinkedHashSet<>(sessions.values())) {
            s.end("shutdown");
            closeTradeMenu(s.a.player);
            closeTradeMenu(s.b.player);
            try {
                engine.cancelBlocking(s.id, "shutdown", Actor.system("trade-shutdown"), s.a.player, s.a.name, s.b.player, s.b.name);
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.SEVERE, "Trade: cancelling trade " + s.id + " on shutdown failed; it is "
                        + "recovered on the next start", Database.cause(e));
            }
        }
        sessions.clear();
        starting.clear();
        requests.clear();
        for (Offer o : inFlight.values()) {
            core.plugin().getLogger().severe("Trade: an offered item was still being stored when the plugin stopped. If it is "
                    + "missing, restore it from this data: " + o + " " + Base64.getEncoder().encodeToString(o.bytes()));
        }
        inFlight.clear();
    }

    // ================================================================== settings + commands

    private void registerSetting() {
        core.services().optional(PlayerSettings.class).ifPresent(s -> {
            try {
                s.register(new SettingToggle(ID, SETTING_REQUESTS, SettingCategory.SOCIAL, Material.EMERALD, true, null, ID,
                        "settings.requests", false));
            } catch (RuntimeException e) {
                core.plugin().getLogger().warning("Trade: could not register the trade-requests setting: " + e.getMessage());
            }
        });
    }

    private void registerCommands() {
        Arg<Player> target = Arg.word("player", (sender, raw) -> {
            Player p = Bukkit.getPlayerExact(raw);
            if (p == null || !net.skybornmc.skyblock.common.visibility.Visibility.knows(sender, p)) {
                throw CommandFailure.of("player-offline", Msg.p("input", raw));
            }
            return p;
        }, Suggest.bySender(this::nearbyNames));
        Arg<String> acceptFrom = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(this::requesterNames));
        Arg<String> denyFrom = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(this::requesterNames));
        core.commands().register(CommandSpec.command("trade")
                .description("Trade items and money safely with a player nearby")
                .category("Economy")
                .permission(PERM_USE)
                .playerOnly()
                .optional(target)
                .example("trade Steve")
                .executes(ctx -> {
                    Player p = ctx.player();
                    Player t = ctx.getOr(target, null);
                    if (t == null) {
                        openOrPick(p);
                    } else {
                        request(p, t);
                    }
                })
                .sub(CommandSpec.sub("accept")
                        .description("Accept a trade request")
                        .permission(PERM_USE)
                        .playerOnly()
                        .optional(acceptFrom)
                        .example("trade accept Steve")
                        .executes(ctx -> accept(ctx.player(), ctx.getOr(acceptFrom, null))))
                .sub(CommandSpec.sub("deny")
                        .description("Deny a trade request")
                        .permission(PERM_USE)
                        .playerOnly()
                        .optional(denyFrom)
                        .example("trade deny Steve")
                        .executes(ctx -> deny(ctx.player(), ctx.getOr(denyFrom, null)))));
    }

    /** Visible online players close enough to trade (tab completion and the picker). */
    private List<String> nearbyNames(CommandSender sender) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player viewer) {
                if (p.equals(viewer) || !net.skybornmc.skyblock.common.visibility.Visibility.knows(viewer, p) || distanceProblem(viewer, p).isPresent()) {
                    continue;
                }
            }
            out.add(p.getName());
        }
        return out;
    }

    private List<String> requesterNames(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (UUID from : requests.incoming(p.getUniqueId(), now())) {
            Player f = Bukkit.getPlayer(from);
            if (f != null) {
                out.add(f.getName());
            }
        }
        return out;
    }

    // ================================================================== requests

    /** {@code /trade} without a name: back into your open trade, or pick a player nearby. */
    void openOrPick(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            openTradeMenu(p, s);
            return;
        }
        List<UUID> incoming = requests.incoming(p.getUniqueId(), now());
        if (incoming.size() == 1) {
            Player from = Bukkit.getPlayer(incoming.getFirst());
            if (from != null) {
                send(p, "request.hint-accept", Msg.p("player", from.getName()));
            }
        }
        core.menus().open(p, new PlayerPickerMenu(v -> msg.component(v, "picker.title"),
                other -> !other.equals(p) && net.skybornmc.skyblock.common.visibility.Visibility.knows(p, other) && distanceProblem(p, other).isEmpty(),
                (viewer, picked) -> {
                    viewer.closeInventory();
                    try {
                        request(viewer, picked);
                    } catch (CommandFailure f) {
                        core.messages().send(viewer, f.module(), f.key(), f.placeholders());
                        sound(viewer, "error");
                    }
                }));
    }

    void request(Player sender, Player target) {
        // P7 (spec 13.1): blocked trading / frozen balance, on either side
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), sender, net.skybornmc.skyblock.common.moderation.SanctionService.Use.TRADE)
                || net.skybornmc.skyblock.common.moderation.SanctionService.deniedOther(core.services(), sender, target, net.skybornmc.skyblock.common.moderation.SanctionService.Use.TRADE)
                || net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), sender, net.skybornmc.skyblock.common.tutorial.Gates.TRADE)) {
            return; // TUTORIAL §4: /trade opens when the tutorial is finished
        }
        long now = now();
        boolean self = sender.equals(target);
        // Owner answer 316: a player the target /ignores gets the same answer as "requests off" (staff can't be ignored).
        boolean accepts = acceptsRequests(target)
                && !net.skybornmc.skyblock.common.social.IgnoreService.ignoring(core.services(), target, sender);
        Optional<TradeRules.Problem> problem = TradeRules.request(self, state(sender), state(target), accepts,
                requests.has(target.getUniqueId(), sender.getUniqueId(), now) ? 0
                        : requests.cooldownLeft(sender.getUniqueId(), now, config.requestCooldownSeconds * 1000L));
        if (problem.isEmpty() && !self) {
            problem = distanceProblem(sender, target);
        }
        if (problem.isEmpty() && profiles().ownOtherProfile(key(sender), key(target))) {
            profiles().blockOwnTransfer(AuditCategory.TRADE, "trade", key(sender), key(target), Actor.player(sender));
            problem = Optional.of(TradeRules.Problem.OWN_PROFILE);
        }
        if (problem.isPresent()) {
            throw fail("rules." + problem.get().key(), rulePlaceholders(sender, target));
        }
        // They already asked us: this is an accept.
        if (requests.has(target.getUniqueId(), sender.getUniqueId(), now)) {
            requests.remove(target.getUniqueId(), sender.getUniqueId(), now);
            start(target, sender);
            return;
        }
        if (requests.has(sender.getUniqueId(), target.getUniqueId(), now)) {
            throw fail("request.already", Msg.p("player", target.getName()));
        }
        requests.send(sender.getUniqueId(), target.getUniqueId(), now, config.requestTimeoutSeconds * 1000L);
        send(sender, "request.sent", Msg.p("player", target.getName()), Msg.p("seconds", config.requestTimeoutSeconds));
        send(target, "request.received", Msg.p("player", sender.getName()), Msg.p("seconds", config.requestTimeoutSeconds));
        sound(sender, "click");
        sound(target, "notify");
    }

    void accept(Player p, String name) {
        Player from = pickRequester(p, name, "accept");
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), p, net.skybornmc.skyblock.common.moderation.SanctionService.Use.TRADE) || net.skybornmc.skyblock.common.moderation.SanctionService.deniedOther(core.services(), p, from, net.skybornmc.skyblock.common.moderation.SanctionService.Use.TRADE)
                || net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), p, net.skybornmc.skyblock.common.tutorial.Gates.TRADE)) {
            return; // P7: blocked trading / frozen balance, on either side; TUTORIAL §4: locked until the finish
        }
        long now = now();
        Optional<TradeRules.Problem> problem = TradeRules.request(false, state(p), state(from), true, 0);
        if (problem.isEmpty()) {
            problem = distanceProblem(p, from);
        }
        if (problem.isEmpty() && profiles().ownOtherProfile(key(p), key(from))) {
            profiles().blockOwnTransfer(AuditCategory.TRADE, "trade", key(from), key(p), Actor.player(p));
            problem = Optional.of(TradeRules.Problem.OWN_PROFILE);
        }
        if (problem.isPresent()) {
            throw fail("rules." + problem.get().key(), rulePlaceholders(p, from));
        }
        requests.remove(from.getUniqueId(), p.getUniqueId(), now);
        start(from, p);
    }

    void deny(Player p, String name) {
        Player from = pickRequester(p, name, "deny");
        requests.remove(from.getUniqueId(), p.getUniqueId(), now());
        send(p, "request.denied", Msg.p("player", from.getName()));
        send(from, "request.denied-other", Msg.p("player", p.getName()));
        sound(from, "error");
    }

    /** The player whose request {@code /trade accept|deny [name]} means. */
    private Player pickRequester(Player p, String name, String action) {
        List<UUID> incoming = requests.incoming(p.getUniqueId(), now());
        if (incoming.isEmpty()) {
            throw fail("request.none");
        }
        if (name == null) {
            if (incoming.size() > 1) {
                List<String> names = new ArrayList<>();
                incoming.forEach(u -> Optional.ofNullable(Bukkit.getPlayer(u)).ifPresent(pl -> names.add(pl.getName())));
                throw fail("request.which", Msg.p("names", String.join(", ", names)), Msg.p("action", action));
            }
            Player only = Bukkit.getPlayer(incoming.getFirst());
            if (only == null) {
                throw fail("request.none");
            }
            return only;
        }
        for (UUID u : incoming) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null && pl.getName().equalsIgnoreCase(name)) {
                return pl;
            }
        }
        throw fail("request.none-from", Msg.p("player", name));
    }

    TradeRules.PlayerState state(Player p) {
        UUID u = p.getUniqueId();
        return new TradeRules.PlayerState(sessions.containsKey(u) || starting.contains(u), frozen(p), staffMode(p),
                TradeRules.combatLeft(lastCombat.getOrDefault(u, 0L), now(), config.combatSeconds));
    }

    Optional<TradeRules.Problem> distanceProblem(Player a, Player b) {
        Location la = a.getLocation();
        Location lb = b.getLocation();
        boolean sameWorld = la.getWorld() != null && la.getWorld().equals(lb.getWorld());
        return TradeRules.distance(sameWorld, sameWorld ? la.distanceSquared(lb) : 0, config.maxBlocks, config.sameWorld);
    }

    private boolean acceptsRequests(Player target) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(target, SETTING_REQUESTS)).orElse(true);
    }

    private TagResolver[] rulePlaceholders(Player p, Player other) {
        long combat = Math.max(TradeRules.combatLeft(lastCombat.getOrDefault(p.getUniqueId(), 0L), now(), config.combatSeconds),
                TradeRules.combatLeft(lastCombat.getOrDefault(other.getUniqueId(), 0L), now(), config.combatSeconds));
        long cooldown = requests.cooldownLeft(p.getUniqueId(), now(), config.requestCooldownSeconds * 1000L);
        return new TagResolver[]{Msg.p("player", other.getName()), Msg.p("blocks", config.maxBlocks),
                Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, Math.max(combat, cooldown)))))};
    }

    // ================================================================== start

    private void start(Player a, Player b) {
        UUID ua = a.getUniqueId();
        UUID ub = b.getUniqueId();
        requests.forget(ua);
        requests.forget(ub);
        starting.add(ua);
        starting.add(ub);
        TradeSession.Side sa = new TradeSession.Side(ua, key(a), a.getName());
        TradeSession.Side sb = new TradeSession.Side(ub, key(b), b.getName());
        engine.open(sa, sb).whenCompleteAsync((id, error) -> {
            starting.remove(ua);
            starting.remove(ub);
            Player pa = Bukkit.getPlayer(ua);
            Player pb = Bukkit.getPlayer(ub);
            if (error != null || id == null) {
                core.plugin().getLogger().log(Level.SEVERE, "Trade: could not start a trade", Database.cause(error));
                sendIfOnline(pa, "error");
                sendIfOnline(pb, "error");
                return;
            }
            TradeSession s = new TradeSession(id, sa, sb, now());
            // Something changed while the row was written (logout, profile switch, another trade): drop it again.
            if (pa == null || pb == null || sessions.containsKey(ua) || sessions.containsKey(ub)
                    || !key(pa).equals(sa.profile) || !key(pb).equals(sb.profile)) {
                s.end("start-failed");
                engine.cancel(id, "start-failed", Actor.system("trade"), ua, sa.name, ub, sb.name);
                sendIfOnline(pa, "start.failed");
                sendIfOnline(pb, "start.failed");
                return;
            }
            sessions.put(ua, s);
            sessions.put(ub, s);
            send(pa, "start.started", Msg.p("player", pb.getName()));
            send(pb, "start.started", Msg.p("player", pa.getName()));
            openTradeMenu(pa, s);
            openTradeMenu(pb, s);
        }, main);
    }

    // ================================================================== offering items

    /** The player clicked a stack in their own inventory while the trade window is open. */
    void offerFromInventory(Player p, int slot) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null || !s.editable()) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        if (slot < 0 || slot >= inv.getStorageContents().length) {
            return;
        }
        ItemStack stack = inv.getItem(slot);
        Optional<String> problem = items.problem(stack, config);
        if (problem.isPresent()) {
            if (!"no-item".equals(problem.get())) {
                send(p, "items." + problem.get());
                sound(p, "error");
            }
            return;
        }
        if (profiles().isSwitching(p.getUniqueId())) {
            return;
        }
        Offer offer;
        try {
            offer = TradeItems.snapshot(stack);
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Trade: could not serialize " + stack.getType() + " of " + p.getName(), e);
            send(p, "error");
            return;
        }
        if (!s.beginAdd(p.getUniqueId(), config.maxItems)) {
            send(p, "offer.full", Msg.number("max", config.maxItems));
            sound(p, "error");
            return;
        }
        inv.setItem(slot, null); // the item now only exists in this snapshot until the escrow has it
        savePlayer(p); // a crash after the escrow write must not bring the item back with old player data
        long ticket = ++inFlightSeq;
        inFlight.put(ticket, offer);
        UUID uuid = p.getUniqueId();
        UUID profile = s.side(uuid).profile;
        refreshMenus(s);
        String token = UUID.randomUUID().toString();
        engine.escrow(s.id, uuid, profile, offer, token).whenCompleteAsync((row, error) -> {
            if (error == null && row != null) {
                inFlight.remove(ticket);
                escrowed(s, uuid, profile, offer, row);
                return;
            }
            // Unknown outcome (database error): look the row up by its token before deciding, so it is never both
            // in the escrow and back in the inventory.
            core.plugin().getLogger().log(Level.SEVERE, "Trade " + s.id + ": storing an offered item failed", Database.cause(error));
            engine.store().byToken(token).whenCompleteAsync((found, lookupError) -> {
                inFlight.remove(ticket);
                if (lookupError != null) {
                    if (sessions.get(uuid) == s && !s.ended()) {
                        s.failAdd(uuid);
                        cancel(s, "error", Actor.system("trade"), false);
                    }
                    core.plugin().getLogger().log(Level.SEVERE, "Trade " + s.id + ": can't tell whether an offered item was stored. "
                            + "If " + p.getName() + " misses it, restore it from this data (check trade_escrow.write_token = "
                            + token + " first): " + Base64.getEncoder().encodeToString(offer.bytes()), Database.cause(lookupError));
                    sendIfOnline(Bukkit.getPlayer(uuid), "error");
                    return;
                }
                escrowed(s, uuid, profile, offer, found.map(TradeStore.EscrowRow::id));
            }, main);
        }, main);
    }

    /** The escrow write finished: {@code row} is the stored row, or empty when the trade had ended (item goes back). */
    private void escrowed(TradeSession s, UUID uuid, UUID profile, Offer offer, Optional<Long> row) {
        boolean active = sessions.get(uuid) == s && !s.ended();
        if (row.isEmpty()) {
            if (active) {
                s.failAdd(uuid);
            }
            giveBack(s.id, uuid, profile, offer);
            refreshMenus(s);
            return;
        }
        if (active) {
            s.finishAdd(uuid, offer.withRow(row.get()));
            sound(Bukkit.getPlayer(uuid), "click");
            refreshMenus(s);
        } else {
            // Stored, then the trade was cancelled: the cancel made it pending; deliver it.
            pendingProfiles.add(profile);
            deliverPending(uuid);
        }
    }

    /** The player clicked one of their offered items in the window: it goes back to them. */
    void takeBack(Player p, long rowId) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        UUID uuid = p.getUniqueId();
        Optional<Offer> offer = s.beginRemove(uuid, rowId);
        if (offer.isEmpty()) {
            return;
        }
        UUID profile = s.side(uuid).profile;
        refreshMenus(s);
        engine.release(s.id, rowId, profile).whenCompleteAsync((ok, error) -> {
            boolean active = sessions.get(uuid) == s && !s.ended();
            if (error != null || ok == null || !ok) {
                if (error != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Trade " + s.id + ": taking an item back failed", Database.cause(error));
                }
                if (active) {
                    s.failRemove(uuid, offer.get());
                    sendIfOnline(Bukkit.getPlayer(uuid), "error");
                    refreshMenus(s);
                }
                return;
            }
            if (active) {
                s.finishRemove(uuid);
                refreshMenus(s);
            }
            pendingProfiles.add(profile);
            deliverPending(uuid);
        }, main);
    }

    /** Returns an item that left an inventory but was never stored: into the inventory if possible, else pending. */
    private void giveBack(long tradeId, UUID player, UUID profile, Offer offer) {
        Player p = online(profile);
        if (p != null && !staffMode(p)) {
            ItemStack item = TradeItems.decode(offer.bytes());
            Map<Integer, ItemStack> left = p.getInventory().addItem(item);
            overflow(tradeId, player, profile, left.values());
            savePlayer(p);
            return;
        }
        putBack(tradeId, player, profile, offer);
    }

    /** Owner answer 7: what does not fit goes to /box; without the box module it waits in the trade escrow as before. */
    private void overflow(long tradeId, UUID player, UUID profile, java.util.Collection<ItemStack> rest) {
        if (rest.isEmpty()) {
            return;
        }
        Optional<net.skybornmc.skyblock.common.box.ItemBox> box = net.skybornmc.skyblock.common.box.ItemBox.of(core.services());
        if (box.isPresent()) {
            box.get().deliver(profile, rest, net.skybornmc.skyblock.common.box.BoxSource.TRADE, Actor.system("trade"));
        } else {
            rest.forEach(r -> putBack(tradeId, player, profile, r));
        }
    }

    private void putBack(long tradeId, UUID player, UUID profile, ItemStack stack) {
        putBack(tradeId, player, profile, TradeItems.snapshot(stack));
    }

    private void putBack(long tradeId, UUID player, UUID profile, Offer offer) {
        engine.store().putBack(tradeId, player, profile, offer, now()).whenCompleteAsync((id, e) -> {
            if (e == null) {
                pendingProfiles.add(profile);
                return;
            }
            Player p = online(profile);
            Optional<net.skybornmc.skyblock.common.box.ItemBox> box = net.skybornmc.skyblock.common.box.ItemBox.of(core.services());
            if (box.isPresent()) {
                // Owner answer 7: the escrow refused it, the /box keeps it (never dropped).
                box.get().deliver(profile, List.of(TradeItems.decode(offer.bytes())), net.skybornmc.skyblock.common.box.BoxSource.TRADE,
                        Actor.system("trade"));
            } else if (p != null) {
                p.getWorld().dropItemNaturally(p.getLocation(), TradeItems.decode(offer.bytes())); // last resort: never lose it
            } else {
                core.plugin().getLogger().log(Level.SEVERE, "Trade: could not return an item to " + player + ". Restore it from "
                        + "this data: " + Base64.getEncoder().encodeToString(offer.bytes()), Database.cause(e));
            }
        }, main);
    }

    // ================================================================== money

    /** Currencies that can be offered right now. */
    List<Currency> currencies() {
        EconomyService eco = economy();
        return TradeRules.allowedCurrencies(eco::tradeable, config.currencies);
    }

    /** Sets this player's offer in one currency (0 removes it). */
    void setMoney(Player p, Currency currency, long amount) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null || !s.editable()) {
            return;
        }
        TradeSession.Side side = s.side(p.getUniqueId());
        long balance = economy().cached(p.getUniqueId(), currency).orElse(0L);
        Optional<TradeRules.MoneyProblem> problem = TradeRules.money(currencies().contains(currency), amount, side.money(currency),
                balance);
        if (problem.isPresent()) {
            send(p, "money." + problem.get().key(), amountTag("balance", currency, balance), amountTag("amount", currency, amount));
            sound(p, "error");
            return;
        }
        if (s.setMoney(p.getUniqueId(), currency, amount)) {
            sound(p, "money");
            refreshMenus(s);
        }
    }

    // ================================================================== confirm and swap

    void confirm(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        UUID uuid = p.getUniqueId();
        if (!s.side(uuid).ready()) {
            Optional<String> own = moneyShort(p, s.side(uuid));
            if (own.isPresent()) {
                send(p, "confirm.money-short", Msg.p("amount", own.get()));
                sound(p, "error");
                return;
            }
            if (!fitsIncoming(p, s.other(uuid))) {
                send(p, "confirm.no-space");
                sound(p, "error");
                return;
            }
        }
        switch (s.confirm(uuid, now(), config.countdownSeconds * 1000L)) {
            case READY -> {
                sound(p, "success");
                Player other = Bukkit.getPlayer(s.other(uuid).player);
                if (other != null) {
                    send(other, "confirm.other-ready", Msg.p("player", p.getName()));
                    sound(other, "notify");
                }
            }
            case COUNTDOWN -> {
                sound(Bukkit.getPlayer(s.a.player), "notify");
                sound(Bukkit.getPlayer(s.b.player), "notify");
            }
            case BUSY -> {
                send(p, "confirm.busy");
                sound(p, "error");
            }
            case EMPTY -> {
                send(p, "confirm.empty");
                sound(p, "error");
            }
            case ALREADY, LOCKED -> {
                return;
            }
        }
        refreshMenus(s);
    }

    void unconfirm(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null && s.unconfirm(p.getUniqueId())) {
            sound(p, "click");
            refreshMenus(s);
        }
    }

    /** The plain amount this side offers but doesn't have any more (display cache), if any. */
    private Optional<String> moneyShort(Player p, TradeSession.Side side) {
        for (Map.Entry<Currency, Long> e : side.money().entrySet()) {
            long balance = economy().cached(p.getUniqueId(), e.getKey()).orElse(Long.MAX_VALUE);
            if (e.getValue() > balance) {
                return Optional.of(economy().format(e.getKey(), e.getValue()));
            }
        }
        return Optional.empty();
    }

    /** Whether everything the other side offers fits into this player's inventory. */
    private boolean fitsIncoming(Player p, TradeSession.Side other) {
        List<ItemStack> incoming = new ArrayList<>();
        for (Offer o : other.items()) {
            try {
                incoming.add(TradeItems.decode(o.bytes()));
            } catch (RuntimeException e) {
                return false;
            }
        }
        return TradeItems.fitsAll(p.getInventory().getStorageContents(), incoming);
    }

    /** The countdown is over: run the swap. */
    private void finish(TradeSession s) {
        Player pa = Bukkit.getPlayer(s.a.player);
        Player pb = Bukkit.getPlayer(s.b.player);
        if (pa == null || pb == null) {
            cancel(s, "quit", pa == null ? Actor.player(s.a.player, s.a.name) : Actor.player(s.b.player, s.b.name), false);
            return;
        }
        for (Player p : List.of(pa, pb)) {
            TradeSession.Side side = s.side(p.getUniqueId());
            if (!fitsIncoming(p, s.other(p.getUniqueId())) || moneyShort(p, side).isPresent()) {
                s.unconfirm(s.a.player);
                s.unconfirm(s.b.player);
                send(pa, "confirm.stopped", Msg.p("player", p.getName()));
                send(pb, "confirm.stopped", Msg.p("player", p.getName()));
                sound(pa, "error");
                sound(pb, "error");
                refreshMenus(s);
                return;
            }
        }
        if (!s.beginCompleting()) {
            return;
        }
        TradeSession.Snapshot snapshot = s.snapshot();
        refreshMenus(s);
        engine.complete(snapshot, Actor.player(pa)).whenCompleteAsync((r, error) -> {
            TradeEngine.Outcome outcome = error != null || r == null ? TradeEngine.Outcome.ERROR : r.outcome();
            String deferredReason = cancelAfterSwap.remove(s.id);
            switch (outcome) {
                case COMPLETED -> completed(s, snapshot);
                case INSUFFICIENT, OVERFLOW -> {
                    String deferred = deferredReason;
                    Actor by = Actor.system("trade");
                    s.reopen();
                    for (UUID u : List.of(s.a.player, s.b.player)) {
                        Player p = Bukkit.getPlayer(u);
                        if (deferred == null && (p == null || !isTradeView(core.menus().openMenu(p)))) {
                            deferred = p == null ? "quit" : "closed";
                            TradeSession.Side side = s.side(u);
                            by = Actor.player(side.player, side.name);
                        }
                    }
                    Player shortP = r == null || r.shortSide() == null ? null : Bukkit.getPlayer(r.shortSide());
                    String name = shortP == null ? "?" : shortP.getName();
                    String key = outcome == TradeEngine.Outcome.INSUFFICIENT ? "swap.insufficient" : "swap.overflow";
                    for (UUID u : List.of(s.a.player, s.b.player)) {
                        Player p = Bukkit.getPlayer(u);
                        send(p, key, Msg.p("player", name));
                        sound(p, "error");
                    }
                    if (deferred != null) {
                        cancel(s, deferred, by, true);
                    } else {
                        refreshMenus(s);
                    }
                }
                case OWN_PROFILE -> cancel(s, "own-profile", Actor.system("trade"), true);
                case STATE_CHANGED -> cancel(s, "changed", Actor.system("trade"), true);
                case ERROR -> cancel(s, "error", Actor.system("trade"), true);
            }
        }, main);
    }

    private void completed(TradeSession s, TradeSession.Snapshot snap) {
        s.end("completed");
        sessions.remove(s.a.player, s);
        sessions.remove(s.b.player, s);
        closeTradeMenu(s.a.player);
        closeTradeMenu(s.b.player);
        pendingProfiles.add(s.a.profile);
        pendingProfiles.add(s.b.profile);
        for (boolean isA : new boolean[]{true, false}) {
            Player p = Bukkit.getPlayer(isA ? s.a.player : s.b.player);
            if (p == null) {
                continue;
            }
            List<Offer> got = isA ? snap.itemsB() : snap.itemsA();
            Map<Currency, Long> gotMoney = isA ? snap.moneyB() : snap.moneyA();
            send(p, "swap.done", Msg.p("player", isA ? s.b.name : s.a.name), Msg.number("items", got.size()),
                    Msg.p("money", moneySummary(gotMoney)));
            sound(p, "money");
        }
        deliverPending(s.a.player);
        deliverPending(s.b.player);
    }

    private String moneySummary(Map<Currency, Long> money) {
        List<String> parts = new ArrayList<>();
        for (Currency c : Currency.values()) {
            long v = money.getOrDefault(c, 0L);
            if (v > 0) {
                parts.add(economy().format(c, v));
            }
        }
        return parts.isEmpty() ? plain("swap.no-money") : String.join(", ", parts);
    }

    private String plain(String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.component(Bukkit.getConsoleSender(), key));
    }

    // ================================================================== cancel

    /** The player cancels (button, closing the window). */
    void cancelBy(Player p, String reason) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            cancel(s, reason, Actor.player(p), false);
        }
    }

    /**
     * Ends a trade without a swap: every offered item goes back to whoever put it in. While the swap is running the
     * cancel waits for its result ({@code force} = called from that result).
     */
    void cancel(TradeSession s, String reason, Actor actor, boolean force) {
        if (s.ended()) {
            return;
        }
        if (s.state() == TradeSession.State.COMPLETING && !force) {
            cancelAfterSwap.putIfAbsent(s.id, reason);
            return;
        }
        s.end(reason);
        sessions.remove(s.a.player, s);
        sessions.remove(s.b.player, s);
        closeTradeMenu(s.a.player);
        closeTradeMenu(s.b.player);
        for (UUID u : List.of(s.a.player, s.b.player)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                String who = actor.uuid() != null && actor.uuid().equals(u) ? "you" : "other";
                String key = actor.uuid() != null && msg.has("cancel." + reason + "." + who) ? "cancel." + reason + "." + who
                        : msg.has("cancel." + reason) ? "cancel." + reason : "cancel.generic";
                send(p, key, Msg.p("player", actor.name() == null ? "?" : actor.name()));
                sound(p, "error");
            }
        }
        engine.cancel(s.id, reason, actor, s.a.player, s.a.name, s.b.player, s.b.name).whenCompleteAsync((outcome, error) -> {
            if (error != null) {
                // The trade stays open in the database; the next recovery run gives everything back.
                core.plugin().getLogger().log(Level.SEVERE, "Trade " + s.id + ": cancel failed; it is recovered automatically",
                        Database.cause(error));
                return;
            }
            pendingProfiles.add(s.a.profile);
            pendingProfiles.add(s.b.profile);
            deliverPending(s.a.player);
            deliverPending(s.b.player);
        }, main);
    }

    // ================================================================== delivery

    /** Delivers every item waiting for this player's active profile that fits, each exactly once. */
    void deliverPending(UUID player) {
        Player p = Bukkit.getPlayer(player);
        if (p == null || profiles().isSwitching(player) || staffMode(p)) {
            return;
        }
        UUID profile = key(p);
        if (!pendingProfiles.contains(profile)) {
            return;
        }
        if (!delivering.add(profile)) {
            redeliver.add(profile);
            return;
        }
        engine.store().pending(profile).whenCompleteAsync((rows, error) -> {
            if (error != null) {
                doneDelivering(profile);
                core.plugin().getLogger().log(Level.WARNING, "Trade: loading waiting items failed", Database.cause(error));
                return;
            }
            if (rows.isEmpty()) {
                doneDelivering(profile);
                pendingProfiles.remove(profile);
                return;
            }
            deliverNext(profile, rows, 0, 0);
        }, main);
    }

    private void deliverNext(UUID profile, List<TradeStore.EscrowRow> rows, int index, int delivered) {
        Player p = online(profile);
        if (index >= rows.size() || p == null || staffMode(p)) {
            doneDelivering(profile);
            if (p != null && delivered > 0) {
                savePlayer(p);
                send(p, "delivery.received", Msg.number("count", delivered));
                sound(p, "success");
            }
            if (index >= rows.size()) {
                pendingProfiles.remove(profile);
            }
            return;
        }
        TradeStore.EscrowRow row = rows.get(index);
        ItemStack item;
        try {
            item = TradeItems.decode(row.offer().bytes());
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Trade: escrow row " + row.id() + " holds an unreadable item; it stays "
                    + "in the database for staff", e);
            deliverNext(profile, rows, index + 1, delivered);
            return;
        }
        if (!TradeItems.fitsAll(p.getInventory().getStorageContents(), List.of(item))) {
            doneDelivering(profile);
            if (delivered > 0) {
                savePlayer(p);
                send(p, "delivery.received", Msg.number("count", delivered));
            }
            send(p, "delivery.full", Msg.number("count", rows.size() - index));
            sound(p, "error");
            return;
        }
        engine.claim(row, Actor.system("trade")).whenCompleteAsync((ok, error) -> {
            if (error != null) {
                doneDelivering(profile);
                core.plugin().getLogger().log(Level.SEVERE, "Trade: delivering escrow row " + row.id() + " failed. If the row shows "
                        + "'delivered' but the player misses the item, restore it from this data: "
                        + Base64.getEncoder().encodeToString(row.offer().bytes()), Database.cause(error));
                return;
            }
            if (!ok) {
                deliverNext(profile, rows, index + 1, delivered); // already delivered by someone else
                return;
            }
            Player now = online(profile);
            if (now == null || staffMode(now)) {
                putBack(row.tradeId(), row.ownerPlayer(), profile, row.offer());
                doneDelivering(profile);
                return;
            }
            Map<Integer, ItemStack> left = now.getInventory().addItem(item);
            overflow(row.tradeId(), row.ownerPlayer(), profile, left.values());
            deliverNext(profile, rows, index + 1, delivered + 1);
        }, main);
    }

    /** A delivery run ended; runs again if new items arrived meanwhile. */
    private void doneDelivering(UUID profile) {
        delivering.remove(profile);
        if (redeliver.remove(profile)) {
            core.scheduler().later("trade:redeliver", 1, () -> {
                Player p = online(profile);
                if (p != null) {
                    pendingProfiles.add(profile);
                    deliverPending(p.getUniqueId());
                }
            });
        }
    }

    private void deliverySweep() {
        // Trades whose cancel failed (database error) are still open in the database: give their items back now.
        Set<Long> active = new java.util.HashSet<>();
        sessions.values().forEach(s -> active.add(s.id));
        engine.cancelOrphans(active, 120_000L).whenCompleteAsync((cancelled, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Trade: orphan check failed", Database.cause(e));
                return;
            }
            for (TradeStore.CancelOutcome outcome : cancelled) {
                for (TradeStore.EscrowRow row : outcome.returned()) {
                    pendingProfiles.add(row.ownerProfile());
                }
            }
        }, main);
        if (pendingProfiles.isEmpty()) {
            return; // PERF2 (P-35): nothing waits, no profile lookup per online player
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (pendingProfiles.contains(key(p))) {
                deliverPending(p.getUniqueId());
            }
        }
    }

    // ================================================================== guards (every tasks.check-ticks)

    private void tick() {
        long now = now();
        for (UUID[] expired : requests.prune(now)) {
            Player from = Bukkit.getPlayer(expired[0]);
            Player to = Bukkit.getPlayer(expired[1]);
            if (from != null) {
                send(from, "request.expired", Msg.p("player", to == null ? "?" : to.getName()));
            }
        }
        for (TradeSession s : new LinkedHashSet<>(sessions.values())) {
            if (s.ended() || s.state() == TradeSession.State.COMPLETING) {
                continue;
            }
            Player pa = Bukkit.getPlayer(s.a.player);
            Player pb = Bukkit.getPlayer(s.b.player);
            if (pa == null || pb == null) {
                cancel(s, "quit", pa == null ? Actor.player(s.a.player, s.a.name) : Actor.player(s.b.player, s.b.name), false);
                continue;
            }
            Player frozen = frozen(pa) ? pa : frozen(pb) ? pb : null;
            if (frozen != null) {
                cancel(s, "frozen", Actor.player(frozen), false);
                continue;
            }
            Player staff = staffMode(pa) ? pa : staffMode(pb) ? pb : null;
            if (staff != null) {
                cancel(s, "staff-mode", Actor.player(staff), false);
                continue;
            }
            if (!key(pa).equals(s.a.profile) || !key(pb).equals(s.b.profile)) {
                cancel(s, "profile", Actor.system("trade"), false);
                continue;
            }
            Location la = pa.getLocation();
            Location lb = pb.getLocation();
            boolean sameWorld = la.getWorld() != null && la.getWorld().equals(lb.getWorld());
            if (TradeRules.tooFarDuringTrade(sameWorld, sameWorld ? la.distanceSquared(lb) : 0, config.cancelBlocks, config.sameWorld,
                    config.maxBlocks)) {
                cancel(s, "distance", Actor.system("trade"), false);
                continue;
            }
            if (now - s.createdAt > config.maxMinutes * 60_000L) {
                cancel(s, "timeout", Actor.system("trade"), false);
                continue;
            }
            if (s.state() == TradeSession.State.COUNTDOWN) {
                if (s.countdownDone(now)) {
                    finish(s);
                } else {
                    int left = s.secondsLeft(now);
                    for (Player p : List.of(pa, pb)) {
                        if (core.menus().openMenu(p) instanceof TradeMenu menu && menu.shownSeconds() != left) {
                            menu.refresh();
                            sound(p, "click");
                        }
                    }
                }
            }
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof TradeMenu) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked == top || !(clicked instanceof PlayerInventory)) {
            return;
        }
        event.setCancelled(true); // the menu framework already cancels; never let an item move by itself
        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            return;
        }
        long now = now();
        Long last = lastInventoryClick.get(p.getUniqueId());
        if (last != null && now - last < 150) {
            return;
        }
        lastInventoryClick.put(p.getUniqueId(), now);
        offerFromInventory(p, event.getSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p) || !isTradeView(event.getInventory().getHolder(false))) {
            return;
        }
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        // One tick later: a new trade view (money picker, page) or a chat prompt for an amount is not a close.
        core.scheduler().later("trade:close", 1, () -> {
            if (sessions.get(p.getUniqueId()) != s || !p.isOnline() || s.state() == TradeSession.State.COMPLETING) {
                return;
            }
            if (isTradeView(core.menus().openMenu(p)) || core.menus().hasPrompt(p)) {
                return;
            }
            cancel(s, "closed", Actor.player(p), false);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        requests.forget(u);
        lastCombat.remove(u);
        lastInventoryClick.remove(u);
        TradeSession s = sessions.get(u);
        if (s != null) {
            cancel(s, "quit", Actor.player(event.getPlayer()), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        long now = now();
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (damager instanceof Player attacker) {
                lastCombat.put(attacker.getUniqueId(), now);
                cancelOnDamage(attacker);
            }
            if (event.getEntity() instanceof Player victim) {
                lastCombat.put(victim.getUniqueId(), now);
            }
        }
        if (event.getEntity() instanceof Player victim) {
            cancelOnDamage(victim);
        }
    }

    private void cancelOnDamage(Player p) {
        if (!config.cancelOnDamage) {
            return;
        }
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            cancel(s, "damage", Actor.player(p), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!config.cancelOnTeleport) {
            return;
        }
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) {
            cancel(s, "teleport", Actor.player(event.getPlayer()), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProfileSwitch(ProfileSwitchEvent event) {
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) {
            cancel(s, "profile", Actor.player(event.getPlayer()), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        engine.store().pending(key(event.getPlayer())).whenCompleteAsync((rows, e) -> {
            if (e == null && !rows.isEmpty()) {
                pendingProfiles.add(rows.getFirst().ownerProfile());
                deliverPending(u);
            }
        }, main);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID u = event.getPlayer().getUniqueId();
        core.scheduler().later("trade:join", config.joinDelayTicks, () -> {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                return;
            }
            UUID profile = key(p);
            engine.store().pending(profile).whenCompleteAsync((rows, e) -> {
                if (e == null && !rows.isEmpty()) {
                    pendingProfiles.add(profile);
                    deliverPending(u);
                }
            }, main);
        });
    }

    // ================================================================== menus

    void openTradeMenu(Player p, TradeSession s) {
        if (sessions.get(p.getUniqueId()) == s && !s.ended()) {
            core.menus().open(p, new TradeMenu(this, s));
        }
    }

    /** Opens the money menu (or the picker directly when only one currency can be offered). */
    void openMoney(Player p, Menu from) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null || !s.editable()) {
            return;
        }
        List<Currency> list = currencies();
        if (list.isEmpty()) {
            send(p, "money.none");
            sound(p, "error");
            return;
        }
        if (list.size() == 1) {
            from.openChild(TradeMoneyMenu.picker(this, s, p, list.getFirst()));
        } else {
            from.openChild(new TradeMoneyMenu(this, s));
        }
    }

    /** True for the trade window and menus opened from it (money). */
    static boolean isTradeView(Object holder) {
        Menu m = holder instanceof Menu menu ? menu : null;
        int depth = 0;
        while (m != null && depth++ < 5) {
            if (m instanceof TradeMenu) {
                return true;
            }
            m = m.parent();
        }
        return false;
    }

    private void closeTradeMenu(UUID player) {
        Player p = Bukkit.getPlayer(player);
        if (p != null && isTradeView(core.menus().openMenu(p))) {
            p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    void refreshMenus(TradeSession s) {
        for (UUID u : List.of(s.a.player, s.b.player)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && core.menus().openMenu(p) instanceof TradeMenu menu && menu.session() == s) {
                menu.refresh();
            }
        }
    }

    TradeSession session(UUID player) {
        return sessions.get(player);
    }

    // ================================================================== helpers

    /**
     * Writes the player's data file now ({@code trade.yml → safety.save-player-data}), right after items left or entered
     * the inventory, so a server crash can't restore an older inventory next to the escrow (dupe) or without a delivery.
     */
    private void savePlayer(Player p) {
        if (!config.savePlayerData || p == null || !p.isOnline()) {
            return;
        }
        try {
            p.saveData();
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Trade: could not save " + p.getName() + "'s player data", e);
        }
    }

    EconomyService economy() {
        return core.services().optional(EconomyService.class).orElseThrow(() -> new IllegalStateException("economy is gone"));
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    UUID key(Player player) {
        return profiles().profileKey(player.getUniqueId());
    }

    /** The online player whose ACTIVE profile is {@code profile} and who is not switching, or null. */
    Player online(UUID profile) {
        return profiles().onlinePlayer(profile).filter(p -> !profiles().isSwitching(p.getUniqueId())).orElse(null);
    }

    boolean frozen(Player player) {
        return flag(player, "frozen");
    }

    boolean staffMode(Player player) {
        return flag(player, "staff_mode");
    }

    private boolean flag(Player player, String placeholder) {
        return player != null && core.placeholders().has(placeholder)
                && core.placeholders().text(player, placeholder).map("true"::equalsIgnoreCase).orElse(false);
    }

    TagResolver amountTag(String name, Currency currency, long amount) {
        return Msg.p(name, economy().format(currency, amount));
    }

    static long now() {
        return System.currentTimeMillis();
    }

    void send(CommandSender to, String key, TagResolver... placeholders) {
        if (to != null) {
            msg.send(to, key, placeholders);
        }
    }

    private void sendIfOnline(Player p, String key) {
        if (p != null) {
            send(p, key);
            sound(p, "error");
        }
    }

    void sound(Player player, String key) {
        if (player != null) {
            core.sounds().play(player, key);
        }
    }

    private CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(ID, key, placeholders);
    }
}
