package net.skybornmc.skyblock.profiles;

import net.kyori.adventure.audience.Audience;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.ProfileDeleteEvent;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileNames;
import net.skybornmc.skyblock.common.profile.ProfileRestoredEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.profile.ProfileWipeResult;
import net.skybornmc.skyblock.common.profile.ProfileWipedEvent;
import net.skybornmc.skyblock.common.profile.SlotRules;
import net.skybornmc.skyblock.common.profile.SlotSource;
import net.skybornmc.skyblock.common.profile.WipeReason;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * The {@link ProfileService}: active profile per online player, the profile key → owner map, switching (save current
 * state → load target state → teleport), create/rename/icon/delete, credit-bought slots and per-profile playtime.
 *
 * <h2>Where a profile's state lives</h2>
 * The <b>active</b> profile's inventory/ender chest/XP live in the normal player file (as before profiles). Inactive
 * profiles live in {@code profile_states}. A switch saves the current state and makes the target active in ONE
 * transaction; the target's state is applied in game afterwards and then its row is removed. A row for the active
 * profile therefore means "not applied yet" and is applied on the next join, so a crash at any moment can neither
 * duplicate nor lose a profile's items.
 */
final class ProfileServiceImpl implements ProfileService, Listener {

    private static final long LOGIN_TIMEOUT_SECONDS = 5;
    private static final long AUTOSAVE_TICKS = 20L * 60;

    /** Online player's profile data. Mutated on the main thread only. */
    static final class Session {
        volatile UUID active;
        volatile List<Profile> profiles;
        volatile List<SlotRules.Unlock> unlocks;
        volatile long checkpoint;
        volatile long lastSwitch;
        volatile boolean switching;

        Session(ProfileStore.Account account, long now) {
            this.active = account.active();
            this.profiles = List.copyOf(account.profiles());
            this.unlocks = List.copyOf(account.unlocks());
            this.checkpoint = now;
        }

        Optional<Profile> profile(UUID id) {
            return profiles.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        Optional<Profile> inSlot(int slot) {
            return profiles.stream().filter(p -> p.slot() == slot).findFirst();
        }
    }

    private final ProfilesModule module;
    private final Core core;
    private final ProfileStore store;
    private final Executor main;
    private final Map<UUID, UUID> keyOwner = new ConcurrentHashMap<>();
    private final Set<UUID> deleted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ProfileStore.Account> loginLoads = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    /** Players whose switch transaction is running (they can't log in again until it finished). */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private net.skybornmc.skyblock.common.util.Scheduler.Task autosaveTask;
    private net.skybornmc.skyblock.common.util.Scheduler.Task purgeTask;
    private net.skybornmc.skyblock.common.util.Scheduler.Task sweepTask;

    ProfileServiceImpl(ProfilesModule module, ProfileStore store, Executor main) {
        this.module = module;
        this.core = module.core;
        this.store = store;
        this.main = main;
    }

    ProfilesConfig config() {
        return module.config;
    }

    // ================================================================== lifecycle

    /** Loads the key map (blocking, startup) and every online player (plugin reload). */
    void start() {
        for (ProfileStore.KeyRow row : core.database().blocking(store::allKeys)) {
            remember(row.id(), row.owner(), row.deleted());
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                ProfileStore.Account account = core.database().blockingTransaction(c -> store.loadOrCreate(c, player.getUniqueId(), now));
                attach(player, account, now);
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not load " + player.getName(), Database.cause(e));
            }
        }
        autosaveTask = core.scheduler().repeat("profiles:playtime", AUTOSAVE_TICKS, AUTOSAVE_TICKS, this::autosave);
        // 4 Nov 2026: expired wipe snapshots are deleted in the background; interrupted wipes are finished.
        long purgeTicks = 20L * 60 * config().purgeIntervalMinutes;
        purgeTask = core.scheduler().repeat("profiles:snapshot-purge", 20L * 90, purgeTicks, this::purgeExpired);
        long sweepTicks = 20L * config().intentSweepSeconds;
        sweepTask = core.scheduler().repeat("profiles:wipe-sweep", sweepTicks, sweepTicks, this::sweepIntents);
    }

    /** Saves playtime of everyone online (blocking, shutdown). */
    void stop() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        List<Object[]> rows = playtimeRows(System.currentTimeMillis(), true);
        if (!rows.isEmpty() && core.database().isRunning()) {
            try {
                core.database().blockingTransaction(c -> {
                    store.addPlaytime(c, rows);
                    return null;
                });
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Profiles: could not save playtime at shutdown", Database.cause(e));
            }
        }
        sessions.clear();
    }

    private void remember(UUID id, UUID owner, boolean isDeleted) {
        keyOwner.put(id, owner);
        if (isDeleted) {
            deleted.add(id);
        } else {
            deleted.remove(id);
        }
    }

    private void remember(ProfileStore.Account account) {
        for (Profile p : account.profiles()) {
            remember(p.id(), p.owner(), false);
        }
    }

    // ================================================================== login / join / quit

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        if (inFlight.contains(uuid)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, module.msg.component(Audience.empty(), "login.switching"));
            return;
        }
        try {
            ProfileStore.Account account = core.database().transaction(c -> store.loadOrCreate(c, uuid, System.currentTimeMillis()))
                    .get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            remember(account);
            loginLoads.put(uuid, account);
        } catch (TimeoutException e) {
            core.plugin().getLogger().warning("Profiles of " + event.getName() + " took longer than " + LOGIN_TIMEOUT_SECONDS + " s to load.");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, module.msg.component(Audience.empty(), "login.failed"));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            core.plugin().getLogger().log(Level.SEVERE, "Profiles of " + event.getName() + " could not be loaded", Database.cause(e));
            // Without the active profile the player's items could end up on the wrong profile: never join without it.
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, module.msg.component(Audience.empty(), "login.failed"));
        }
    }

    /** LOWEST: every other module's join handler already sees the right profile key. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ProfileStore.Account account = loginLoads.remove(player.getUniqueId());
        if (account == null) {
            core.plugin().getLogger().severe("Profiles: no profile data for " + player.getName() + " at join; kicking to protect their items.");
            Bukkit.getScheduler().runTask(core.plugin(), () -> player.kick(module.msg.component(player, "login.failed")));
            return;
        }
        attach(player, account, System.currentTimeMillis());
    }

    private void attach(Player player, ProfileStore.Account account, long now) {
        Session session = new Session(account, now);
        sessions.put(player.getUniqueId(), session);
        UUID owner = player.getUniqueId();
        switch (WipeRules.joinStep(account.active(), account.capture(), account.pending() != null)) {
            case NOTHING -> {
                // the player file is the active profile
            }
            case APPLY_PENDING -> applyPending(player, account.active(), account.pending());
            case DROP_CAPTURE -> dropCapture(owner, account.capture());
            case DROP_CAPTURE_THEN_APPLY -> {
                dropCapture(owner, account.capture());
                applyPending(player, account.active(), account.pending());
            }
            case CAPTURE_THEN_APPLY -> captureThenApply(player, session, account);
        }
        // Wipes that happened while the player was offline (kick, disband, staff): a chat summary once they are in.
        core.scheduler().later("profiles:wipe-notices", 60L, () -> sendWipeNotices(player));
    }

    /** A switch (or wipe/restore) finished in the database but was not applied in game (crash or quit): apply it now. */
    private void applyPending(Player player, UUID active, StoredState pending) {
        StateCodec.apply(player, pending);
        player.saveData();
        core.database().run(c -> store.consumeState(c, player.getUniqueId(), active)).exceptionally(e -> {
            core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not clear the applied state of " + player.getName(), Database.cause(e));
            return null;
        });
        core.audit().logPlayer(AuditCategory.AUTH, "profile_switch_resumed", Actor.system("profiles"), player.getUniqueId(),
                player.getName(), null, player.getLocation(), Map.of("profile", active.toString()));
        Bukkit.getScheduler().runTask(core.plugin(), () -> teleportToProfile(player, active));
    }

    private void dropCapture(UUID owner, UUID profileId) {
        core.database().run(c -> store.dropCapture(c, owner, profileId)).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Profiles: could not drop a stale capture row of " + owner, Database.cause(e));
            return null;
        });
    }

    /**
     * 4 Nov 2026: the player was wiped (or restored away) while offline, so their player file still holds the items of a
     * profile that is no longer active. They stay locked while those items are saved to that profile (its snapshot); only
     * after the commit the active profile's state is applied. A crash in between repeats this on the next join; nothing is
     * ever on two profiles.
     */
    private void captureThenApply(Player player, Session session, ProfileStore.Account account) {
        UUID owner = player.getUniqueId();
        UUID capturedFor = account.capture();
        session.switching = true;
        StoredState fileState = StateCodec.capture(player);
        int items = WipeRules.clampItems(WipeRules.countItems(player.getInventory().getContents())
                + WipeRules.countItems(player.getEnderChest().getContents()));
        long now = System.currentTimeMillis();
        core.database().transaction(c -> store.consumeCapture(c, owner, capturedFor, fileState, items, now))
                .whenCompleteAsync((ok, error) -> {
                    if (sessions.get(owner) != session || !player.isOnline()) {
                        return; // quit meanwhile: the file is unchanged; a committed capture makes the next join apply the state
                    }
                    if (error != null) {
                        core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not save the items of wiped profile " + capturedFor
                                + " of " + player.getName() + "; kicked to keep them safe", Database.cause(error));
                        player.kick(module.msg.component(player, "login.failed"));
                        return;
                    }
                    StoredState apply = account.pending() != null ? account.pending() : Boolean.TRUE.equals(ok) ? StoredState.FRESH : null;
                    if (apply != null) {
                        applyPending(player, account.active(), apply);
                    }
                    session.switching = false;
                    core.audit().logPlayer(AuditCategory.AUTH, "profile_wipe_items_taken", Actor.system("profiles"), owner, player.getName(),
                            (long) items, player.getLocation(), Map.of("profile", capturedFor.toString(), "active", account.active().toString(),
                                    "saved", Boolean.TRUE.equals(ok)));
                }, main);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loginLoads.remove(uuid);
        Session session = sessions.remove(uuid);
        if (session == null || session.switching) {
            return; // a running switch saves the playtime itself
        }
        long now = System.currentTimeMillis();
        long delta = Math.max(0, now - session.checkpoint);
        UUID active = session.active;
        core.database().run(c -> store.addPlaytime(c, java.util.Collections.singletonList(new Object[]{delta, now, active})))
                .exceptionally(e -> {
                    core.plugin().getLogger().warning("Profiles: could not save playtime: " + Database.cause(e).getMessage());
                    return null;
                });
    }

    private void autosave() {
        List<Object[]> rows = playtimeRows(System.currentTimeMillis(), false);
        if (rows.isEmpty()) {
            return;
        }
        core.database().run(c -> store.addPlaytime(c, rows)).exceptionally(e -> {
            core.plugin().getLogger().warning("Profiles: playtime autosave failed: " + Database.cause(e).getMessage());
            return null;
        });
    }

    private List<Object[]> playtimeRows(long now, boolean all) {
        List<Object[]> rows = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (s.switching && !all) {
                continue;
            }
            long delta = Math.max(0, now - s.checkpoint);
            s.checkpoint = now;
            UUID active = s.active;
            s.profiles = s.profiles.stream().map(p -> p.id().equals(active) ? p.withPlay(now, p.playtimeMillis() + delta) : p).toList();
            rows.add(new Object[]{delta, now, active});
        }
        return rows;
    }

    // ================================================================== ProfileService

    Optional<Session> session(UUID player) {
        return Optional.ofNullable(sessions.get(player));
    }

    @Override
    public UUID profileKey(UUID player) {
        Session s = sessions.get(player);
        return s == null ? player : s.active;
    }

    @Override
    public CompletableFuture<UUID> profileKeyAsync(UUID player) {
        Session s = sessions.get(player);
        if (s != null) {
            return CompletableFuture.completedFuture(s.active);
        }
        // QUICK1 (PERFORMANCE P-03): during pre-login the account was just loaded by this module (NORMAL priority), so
        // later pre-login handlers (skills) get the active profile without a second database round trip.
        ProfileStore.Account loading = loginLoads.get(player);
        if (loading != null) {
            return CompletableFuture.completedFuture(loading.active());
        }
        return core.database().async(c -> store.activeProfile(c, player).orElse(player));
    }

    @Override
    public UUID ownerOf(UUID profileKey) {
        return profileKey == null ? null : keyOwner.getOrDefault(profileKey, profileKey);
    }

    @Override
    public boolean isLive(UUID profileKey) {
        return profileKey != null && !deleted.contains(profileKey);
    }

    @Override
    public Optional<Profile> active(UUID player) {
        Session s = sessions.get(player);
        return s == null ? Optional.empty() : s.profile(s.active);
    }

    @Override
    public List<Profile> profiles(UUID player) {
        Session s = sessions.get(player);
        return s == null ? List.of() : s.profiles;
    }

    @Override
    public CompletableFuture<List<Profile>> loadProfiles(UUID player) {
        Session s = sessions.get(player);
        if (s != null) {
            return CompletableFuture.completedFuture(s.profiles);
        }
        return core.database().async(c -> store.liveProfiles(c, player));
    }

    @Override
    public String activeName(Player player) {
        return active(player.getUniqueId()).map(this::name).orElse("");
    }

    /** The shown name of a profile: the player's own name for it, or "Profile <slot>". */
    String name(Profile profile) {
        return profile.name() != null ? profile.name()
                : core.messages().raw(ProfilesModule.ID, "profile.default-name").replace("<slot>", String.valueOf(profile.slot()));
    }

    /** Playtime of a profile including the running session when it is the active one. */
    long playtime(UUID player, Profile profile) {
        Session s = sessions.get(player);
        if (s != null && profile.id().equals(s.active)) {
            return profile.playtimeMillis() + Math.max(0, System.currentTimeMillis() - s.checkpoint);
        }
        return profile.playtimeMillis();
    }

    @Override
    public boolean isSwitching(UUID player) {
        Session s = sessions.get(player);
        return s != null && s.switching;
    }

    @Override
    public boolean blockOwnTransfer(AuditCategory category, String path, UUID fromKey, UUID toKey, Actor actor) {
        if (!ownOtherProfile(fromKey, toKey)) {
            return false;
        }
        UUID owner = ownerOf(fromKey);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", path);
        details.put("from_profile", fromKey.toString());
        details.put("to_profile", toKey.toString());
        core.audit().logPlayer(category, AUDIT_BLOCKED, actor == null ? Actor.SYSTEM : actor, owner,
                core.players().get(owner).map(PlayerData::name).orElse(null), null, null, details);
        return true;
    }

    // ================================================================== slots

    boolean slotUnlocked(Player player, int slot) {
        Session s = sessions.get(player.getUniqueId());
        return s != null && SlotRules.unlocked(slot, s.unlocks, n -> player.hasPermission(SlotRules.rankPermission(n)));
    }

    Optional<SlotSource> slotSource(Player player, int slot) {
        Session s = sessions.get(player.getUniqueId());
        return s == null ? Optional.empty() : SlotRules.source(slot, s.unlocks, n -> player.hasPermission(SlotRules.rankPermission(n)));
    }

    /**
     * Buys a slot with credits (earned first, then bought; C1). Atomic: the payment has an idempotency key per player and
     * slot, so it is paid once.
     */
    void buySlot(Player player, int slot, Runnable after) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            module.fail(player, "error");
            return;
        }
        long price = config().price(slot);
        Optional<SlotRules.BuyProblem> problem = SlotRules.canBuy(slot, s.unlocks,
                n -> player.hasPermission(SlotRules.rankPermission(n)), price);
        if (problem.isPresent()) {
            module.fail(player, "slots." + switch (problem.get()) {
                case ALREADY_UNLOCKED -> "already";
                case NOT_FOR_SALE -> "not-for-sale";
                default -> "invalid";
            });
            return;
        }
        Optional<EconomyService> eco = core.services().optional(EconomyService.class);
        if (eco.isEmpty()) {
            module.fail(player, "slots.unavailable");
            return;
        }
        UUID owner = player.getUniqueId();
        Transaction tx = Transaction.withdraw(AccountRef.player(owner), Currency.CREDITS, price, "profile_slot")
                .actor(Actor.player(player)).reference("profile_slot:" + slot)
                .idempotencyKey("profile_slot:" + owner + ":" + slot).detail("slot", slot).detail("target_name", player.getName());
        net.skybornmc.skyblock.common.economy.CreditSpend.execute(eco.get(), tx, null).thenCompose(result -> {
            if (result.status() == TxResult.Status.SUCCESS || result.status() == TxResult.Status.DUPLICATE) {
                // DUPLICATE = paid earlier but the unlock was not saved (crash): give the slot now, never charge twice.
                return core.database().transaction(c -> {
                    store.addUnlock(c, owner, slot, SlotSource.CREDITS, 0, System.currentTimeMillis());
                    return store.unlocks(c, owner);
                }).thenApply(unlocks -> new Object[]{result, unlocks});
            }
            return CompletableFuture.completedFuture(new Object[]{result, null});
        }).whenCompleteAsync((pair, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: buying slot " + slot + " for " + player.getName() + " failed",
                        Database.cause(error));
                module.fail(player, "error");
                return;
            }
            TxResult result = (TxResult) pair[0];
            @SuppressWarnings("unchecked")
            List<SlotRules.Unlock> unlocks = (List<SlotRules.Unlock>) pair[1];
            if (unlocks != null) {
                Session live = sessions.get(owner);
                if (live != null) {
                    live.unlocks = List.copyOf(unlocks);
                }
                core.audit().logPlayer(AuditCategory.AUTH, "profile_slot_unlock", Actor.player(player), owner, player.getName(), price,
                        player.getLocation(), Map.of("slot", slot, "source", SlotSource.CREDITS.key(), "status", result.status().name()));
                if (player.isOnline()) {
                    module.msg.send(player, "slots.bought", Msg.number("slot", slot), Msg.p("price", Format.number(price)));
                    core.sounds().play(player, "unlock");
                }
                after.run();
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            if (result.status() == TxResult.Status.INSUFFICIENT_FUNDS) {
                long have = net.skybornmc.skyblock.common.economy.CreditView.cachedShown(eco.get(), owner, Currency.CREDITS).orElse(0);
                module.fail(player, "slots.not-enough", Msg.p("price", Format.number(price)), Msg.p("have", Format.number(have)));
            } else {
                module.fail(player, "error");
            }
        }, main);
    }

    /** Owner decision P8 (for the season reset, Phase 10): drops every stored slot unlock that was not bought with credits. */
    @Override
    public CompletableFuture<Integer> seasonResetSlots(Actor actor) {
        return core.database().transaction(store::seasonReset).thenApply(removed -> {
            core.audit().log(AuditCategory.AUTH, "profile_slots_season_reset", actor, Map.of("removed", removed));
            return removed;
        });
    }

    // ================================================================== create / rename / icon / delete

    void create(Player player, int slot, Runnable after) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            module.fail(player, "error");
            return;
        }
        if (!slotUnlocked(player, slot)) {
            module.fail(player, "create.locked");
            return;
        }
        if (s.inSlot(slot).isPresent()) {
            module.fail(player, "create.used");
            return;
        }
        UUID owner = player.getUniqueId();
        core.database().transaction(c -> {
            Profile created = store.create(c, owner, slot, System.currentTimeMillis());
            return new Object[]{created, store.liveProfiles(c, owner)};
        }).whenCompleteAsync((pair, error) -> {
            if (error != null) {
                refusedOrError(player, "create", error);
                return;
            }
            Profile created = (Profile) pair[0];
            @SuppressWarnings("unchecked")
            List<Profile> live = (List<Profile>) pair[1];
            remember(created.id(), owner, false);
            Session current = sessions.get(owner);
            if (current != null) {
                current.profiles = List.copyOf(live);
            }
            core.audit().logPlayer(AuditCategory.AUTH, "profile_create", Actor.player(player), owner, player.getName(), null,
                    player.getLocation(), Map.of("profile", created.id().toString(), "slot", slot));
            if (player.isOnline()) {
                module.msg.send(player, "create.done", Msg.p("profile", name(created)));
                core.sounds().play(player, "success");
            }
            after.run();
        }, main);
    }

    void rename(Player player, UUID profileId, String raw, Runnable after) {
        String clean = ProfileNames.clean(raw);
        Optional<ProfileNames.Problem> problem = ProfileNames.check(clean, config().nameMaxLength);
        if (problem.isPresent()) {
            module.fail(player, "rename." + problem.get().key, Msg.number("max", config().nameMaxLength));
            return;
        }
        UUID owner = player.getUniqueId();
        core.database().transaction(c -> {
            store.rename(c, owner, profileId, clean);
            return store.liveProfiles(c, owner);
        }).whenCompleteAsync((live, error) -> {
            if (error != null) {
                refusedOrError(player, "rename", error);
                return;
            }
            updateProfiles(owner, live);
            core.audit().logPlayer(AuditCategory.AUTH, "profile_rename", Actor.player(player), owner, player.getName(), null,
                    player.getLocation(), Map.of("profile", profileId.toString(), "name", clean));
            if (player.isOnline()) {
                module.msg.send(player, "rename.done", Msg.p("profile", clean));
                core.sounds().play(player, "success");
            }
            after.run();
        }, main);
    }

    void setIcon(Player player, UUID profileId, Material icon, Runnable after) {
        UUID owner = player.getUniqueId();
        String key = icon.getKey().getKey();
        core.database().transaction(c -> {
            store.setIcon(c, owner, profileId, key);
            return store.liveProfiles(c, owner);
        }).whenCompleteAsync((live, error) -> {
            if (error != null) {
                refusedOrError(player, "icon", error);
                return;
            }
            updateProfiles(owner, live);
            core.audit().logPlayer(AuditCategory.AUTH, "profile_icon", Actor.player(player), owner, player.getName(), null,
                    player.getLocation(), Map.of("profile", profileId.toString(), "icon", key));
            if (player.isOnline()) {
                core.sounds().play(player, "success");
            }
            after.run();
        }, main);
    }

    /** Deletes an inactive profile after the typed confirmation (owner P9: no cooldown). */
    void delete(Player player, UUID profileId) {
        Session s = sessions.get(player.getUniqueId());
        Optional<Profile> profile = s == null ? Optional.empty() : s.profile(profileId);
        if (profile.isEmpty()) {
            module.fail(player, "delete.gone");
            return;
        }
        if (profileId.equals(s.active)) {
            module.fail(player, "delete.active");
            return;
        }
        if (s.switching) {
            module.fail(player, "switch.busy");
            return;
        }
        ProfileDeleteEvent event = new ProfileDeleteEvent(player, profile.get());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (event.reason() != null) {
                player.sendMessage(event.reason());
            } else {
                module.msg.send(player, "delete.refused");
            }
            core.sounds().play(player, "error");
            return;
        }
        UUID owner = player.getUniqueId();
        core.database().transaction(c -> {
            Profile gone = store.delete(c, owner, profileId, System.currentTimeMillis());
            return new Object[]{gone, store.liveProfiles(c, owner)};
        }).whenCompleteAsync((pair, error) -> {
            if (error != null) {
                refusedOrError(player, "delete", error);
                return;
            }
            Profile gone = (Profile) pair[0];
            @SuppressWarnings("unchecked")
            List<Profile> live = (List<Profile>) pair[1];
            remember(gone.id(), owner, true);
            updateProfiles(owner, live);
            core.audit().logPlayer(AuditCategory.AUTH, "profile_delete", Actor.player(player), owner, player.getName(), null,
                    player.getLocation(), Map.of("profile", gone.id().toString(), "slot", gone.slot(), "name", name(gone)));
            Bukkit.getPluginManager().callEvent(new ProfileDeletedEvent(gone));
            if (player.isOnline()) {
                module.msg.send(player, "delete.done", Msg.p("profile", name(gone)));
                core.sounds().play(player, "success");
            }
        }, main);
    }

    // ================================================================== staff: info and reset (feedback 31 Oct 2026)

    /** {@code /profiles info <player>}: every live profile of the account (slot, name, active, playtime, island). */
    void staffInfo(org.bukkit.command.CommandSender staff, String name) {
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            module.msg.send(staff, "staff.unknown", Msg.p("player", name));
            return;
        }
        UUID owner = target.getUniqueId();
        core.database().async(c -> new Object[]{store.liveProfiles(c, owner), store.activeProfile(c, owner).orElse(null)})
                .whenCompleteAsync((pair, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Profiles: info of " + name + " failed", Database.cause(error));
                        module.msg.send(staff, "error");
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<Profile> live = (List<Profile>) pair[0];
                    UUID active = (UUID) pair[1];
                    module.msg.send(staff, "staff.info.header", Msg.p("player", name), Msg.number("count", live.size()),
                            Msg.p("online", target.isOnline() ? "online" : "offline"));
                    var islands = core.services().optional(net.skybornmc.skyblock.common.island.IslandService.class);
                    for (Profile p : live) {
                        String island = islands.flatMap(i -> i.islandOf(p.id())).map(i -> i.name()).orElse("-");
                        module.msg.send(staff, p.id().equals(active) ? "staff.info.line-active" : "staff.info.line",
                                Msg.number("slot", p.slot()), Msg.p("profile", name(p)), Msg.p("island", island),
                                Msg.p("playtime", net.skybornmc.skyblock.common.util.Format.duration(java.time.Duration.ofMillis(p.playtimeMillis()))),
                                Msg.p("player", name));
                    }
                }, main);
    }

    /**
     * {@code /profiles reset <player> [slot]}: wipes one profile (default the active one) with the normal wipe (4 Nov 2026):
     * snapshot first (restorable for {@code wipe.snapshot-days}), then a fresh profile in the same slot. Online players
     * switch to the fresh profile at once; offline players lose the items in their file on the next join. A team island's
     * leader is refused (transfer the island first), like a player's own delete.
     */
    void staffReset(org.bukkit.command.CommandSender staff, String name, Integer slot) {
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            module.msg.send(staff, "staff.unknown", Msg.p("player", name));
            return;
        }
        if (staff instanceof Player self && self.getUniqueId().equals(target.getUniqueId())) {
            module.msg.send(staff, "staff.reset.self");
            return;
        }
        UUID owner = target.getUniqueId();
        Actor actor = staff instanceof Player p ? Actor.player(p) : Actor.system("console");
        core.database().async(c -> new Object[]{store.liveProfiles(c, owner), store.activeProfile(c, owner).orElse(null)})
                .whenCompleteAsync((pair, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.SEVERE, "Profiles: staff reset of " + name + " failed", Database.cause(error));
                        module.msg.send(staff, "error");
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<Profile> live = (List<Profile>) pair[0];
                    UUID active = (UUID) pair[1];
                    Optional<Profile> pick = live.stream()
                            .filter(p -> slot == null ? p.id().equals(active) : p.slot() == slot).findFirst();
                    if (pick.isEmpty()) {
                        module.msg.send(staff, "staff.reset.no-profile", Msg.p("player", name), Msg.p("slot", slot == null ? "active" : String.valueOf(slot)));
                        return;
                    }
                    Profile profile = pick.get();
                    Optional<IslandService> islands = core.services().optional(IslandService.class);
                    boolean teamLeader = islands.flatMap(i -> i.islandOf(profile.id()))
                            .map(i -> i.team() && profile.id().equals(i.owner())).orElse(false);
                    if (teamLeader) {
                        module.msg.send(staff, "staff.reset.team-leader", Msg.p("player", name));
                        return;
                    }
                    UUID islandId = islands.flatMap(i -> i.islandOf(profile.id())).map(i -> i.id()).orElse(null);
                    String islandName = islands.flatMap(i -> i.islandOf(profile.id())).map(i -> i.name()).orElse(null);
                    wipe(profile.id(), WipeReason.STAFF, actor, islandId, islandName).whenCompleteAsync((result, failure) -> {
                        if (failure != null || result == null || !result.done()) {
                            module.msg.send(staff, result != null && result.status() == ProfileWipeResult.Status.BUSY
                                    ? "staff.reset.busy" : "error");
                            return;
                        }
                        module.msg.send(staff, "staff.reset.done", Msg.p("player", name), Msg.number("slot", profile.slot()),
                                Msg.p("profile", name(profile)), Msg.number("snapshot", result.snapshotId()),
                                Msg.number("days", config().snapshotDays));
                    }, main);
                }, main);
    }

    // ================================================================== wipe (owner 4 Nov 2026: island = profile)

    @Override
    public boolean supportsWipe() {
        return true;
    }

    private static String actorName(Actor actor) {
        return actor == null || actor.name() == null ? "system" : actor.name();
    }

    @Override
    public CompletableFuture<Void> intendWipe(UUID profileKey, WipeReason reason, Actor actor, UUID islandId, String islandName) {
        if (profileKey == null || reason == null) {
            return CompletableFuture.completedFuture(null);
        }
        ProfileStore.Intent intent = new ProfileStore.Intent(profileKey, ownerOf(profileKey), reason.key(), actorName(actor), islandId,
                islandName, System.currentTimeMillis());
        return core.database().run(c -> store.intend(c, intent)).exceptionally(e -> {
            // Without the intent only the crash safety is lost (the wipe itself still runs after the removal).
            core.plugin().getLogger().log(Level.WARNING, "Profiles: could not save the wipe intent of " + profileKey, Database.cause(e));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> dropWipeIntent(UUID profileKey) {
        if (profileKey == null) {
            return CompletableFuture.completedFuture(null);
        }
        return core.database().run(c -> store.dropIntent(c, profileKey)).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Profiles: could not drop the wipe intent of " + profileKey, Database.cause(e));
            return null;
        });
    }

    @Override
    public CompletableFuture<ProfileWipeResult> wipe(UUID profileKey, WipeReason reason, Actor actor, UUID islandId, String islandName) {
        CompletableFuture<ProfileWipeResult> out = new CompletableFuture<>();
        if (profileKey == null || reason == null) {
            out.complete(ProfileWipeResult.of(ProfileWipeResult.Status.NOT_FOUND));
            return out;
        }
        WipeJob job = new WipeJob(profileKey, reason, actor == null ? Actor.system("profiles") : actor, islandId, islandName, out);
        main.execute(() -> startWipe(job, 0));
        return out;
    }

    /** One wipe on its way (main thread). */
    private record WipeJob(UUID key, WipeReason reason, Actor actor, UUID islandId, String islandName,
                           CompletableFuture<ProfileWipeResult> out) {
    }

    /** A player who is switching (or still loading) can't be wiped this tick: try again shortly. */
    private boolean busy(WipeJob job, int attempt) {
        UUID owner = ownerOf(job.key());
        Player player = Bukkit.getPlayer(owner);
        Session s = sessions.get(owner);
        if (player == null || (s != null && !s.switching)) {
            return false;
        }
        if (attempt < config().wipeBusyRetries) {
            core.scheduler().later("profiles:wipe-retry", 40L, () -> startWipe(job, attempt + 1));
        } else {
            core.plugin().getLogger().warning("Profiles: wipe of " + job.key() + " (" + player.getName() + ") waits for the sweep: "
                    + "the player kept switching.");
            job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.BUSY));
        }
        return true;
    }

    /** Reads the money and shards for the snapshot and the summary, then wipes. */
    private void startWipe(WipeJob job, int attempt) {
        if (!isLive(job.key())) {
            job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.NOT_FOUND));
            return;
        }
        if (busy(job, attempt)) {
            return;
        }
        Optional<EconomyService> eco = core.services().optional(EconomyService.class);
        CompletableFuture<Long> money = eco.isEmpty() ? CompletableFuture.completedFuture(null)
                : eco.get().balance(AccountRef.player(job.key()), Currency.MONEY).handle((v, e) -> e == null ? v : null);
        CompletableFuture<Long> shards = eco.isEmpty() ? CompletableFuture.completedFuture(null)
                : eco.get().balance(AccountRef.player(job.key()), Currency.SHARDS).handle((v, e) -> e == null ? v : null)
                // owner 4 Nov 2026: Farming and Mining Shards together in the summary
                .thenCombine(eco.get().balance(AccountRef.player(job.key()), Currency.MINING_SHARDS).handle((v, e) -> e == null ? v : null),
                        (a, b) -> a == null && b == null ? null : (a == null ? 0L : a) + (b == null ? 0L : b));
        money.thenCombine(shards, (m, s) -> new Long[]{m, s})
                .whenCompleteAsync((amounts, error) -> wipeNow(job, attempt, amounts == null ? null : amounts[0],
                        amounts == null ? null : amounts[1]), main);
    }

    private void wipeNow(WipeJob job, int attempt, Long money, Long shards) {
        if (!isLive(job.key())) {
            job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.NOT_FOUND));
            return;
        }
        if (busy(job, attempt)) {
            return;
        }
        UUID owner = ownerOf(job.key());
        Player player = Bukkit.getPlayer(owner);
        Session s = sessions.get(owner);
        boolean onlineActive = player != null && s != null && job.key().equals(s.active);
        long now = System.currentTimeMillis();
        StoredState captured = null;
        Map<String, String> settings = null;
        long playtimeAdd = 0;
        Integer items = null;
        if (onlineActive) {
            Optional<PlayerData> data = core.players().get(owner);
            if (data.isEmpty()) {
                // Still loading: never wipe with an unknown settings map; the retry (or the sweep) does it.
                if (attempt < config().wipeBusyRetries) {
                    core.scheduler().later("profiles:wipe-retry", 40L, () -> startWipe(job, attempt + 1));
                } else {
                    job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.BUSY));
                }
                return;
            }
            // From here until the result the player is locked (ProfileGuards), exactly like a switch.
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            ItemStack cursor = player.getItemOnCursor();
            if (!cursor.isEmpty()) {
                player.setItemOnCursor(null);
                // Back into the inventory so it is part of the snapshot. What does not fit is wiped with the rest
                // (never dropped: a dropped item would survive the wipe).
                player.getInventory().addItem(cursor);
            }
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            s.switching = true;
            inFlight.add(owner);
            captured = StateCodec.capture(player);
            items = WipeRules.clampItems(WipeRules.countItems(player.getInventory().getContents())
                    + WipeRules.countItems(player.getEnderChest().getContents()));
            settings = data.get().snapshot().settings();
            playtimeAdd = Math.max(0, now - s.checkpoint);
        }
        ProfileStore.WipeInput in = new ProfileStore.WipeInput(owner, job.key(), captured, settings, playtimeAdd, job.reason().key(),
                actorName(job.actor()), job.islandId(), job.islandName(), money, shards, items, player != null, now,
                WipeRules.expiresAt(now, config().snapshotDays));
        CompletableFuture<ProfileStore.Wiped> tx = core.database().transaction(c -> store.wipe(c, in, config()::profileSetting));
        if (onlineActive) {
            tx.whenComplete((result, error) -> inFlight.remove(owner));
        }
        tx.copy().orTimeout(config().switchTimeoutSeconds, TimeUnit.SECONDS)
                .whenCompleteAsync((result, error) -> finishWipe(job, player, s, onlineActive, in, result, error), main);
    }

    private void finishWipe(WipeJob job, Player player, Session s, boolean onlineActive, ProfileStore.WipeInput in,
                            ProfileStore.Wiped result, Throwable error) {
        UUID owner = in.owner();
        boolean switched = false;
        try {
            if (error != null) {
                Throwable cause = Database.cause(error);
                if (cause instanceof TimeoutException) {
                    // Unknown outcome: the player was locked since the capture, so their file equals the saved state.
                    // The kick saves it and the next join applies whatever the database decided (like a switch timeout).
                    core.plugin().getLogger().severe("Profiles: wipe of " + in.profileId() + " timed out; player kicked to keep the items safe.");
                    if (onlineActive && player.isOnline()) {
                        player.kick(module.msg.component(player, "login.switch-timeout"));
                    }
                    job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.UNAVAILABLE));
                    return;
                }
                if (cause instanceof ProfileStore.Refused) {
                    job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.NOT_FOUND));
                    return;
                }
                // Rolled back: nothing changed. The intent (if any) stays and the sweep tries again.
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: wipe of profile " + in.profileId() + " failed", cause);
                job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.UNAVAILABLE));
                return;
            }
            Profile gone = result.gone();
            Profile fresh = result.fresh();
            remember(gone.id(), owner, true);
            remember(fresh.id(), owner, false);
            Session live = sessions.get(owner);
            if (live != null) {
                live.profiles = live.profiles.stream().map(p -> p.id().equals(gone.id()) ? fresh : p).toList();
            }
            if (onlineActive && player.isOnline() && live == s) {
                try {
                    StateCodec.apply(player, StoredState.FRESH);
                } catch (RuntimeException e) {
                    // The database says "fresh, not applied": the next join applies the empty state again.
                    core.plugin().getLogger().log(Level.SEVERE, "Profiles: emptying " + player.getName() + " after a wipe failed", e);
                    player.kick(module.msg.component(player, "login.switch-timeout"));
                    job.out().complete(new ProfileWipeResult(ProfileWipeResult.Status.WIPED_ON_JOIN, gone.id(), fresh.id(), result.snapshotId()));
                    return;
                }
                long now = System.currentTimeMillis();
                s.active = fresh.id();
                s.checkpoint = now;
                core.players().get(owner).ifPresent(d -> swapSettings(d, result.settings()));
                net.skybornmc.skyblock.common.player.PlayerSaves.save(player, "a profile wipe");
                UUID freshId = fresh.id();
                core.database().run(c -> store.consumeState(c, owner, freshId)).exceptionally(e -> {
                    core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not clear the applied state of " + player.getName(), Database.cause(e));
                    return null;
                });
                s.switching = false;
                switched = true;
                Bukkit.getPluginManager().callEvent(new ProfileSwitchedEvent(player, gone, fresh));
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("profile", gone.id().toString());
            details.put("new_profile", fresh.id().toString());
            details.put("slot", gone.slot());
            details.put("reason", job.reason().key());
            details.put("snapshot", result.snapshotId());
            details.put("was_active", result.wasActive());
            details.put("online", switched);
            details.put("items_on_join", result.captureOnJoin());
            details.put("money", in.money() == null ? -1 : in.money());
            details.put("shards", in.shards() == null ? -1 : in.shards());
            details.put("items", in.items() == null ? -1 : in.items());
            if (job.islandId() != null) {
                details.put("island", job.islandId().toString());
            }
            if (job.islandName() != null) {
                details.put("island_name", job.islandName());
            }
            core.audit().logPlayer(AuditCategory.AUTH, "profile_wipe", job.actor(), owner,
                    core.players().get(owner).map(PlayerData::name).orElse(player == null ? null : player.getName()), in.money(),
                    player != null && player.isOnline() ? player.getLocation() : null, details);
            Bukkit.getPluginManager().callEvent(new ProfileWipedEvent(gone, fresh, job.reason(), job.actor(), result.snapshotId(), switched));
            if (player != null && player.isOnline()) {
                sendSummary(player, job.reason().key(), job.islandName(), in.money(), in.shards(), in.items());
            }
            ProfileWipeResult.Status status = result.captureOnJoin() ? ProfileWipeResult.Status.WIPED_ON_JOIN : ProfileWipeResult.Status.WIPED;
            job.out().complete(new ProfileWipeResult(status, gone.id(), fresh.id(), result.snapshotId()));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Profiles: finishing the wipe of " + in.profileId() + " failed", e);
            if (!job.out().isDone()) {
                job.out().complete(ProfileWipeResult.of(ProfileWipeResult.Status.UNAVAILABLE));
            }
        } finally {
            if (onlineActive && s != null && player.isOnline()) {
                s.switching = false;
            }
        }
    }

    /** A raw text of messages/profiles.yml, or {@code fallback} when it is missing or blank. */
    private String raw(String key, String fallback) {
        String text = core.messages().raw(ProfilesModule.ID, key);
        return text == null || text.isBlank() ? fallback : text;
    }

    /** The chat summary of a wipe: what was lost, what stays, how to start again. */
    private void sendSummary(Player player, String reasonKey, String islandName, Long money, Long shards, Integer items) {
        String island = islandName == null || islandName.isBlank() ? raw("wipe.summary.no-island", "your island") : islandName;
        String why = raw("wipe.reason." + reasonKey, reasonKey);
        Optional<EconomyService> eco = core.services().optional(EconomyService.class);
        String unknown = raw("wipe.summary.unknown", "?");
        String moneyText = money == null ? unknown : eco.map(e -> e.format(Currency.MONEY, money)).orElse(Format.money(money));
        String shardText = shards == null ? unknown : Format.number(shards);
        String itemText = items == null ? unknown : Format.number(items);
        module.msg.send(player, "wipe.summary.text", Msg.mm("why", why.replace("<island>",
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(island))), Msg.p("money", moneyText),
                Msg.p("shards", shardText), Msg.p("items", itemText), Msg.number("days", config().snapshotDays));
        core.sounds().play(player, "warning");
    }

    /** Join: the summaries of wipes that happened while the player was offline (once each). */
    private void sendWipeNotices(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID owner = player.getUniqueId();
        core.database().async(c -> store.unnotified(c, owner)).whenCompleteAsync((list, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Profiles: could not load the wipe notices of " + player.getName(), Database.cause(error));
                return;
            }
            if (list.isEmpty() || !player.isOnline()) {
                return;
            }
            for (ProfileStore.Snapshot snap : list) {
                sendSummary(player, snap.reason(), snap.islandName(), snap.money(), snap.shards(), snap.items());
            }
            core.database().run(c -> store.markNotified(c, owner)).exceptionally(e -> null);
        }, main);
    }

    // ================================================================== crash sweep and purge

    /**
     * Crash safety: an intent whose profile left its island (the removal committed but the wipe did not) is wiped now; an
     * intent whose profile is still on that island (the removal never committed) or is gone already is dropped.
     */
    private void sweepIntents() {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        if (islands.isEmpty() || !core.database().isRunning()) {
            return; // without islands we can't tell whether the removal happened: wait
        }
        long now = System.currentTimeMillis();
        long grace = config().intentGraceSeconds * 1000L;
        core.database().async(c -> store.intents(c, now - grace, 100)).whenCompleteAsync((list, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Profiles: wipe intent sweep failed", Database.cause(error));
                return;
            }
            for (ProfileStore.Intent intent : list) {
                boolean still = intent.islandId() != null && islands.get().islandOfProfile(intent.profileId())
                        .map(i -> i.id().equals(intent.islandId())).orElse(false);
                switch (WipeRules.intent(isLive(intent.profileId()), still, now - intent.createdAt(), grace)) {
                    case WAIT -> {
                        // too young
                    }
                    case DROP -> dropWipeIntent(intent.profileId());
                    case WIPE -> {
                        WipeReason reason = WipeReason.byKey(intent.reason()).orElse(WipeReason.LEAVE);
                        core.plugin().getLogger().warning("Profiles: finishing an interrupted wipe of " + intent.profileId() + " (" + reason.key() + ")");
                        wipe(intent.profileId(), reason, Actor.system("profiles"), intent.islandId(), intent.islandName());
                    }
                }
            }
        }, main);
    }

    /** Deletes expired snapshots: claims each in the database, then lets every module delete the old key's rows. */
    private void purgeExpired() {
        if (!core.database().isRunning()) {
            return;
        }
        long now = System.currentTimeMillis();
        int batch = config().purgeBatch;
        core.database().async(c -> store.purgeDue(c, now, batch)).whenComplete((due, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Profiles: snapshot purge failed", Database.cause(error));
                return;
            }
            for (ProfileStore.Snapshot snap : due) {
                core.database().transaction(c -> store.claimPurge(c, snap.id(), now)).whenCompleteAsync((old, failure) -> {
                    if (failure != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Profiles: purging snapshot " + snap.id() + " failed", Database.cause(failure));
                        return;
                    }
                    if (old.isEmpty()) {
                        return; // restored or purged meanwhile
                    }
                    remember(old.get().id(), old.get().owner(), true);
                    core.audit().logPlayer(AuditCategory.AUTH, "profile_snapshot_purge", Actor.system("profiles"), snap.owner(),
                            core.players().get(snap.owner()).map(PlayerData::name).orElse(null), null, null,
                            Map.of("snapshot", snap.id(), "profile", snap.profileId().toString(), "slot", snap.slot(), "reason", snap.reason()));
                    // Every module deletes what it stored under the old key (the same event as a deleted profile).
                    Bukkit.getPluginManager().callEvent(new ProfileDeletedEvent(old.get()));
                }, main);
            }
        });
    }

    // ================================================================== staff: snapshots and restore

    /** {@code /profiles snapshots <player>}: the newest snapshots with status and a click to restore the open ones. */
    void staffSnapshots(org.bukkit.command.CommandSender staff, String name) {
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            module.msg.send(staff, "staff.unknown", Msg.p("player", name));
            return;
        }
        UUID owner = target.getUniqueId();
        int limit = config().snapshotListLimit;
        core.database().async(c -> store.snapshots(c, owner, limit)).whenCompleteAsync((list, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Profiles: snapshots of " + name + " failed", Database.cause(error));
                module.msg.send(staff, "error");
                return;
            }
            if (list.isEmpty()) {
                module.msg.send(staff, "snapshots.none", Msg.p("player", name));
                return;
            }
            long now = System.currentTimeMillis();
            module.msg.send(staff, "snapshots.header", Msg.p("player", name), Msg.number("count", list.size()),
                    Msg.number("days", config().snapshotDays));
            Optional<EconomyService> eco = core.services().optional(EconomyService.class);
            String unknown = raw("wipe.summary.unknown", "?");
            for (ProfileStore.Snapshot snap : list) {
                WipeRules.SnapshotStatus status = snap.status(now);
                String statusText = raw("snapshots.status." + status.key(), status.key())
                        .replace("<time>", Format.duration(Duration.ofMillis(Math.max(0, snap.expiresAt() - now))))
                        .replace("<by>", snap.restoredBy() == null ? "?" : snap.restoredBy());
                String money = snap.money() == null ? unknown
                        : eco.map(e -> e.format(Currency.MONEY, snap.money())).orElse(Format.money(snap.money()));
                module.msg.send(staff, status == WipeRules.SnapshotStatus.OPEN ? "snapshots.line-open" : "snapshots.line",
                        Msg.number("id", snap.id()), Msg.number("slot", snap.slot()), Msg.p("reason", snap.reason()),
                        Msg.p("island", snap.islandName() == null ? "-" : snap.islandName()), Msg.p("money", money),
                        Msg.p("items", snap.items() == null ? unknown : Format.number(snap.items())),
                        Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, now - snap.createdAt())))),
                        Msg.p("by", snap.actorName()), Msg.mm("status", statusText), Msg.p("player", name));
            }
        }, main);
    }

    /** Open snapshot ids of a player (tab completion of {@code /profiles restore}), newest first; {@code slot} null = any. */
    CompletableFuture<List<String>> openSnapshotIds(UUID owner, Integer slot) {
        long now = System.currentTimeMillis();
        return core.database().async(c -> store.snapshots(c, owner, 25)).thenApply(list -> list.stream()
                .filter(s -> s.status(now) == WipeRules.SnapshotStatus.OPEN && (slot == null || s.slot() == slot))
                .map(s -> String.valueOf(s.id())).toList());
    }

    /**
     * {@code /profiles restore <player> <slot> [snapshot]}: puts a wiped profile back into its slot (default: the newest
     * open snapshot of that slot). The profile in the slot now is wiped into its own snapshot, so a restore can be undone
     * the same way. Online players on that slot switch at once. Audited ({@code profile_restore}).
     */
    void staffRestore(org.bukkit.command.CommandSender staff, String name, int slot, Integer snapshotId) {
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            module.msg.send(staff, "staff.unknown", Msg.p("player", name));
            return;
        }
        UUID owner = target.getUniqueId();
        long now = System.currentTimeMillis();
        core.database().async(c -> store.snapshots(c, owner, 50)).whenCompleteAsync((list, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Profiles: restore lookup for " + name + " failed", Database.cause(error));
                module.msg.send(staff, "error");
                return;
            }
            Optional<ProfileStore.Snapshot> pick = list.stream()
                    .filter(s -> snapshotId == null ? s.slot() == slot && s.status(now) == WipeRules.SnapshotStatus.OPEN : s.id() == snapshotId)
                    .findFirst();
            if (pick.isEmpty()) {
                module.msg.send(staff, snapshotId == null ? "restore.none" : "restore.unknown", Msg.p("player", name), Msg.number("slot", slot),
                        Msg.p("snapshot", snapshotId == null ? "-" : String.valueOf(snapshotId)));
                return;
            }
            if (pick.get().slot() != slot) {
                module.msg.send(staff, "restore.other-slot", Msg.number("snapshot", pick.get().id()), Msg.number("slot", pick.get().slot()),
                        Msg.p("player", name));
                return;
            }
            doRestore(staff, owner, name, pick.get());
        }, main);
    }

    private void doRestore(org.bukkit.command.CommandSender staff, UUID owner, String name, ProfileStore.Snapshot snap) {
        Player player = Bukkit.getPlayer(owner);
        Session s = sessions.get(owner);
        if (player != null && (s == null || s.switching)) {
            module.msg.send(staff, "restore.busy", Msg.p("player", name));
            return;
        }
        boolean slotActive = player != null && s.inSlot(snap.slot()).map(p -> p.id().equals(s.active)).orElse(false);
        Actor actor = staff instanceof Player p ? Actor.player(p) : Actor.system("console");
        long now = System.currentTimeMillis();
        StoredState captured = null;
        Map<String, String> settings = null;
        long playtimeAdd = 0;
        if (slotActive) {
            Optional<PlayerData> data = core.players().get(owner);
            if (data.isEmpty()) {
                module.msg.send(staff, "restore.busy", Msg.p("player", name));
                return;
            }
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            if (!player.getItemOnCursor().isEmpty()) {
                ItemStack cursor = player.getItemOnCursor();
                player.setItemOnCursor(null);
                player.getInventory().addItem(cursor);
            }
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            s.switching = true;
            inFlight.add(owner);
            captured = StateCodec.capture(player);
            settings = data.get().snapshot().settings();
            playtimeAdd = Math.max(0, now - s.checkpoint);
        }
        ProfileStore.RestoreInput in = new ProfileStore.RestoreInput(owner, snap.id(), captured, settings, playtimeAdd, actorName(actor), now,
                WipeRules.expiresAt(now, config().snapshotDays));
        CompletableFuture<ProfileStore.Restored> tx = core.database().transaction(c -> store.restore(c, in, config()::profileSetting));
        if (slotActive) {
            tx.whenComplete((result, error) -> inFlight.remove(owner));
        }
        tx.copy().orTimeout(config().switchTimeoutSeconds, TimeUnit.SECONDS)
                .whenCompleteAsync((result, error) -> finishRestore(staff, player, s, slotActive, name, actor, snap, result, error), main);
    }

    private void finishRestore(org.bukkit.command.CommandSender staff, Player player, Session s, boolean slotActive, String name, Actor actor,
                               ProfileStore.Snapshot snap, ProfileStore.Restored result, Throwable error) {
        UUID owner = snap.owner();
        try {
            if (error != null) {
                Throwable cause = Database.cause(error);
                if (cause instanceof TimeoutException) {
                    core.plugin().getLogger().severe("Profiles: restore of snapshot " + snap.id() + " timed out; player kicked to keep the items safe.");
                    if (slotActive && player.isOnline()) {
                        player.kick(module.msg.component(player, "login.switch-timeout"));
                    }
                    module.msg.send(staff, "error");
                    return;
                }
                if (cause instanceof ProfileStore.Refused refused) {
                    module.msg.send(staff, "restore.refused." + switch (refused.key) {
                        case "restored" -> "restored";
                        case "expired" -> "expired";
                        case "live" -> "live";
                        default -> "gone";
                    }, Msg.number("snapshot", snap.id()), Msg.p("player", name));
                    return;
                }
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: restore of snapshot " + snap.id() + " failed", cause);
                module.msg.send(staff, "error");
                return;
            }
            Profile restored = result.restored();
            Profile replaced = result.replaced();
            remember(restored.id(), owner, false);
            if (replaced != null) {
                remember(replaced.id(), owner, true);
            }
            Session live = sessions.get(owner);
            if (live != null) {
                List<Profile> list = new ArrayList<>(live.profiles.stream()
                        .filter(p -> replaced == null || !p.id().equals(replaced.id())).toList());
                list.add(restored);
                list.sort(java.util.Comparator.comparingInt(Profile::slot).thenComparingLong(Profile::createdAt));
                live.profiles = List.copyOf(list);
            }
            boolean switched = false;
            if (result.becameActive() && player != null && player.isOnline() && live == s && result.apply() != null) {
                try {
                    StateCodec.apply(player, result.apply());
                } catch (RuntimeException e) {
                    core.plugin().getLogger().log(Level.SEVERE, "Profiles: loading restored profile " + restored.id() + " failed", e);
                    player.kick(module.msg.component(player, "login.switch-timeout"));
                    return;
                }
                long now = System.currentTimeMillis();
                s.active = restored.id();
                s.checkpoint = now;
                core.players().get(owner).ifPresent(d -> swapSettings(d, result.settings()));
                net.skybornmc.skyblock.common.player.PlayerSaves.save(player, "a profile restore");
                UUID restoredId = restored.id();
                core.database().run(c -> store.consumeState(c, owner, restoredId)).exceptionally(e -> {
                    core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not clear the applied state of " + player.getName(), Database.cause(e));
                    return null;
                });
                s.switching = false;
                switched = true;
                Bukkit.getPluginManager().callEvent(new ProfileSwitchedEvent(player, replaced, restored));
                teleportToProfile(player, restoredId);
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("snapshot", snap.id());
            details.put("profile", restored.id().toString());
            details.put("slot", restored.slot());
            details.put("wipe_reason", snap.reason());
            details.put("replaced", replaced == null ? "-" : replaced.id().toString());
            details.put("replaced_snapshot", result.replacedSnapshotId());
            details.put("active", result.becameActive());
            details.put("online", switched);
            core.audit().logPlayer(AuditCategory.AUTH, "profile_restore", actor, owner, name, snap.money(), null, details);
            Bukkit.getPluginManager().callEvent(new ProfileRestoredEvent(restored, replaced,
                    WipeReason.byKey(snap.reason()).orElse(WipeReason.STAFF), snap.islandId(), actor, snap.id()));
            module.msg.send(staff, "restore.done", Msg.p("player", name), Msg.number("slot", restored.slot()), Msg.number("snapshot", snap.id()),
                    Msg.p("profile", name(restored)), Msg.number("replaced", result.replacedSnapshotId()));
            if (snap.islandId() != null) {
                module.msg.send(staff, "restore.island-hint", Msg.p("player", name),
                        Msg.p("island", snap.islandName() == null ? "?" : snap.islandName()));
            }
            if (player != null && player.isOnline()) {
                module.msg.send(player, "restore.you", Msg.p("profile", name(restored)), Msg.number("slot", restored.slot()));
                core.sounds().play(player, "success");
            }
        } finally {
            if (slotActive && s != null && player != null && player.isOnline()) {
                s.switching = false;
            }
        }
    }

    private void updateProfiles(UUID owner, List<Profile> live) {
        Session current = sessions.get(owner);
        if (current != null) {
            current.profiles = List.copyOf(live);
        }
    }

    private void refusedOrError(Player player, String action, Throwable error) {
        Throwable cause = Database.cause(error);
        if (cause instanceof ProfileStore.Refused refused) {
            module.fail(player, action + "." + refused.key);
            return;
        }
        core.plugin().getLogger().log(Level.SEVERE, "Profiles: " + action + " for " + player.getName() + " failed", cause);
        module.fail(player, "error");
    }

    // ================================================================== switching

    /** {@code /profiles} → switch: save current state, load the target's, teleport to its island home (or spawn). */
    void switchTo(Player player, UUID targetId) {
        UUID owner = player.getUniqueId();
        Session s = sessions.get(owner);
        long now = System.currentTimeMillis();
        Profile target = s == null ? null : s.profile(targetId).orElse(null);
        Optional<SwitchRules.Problem> problem = SwitchRules.check(new SwitchRules.Input(s != null, s != null && s.switching,
                s == null ? null : s.active, target, target != null && slotUnlocked(player, target.slot()),
                s == null ? 0 : s.lastSwitch, now, config().switchCooldownSeconds * 1000L,
                player.hasPermission("skyblock.cooldown.bypass"),
                config().blockedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName())),
                player.isDead()));
        if (problem.isPresent()) {
            long left = s == null ? 0 : SwitchRules.cooldownLeft(s.lastSwitch, now, config().switchCooldownSeconds * 1000L);
            module.fail(player, "switch." + problem.get().key, Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, left)))));
            return;
        }
        Profile from = s.profile(s.active).orElse(null);
        if (from == null) {
            module.fail(player, "error");
            return;
        }
        ProfileSwitchEvent event = new ProfileSwitchEvent(player, from, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (event.reason() != null) {
                player.sendMessage(event.reason());
            } else {
                module.msg.send(player, "switch.refused");
            }
            core.sounds().play(player, "error");
            return;
        }
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        if (!player.getItemOnCursor().isEmpty()) {
            module.fail(player, "switch.cursor");
            return;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Optional<PlayerData> data = core.players().get(owner);
        if (data.isEmpty()) {
            module.fail(player, "switch.not-loaded");
            return;
        }

        // From here until the result: the player is locked (ProfileGuards cancels every item/world action).
        s.switching = true;
        inFlight.add(owner);
        StoredState current = StateCodec.capture(player);
        Map<String, String> settings = data.get().snapshot().settings();
        long playtimeAdd = Math.max(0, now - s.checkpoint);
        module.msg.send(player, "switch.started", Msg.p("profile", name(target)));
        CompletableFuture<ProfileStore.Switched> tx = core.database().transaction(c -> store.switchTo(c, owner, from.id(), target.id(),
                current, settings, config()::profileSetting, playtimeAdd, System.currentTimeMillis()));
        // Only the real end of the transaction lets this player log in again (a timeout below does not).
        tx.whenComplete((result, error) -> inFlight.remove(owner));
        tx.copy().orTimeout(config().switchTimeoutSeconds, TimeUnit.SECONDS)
                .whenCompleteAsync((result, error) -> finishSwitch(player, s, from, target, result, error), main);
    }

    private void finishSwitch(Player player, Session s, Profile from, Profile target, ProfileStore.Switched result, Throwable error) {
        UUID owner = player.getUniqueId();
        try {
            if (error != null) {
                Throwable cause = Database.cause(error);
                if (cause instanceof TimeoutException) {
                    // The outcome is unknown and may still commit. The player was locked since the save, so their player
                    // file equals the saved state: kick them (it saves that file) and let the next join apply whatever
                    // the database decided. They can't log in before the transaction has really ended.
                    core.plugin().getLogger().severe("Profiles: switch of " + player.getName() + " timed out; kicked to keep their items safe.");
                    if (player.isOnline()) {
                        player.kick(module.msg.component(player, "login.switch-timeout"));
                    }
                    return;
                }
                s.switching = false;
                if (cause instanceof ProfileStore.Refused refused) {
                    module.fail(player, "switch." + switch (refused.key) {
                        case "same" -> "same";
                        case "gone" -> "gone";
                        default -> "changed";
                    });
                } else {
                    // The transaction rolled back: nothing changed, the player keeps playing on the current profile.
                    core.plugin().getLogger().log(Level.SEVERE, "Profiles: switch of " + player.getName() + " failed", cause);
                    module.fail(player, "error");
                }
                return;
            }
            long now = System.currentTimeMillis();
            remember(target.id(), owner, false);
            if (!player.isOnline()) {
                // Left while saving: the player file holds the old state, the database says "target, not applied":
                // the next join applies the target state.
                return;
            }
            try {
                StateCodec.apply(player, result.target());
            } catch (RuntimeException e) {
                // Never play on with a half-loaded inventory: the database says "target, not applied", so the next join
                // applies it again from the saved state.
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: loading profile " + target.id() + " of " + player.getName() + " failed", e);
                player.kick(module.msg.component(player, "login.switch-timeout"));
                return;
            }
            s.active = target.id();
            s.checkpoint = now;
            s.lastSwitch = now;
            s.profiles = s.profiles.stream().map(p -> p.id().equals(result.from().id()) ? result.from()
                    : p.id().equals(result.to().id()) ? result.to() : p).toList();
            core.players().get(owner).ifPresent(d -> swapSettings(d, result.settings()));
            player.saveData();
            UUID targetId = target.id();
            core.database().run(c -> store.consumeState(c, owner, targetId)).exceptionally(e -> {
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not clear the applied state of " + player.getName(), Database.cause(e));
                return null;
            });
            s.switching = false;
            core.audit().logPlayer(AuditCategory.AUTH, "profile_switch", Actor.player(player), owner, player.getName(), null,
                    player.getLocation(), Map.of("from", from.id().toString(), "to", targetId.toString(), "slot", target.slot()));
            Bukkit.getPluginManager().callEvent(new ProfileSwitchedEvent(player, result.from(), result.to()));
            teleportToProfile(player, targetId);
            module.msg.send(player, "switch.done", Msg.p("profile", name(result.to())));
            core.sounds().play(player, "teleport");
        } finally {
            if (player.isOnline()) {
                s.switching = false;
            }
        }
    }

    /** Puts the new profile's settings into the cached player data (the database row was written in the switch). */
    private void swapSettings(PlayerData data, Map<String, String> newSettings) {
        for (String key : data.snapshot().settings().keySet()) {
            if (config().profileSetting(key) && !newSettings.containsKey(key)) {
                data.setSetting(key, null);
            }
        }
        newSettings.forEach((k, v) -> {
            if (config().profileSetting(k)) {
                data.setSetting(k, v);
            }
        });
    }

    /** Teleports to the profile's island home, or the spawn when it has no (built) island. */
    private void teleportToProfile(Player player, UUID profileId) {
        if (!player.isOnline()) {
            return;
        }
        Optional<Location> home = core.services().optional(IslandService.class)
                .flatMap(islands -> islands.islandOfProfile(profileId).flatMap(island -> islands.home(island.id())));
        Location to = home.orElseGet(() -> {
            World world = Bukkit.getWorld(config().spawnWorld);
            return world == null ? null : world.getSpawnLocation().toCenterLocation();
        });
        if (to != null) {
            // F4: the core TeleportService (async chunk preload, /back); a system teleport, so no guard can refuse it
            // and the switch messages/sound stay with the profile code.
            core.services().get(net.skybornmc.skyblock.common.teleport.TeleportService.class).now(player, to,
                    net.skybornmc.skyblock.common.teleport.TeleportReason.PROFILE,
                    net.skybornmc.skyblock.common.teleport.TeleportOptions.builder().ignoreGuards().quiet()
                            .cause(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).build());
        }
    }
}
