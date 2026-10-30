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
 * The builder shop (M3.5, quick-buy grid SELL2): {@code /shop [category]} opens categories → a quick-buy grid (Java:
 * left-click 1, right-click 64, shift-click the amount screen; Bedrock: a tap opens the amount screen). Prices come from
 * {@code shop.yml}; a real money sink (no selling back here: the Sell button opens the {@code /sell} chest).
 *
 * <p><b>Purchase safety.</b> Free inventory space is counted before paying: when not all of it fits, only what fits is
 * bought and charged ("Bought 40 of 64: no more room"); no room at all = refused, nothing paid. The payment
 * is one ledger transaction from the player's <b>profile</b> account that writes the purchase row in the same database
 * transaction. Items are handed out only after it succeeded, then the row is marked delivered. If the player left or
 * switched profile meanwhile, the purchase waits ({@code pending}) and is handed over on the next join / switch back;
 * rows a crash left half-way are handed over again on startup. Items that no longer fit (the inventory filled up while
 * the payment ran) drop at the player's feet.</p>
 *
 * <p><b>Buy &gt; sell.</b> An item is never sold for less than it (or what it turns back into) could ever bring with
 * {@code /sell} at today's settings ({@link SellService#maxUnitPrice}); such items show as not for sale and the console
 * names them until their price is raised.</p>
 */
public final class ShopModule implements SkyblockModule, Listener {

    static final String ID = "shop";
    static final String PERM_USE = "skyblock.shop.use";
    static final String CATALOG_FILE = "shop.yml";

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
        registerCommand();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        store.recover(System.currentTimeMillis()).whenComplete((n, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Shop: startup recovery failed; purchases may wait until the next start",
                        Database.cause(e));
            } else if (n > 0) {
                core.plugin().getLogger().warning("Shop: " + n + " purchase(s) were paid but not handed over (crash or stop); "
                        + "they are handed over on the next join.");
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
    }

    // ================================================================== catalog

    ShopCatalog catalog() {
        return catalog;
    }

    /**
     * Reads {@code shop.yml}; a missing file is written from the bundled one, and items that are new in the bundled file
     * are added to an existing file (switch an item off with {@code enabled: false} instead of deleting it).
     */
    private ShopCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), CATALOG_FILE);
        YamlConfiguration bundled = bundled();
        Predicate<String> material = ShopModule::isItem;
        Consumer<String> warn = w -> core.plugin().getLogger().warning(w);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            warn.accept("shop.yml is not valid YAML, using the bundled prices. " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return ShopCatalog.load(bundled, material, warn);
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
            core.plugin().getLogger().log(Level.WARNING, "Could not write shop.yml", e);
        }
        ShopCatalog loaded = ShopCatalog.load(yaml, material, warn);
        core.plugin().getLogger().info("Shop: " + loaded.categories().size() + " categories, " + loaded.size() + " item(s).");
        return loaded;
    }

    /** Adds bundled categories/items the file does not have yet (an item moved to another category is not added again). */
    static boolean addMissing(YamlConfiguration yaml, YamlConfiguration bundled) {
        var root = bundled.getConfigurationSection(ShopCatalog.ROOT);
        if (root == null) {
            return false;
        }
        boolean changed = false;
        for (String category : root.getKeys(false)) {
            String base = ShopCatalog.ROOT + "." + category;
            if (!yaml.contains(base)) {
                // A new category: its settings, then its items one by one (items the owner moved elsewhere stay there).
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
                if (!yaml.contains(base + ".items." + id) && !listedElsewhere(yaml, id)) {
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

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CATALOG_FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, "shop.yml in the jar is broken", e);
        }
        return yaml;
    }

    static boolean isItem(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir();
    }

    static Material material(ShopCatalog.Item item) {
        return Material.matchMaterial(item.id());
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
        Arg<ShopCatalog.Category> category = Arg.word("category", (sender, raw) -> catalog.category(raw)
                .orElseThrow(() -> new CommandFailure(ID, "unknown-category", Msg.p("input", raw),
                        Msg.p("categories", String.join(", ", categoryIds())))), Suggest.of(this::categoryIds));
        core.commands().register(CommandSpec.command("shop")
                .description("Buy building blocks, decoration and more")
                .category("Economy")
                .permission(PERM_USE)
                .playerOnly()
                .optional(category)
                .example("shop wood")
                .executes(ctx -> open(ctx.player(), ctx.getOr(category, null))));
    }

    List<String> categoryIds() {
        return catalog.categories().stream().map(ShopCatalog.Category::id).toList();
    }

    void open(Player player, ShopCatalog.Category category) {
        Optional<String> problem = problem(player);
        if (problem.isPresent()) {
            throw new CommandFailure(ID, problem.get());
        }
        ShopMenu home = new ShopMenu(this);
        if (category == null) {
            core.menus().open(player, home);
        } else {
            CategoryMenu menu = new CategoryMenu(this, category);
            menu.parent(home); // Back goes to the categories
            core.menus().open(player, menu);
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
        } else if (problem.isEmpty() && !priceSafe(item)) {
            problem = Optional.of("buy.unavailable");
        }
        if (problem.isPresent()) {
            fail(player, problem.get());
            done(after);
            return;
        }
        Optional<Map.Entry<String, Integer>> missing = missingRequirement(player, item);
        if (missing.isPresent()) {
            fail(player, "buy.locked", Msg.c("skill", skillName(player, missing.get().getKey())),
                    Msg.number("level", missing.get().getValue()));
            done(after);
            return;
        }
        if (quantity < 1 || quantity > settings.maxAmount) {
            fail(player, "buy.bad-amount", Msg.number("max", settings.maxAmount));
            done(after);
            return;
        }
        Material material = material(item);
        long fits = InventorySpace.fits(player.getInventory(), new ItemStack(material));
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
        Material material = Material.matchMaterial(p.itemId());
        if (material == null) {
            core.plugin().getLogger().severe("Shop: purchase " + p.id() + " is for unknown item '" + p.itemId() + "'; it stays "
                    + "waiting for staff (refund " + p.totalPrice() + " to " + p.playerName() + " or fix the item id).");
            store.move(p.id(), ShopStore.DELIVERING, ShopStore.PENDING, System.currentTimeMillis());
            return;
        }
        int dropped = 0;
        int maxStack = new ItemStack(material).getMaxStackSize();
        for (int amount : ShopMath.stacks(p.quantity(), maxStack)) {
            for (ItemStack rest : player.getInventory().addItem(new ItemStack(material, amount)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
                dropped += rest.getAmount();
            }
        }
        if (settings.savePlayerData) {
            try {
                player.saveData();
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Shop: could not save " + player.getName() + "'s data after a purchase", e);
            }
        }
        store.move(p.id(), ShopStore.DELIVERING, ShopStore.DELIVERED, System.currentTimeMillis()).exceptionally(e -> {
            core.plugin().getLogger().log(Level.SEVERE, "Shop: purchase " + p.id() + " was handed over but could not be marked "
                    + "delivered; check it in /logs before handing it over again", Database.cause(e));
            return false;
        });
        Component name = itemName(material);
        if (late) {
            core.audit().logPlayer(AuditCategory.ITEM, "shop_deliver", Actor.system("shop"), p.player(), p.playerName(),
                    p.totalPrice(), player.getLocation(), details(p));
            msg.send(player, "buy.delivered-late", Msg.number("amount", p.quantity()), Msg.c("item", name));
        } else {
            // SELL2 feedback: the action bar always ("✔ +64 Oak Log -$512"), a chat line only for big purchases.
            boolean partial = requested > p.quantity();
            TagResolver[] tags = {Msg.number("amount", p.quantity()), Msg.number("requested", requested), Msg.c("item", name),
                    money("price", p.totalPrice()),
                    money("balance", balance == null ? economy().cached(player.getUniqueId(), Currency.MONEY).orElse(0) : balance)};
            msg.actionBar(player, partial ? "buy.partial-bar" : "buy.bought-bar", tags);
            if (p.totalPrice() >= settings.chatFrom) {
                msg.send(player, partial ? "buy.partial" : "buy.bought", tags);
            }
        }
        if (dropped > 0) {
            msg.send(player, "buy.dropped", Msg.number("amount", dropped));
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
                Material material = Material.matchMaterial(p.itemId());
                if (player != null && material != null
                        && InventorySpace.fits(player.getInventory(), new ItemStack(material)) < p.quantity()) {
                    // No room yet: it keeps waiting (handed over on the next join or profile switch).
                    store.move(p.id(), ShopStore.DELIVERING, ShopStore.PENDING, System.currentTimeMillis());
                    msg.send(player, "buy.waiting-no-room", Msg.number("amount", p.quantity()), Msg.c("item", itemName(material)));
                } else {
                    hand(p, true, null, p.quantity());
                }
            }
            claimNext(profile, left);
        }, main);
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("shop:deliver", settings.joinDelayTicks, () -> deliverPending(player));
    }

    /** The inventory is about to be swapped: close the shop first. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProfileSwitch(ProfileSwitchEvent event) {
        if (isShopMenu(core.menus().openMenu(event.getPlayer()))) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        deliverPending(event.getPlayer());
    }

    static boolean isShopMenu(Menu menu) {
        if (menu instanceof ShopMenu || menu instanceof CategoryMenu || menu instanceof BuyAmountMenu) {
            return true;
        }
        return menu != null && menu.parent() != null && isShopMenu(menu.parent());
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

    /** Bedrock player (Floodgate): taps only, so the quick-buy grid opens the amount screen. */
    boolean bedrock(Player player) {
        return player != null && core.bedrock().isBedrock(player.getUniqueId());
    }

    // ================================================================== quick buy (SELL2)

    /** Buys now, or asks to confirm first when the total reaches {@code buy.confirm-above}. {@code after}: refresh. */
    void attempt(Menu from, Player player, ShopCatalog.Item item, int amount, Runnable after) {
        long total = ShopMath.total(item.price(), amount).orElse(Long.MAX_VALUE);
        if (total >= settings.confirmAbove) {
            Material m = material(item);
            Material shownMaterial = m == null ? Material.BARRIER : m;
            Component name = itemName(shownMaterial);
            ItemStack shown = core.menus().item(new ItemStack(shownMaterial, Math.max(1, Math.min(shownMaterial.getMaxStackSize(), amount))),
                    core.menus().text(player, ID, "confirm.shown", Msg.number("amount", amount), Msg.c("item", name)),
                    core.menus().lines(player, ID, "confirm.shown-lore", money("total", total)));
            net.skybornmc.skyblock.common.menu.ConfirmMenu confirm = new net.skybornmc.skyblock.common.menu.ConfirmMenu(
                    v -> core.menus().text(v, ID, "confirm.crumb"), shown,
                    v -> core.menus().lines(v, ID, "confirm.lines", Msg.number("amount", amount), Msg.c("item", name),
                            money("total", total), money("balance", balance(v))),
                    p -> buy(p, item, amount, after), null);
            confirm.parent(from);
            core.menus().open(player, confirm);
            return;
        }
        buy(player, item, amount, after);
    }

    /** The Sell button of the shop menus (opens the sell chest), if selling exists. */
    Optional<ItemStack> sellButton(Player viewer) {
        if (core.services().optional(SellService.class).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(core.menus().item(Material.GOLD_INGOT, viewer, ID, "menu.sell"));
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
