package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
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
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.stacking.StackingService;
import org.bukkit.event.player.PlayerQuitEvent;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The builder shop (M3.5, Shop v2, GUI-P3 / I9 section 12): {@code /shop} opens the category menu (centred icons),
 * {@code /shop <category>} the item grid of one category. On a tile: left click (Bedrock tap) = buy GUI, right click =
 * sell GUI, middle click or Shift + Right-click = sell every one of that item you carry, Shift + Left = buy a stack.
 * Bedrock reaches selling through the Sell button of the buy GUI and Sell all on the sell GUI. Prices come from
 * {@code shop.yml}; every sale goes through {@link SellService} (the /sell rules, one ledger transaction, items back or to
 * /box on failure). F14: "Undo last buy" for 15 s and a second click for buys over half your money, both switchable in
 * {@code /settings}.
 *
 * <p><b>Purchase safety.</b> Free inventory space is counted before paying: when not all of it fits, only what fits is
 * bought and charged ("Bought 40 of 64: no more room"); no room at all = refused, nothing paid. The payment
 * is one ledger transaction from the player's <b>profile</b> account that writes the purchase row in the same database
 * transaction. Items are handed out only after it succeeded, then the row is marked delivered. If the player left or
 * switched profile meanwhile, the purchase waits ({@code pending}) and is handed over on the next join / switch back;
 * rows a crash left half-way go to staff review on startup. Items that no longer fit go to {@code /box}.</p>
 *
 * <p><b>Undo.</b> The items are taken back first (all of them, from the inventory, main thread), then the refund runs as
 * one ledger deposit together with {@code delivered → refunded} on the purchase row, so it pays exactly once; if the
 * refund fails, the items are given back (inventory, then {@code /box}). Can lose a refund, never dupe money.</p>
 *
 * <p><b>Buy &gt; sell.</b> An item is never sold for less than it (or what it turns back into) could ever bring with
 * {@code /sell} at today's settings ({@link SellService#maxUnitPrice}); such items show as not for sale and the console
 * names them until their price is raised.</p>
 */
public final class ShopModule implements SkyblockModule, Listener {

    static final String ID = "shop";
    static final String PERM_USE = "skyblock.shop.use";
    static final String CATALOG_FILE = "shop.yml";
    static final String V1_DEFAULTS = "migrations/shop-v1.yml";
    /** The frozen Shop v2 defaults: target of the v1 migration, baseline of the v3 one (MENUS-V3 6.3). */
    static final String V2_DEFAULTS = "migrations/shop-v2.yml";
    /** The frozen Shop v3 defaults: baseline of the v4 migration (ECON-SELL-SHOP-AH B1 shop lists). */
    static final String V3_DEFAULTS = "migrations/shop-v3.yml";
    /** /settings toggle ids (F14). */
    static final String SETTING_UNDO = "shop-undo";
    static final String SETTING_BIG_BUY = "shop-big-buy";

    final Core core;
    final ModuleMessages msg;
    final ShopSettings settings = new ShopSettings();
    private volatile ShopCatalog catalog = ShopCatalog.empty();
    /** B2: shop.yml as loaded, before staff switched items off ({@link #catalog} = this minus {@link #overrides}). */
    private volatile ShopCatalog fileCatalog = ShopCatalog.empty();
    private ShopOverrides overrides;
    private ShopStore store;
    private ShopEngine engine;
    private Executor main;
    private volatile boolean ready;
    /** Players with a purchase on its way (one at a time). */
    private final Set<UUID> buying = ConcurrentHashMap.newKeySet();
    /** Profiles whose waiting purchases are being handed over right now. */
    private final Set<UUID> delivering = ConcurrentHashMap.newKeySet();
    /** F14: the last purchase each player can still undo. */
    final UndoLedger undo = new UndoLedger();
    /** GUI-P3: one shop sale in flight per player and a minimum gap between sales (click spam). Main thread. */
    private final ShopSellGuard sellGuard = new ShopSellGuard();

    public ShopModule(Core core) {
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
        core.configs().load(settings);
        main = new MainThread(core.plugin());
        store = new ShopStore(core.database());
        engine = new ShopEngine(this::economy, System::currentTimeMillis, () -> 0);
        overrides = new ShopOverrides(core.database());
        fileCatalog = loadCatalog();
        catalog = fileCatalog;
        warnUndercuts();
        overrides.load().whenCompleteAsync((ok, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Shop: could not load staff switch-offs (/eco shop disable)", Database.cause(e));
            } else {
                applyOverrides();
            }
        }, main);
        // PHASE-5 M5.2: block worth of the island level reads shop prices (any thread; the catalog is volatile).
        core.services().register(ID, net.skybornmc.skyblock.common.shop.ShopPrices.class, itemId -> {
            if (itemId == null) {
                return java.util.OptionalLong.empty();
            }
            String id = itemId.trim().toLowerCase(java.util.Locale.ROOT);
            if (id.startsWith("minecraft:")) {
                id = id.substring("minecraft:".length());
            }
            return catalog.worth(id);
        });
        registerCommand();
        core.commands().extend("eco", ecoShopCommand());
        registerSettings();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        store.recover(System.currentTimeMillis()).whenComplete((stuck, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Shop: startup recovery failed; purchases may wait until the next start",
                        Database.cause(e));
            } else if (!stuck.isEmpty()) {
                // SECFIX1 F-12: maybe handed over already (items in the saved inventory): never again automatically.
                core.plugin().getLogger().warning("Shop: " + stuck.size() + " purchase(s) were paid while the server stopped mid "
                        + "hand-over; they are marked 'review' for staff (shop_review in /logs). Check the player's inventory and "
                        + "/box, then refund or deliver by hand.");
                for (ShopStore.Purchase p : stuck) {
                    core.audit().logPlayer(AuditCategory.ITEM, "shop_review", Actor.system("shop"), p.player(), p.playerName(),
                            p.totalPrice(), null, details(p));
                }
            }
            ready = true;
            main.execute(() -> Bukkit.getOnlinePlayers().forEach(this::deliverPending));
        });
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        fileCatalog = loadCatalog();
        applyOverrides();
        warnUndercuts();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isShopMenu(core.menus().openMenu(p))) {
                p.closeInventory();
            }
        }
        ready = false;
        buying.clear();
        delivering.clear();
        undo.clearAll();
        sellGuard.clear();
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
    }

    /** F14: both shop safety helpers can be turned off per player in /settings (default on). */
    private void registerSettings() {
        core.services().optional(PlayerSettings.class).ifPresent(s -> {
            s.register(new SettingToggle(ID, SETTING_UNDO, SettingCategory.GAMEPLAY, Material.CLOCK, true, null, ID,
                    "settings." + SETTING_UNDO, false));
            s.register(new SettingToggle(ID, SETTING_BIG_BUY, SettingCategory.GAMEPLAY, Material.ORANGE_CONCRETE, true, null, ID,
                    "settings." + SETTING_BIG_BUY, false));
            s.onChange(SETTING_UNDO, (player, on) -> {
                if (!on) {
                    undo.clear(player.getUniqueId());
                }
            });
        });
    }

    /** A /settings toggle of this module for this player (default on when the settings service is missing). */
    boolean setting(Player player, String id) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(player, id)).orElse(true);
    }

    // ================================================================== catalog

    ShopCatalog catalog() {
        return catalog;
    }

    /**
     * Reads {@code shop.yml}; a missing file is written from the bundled one. An older file is migrated once per version
     * ({@link ShopCatalogMigration}): version 1 to Shop v2 (backup {@code shop.yml.before-v2}, {@code shop-migration-v2.log}),
     * then version 2 to v3 (MENUS-V3: backup {@code shop.yml.before-v3}, {@code shop-migration-v3.log}); every change also
     * in the console and the audit log. Items that are new in the bundled file are added to an existing file afterwards
     * (switch an item off with {@code enabled: false} instead of deleting it; parked items are never added back).
     */
    private ShopCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), CATALOG_FILE);
        YamlConfiguration bundled = bundled(CATALOG_FILE);
        Predicate<String> material = ShopModule::isItem;
        Predicate<String> spawner = this::isSpawnerType;
        Consumer<String> warn = w -> core.plugin().getLogger().warning(w);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            warn.accept("shop.yml is not valid YAML, using the bundled prices. " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return ShopCatalog.load(bundled, material, spawner, pricing(), warn);
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
            } else {
                YamlConfiguration v2Defaults = bundled(V2_DEFAULTS);
                Optional<ShopCatalogMigration.Result> migrated = ShopCatalogMigration.migrate(yaml, v2Defaults, bundled(V1_DEFAULTS));
                if (migrated.isPresent()) {
                    yaml = migrated.get().yaml();
                    writeMigration(file, yaml, migrated.get().changes(), ShopCatalogMigration.V2);
                }
                YamlConfiguration v3Defaults = bundled(V3_DEFAULTS);
                // v2 -> v3 targets the frozen v3 file; v3 -> v4 then moves it to the bundled file.
                Optional<ShopCatalogMigration.Result> v3 = ShopCatalogMigration.migrateV3(yaml, v3Defaults, v2Defaults);
                if (v3.isPresent()) {
                    yaml = v3.get().yaml();
                    writeMigration(file, yaml, v3.get().changes(), ShopCatalogMigration.V3);
                }
                Optional<ShopCatalogMigration.Result> v4 = ShopCatalogMigration.migrateV4(yaml, bundled, v3Defaults);
                if (v4.isPresent()) {
                    yaml = v4.get().yaml();
                    writeMigration(file, yaml, v4.get().changes(), ShopCatalogMigration.V4);
                }
                if (addMissing(yaml, bundled)) {
                    yaml.save(file);
                }
            }
        } catch (IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not write shop.yml", e);
        }
        ShopCatalog loaded = ShopCatalog.load(yaml, material, spawner, pricing(), warn);
        core.plugin().getLogger().info("Shop: " + loaded.categories().size() + " tabs, " + loaded.size() + " item(s).");
        return loaded;
    }

    /**
     * Saves a migrated shop.yml: backup of the file as it was ({@code shop.yml.before-v<version>}, first time only), then
     * the file, the change log ({@code shop-migration-v<version>.log}) and one audit row.
     */
    private void writeMigration(File file, YamlConfiguration yaml, List<String> changes, int version) throws IOException {
        File backup = new File(file.getParentFile(), CATALOG_FILE + ".before-v" + version);
        if (!backup.exists()) {
            java.nio.file.Files.copy(file.toPath(), backup.toPath());
        }
        yaml.save(file);
        java.nio.file.Files.write(new File(file.getParentFile(), "shop-migration-v" + version + ".log").toPath(), changes, StandardCharsets.UTF_8);
        core.plugin().getLogger().info("Shop: shop.yml moved to Shop v" + version + ". Backup: " + backup.getName() + ". Changes:");
        changes.forEach(c -> core.plugin().getLogger().info("Shop migration: " + c));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", version);
        details.put("changes", changes.size());
        details.put("log", changes.size() > 150 ? new ArrayList<>(changes.subList(0, 150)) : changes);
        core.audit().log(AuditCategory.CONFIG, "shop_catalog_migrate", Actor.system(ID), details);
    }

    /**
     * Adds bundled tabs/items the file does not have yet (an item moved to another tab, or parked under
     * {@code worth-only}, is not added again).
     */
    static boolean addMissing(YamlConfiguration yaml, YamlConfiguration bundled) {
        var root = bundled.getConfigurationSection(ShopCatalog.ROOT);
        if (root == null) {
            return false;
        }
        boolean changed = false;
        for (String category : root.getKeys(false)) {
            String base = ShopCatalog.ROOT + "." + category;
            if (!yaml.contains(base)) {
                // A new tab: its settings, then its items one by one (items the owner moved elsewhere stay there).
                var section = root.getConfigurationSection(category);
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        if (!key.equals("items")) {
                            yaml.set(base + "." + key, section.get(key));
                        }
                    }
                }
                changed = true;
            }
            var items = root.getConfigurationSection(category + ".items");
            if (items == null) {
                continue;
            }
            for (String id : items.getKeys(false)) {
                if (!yaml.contains(base + ".items." + id) && !listedElsewhere(yaml, id)
                        && !yaml.contains(ShopCatalog.WORTH_ONLY + "." + id)) {
                    yaml.set(base + ".items." + id, items.get(id));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean listedElsewhere(YamlConfiguration yaml, String id) {
        var root = yaml.getConfigurationSection(ShopCatalog.ROOT);
        if (root == null) {
            return false;
        }
        for (String category : root.getKeys(false)) {
            if (root.contains(category + ".items." + id)) {
                return true;
            }
        }
        return false;
    }

    private YamlConfiguration bundled(String resource) {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, resource + " in the jar is broken", e);
        }
        return yaml;
    }

    /**
     * ECON-SPAWNERS step 12: a mob the shop may list as a spawner: sold for money (not premium, not legacy) and its tier at
     * most {@code spawners.max-tier}. Anything else in shop.yml is skipped by the catalog loader with a console line.
     */
    /**
     * B1: {@code price: auto} from the sell base ({@code SellService}, economy is enabled first) x {@code shop.auto-markup},
     * every price x {@code buy.price-multiplier}. Without the economy module {@code auto} items are skipped.
     */
    private ShopCatalog.Pricing pricing() {
        Optional<SellService> sell = core.services().optional(SellService.class);
        return new ShopCatalog.Pricing(id -> sell.flatMap(s -> s.price(id)).map(p -> p.base()).orElse(Double.NaN),
                settings.autoMarkup, settings.priceMultiplier);
    }

    private boolean isSpawnerType(String type) {
        return stacking().map(st -> st.spawnerSellable(type)
                && st.spawnerTier(type).orElse(0) >= 1 && st.spawnerTier(type).orElse(0) <= settings.spawnerMaxTier).orElse(false);
    }

    /** The island the player plays on (active profile), or null. Main thread. */
    private java.util.UUID islandOf(Player player) {
        return player == null ? null : core.services().optional(net.skybornmc.skyblock.common.island.IslandService.class)
                .flatMap(i -> i.islandOf(player.getUniqueId()))
                .map(net.skybornmc.skyblock.common.island.IslandService.IslandInfo::id).orElse(null);
    }

    Optional<StackingService> stacking() {
        return core.services().optional(StackingService.class);
    }

    static boolean isItem(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir();
    }

    static Material material(ShopCatalog.Item item) {
        return ShopMenu.material(item);
    }

    // ================================================================== buy > sell

    /** The most one item of this id could ever bring with /sell right now (empty = not sellable / no selling). */
    OptionalDouble maxSell(String itemId) {
        return core.services().optional(SellService.class).map(s -> s.maxUnitPrice(itemId)).orElse(OptionalDouble.empty());
    }

    /** True if this item may be sold now: its price is above anything /sell could pay for it (or the check is off). */
    boolean priceSafe(ShopCatalog.Item item) {
        return !settings.checkSellPrices || !ShopMath.undercutsSell(item, this::maxSell);
    }

    private void warnUndercuts() {
        if (!settings.checkSellPrices) {
            core.plugin().getLogger().warning("Shop: safety.check-sell-prices is OFF; items priced below their /sell value "
                    + "can be bought and sold for endless money.");
            return;
        }
        for (ShopCatalog.Item item : catalog.items()) {
            if (item.enabled() && !priceSafe(item)) {
                core.plugin().getLogger().warning("Shop: '" + item.id() + "' costs " + item.price() + " but can sell for up to "
                        + (long) Math.ceil(ShopMath.maxSellValue(item.id(), item.contains(), this::maxSell))
                        + " with /sell; it is NOT for sale until its price in shop.yml is raised above that.");
            }
        }
    }

    // ================================================================== requirements

    /** The first skill requirement the player misses (skill key → level), or empty. Main thread. */
    Optional<Map.Entry<String, Integer>> missingRequirement(Player player, ShopCatalog.Item item) {
        if (item.requires().isEmpty()) {
            return Optional.empty();
        }
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        for (Map.Entry<String, Integer> req : item.requires().entrySet()) {
            int level;
            try {
                SkillType type = SkillType.valueOf(req.getKey().toUpperCase(Locale.ROOT));
                level = skills.map(s -> s.level(player, type)).orElse(0);
            } catch (IllegalArgumentException e) {
                level = 0;
            }
            if (level < req.getValue()) {
                return Optional.of(req);
            }
        }
        return Optional.empty();
    }

    // ================================================================== command

    // ================================================================== /eco shop (B2, staff)

    static final String PERM_SHOP_VIEW = "skyblock.staff.economy.shop.view";
    static final String PERM_SHOP_TOGGLE = "skyblock.staff.economy.shop.toggle";

    private void applyOverrides() {
        catalog = overrides == null ? fileCatalog : fileCatalog.withDisabled(overrides.disabled());
    }

    private CommandSpec ecoShopCommand() {
        Arg<ShopCatalog.Item> item = Arg.word("item", (sender, raw) -> {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.startsWith("minecraft:")) {
                id = id.substring("minecraft:".length());
            }
            return fileCatalog.item(id).orElseThrow(() -> new CommandFailure(ID, "eco-shop.unknown", Msg.p("item", raw)));
        }, Suggest.of(() -> fileCatalog.items().stream().map(ShopCatalog.Item::id).toList()));
        Arg<String> reason = Arg.greedy("reason", (sender, raw) -> raw.strip(), Suggest.of("Price exploit", "Broken item", "Fixed"));
        return CommandSpec.sub("shop")
                .description("Staff: look up a shop item, or switch one off and on without editing shop.yml")
                .permission(PERM_SHOP_VIEW)
                .sub(CommandSpec.sub("price")
                        .description("Show a shop item's tab, price, block worth, state and the most /sell pays for it")
                        .permission(PERM_SHOP_VIEW)
                        .arg(item)
                        .example("eco shop price oak_log")
                        .executes(ctx -> ecoShopPrice(ctx.sender(), ctx.get(item))))
                .sub(CommandSpec.sub("disable")
                        .description("Stop selling one shop item (hidden from /shop, kept in shop.yml)")
                        .permission(PERM_SHOP_TOGGLE)
                        .arg(item).arg(reason)
                        .example("eco shop disable hopper Price exploit")
                        .executes(ctx -> ecoShopToggle(ctx.sender(), ctx.get(item), false, ctx.get(reason))))
                .sub(CommandSpec.sub("enable")
                        .description("Sell a switched-off shop item again")
                        .permission(PERM_SHOP_TOGGLE)
                        .arg(item).arg(reason)
                        .example("eco shop enable hopper Fixed")
                        .executes(ctx -> ecoShopToggle(ctx.sender(), ctx.get(item), true, ctx.get(reason))));
    }

    private void ecoShopPrice(CommandSender sender, ShopCatalog.Item fileItem) {
        ShopCatalog.Item item = catalog.item(fileItem.id()).orElse(fileItem);
        ShopOverrides.Off off = overrides.get(item.id());
        String state = off != null ? "off by staff: " + off.reason() : !item.enabled() ? "off in shop.yml"
                : !priceSafe(item) ? "hidden: /sell pays more" : "on sale";
        double max = item.spawner() ? 0 : ShopMath.maxSellValue(item.id(), item.contains(), this::maxSell);
        msg.send(sender, "eco-shop.price", Msg.p("item", item.id()), Msg.p("tab", item.category()),
                Msg.number("price", item.price()), Msg.p("worth", worthText(item.id())),
                Msg.p("max-sell", max > 0 ? String.valueOf((long) Math.ceil(max)) : "-"), Msg.p("state", state));
    }

    private String worthText(String id) {
        OptionalLong worth = catalog.worth(id);
        return worth.isPresent() ? "$" + worth.getAsLong() : "-";
    }

    private void ecoShopToggle(CommandSender sender, ShopCatalog.Item item, boolean enable, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new CommandFailure(ID, "eco-shop.reason");
        }
        boolean isOff = overrides.get(item.id()) != null;
        if (enable == !isOff) {
            msg.send(sender, enable ? "eco-shop.already-on" : "eco-shop.already-off", Msg.p("item", item.id()));
            return;
        }
        String actor = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        overrides.set(item.id(), enable, reason, actor, System.currentTimeMillis()).whenCompleteAsync((ok, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Shop: could not save the switch of " + item.id(), Database.cause(e));
                msg.send(sender, "error");
                return;
            }
            applyOverrides();
            msg.send(sender, enable ? "eco-shop.enabled" : "eco-shop.disabled", Msg.p("item", item.id()), Msg.p("reason", reason));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("item", item.id());
            details.put("enabled", enable);
            details.put("reason", reason);
            core.audit().log(AuditCategory.ECONOMY, "shop_item_toggle", Actor.of(sender), details);
        }, main);
    }

    private void registerCommand() {
        Arg<ShopCatalog.Category> category = Arg.word("category", (sender, raw) -> tabOf(raw)
                .orElseThrow(() -> new CommandFailure(ID, "unknown-category", Msg.p("input", raw),
                        Msg.p("categories", String.join(", ", categoryIds())))), Suggest.of(this::categoryIds));
        core.commands().register(CommandSpec.command("shop")
                .description("Buy and sell blocks, decoration, spawners and more")
                .category("Economy")
                .permission(PERM_USE)
                .playerOnly()
                .optional(category)
                .example("shop wood")
                .executes(ctx -> open(ctx.player(), ctx.getOr(category, null))));
    }

    /** A tab by id; the old v1 category ids ({@code utility}, {@code glass-colors} ...) still find their new tab. */
    Optional<ShopCatalog.Category> tabOf(String raw) {
        Optional<ShopCatalog.Category> direct = catalog.category(raw);
        if (direct.isPresent() || raw == null) {
            return direct;
        }
        String mapped = ShopCatalogMigration.TAB_OF_OLD_CATEGORY.get(raw.toLowerCase(Locale.ROOT));
        return mapped == null ? Optional.empty() : catalog.category(mapped);
    }

    List<String> categoryIds() {
        return catalog.categories().stream().map(ShopCatalog.Category::id).toList();
    }

    /**
     * {@code /shop}: the category menu (its Back target is the menu the player came from, e.g. the hub);
     * {@code /shop <category>}: that category's grid with the category menu as its Back target (D1: no remembered tab).
     */
    void open(Player player, ShopCatalog.Category category) {
        Optional<String> problem = problem(player);
        if (problem.isPresent()) {
            throw new CommandFailure(ID, problem.get());
        }
        if (category == null) {
            core.menus().open(player, new ShopCategoryMenu(this));
            return;
        }
        ShopMenu grid = new ShopMenu(this, category.id());
        grid.parent(new ShopCategoryMenu(this)); // a code-set parent wins (Navigation): Back goes to the categories
        core.menus().open(player, grid);
    }

    /** Opens {@code next} instead of {@code from} with the same Back target (buy GUI and sell GUI swap without piling up Back). */
    void switchTo(Menu from, Menu next) {
        next.parent(from.parent());
        core.menus().open(from.viewer(), next);
    }

    /** Why this player can't use the shop right now (message key), or empty. Main thread. */
    Optional<String> problem(Player player) {
        if (!ready) {
            return Optional.of("loading");
        }
        if (flag(player, "frozen")) {
            return Optional.of("frozen");
        }
        if (flag(player, "staff_mode")) {
            return Optional.of("staff-mode");
        }
        if (profiles().isSwitching(player.getUniqueId())) {
            return Optional.of("switching");
        }
        return Optional.empty();
    }

    // ================================================================== what an item is

    /** The stack a purchase of {@code item} hands out, amount 1 (a spawner item from the stacking module); empty if impossible. */
    Optional<ItemStack> template(ShopCatalog.Item item) {
        return template(item.id());
    }

    /** {@link #template(ShopCatalog.Item)} by item id (also for purchase rows of items no longer in the catalog). */
    Optional<ItemStack> template(String itemId) {
        if (itemId != null && itemId.startsWith(ShopCatalog.SPAWNER_PREFIX)) {
            String mob = itemId.substring(ShopCatalog.SPAWNER_PREFIX.length());
            return stacking().flatMap(st -> st.createSpawner(mob, 1, "shop"));
        }
        Material m = itemId == null ? null : Material.matchMaterial(itemId);
        return m == null || !m.isItem() || m.isAir() ? Optional.empty() : Optional.of(new ItemStack(m));
    }

    /** The shop icon of an item (spawners show the real spawner item with its rarity). */
    ItemStack icon(ShopCatalog.Item item) {
        return template(item).map(ItemStack::clone).orElseGet(() -> {
            Material m = material(item);
            return new ItemStack(m == null ? Material.BARRIER : m);
        });
    }

    int maxStack(ShopCatalog.Item item) {
        return template(item).map(ItemStack::getMaxStackSize).orElse(64);
    }

    long fits(Player player, ShopCatalog.Item item) {
        return template(item).map(t -> InventorySpace.fits(player.getInventory(), t)).orElse(0L);
    }

    /** The translated name ("Oak Log", "Pig Spawner"). */
    Component displayName(Player viewer, ShopCatalog.Item item) {
        if (item.spawner()) {
            return msg.component(viewer, "spawner-name", Msg.c("mob", Component.translatable("entity.minecraft." + item.mob())));
        }
        Material m = material(item);
        return m == null ? Component.text(item.id()) : itemName(m);
    }

    private Component displayName(String itemId) {
        if (itemId != null && itemId.startsWith(ShopCatalog.SPAWNER_PREFIX)) {
            return msg.component(net.kyori.adventure.audience.Audience.empty(), "spawner-name", Msg.c("mob",
                    Component.translatable("entity.minecraft." + itemId.substring(ShopCatalog.SPAWNER_PREFIX.length()))));
        }
        Material m = itemId == null ? null : Material.matchMaterial(itemId);
        return m == null ? Component.text(String.valueOf(itemId)) : itemName(m);
    }

    /** Spawner tier of a shop spawner (stacking.yml), 0 if unknown. */
    int spawnerTier(ShopCatalog.Item item) {
        return item.spawner() ? stacking().flatMap(st -> {
            java.util.OptionalInt t = st.spawnerTier(item.mob());
            return t.isPresent() ? Optional.of(t.getAsInt()) : Optional.empty();
        }).orElse(0) : 0;
    }

    /** Owner F2 hook: how well the next spawner of this type works on the player's island (stacking.yml spawners.efficiency). */
    int nextOutputPercent(Player viewer, ShopCatalog.Item item) {
        int p = stacking().map(st -> st.nextSpawnerOutputPercent(viewer, item.mob())).orElse(100);
        return Math.max(0, Math.min(100, p));
    }

    /**
     * ECON-SPAWNERS step 13: money one spawner of this mob earns per production hour at base levels (first spawner, no
     * island upgrades; upgrades only shorten the payback) x {@code spawners.dynamic-factor} (the average dynamic sell
     * price). 0 without a sellable drop, so the lore shows "never".
     */
    long incomePerHour(ShopCatalog.Item item) {
        if (!item.spawner()) {
            return 0;
        }
        double perHour = stacking().map(st -> st.spawnerIncomePerHour(null, item.mob())).orElse(0.0);
        return Math.max(0, Math.round(perHour * settings.spawnerDynamicFactor));
    }

    /** What stops this player's island from buying the spawner: an empty result = nothing. Main thread. */
    private Optional<SpawnerBlock> spawnerBlock(Player viewer, ShopCatalog.Item item, int tier) {
        Optional<StackingService> st = stacking();
        java.util.UUID island = islandOf(viewer);
        if (st.isEmpty() || island == null) {
            return Optional.empty(); // no island: the spawner can't be placed anyway; nothing to gate
        }
        StackingService.Unlock unlock = st.get().spawnerUnlock(island, tier);
        if (!unlock.unlocked()) {
            return Optional.of(new SpawnerBlock(unlock, 0, tier));
        }
        java.util.OptionalInt cap = st.get().spawnerCap(island, tier);
        if (cap.isPresent() && st.get().spawnersOfTier(island, tier) >= cap.getAsInt()) {
            return Optional.of(new SpawnerBlock(null, cap.getAsInt(), tier));
        }
        return Optional.empty();
    }

    /** Why a spawner can't be bought: still locked ({@code unlock} set) or the island's cap of the tier is reached. */
    private record SpawnerBlock(StackingService.Unlock unlock, int cap, int tier) {
    }

    /** "tier 1 mobs", or the named mobs ("Zombies"), or "..." while the island's counts load. */
    private Component unlockWhat(Player viewer, StackingService.Unlock unlock, int tier) {
        return switch (unlock.mobsLabelKey()) {
            case "mobs" -> Component.join(net.kyori.adventure.text.JoinConfiguration.commas(true),
                    unlock.mobs().stream().map(m -> Component.translatable("entity.minecraft." + m)).toList());
            case "loading" -> core.menus().text(viewer, ID, "entry.spawner-loading");
            default -> core.menus().text(viewer, ID, "entry.spawner-tier-mobs", Msg.number("tier", tier - 1));
        };
    }

    private void spawnerLines(Player viewer, SpawnerBlock block, List<Component> lore) {
        if (block.unlock() != null) {
            StackingService.Unlock u = block.unlock();
            lore.addAll(core.menus().lines(viewer, ID, "entry.spawner-unlock", Msg.number("needed", u.needed()),
                    Msg.number("have", u.kills()), Msg.c("what", unlockWhat(viewer, u, block.tier()))));
        } else {
            lore.addAll(core.menus().lines(viewer, ID, "entry.spawner-cap", Msg.number("max", block.cap())));
        }
    }

    /** Tells the player why the spawner is not for them right now; false if nothing blocks it. */
    private boolean refuseSpawner(Player player, ShopCatalog.Item item) {
        int tier = spawnerTier(item);
        Optional<SpawnerBlock> block = item.spawner() ? spawnerBlock(player, item, tier) : Optional.empty();
        if (block.isEmpty()) {
            return false;
        }
        if (block.get().unlock() != null) {
            StackingService.Unlock u = block.get().unlock();
            fail(player, "buy.spawner-locked", Msg.number("left", Math.max(0, u.needed() - u.kills())),
                    Msg.c("what", unlockWhat(player, u, tier)));
        } else {
            fail(player, "buy.spawner-capped", Msg.number("max", block.get().cap()), Msg.number("tier", tier));
        }
        return true;
    }

    /**
     * ECON-SPAWNERS step 13: extra Information rows of a spawner tile: its drop and today's base sell price, and how many
     * of the tier the viewer's island holds of its cap. Called from {@code ShopTooltips.spawnerRows} (one seam line).
     */
    void spawnerRows(net.skybornmc.skyblock.common.item.LoreBuilder.Section info, Player viewer, ShopCatalog.Item item) {
        Optional<StackingService> st = stacking();
        if (st.isEmpty() || !item.spawner()) {
            return;
        }
        st.get().spawnerDropItem(item.mob()).ifPresent(drop -> {
            String name = core.messages().raw("stacking", "drops." + drop);
            if (name == null || name.isBlank() || name.startsWith("[missing")) {
                name = drop;
            }
            long price = core.services().optional(SellService.class).flatMap(s -> s.price("custom:" + drop))
                    .map(p -> Math.round((double) p.base())).orElse(0L);
            info.row(raw("lore.spawner-drop"), net.skybornmc.skyblock.common.item.LoreBuilder.fill(raw("lore.spawner-drop-value"),
                    Map.of("item", name, "price", net.skybornmc.skyblock.common.util.Format.number(price))));
        });
        java.util.UUID island = islandOf(viewer);
        int tier = spawnerTier(item);
        if (island != null && tier > 0) {
            java.util.OptionalInt cap = st.get().spawnerCap(island, tier);
            long owned = st.get().spawnersOfTier(island, tier);
            info.row(raw("lore.spawner-island"), net.skybornmc.skyblock.common.item.LoreBuilder.fill(raw("lore.spawner-island-value"),
                    Map.of("owned", net.skybornmc.skyblock.common.util.Format.number(owned),
                            "cap", cap.isPresent() ? net.skybornmc.skyblock.common.util.Format.number(cap.getAsInt()) : raw("lore.spawner-no-cap"))));
        }
    }

    /**
     * {@code open}, {@code locked} (adds the requirement lines to {@code lore}) or {@code unavailable} (price at or below
     * the /sell value, a spawner above the shop's tier or without the stacking module). Main thread.
     */
    String state(Player viewer, ShopCatalog.Item item, List<Component> lore) {
        if (!priceSafe(item) || template(item).isEmpty()) {
            return "unavailable";
        }
        if (item.spawner()) {
            int tier = spawnerTier(item);
            if (!settings.spawnersEnabled || tier < 1 || tier > settings.spawnerMaxTier) {
                return "unavailable";
            }
            Optional<SpawnerBlock> block = spawnerBlock(viewer, item, tier);
            if (block.isPresent()) {
                spawnerLines(viewer, block.get(), lore);
                return "locked";
            }
        }
        Optional<Map.Entry<String, Integer>> missing = missingRequirement(viewer, item);
        if (missing.isPresent()) {
            lore.addAll(core.menus().lines(viewer, ID, "entry.requires",
                    Msg.c("skill", skillName(viewer, missing.get().getKey())), Msg.number("level", missing.get().getValue())));
            return "locked";
        }
        return "open";
    }

    /** True (and the player told why) if the item can't be bought by this player right now. */
    boolean refuse(Player player, ShopCatalog.Item shown) {
        ShopCatalog.Item item = catalog.item(shown.id()).orElse(null);
        if (item == null || !item.enabled()) {
            fail(player, "buy.gone");
            return true;
        }
        String now = state(player, item, new ArrayList<>());
        if ("locked".equals(now) && refuseSpawner(player, item)) {
            return true;
        }
        if (!"open".equals(now)) {
            fail(player, "unavailable".equals(now) ? "buy.unavailable" : "buy.locked-click");
            return true;
        }
        return false;
    }

    /** What {@code amount} of the item would cost right now with what fits (for the big-buy check). */
    long plannedTotal(Player player, ShopCatalog.Item item, long amount) {
        ShopMath.BuyPlan plan = ShopMath.plan(amount, fits(player, item), settings.maxAmount);
        return ShopMath.total(item.price(), Math.max(1, plan.amount())).orElse(Long.MAX_VALUE);
    }

    /** F14: the buy needs a second click (over big-buy.percent of the player's money, and the player didn't turn it off). */
    boolean needsSecondClick(Player player, long total) {
        return setting(player, SETTING_BIG_BUY) && ShopMath.bigBuy(total, balance(player), settings.bigBuyPercent);
    }

    /** The armed notice of a Java shift-click big buy (the grid item can't turn orange, so the action bar says it). */
    void armedNotice(Player player, ShopCatalog.Item item, long total) {
        long balance = balance(player);
        msg.actionBar(player, "big.armed-bar", Msg.number("percent", ShopMath.percentOf(total, balance)),
                money("after", Math.max(0, balance - total)), Msg.c("item", displayName(player, item)));
        core.sounds().play(player, "notify");
    }

    // ================================================================== buying

    /**
     * Buys {@code quantity} of {@code shown} (the item as the menu showed it). Everything is checked again here; nothing
     * is paid unless it all holds. {@code after} runs on the main thread when it is done (refresh the menu).
     */
    void buy(Player player, ShopCatalog.Item shown, int quantity, Runnable after) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.SHOP)) {
            return; // P7: frozen balance
        }
        Optional<String> problem = problem(player);
        ShopCatalog.Item item = catalog.item(shown.id()).orElse(null);
        if (problem.isEmpty() && (item == null || !item.enabled())) {
            problem = Optional.of("buy.gone");
        } else if (problem.isEmpty() && item.price() != shown.price()) {
            problem = Optional.of("buy.price-changed");
        } else if (problem.isEmpty() && !"open".equals(state(player, item, new ArrayList<>()))) {
            if (refuseSpawner(player, item)) {
                done(after);
                return;
            }
            problem = Optional.of(missingRequirement(player, item).isPresent() ? "buy.locked-click" : "buy.unavailable");
        }
        if (problem.isPresent()) {
            fail(player, problem.get());
            done(after);
            return;
        }
        if (quantity < 1 || quantity > settings.maxAmount) {
            fail(player, "buy.bad-amount", Msg.number("max", settings.maxAmount));
            done(after);
            return;
        }
        ItemStack template = template(item).orElse(null);
        if (template == null) {
            fail(player, "buy.unavailable");
            done(after);
            return;
        }
        long fits = InventorySpace.fits(player.getInventory(), template);
        ShopMath.BuyPlan plan = ShopMath.plan(quantity, fits, settings.maxAmount);
        if (plan.amount() < 1) {
            fail(player, "buy.no-room", Msg.number("fits", 0), Msg.number("amount", quantity));
            done(after);
            return;
        }
        int requested = quantity;
        int count = plan.amount(); // a partial buy charges only what fits
        OptionalLong total = ShopMath.total(item.price(), count);
        if (total.isEmpty()) {
            fail(player, "buy.bad-amount", Msg.number("max", settings.maxAmount));
            done(after);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!buying.add(uuid)) {
            fail(player, "buy.busy");
            done(after);
            return;
        }
        UUID profile = profiles().profileKey(player);
        ShopEngine.Request request = new ShopEngine.Request(uuid, profile, player.getName(), item, count, Actor.player(player));
        engine.buy(request).whenCompleteAsync((result, error) -> {
            buying.remove(uuid);
            ShopEngine.Result r = error != null || result == null ? new ShopEngine.Result(ShopEngine.Status.ERROR, null, null) : result;
            Player online = Bukkit.getPlayer(uuid);
            switch (r.status()) {
                case OK -> hand(r.purchase(), false, r.balance(), requested);
                case NOT_ENOUGH -> {
                    if (online != null) {
                        fail(online, "buy.not-enough", money("price", total.getAsLong()),
                                money("balance", economy().cached(uuid, Currency.MONEY).orElse(0)));
                    }
                }
                case INVALID -> {
                    if (online != null) {
                        fail(online, "buy.bad-amount", Msg.number("max", settings.maxAmount));
                    }
                }
                default -> {
                    if (online != null) {
                        fail(online, "error");
                    }
                }
            }
            done(after);
        }, main);
    }

    private static void done(Runnable after) {
        if (after != null) {
            after.run();
        }
    }

    /** The stacks a purchase hands out (vanilla items or spawner items), or empty if the item can't be made right now. */
    private Optional<List<ItemStack>> stacks(String itemId, int quantity) {
        Optional<ItemStack> template = template(itemId);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> out = new ArrayList<>();
        for (int amount : ShopMath.stacks(quantity, template.get().getMaxStackSize())) {
            ItemStack stack = template.get().clone();
            stack.setAmount(amount);
            out.add(stack);
        }
        return Optional.of(out);
    }

    /**
     * Hands a paid purchase (state {@code delivering}) to its profile's player, or parks it as {@code pending} if they
     * are not there (left, switched profile, staff mode). Main thread.
     */
    private void hand(ShopStore.Purchase p, boolean late, Long balance, int requested) {
        Player player = receiver(p.profile());
        if (player == null) {
            store.move(p.id(), ShopStore.DELIVERING, ShopStore.PENDING, System.currentTimeMillis()).whenComplete((moved, e) -> {
                if (e != null) {
                    core.plugin().getLogger().log(Level.SEVERE, "Shop: could not park purchase " + p.id() + "; it is handed over "
                            + "after the next restart", Database.cause(e));
                    return;
                }
                core.audit().logPlayer(AuditCategory.ITEM, "shop_pending", Actor.system("shop"), p.player(), p.playerName(),
                        p.totalPrice(), null, details(p));
            });
            return;
        }
        Optional<List<ItemStack>> stacks = stacks(p.itemId(), p.quantity());
        if (stacks.isEmpty()) {
            core.plugin().getLogger().severe("Shop: purchase " + p.id() + " is for '" + p.itemId() + "', which can't be made "
                    + "right now (unknown item, or the stacking module is off for spawners); it stays waiting (refund "
                    + p.totalPrice() + " to " + p.playerName() + " or fix it, then it is handed over on the next join).");
            store.move(p.id(), ShopStore.DELIVERING, ShopStore.PENDING, System.currentTimeMillis());
            return;
        }
        // Owner answer 7: never dropped. If the inventory filled up meanwhile, the rest goes to /box.
        // The box tells the player.
        net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), player, net.skybornmc.skyblock.common.box.BoxSource.SHOP, stacks.get());
        if (settings.savePlayerData) {
            try {
                player.saveData();
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Shop: could not save " + player.getName() + "'s data after a purchase", e);
            }
        }
        UUID uuid = player.getUniqueId();
        store.move(p.id(), ShopStore.DELIVERING, ShopStore.DELIVERED, System.currentTimeMillis()).whenCompleteAsync((moved, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Shop: purchase " + p.id() + " was handed over but could not be marked "
                        + "delivered; check it in /logs before handing it over again", Database.cause(e));
                return;
            }
            // F14: only a purchase handed over right now, and only once the row says delivered (the refund guard needs it).
            Player online = Bukkit.getPlayer(uuid);
            if (!late && Boolean.TRUE.equals(moved) && online != null && setting(online, SETTING_UNDO)) {
                undo.offer(uuid, new UndoLedger.Offer(p.id(), p.profile(), p.itemId(), p.quantity(), p.totalPrice(),
                        System.currentTimeMillis() + settings.undoSeconds * 1000L));
                refreshUndo(online);
            }
        }, main);
        Component name = displayName(p.itemId());
        if (late) {
            core.audit().logPlayer(AuditCategory.ITEM, "shop_deliver", Actor.system("shop"), p.player(), p.playerName(),
                    p.totalPrice(), player.getLocation(), details(p));
            msg.send(player, "buy.delivered-late", Msg.number("amount", p.quantity()), Msg.c("item", name));
        } else {
            // Action bar always ("✔ +64 Torch −$256 · $1,199,744 left"), a chat line only for big purchases.
            boolean partial = requested > p.quantity();
            TagResolver[] tags = {Msg.number("amount", p.quantity()), Msg.number("requested", requested), Msg.c("item", name),
                    money("price", p.totalPrice()),
                    money("balance", balance == null ? economy().cached(player.getUniqueId(), Currency.MONEY).orElse(0) : balance)};
            msg.actionBar(player, partial ? "buy.partial-bar" : "buy.bought-bar", tags);
            if (p.totalPrice() >= settings.chatFrom) {
                msg.send(player, partial ? "buy.partial" : "buy.bought", tags);
            }
        }
        core.sounds().play(player, late ? "money" : settings.buySound);
    }

    private static Map<String, Object> details(ShopStore.Purchase p) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("purchase", p.id().toString());
        d.put("item", p.itemId());
        d.put("quantity", p.quantity());
        d.put("unit_price", p.unitPrice());
        d.put("profile", p.profile().toString());
        return d;
    }

    /** The online player whose active profile is {@code profile}, not switching and not in staff mode; else null. */
    private Player receiver(UUID profile) {
        Player p = profiles().onlinePlayer(profile).orElse(null);
        if (p == null || !p.isOnline() || profiles().isSwitching(p.getUniqueId()) || flag(p, "staff_mode")) {
            return null;
        }
        return p;
    }

    /** Hands over purchases waiting for this player's active profile (each claimed exactly once). */
    void deliverPending(Player player) {
        if (!ready || player == null || !player.isOnline()) {
            return;
        }
        UUID profile = profiles().profileKey(player);
        if (!delivering.add(profile)) {
            return;
        }
        store.pending(profile).whenComplete((list, error) -> {
            if (error != null) {
                delivering.remove(profile);
                core.plugin().getLogger().log(Level.WARNING, "Shop: could not load waiting purchases of " + player.getName(),
                        Database.cause(error));
                return;
            }
            claimNext(profile, new ArrayList<>(list));
        });
    }

    /** One purchase after the other: claim (pending → delivering), then hand over on the main thread. */
    private void claimNext(UUID profile, List<ShopStore.Purchase> left) {
        if (left.isEmpty()) {
            delivering.remove(profile);
            return;
        }
        ShopStore.Purchase p = left.removeFirst();
        store.move(p.id(), ShopStore.PENDING, ShopStore.DELIVERING, System.currentTimeMillis()).whenCompleteAsync((claimed, e) -> {
            if (e == null && Boolean.TRUE.equals(claimed)) {
                Player player = receiver(profile);
                ItemStack template = template(p.itemId()).orElse(null);
                if (player != null && template != null
                        && InventorySpace.fits(player.getInventory(), template) < p.quantity()) {
                    // No room yet: it keeps waiting (handed over on the next join or profile switch).
                    store.move(p.id(), ShopStore.DELIVERING, ShopStore.PENDING, System.currentTimeMillis());
                    msg.send(player, "buy.waiting-no-room", Msg.number("amount", p.quantity()), Msg.c("item", displayName(p.itemId())));
                } else {
                    hand(p, true, null, p.quantity());
                }
            }
            claimNext(profile, left);
        }, main);
    }

    // ================================================================== undo last buy (F14)

    /**
     * "Undo last buy": takes every bought item back out of the inventory, then refunds the full price once (ledger
     * deposit + {@code delivered → refunded} in one database transaction). If the refund fails the items come back.
     * {@code after} runs on the main thread when done. Main thread.
     */
    void undoLast(Player player, Runnable after) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (!setting(player, SETTING_UNDO)) {
            fail(player, "undo.off");
            done(after);
            return;
        }
        Optional<UndoLedger.Offer> offer = undo.peek(uuid, now);
        if (offer.isEmpty()) {
            fail(player, "undo.nothing", Msg.number("seconds", settings.undoSeconds));
            done(after);
            return;
        }
        UndoLedger.Offer o = offer.get();
        Optional<String> problem = problem(player);
        if (problem.isPresent()) {
            fail(player, problem.get());
            done(after);
            return;
        }
        if (!o.profile().equals(profiles().profileKey(player))) {
            undo.clear(uuid);
            fail(player, "undo.other-profile");
            done(after);
            return;
        }
        ItemStack template = template(o.itemId()).orElse(null);
        if (template == null || InventorySpace.count(player.getInventory(), template) < o.quantity()) {
            fail(player, "undo.missing", Msg.number("amount", o.quantity()), Msg.c("item", displayName(o.itemId())));
            done(after);
            return;
        }
        if (!buying.add(uuid)) {
            fail(player, "buy.busy");
            done(after);
            return;
        }
        if (!undo.claim(uuid, o.purchase(), now)) {
            buying.remove(uuid);
            fail(player, "undo.nothing", Msg.number("seconds", settings.undoSeconds));
            done(after);
            return;
        }
        if (!InventorySpace.take(player.getInventory(), template, o.quantity())) {
            buying.remove(uuid);
            fail(player, "undo.missing", Msg.number("amount", o.quantity()), Msg.c("item", displayName(o.itemId())));
            done(after);
            return;
        }
        String name = player.getName();
        engine.undo(o, name, Actor.player(player)).whenCompleteAsync((status, error) -> {
            buying.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("purchase", o.purchase().toString());
            d.put("item", o.itemId());
            d.put("quantity", o.quantity());
            d.put("profile", o.profile().toString());
            if (error == null && status == TxResult.Status.SUCCESS) {
                core.audit().logPlayer(AuditCategory.ITEM, "shop_undo", Actor.system(ID), uuid, name, o.total(),
                        online == null ? null : online.getLocation(), d);
                if (online != null) {
                    msg.actionBar(online, "undo.done-bar", Msg.number("amount", o.quantity()),
                            Msg.c("item", displayName(o.itemId())), money("price", o.total()));
                    core.sounds().play(online, "money");
                }
            } else {
                // Nothing was refunded: the items go back (inventory, then /box; offline = box), never lost silently.
                d.put("status", String.valueOf(status));
                core.audit().logPlayer(AuditCategory.ITEM, "shop_undo_failed", Actor.system(ID), uuid, name, o.total(), null, d);
                core.plugin().getLogger().warning("Shop: undo of purchase " + o.purchase() + " for " + name + " failed (" + status
                        + "); the items were given back.");
                giveBack(uuid, o.profile(), template, o.quantity());
                if (online != null) {
                    fail(online, "undo.failed");
                }
            }
            done(after);
        }, main);
    }

    /** Gives undo items back after a failed refund: online on that profile = inventory then /box; otherwise that profile's /box. */
    private void giveBack(UUID uuid, UUID profile, ItemStack template, int quantity) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int amount : ShopMath.stacks(quantity, template.getMaxStackSize())) {
            ItemStack stack = template.clone();
            stack.setAmount(amount);
            stacks.add(stack);
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && profile.equals(profiles().profileKey(online))) {
            net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), online, net.skybornmc.skyblock.common.box.BoxSource.SHOP, stacks);
            return;
        }
        Optional<net.skybornmc.skyblock.common.box.ItemBox> box = net.skybornmc.skyblock.common.box.ItemBox.of(core.services());
        if (box.isPresent()) {
            box.get().deliver(profile, stacks, net.skybornmc.skyblock.common.box.BoxSource.SHOP, Actor.system(ID));
        } else {
            core.plugin().getLogger().severe("Shop: could not give back " + quantity + " " + template.getType() + " to " + uuid
                    + " after a failed undo (no box module); give them by hand.");
        }
    }

    /** Repaints the Undo button of an open shop page (after a buy the offer starts counting). */
    private void refreshUndo(Player player) {
        Menu open = core.menus().openMenu(player);
        if (open instanceof ShopMenu || open instanceof BuyMenu || open instanceof ShopCategoryMenu) {
            open.refresh();
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("shop:deliver", settings.joinDelayTicks, () -> deliverPending(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        undo.clear(event.getPlayer().getUniqueId());
        sellGuard.forget(event.getPlayer().getUniqueId());
    }

    /** The inventory is about to be swapped: close the shop first (an undo offer can't follow to another profile). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProfileSwitch(ProfileSwitchEvent event) {
        undo.clear(event.getPlayer().getUniqueId());
        if (isShopMenu(core.menus().openMenu(event.getPlayer()))) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        deliverPending(event.getPlayer());
    }

    static boolean isShopMenu(Menu menu) {
        if (menu instanceof ShopMenu || menu instanceof BuyMenu || menu instanceof ShopCategoryMenu || menu instanceof SellItemMenu) {
            return true;
        }
        return menu != null && menu.parent() != null && isShopMenu(menu.parent());
    }

    // ================================================================== menu parts

    /** A category's tile icon: its catalogue icon ({@code shop/<id>}), or the vanilla {@code icon} item for owner-added categories. */
    ItemStack categoryIcon(Player viewer, ShopCatalog.Category c, Component name, List<Component> lore) {
        if (core.menus().icons().has(c.iconKey())) {
            return core.menus().icon(viewer, c.iconKey(), name, lore);
        }
        Material icon = Material.matchMaterial(c.icon() == null ? "" : c.icon());
        return core.menus().item(new ItemStack(icon == null || !icon.isItem() ? Material.CHEST : icon), name, lore);
    }

    Component categoryName(Player viewer, ShopCatalog.Category c) {
        String key = "categories." + c.id() + ".name";
        return msg.has(key) ? core.menus().text(viewer, ID, key) : Component.text(words(c.id()));
    }

    List<Component> categoryLore(Player viewer, ShopCatalog.Category c) {
        String key = "categories." + c.id() + ".lore";
        return msg.has(key) ? core.menus().lines(viewer, ID, key) : List.of();
    }

    /** A category name as a plain title crumb (titles stay grey, I9 section 10). */
    Component categoryCrumb(Player viewer, ShopCatalog.Category c) {
        return Component.text(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(categoryName(viewer, c)));
    }

    /** The sell chest button (46 on the grid, 28/19 on the category menu), when selling exists. */
    Optional<MenuButton> sellChestButton(Player viewer, Menu from) {
        return sellButton(viewer).map(item -> MenuButton.of(item, click -> openSell(click.player(), from)));
    }

    static String words(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.split("[-_]")) {
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

    /**
     * Undo last buy: a clock ({@code state/undo}) whose count is the seconds left, or a gray dye ({@code state/off})
     * "Nothing to undo". Empty when the player turned undo off in /settings.
     */
    Optional<MenuButton> undoButton(Player viewer) {
        if (viewer == null || !setting(viewer, SETTING_UNDO)) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        Optional<UndoLedger.Offer> offer = undo.peek(viewer.getUniqueId(), now);
        if (offer.isEmpty()) {
            return Optional.of(MenuButton.display(core.menus().icon(viewer, "state/off", core.menus().text(viewer, ID, "undo.idle.name"),
                    core.menus().lines(viewer, ID, "undo.idle.lore"))));
        }
        UndoLedger.Offer o = offer.get();
        int seconds = o.secondsLeft(now);
        TagResolver[] tags = {Msg.number("amount", o.quantity()), Msg.c("item", displayName(o.itemId())), money("price", o.total()),
                Msg.number("seconds", seconds)};
        ItemStack plate = core.menus().icon(viewer, "state/undo", core.menus().text(viewer, ID, "undo.ready.name", tags),
                core.menus().lines(viewer, ID, "undo.ready.lore", tags));
        plate.setAmount(Math.max(1, Math.min(64, seconds)));
        return Optional.of(MenuButton.of(plate, click -> undoLast(click.player(), () -> {
            Menu open = core.menus().openMenu(click.player());
            if (open == click.menu()) {
                open.refresh();
            }
        })));
    }

    // ================================================================== helpers

    EconomyService economy() {
        return core.services().optional(EconomyService.class).orElseThrow(() -> new IllegalStateException("economy is gone"));
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    private boolean flag(Player player, String placeholder) {
        return player != null && core.placeholders().has(placeholder)
                && core.placeholders().text(player, placeholder).map("true"::equalsIgnoreCase).orElse(false);
    }

    TagResolver money(String name, long amount) {
        return Msg.p(name, moneyText(amount));
    }

    /** A plain money amount ({@code $512}) for lore built with the LoreBuilder. */
    String moneyText(long amount) {
        return economy().format(Currency.MONEY, amount);
    }

    /** A raw (unparsed) text of messages/shop.yml, for LoreBuilder labels; the key itself if it is missing. */
    String raw(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? key : raw;
    }

    /** Bedrock player (Floodgate): the ? guide leaves out the Java shift-click shortcut; Type an amount uses the picker. */
    boolean bedrock(Player player) {
        return player != null && core.bedrock().isBedrock(player.getUniqueId());
    }

    /** The sell chest button item ({@code shop/sell}, gold nugget), if selling exists. */
    Optional<ItemStack> sellButton(Player viewer) {
        if (core.services().optional(SellService.class).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(core.menus().icon(viewer, "shop/sell", core.menus().text(viewer, ID, "menu.sell.name"),
                core.menus().lines(viewer, ID, "menu.sell.lore")));
    }

    // ================================================================== selling (GUI-P3)

    Optional<SellService> sellService() {
        return core.services().optional(SellService.class);
    }

    /**
     * Whether {@code item} can be sold now, its live price and (with {@code withCarried}) how many the player carries.
     * Spawners are never sellable here. Main thread.
     */
    SellInfo sellInfo(Player player, ShopCatalog.Item item, boolean withCarried) {
        Optional<SellService> sell = sellService();
        if (sell.isEmpty() || item.spawner()) {
            return SellInfo.NONE;
        }
        Optional<ItemStack> template = template(item);
        if (template.isEmpty() || !sell.get().sellable(template.get())) {
            return SellInfo.NONE;
        }
        long carried = withCarried && player != null ? sell.get().carried(player, template.get()) : 0;
        return new SellInfo(true, sell.get().price(item.id()), carried);
    }

    /** Middle click / Shift + Right-click on a tile: sells every one of that item the player carries. Main thread. */
    void sellAll(Player player, ShopCatalog.Item item, Runnable after) {
        sellAmount(player, item, 0, after);
    }

    /**
     * The only code in the shop that sells (GUI-P3 5.8): checks, the click-spam guard, then {@link SellService#sellItem}
     * with source {@code shop} (the /sell escrow, one ledger transaction {@code sell:<sale id>}, give-back to the
     * inventory then /box). The economy prints the sale line and plays the money sound. {@code amount} 0 = all.
     */
    void sellAmount(Player player, ShopCatalog.Item item, int amount, Runnable after) {
        Optional<String> problem = problem(player);
        if (problem.isPresent()) {
            fail(player, problem.get());
            done(after);
            return;
        }
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player,
                net.skybornmc.skyblock.common.moderation.SanctionService.Use.SHOP)) {
            done(after);
            return; // frozen balance blocks selling like buying
        }
        SellInfo info = sellInfo(player, item, true);
        Optional<SellService> sell = sellService();
        Optional<ItemStack> template = template(item);
        if (!info.sellable() || sell.isEmpty() || template.isEmpty()) {
            core.sounds().play(player, "error"); // the tile already says Not sellable
            done(after);
            return;
        }
        if (info.carried() <= 0) {
            msg.actionBar(player, "sell.none-carried", Msg.c("item", displayName(player, item)));
            core.sounds().play(player, "error");
            done(after);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!sellGuard.tryStart(uuid, System.currentTimeMillis(), settings.sellMinGapMs)) {
            return; // click spam: one sale at a time, with a short gap
        }
        sell.get().sellItem(player, template.get(), Math.max(0, amount), "shop", result -> {
            sellGuard.finish(uuid, System.currentTimeMillis());
            done(after);
        });
    }

    /** True while a shop sale of this player is on its way (the sell GUI skips its live refresh). */
    boolean selling(Player player) {
        return player != null && sellGuard.inFlight(player.getUniqueId());
    }

    void openSell(Player player, Menu from) {
        core.services().optional(SellService.class).ifPresent(s -> s.openSellChest(player, from));
    }

    long balance(Player player) {
        return economy().cached(player.getUniqueId(), Currency.MONEY).orElse(0);
    }

    static Component itemName(Material material) {
        return Component.translatable(material.translationKey());
    }

    Component skillName(Player viewer, String skill) {
        String key = "skills." + skill;
        return msg.has(key) ? msg.component(viewer, key) : Component.text(skill);
    }

    void fail(CommandSender to, String key, TagResolver... placeholders) {
        msg.send(to, key, placeholders);
        if (to instanceof Player p) {
            core.sounds().play(p, "error");
        }
    }
}
