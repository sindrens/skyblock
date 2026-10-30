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
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.profile.SlotRules;
import net.skybornmc.skyblock.common.profile.SlotSource;
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
 * state → load target state → teleport), create/rename/icon/delete, gem-bought slots and per-profile playtime.
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
    }

    /** Saves playtime of everyone online (blocking, shutdown). */
    void stop() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
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
        if (account.pending() != null) {
            // A switch finished in the database but was not applied in game (crash or quit): apply it now.
            StateCodec.apply(player, account.pending());
            player.saveData();
            UUID active = account.active();
            core.database().run(c -> store.consumeState(c, player.getUniqueId(), active)).exceptionally(e -> {
                core.plugin().getLogger().log(Level.SEVERE, "Profiles: could not clear the applied state of " + player.getName(), Database.cause(e));
                return null;
            });
            core.audit().logPlayer(AuditCategory.AUTH, "profile_switch_resumed", Actor.system("profiles"), player.getUniqueId(),
                    player.getName(), null, player.getLocation(), Map.of("profile", active.toString()));
            Bukkit.getScheduler().runTask(core.plugin(), () -> teleportToProfile(player, active));
        }
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

    /** Buys a slot with gems (atomic: the gem payment has an idempotency key per player and slot, so it is paid once). */
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
        Transaction tx = Transaction.withdraw(AccountRef.player(owner), Currency.GEMS, price, "profile_slot")
                .actor(Actor.player(player)).reference("profile_slot:" + slot)
                .idempotencyKey("profile_slot:" + owner + ":" + slot).detail("slot", slot).detail("target_name", player.getName());
        eco.get().execute(tx).thenCompose(result -> {
            if (result.status() == TxResult.Status.SUCCESS || result.status() == TxResult.Status.DUPLICATE) {
                // DUPLICATE = paid earlier but the unlock was not saved (crash): give the slot now, never charge twice.
                return core.database().transaction(c -> {
                    store.addUnlock(c, owner, slot, SlotSource.GEMS, 0, System.currentTimeMillis());
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
                        player.getLocation(), Map.of("slot", slot, "source", SlotSource.GEMS.key(), "status", result.status().name()));
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
                long have = eco.get().cached(owner, Currency.GEMS).orElse(0);
                module.fail(player, "slots.not-enough", Msg.p("price", Format.number(price)), Msg.p("have", Format.number(have)));
            } else {
                module.fail(player, "error");
            }
        }, main);
    }

    /** Owner decision P8 (for the season reset, Phase 10): drops every stored slot unlock that was not bought with gems. */
    CompletableFuture<Integer> seasonReset(Actor actor) {
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
        core.database().transaction(c -> store.switchTo(c, owner, from.id(), target.id(), current, settings,
                        config()::profileSetting, playtimeAdd, System.currentTimeMillis()))
                .orTimeout(config().switchTimeoutSeconds, TimeUnit.SECONDS)
                .whenCompleteAsync((result, error) -> finishSwitch(player, s, from, target, result, error), main);
    }

    private void finishSwitch(Player player, Session s, Profile from, Profile target, ProfileStore.Switched result, Throwable error) {
        UUID owner = player.getUniqueId();
        try {
            if (error != null) {
                s.switching = false;
                Throwable cause = Database.cause(error);
                if (cause instanceof ProfileStore.Refused refused) {
                    module.fail(player, "switch." + switch (refused.key) {
                        case "same" -> "same";
                        case "gone" -> "gone";
                        default -> "changed";
                    });
                } else {
                    // Timeout or database error: if the transaction still commits later, the next join applies it.
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
            StateCodec.apply(player, result.target());
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
            s.switching = false;
            inFlight.remove(owner);
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
            player.teleportAsync(to);
        }
    }
}
