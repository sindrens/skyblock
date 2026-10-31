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
 * The builder shop (M3.5, Shop v2 GUI-3, docs/design/SHOP_DESIGN.md): {@code /shop [tab]} opens one tabbed page (9 tabs in
 * the top row, no category screen). A click on an item opens its buy page (Buy 1/16/64, Fill inventory, Type an amount);
 * Java shift-click buys a stack. Prices come from {@code shop.yml}; a real money sink (no selling back here: the Sell
 * button opens the {@code /sell} chest). F14: "Undo last buy" for 15 s and a second click for buys over half your money,
 * both switchable in {@code /settings}.
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
    /** /settings toggle ids (F14). */
    static final String SETTING_UNDO = "shop-undo";
    static final String SETTING_BIG_BUY = "shop-big-buy";

    final Core core;
    final ModuleMessages msg;
    final ShopSettings settings = new ShopSettings();
    private volatile ShopCatalog catalog = ShopCatalog.empty();
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
    /** The tab /shop opens next (the last one used; Essentials the first time). */
    private final Map<UUID, String> lastTab = new ConcurrentHashMap<>();

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
        catalog = loadCatalog();
        warnUndercuts();
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
        catalog = loadCatalog();
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
     * Reads {@code shop.yml}; a missing file is written from the bundled one. A version-1 file is migrated once to Shop v2
     * ({@link ShopCatalogMigration}: backup {@code shop.yml.before-v2}, every change in the console, in
     * {@code shop-migration-v2.log} and in the audit log). Items that are new in the bundled file are added to an existing
     * file (switch an item off with {@code enabled: false} instead of deleting it).
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
            return ShopCatalog.load(bundled, material, spawner, warn);
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
                Optional<ShopCatalogMigration.Result> migrated = ShopCatalogMigration.migrate(yaml, bundled, bundled(V1_DEFAULTS));
                if (migrated.isPresent()) {
                    yaml = migrated.get().yaml();
                    writeMigration(file, yaml, migrated.get().changes());
                }
                if (addMissing(yaml, bundled)) {
                    yaml.save(file);
                }
            }
        } catch (IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not write shop.yml", e);
        }
        ShopCatalog loaded = ShopCatalog.load(yaml, material, spawner, warn);
        core.plugin().getLogger().info("Shop: " + loaded.categories().size() + " tabs, " + loaded.size() + " item(s).");
        return loaded;
    }

    /** Saves a migrated shop.yml: backup first, then the file, the change log and one audit row. */
    private void writeMigration(File file, YamlConfiguration yaml, List<String> changes) throws IOException {
        File backup = new File(file.getParentFile(), CATALOG_FILE + ".before-v2");
        if (!backup.exists()) {
            java.nio.file.Files.copy(file.toPath(), backup.toPath());
        }
        yaml.save(file);
        java.nio.file.Files.write(new File(file.getParentFile(), "shop-migration-v2.log").toPath(), changes, StandardCharsets.UTF_8);
        core.plugin().getLogger().info("Shop: shop.yml moved to Shop v2 (9 tabs). Backup: " + backup.getName() + ". Changes:");
        changes.forEach(c -> core.plugin().getLogger().info("Shop migration: " + c));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", ShopCatalogMigration.VERSION);
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

    /** A mob type the stacking module sells as a spawner (any tier; the shop's own max tier is checked when buying). */
    private boolean isSpawnerType(String type) {
        return stacking().map(st -> st.spawnerTier(type).isPresent()).orElse(false);
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

    /** Today's /sell price of one shop item (before sell bonuses), rounded to whole money; empty if /sell doesn't buy it. */
    java.util.OptionalLong sellPrice(ShopCatalog.Item item) {
        return core.services().optional(SellService.class).flatMap(s -> s.price(item.id()))
                .map(p -> java.util.OptionalLong.of(Math.max(0, Math.round(p.unit())))).orElse(java.util.OptionalLong.empty());
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

    private void registerCommand() {
        Arg<ShopCatalog.Category> category = Arg.word("tab", (sender, raw) -> tabOf(raw)
                .orElseThrow(() -> new CommandFailure(ID, "unknown-category", Msg.p("input", raw),
                        Msg.p("categories", String.join(", ", categoryIds())))), Suggest.of(this::categoryIds));
        core.commands().register(CommandSpec.command("shop")
                .description("Buy building blocks, decoration, spawners and more")
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

    /** Opens the shop on {@code category}, or on the player's last tab (Essentials, the first tab, the first time). */
    void open(Player player, ShopCatalog.Category category) {
        Optional<String> problem = problem(player);
        if (problem.isPresent()) {
            throw new CommandFailure(ID, problem.get());
        }
        String tab = category != null ? category.id() : lastTab.get(player.getUniqueId());
        if (tab == null || catalog.category(tab).isEmpty()) {
            tab = catalog.categories().isEmpty() ? "" : catalog.categories().getFirst().id();
        }
        rememberTab(player, tab);
        core.menus().open(player, new ShopMenu(this, tab));
    }

    void rememberTab(Player player, String tab) {
        if (player != null && tab != null && !tab.isEmpty()) {
            lastTab.put(player.getUniqueId(), tab);
        }
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

    long incomePerHour(ShopCatalog.Item item) {
        return settings.incomePerHour(spawnerTier(item));
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
        if (open instanceof ShopMenu || open instanceof BuyMenu) {
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
        if (menu instanceof ShopMenu || menu instanceof BuyMenu) {
            return true;
        }
        return menu != null && menu.parent() != null && isShopMenu(menu.parent());
    }

    // ================================================================== menu parts

    /** A tab of row 0: gold glint and "✔ You are here" when selected (no click), otherwise one click switches. */
    MenuButton tabButton(Player viewer, ShopCatalog.Category c, boolean selected, Consumer<MenuClick> onClick) {
        Material icon = Material.matchMaterial(c.icon());
        List<Component> lore = new ArrayList<>(tabDescription(viewer, c));
        lore.addAll(core.menus().lines(viewer, "tab." + (selected ? "selected" : "action")));
        ItemStack item = core.menus().item(new ItemStack(icon == null ? Material.CHEST : icon),
                msg.component(viewer, "tab-name", Msg.c("name", tabName(viewer, c))), lore);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return MenuButton.display(item);
        }
        return MenuButton.of(item, onClick);
    }

    Component tabName(Player viewer, ShopCatalog.Category c) {
        String key = "categories." + c.id() + ".name";
        return msg.has(key) ? core.menus().text(viewer, ID, key) : Component.text(words(c.id()));
    }

    private List<Component> tabDescription(Player viewer, ShopCatalog.Category c) {
        String key = "categories." + c.id() + ".lore";
        return msg.has(key) ? core.menus().lines(viewer, ID, key) : List.of();
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

    /** The shop's own bottom-bar buttons: 46 balance, 47 Undo last buy (when on), 51 Sell. */
    void decorateBar(Menu menu, Player viewer, java.util.function.BiConsumer<Integer, MenuButton> set) {
        set.accept(ShopMenu.SLOT_BALANCE, MenuButton.display(core.menus().item(Material.EMERALD, viewer, ID, "menu.balance",
                money("balance", balance(viewer)))));
        undoButton(viewer).ifPresent(b -> set.accept(ShopMenu.SLOT_UNDO, b));
        sellButton(viewer).ifPresent(button -> set.accept(ShopMenu.SLOT_SELL,
                MenuButton.of(button, click -> openSell(click.player(), menu))));
    }

    /**
     * Undo last buy (47): a gold plate whose count is the seconds left, or a grey "Nothing to undo" plate. Empty when the
     * player turned undo off in /settings.
     */
    Optional<MenuButton> undoButton(Player viewer) {
        if (viewer == null || !setting(viewer, SETTING_UNDO)) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        Optional<UndoLedger.Offer> offer = undo.peek(viewer.getUniqueId(), now);
        if (offer.isEmpty()) {
            return Optional.of(MenuButton.display(core.menus().item(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, viewer, ID, "undo.idle")));
        }
        UndoLedger.Offer o = offer.get();
        int seconds = o.secondsLeft(now);
        ItemStack plate = core.menus().item(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, viewer, ID, "undo.ready",
                Msg.number("amount", o.quantity()), Msg.c("item", displayName(o.itemId())), money("price", o.total()),
                Msg.number("seconds", seconds));
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

    /** The Sell button of the shop pages (opens the sell chest), if selling exists. Gold nugget until pack art exists. */
    Optional<ItemStack> sellButton(Player viewer) {
        if (core.services().optional(SellService.class).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(core.menus().item(Material.GOLD_NUGGET, viewer, ID, "menu.sell"));
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
