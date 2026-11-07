package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.progression.TaskStorage;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * The Island Vault (batch 4, 7 Nov 2026): {@code /is storage}, one chest shared by the island. Every member who opens it
 * looks at the <b>same</b> server inventory (no copies, so two members can't take the same item twice). Rows come from
 * the Island Vault upgrade ({@link IslandManager#vaultRows}).
 *
 * <p>Saving: a second after a change, when the last viewer closes, every minute and on shutdown (blocking). A task
 * hand-in from the storage ({@link TaskStorage}) takes the items and writes the storage and the task progress in one
 * database transaction; if that fails, the items go back. Bound items and island tools never go in (they would become
 * tradeable between members).</p>
 */
final class IslandVault implements Listener, TaskStorage {

    /** The island tool marker of the islandtools module (same key; island tools are personal). */
    static final NamespacedKey TOOL_KIND = ItemKeys.key("island_tool_kind");
    static final int MAX_ROWS = 6;
    private static final long SAVE_DELAY_TICKS = 20L;
    private static final long IDLE_UNLOAD_MILLIS = 5 * 60_000L;

    /** One loaded island storage; the holder of its inventory. */
    static final class Vault implements InventoryHolder {
        final UUID island;
        Inventory inventory;
        long version;
        long savedVersion;
        boolean saving;
        boolean saveQueued;
        long touchedAt = System.currentTimeMillis();

        Vault(UUID island) {
            this.island = island;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        boolean dirty() {
            return version > savedVersion;
        }
    }

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final IslandVaultStore store;
    private final ModuleMessages msg;
    private final Executor main;
    private final Map<UUID, Vault> vaults = new HashMap<>();
    private final Map<UUID, CompletableFuture<Vault>> loading = new HashMap<>();
    private Scheduler.Task autosave;

    IslandVault(IslandsModule module, IslandManager islands, IslandVaultStore store) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.store = store;
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
    }

    void start() {
        autosave = core.scheduler().repeat("islands:vault-save", 1200L, 1200L, this::autosave);
    }

    /** Shutdown: closes every view and writes every changed storage (blocking). */
    void stop() {
        if (autosave != null) {
            autosave.cancel();
            autosave = null;
        }
        for (Vault v : new ArrayList<>(vaults.values())) {
            for (HumanEntity viewer : new ArrayList<>(v.inventory.getViewers())) {
                viewer.closeInventory();
            }
            if (v.dirty()) {
                try {
                    store.saveNow(v.island, snapshot(v.inventory), System.currentTimeMillis());
                    v.savedVersion = v.version;
                } catch (RuntimeException e) {
                    core.plugin().getLogger().log(Level.SEVERE, "Island storage of " + v.island + " could not be saved on shutdown", e);
                }
            }
        }
        vaults.clear();
    }

    // ------------------------------------------------------------------ opening

    /** {@code /is storage}. */
    void open(Player player) {
        UUID key = islands.key(player);
        Optional<Island> own = islands.islandOfPlayer(key);
        if (own.isEmpty()) {
            msg.send(player, "vault.no-island");
            return;
        }
        Island island = own.get();
        if (!islands.roleAllows(island, key, IslandPerm.CONTAINERS)) {
            msg.send(player, "vault.no-permission");
            return;
        }
        int rows = islands.vaultRows(island.id());
        if (rows <= 0) {
            msg.send(player, "vault.locked");
            return;
        }
        vault(island.id(), rows).whenCompleteAsync((v, error) -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null || v == null) {
                core.plugin().getLogger().log(Level.WARNING, "Island storage of " + island.id() + " could not be loaded", error);
                msg.send(player, "vault.failed");
                return;
            }
            v.touchedAt = System.currentTimeMillis();
            player.openInventory(v.inventory);
        }, main);
    }

    /** The loaded storage (main thread), grown to {@code rows} when nobody is looking at it. */
    CompletableFuture<Vault> vault(UUID islandId, int rows) {
        Vault v = vaults.get(islandId);
        if (v != null) {
            int size = slots(rows, 0);
            if (size > v.inventory.getSize() && v.inventory.getViewers().isEmpty()) {
                Inventory bigger = create(v, size);
                ItemStack[] old = v.inventory.getContents();
                for (int i = 0; i < old.length; i++) {
                    bigger.setItem(i, old[i]);
                }
                v.inventory = bigger;
            }
            return CompletableFuture.completedFuture(v);
        }
        CompletableFuture<Vault> pending = loading.get(islandId);
        if (pending != null) {
            return pending;
        }
        CompletableFuture<Vault> load = store.load(islandId).thenApplyAsync(rowsBySlot -> {
            loading.remove(islandId);
            Vault existing = vaults.get(islandId);
            if (existing != null) {
                return existing;
            }
            int highest = rowsBySlot.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
            Vault fresh = new Vault(islandId);
            fresh.inventory = create(fresh, slots(rows, highest));
            for (Map.Entry<Integer, byte[]> e : rowsBySlot.entrySet()) {
                try {
                    fresh.inventory.setItem(e.getKey(), ItemStack.deserializeBytes(e.getValue()));
                } catch (RuntimeException ex) {
                    // Never open (and later overwrite) a storage with an item we could not read: staff must look first.
                    throw new IllegalStateException("Island storage of " + islandId + ": slot " + e.getKey() + " can't be read", ex);
                }
            }
            vaults.put(islandId, fresh);
            return fresh;
        }, main);
        load.whenCompleteAsync((loaded, error) -> {
            if (error != null) {
                loading.remove(islandId);
            }
        }, main);
        loading.put(islandId, load);
        return load;
    }

    /** Slots for {@code rows} rows, at least enough for the highest stored slot (never more than 6 rows). Pure. */
    static int slots(int rows, int highestSlot) {
        int need = Math.max(Math.max(1, rows), (highestSlot / 9) + 1);
        return Math.min(MAX_ROWS, need) * 9;
    }

    private Inventory create(Vault v, int size) {
        return Bukkit.createInventory(v, size, msg.fixed(Bukkit.getConsoleSender(), "vault.title"));
    }

    // ------------------------------------------------------------------ guarding and saving

    /** Items that never go into the shared storage: bound items and island tools. Pure apart from the item's data. */
    static boolean forbidden(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return BoundItems.containsBound(stack) || (stack.hasItemMeta() && stack.getPersistentDataContainer().has(TOOL_KIND)); // shulkers and bundles too
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Vault v)) {
            return;
        }
        boolean top = event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory());
        ItemStack incoming = null;
        InventoryAction action = event.getAction();
        if (top && event.getClick() == ClickType.SWAP_OFFHAND) {
            incoming = event.getWhoClicked().getInventory().getItemInOffHand();
        } else if (top && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            incoming = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
        } else if (top) {
            switch (action) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> incoming = event.getCursor();
                default -> { }
            }
        } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            incoming = event.getCurrentItem();
        }
        if (forbidden(incoming)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                msg.actionBar(p, "vault.not-allowed");
            }
            return;
        }
        changed(v);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Vault v)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean intoTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (intoTop && forbidden(event.getOldCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                msg.actionBar(p, "vault.not-allowed");
            }
            return;
        }
        if (intoTop) {
            changed(v);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault v)) {
            return;
        }
        v.touchedAt = System.currentTimeMillis();
        if (v.dirty()) {
            save(v); // the last change is written now, not a second later
        }
    }

    /** A change in the storage: saved one second later (several clicks, one write). */
    private void changed(Vault v) {
        v.version++;
        v.touchedAt = System.currentTimeMillis();
        if (!v.saveQueued) {
            v.saveQueued = true;
            core.scheduler().later("islands:vault-change", SAVE_DELAY_TICKS, () -> {
                v.saveQueued = false;
                save(v);
            });
        }
    }

    /** Writes the storage (main thread snapshot, async write); one write at a time, the newest state wins. */
    private void save(Vault v) {
        if (!v.dirty()) {
            return;
        }
        if (v.saving) {
            v.saveQueued = true; // saved again when the running write is done
            return;
        }
        v.saving = true;
        long version = v.version;
        Map<Integer, byte[]> snapshot = snapshot(v.inventory);
        store.save(v.island, snapshot, System.currentTimeMillis()).whenCompleteAsync((ok, error) -> {
            v.saving = false;
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island storage of " + v.island + " could not be saved (tried again in a minute)", error);
                return;
            }
            v.savedVersion = Math.max(v.savedVersion, version);
            if (v.saveQueued && v.dirty()) {
                v.saveQueued = false;
                save(v);
            }
        }, main);
    }

    private void autosave() {
        long now = System.currentTimeMillis();
        for (Vault v : new ArrayList<>(vaults.values())) {
            if (v.dirty()) {
                save(v);
            } else if (!v.saving && v.inventory.getViewers().isEmpty() && now - v.touchedAt > IDLE_UNLOAD_MILLIS) {
                vaults.remove(v.island); // read again from the database next time
            }
        }
    }

    static Map<Integer, byte[]> snapshot(Inventory inventory) {
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].isEmpty()) {
                out.put(i, contents[i].serializeAsBytes());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ task hand-in (TaskStorage)

    /** Same rule as the inventory hand-in: {@code custom:<id>} = that custom item, a material = the plain vanilla item. */
    static boolean matches(ItemStack stack, String target) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || target == null) {
            return false;
        }
        if (target.startsWith("custom:")) {
            return ItemRegistry.is(stack, target.substring("custom:".length())) && ItemRegistry.uuidOf(stack).isEmpty();
        }
        Material m = Material.matchMaterial(target);
        return m != null && stack.getType() == m && !ItemRegistry.isCustom(stack) && stack.isSimilar(new ItemStack(m));
    }

    @Override
    public long count(UUID islandId, String itemKey) {
        Vault v = vaults.get(islandId);
        if (v == null) {
            int rows = islands.vaultRows(islandId);
            if (rows > 0) {
                vault(islandId, rows); // loads it; the hand-in screen refreshes and sees it
            }
            return 0;
        }
        long n = 0;
        for (ItemStack stack : v.inventory.getContents()) {
            if (matches(stack, itemKey)) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    @Override
    public CompletableFuture<Long> take(UUID islandId, String itemKey, long max, Actor actor, Progress progress) {
        Vault v = vaults.get(islandId);
        if (v == null || max <= 0) {
            return CompletableFuture.completedFuture(0L);
        }
        Map<Integer, ItemStack> removed = new LinkedHashMap<>();
        long left = max;
        ItemStack[] contents = v.inventory.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(stack, itemKey)) {
                continue;
            }
            int take = (int) Math.min(left, stack.getAmount());
            ItemStack part = stack.clone();
            part.setAmount(take);
            removed.put(i, part);
            if (take >= stack.getAmount()) {
                v.inventory.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                v.inventory.setItem(i, stack);
            }
            left -= take;
        }
        long taken = max - left;
        if (taken <= 0) {
            return CompletableFuture.completedFuture(0L);
        }
        v.version++;
        long version = v.version;
        Map<Integer, byte[]> snapshot = snapshot(v.inventory);
        long now = System.currentTimeMillis();
        CompletableFuture<Long> result = new CompletableFuture<>();
        core.database().transaction(c -> {
            IslandVaultStore.save(c, islandId, snapshot, now);
            progress.apply(c, taken);
            return taken;
        }).whenCompleteAsync((n, error) -> {
            if (error != null) {
                giveBack(v, removed);
                result.completeExceptionally(error);
                return;
            }
            v.savedVersion = Math.max(v.savedVersion, version);
            result.complete(n);
        }, main);
        return result;
    }

    /** A failed hand-in: the items go back to their slots (or anywhere free); what does not fit is logged for staff. */
    private void giveBack(Vault v, Map<Integer, ItemStack> removed) {
        List<ItemStack> rest = new ArrayList<>();
        removed.forEach((slot, stack) -> {
            ItemStack now = v.inventory.getItem(slot);
            if (now == null || now.isEmpty()) {
                v.inventory.setItem(slot, stack);
            } else if (now.isSimilar(stack) && now.getAmount() + stack.getAmount() <= now.getMaxStackSize()) {
                now.setAmount(now.getAmount() + stack.getAmount());
                v.inventory.setItem(slot, now);
            } else {
                rest.add(stack);
            }
        });
        for (ItemStack left : v.inventory.addItem(rest.toArray(new ItemStack[0])).values()) {
            core.plugin().getLogger().severe("Island storage of " + v.island + ": " + left.getAmount() + "x " + left.getType()
                    + " could not go back after a failed task hand-in (storage full); staff can give it back");
        }
        changed(v);
    }
}
