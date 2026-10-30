package net.skybornmc.skyblock.box;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.Debounce;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.ProfileDeleteEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The published {@link ItemBox}: deliveries (inventory first, the rest to the box), the count cache of online players'
 * profiles, join/switch notices, remove-then-give takes and the admin actions.
 */
final class BoxService implements ItemBox, Listener {

    static final String AUDIT_TAKE = "box_take";
    static final String AUDIT_RETURNED = "box_take_returned";
    static final String AUDIT_OVER_CAP = "box_over_cap";
    static final String AUDIT_CLEAR = "box_clear";
    static final String AUDIT_REMOVE = "box_remove";
    static final String AUDIT_VIEW = "box_view";

    private final BoxModule m;
    private final Core core;
    private final BoxEngine engine;
    /** Profile key → waiting stacks, for the profiles of online players (absent = not loaded). */
    private final Map<UUID, Integer> counts = new ConcurrentHashMap<>();
    /** Profiles with a take running (one at a time per profile). */
    private final Set<UUID> taking = ConcurrentHashMap.newKeySet();
    private final Debounce clicks;

    BoxService(BoxModule module, BoxEngine engine) {
        this.m = module;
        this.core = module.core();
        this.engine = engine;
        this.clicks = new Debounce(module.config().clickCooldownMs);
    }

    void reloadSettings() {
        clicks.interval(m.config().clickCooldownMs);
    }

    BoxEngine engine() {
        return engine;
    }

    private ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    // ================================================================== ItemBox

    @Override
    public CompletableFuture<Delivery> deliver(UUID profileKey, Collection<ItemStack> items, String source, Actor actor) {
        flagStaffItems(profileKey, items, source, actor);
        List<BoxItem> rows = BoxItems.snapshot(items, source, System.currentTimeMillis());
        return deliverRows(profileKey, rows, actor, true);
    }

    /**
     * Owner answer 332 safety net: staff items never go to another player (trade and auction refuse them at the start;
     * there is no player-to-player box send). Should one still arrive through a player-to-player source, the delivery is
     * kept (nothing is ever lost) but logged as {@code staff_item_transfer} so staff can take it back.
     */
    private void flagStaffItems(UUID profileKey, Collection<ItemStack> items, String source, Actor actor) {
        String key = BoxSource.clean(source);
        if (!m.config().staffItemFlagSources.contains(key) || !net.skybornmc.skyblock.common.item.StaffItems.anyMarked(items)) {
            return;
        }
        UUID owner = profiles().ownerOf(profileKey);
        if (actor != null && owner.equals(actor.uuid())) {
            return;
        }
        core.audit().logPlayer(AuditCategory.ITEM, "staff_item_transfer", actor == null ? Actor.SYSTEM : actor, owner, null, null, null,
                Map.of("profile", profileKey.toString(), "source", key));
    }

    /** Stores serialized rows, audits, updates the cache and tells the owner. Never fails (spool). */
    CompletableFuture<Delivery> deliverRows(UUID profileKey, List<BoxItem> rows, Actor actor, boolean notify) {
        if (rows.isEmpty()) {
            return CompletableFuture.completedFuture(Delivery.NONE);
        }
        UUID owner = profiles().ownerOf(profileKey);
        return engine.deliver(profileKey, rows).thenApply(stored -> {
            int count = stored.count() >= 0 ? stored.count()
                    : counts.getOrDefault(profileKey, 0) + rows.size();
            counts.computeIfPresent(profileKey, (k, v) -> count);
            boolean over = BoxCap.isFull(count, m.config().maxEntries);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("profile", profileKey.toString());
            details.put("source", rows.getFirst().source());
            details.put("items", BoxItems.summary(rows, 30));
            details.put("ids", stored.ids());
            details.put("count", count);
            details.put("spooled", stored.spooled());
            core.audit().logPlayer(AuditCategory.ITEM, AUDIT_DELIVER, actor, owner, null, (long) rows.size(), null, details);
            if (over) {
                core.audit().logPlayer(AuditCategory.ITEM, AUDIT_OVER_CAP, Actor.system("box"), owner, null, (long) count, null,
                        Map.of("profile", profileKey.toString(), "cap", m.config().maxEntries));
            }
            if (notify) {
                core.scheduler().main("box:notify", () -> delivered(profileKey, rows.size(), count));
            }
            return new Delivery(rows.size(), count, over, stored.spooled());
        });
    }

    @Override
    public List<ItemStack> giveItems(Player player, Collection<ItemStack> items, String source) {
        List<ItemStack> rest = new ArrayList<>();
        boolean canReceive = canReceive(player);
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (canReceive) {
                rest.addAll(player.getInventory().addItem(item.clone()).values());
            } else {
                rest.add(item.clone());
            }
        }
        store(player, rest, source);
        return rest;
    }

    @Override
    public void store(Player player, Collection<ItemStack> leftovers, String source) {
        if (leftovers == null || leftovers.isEmpty()) {
            return;
        }
        deliver(profiles().profileKey(player), leftovers, source, Actor.system(BoxSource.clean(source)));
    }

    @Override
    public int cachedCount(UUID profileKey) {
        return counts.getOrDefault(profileKey, -1);
    }

    @Override
    public boolean full(UUID profileKey) {
        Integer c = counts.get(profileKey);
        return c != null && BoxCap.isFull(c, m.config().maxEntries);
    }

    @Override
    public void openStaffView(Player staff, UUID player, String name, Menu back) {
        if (!staff.hasPermission(BoxModule.PERM_ADMIN)) {
            m.msg().send(staff, "staff.no-permission");
            return;
        }
        profiles().loadProfiles(player).thenCombine(profiles().profileKeyAsync(player), (list, active) -> {
            List<UUID> keys = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Profile p : list) {
                keys.add(p.id());
                names.add(p.name());
            }
            if (keys.isEmpty()) {
                keys.add(player);
                names.add(name);
            }
            int index = Math.max(0, keys.indexOf(active));
            return new StaffBoxMenu.Target(player, name, keys, names, index);
        }).whenComplete((target, error) -> core.scheduler().main("box:staff-view", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Box: opening the staff view failed", Database.cause(error));
                m.msg().send(staff, "error");
                return;
            }
            if (staff.isOnline()) {
                StaffBoxMenu.open(m, staff, target, back);
                core.audit().logPlayer(AuditCategory.STAFF, AUDIT_VIEW, Actor.player(staff), player, name, null, null,
                        Map.of("profile", target.key().toString()));
            }
        }));
    }

    // ================================================================== guards

    /** Online on {@code profileKey}, not switching, not in staff mode. */
    Player receiver(UUID profileKey) {
        return profiles().onlinePlayer(profileKey).filter(this::canReceive).orElse(null);
    }

    boolean canReceive(Player p) {
        return p != null && p.isOnline() && !profiles().isSwitching(p.getUniqueId()) && !staffMode(p);
    }

    boolean staffMode(Player player) {
        return core.placeholders().has("staff_mode")
                && core.placeholders().text(player, "staff_mode").map("true"::equalsIgnoreCase).orElse(false);
    }

    UUID key(Player p) {
        return profiles().profileKey(p);
    }

    // ================================================================== notices

    private void delivered(UUID profileKey, int stacks, int count) {
        Player p = receiver(profileKey);
        if (p == null) {
            return;
        }
        if (m.config().notifyOnDelivery) {
            m.msg().send(p, "delivered", Msg.number("stacks", stacks), Msg.number("count", count));
            core.sounds().play(p, "notify");
        }
        actionBar(p, count);
        capWarning(p, count);
        if (core.menus().openMenu(p) instanceof BoxMenu menu) {
            menu.reload();
        }
    }

    private void actionBar(Player p, int count) {
        if (m.config().actionBar && count > 0) {
            m.msg().actionBar(p, "action-bar", Msg.number("count", count));
        }
    }

    private void capWarning(Player p, int count) {
        switch (BoxCap.of(count, m.config().maxEntries, m.config().warnPercent)) {
            case WARN -> m.msg().send(p, "cap.warn", capTags(count));
            case FULL -> m.msg().send(p, "cap.full", capTags(count));
            case OK -> {
            }
        }
    }

    TagResolver[] capTags(int count) {
        return new TagResolver[]{Msg.number("count", count), Msg.number("max", m.config().maxEntries)};
    }

    /** Loads the counts of all profiles of a player; then shows the join line for the active one. */
    private void loadAndGreet(Player player, boolean greet) {
        UUID uuid = player.getUniqueId();
        profiles().loadProfiles(uuid).thenCompose(list -> {
            Set<UUID> keys = new LinkedHashSet<>();
            keys.add(uuid);
            list.forEach(p -> keys.add(p.id()));
            return engine.counts(keys);
        }).whenComplete((loaded, error) -> core.scheduler().main("box:join", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Box: loading box counts of " + player.getName() + " failed", Database.cause(error));
                return;
            }
            Player now = Bukkit.getPlayer(uuid);
            if (now == null) {
                return;
            }
            counts.putAll(loaded);
            if (greet) {
                greet(now);
            }
        }));
    }

    /** Loads the counts of an online player without a chat line (module start / reload). */
    void loadQuietly(Player p) {
        loadAndGreet(p, false);
    }

    private void greet(Player p) {
        Integer count = counts.get(key(p));
        if (count == null || count <= 0) {
            return;
        }
        m.msg().send(p, "join", Msg.number("count", count));
        actionBar(p, count);
        capWarning(p, count);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        core.scheduler().later("box:join", m.config().joinDelayTicks, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                loadAndGreet(p, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        core.scheduler().later("box:switch", m.config().joinDelayTicks, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                loadAndGreet(p, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        counts.keySet().removeIf(k -> k.equals(uuid) || profiles().ownerOf(k).equals(uuid));
        clicks.forget(uuid);
    }

    /** A profile with items in its box can't be deleted: they would be lost. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProfileDelete(ProfileDeleteEvent event) {
        Integer count = counts.get(event.profile().id());
        if (count == null) {
            event.refuse(m.msg().component(event.getPlayer(), "profiles.delete-loading"));
        } else if (count > 0) {
            event.refuse(m.msg().component(event.getPlayer(), "profiles.delete-items", Msg.number("count", count)));
        }
    }

    // ================================================================== taking

    /**
     * Takes stacks out (remove-then-give): plans what fits, removes exactly those rows in one transaction, gives them,
     * and puts anything that did not fit (or everything, if the player left or switched meanwhile) back into the box.
     *
     * @param all  "Take all that fit" (skip stacks that don't fit) instead of one stack
     * @param then runs on the main thread when done (menu reload)
     */
    void take(Player p, List<BoxEntry> wanted, boolean all, Runnable then) {
        UUID uuid = p.getUniqueId();
        if (!clicks.tryClick(uuid, System.currentTimeMillis())) {
            return;
        }
        if (profiles().isSwitching(uuid)) {
            m.msg().send(p, "take.busy");
            return;
        }
        if (staffMode(p)) {
            m.msg().send(p, "take.staff-mode");
            core.sounds().play(p, "error");
            return;
        }
        UUID profile = key(p);
        if (wanted.isEmpty() || !taking.add(profile)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        List<BoxEntry> readable = new ArrayList<>();
        int unreadable = 0;
        for (BoxEntry e : wanted) {
            try {
                stacks.add(BoxItems.decode(e.bytes()));
                readable.add(e);
            } catch (RuntimeException ex) {
                unreadable++;
                core.plugin().getLogger().log(Level.WARNING, "Box: entry " + e.id() + " of " + profile + " is unreadable", ex);
            }
        }
        if (unreadable > 0) {
            m.msg().send(p, "take.unreadable", Msg.number("count", unreadable));
        }
        List<Integer> fit = BoxItems.fitting(p.getInventory(), stacks, !all);
        if (fit.isEmpty()) {
            taking.remove(profile);
            if (!readable.isEmpty()) {
                m.msg().send(p, "take.no-space");
                core.sounds().play(p, "error");
            }
            return;
        }
        List<Long> ids = new ArrayList<>();
        fit.forEach(i -> ids.add(readable.get(i).id()));
        String name = p.getName();
        engine.take(profile, ids).whenComplete((taken, error) -> core.scheduler().main("box:take", () -> {
            taking.remove(profile);
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Box: taking items of " + profile + " failed", Database.cause(error));
                Player now = Bukkit.getPlayer(uuid);
                if (now != null) {
                    m.msg().send(now, "error");
                }
                then.run();
                return;
            }
            give(uuid, name, profile, taken, wanted.size() - fit.size());
            then.run();
        }));
    }

    private void give(UUID uuid, String name, UUID profile, BoxEngine.Taken taken, int notTaken) {
        if (taken.entries().isEmpty()) {
            counts.computeIfPresent(profile, (k, v) -> taken.count());
            return;
        }
        Player now = receiver(profile);
        if (now == null) {
            // Left, switched profile or went into staff mode while the database worked: everything goes back.
            List<BoxItem> back = BoxEngine.items(taken.entries());
            core.audit().logPlayer(AuditCategory.ITEM, AUDIT_RETURNED, Actor.system("box"), uuid, name, (long) back.size(), null,
                    Map.of("profile", profile.toString(), "items", BoxItems.summary(back, 30)));
            deliverRows(profile, back, Actor.system("box"), false);
            return;
        }
        int given = 0;
        List<BoxItem> back = new ArrayList<>();
        List<BoxItem> took = new ArrayList<>();
        for (BoxEntry e : taken.entries()) {
            ItemStack stack;
            try {
                stack = BoxItems.decode(e.bytes());
            } catch (RuntimeException ex) {
                back.add(e.asItem());
                continue;
            }
            Collection<ItemStack> rest = now.getInventory().addItem(stack).values();
            if (rest.isEmpty()) {
                given++;
                took.add(e.asItem());
            } else {
                // The inventory changed meanwhile: what did not fit goes back with its original source and time.
                rest.forEach(r -> back.add(BoxItems.snapshot(r, e.source(), e.createdAt())));
                if (rest.stream().mapToInt(ItemStack::getAmount).sum() < e.amount()) {
                    took.add(e.asItem());
                }
            }
        }
        counts.put(profile, taken.count() + back.size());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("profile", profile.toString());
        details.put("ids", taken.entries().stream().map(BoxEntry::id).toList());
        details.put("items", BoxItems.summary(took, 30));
        details.put("returned", BoxItems.summary(back, 30));
        core.audit().logPlayer(AuditCategory.ITEM, AUDIT_TAKE, Actor.player(now), uuid, name, (long) given, now.getLocation(), details);
        if (!back.isEmpty()) {
            deliverRows(profile, back, Actor.system("box"), false);
            m.msg().send(now, "take.some-back", Msg.number("count", back.size()));
        }
        if (given > 0) {
            m.msg().send(now, "take.took", Msg.number("count", given), Msg.number("left", taken.count() + back.size()));
            core.sounds().play(now, "success");
        }
        if (notTaken > 0 && given > 0) {
            m.msg().send(now, "take.rest-no-space", Msg.number("count", notTaken));
        }
    }

    // ================================================================== admin

    /** Admin clear ({@code ids == null}) or remove of one entry: rows move to box_cleared, audited. */
    void archive(Player staff, StaffBoxMenu.Target target, List<Long> ids, Runnable then) {
        if (!staff.hasPermission(BoxModule.PERM_ADMIN)) {
            m.msg().send(staff, "staff.no-permission");
            return;
        }
        UUID profile = target.key();
        engine.archive(profile, ids, staff.getUniqueId(), staff.getName(), System.currentTimeMillis())
                .whenComplete((moved, error) -> core.scheduler().main("box:archive", () -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Box: clearing " + profile + " failed", Database.cause(error));
                        m.msg().send(staff, "error");
                        then.run();
                        return;
                    }
                    counts.computeIfPresent(profile, (k, v) -> Math.max(0, v - moved.size()));
                    List<BoxItem> items = BoxEngine.items(moved);
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("profile", profile.toString());
                    details.put("ids", moved.stream().map(BoxEntry::id).toList());
                    details.put("items", BoxItems.summary(items, 50));
                    details.put("restore", "rows kept in table box_cleared");
                    core.audit().logPlayer(AuditCategory.STAFF, ids == null ? AUDIT_CLEAR : AUDIT_REMOVE, Actor.player(staff),
                            target.player(), target.name(), (long) moved.size(), staff.getLocation(), details);
                    m.msg().send(staff, ids == null ? "staff.cleared" : "staff.removed", Msg.number("count", moved.size()),
                            Msg.p("player", target.name()));
                    Player owner = receiver(profile);
                    if (owner != null && core.menus().openMenu(owner) instanceof BoxMenu menu) {
                        menu.reload();
                    }
                    then.run();
                }));
    }

    /** Imports spooled deliveries (database was down). */
    void importSpool() {
        engine.importSpool().whenComplete((n, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Box: importing spooled deliveries failed; trying again later", Database.cause(error));
            } else if (n > 0) {
                core.plugin().getLogger().info("Box: imported " + n + " spooled stack(s) into the database");
                core.audit().log(AuditCategory.ITEM, "box_spool_import", Actor.system("box"), Map.of("stacks", n));
                core.scheduler().main("box:spool-refresh", () -> Bukkit.getOnlinePlayers().forEach(p -> loadAndGreet(p, false)));
            }
        });
    }

    boolean isTaking(UUID profile) {
        return taking.contains(profile);
    }

    Optional<Integer> count(UUID profile) {
        return Optional.ofNullable(counts.get(profile));
    }
}
