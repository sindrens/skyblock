package net.skybornmc.skyblock.economy.sell;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellRequest;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.sell.Trend;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.economy.EconomyConfig;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Selling (M3.4) inside the economy module: the price table ({@code sell.yml}), dynamic prices (steps, hourly impact,
 * saving, all off the main thread), {@link SellService}, {@code /sell} ({@code hand}, {@code all}) and the sell menu.
 *
 * <p><b>Item safety.</b> Items stay in the player's inventory while they are in the sell box (the menu only remembers
 * which slots were picked and blocks every inventory click), so closing the menu, logging out or a server crash can never
 * lose them. On confirm they are checked again, taken out on the main thread, the player's data is saved, and one
 * guarded ledger transaction pays them; if it fails every stack is handed back (inventory, else the feet, else stored
 * and handed back on the next join).</p>
 */
public final class SellFeature implements Listener {

    public static final String PERM_SELL = "skyblock.economy.sell";
    static final String CATALOG_FILE = "sell.yml";
    private static final int STORAGE_SLOTS = 36;

    final EconomyModule module;
    final Core core;
    final ModuleMessages msg;
    private final EconomyConfig config;
    private final EconomyService economy;
    private volatile SellCatalog catalog = SellCatalog.empty();
    private PriceBook book;
    private SellStore store;
    private SellServiceImpl service;
    private volatile boolean ready;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    /** Players with a sale on its way (a second one waits; no double clicks, no switching items mid-sale). */
    private final java.util.Set<UUID> selling = ConcurrentHashMap.newKeySet();

    public SellFeature(EconomyModule module, Core core, ModuleMessages msg, EconomyConfig config, EconomyService economy) {
        this.module = module;
        this.core = core;
        this.msg = msg;
        this.config = config;
        this.economy = economy;
    }

    // ================================================================== lifecycle

    public void enable() {
        catalog = loadCatalog();
        book = new PriceBook(config::dynamicSettings);
        store = new SellStore(core.database());
        service = new SellServiceImpl(() -> catalog, book, store, economy, () -> config, core.items(),
                () -> core.services().optional(BonusService.class), System::currentTimeMillis, Format::zone,
                core.plugin().getLogger());
        core.services().register(EconomyModule.ID, SellService.class, service);
        // After a sale (main thread): island stats (items sold, money earned) for the profile that sold, and wardrobe
        // set XP (WARDROBE §3.1) only if that profile is still the active one.
        core.scheduler().later("sell-stat-sources", 1, () -> core.services().optional(net.skybornmc.skyblock.common.island.IslandStatsService.class)
                .ifPresent(stats -> {
                    stats.source(net.skybornmc.skyblock.common.island.IslandStat.ITEMS_SOLD);
                    stats.source(net.skybornmc.skyblock.common.island.IslandStat.MONEY_EARNED);
                }));
        service.onSold((playerId, profileKey, items, money) -> core.scheduler().main("sell-set-xp", () -> {
            core.services().optional(net.skybornmc.skyblock.common.island.IslandStatsService.class).ifPresent(stats -> {
                stats.recordFor(profileKey, net.skybornmc.skyblock.common.island.IslandStat.ITEMS_SOLD, items);
                stats.recordFor(profileKey, net.skybornmc.skyblock.common.island.IslandStat.MONEY_EARNED, money);
            });
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerId);
            if (p == null || !net.skybornmc.skyblock.common.profile.ProfileService.of(core.services()).profileKey(p).equals(profileKey)) {
                return;
            }
            core.services().optional(net.skybornmc.skyblock.common.wardrobe.WardrobeService.class)
                    .ifPresent(w -> w.setXp(p, net.skybornmc.skyblock.common.wardrobe.WardrobeService.XP_SELL_ITEM, items));
        }));
        store.loadPrices().whenComplete((loaded, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Sell: could not load prices; selling stays off until a restart", error);
                return;
            }
            book.load(loaded.states(), loaded.updatedAt(), System.currentTimeMillis());
            book.ensure(catalog.sellableKeys());
            ready = true;
            core.plugin().getLogger().info("Sell: " + catalog.size() + " price(s), " + book.size() + " price state(s) loaded.");
        });
        long interval = config.sellIntervalMinutes * 1_200L;
        tasks.add(core.scheduler().asyncRepeat(interval, interval, () -> {
            if (ready) {
                book.step(System.currentTimeMillis());
            }
        }));
        tasks.add(core.scheduler().asyncRepeat(72_000L, 72_000L, () -> {
            if (ready) {
                book.recompute(System.currentTimeMillis());
            }
        }));
        long save = config.sellSaveSeconds * 20L;
        tasks.add(core.scheduler().asyncRepeat(save, save, this::save));
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        registerCommand();
        for (Player p : Bukkit.getOnlinePlayers()) {
            deliverRefunds(p);
        }
    }

    public void reload() {
        catalog = loadCatalog();
        if (ready) {
            book.ensure(catalog.sellableKeys());
        }
    }

    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof SellMenu) {
                p.closeInventory();
            }
        }
        HandlerList.unregisterAll(this);
        if (ready) {
            try {
                store.savePricesBlocking(book.takeDirty(), System.currentTimeMillis());
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Sell: could not save prices on shutdown", e);
            }
        }
        ready = false;
    }

    private void save() {
        if (!ready) {
            return;
        }
        List<PriceState> dirty = book.takeDirty();
        store.savePrices(dirty, System.currentTimeMillis()).exceptionally(e -> {
            book.markDirty(dirty);
            core.plugin().getLogger().log(Level.WARNING, "Sell: could not save prices (retried later)", e);
            return null;
        });
    }

    public SellServiceImpl service() {
        return service;
    }

    EconomyConfig config() {
        return config;
    }

    /**
     * Reads {@code sell.yml}; a missing file is written from the bundled one, and entries that are new in the bundled file
     * are added to an existing file (switch an item off with {@code sellable: false} instead of deleting it).
     */
    private SellCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), CATALOG_FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        java.util.function.Predicate<String> material = id -> {
            Material m = Material.matchMaterial(id);
            return m != null && m.isItem() && !m.isAir();
        };
        java.util.function.Consumer<String> warn = w -> core.plugin().getLogger().warning(w);
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            warn.accept("sell.yml is not valid YAML, using the bundled prices. " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return SellCatalog.load(bundled, material, warn);
        }
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(CATALOG_FILE)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, file.toPath());
                    }
                }
                yaml = bundled;
            } else if (addMissing(yaml, bundled)) {
                yaml.save(file);
            }
        } catch (IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not write sell.yml", e);
        }
        return SellCatalog.load(yaml, material, warn);
    }

    /** Adds bundled entries the file does not have yet; true if something was added. */
    static boolean addMissing(YamlConfiguration yaml, YamlConfiguration bundled) {
        boolean changed = false;
        for (String root : List.of(SellCatalog.VANILLA_ROOT, SellCatalog.CUSTOM_ROOT)) {
            var section = bundled.getConfigurationSection(root);
            if (section == null) {
                continue;
            }
            for (String category : section.getKeys(false)) {
                var items = section.getConfigurationSection(category);
                if (items == null) {
                    continue;
                }
                for (String id : items.getKeys(false)) {
                    String path = root + "." + category + "." + id;
                    if (!yaml.contains(path) && !listedElsewhere(yaml, root, id)) {
                        yaml.set(path, items.get(id));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** The owner may have moved an item to another category: then it is not added again. */
    private static boolean listedElsewhere(YamlConfiguration yaml, String root, String id) {
        var section = yaml.getConfigurationSection(root);
        if (section == null) {
            return false;
        }
        for (String category : section.getKeys(false)) {
            var items = section.getConfigurationSection(category);
            if (items != null && items.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CATALOG_FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, "sell.yml in the jar is broken", e);
        }
        return yaml;
    }

    // ================================================================== command

    private void registerCommand() {
        Duration cooldown = Duration.ofSeconds(config.sellCooldownSeconds);
        core.commands().register(CommandSpec.command("sell")
                .description("Sell your loot at live prices")
                .category("Economy")
                .permission(PERM_SELL)
                .playerOnly()
                .executes(ctx -> open(ctx.player()))
                .sub(CommandSpec.sub("hand")
                        .description("Sell the stack in your hand")
                        .permission(PERM_SELL)
                        .playerOnly()
                        .cooldown(cooldown)
                        .example("sell hand")
                        .executes(ctx -> sellHand(ctx.player())))
                .sub(CommandSpec.sub("all")
                        .description("Sell everything sellable in your inventory")
                        .permission(PERM_SELL)
                        .playerOnly()
                        .cooldown(cooldown)
                        .example("sell all")
                        .executes(ctx -> sellAll(ctx.player()))));
    }

    void open(Player player) {
        checkCanSell(player);
        core.menus().open(player, new SellMenu(this));
    }

    private void checkCanSell(Player player) {
        if (!ready) {
            throw new CommandFailure(EconomyModule.ID, "sell.loading");
        }
        if (module.frozen(player)) {
            throw new CommandFailure(EconomyModule.ID, "sell.frozen");
        }
        if (profiles().isSwitching(player.getUniqueId())) {
            throw new CommandFailure(EconomyModule.ID, "sell.switching");
        }
    }

    private void sellHand(Player player) {
        checkCanSell(player);
        PlayerInventory inv = player.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack held = inv.getItem(slot);
        if (held == null || held.isEmpty()) {
            throw new CommandFailure(EconomyModule.ID, "sell.hand-empty");
        }
        Optional<String> reason = service.unsellableReason(held);
        if (reason.isPresent()) {
            throw new CommandFailure(EconomyModule.ID, "sell.reason." + reason.get(), Msg.c("item", itemName(held)));
        }
        Map<Integer, ItemStack> pick = new LinkedHashMap<>();
        pick.put(slot, held.clone());
        sell(player, pick, "hand", null);
    }

    private void sellAll(Player player) {
        checkCanSell(player);
        Map<Integer, ItemStack> pick = sellableSlots(player);
        if (pick.isEmpty()) {
            throw new CommandFailure(EconomyModule.ID, "sell.all-nothing");
        }
        sell(player, pick, "all", null);
    }

    /** Every sellable stack in the storage slots (hotbar + main inventory; armor and offhand are left alone). */
    Map<Integer, ItemStack> sellableSlots(Player player) {
        Map<Integer, ItemStack> pick = new LinkedHashMap<>();
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.isEmpty() && service.sellable(item)) {
                pick.put(slot, item.clone());
            }
        }
        return pick;
    }

    ProfileService profiles() {
        return module.profiles();
    }

    // ================================================================== the sale

    /**
     * Sells the picked slots. Each slot must still hold the same kind of item (amount may have changed while the menu was
     * open); stacks that changed or became unsellable are left alone. {@code done} runs on the main thread afterwards.
     */
    void sell(Player player, Map<Integer, ItemStack> picked, String source, Consumer<SellResult> done) {
        UUID uuid = player.getUniqueId();
        if (!ready || module.frozen(player) || profiles().isSwitching(uuid)) {
            msg.send(player, !ready ? "sell.loading" : module.frozen(player) ? "sell.frozen" : "sell.switching");
            sound(player, "error");
            finish(done, null);
            return;
        }
        if (!selling.add(uuid)) {
            msg.send(player, "sell.busy");
            finish(done, null);
            return;
        }
        PlayerInventory inv = player.getInventory();
        List<ItemStack> taken = new ArrayList<>();
        boolean changed = false;
        for (Map.Entry<Integer, ItemStack> e : picked.entrySet()) {
            int slot = e.getKey();
            ItemStack now = slot >= 0 && slot < STORAGE_SLOTS ? inv.getItem(slot) : null;
            if (now == null || now.isEmpty() || !now.isSimilar(e.getValue()) || !service.sellable(now)) {
                changed = true;
                continue;
            }
            taken.add(now.clone());
            inv.setItem(slot, null); // escrow: from here the items only exist in this sale until it is paid or refunded
        }
        if (changed) {
            msg.send(player, "sell.changed");
        }
        if (taken.isEmpty()) {
            selling.remove(uuid);
            if (!changed) {
                msg.send(player, "sell.all-nothing");
            }
            sound(player, "error");
            finish(done, null);
            return;
        }
        if (config.sellSavePlayerData) {
            try {
                player.saveData();
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Sell: could not save " + player.getName() + "'s data before a sale", e);
            }
        }
        UUID profile = profiles().profileKey(player);
        String name = player.getName();
        SellRequest request = new SellRequest(uuid, name, profile, taken, service.bonusPercent(player), source, Actor.player(player));
        service.sell(request).whenCompleteAsync((result, error) -> {
            selling.remove(uuid);
            SellResult r = error != null || result == null
                    ? new SellResult(SellResult.Status.ERROR, null, taken, null, null) : result;
            giveBack(uuid, profile, r.giveBack());
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                report(online, r);
            }
            finish(done, r);
        }, module.mainThread());
    }

    private static void finish(Consumer<SellResult> done, SellResult result) {
        if (done != null) {
            done.accept(result);
        }
    }

    private void report(Player player, SellResult r) {
        switch (r.status()) {
            case SOLD -> {
                var q = r.quote();
                TagResolver[] p = {Msg.number("items", q.items()), module.amountTag("amount", player, Currency.MONEY, q.total()),
                        module.amountTag("balance", player, Currency.MONEY, r.balance() == null ? 0 : r.balance()),
                        Msg.p("bonus", percentText(q.cappedBonusPercent()))};
                msg.send(player, q.cappedBonusPercent() > 0 ? "sell.sold-bonus" : "sell.sold", p);
                core.sounds().play(player, "money");
                if (!r.giveBack().isEmpty()) {
                    msg.send(player, "sell.some-unsold", Msg.number("stacks", r.giveBack().size()));
                }
            }
            case NOTHING_SELLABLE -> {
                msg.send(player, "sell.all-nothing");
                sound(player, "error");
            }
            case TOO_CHEAP -> {
                msg.send(player, "sell.too-cheap");
                sound(player, "error");
            }
            case OVERFLOW -> {
                msg.send(player, "sell.overflow");
                sound(player, "error");
            }
            default -> {
                msg.send(player, "sell.failed");
                sound(player, "error");
            }
        }
    }

    // ================================================================== giving items back

    /** Hands stacks back to their owner: inventory, else at their feet; stored for later if they left or switched profile. */
    void giveBack(UUID owner, UUID profile, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        if (player != null && player.isOnline() && !profiles().isSwitching(owner) && profiles().profileKey(player).equals(profile)) {
            int dropped = 0;
            for (ItemStack stack : stacks) {
                for (ItemStack rest : player.getInventory().addItem(stack.clone()).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    dropped++;
                }
            }
            if (dropped > 0) {
                msg.send(player, "sell.returned-dropped", Msg.number("stacks", dropped));
            }
            return;
        }
        for (ItemStack stack : stacks) {
            byte[] bytes = stack.serializeAsBytes();
            store.addRefund(owner, profile, bytes, stack.getType().name(), stack.getAmount(), System.currentTimeMillis())
                    .exceptionally(e -> {
                        core.plugin().getLogger().log(Level.SEVERE, "Sell: could not store a refund for " + owner
                                + ". Restore it from this data: " + Base64.getEncoder().encodeToString(bytes), e);
                        return null;
                    });
        }
    }

    /** Refunds waiting for this player's active profile. */
    void deliverRefunds(Player player) {
        UUID uuid = player.getUniqueId();
        store.refunds(uuid).thenAcceptAsync(list -> {
            if (list.isEmpty() || !player.isOnline()) {
                return;
            }
            UUID profile = profiles().profileKey(player);
            for (SellStore.Refund refund : list) {
                if (!refund.profile().equals(profile)) {
                    continue;
                }
                store.takeRefund(refund.id()).thenAcceptAsync(taken -> {
                    if (!taken) {
                        return; // already delivered elsewhere
                    }
                    ItemStack item = ItemStack.deserializeBytes(refund.item());
                    giveBack(uuid, refund.profile(), List.of(item)); // stored again if they left or switched meanwhile
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        msg.send(online, "sell.refund-delivered", Msg.c("item", itemName(item)), Msg.number("amount", item.getAmount()));
                    }
                }, module.mainThread());
            }
        }, module.mainThread()).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Sell: could not load refunds of " + player.getName(), e);
            return null;
        });
    }

    // ================================================================== events

    /** Clicks on the player's own inventory while the sell menu is open: put a stack in / take it out (Bedrock: a tap). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SellMenu menu)) {
            return;
        }
        event.setCancelled(true); // the menu framework already did; nothing may move here
        if (!(event.getWhoClicked() instanceof Player player) || menu.viewer() != player
                || event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 0 || slot >= STORAGE_SLOTS) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < config.sellMenuClickMs) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        menu.toggle(slot);
        Bukkit.getScheduler().runTask(core.plugin(), player::updateInventory);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A little later: the profile module loads the active profile on join.
        Bukkit.getScheduler().runTaskLater(core.plugin(), () -> {
            if (player.isOnline() && ready) {
                deliverRefunds(player);
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    /** The inventory is about to be swapped: close the sell box first. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProfileSwitch(ProfileSwitchEvent event) {
        Menu open = core.menus().openMenu(event.getPlayer());
        if (open instanceof SellMenu) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        deliverRefunds(event.getPlayer());
    }

    // ================================================================== text helpers

    void sound(Player player, String key) {
        core.sounds().play(player, key);
    }

    /** A price per item (fractional below $100: {@code $0.85}, {@code $33.50}), in the money color. */
    Component unitPrice(Player viewer, double unit) {
        String text;
        if (unit >= 100) {
            long whole = (long) Math.floor(unit);
            text = whole < 100_000 ? Format.number(whole) : Format.compact(whole);
        } else {
            double cents = Math.floor(unit * 100 + 1e-6) / 100.0;
            text = cents == Math.rint(cents) ? String.valueOf((long) cents) : String.format(Locale.ROOT, "%.2f", cents);
        }
        return msg.component(viewer, "format.money", Msg.p("value", text), Msg.p("exact", text));
    }

    Component money(Player viewer, long value) {
        return module.amount(viewer, Currency.MONEY, value);
    }

    /** {@code +12.5} / {@code -8} (one decimal, trailing .0 dropped). */
    static String percentText(double value) {
        double r = Math.round(value * 10) / 10.0;
        String body = r == Math.rint(r) ? String.valueOf((long) Math.abs(r)) : String.format(Locale.ROOT, "%.1f", Math.abs(r));
        return (r < 0 ? "-" : "+") + body;
    }

    /** "% from base" in green (above), red (below) or gray (at base). */
    Component fromBase(Player viewer, SellPrice price) {
        double pct = price.percentFromBase();
        String key = Math.abs(pct) < 0.05 ? "sell.percent.zero" : pct > 0 ? "sell.percent.up" : "sell.percent.down";
        return msg.component(viewer, key, Msg.p("value", percentText(pct)));
    }

    Component trend(Player viewer, Trend trend) {
        return msg.component(viewer, "sell.trend." + trend.key());
    }

    /** The item's own name (translated for vanilla items on both Java and Bedrock). */
    static Component itemName(ItemStack stack) {
        return stack.effectiveName();
    }

    /** Name of a price table key: the vanilla item's translated name, or the words of a custom id. */
    static Component keyName(String key) {
        if (key == null) {
            return Component.empty();
        }
        if (key.startsWith(SellCatalog.CUSTOM_PREFIX)) {
            return Component.text(words(key.substring(SellCatalog.CUSTOM_PREFIX.length())));
        }
        Material m = Material.matchMaterial(key);
        return m == null ? Component.text(words(key)) : Component.translatable(m.translationKey());
    }

    static String words(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /** Name of a sell bonus source ({@code sell.bonus-source.<id>}, else the id in words). */
    Component sourceName(Player viewer, String id) {
        String key = "sell.bonus-source." + id;
        return msg.has(key) ? msg.component(viewer, key) : Component.text(words(id.replace('-', '_')));
    }

    Component categoryName(Player viewer, String category) {
        String key = "sell.category." + category;
        return msg.has(key) ? msg.component(viewer, key) : Component.text(words(category.replace('-', '_')));
    }
}
