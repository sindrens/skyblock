package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.DupeReport;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Worn pieces at runtime (WARDROBE §4.2-§4.3): per-player sessions, wearing and taking off (database first, then the
 * items), reconcile (copies deleted, missing pieces re-created), profile switches (pieces stripped before the profile's
 * inventory is saved, the new profile's pieces rendered after the load) and the bonus cache. Main thread.
 */
final class Equipment implements Listener {

    private final WardrobeModule module;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> reconcileQueued = new HashSet<>();
    private Scheduler.Task periodic;

    Equipment(WardrobeModule module) {
        this.module = module;
    }

    void start() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
        periodic = module.core.scheduler().repeat("wardrobe-reconcile", 600L, 600L, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                reconcile(p);
            }
        });
    }

    void stop() {
        if (periodic != null) {
            periodic.cancel();
        }
        sessions.clear();
    }

    Optional<Session> session(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    Session sessionOrNull(UUID player) {
        return sessions.get(player);
    }

    ProfileService profiles() {
        return ProfileService.of(module.core.services());
    }

    // ------------------------------------------------------------------ loading

    /** (Re)loads the player's wardrobe for their active profile, then reconciles and recovers the chest. */
    void load(Player player) {
        UUID uuid = player.getUniqueId();
        UUID profile = profiles().profileKey(player);
        Session s = sessions.computeIfAbsent(uuid, u -> new Session(u, profile));
        s.generation++;
        int generation = s.generation;
        s.loaded = false;
        s.profile = profile;
        int season = module.config().seasonId;
        WardrobeStore store = module.store();
        LoadoutStore loadouts = module.loadoutStore();
        module.core.database().async(c -> new Object[]{store.activeEntries(c, profile), store.equipped(c, profile),
                store.sigils(c, uuid), store.claims(c, profile, season), loadouts.loadouts(c, profile), loadouts.bought(c, uuid), store.pieceEnchants(c, profile)})
                .whenComplete((data, error) -> module.core.scheduler().main("wardrobe-load", () -> {
            Player p = Bukkit.getPlayer(uuid);
            Session current = sessions.get(uuid);
            if (p == null || current != s || s.generation != generation) {
                return;
            }
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Wardrobe: could not load " + p.getName(), Database.cause(error));
                module.core.scheduler().later("wardrobe-load-retry", 100L, () -> {
                    if (p.isOnline() && sessions.get(uuid) == s && s.generation == generation) {
                        load(p);
                    }
                });
                return;
            }
            if (!profiles().profileKey(p).equals(profile)) {
                load(p); // switched meanwhile
                return;
            }
            @SuppressWarnings("unchecked")
            List<WardrobeStore.Entry> entries = (List<WardrobeStore.Entry>) data[0];
            @SuppressWarnings("unchecked")
            Map<ArmorPiece, WardrobeStore.Equipped> equipped = (Map<ArmorPiece, WardrobeStore.Equipped>) data[1];
            @SuppressWarnings("unchecked")
            Set<String> sigils = (Set<String>) data[2];
            @SuppressWarnings("unchecked")
            Set<String> claims = (Set<String>) data[3];
            s.entries = List.copyOf(entries);
            s.equipped = new EnumMap<>(ArmorPiece.class);
            s.equipped.putAll(equipped);
            s.sigils = Set.copyOf(sigils);
            s.claims = Set.copyOf(claims);
            @SuppressWarnings("unchecked")
            List<Loadout> presets = (List<Loadout>) data[4];
            s.loadouts = List.copyOf(presets);
            s.boughtSlots = (Integer) data[5];
            @SuppressWarnings("unchecked")
            Map<UUID, Map<ArmorPiece, List<net.skybornmc.skyblock.common.tools.ToolPerk>>> enchants =
                    (Map<UUID, Map<ArmorPiece, List<net.skybornmc.skyblock.common.tools.ToolPerk>>>) data[6];
            s.enchants = new java.util.HashMap<>(enchants);
            s.loaded = true;
            s.stripped = false;
            module.leveling().applyPending(s);
            refreshBonuses(s);
            reconcile(p);
            module.chest().recover(p);
            module.shop().cleanTokens(p);
            module.tomes().clean(p);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        reconcileQueued.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ profile switch (§4.3)

    /** Pieces are stripped before the profiles module saves this profile's inventory (it runs right after this event). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwitch(ProfileSwitchEvent event) {
        Player player = event.getPlayer();
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        strip(player);
        s.stripped = true;
        UUID oldProfile = s.profile;
        int generation = s.generation;
        // If the switch fails (cursor item, database error), the profile stays: put the pieces back once it is over.
        long[] waited = {0};
        Scheduler.Task[] task = new Scheduler.Task[1];
        task[0] = module.core.scheduler().repeat("wardrobe-switch-watch", 10L, 10L, () -> {
            waited[0] += 10;
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || sessions.get(s.player) != s || s.generation != generation || waited[0] > 20L * 120) {
                task[0].cancel();
                return;
            }
            if (profiles().isSwitching(s.player)) {
                return;
            }
            task[0].cancel();
            if (profiles().profileKey(p).equals(oldProfile) && s.stripped) {
                s.stripped = false;
                reconcile(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        load(event.getPlayer());
    }

    /** Refuses a profile switch while a wardrobe action is saving. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwitchBusy(ProfileSwitchEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && s.busy) {
            event.refuse(module.msg().component(event.getPlayer(), "busy"));
        }
    }

    /** Re-renders worn pieces from the session rows (after a loadout swap wrote new rows). Public for {@link Loadouts}. */
    void renderWorn(Player player, Session s) {
        for (WardrobeStore.Equipped row : List.copyOf(s.equipped.values())) {
            render(player, s, row); // a gone entry is cleaned by the next reconcile
        }
    }

    /** Removes every wardrobe piece from the player's armor slots (the rows stay). */
    void strip(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ArmorPiece p : ArmorPiece.values()) {
            if (WardrobeItems.isPiece(inv.getItem(p.slot()))) {
                inv.setItem(p.slot(), null);
            }
        }
    }

    // ------------------------------------------------------------------ death, respawn, reconcile triggers

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(WardrobeItems::isPiece);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        queueReconcile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            queueReconcile(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            queueReconcile(p);
        }
    }

    /** DupeDetector scans: check the players involved as well. */
    void onDupe(DupeReport report) {
        for (UUID id : report.playerIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                queueReconcile(p);
            }
        }
    }

    void queueReconcile(Player player) {
        if (reconcileQueued.add(player.getUniqueId())) {
            module.core.scheduler().later("wardrobe-reconcile-one", 1L, () -> {
                reconcileQueued.remove(player.getUniqueId());
                if (player.isOnline()) {
                    reconcile(player);
                }
            });
        }
    }

    // ------------------------------------------------------------------ reconcile (§4.3)

    /**
     * Deletes every invalid piece the player holds (inventory, cursor, ender chest, open container), re-creates missing
     * worn pieces and drops rows whose slot now holds vanilla armor. Skipped while loading, switching or busy.
     */
    void reconcile(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.loaded || s.stripped || s.busy || profiles().isSwitching(player.getUniqueId())) {
            return;
        }
        if (!profiles().profileKey(player).equals(s.profile)) {
            load(player);
            return;
        }
        PlayerInventory inv = player.getInventory();
        List<String> alerts = new ArrayList<>();
        Map<ArmorPiece, ReconcileRules.Slot> slots = new EnumMap<>(ArmorPiece.class);
        Map<Integer, ArmorPiece> armorIndex = new HashMap<>();
        for (ArmorPiece p : ArmorPiece.values()) {
            armorIndex.put(armorSlotIndex(p), p);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            ArmorPiece armor = armorIndex.get(i);
            Optional<WardrobeItems.PieceTag> tag = WardrobeItems.tag(item);
            if (tag.isEmpty()) {
                if (armor != null) {
                    slots.put(armor, item == null || item.isEmpty() ? ReconcileRules.Slot.EMPTY : ReconcileRules.Slot.OTHER);
                }
                continue;
            }
            ReconcileRules.Verdict v = ReconcileRules.decide(tag.get(), armor, s.profile, s.equipped);
            if (v == ReconcileRules.Verdict.KEEP) {
                slots.put(armor, ReconcileRules.Slot.VALID);
                continue;
            }
            inv.setItem(i, null);
            if (armor != null) {
                slots.put(armor, ReconcileRules.Slot.EMPTY);
            }
            stale(player, tag.get(), armor != null ? "armor " + armor.key() : "inventory slot " + i, v, alerts);
        }
        if (WardrobeItems.isPiece(player.getItemOnCursor())) {
            WardrobeItems.tag(player.getItemOnCursor()).ifPresent(t -> stale(player, t, "cursor", ReconcileRules.Verdict.DELETE_ALERT, alerts));
            player.setItemOnCursor(null);
        }
        sweep(player, player.getEnderChest(), "ender chest", alerts);
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER
                && !(top.getHolder(false) instanceof net.skybornmc.skyblock.common.menu.Menu)) {
            sweep(player, top, "open " + top.getType().name().toLowerCase(java.util.Locale.ROOT), alerts);
        }
        // Fill: re-create missing pieces, drop rows whose slot holds vanilla armor now.
        List<ArmorPiece> drop = new ArrayList<>();
        for (Map.Entry<ArmorPiece, ReconcileRules.Fill> f : ReconcileRules.fill(s.equipped, slots).entrySet()) {
            WardrobeStore.Equipped row = s.equipped.get(f.getKey());
            switch (f.getValue()) {
                case RENDER -> {
                    if (!render(player, s, row)) {
                        drop.add(f.getKey());
                    }
                }
                case DROP_ROW -> drop.add(f.getKey());
                case NONE -> {
                }
            }
        }
        if (!drop.isEmpty()) {
            drop.forEach(s.equipped::remove);
            refreshBonuses(s);
            UUID profile = s.profile;
            module.ops().equip(profile, List.of(), drop).thenAccept(r -> {
                if (!r.ok()) {
                    module.core.plugin().getLogger().warning("Wardrobe: could not clear worn slots " + drop + " of " + player.getName());
                }
            });
            module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_unequip", Actor.system("wardrobe"), player.getUniqueId(),
                    player.getName(), null, null, Map.of("slots", drop.stream().map(ArmorPiece::key).toList(), "reason", "slot_taken"));
        }
        if (!alerts.isEmpty() && module.config().alertStaff) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(DupeDetector.PERM_NOTIFY)) {
                    module.msg().send(staff, "staff-copy-alert", Msg.p("player", player.getName()), Msg.p("where", String.join(", ", alerts)));
                }
            }
        }
    }

    private void sweep(Player player, Inventory inventory, String where, List<String> alerts) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            Optional<WardrobeItems.PieceTag> tag = WardrobeItems.tag(item);
            if (tag.isPresent()) {
                inventory.setItem(i, null);
                stale(player, tag.get(), where + " slot " + i, ReconcileRules.Verdict.DELETE_ALERT, alerts);
            }
        }
    }

    private void stale(Player player, WardrobeItems.PieceTag tag, String where, ReconcileRules.Verdict verdict, List<String> alerts) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("where", where);
        details.put("set", String.valueOf(tag.setId()));
        details.put("entry", String.valueOf(tag.entry()));
        details.put("alert", verdict == ReconcileRules.Verdict.DELETE_ALERT);
        module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_stale_piece", Actor.system("wardrobe"), player.getUniqueId(),
                player.getName(), null, player.getLocation(), details);
        if (verdict == ReconcileRules.Verdict.DELETE_ALERT) {
            alerts.add(where);
        }
    }

    /** Vanilla's raw inventory index of an armor slot (36 boots … 39 helmet). */
    static int armorSlotIndex(ArmorPiece piece) {
        return switch (piece) {
            case FEET -> 36;
            case LEGS -> 37;
            case CHEST -> 38;
            case HEAD -> 39;
        };
    }

    /** Puts a fresh view item of a worn row into its slot. False if the entry or set is gone. */
    private boolean render(Player player, Session s, WardrobeStore.Equipped row) {
        Optional<WardrobeStore.Entry> entry = s.entry(row.entryId());
        Optional<SetDef> def = entry.flatMap(e -> module.registry().get(e.lookSetId() != null ? e.lookSetId() : e.setId()));
        if (entry.isEmpty() || def.isEmpty()) {
            return false;
        }
        player.getInventory().setItem(row.piece().slot(), module.items().piece(def.get(), entry.get(), row.piece(), s.profile, row.nonce(), s.warzone,
                s.enchants(entry.get().entryId(), row.piece())));
        return true;
    }

    /**
     * Re-renders worn pieces in place (same nonce): after a level-up (trim, glint, lore) or a world change (warzone armor).
     * Only slots that hold the valid piece are touched.
     */
    void rerender(Player player, Session s, java.util.function.Predicate<UUID> entries) {
        if (s == null || !s.loaded || s.stripped) {
            return;
        }
        for (WardrobeStore.Equipped row : s.equipped.values()) {
            if (!entries.test(row.entryId())) {
                continue;
            }
            Optional<WardrobeItems.PieceTag> tag = WardrobeItems.tag(player.getInventory().getItem(row.piece().slot()));
            if (tag.isPresent() && row.nonce().equals(tag.get().nonce()) && row.entryId().equals(tag.get().entry())) {
                render(player, s, row);
            }
        }
    }

    /** Warzone rules on/off when the player changes world (bonuses and armor points). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.loaded) {
            return;
        }
        boolean before = s.warzone;
        refreshBonuses(s);
        if (before != s.warzone) {
            rerender(player, s, id -> true);
        }
    }

    // ------------------------------------------------------------------ wear / take off

    /** Why the player can't change their wardrobe now (message key), or null. */
    String problem(Player player, Session s) {
        if (s == null || !s.loaded) {
            return "loading";
        }
        if (s.busy || s.stripped || profiles().isSwitching(player.getUniqueId())) {
            return "busy";
        }
        if (module.frozen(player)) {
            return "frozen";
        }
        if (module.config().blockedWorld(player.getWorld().getName())) {
            return "blocked-world";
        }
        if (module.combatLeft(player, module.config().equipCombatSeconds) > 0) {
            return "in-combat"; // LD6: the loadout place rules apply to single pieces too
        }
        return null;
    }

    private boolean checkCooldown(Player player, Session s) {
        long left = s.lastEquip + module.config().equipCooldownSeconds * 1000L - System.currentTimeMillis();
        if (left > 0 && !player.hasPermission("skyblock.cooldown.bypass")) {
            module.fail(player, "cooldown", Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, left)))));
            return false;
        }
        return true;
    }

    /** Wears pieces of an owned entry (WARDROBE §6.2); vanilla armor in those slots moves to the inventory. */
    void wear(Player player, WardrobeStore.Entry entry, Collection<ArmorPiece> pieces) {
        Session s = sessions.get(player.getUniqueId());
        String problem = problem(player, s);
        if (problem != null) {
            module.fail(player, problem);
            return;
        }
        if (s.entry(entry.entryId()).isEmpty()) {
            module.fail(player, "not-owned");
            return;
        }
        if (!checkCooldown(player, s)) {
            return;
        }
        List<ArmorPiece> todo = pieces.stream().filter(p -> {
            WardrobeStore.Equipped q = s.equipped.get(p);
            return q == null || !q.entryId().equals(entry.entryId());
        }).toList();
        if (todo.isEmpty()) {
            module.fail(player, "already-worn");
            return;
        }
        PlayerInventory inv = player.getInventory();
        int needed = 0;
        String vanillaName = null;
        for (ArmorPiece p : todo) {
            ItemStack current = inv.getItem(p.slot());
            if (current != null && !current.isEmpty() && !WardrobeItems.isPiece(current)) {
                needed++;
                vanillaName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(current.effectiveName());
            }
        }
        if (needed > freeSlots(inv)) {
            module.fail(player, "make-room", Msg.p("item", vanillaName == null ? "" : vanillaName));
            return;
        }
        List<WardrobeStore.Equipped> wear = new ArrayList<>();
        for (ArmorPiece p : todo) {
            wear.add(new WardrobeStore.Equipped(p, entry.entryId(), UUID.randomUUID()));
        }
        s.busy = true;
        UUID profile = s.profile;
        int generation = s.generation;
        module.ops().equip(profile, wear, List.of()).thenAccept(r -> module.core.scheduler().main("wardrobe-wear", () -> {
            s.busy = false;
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || sessions.get(s.player) != s || s.generation != generation) {
                return; // reloads render from the rows
            }
            if (!r.ok()) {
                module.fail(p, r.status() == WardrobeOps.Status.GONE ? "not-owned" : "error");
                load(p);
                return;
            }
            List<ArmorPiece> blocked = new ArrayList<>();
            for (WardrobeStore.Equipped q : wear) {
                ItemStack current = p.getInventory().getItem(q.piece().slot());
                if (current != null && !current.isEmpty() && !WardrobeItems.isPiece(current)) {
                    Map<Integer, ItemStack> left = p.getInventory().addItem(current);
                    if (!left.isEmpty()) {
                        blocked.add(q.piece()); // filled meanwhile: keep the vanilla piece, drop the row
                        continue;
                    }
                }
                s.equipped.put(q.piece(), q);
                render(p, s, q);
            }
            if (!blocked.isEmpty()) {
                module.ops().equip(profile, List.of(), blocked);
            }
            s.lastEquip = System.currentTimeMillis();
            refreshBonuses(s);
            String setName = module.registry().get(entry.setId()).map(SetDef::name).orElse(entry.setId());
            module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_equip", Actor.player(p), p.getUniqueId(), p.getName(), null, null,
                    Map.of("set", entry.setId(), "entry", entry.entryId().toString(), "slots",
                            wear.stream().map(q -> q.piece().key()).filter(k -> blocked.stream().noneMatch(b -> b.key().equals(k))).toList(),
                            "profile", profile.toString()));
            module.msg().send(p, "worn", Msg.p("set", setName), Msg.p("count", s.worn(entry.entryId())));
            module.core.sounds().play(p, "success");
            module.menus().refreshOpen(p);
        }));
    }

    /** Takes pieces off (they stay in the wardrobe). */
    void takeOff(Player player, Collection<ArmorPiece> pieces) {
        Session s = sessions.get(player.getUniqueId());
        String problem = problem(player, s);
        if (problem != null) {
            module.fail(player, problem);
            return;
        }
        List<ArmorPiece> todo = pieces.stream().filter(s.equipped::containsKey).toList();
        if (todo.isEmpty()) {
            module.fail(player, "nothing-worn");
            return;
        }
        if (!checkCooldown(player, s)) {
            return;
        }
        s.busy = true;
        UUID profile = s.profile;
        int generation = s.generation;
        module.ops().equip(profile, List.of(), todo).thenAccept(r -> module.core.scheduler().main("wardrobe-off", () -> {
            s.busy = false;
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || sessions.get(s.player) != s || s.generation != generation) {
                return;
            }
            if (!r.ok()) {
                module.fail(p, "error");
                return;
            }
            for (ArmorPiece piece : todo) {
                s.equipped.remove(piece);
                if (WardrobeItems.isPiece(p.getInventory().getItem(piece.slot()))) {
                    p.getInventory().setItem(piece.slot(), null);
                }
            }
            s.lastEquip = System.currentTimeMillis();
            refreshBonuses(s);
            module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_unequip", Actor.player(p), p.getUniqueId(), p.getName(), null, null,
                    Map.of("slots", todo.stream().map(ArmorPiece::key).toList(), "profile", profile.toString()));
            module.msg().send(p, "taken-off", Msg.p("count", todo.size()));
            module.core.sounds().play(p, "click");
            module.menus().refreshOpen(p);
        }));
    }

    static int freeSlots(PlayerInventory inv) {
        int free = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (item == null || item.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    // ------------------------------------------------------------------ bonuses

    /**
     * Recomputes the cached bonuses from the worn rows (never per block). In a warzone world every set counts at 1× and
     * only the warzone families work (WD18).
     */
    void refreshBonuses(Session s) {
        List<SetBonuses.Worn> worn = new ArrayList<>();
        for (WardrobeStore.Equipped q : s.equipped.values()) {
            s.entry(q.entryId()).ifPresent(e -> worn.add(new SetBonuses.Worn(e.setId(), e.mergedSetId(), e.level())));
        }
        Player player = Bukkit.getPlayer(s.player);
        s.warzone = player != null && module.config().warzoneWorld(player.getWorld().getName());
        s.bonuses = SetBonuses.compute(module.registry(), worn, module::bonusMultiplier, s.warzone ? module.config().normalization() : null);
    }

    /** After a registry/config reload. */
    void refreshAll() {
        for (Session s : sessions.values()) {
            refreshBonuses(s);
            Player p = Bukkit.getPlayer(s.player);
            if (p != null) {
                rerender(p, s, id -> true);
            }
        }
    }

    Collection<Session> sessions() {
        return sessions.values();
    }
}
