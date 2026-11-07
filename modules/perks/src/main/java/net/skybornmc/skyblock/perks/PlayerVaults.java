package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.store.CreditShop;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * {@code /pv [n]} (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 2): private vaults per account. How many: the highest
 * {@code skyblock.perks.pv.<n>} (ranks: Hatchling 1 … Immortal 4) plus extra vaults bought in the credit shop
 * ({@link #EXTRA}), at most {@code vaults.max}. A vault above that number (a rank that went away) can still be emptied
 * but nothing can be put in. Saved a second after a change, on close and on shutdown (like the Island Vault).
 */
final class PlayerVaults implements Listener {

    static final String PERM = "skyblock.perks.pv";
    /** Credit shop account thing: one extra vault. */
    static final String EXTRA = "pv_extra";

    /** One open vault; the holder of its inventory. */
    static final class Vault implements InventoryHolder {
        final UUID account;
        final int number;
        Inventory inventory;
        boolean takeOutOnly;
        long version;
        long savedVersion;
        boolean saving;
        boolean saveQueued;

        Vault(UUID account, int number) {
            this.account = account;
            this.number = number;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        boolean dirty() {
            return version > savedVersion;
        }
    }

    private final Core core;
    private final PerksConfig config;
    private final ModuleMessages msg;
    private final Executor main;
    private final Map<String, Vault> open = new HashMap<>();

    PlayerVaults(Core core, PerksConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.main = new MainThread(core.plugin());
    }

    void register() {
        net.skybornmc.skyblock.common.command.Arg<Integer> number = Args.integer("number", 1, 9, "1", "2", "3");
        core.commands().register(CommandSpec.command("pv").description("Your private vaults (from your rank or the store)")
                .category("Perks").permission(PERM).playerOnly().aliases("vaults", "playervault")
                .optional(number)
                .example("pv 2")
                .executes(ctx -> command(ctx.player(), ctx.getOr(number, null))));
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        if (config.vaultsExtraCredits > 0 && config.vaultsExtraMax > 0) {
            CreditShop.register(PerksModule.ID, CreditShop.Offer.of("pv_extra", CreditShop.Tab.EXTRAS, 55,
                    (viewer, receiver) -> core.menus().item(Material.ENDER_CHEST, viewer, PerksModule.ID, "vaults.store",
                            Msg.number("have", count(receiver)), Msg.number("bought", bought(receiver)), Msg.number("max", config.vaultsExtraMax)),
                    (viewer, receiver) -> bought(receiver) >= config.vaultsExtraMax || count(receiver) >= config.vaultsMax ? -1L : config.vaultsExtraCredits,
                    false, true, (buyer, receiver, purchaseId) -> CreditShop.of(core.services())
                            .map(s -> s.giveThing(EXTRA).deliver(buyer, receiver, purchaseId))
                            .orElse(CompletableFuture.completedFuture(false))));
        }
    }

    /** Shutdown: closes the views and writes changed vaults (blocking). */
    void stop() {
        CreditShop.unregisterAll(PerksModule.ID);
        for (Vault v : new ArrayList<>(open.values())) {
            for (HumanEntity viewer : new ArrayList<>(v.inventory.getViewers())) {
                viewer.closeInventory();
            }
            if (v.dirty()) {
                try {
                    Map<Integer, byte[]> snap = snapshot(v.inventory);
                    core.database().blockingTransaction(c -> {
                        save(c, v.account, v.number, snap, System.currentTimeMillis());
                        return null;
                    });
                } catch (RuntimeException e) {
                    core.plugin().getLogger().log(Level.SEVERE, "Vault " + v.number + " of " + v.account + " could not be saved on shutdown", e);
                }
            }
        }
        open.clear();
    }

    int bought(Player p) {
        return CreditShop.of(core.services()).map(s -> s.owned(p.getUniqueId(), EXTRA)).orElse(0);
    }

    /** Vaults this player may fill: the rank's number plus bought ones, at most vaults.max. */
    int count(Player p) {
        int rank = 0;
        for (int n = config.vaultsMax; n >= 1; n--) {
            if (p.hasPermission(PERM + "." + n)) {
                rank = n;
                break;
            }
        }
        return Math.min(config.vaultsMax, rank + Math.min(config.vaultsExtraMax, bought(p)));
    }

    private void command(Player p, Integer number) {
        if (!config.vaultsEnabled) {
            msg.send(p, "vaults.disabled");
            return;
        }
        int count = count(p);
        if (number == null) {
            if (count <= 1) {
                openVault(p, 1);
            } else {
                core.menus().open(p, new PickMenu());
            }
            return;
        }
        openVault(p, number);
    }

    void openVault(Player p, int number) {
        int count = count(p);
        if (number < 1 || number > config.vaultsMax) {
            msg.send(p, "vaults.no-such", Msg.number("max", config.vaultsMax));
            return;
        }
        boolean takeOut = number > count;
        UUID account = p.getUniqueId();
        String key = account + ":" + number;
        Vault live = open.get(key);
        if (live != null) {
            live.takeOutOnly = takeOut;
            p.openInventory(live.inventory);
            return;
        }
        core.database().async(c -> load(c, account, number)).whenCompleteAsync((slots, error) -> {
            if (!p.isOnline()) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Vault " + number + " of " + p.getName() + " could not be loaded", error);
                msg.send(p, "vaults.failed");
                return;
            }
            if (takeOut && slots.isEmpty()) {
                msg.send(p, "vaults.locked", Msg.number("number", number), Msg.number("count", count));
                return;
            }
            Vault v = open.computeIfAbsent(key, k -> new Vault(account, number));
            if (v.inventory == null) {
                v.inventory = Bukkit.createInventory(v, Math.max(1, config.vaultsRows) * 9,
                        msg.component(p, takeOut ? "vaults.title-locked" : "vaults.title", Msg.number("number", number)));
                for (Map.Entry<Integer, byte[]> e : slots.entrySet()) {
                    if (e.getKey() < v.inventory.getSize()) {
                        v.inventory.setItem(e.getKey(), ItemStack.deserializeBytes(e.getValue()));
                    }
                }
            }
            v.takeOutOnly = takeOut;
            p.openInventory(v.inventory);
        }, main);
    }

    // ------------------------------------------------------------------ guarding and saving

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Vault v)) {
            return;
        }
        if (v.takeOutOnly) {
            boolean top = event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory());
            InventoryAction a = event.getAction();
            boolean putsIn = (top && (a == InventoryAction.PLACE_ALL || a == InventoryAction.PLACE_ONE || a == InventoryAction.PLACE_SOME
                    || a == InventoryAction.SWAP_WITH_CURSOR || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND))
                    || (!top && a == InventoryAction.MOVE_TO_OTHER_INVENTORY);
            if (putsIn) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player p) {
                    msg.actionBar(p, "vaults.take-out-only");
                }
                return;
            }
        }
        changed(v);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Vault v)) {
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(s -> s < size)) {
            if (v.takeOutOnly) {
                event.setCancelled(true);
                return;
            }
            changed(v);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault v)) {
            return;
        }
        save(v);
        if (v.inventory.getViewers().size() <= 1 && !v.dirty()) {
            core.scheduler().later("perks:vault-unload", 20L, () -> {
                if (v.inventory.getViewers().isEmpty() && !v.dirty() && !v.saving) {
                    open.remove(v.account + ":" + v.number);
                }
            });
        }
    }

    private void changed(Vault v) {
        v.version++;
        if (!v.saveQueued) {
            v.saveQueued = true;
            core.scheduler().later("perks:vault-change", 20L, () -> {
                v.saveQueued = false;
                save(v);
            });
        }
    }

    private void save(Vault v) {
        if (!v.dirty()) {
            return;
        }
        if (v.saving) {
            v.saveQueued = true;
            return;
        }
        v.saving = true;
        long version = v.version;
        Map<Integer, byte[]> snap = snapshot(v.inventory);
        long now = System.currentTimeMillis();
        core.database().transaction(c -> {
            save(c, v.account, v.number, snap, now);
            return null;
        }).whenCompleteAsync((ok, error) -> {
            v.saving = false;
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Vault " + v.number + " of " + v.account + " could not be saved (tried again on the next change)", error);
                return;
            }
            v.savedVersion = Math.max(v.savedVersion, version);
            if (v.saveQueued && v.dirty()) {
                v.saveQueued = false;
                save(v);
            }
        }, main);
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

    static Map<Integer, byte[]> load(Connection c, UUID account, int number) throws SQLException {
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT slot, item FROM perks_vaults WHERE account_uuid = ? AND vault_no = ? ORDER BY slot",
                rs -> new Object[]{rs.getInt("slot"), rs.getBytes("item")}, account, number)) {
            out.put((Integer) row[0], (byte[]) row[1]);
        }
        return out;
    }

    static void save(Connection c, UUID account, int number, Map<Integer, byte[]> slots, long now) throws SQLException {
        Sql.update(c, "DELETE FROM perks_vaults WHERE account_uuid = ? AND vault_no = ?", account, number);
        if (slots.isEmpty()) {
            return;
        }
        List<Object[]> rows = new ArrayList<>(slots.size());
        byte[] id = Sql.toBytes(account);
        slots.forEach((slot, item) -> rows.add(new Object[]{id, number, slot, item, now}));
        Sql.batch(c, "INSERT INTO perks_vaults (account_uuid, vault_no, slot, item, updated_at) VALUES (?, ?, ?, ?, ?)", rows);
    }

    // ------------------------------------------------------------------ the pick menu

    /** {@code /pv} with more than one vault: one tile per vault (open, take-out only, locked). */
    final class PickMenu extends Menu {

        @Override
        public int rows() {
            return 3;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, PerksModule.ID, "vaults.menu.title"));
        }

        @Override
        public String messageModule() {
            return PerksModule.ID;
        }

        @Override
        protected void build() {
            navBar();
            int count = count(viewer);
            int max = config.vaultsMax;
            int first = 13 - (max - 1) / 2;
            for (int n = 1; n <= max; n++) {
                int number = n;
                boolean usable = n <= count;
                set(Math.max(9, Math.min(17, first + n - 1)), service.item(usable ? Material.ENDER_CHEST : Material.GRAY_DYE, viewer, PerksModule.ID,
                        usable ? "vaults.menu.vault" : "vaults.menu.locked", Msg.number("number", number)), click -> {
                    click.player().closeInventory();
                    openVault(click.player(), number);
                });
            }
        }
    }
}
