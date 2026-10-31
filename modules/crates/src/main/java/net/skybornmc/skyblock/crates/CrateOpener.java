package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.production.ProductionService;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Runs crate opens on the server: checks, snapshot of what the roll needs (tier, owned cosmetics, live reward systems),
 * the atomic commit ({@link OpenEngine}), then the hand-out of items, cosmetics and fuel and the feedback. One open per
 * player at a time.
 */
final class CrateOpener {

    /** Thrown inside a plain database transaction when the guard refuses (keys or pity changed). */
    private static final class GuardRefused extends RuntimeException {
        GuardRefused() {
            super(null, null, false, false);
        }
    }

    /** What an attempt ended with. */
    private record Outcome(OpenEngine.Status status, OpenPlan.Plan plan, boolean noKeys) {
    }

    private final CratesModule m;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    CrateOpener(CratesModule module) {
        this.m = module;
    }

    // ================================================================== open

    /**
     * Opens {@code requested} crates (capped by keys and {@code open.max-per-click}). {@code done} runs on the main thread
     * with the plan when something was opened (null otherwise), e.g. to show the summary screen or refresh a menu.
     */
    void open(Player player, CrateCatalog.Crate crate, int requested, Consumer<OpenPlan.Plan> done) {
        open(player, crate, requested, done, CrateShow.Source.none());
    }

    /**
     * Same, with where it was started (M10.3): a single open from a menu plays the spinner, at a pedestal the in-world
     * show. The show starts only after the open is committed (keys taken, reward saved and handed out).
     */
    void open(Player player, CrateCatalog.Crate crate, int requested, Consumer<OpenPlan.Plan> done, CrateShow.Source source) {
        Optional<String> problem = m.problem(player);
        if (problem.isEmpty() && crate.hidden()) {
            problem = Optional.of("coming-soon");
        }
        if (problem.isPresent()) {
            m.fail(player, problem.get());
            finish(done, null);
            return;
        }
        if (ItemBox.refuses(m.core.services(), player)) {
            finish(done, null); // the box told the player why
            return;
        }
        UUID uuid = player.getUniqueId();
        if (m.show().animating(uuid) || !busy.add(uuid)) {
            m.fail(player, "busy");
            finish(done, null);
            return;
        }
        UUID profile = m.profiles().profileKey(player);
        OpenPlan.Context context = context(player, crate, profile);
        attempt(uuid, player.getName(), profile, crate, Math.max(1, requested), context, m.settings.retries, Actor.player(player),
                done, source == null ? CrateShow.Source.none() : source);
    }

    private void attempt(UUID player, String name, UUID profile, CrateCatalog.Crate crate, int requested, OpenPlan.Context ctx,
                         int retriesLeft, Actor actor, Consumer<OpenPlan.Plan> done, CrateShow.Source source) {
        int season = m.season();
        m.core.database().async(c -> CrateStore.state(c, profile, crate.id())).thenCompose(state -> {
            CrateMath.KeyUse use = CrateMath.keys(state.keys().bound(), state.keys().tradeable(), requested, m.settings.maxPerClick);
            if (use.total() == 0) {
                return CompletableFuture.completedFuture(new Outcome(OpenEngine.Status.ERROR, null, true));
            }
            int counter = state.pity().map(p -> p.counter(season)).orElse(0);
            OpenPlan.Plan plan = OpenPlan.build(crate, use, counter, ctx, ThreadLocalRandom.current(), UUID::randomUUID);
            return OpenEngine.commit(runner(), plan, profile, player, state.pity(), season, actor, System.currentTimeMillis())
                    .thenApply(status -> new Outcome(status, plan, false));
        }).whenCompleteAsync((out, error) -> {
            if (error == null && out != null && out.status() == OpenEngine.Status.CHANGED && retriesLeft > 0) {
                attempt(player, name, profile, crate, requested, ctx, retriesLeft - 1, actor, done, source);
                return;
            }
            busy.remove(player);
            Player online = Bukkit.getPlayer(player);
            if (error != null || out == null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Crates: open of " + name + " failed", Database.cause(error));
                if (online != null) {
                    m.fail(online, "error");
                }
                finish(done, null);
                return;
            }
            if (out.noKeys()) {
                if (online != null) {
                    m.fail(online, "no-keys", Msg.mm("key", m.keyMini(crate.id())));
                }
                m.changed(profile);
                finish(done, null);
                return;
            }
            switch (out.status()) {
                case OK -> {
                    opened(out.plan(), player, name, profile, actor, online, source);
                    finish(done, out.plan());
                }
                case CHANGED -> {
                    if (online != null) {
                        m.fail(online, "changed");
                    }
                    m.changed(profile);
                    finish(done, null);
                }
                default -> {
                    if (online != null) {
                        m.fail(online, "error");
                    }
                    finish(done, null);
                }
            }
        }, m.main());
    }

    private static void finish(Consumer<OpenPlan.Plan> done, OpenPlan.Plan plan) {
        if (done != null) {
            done.accept(plan);
        }
    }

    /** Runs the transactions and the guard in one database transaction (the economy's batch). */
    private OpenEngine.Runner runner() {
        return (txs, guard) -> {
            Optional<EconomyService> eco = m.core.services().optional(EconomyService.class);
            if (eco.isPresent()) {
                return eco.get().executeBatch(txs, guard);
            }
            if (!txs.isEmpty()) {
                return CompletableFuture.completedFuture(BatchResult.failed(TxResult.Status.ERROR, -1));
            }
            return m.core.database().transaction(c -> {
                if (!guard.holds(c)) {
                    throw new GuardRefused();
                }
                return BatchResult.success(List.of());
            }).exceptionally(e -> Database.cause(e) instanceof GuardRefused ? BatchResult.failed(TxResult.Status.DUPLICATE, -1)
                    : BatchResult.failed(TxResult.Status.ERROR, -1));
        };
    }

    // ================================================================== snapshot (main thread)

    /** Everything the plan needs from other modules, read now on the main thread. */
    OpenPlan.Context context(Player player, CrateCatalog.Crate crate, UUID profile) {
        int tier = m.tier(player);
        Optional<Cosmetics> cosmetics = m.cosmetics();
        Set<String> owned = new HashSet<>();
        for (CrateCatalog.Reward r : crate.rewards()) {
            if (!r.type().cosmetic()) {
                continue;
            }
            Cosmetics.Kind kind = r.type() == RewardType.TITLE ? Cosmetics.Kind.TITLE : Cosmetics.Kind.TAG;
            Set<String> known = cosmetics.map(c -> c.ids(kind)).orElse(Set.of());
            for (String id : r.ids()) {
                // Unknown ids count as owned, so they are never picked.
                if (!known.contains(id) || cosmetics.get().owns(player.getUniqueId(), kind, id)) {
                    owned.add(RollEngine.cosmeticKey(r, id));
                }
            }
        }
        Optional<EnchantService> enchants = m.core.services().optional(EnchantService.class);
        Map<String, List<String>> books = new HashMap<>();
        enchants.ifPresent(e -> {
            for (String id : e.ids()) {
                if (e.enabled(id)) {
                    books.computeIfAbsent(e.rarity(id).id(), k -> new ArrayList<>()).add(id);
                }
            }
        });
        Optional<StackingService> stacking = m.core.services().optional(StackingService.class);
        Map<Integer, List<String>> spawners = new HashMap<>();
        stacking.ifPresent(s -> {
            for (int t = 1; t <= 5; t++) {
                spawners.put(t, s.spawnerTypes(t));
            }
        });
        boolean wardrobe = m.core.services().has(WardrobeService.class);
        boolean fuel = fuelSupported(); // Fuel Cells (PHASE-7 M7.1): kept until used on an island
        Optional<BoosterService> boosters = BoosterService.of(m.core.services());
        return new OpenPlan.Context(tier, m.refPerMinute(), m.settings.crystalGrowth, owned::contains,
                r -> switch (r.type()) {
                    case ENCHANT_BOOK, SCROLL -> enchants.isPresent();
                    case SET_TOKEN -> wardrobe;
                    case SPAWNER -> stacking.isPresent();
                    case ITEM -> validItem(r.material());
                    case TAG, TITLE -> cosmetics.isPresent();
                    case FUEL -> fuel;
                    // M10.2: booster items when the boosters module runs and the numbers are inside its limits.
                    case BOOSTER, GLOBAL_BOOSTER -> boosters.isPresent() && BoosterType.byId(r.booster())
                            .map(t -> boosters.get().valid(t, r.percent(), r.minutes(), r.type() == RewardType.GLOBAL_BOOSTER))
                            .orElse(false);
                    default -> true;
                },
                pool -> books.getOrDefault(pool, List.of()), t -> spawners.getOrDefault(t, List.of()));
    }

    static boolean validItem(String material) {
        Material mat = material == null ? null : Material.matchMaterial(material);
        return mat != null && mat.isItem() && !mat.isAir();
    }

    /**
     * True while Island Fuel exists (PHASE-7 M7.1: the production module publishes {@link ProductionService} only while
     * {@code production.yml → fuel.enabled} is on). Fuel rewards are then bound Fuel Cells; before that they roll as
     * coming-soon (their fallback).
     */
    private boolean fuelSupported() {
        return m.core.services().has(ProductionService.class);
    }

    // ================================================================== after the commit (main thread)

    private void opened(OpenPlan.Plan plan, UUID player, String name, UUID profile, Actor actor, Player online,
                        CrateShow.Source source) {
        audit(plan, player, name, profile, actor);
        m.changed(profile);
        handOut(plan, player, name, profile, actor);
        fireOpened(plan, player, profile, online);
        // M10.3: the show only starts now. The reward is decided, saved and handed out above; the spinner / in-world show
        // is cosmetic, and leaving or closing the menu mid-show changes nothing.
        try {
            m.show().present(online, name, plan, source);
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Crates: the open animation of " + name + " failed (the reward is given)", e);
            if (online != null) {
                m.show().ended(online.getUniqueId());
                feedback(online, plan);
            }
        }
    }

    /** M10.10: other modules (collection log, later quests) hear about the committed batch. Never breaks the open. */
    private void fireOpened(OpenPlan.Plan plan, UUID player, UUID profile, Player online) {
        if (net.skybornmc.skyblock.common.crate.CrateOpenedEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return;
        }
        try {
            List<net.skybornmc.skyblock.common.crate.CrateOpenedEvent.Won> won = new ArrayList<>(plan.grants().size());
            for (OpenPlan.Grant g : plan.grants()) {
                won.add(new net.skybornmc.skyblock.common.crate.CrateOpenedEvent.Won(g.reward().id(), g.top(), g.fallback()));
            }
            Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.crate.CrateOpenedEvent(player,
                    online != null && online.isOnline() ? online : null, profile, plan.crate().id(), won));
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Crates: a listener of the open event failed (the reward is given)", e);
        }
    }

    private void audit(OpenPlan.Plan plan, UUID player, String name, UUID profile, Actor actor) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("crate", plan.crate().id());
        d.put("batch", plan.batchId().toString());
        d.put("profile", profile.toString());
        d.put("bound_keys", plan.keys().bound());
        d.put("tradeable_keys", plan.keys().tradeable());
        d.put("pity_after", plan.pityAfter());
        d.put("top", plan.topCount());
        d.put("money", plan.total(Currency.MONEY));
        d.put("crystals", plan.total(Currency.CRYSTALS));
        d.put("shards", plan.total(Currency.SHARDS));
        d.put("rewards", summaryText(plan));
        m.audit("crate_open", actor, player, name, (long) plan.grants().size(), d);
    }

    /** {@code money x3, book x1 ...} for the audit row. */
    static String summaryText(OpenPlan.Plan plan) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OpenPlan.Grant g : plan.grants()) {
            counts.merge(g.reward().id() + (g.fallback() ? "(fallback)" : ""), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ", ").append(k).append(" x").append(v));
        return OpenEngine.cut(sb.toString(), 512);
    }

    /** Hands out items, cosmetics and fuel of a committed batch (rows {@code pending} → {@code delivered}/{@code failed}). */
    private void handOut(OpenPlan.Plan plan, UUID player, String name, UUID profile, Actor actor) {
        String origin = "crate:" + plan.crate().id();
        for (OpenPlan.Grant g : plan.handouts()) {
            RewardType type = RewardType.byKey(g.kind()).orElse(RewardType.COMING_SOON);
            switch (type) {
                case TAG, TITLE -> grantCosmetic(g.openId(), plan.crate().id(), type, g.ref(), player, name, origin);
                default -> createItems(g, type, origin, player, profile, actor).whenCompleteAsync((items, error) -> {
                    if (error != null || items == null || items.isEmpty()) {
                        failed(g.openId(), plan.crate().id(), g.kind(), g.ref(), player, name, error);
                        return;
                    }
                    if (g.bound()) {
                        BoundItems.markAll(items, profile); // REVIEW3 R3-2: loot of a bound key is bound too
                    }
                    deliver(g.openId(), profile, items, actor, plan.crate().id(), player, name);
                }, m.main());
            }
        }
    }

    private CompletableFuture<List<ItemStack>> createItems(OpenPlan.Grant g, RewardType type, String origin, UUID player, UUID profile,
                                                           Actor actor) {
        int amount = (int) Math.max(1, Math.min(64, g.amount()));
        try {
            return switch (type) {
                case ENCHANT_BOOK -> {
                    EnchantService e = m.core.services().optional(EnchantService.class).orElseThrow();
                    int colon = g.ref().lastIndexOf(':');
                    String id = colon > 0 ? g.ref().substring(0, colon) : g.ref();
                    int level = colon > 0 ? Integer.parseInt(g.ref().substring(colon + 1)) : 1;
                    level = Math.max(1, Math.min(level, Math.max(1, e.maxLevel(id))));
                    List<CompletableFuture<ItemStack>> books = new ArrayList<>();
                    for (int i = 0; i < amount; i++) {
                        books.add(e.createBook(id, level, origin, player));
                    }
                    yield all(books);
                }
                case SCROLL -> {
                    EnchantService e = m.core.services().optional(EnchantService.class).orElseThrow();
                    List<CompletableFuture<ItemStack>> scrolls = new ArrayList<>();
                    for (int i = 0; i < amount; i++) {
                        scrolls.add(e.createScroll(origin, player));
                    }
                    yield all(scrolls);
                }
                case SET_TOKEN -> {
                    WardrobeService w = m.core.services().optional(WardrobeService.class).orElseThrow();
                    List<CompletableFuture<ItemStack>> tokens = new ArrayList<>();
                    for (int i = 0; i < amount; i++) {
                        tokens.add(w.createToken(g.ref(), origin, player, actor));
                    }
                    yield all(tokens);
                }
                case SPAWNER -> {
                    StackingService s = m.core.services().optional(StackingService.class).orElseThrow();
                    yield CompletableFuture.completedFuture(s.createSpawner(g.ref(), amount, origin).map(List::of).orElse(List.of()));
                }
                case ITEM -> {
                    Material mat = Material.matchMaterial(g.ref());
                    if (mat == null || !mat.isItem() || mat.isAir()) {
                        yield CompletableFuture.completedFuture(List.of());
                    }
                    List<ItemStack> stacks = new ArrayList<>();
                    int max = Math.max(1, new ItemStack(mat).getMaxStackSize());
                    for (int left = amount; left > 0; left -= max) {
                        stacks.add(new ItemStack(mat, Math.min(max, left)));
                    }
                    yield CompletableFuture.completedFuture(stacks);
                }
                case BOOSTER, GLOBAL_BOOSTER -> {
                    BoosterService b = BoosterService.of(m.core.services()).orElseThrow();
                    String[] ref = g.ref().split(":");
                    BoosterType bt = BoosterType.byId(ref[0]).orElseThrow();
                    int percent = Integer.parseInt(ref[1]);
                    int minutes = Integer.parseInt(ref[2]);
                    List<ItemStack> boosterItems = new ArrayList<>();
                    for (int i = 0; i < amount; i++) {
                        (type == RewardType.GLOBAL_BOOSTER ? b.createGlobal(bt, percent, minutes, profile, g.bound(), origin)
                                : b.createPersonal(bt, percent, minutes, profile, origin)).ifPresent(boosterItems::add);
                    }
                    yield CompletableFuture.completedFuture(boosterItems);
                }
                case FUEL -> {
                    // PHASE-7 M7.1: fuel minutes become Fuel Cells (1 / 3 / 6 h), always bound to the profile (PD4).
                    ProductionService p = ProductionService.of(m.core.services()).orElseThrow();
                    yield CompletableFuture.completedFuture(p.createFuelCells(profile, g.amount(), origin));
                }
                default -> CompletableFuture.completedFuture(List.of());
            };
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletableFuture<List<ItemStack>> all(List<CompletableFuture<ItemStack>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<ItemStack> out = new ArrayList<>();
            for (CompletableFuture<ItemStack> f : futures) {
                ItemStack s = f.join();
                if (s != null && !s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        });
    }

    /**
     * Items to the player if they are online on that profile (inventory first, the rest to /box; then the player file is
     * saved, SECFIX1), else straight into that profile's box. Then the row becomes {@code delivered}.
     */
    private void deliver(UUID openId, UUID profile, List<ItemStack> items, Actor actor, String crate, UUID player, String name) {
        Player receiver = receiver(profile);
        if (receiver != null) {
            ItemBox.give(m.core.services(), receiver, BoxSource.CRATE, items);
            if (m.settings.savePlayerData) {
                PlayerSaves.save(receiver, "a crate reward");
            }
            mark(openId, CrateStore.DELIVERED);
            return;
        }
        Optional<ItemBox> box = ItemBox.of(m.core.services());
        if (box.isEmpty()) {
            failed(openId, crate, "item", String.valueOf(items.size()), player, name, null);
            return;
        }
        box.get().deliver(profile, items, BoxSource.CRATE, actor).whenComplete((d, e) -> {
            if (e != null) {
                failed(openId, crate, "item", String.valueOf(items.size()), player, name, e);
            } else {
                mark(openId, CrateStore.DELIVERED);
            }
        });
    }

    private void grantCosmetic(UUID openId, String crate, RewardType type, String id, UUID player, String name, String origin) {
        Optional<Cosmetics> cosmetics = m.cosmetics();
        if (cosmetics.isEmpty()) {
            failed(openId, crate, type.key(), id, player, name, null);
            return;
        }
        Cosmetics.Kind kind = type == RewardType.TITLE ? Cosmetics.Kind.TITLE : Cosmetics.Kind.TAG;
        CompletableFuture<Boolean> f;
        try {
            f = cosmetics.get().grant(player, kind, id, origin);
        } catch (RuntimeException e) {
            f = CompletableFuture.failedFuture(e);
        }
        f.whenComplete((isNew, e) -> {
            if (e != null) {
                failed(openId, crate, type.key(), id, player, name, e);
            } else {
                mark(openId, CrateStore.DELIVERED);
            }
        });
    }

    private void mark(UUID openId, String to) {
        m.core.database().async(c -> CrateStore.move(c, openId, CrateStore.PENDING, to, System.currentTimeMillis()))
                .exceptionally(e -> {
                    m.core.plugin().getLogger().log(Level.SEVERE, "Crates: open " + openId + " was handled but could not be marked "
                            + to + "; check it in /logs (crates) before giving anything by hand", Database.cause(e));
                    return false;
                });
    }

    /** A reward could not be handed out: the row becomes {@code failed}, staff see it in /logs, the player is told. */
    private void failed(UUID openId, String crate, String kind, String ref, UUID player, String name, Throwable error) {
        if (error != null) {
            m.core.plugin().getLogger().log(Level.WARNING, "Crates: could not hand out reward " + kind + " (" + ref + ") of open "
                    + openId + " to " + name, Database.cause(error));
        }
        mark(openId, CrateStore.FAILED);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("open", openId.toString());
        d.put("crate", crate);
        d.put("kind", kind);
        d.put("ref", ref);
        m.audit("crate_grant_failed", Actor.system(CratesModule.ID), player, name, null, d);
        m.main().execute(() -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                m.fail(online, "open.grant-failed");
            }
        });
    }

    /** The online player whose active profile is {@code profile}, not switching and not in staff mode; else null. */
    private Player receiver(UUID profile) {
        Player p = m.profiles().onlinePlayer(profile).orElse(null);
        if (p == null || !p.isOnline() || m.profiles().isSwitching(p.getUniqueId()) || m.flag(p, "staff_mode")) {
            return null;
        }
        return p;
    }

    // ================================================================== feedback

    /** The result message and sound of an open (at the reveal of its show). */
    void feedback(Player player, OpenPlan.Plan plan) {
        boolean top = plan.topCount() > 0;
        m.core.sounds().play(player, top ? m.settings.topSound : m.settings.openSound);
        if (plan.grants().size() == 1) {
            OpenPlan.Grant g = plan.grants().getFirst();
            m.msg.send(player, g.top() ? "open.single-top" : "open.single", Msg.mm("crate", m.crateMini(plan.crate())),
                    Msg.c("reward", grantName(player, plan.crate(), g)));
            if (g.fallback()) {
                m.msg.send(player, "open.fallback-note", Msg.p("amount", m.format(Currency.CRYSTALS, g.amount())));
            }
            if (g.forced()) {
                m.msg.send(player, "open.pity-fired");
            }
            return;
        }
        m.msg.send(player, "open.summary-header", Msg.number("count", plan.grants().size()), Msg.mm("crate", m.crateMini(plan.crate())));
        List<Line> lines = lines(plan);
        int shown = 0;
        for (Line line : lines) {
            if (shown++ >= m.settings.summaryLines) {
                break;
            }
            m.msg.send(player, line.top() ? "open.summary-line-top" : "open.summary-line",
                    Msg.c("reward", line.name(player, this, plan.crate())), Msg.number("times", line.times()));
        }
        if (lines.size() > m.settings.summaryLines) {
            m.msg.send(player, "open.summary-more", Msg.number("more", lines.size() - m.settings.summaryLines));
        }
        if (plan.grants().stream().anyMatch(OpenPlan.Grant::forced)) {
            m.msg.send(player, "open.pity-fired");
        }
    }

    /** Equal rewards of a batch merged into one line (same reward, kind and ref; amounts added up). */
    record Line(OpenPlan.Grant first, int times, long amount, boolean top) {

        Component name(Player viewer, CrateOpener opener, CrateCatalog.Crate crate) {
            OpenPlan.Grant merged = new OpenPlan.Grant(first.openId(), first.reward(), first.kind(), first.ref(), amount, first.bound(),
                    top, first.forced());
            return opener.grantName(viewer, crate, merged);
        }
    }

    static List<Line> lines(OpenPlan.Plan plan) {
        Map<String, Line> merged = new LinkedHashMap<>();
        for (OpenPlan.Grant g : plan.grants()) {
            String key = g.reward().id() + "|" + g.kind() + "|" + g.ref();
            merged.merge(key, new Line(g, 1, g.amount(), g.top()),
                    (a, b) -> new Line(a.first(), a.times() + 1, a.amount() + b.amount(), a.top() || b.top()));
        }
        List<Line> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> Boolean.compare(b.top(), a.top()));
        return out;
    }

    /** What a grant gave, as shown to the player (cosmetics with their own look, fallbacks as crystals). */
    Component grantName(Player viewer, CrateCatalog.Crate crate, OpenPlan.Grant g) {
        if (g.fallback()) {
            return m.msg.component(viewer, "open.fallback-name", Msg.mm("reward", m.rewardMini(crate, g.reward(), "")),
                    Msg.p("amount", m.format(Currency.CRYSTALS, g.amount())));
        }
        RewardType type = RewardType.byKey(g.kind()).orElse(RewardType.COMING_SOON);
        if (type.cosmetic()) {
            Cosmetics.Kind kind = type == RewardType.TITLE ? Cosmetics.Kind.TITLE : Cosmetics.Kind.TAG;
            Component look = m.cosmetics().map(c -> c.display(viewer, kind, g.ref())).orElse(Component.text(g.ref()));
            return m.msg.component(viewer, type == RewardType.TITLE ? "open.won-title" : "open.won-tag", Msg.c("name", look));
        }
        String amount = switch (type) {
            case MONEY -> m.format(Currency.MONEY, g.amount());
            case CRYSTALS -> m.format(Currency.CRYSTALS, g.amount());
            case SHARDS -> m.format(Currency.SHARDS, g.amount());
            case FUEL -> CratesModule.durationText((int) g.amount());
            default -> String.valueOf(g.amount());
        };
        Component name = m.mini(viewer, m.rewardMini(crate, g.reward(), amount));
        if (type == RewardType.KEY || type == RewardType.ENCHANT_BOOK || type == RewardType.SCROLL || type == RewardType.SET_TOKEN
                || type == RewardType.SPAWNER || type == RewardType.ITEM || type == RewardType.BOOSTER
                || type == RewardType.GLOBAL_BOOSTER) {
            return m.msg.component(viewer, "open.with-count", Msg.c("reward", name), Msg.number("count", g.amount()));
        }
        return name;
    }

    // ================================================================== startup recovery

    /** Cosmetics a crash left pending are granted again (idempotent); items/fuel moved to review are reported to staff. */
    void recovered(List<CrateStore.OpenRow> again, List<CrateStore.OpenRow> review) {
        for (CrateStore.OpenRow r : again) {
            RewardType type = RewardType.byKey(r.kind()).orElse(RewardType.TAG);
            grantCosmetic(r.openId(), r.crate(), type, r.ref(), r.player(), null, "crate:" + r.crate());
        }
        if (!review.isEmpty()) {
            m.core.plugin().getLogger().warning("Crates: " + review.size() + " reward(s) were rolled while the server stopped mid "
                    + "hand-out; they are marked 'review' (crate_review in /logs, category crates). Check the player's inventory "
                    + "and /box, then give them by hand if they are missing.");
        }
        for (CrateStore.OpenRow r : review) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("open", r.openId().toString());
            d.put("crate", r.crate());
            d.put("reward", r.rewardId());
            d.put("kind", r.kind());
            d.put("ref", r.ref());
            d.put("amount", r.amount());
            d.put("bound", r.boundKey());
            d.put("profile", r.profile().toString());
            m.audit("crate_review", Actor.system(CratesModule.ID), r.player(), null, r.amount(), d);
        }
    }

    /** True while an open of this player runs. */
    boolean busy(UUID player) {
        return busy.contains(player);
    }

    /** Opens the summary screen of a bulk open ({@code from} = where Back goes). */
    void showResult(Player player, OpenPlan.Plan plan, Menu from) {
        ResultMenu result = new ResultMenu(m, plan);
        if (from != null) {
            from.openChild(result);
        } else {
            m.core.menus().open(player, result);
        }
    }
}
