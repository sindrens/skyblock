package net.skybornmc.skyblock.economy.store;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.CreditSpend;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.StoreChargebackEvent;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.moderation.SanctionService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.store.CreditShop;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The credit shop (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 1): the offers of every module, the purchase (pay, then
 * deliver, refund when the delivery fails), the account things ({@link #owned}), the ranks and server-wide boosters
 * the economy sells itself, and "gg" after a purchase.
 *
 * <p><b>One purchase:</b> the credits and a {@code credit_shop_purchases} row (state {@code paid}) are written in one
 * database transaction; the delivery runs after that on the main thread; its result sets the row to {@code delivered}
 * or refunds the exact credits (same halves, {@link CreditSpend#refund}) and sets {@code refunded}. A buyer can't start a
 * second purchase while one runs (double clicks).</p>
 */
public final class CreditShopImpl implements CreditShop, Listener {

    public static final String MODULE = "economy";
    /** Offer id prefix of the ranks (chargebacks look for it). */
    static final String RANK = "rank_";

    private final Core core;
    private final ModuleMessages msg;
    private final Supplier<EconomyConfig> config;
    private final EconomyService economy;
    private final Executor main;
    private final java.util.function.Consumer<UUID> refreshCredits;
    private final Map<UUID, Map<String, Integer>> owned = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    /** gg window: purchase id → (ends at, players who already said gg). */
    private final Map<String, GgWindow> gg = new ConcurrentHashMap<>();
    private volatile RankLadder ladder = new RankLadder(List.of());

    record GgWindow(long until, Set<UUID> said) {
    }

    public CreditShopImpl(Core core, ModuleMessages msg, Supplier<EconomyConfig> config, EconomyService economy, Executor main,
                          java.util.function.Consumer<UUID> refreshCredits) {
        this.refreshCredits = refreshCredits;
        this.core = core;
        this.msg = msg;
        this.config = config;
        this.economy = economy;
        this.main = main;
    }

    /** Enable / reload: the ranks and boosters of the economy itself. Main thread. */
    public void reload() {
        ladder = RankLadder.parse(config.get().creditShopRanks, problem -> core.plugin().getLogger().warning(problem));
        unregisterAll(MODULE);
        registerRanks();
        registerBoosters();
        registerRenameToken();
    }

    RankLadder ladder() {
        return ladder;
    }

    Core core() {
        return core;
    }

    ModuleMessages msg() {
        return msg;
    }

    EconomyService economy() {
        return economy;
    }

    // ------------------------------------------------------------------ registry

    private static void register(String module, Offer offer) {
        CreditShop.register(module, offer);
    }

    private static void unregisterAll(String module) {
        CreditShop.unregisterAll(module);
    }

    /** The offers of a tab, in order. */
    List<Offer> offers(Tab tab) {
        List<Offer> out = new ArrayList<>();
        for (Map<String, Offer> m : CreditShop.offers().values()) {
            for (Offer o : m.values()) {
                if (o.tab() == tab) {
                    out.add(o);
                }
            }
        }
        out.sort(Comparator.comparingInt(Offer::order).thenComparing(Offer::id));
        return out;
    }

    @Override
    public void open(Player player, Tab tab) {
        if (!config.get().creditShopEnabled) {
            msg.send(player, "credit.shop.disabled");
            core.sounds().play(player, "error");
            return;
        }
        core.menus().open(player, new CreditShopMenu(this, tab == null ? Tab.RANKS : tab, null));
    }

    /** The shop in gift mode for {@code receiver}. */
    public void openGift(Player buyer, Player receiver) {
        core.menus().open(buyer, new CreditShopMenu(this, Tab.RANKS, receiver));
    }

    // ------------------------------------------------------------------ account things

    @Override
    public int owned(UUID account, String key) {
        Map<String, Integer> m = owned.get(account);
        return m == null ? 0 : m.getOrDefault(key, 0);
    }

    @Override
    public CompletableFuture<Boolean> consume(UUID account, String key) {
        return core.database().transaction(c -> Sql.update(c,
                        "UPDATE credit_shop_owned SET amount = amount - 1, updated_at = ? WHERE account_uuid = ? AND thing = ? AND amount > 0",
                        System.currentTimeMillis(), account, key) == 1)
                .thenApplyAsync(done -> {
                    if (done) {
                        owned.computeIfPresent(account, (k, m) -> {
                            m.merge(key, -1, Integer::sum);
                            return m;
                        });
                    }
                    return done;
                }, main);
    }

    /** Adds account things (in the purchase's own transaction, see {@link #giveThing}). */
    private static void addThing(java.sql.Connection c, UUID account, String key, int amount, long now) throws java.sql.SQLException {
        if (Sql.update(c, "UPDATE credit_shop_owned SET amount = amount + ?, updated_at = ? WHERE account_uuid = ? AND thing = ?",
                amount, now, account, key) == 0) {
            Sql.update(c, "INSERT INTO credit_shop_owned (account_uuid, thing, amount, updated_at) VALUES (?, ?, ?, ?)", account, key, amount, now);
        }
    }

    /** A delivery that gives one account thing (tokens, extra vaults ...). */
    @Override
    public Deliver giveThing(String key) {
        return (buyer, receiver, purchaseId) -> core.database().transaction(c -> {
            addThing(c, receiver.getUniqueId(), key, 1, System.currentTimeMillis());
            return true;
        }).thenApplyAsync(ok -> {
            owned.computeIfAbsent(receiver.getUniqueId(), k -> new ConcurrentHashMap<>()).merge(key, 1, Integer::sum);
            return ok;
        }, main);
    }

    /** Join: the account things into the cache. */
    public void load(Player p) {
        UUID id = p.getUniqueId();
        core.database().async(c -> Sql.query(c, "SELECT thing, amount FROM credit_shop_owned WHERE account_uuid = ?",
                        rs -> Map.entry(rs.getString(1), rs.getInt(2)), id))
                .thenAcceptAsync(rows -> {
                    Map<String, Integer> m = new ConcurrentHashMap<>();
                    rows.forEach(e -> m.put(e.getKey(), e.getValue()));
                    owned.put(id, m);
                }, main);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        owned.remove(event.getPlayer().getUniqueId());
        busy.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ buying

    /** Pure: a new purchase id (fits the 94-character key limit with the refund prefix). */
    static String newPurchaseId() {
        return "cs:" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /**
     * Buys {@code offer} for {@code receiver} (the buyer themselves unless it is a gift). Main thread. {@code after} runs
     * on the main thread when it is over (bought or not), e.g. to redraw the menu.
     */
    void buy(Player buyer, Offer offer, Player receiver, Runnable after) {
        boolean gift = !buyer.equals(receiver);
        long price = offer.price().apply(buyer, receiver);
        if (offer.deliver() == null || price < 0 || (gift && !offer.giftable())) {
            msg.send(buyer, "credit.shop.not-for-sale");
            core.sounds().play(buyer, "error");
            after.run();
            return;
        }
        if (SanctionService.denied(core.services(), buyer, SanctionService.Use.SHOP)) {
            after.run();
            return;
        }
        if (!busy.add(buyer.getUniqueId())) {
            msg.send(buyer, "credit.shop.busy");
            return;
        }
        boolean boughtOnly = offer.boughtOnly() || gift;
        String id = newPurchaseId();
        long now = System.currentTimeMillis();
        UUID buyerId = buyer.getUniqueId();
        UUID receiverId = receiver.getUniqueId();
        Transaction tx = Transaction.withdraw(AccountRef.player(buyerId), Currency.CREDITS, price, TxReason.CREDIT_SHOP)
                .actor(Actor.player(buyer)).reference("credit_shop:" + offer.id()).idempotencyKey(id)
                .detail("offer", offer.id()).detail("receiver", receiverId.toString());
        TxGuard row = c -> Sql.update(c, "INSERT INTO credit_shop_purchases (id, buyer_uuid, receiver_uuid, offer_id, price, bought_only, state, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'paid', ?)", id, buyerId, receiverId, offer.id(), price, boughtOnly, now) == 1;
        CompletableFuture<TxResult> paid = boughtOnly ? economy.executeGuarded(tx, row) : CreditSpend.execute(economy, tx, row);
        paid.whenCompleteAsync((result, error) -> {
            if (error != null || result == null || !result.ok()) {
                busy.remove(buyerId);
                String key = error != null || result == null ? "credit.shop.failed"
                        : switch (result.status()) {
                    case INSUFFICIENT_FUNDS -> boughtOnly ? "credit.shop.not-enough-bought" : "credit.shop.not-enough";
                    default -> "credit.shop.failed";
                };
                if (buyer.isOnline()) {
                    msg.send(buyer, key, Msg.number("price", price));
                    core.sounds().play(buyer, "error");
                }
                after.run();
                return;
            }
            refreshCredits(buyerId);
            if (!receiver.isOnline()) {
                refund(buyer, offer, price, id, boughtOnly, after);
                return;
            }
            CompletableFuture<Boolean> given;
            try {
                given = offer.deliver().deliver(buyer, receiver, id);
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Credit shop: delivery of " + offer.id() + " failed", e);
                given = CompletableFuture.completedFuture(false);
            }
            given.whenCompleteAsync((ok, deliverError) -> {
                if (deliverError != null || ok == null || !ok) {
                    if (deliverError != null) {
                        core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Credit shop: delivery of " + offer.id() + " failed", deliverError);
                    }
                    refund(buyer, offer, price, id, boughtOnly, after);
                    return;
                }
                busy.remove(buyerId);
                core.database().run(c -> Sql.update(c, "UPDATE credit_shop_purchases SET state = 'delivered' WHERE id = ?", id));
                Map<String, Object> details = new HashMap<>();
                details.put("offer", offer.id());
                details.put("purchase", id);
                details.put("receiver", receiver.getName());
                details.put("bought_only", boughtOnly);
                core.audit().log(AuditCategory.ECONOMY, "credit_shop_buy", Actor.player(buyer), receiverId.toString(), receiver.getName(),
                        price, null, details);
                if (buyer.isOnline()) {
                    msg.send(buyer, gift ? "credit.shop.gifted" : "credit.shop.bought", Msg.p("player", receiver.getName()),
                            Msg.number("price", price));
                    core.sounds().play(buyer, "success");
                }
                if (gift) {
                    msg.send(receiver, "credit.shop.gift-received", Msg.p("player", buyer.getName()));
                    core.sounds().play(receiver, "unlock");
                }
                after.run();
            }, main);
        }, main);
    }

    private void refund(Player buyer, Offer offer, long price, String id, boolean boughtOnly, Runnable after) {
        UUID buyerId = buyer.getUniqueId();
        Transaction back = Transaction.deposit(AccountRef.player(buyerId), Currency.CREDITS, price, TxReason.CREDIT_SHOP_REFUND)
                .actor(Actor.SYSTEM).reference("credit_shop:" + offer.id()).idempotencyKey("r" + id);
        CompletableFuture<TxResult> refund = boughtOnly ? economy.execute(back) : CreditSpend.refund(economy, back, id);
        refund.whenCompleteAsync((r, e) -> {
            busy.remove(buyerId);
            core.database().run(c -> Sql.update(c, "UPDATE credit_shop_purchases SET state = 'refunded' WHERE id = ?", id));
            if (e != null || r == null || !r.ok()) {
                core.plugin().getLogger().severe("Credit shop: REFUND FAILED for " + buyer.getName() + " (" + buyerId + "), purchase " + id
                        + ", " + price + " credits. Give them back with /eco give.");
            }
            refreshCredits(buyerId);
            if (buyer.isOnline()) {
                msg.send(buyer, "credit.shop.refunded", Msg.number("price", price));
                core.sounds().play(buyer, "error");
            }
            after.run();
        }, main);
    }

    private void refreshCredits(UUID uuid) {
        refreshCredits.accept(uuid);
    }

    /** Bought and earned credits of an online player (cached, display only; 0 while not loaded). */
    long[] wallet(Player p) {
        return new long[]{economy.cached(p.getUniqueId(), Currency.CREDITS).orElse(0),
                economy.cached(p.getUniqueId(), Currency.CREDITS_EARNED).orElse(0)};
    }

    // ------------------------------------------------------------------ ranks

    private void registerRanks() {
        List<RankLadder.Step> steps = ladder.steps();
        for (int i = 0; i < steps.size(); i++) {
            int index = i;
            RankLadder.Step step = steps.get(i);
            register(MODULE, Offer.of(RANK + step.group(), Tab.RANKS, i, (viewer, receiver) -> rankIcon(viewer, receiver, index),
                    (viewer, receiver) -> ladder.price(index, ladder.highest(g -> has(receiver, g))), true, true,
                    (buyer, receiver, purchaseId) -> giveRank(buyer, receiver, step, purchaseId)));
        }
    }

    /** LuckPerms gives every player {@code group.<name>} for each group they are in, inherited ones too. */
    static boolean has(Player p, String group) {
        return p.hasPermission("group." + group);
    }

    private ItemStack rankIcon(Player viewer, Player receiver, int index) {
        RankLadder.Step step = ladder.steps().get(index);
        int owned = ladder.highest(g -> has(receiver, g));
        String state = index <= owned ? "owned" : owned >= 0 ? "upgrade" : "new";
        Material icon = switch (index) {
            case 0 -> Material.EGG;
            case 1 -> Material.FEATHER;
            case 2 -> Material.BLAZE_POWDER;
            case 3 -> Material.FIRE_CHARGE;
            case 4 -> Material.SUNFLOWER;
            case 5 -> Material.BLAZE_ROD;
            default -> Material.NETHER_STAR;
        };
        String name = core.ranks().groupDisplayName(step.group());
        ItemStack item = core.menus().item(icon, viewer, MODULE, "credit.shop.rank." + step.group(), Msg.p("rank", name == null ? step.group() : name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(item.lore() == null ? List.of() : item.lore());
        lore.addAll(core.menus().lines(viewer, MODULE, "credit.shop.rank-state." + state,
                Msg.number("full", step.price()), Msg.p("rank", name == null ? step.group() : name)));
        item.lore(lore);
        if (index <= owned) {
            item.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private CompletableFuture<Boolean> giveRank(Player buyer, Player receiver, RankLadder.Step step, String purchaseId) {
        UUID id = receiver.getUniqueId();
        return core.ranks().addGroup(id, step.group()).thenApplyAsync(added -> {
            // the lower ladder groups are parents of this one: drop the direct ones so the player has one donor group
            for (RankLadder.Step s : ladder.steps()) {
                if (!s.group().equals(step.group()) && s.price() < step.price()) {
                    core.ranks().removeGroup(id, s.group());
                }
            }
            String name = core.ranks().groupDisplayName(step.group());
            for (Player online : Bukkit.getOnlinePlayers()) {
                msg.send(online, "credit.shop.rank-bought", Msg.p("player", receiver.getName()), Msg.p("rank", name == null ? step.group() : name),
                        Msg.number("seconds", config.get().creditShopGgSeconds));
            }
            msg.title(receiver, "credit.shop.rank-title", Msg.p("rank", name == null ? step.group() : name));
            openGg(purchaseId);
            return true;
        }, main).exceptionally(error -> {
            core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Credit shop: could not give rank " + step.group(), error);
            return false;
        });
    }

    // ------------------------------------------------------------------ server-wide boosters

    /** Pure: one booster line {@code type:percent:minutes:credits}, or null. */
    static Object[] parseBooster(String line) {
        String[] p = line == null ? new String[0] : line.trim().toLowerCase(Locale.ROOT).split(":");
        if (p.length != 4) {
            return null;
        }
        try {
            BoosterType type = BoosterType.valueOf(p[0].toUpperCase(Locale.ROOT));
            int percent = Integer.parseInt(p[1]);
            int minutes = Integer.parseInt(p[2]);
            long price = Long.parseLong(p[3]);
            return percent > 0 && minutes > 0 && price > 0 ? new Object[]{type, percent, minutes, price} : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void registerBoosters() {
        List<String> lines = config.get().creditShopBoosters;
        for (int i = 0; i < lines.size(); i++) {
            Object[] b = parseBooster(lines.get(i));
            if (b == null) {
                core.plugin().getLogger().warning("economy.yml credit-shop.boosters: '" + lines.get(i)
                        + "' is not <type>:<percent>:<minutes>:<credits> (skipped)");
                continue;
            }
            BoosterType type = (BoosterType) b[0];
            int percent = (int) b[1];
            int minutes = (int) b[2];
            long price = (long) b[3];
            String id = "booster_" + type.name().toLowerCase(Locale.ROOT) + "_" + minutes;
            register(MODULE, Offer.of(id, Tab.BOOSTERS, i, (viewer, receiver) -> core.menus().item(boosterIcon(type), viewer, MODULE,
                            "credit.shop.booster", Msg.p("type", core.menus().text(viewer, MODULE, "credit.shop.booster-type." + type.name().toLowerCase(Locale.ROOT))),
                            Msg.number("percent", percent), Msg.number("minutes", minutes)),
                    (viewer, receiver) -> BoosterService.of(core.services()).filter(s -> s.valid(type, percent, minutes, true)).isPresent() ? price : -1L,
                    false, true, (buyer, receiver, purchaseId) -> {
                        UUID profile = ProfileService.of(core.services()).profileKey(receiver);
                        var item = BoosterService.of(core.services()).flatMap(s -> s.createGlobal(type, percent, minutes, profile, true, "credit_shop"));
                        if (item.isEmpty()) {
                            return CompletableFuture.completedFuture(false);
                        }
                        give(receiver, item.get());
                        return CompletableFuture.completedFuture(true);
                    }));
        }
    }

    private static Material boosterIcon(BoosterType type) {
        return switch (type) {
            case SELL -> Material.GOLD_INGOT;
            case SKILL_XP -> Material.LAPIS_LAZULI;
            case TOOL_XP -> Material.ECHO_SHARD;
            case CRYSTALS -> Material.PRISMARINE_CRYSTALS;
        };
    }

    /** Main thread: into the inventory, the rest to the player's box. */
    void give(Player p, ItemStack item) {
        ItemBox.of(core.services()).ifPresentOrElse(box -> box.giveItems(p, List.of(item), "credit_shop"),
                () -> p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left)));
    }

    // ------------------------------------------------------------------ island rename token

    /** Account thing of an island rename token (the islands module consumes it in /is name). */
    public static final String RENAME_TOKEN = "island_rename";

    private void registerRenameToken() {
        long price = config.get().creditShopRenamePrice;
        if (price <= 0) {
            return;
        }
        register(MODULE, Offer.of("island_rename", Tab.EXTRAS, 60, (viewer, receiver) -> core.menus().item(Material.NAME_TAG, viewer, MODULE,
                        "credit.shop.rename", Msg.number("have", owned(receiver.getUniqueId(), RENAME_TOKEN))),
                (viewer, receiver) -> price, false, true, giveThing(RENAME_TOKEN)));
    }

    // ------------------------------------------------------------------ gg

    private void openGg(String purchaseId) {
        int seconds = config.get().creditShopGgSeconds;
        if (seconds > 0 && config.get().creditShopGgCredits > 0) {
            gg.put(purchaseId, new GgWindow(System.currentTimeMillis() + seconds * 1000L, ConcurrentHashMap.newKeySet()));
        }
    }

    /** Opens a gg window after a credit pack bought on Tebex (called by the store grant). */
    public void storePurchase(Player buyer, String tx) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            msg.send(online, "credit.shop.pack-bought", Msg.p("player", buyer.getName()), Msg.number("seconds", config.get().creditShopGgSeconds));
        }
        openGg("tebex:" + tx);
    }

    /** Pure: is this chat line a gg? ("gg", "GG!", "gg wp"; at most 20 characters). */
    static boolean isGg(String text) {
        if (text == null || text.length() > 20) {
            return false;
        }
        String t = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "");
        return t.equals("gg") || t.startsWith("gg ") || t.equals("ggs") || t.equals("ggwp");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (gg.isEmpty() || !isGg(PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            return;
        }
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        long credits = config.get().creditShopGgCredits;
        gg.values().removeIf(w -> w.until() < now);
        for (Map.Entry<String, GgWindow> e : gg.entrySet()) {
            if (e.getValue().until() >= now && e.getValue().said().add(p.getUniqueId())) {
                economy.execute(Transaction.deposit(AccountRef.player(p.getUniqueId()), Currency.CREDITS_EARNED, credits, TxReason.CREDIT_GG)
                        .actor(Actor.SYSTEM).reference("gg:" + e.getKey()).idempotencyKey("gg:" + e.getKey() + ":" + p.getUniqueId()))
                        .thenAcceptAsync(r -> {
                            if (r.ok() && p.isOnline()) {
                                refreshCredits(p.getUniqueId());
                                msg.actionBar(p, "credit.shop.gg-thanks", Msg.number("credits", credits));
                            }
                        }, main);
            }
        }
    }

    // ------------------------------------------------------------------ chargebacks

    /**
     * A chargeback that leaves credits owed (they were spent): the ranks bought with credits since go, the purchases are
     * marked revoked, and staff are told (and the player is banned if credit-shop rules say so, via the console /ban).
     */
    @EventHandler
    public void onChargeback(StoreChargebackEvent event) {
        if (event.owed() <= 0) {
            return;
        }
        UUID buyer = event.player();
        core.database().transaction(c -> {
            List<Map.Entry<String, UUID>> rows = Sql.query(c, "SELECT id, receiver_uuid, offer_id FROM credit_shop_purchases WHERE buyer_uuid = ? "
                    + "AND state = 'delivered' AND offer_id LIKE 'rank\\_%' ESCAPE '\\'", rs -> Map.entry(rs.getString("offer_id"),
                    Sql.uuid(rs, "receiver_uuid")), buyer);
            Sql.update(c, "UPDATE credit_shop_purchases SET state = 'revoked' WHERE buyer_uuid = ? AND state = 'delivered' AND offer_id LIKE 'rank\\_%' ESCAPE '\\'", buyer);
            return rows;
        }).thenAcceptAsync(rows -> {
            for (Map.Entry<String, UUID> r : rows) {
                core.ranks().removeGroup(r.getValue(), r.getKey().substring(RANK.length()));
            }
            String name = Bukkit.getOfflinePlayer(buyer).getName();
            if (config.get().creditShopChargebackBan && name != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + name + " " + config.get().creditShopChargebackReason);
            }
            core.audit().log(AuditCategory.ECONOMY, "credit_shop_revoke", Actor.system("chargeback"), buyer.toString(), name,
                    event.owed(), null, Map.of("ranks", rows.size(), "tx", event.transaction()));
        }, main);
    }

    /** Display: a credit amount with the symbol. */
    String credits(long amount) {
        return Format.number(amount);
    }
}
