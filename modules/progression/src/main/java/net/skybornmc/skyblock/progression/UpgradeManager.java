package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BankService;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.island.IslandUpgradeEffects;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.TaskCompletedEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Island upgrades (PHASE-5 M5.4): the catalog ({@code upgrades.yml}), the bought levels ({@code island_upgrades}, all
 * islands of the season in memory), buying and the effects.
 *
 * <ul>
 *   <li><b>Gating:</b> a level needs every challenge book task of its key {@code upgrade.<id>.<level>}
 *       ({@code TaskService#unlocked}) and its optional island level; buying needs the island permission "upgrades"
 *       (owner 170: leader always, admin by default).</li>
 *   <li><b>Payment:</b> from the island bank only (owner 169), one economy transaction (ledger reason
 *       {@code island_upgrade}, no fee, interest untouched) with the level row as its guard: both commit or neither; the
 *       idempotency key per island, season, upgrade and level stops a second payment. A second click while one is
 *       running is refused in memory too.</li>
 *   <li><b>Effects:</b> size, member slots and warp slots are raised through the islands module's setters right after the
 *       purchase and again after a restart (on load and when a member joins; never lowered). The others are live
 *       hooks: {@link IslandUpgradeEffects} (spawner stack and rate, entity limits, bank capacity and interest levels)
 *       and the BonusService source {@code upgrades} (sell, Depths drops, skill XP for members). Island level points:
 *       {@link LevelSource#UPGRADES} (owner P5-17).</li>
 * </ul>
 * Main thread unless noted; placeholders may read from other threads (concurrent map of immutable maps).
 */
final class UpgradeManager implements Listener {

    static final String PERM_UPGRADE = "skyblock.progression.upgrade";
    static final String BONUS_SOURCE = "upgrades";
    static final String AUDIT_BUY = "upgrade_buy";

    /** What a purchase did. */
    enum Outcome { BOUGHT, NO_MONEY, ALREADY, FAILED }

    private final Core core;
    private final UpgradesConfig config;
    private final ModuleMessages msg;
    private final LevelManager levels;
    private final TaskManager tasks;
    private final int season;
    private final UpgradeStore store = new UpgradeStore();
    private final Map<UUID, Map<String, Integer>> bought = new ConcurrentHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final Set<String> raiseWarned = new HashSet<>();
    private final Map<UUID, Integer> readyCache = new ConcurrentHashMap<>();
    /** PERF6: last island bank balance read, so an upgrade menu opens with it instead of "..." (refreshed while open). */
    private final Map<UUID, Long> knownBank = new ConcurrentHashMap<>();
    private volatile UpgradeCatalog catalog = UpgradeCatalog.empty();
    /** M5.5: the masteries' island level points, added to this source (LevelSource.UPGRADES = upgrades + masteries). */
    private volatile java.util.function.ToDoubleFunction<UUID> extraPoints;
    private final IslandUpgradeEffects effects = new Effects();
    private UpgradeMenus menus;
    private boolean ready;
    private boolean closed;
    private boolean hooked;

    UpgradeManager(Core core, UpgradesConfig config, ModuleMessages msg, LevelManager levels, TaskManager tasks, int season) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.levels = levels;
        this.tasks = tasks;
        this.season = season;
    }

    void menus(UpgradeMenus menus) {
        this.menus = menus;
    }

    Core core() {
        return core;
    }

    ModuleMessages msg() {
        return msg;
    }

    UpgradesConfig config() {
        return config;
    }

    UpgradeCatalog catalog() {
        return catalog;
    }

    TaskManager tasks() {
        return tasks;
    }

    LevelManager levels() {
        return levels;
    }

    boolean ready() {
        return ready;
    }

    /** True when {@code /is upgrade} can open: switched on, loaded and at least one upgrade in the catalog. */
    boolean available() {
        return config.enabled && ready && catalog.size() > 0;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        catalog = loadCatalog();
        load();
    }

    /** Reload: settings were reloaded by the module; the catalog is read again. Bought levels stay. */
    void applyConfig() {
        catalog = loadCatalog();
        if (ready) {
            for (UUID island : bought.keySet()) {
                reconcile(island);
                levels.request(island);
            }
        }
    }

    private void load() {
        core.database().async(c -> store.load(c, season)).whenComplete((loaded, error) -> core.scheduler().main("progression-upgrade-load", () -> {
            if (closed) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: loading the island upgrades failed; retrying in 30 s",
                        Database.cause(error));
                core.scheduler().later("progression-upgrade-load-retry", 30 * 20L, this::load);
                return;
            }
            loaded.forEach((island, levelsById) -> bought.merge(island, Map.copyOf(levelsById), UpgradeManager::higher));
            ready = true;
            hook();
            for (UUID island : bought.keySet()) {
                reconcile(island);
                levels.request(island);
            }
            core.plugin().getLogger().info("Progression: loaded the upgrades of " + loaded.size() + " islands ("
                    + catalog.size() + " upgrades, season " + season + ").");
        }));
    }

    /** Effects, bonus source and level points, once the bought levels are known (before that every hook reads "none"). */
    private void hook() {
        islands().ifPresent(s -> s.upgradeEffects(effects));
        core.services().optional(BonusService.class).ifPresent(b -> b.register(BONUS_SOURCE, this::bonus));
        levels.provider(LevelSource.UPGRADES, this::levelPoints);
        hooked = true;
    }

    void shutdown() {
        closed = true;
        if (hooked) {
            islands().ifPresent(s -> s.upgradeEffects(null));
            core.services().optional(BonusService.class).ifPresent(b -> b.unregister(BONUS_SOURCE));
            levels.provider(LevelSource.UPGRADES, null);
            hooked = false;
        }
    }

    static Map<String, Integer> higher(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new HashMap<>(a);
        b.forEach((k, v) -> out.merge(k, v, Math::max));
        return Map.copyOf(out);
    }

    // ------------------------------------------------------------------ upgrades.yml

    /**
     * Reads {@code upgrades.yml} from the data folder. A missing file is written from the bundled one; upgrades that are new
     * in the bundled file are added to an existing file (turn one off with {@code enabled: false} instead of deleting it).
     */
    private UpgradeCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), UpgradeCatalog.FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning(UpgradeCatalog.FILE + " is not valid YAML, using the bundled upgrades. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return UpgradeCatalog.load(bundled, UpgradeManager::isItem, w -> core.plugin().getLogger().warning(w));
        }
        boolean changed = false;
        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(UpgradeCatalog.FILE)) {
                file.getParentFile().mkdirs();
                if (in != null) {
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + UpgradeCatalog.FILE, e);
            }
            yaml = bundled;
        } else if (bundled.getConfigurationSection(UpgradeCatalog.SECTION) != null) {
            for (String id : bundled.getConfigurationSection(UpgradeCatalog.SECTION).getKeys(false)) {
                String path = UpgradeCatalog.SECTION + "." + id;
                if (!yaml.contains(path)) {
                    yaml.set(path, bundled.get(path));
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + UpgradeCatalog.FILE, e);
            }
        }
        UpgradeCatalog loaded = UpgradeCatalog.load(yaml, UpgradeManager::isItem, w -> core.plugin().getLogger().warning(w));
        core.plugin().getLogger().info("Progression: " + loaded.size() + " island upgrade(s).");
        return loaded;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(UpgradeCatalog.FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, UpgradeCatalog.FILE + " in the jar is broken", e);
        }
        return yaml;
    }

    static boolean isItem(String id) {
        Material m = id == null ? null : Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir();
    }

    // ------------------------------------------------------------------ reads

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** Bought levels of the island (never null, immutable). Any thread. */
    Map<String, Integer> of(UUID islandId) {
        Map<String, Integer> m = islandId == null ? null : bought.get(islandId);
        return m == null ? Map.of() : m;
    }

    int level(UUID islandId, String upgradeId) {
        return of(islandId).getOrDefault(upgradeId, 0);
    }

    /** Total bought levels of the island (placeholder {@code island_upgrades_bought}). */
    int totalLevels(UUID islandId) {
        int sum = 0;
        for (int v : of(islandId).values()) {
            sum += v;
        }
        return sum;
    }

    double points(UUID islandId) {
        return UpgradeRules.points(catalog, of(islandId), config.points());
    }

    /** Island level points of {@link LevelSource#UPGRADES}: upgrades plus the masteries' part (M5.5). Any thread. */
    double levelPoints(UUID islandId) {
        java.util.function.ToDoubleFunction<UUID> extra = extraPoints;
        return points(islandId) + (extra == null ? 0 : Math.max(0, extra.applyAsDouble(islandId)));
    }

    /** M5.5: sets (or with null removes) the masteries' level points; every known island is recalculated. */
    void extraPoints(java.util.function.ToDoubleFunction<UUID> provider) {
        this.extraPoints = provider;
        if (ready && !closed) {
            bought.keySet().forEach(levels::request);
        }
    }

    /** The island bank balance changed (a mastery was paid): menus read it again. */
    void forgetBank(UUID islandId) {
        knownBank.remove(islandId);
    }

    boolean tasksDone(UUID islandId, UpgradeCatalog.Def def) {
        return UpgradeRules.tasksDone(def, level(islandId, def.id()), tasks.ready(), key -> tasks.unlocked(islandId, key));
    }

    /** True when {@code player} may spend the island bank on upgrades (island permission "upgrades"). */
    boolean canBuy(Player player, UUID islandId) {
        return islands().map(s -> s.hasIslandPermission(islandId, player.getUniqueId(), IslandService.PERM_UPGRADES)).orElse(false);
    }

    /** The next level's state for {@code viewer}; {@code bank} &lt; 0 = balance not known. */
    UpgradeRules.Status status(Player viewer, UUID islandId, UpgradeCatalog.Def def, long bank) {
        int current = level(islandId, def.id());
        return UpgradeRules.status(def, current, tasksDone(islandId, def), levels.level(islandId), canBuy(viewer, islandId), bank);
    }

    /** Upgrades whose next level has its tasks and island level met (placeholder {@code island_upgrades_ready}). */
    int readyCount(UUID islandId) {
        if (!ready || islandId == null) {
            return 0;
        }
        int n = 0;
        int islandLevel = levels.level(islandId);
        for (UpgradeCatalog.Def def : catalog.all()) {
            if (UpgradeRules.status(def, level(islandId, def.id()), tasksDone(islandId, def), islandLevel, true, -1).unlocked()) {
                n++;
            }
        }
        return n;
    }

    /** The island bank balance (async, authoritative), or -1 when the economy is missing or the read failed. */
    CompletableFuture<Long> bankBalance(UUID islandId) {
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        if (economy == null) {
            return CompletableFuture.completedFuture(-1L);
        }
        return economy.balance(AccountRef.island(islandId), Currency.MONEY).exceptionally(e -> -1L).thenApply(v -> {
            if (v != null && v >= 0) {
                knownBank.put(islandId, v);
            } else {
                knownBank.remove(islandId);
            }
            return v;
        });
    }

    /**
     * The island bank balance last read by {@link #bankBalance} (any thread), or -1 when unknown. Only for display: a
     * purchase always pays through the economy, which checks the real balance.
     */
    long knownBank(UUID islandId) {
        return islandId == null ? -1 : knownBank.getOrDefault(islandId, -1L);
    }

    /**
     * Everything except the catalog and the messages that the effect text of {@code def} at {@code level} depends on
     * (PERF6 menu item signatures): the bank amounts of bank.yml for the bank upgrades, the island's stored value at
     * level 0, else nothing (the value comes from the catalog).
     */
    Object effectInputs(UpgradeCatalog.Def def, int level, UUID islandId) {
        UpgradeEffect effect = def.effect();
        if (effect == UpgradeEffect.BANK_CAPACITY || effect == UpgradeEffect.BANK_INTEREST) {
            int bankLevel = UpgradeRules.bankLevel(def, level);
            BankService bank = core.services().optional(BankService.class).orElse(null);
            boolean capacity = effect == UpgradeEffect.BANK_CAPACITY;
            return bank == null ? -1L : capacity ? bank.islandCapacity(bankLevel) : bank.islandPayoutCap(bankLevel);
        }
        return level <= 0 ? storedNow(effect, islandId) : 0;
    }

    // ------------------------------------------------------------------ effect texts

    /** The effect at {@code level} as text, e.g. "7x7 border"; level 0 shows the island's value now where it has one. */
    Component effectText(Player viewer, UpgradeCatalog.Def def, int level, UUID islandId) {
        UpgradeEffect effect = def.effect();
        if (effect == UpgradeEffect.BANK_CAPACITY || effect == UpgradeEffect.BANK_INTEREST) {
            return bankEffectText(viewer, def, level);
        }
        double value;
        if (level <= 0) {
            OptionalInt now = storedNow(effect, islandId);
            if (now.isEmpty()) {
                return msg.component(viewer, "upgrades.effect-none");
            }
            value = now.getAsInt();
        } else {
            value = def.valueAt(level);
        }
        return msg.component(viewer, "upgrades.effect." + effect.id(), Msg.p("value", UpgradeCatalog.trim(value)),
                Msg.p("members", UpgradeCatalog.trim(Math.max(0, value - 1))));
    }

    /**
     * Bank Capacity / Bank Interest: the real amounts of bank.yml (capacity, interest cap per payout) through
     * {@link BankService}, the single source of the numbers (UX3). Level 0 shows the island bank's base amounts.
     * Falls back to "level N" while the bank is off.
     */
    private Component bankEffectText(Player viewer, UpgradeCatalog.Def def, int level) {
        int bankLevel = UpgradeRules.bankLevel(def, level);
        BankService bank = core.services().optional(BankService.class).orElse(null);
        boolean capacity = def.effect() == UpgradeEffect.BANK_CAPACITY;
        long amount = bank == null ? -1 : capacity ? bank.islandCapacity(bankLevel) : bank.islandPayoutCap(bankLevel);
        String key = "upgrades.effect." + def.effect().id();
        if (amount < 0) {
            return msg.component(viewer, key + "-level", Msg.p("value", bankLevel));
        }
        return msg.component(viewer, key, Msg.p("amount", Format.money(amount)), Msg.p("hours", Math.max(1, bank.interestPeriodHours())),
                Msg.p("value", bankLevel));
    }

    /** The islands module's value now of a stored effect (size, member slots, warp slots). */
    private OptionalInt storedNow(UpgradeEffect effect, UUID islandId) {
        IslandService s = islands().orElse(null);
        if (s == null || islandId == null) {
            return OptionalInt.empty();
        }
        return switch (effect) {
            case ISLAND_SIZE -> s.island(islandId).map(i -> OptionalInt.of(i.sizeChunks())).orElse(OptionalInt.empty());
            case MEMBER_SLOTS -> OptionalInt.of(s.memberSlots(islandId));
            case WARP_SLOTS -> OptionalInt.of(s.warpSlots(islandId));
            default -> OptionalInt.empty();
        };
    }

    Component upgradeName(Player viewer, UpgradeCatalog.Def def) {
        String key = "upgrades.name." + def.id();
        String raw = core.messages().raw(ProgressionModule.ID, key);
        if (raw == null) {
            return Component.text(TaskRules.pretty(def.id()));
        }
        return msg.component(viewer, key);
    }

    // ------------------------------------------------------------------ buying

    /**
     * Buys level {@code level} of an upgrade for {@code player}'s island, paid from the island bank. Every rule is checked
     * again here (the menu may be old). {@code done} runs on the main thread with the outcome (null = refused before any
     * payment, the player was told why).
     */
    void buy(Player player, String upgradeId, int level, java.util.function.Consumer<Outcome> done) {
        UpgradeCatalog.Def def = catalog.get(upgradeId).orElse(null);
        IslandService islands = islands().orElse(null);
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        IslandService.IslandInfo island = islands == null ? null : islands.islandOf(player.getUniqueId()).orElse(null);
        if (!available() || def == null || islands == null) {
            msg.send(player, "upgrades.unavailable");
            finish(done, null);
            return;
        }
        if (island == null) {
            msg.send(player, "upgrades.no-island");
            finish(done, null);
            return;
        }
        UUID islandId = island.id();
        int current = level(islandId, def.id());
        if (current >= level) {
            msg.send(player, "upgrades.buy.already", Msg.c("upgrade", upgradeName(player, def)), Msg.p("level", UpgradeRules.roman(level)));
            finish(done, null);
            return;
        }
        UpgradeRules.Status status = status(player, islandId, def, -1);
        if (current != level - 1 || status != UpgradeRules.Status.READY) {
            refuse(player, def, current, status);
            finish(done, null);
            return;
        }
        String flight = islandId + ":" + def.id();
        if (!inFlight.add(flight)) {
            msg.send(player, "upgrades.buy.busy");
            finish(done, null);
            return;
        }
        UpgradeCatalog.Level next = def.level(level);
        long cost = next.cost();
        long now = System.currentTimeMillis();
        TxGuard guard = c -> store.buy(c, islandId, season, def.id(), level, profile, now);
        CompletableFuture<TxResult> result;
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        if (cost > 0) {
            if (economy == null) {
                inFlight.remove(flight);
                msg.send(player, "upgrades.unavailable");
                finish(done, null);
                return;
            }
            Transaction tx = Transaction.withdraw(AccountRef.island(islandId), Currency.MONEY, cost, UpgradeRules.LEDGER_REASON)
                    .actor(Actor.player(player))
                    .reference(UpgradeRules.reference(def.id(), level))
                    .idempotencyKey(UpgradeRules.idempotencyKey(season, islandId, def.id(), level))
                    .detail("upgrade", def.id())
                    .detail("level", level)
                    .detail("island", islandId.toString())
                    .detail("target_name", island.name());
            result = economy.executeGuarded(tx, guard);
        } else {
            result = core.database().transaction(guard::holds)
                    .thenApply(ok -> TxResult.of(ok ? TxResult.Status.SUCCESS : TxResult.Status.DUPLICATE))
                    .exceptionally(e -> TxResult.of(TxResult.Status.ERROR));
        }
        result.whenComplete((r, error) -> core.scheduler().main("progression-upgrade-buy", () -> {
            inFlight.remove(flight);
            knownBank.remove(islandId); // the balance changed (or may have): menus read it again
            TxResult.Status s = error != null || r == null ? TxResult.Status.ERROR : r.status();
            Outcome outcome = switch (s) {
                case SUCCESS -> Outcome.BOUGHT;
                case INSUFFICIENT_FUNDS -> Outcome.NO_MONEY;
                case DUPLICATE -> Outcome.ALREADY;
                default -> Outcome.FAILED;
            };
            switch (outcome) {
                case BOUGHT -> bought(player, island, def, level, cost, r == null ? null : r.fromBalance());
                case NO_MONEY -> {
                    msg.send(player, "upgrades.buy.no-money", Msg.p("cost", Format.money(cost)));
                    core.sounds().play(player, "error");
                }
                case ALREADY -> {
                    msg.send(player, "upgrades.buy.changed");
                    refreshFromDatabase(islandId, def.id());
                }
                case FAILED -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Progression: buying upgrade " + def.id() + " " + level
                                + " for island " + islandId + " failed", Database.cause(error));
                    }
                    msg.send(player, "upgrades.buy.failed");
                    core.sounds().play(player, "error");
                }
            }
            finish(done, outcome);
        }));
    }

    private static void finish(java.util.function.Consumer<Outcome> done, Outcome outcome) {
        if (done != null) {
            done.accept(outcome);
        }
    }

    /** Tells the player why the next level can't be bought right now. */
    void refuse(Player player, UpgradeCatalog.Def def, int current, UpgradeRules.Status status) {
        Component name = upgradeName(player, def);
        UpgradeCatalog.Level next = def.level(current + 1);
        switch (status) {
            case DISABLED, COMING_SOON -> msg.send(player, "upgrades.buy.coming-soon", Msg.c("upgrade", name));
            case MAXED -> msg.send(player, "upgrades.buy.maxed", Msg.c("upgrade", name));
            case TASKS_OPEN -> msg.send(player, "upgrades.buy.tasks", Msg.c("upgrade", name));
            case ISLAND_LEVEL -> msg.send(player, "upgrades.buy.island-level", Msg.c("upgrade", name),
                    Msg.p("level", next == null ? 0 : next.minIslandLevel()));
            case NO_PERMISSION -> msg.send(player, "upgrades.buy.no-permission");
            case NO_MONEY -> msg.send(player, "upgrades.buy.no-money", Msg.p("cost", Format.money(next == null ? 0 : next.cost())));
            case READY -> msg.send(player, "upgrades.buy.changed");
        }
        core.sounds().play(player, "error");
    }

    /** After a refused (duplicate) purchase: the stored level wins, e.g. another member bought it a moment earlier. */
    private void refreshFromDatabase(UUID islandId, String upgradeId) {
        core.database().async(c -> store.level(c, islandId, season, upgradeId)).whenComplete((level, error) ->
                core.scheduler().main("progression-upgrade-refresh", () -> {
                    if (error == null && level != null && level > 0) {
                        setLevel(islandId, upgradeId, level);
                        reconcile(islandId);
                        levels.request(islandId);
                    }
                }));
    }

    private void setLevel(UUID islandId, String upgradeId, int level) {
        bought.merge(islandId, Map.of(upgradeId, level), UpgradeManager::higher);
    }

    /** A purchase was saved: memory, effects, island level, audit and notices. */
    private void bought(Player buyer, IslandService.IslandInfo island, UpgradeCatalog.Def def, int level, long cost, Long bankAfter) {
        UUID islandId = island.id();
        setLevel(islandId, def.id(), level);
        reconcile(islandId);
        levels.request(islandId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("upgrade", def.id());
        details.put("level", level);
        details.put("cost", cost);
        details.put("buyer", buyer.getName());
        details.put("buyer_profile", ProfileService.of(core.services()).profileKey(buyer).toString());
        if (bankAfter != null) {
            details.put("bank_after", bankAfter);
        }
        details.put("season", season);
        core.audit().log(AuditCategory.ISLAND, AUDIT_BUY, Actor.player(buyer), islandId.toString(), island.name(), cost, null, details);
        msg.send(buyer, "upgrades.buy.done", Msg.c("upgrade", upgradeName(buyer, def)), Msg.p("level", UpgradeRules.roman(level)),
                Msg.p("cost", Format.money(cost)), Msg.p("bank", bankAfter == null ? "?" : Format.money(bankAfter)));
        for (Player p : onlineMembers(islandId)) {
            TagResolverSet t = new TagResolverSet(upgradeName(p, def), UpgradeRules.roman(level), effectText(p, def, level, islandId), buyer.getName());
            if (config.notifyChat && !p.equals(buyer)) {
                msg.send(p, "upgrades.bought.chat", t.resolvers());
            }
            if (config.notifyActionBar) {
                msg.actionBar(p, "upgrades.bought.action-bar", t.resolvers());
            }
            core.sounds().play(p, config.buySound);
        }
    }

    /** The placeholders of a purchase notice. */
    private record TagResolverSet(Component upgrade, String level, Component effect, String player) {
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] resolvers() {
            return new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.c("upgrade", upgrade), Msg.p("level", level),
                    Msg.c("effect", effect), Msg.p("player", player)};
        }
    }

    List<Player> onlineMembers(UUID islandId) {
        IslandService islands = islands().orElse(null);
        if (islands == null) {
            return List.of();
        }
        ProfileService profiles = ProfileService.of(core.services());
        List<Player> out = new ArrayList<>();
        for (UUID key : islands.members(islandId).keySet()) {
            profiles.onlinePlayer(key).ifPresent(out::add);
        }
        return out;
    }

    // ------------------------------------------------------------------ stored effects (raised, never lowered)

    /** Raises the island's size, member slots and warp slots to what its bought levels give (never lowers anything). */
    void reconcile(UUID islandId) {
        IslandService s = islands().orElse(null);
        Map<String, Integer> b = of(islandId);
        if (s == null || b.isEmpty()) {
            return;
        }
        IslandService.IslandInfo info = s.island(islandId).orElse(null);
        if (info == null) {
            return;
        }
        Actor actor = Actor.system(ProgressionModule.ID);
        UpgradeCatalog cat = catalog;
        int size = UpgradeRules.raiseTo(cat, b, UpgradeEffect.ISLAND_SIZE, info.sizeChunks());
        if (size > 0) {
            watch(islandId, "size", s.setSize(islandId, size, actor));
        }
        int slots = UpgradeRules.raiseTo(cat, b, UpgradeEffect.MEMBER_SLOTS, s.memberSlots(islandId));
        if (slots > 0) {
            watch(islandId, "member slots", s.setMemberSlots(islandId, slots, actor));
        }
        int warps = UpgradeRules.raiseTo(cat, b, UpgradeEffect.WARP_SLOTS, s.warpSlots(islandId));
        if (warps > 0) {
            watch(islandId, "warp slots", s.setWarpSlots(islandId, warps, actor));
        }
    }

    private void watch(UUID islandId, String what, CompletableFuture<Boolean> future) {
        future.whenComplete((ok, error) -> {
            if ((error != null || !Boolean.TRUE.equals(ok)) && raiseWarned.add(islandId + what)) {
                core.plugin().getLogger().warning("Progression: raising the " + what + " of island " + islandId
                        + " for its upgrades failed (above the islands.yml cap?); tried again on the next start or join.");
            }
        });
    }

    // ------------------------------------------------------------------ hooks

    /** BonusService source {@code upgrades}: sell, Depths drops and skill XP of the island's members (never coop/visitors). */
    double bonus(BonusFamily family, BonusContext context) {
        Player player = context == null ? null : context.player();
        if (player == null || !ready) {
            return 0;
        }
        UpgradeEffect effect = switch (family) {
            case SELL -> UpgradeEffect.SELL_BONUS;
            case SKILL_XP -> UpgradeEffect.XP_BOOST;
            case EXTRA_DROPS -> "mining".equals(context.activity()) ? UpgradeEffect.MINE_FORTUNE
                    : "foraging".equals(context.activity()) ? UpgradeEffect.LUMBERJACK : null;
            default -> null;
        };
        if (effect == null) {
            return 0;
        }
        UUID island = islands().flatMap(s -> s.islandOf(player.getUniqueId())).map(IslandService.IslandInfo::id).orElse(null);
        return island == null ? 0 : UpgradeRules.bonusPercent(catalog, of(island), effect);
    }

    private final class Effects implements IslandUpgradeEffects {

        @Override
        public OptionalInt spawnerStackLimit(UUID islandId) {
            return UpgradeRules.spawnerStack(catalog, of(islandId));
        }

        @Override
        public double spawnerRateMultiplier(UUID islandId) {
            return UpgradeRules.spawnerRate(catalog, of(islandId));
        }

        @Override
        public int entityLimitBonusPercent(UUID islandId) {
            return UpgradeRules.whole(catalog, of(islandId), UpgradeEffect.ENTITY_LIMITS);
        }

        @Override
        public int bankCapacityLevel(UUID islandId) {
            return UpgradeRules.whole(catalog, of(islandId), UpgradeEffect.BANK_CAPACITY);
        }

        @Override
        public int bankInterestLevel(UUID islandId) {
            return UpgradeRules.whole(catalog, of(islandId), UpgradeEffect.BANK_INTEREST);
        }
    }

    // ------------------------------------------------------------------ events

    /** After a restart a stored effect may be behind (crash between payment and setter): fixed when a member joins. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        core.scheduler().later("progression-upgrade-join", 40L, () -> {
            if (ready && !closed) {
                islands().flatMap(s -> s.islandOf(player)).ifPresent(i -> reconcile(i.id()));
            }
        });
    }

    @EventHandler
    public void onIslandRemoved(IslandRemovedEvent event) {
        bought.remove(event.islandId());
        readyCache.remove(event.islandId());
        knownBank.remove(event.islandId());
    }

    /** "Ready to buy" line in island chat when the last task of an upgrade's next level was done. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (!config.notifyReady || !available()) {
            return;
        }
        UUID islandId = event.islandId();
        Set<String> told = new HashSet<>();
        for (String key : event.unlocksCompleted()) {
            if (!key.startsWith("upgrade.")) {
                continue;
            }
            int dot = key.lastIndexOf('.');
            String id = key.substring("upgrade.".length(), Math.max("upgrade.".length(), dot));
            int n;
            try {
                n = Integer.parseInt(key.substring(dot + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            UpgradeCatalog.Def def = catalog.get(id).orElse(null);
            if (def == null || !def.buyable() || level(islandId, id) != n - 1 || !told.add(id)) {
                continue;
            }
            UpgradeCatalog.Level next = def.level(n);
            if (next == null || levels.level(islandId) < next.minIslandLevel()) {
                continue;
            }
            for (Player p : onlineMembers(islandId)) {
                msg.send(p, "upgrades.ready.chat", Msg.c("upgrade", upgradeName(p, def)), Msg.p("level", UpgradeRules.roman(n)),
                        Msg.p("cost", Format.money(next.cost())));
            }
        }
    }

    // ------------------------------------------------------------------ placeholders

    void registerPlaceholders(String module, java.util.function.Function<Player, Optional<UUID>> islandOf) {
        core.placeholders().registerText(module, "island_upgrades_bought", p ->
                String.valueOf(islandOf.apply(p).map(this::totalLevels).orElse(0)));
        // Task and level reads are main-thread only: other threads get the value last computed on the main thread.
        core.placeholders().registerText(module, "island_upgrades_ready", p -> String.valueOf(islandOf.apply(p).map(i -> {
            if (!Bukkit.isPrimaryThread()) {
                return readyCache.getOrDefault(i, 0);
            }
            int n = readyCount(i);
            readyCache.put(i, n);
            return n;
        }).orElse(0)));
        for (UpgradeCatalog.Def def : catalog.all()) {
            String id = def.id();
            core.placeholders().registerText(module, "island_upgrade_" + id, p ->
                    String.valueOf(islandOf.apply(p).map(i -> level(i, id)).orElse(0)));
        }
    }
}
