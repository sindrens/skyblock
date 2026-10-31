package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.TaskCompletedEvent;
import net.skybornmc.skyblock.common.satchel.SatchelService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Island masteries and Ascension (PHASE-5 M5.5): the catalog ({@code masteries.yml}), the bought ranks
 * ({@code island_masteries}, all islands of the season in memory), buying and the effects.
 *
 * <ul>
 *   <li><b>Gating:</b> a rank needs its pillar level or island level, every challenge book task of its key
 *       {@code mastery.<id>.<rank>} ({@code TaskService#unlocked}) and, for Market Contacts, Sell Multiplier maxed.
 *       Ascension needs every shown upgrade and mastery maxed. Buying needs the island permission "upgrades".</li>
 *   <li><b>Payment:</b> exactly like upgrades: island bank only, one economy transaction (ledger reason
 *       {@code island_mastery}, no fee) with the rank row as its guard; idempotency key per island, season, mastery and
 *       rank; a second click while one runs is refused in memory.</li>
 *   <li><b>Effects:</b> BonusService source {@code masteries} for members (Warrior MOB_DAMAGE, which the enchants module
 *       applies to mobs only, never in PvP; Crystal Rush CRYSTALS; Lucky Strike RARE_FINDS; Market Contacts SELL; the family
 *       caps hold), Treasury → {@code IslandService#bankFeeMasteryProvider}, Wings → {@code #wingsProvider}, Homestead →
 *       {@code #extraIslandHomesProvider} (shared island homes, P5-21), Deep Pockets → {@code SatchelService#capacityBonus}.
 *       Island level points go into the upgrades' source ({@code LevelSource.UPGRADES}: upgrades + masteries).</li>
 * </ul>
 * Main thread unless noted; placeholders and hooks may read from other threads (concurrent map of immutable maps).
 */
final class MasteryManager implements Listener {

    static final String BONUS_SOURCE = "masteries";
    static final String SATCHEL_SOURCE = "deep_pockets";
    static final String AUDIT_BUY = "mastery_buy";

    private final Core core;
    private final MasteriesConfig config;
    private final UpgradesConfig upgradesConfig;
    private final ModuleMessages msg;
    private final LevelManager levels;
    private final PillarManager pillars;
    private final TaskManager tasks;
    private final UpgradeManager upgrades;
    private final int season;
    private final MasteryStore store = new MasteryStore();
    private final Map<UUID, Map<String, Integer>> bought = new ConcurrentHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    /** Deep Pockets reads the satchel's capacity without itself (the satchel asks every bonus source). */
    private final ThreadLocal<Boolean> satchelReentry = ThreadLocal.withInitial(() -> false);
    private volatile MasteryCatalog catalog = MasteryCatalog.empty();
    private boolean ready;
    private boolean closed;
    private boolean hooked;

    MasteryManager(Core core, MasteriesConfig config, UpgradesConfig upgradesConfig, ModuleMessages msg, LevelManager levels,
                   PillarManager pillars, TaskManager tasks, UpgradeManager upgrades, int season) {
        this.core = core;
        this.config = config;
        this.upgradesConfig = upgradesConfig;
        this.msg = msg;
        this.levels = levels;
        this.pillars = pillars;
        this.tasks = tasks;
        this.upgrades = upgrades;
        this.season = season;
    }

    Core core() {
        return core;
    }

    ModuleMessages msg() {
        return msg;
    }

    MasteriesConfig config() {
        return config;
    }

    MasteryCatalog catalog() {
        return catalog;
    }

    TaskManager tasks() {
        return tasks;
    }

    UpgradeManager upgrades() {
        return upgrades;
    }

    /** True when the Masteries tab and {@code /is masteries} can open: switched on, loaded, upgrades open too. */
    boolean available() {
        return config.enabled && ready && upgrades.available() && (!catalog.shown().isEmpty() || config.ascensionEnabled);
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        catalog = loadCatalog();
        load();
    }

    /** Reload: settings were reloaded by the module; the catalog is read again. Bought ranks stay. */
    void applyConfig() {
        catalog = loadCatalog();
        if (ready) {
            bought.keySet().forEach(levels::request);
        }
    }

    private void load() {
        core.database().async(c -> store.load(c, season)).whenComplete((loaded, error) -> core.scheduler().main("progression-mastery-load", () -> {
            if (closed) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: loading the island masteries failed; retrying in 30 s",
                        Database.cause(error));
                core.scheduler().later("progression-mastery-load-retry", 30 * 20L, this::load);
                return;
            }
            loaded.forEach((island, ranks) -> bought.merge(island, Map.copyOf(ranks), UpgradeManager::higher));
            ready = true;
            hook();
            bought.keySet().forEach(levels::request);
            core.plugin().getLogger().info("Progression: loaded the masteries of " + loaded.size() + " islands ("
                    + catalog.size() + " masteries, " + catalog.shown().size() + " shown, season " + season + ").");
        }));
    }

    /** Effects, bonus source and level points, once the bought ranks are known (before that every hook reads "none"). */
    private void hook() {
        core.services().optional(BonusService.class).ifPresent(b -> b.register(BONUS_SOURCE, this::bonus));
        islands().ifPresent(s -> {
            s.wingsProvider(this::hasWings);
            s.bankFeeMasteryProvider(this::treasuryLevel);
            s.extraIslandHomesProvider(this::extraHomes);
        });
        core.services().optional(SatchelService.class).ifPresent(s -> s.capacityBonus(SATCHEL_SOURCE, this::satchelBonus));
        upgrades.extraPoints(this::points);
        hooked = true;
    }

    void shutdown() {
        closed = true;
        if (hooked) {
            core.services().optional(BonusService.class).ifPresent(b -> b.unregister(BONUS_SOURCE));
            islands().ifPresent(s -> {
                s.wingsProvider(null);
                s.bankFeeMasteryProvider(null);
                s.extraIslandHomesProvider(null);
            });
            core.services().optional(SatchelService.class).ifPresent(s -> s.removeCapacityBonus(SATCHEL_SOURCE));
            upgrades.extraPoints(null);
            hooked = false;
        }
    }

    // ------------------------------------------------------------------ masteries.yml

    /**
     * Reads {@code masteries.yml} from the data folder. A missing file is written from the bundled one; masteries that are
     * new in the bundled file are added to an existing file (turn one off with {@code enabled: false} instead).
     */
    private MasteryCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), MasteryCatalog.FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning(MasteryCatalog.FILE + " is not valid YAML, using the bundled masteries. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return MasteryCatalog.load(bundled, UpgradeManager::isItem, w -> core.plugin().getLogger().warning(w));
        }
        boolean changed = false;
        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(MasteryCatalog.FILE)) {
                file.getParentFile().mkdirs();
                if (in != null) {
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + MasteryCatalog.FILE, e);
            }
            yaml = bundled;
        } else if (bundled.getConfigurationSection(MasteryCatalog.SECTION) != null) {
            for (String id : bundled.getConfigurationSection(MasteryCatalog.SECTION).getKeys(false)) {
                String path = MasteryCatalog.SECTION + "." + id;
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
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + MasteryCatalog.FILE, e);
            }
        }
        MasteryCatalog loaded = MasteryCatalog.load(yaml, UpgradeManager::isItem, w -> core.plugin().getLogger().warning(w));
        for (MasteryCatalog.Def def : loaded.all()) {
            for (String id : def.requiresMaxed()) {
                if (upgrades.catalog().get(id).isEmpty()) {
                    core.plugin().getLogger().warning(MasteryCatalog.FILE + " → " + def.id() + ": requires-maxed upgrade '" + id
                            + "' is not in upgrades.yml; the mastery stays locked.");
                }
            }
        }
        core.plugin().getLogger().info("Progression: " + loaded.size() + " island masteries (" + loaded.shown().size() + " shown).");
        return loaded;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MasteryCatalog.FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, MasteryCatalog.FILE + " in the jar is broken", e);
        }
        return yaml;
    }

    // ------------------------------------------------------------------ reads

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** Bought ranks of the island (Ascension under {@link MasteryRules#ASCENSION_ID}); never null, immutable. Any thread. */
    Map<String, Integer> of(UUID islandId) {
        Map<String, Integer> m = islandId == null ? null : bought.get(islandId);
        return m == null ? Map.of() : m;
    }

    int rank(UUID islandId, String masteryId) {
        return of(islandId).getOrDefault(masteryId, 0);
    }

    int ascension(UUID islandId) {
        return rank(islandId, MasteryRules.ASCENSION_ID);
    }

    /** Bought mastery ranks without Ascension (placeholder {@code island_masteries_bought}). */
    int totalRanks(UUID islandId) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : of(islandId).entrySet()) {
            if (!MasteryRules.ASCENSION_ID.equals(e.getKey())) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    double points(UUID islandId) {
        return MasteryRules.points(catalog, of(islandId), upgradesConfig.points(), config.ascensionLevelPoints);
    }

    boolean tasksDone(UUID islandId, MasteryCatalog.Def def) {
        return MasteryRules.tasksDone(def, rank(islandId, def.id()), tasks.ready(), key -> tasks.unlocked(islandId, key));
    }

    boolean allMaxed(UUID islandId) {
        return MasteryRules.allMaxed(upgrades.catalog(), upgrades.of(islandId), catalog, of(islandId));
    }

    /** The next rank's state for {@code viewer}; {@code bank} &lt; 0 = balance not known. */
    MasteryRules.Status status(Player viewer, UUID islandId, MasteryCatalog.Def def, long bank) {
        return status(islandId, def, upgrades.canBuy(viewer, islandId), bank);
    }

    private MasteryRules.Status status(UUID islandId, MasteryCatalog.Def def, boolean permission, long bank) {
        int current = rank(islandId, def.id());
        int pillar = def.pillar() == null ? 0 : pillars.pillarLevel(islandId, def.pillar());
        MasteryRules.Gate gate = new MasteryRules.Gate(MasteryRules.requiresMet(def, upgrades.catalog(), upgrades.of(islandId)),
                pillar, levels.level(islandId), tasksDone(islandId, def), permission, bank);
        return MasteryRules.status(def, current, gate);
    }

    /** Price of the next Ascension level of the island, -1 when none can be bought. */
    long ascensionCost(UUID islandId) {
        return MasteryRules.ascensionCost(config.ascensionBaseCost, config.ascensionGrowth, config.ascensionStartLevel, ascension(islandId) + 1);
    }

    MasteryRules.Status ascensionStatus(Player viewer, UUID islandId, long bank) {
        return ascensionStatus(islandId, upgrades.canBuy(viewer, islandId), bank);
    }

    private MasteryRules.Status ascensionStatus(UUID islandId, boolean permission, long bank) {
        MasteryRules.Gate gate = new MasteryRules.Gate(allMaxed(islandId), 0, levels.level(islandId), true, permission, bank);
        return MasteryRules.ascensionStatus(config.ascensionEnabled, ascension(islandId), config.ascensionMaxLevels, ascensionCost(islandId), gate);
    }

    // ------------------------------------------------------------------ texts

    Component masteryName(Player viewer, MasteryCatalog.Def def) {
        String key = "masteries.name." + def.id();
        String raw = core.messages().raw(ProgressionModule.ID, key);
        if (raw == null) {
            return Component.text(TaskRules.pretty(def.id()));
        }
        return msg.component(viewer, key);
    }

    Component ascensionName(Player viewer) {
        return msg.component(viewer, "masteries.ascension.name");
    }

    /** The effect at {@code rank} as text, e.g. "+8% damage to mobs"; rank 0 = "Nothing yet". */
    Component effectText(Player viewer, MasteryCatalog.Def def, int rank) {
        if (rank <= 0) {
            return msg.component(viewer, "upgrades.effect-none");
        }
        double value = def.valueAt(rank);
        return msg.component(viewer, "masteries.effect." + def.effect().id(), Msg.p("value", UpgradeCatalog.trim(value)),
                Msg.p("fee", def.effect() == MasteryEffect.TREASURY ? feeText(value) : ""));
    }

    /**
     * Treasury: the bank fee at the fee step this rank gives, from bank.yml through the bank's own steps
     * (2 → 1.5 → 1.0 → 0.5%), the single source of the numbers. "?" while the bank is off.
     */
    String feeText(double step) {
        double percent = core.services().optional(BankService.class).map(b -> b.withdrawFeePercent((int) Math.round(step))).orElse(-1.0);
        return percent < 0 ? "?" : UpgradeCatalog.trim(percent);
    }

    // ------------------------------------------------------------------ buying

    /**
     * Buys rank {@code rank} of a mastery (or Ascension level {@code rank} with {@link MasteryRules#ASCENSION_ID}) for
     * {@code player}'s island, paid from the island bank. Every rule is checked again here. {@code done} runs on the main
     * thread with the outcome (null = refused before any payment, the player was told why).
     */
    void buy(Player player, String masteryId, int rank, Consumer<UpgradeManager.Outcome> done) {
        boolean ascension = MasteryRules.ASCENSION_ID.equals(masteryId);
        MasteryCatalog.Def def = ascension ? null : catalog.get(masteryId).orElse(null);
        IslandService islands = islands().orElse(null);
        IslandService.IslandInfo island = islands == null ? null : islands.islandOf(player.getUniqueId()).orElse(null);
        if (!available() || (!ascension && def == null) || islands == null) {
            msg.send(player, "masteries.unavailable");
            finish(done, null);
            return;
        }
        if (island == null) {
            msg.send(player, "upgrades.no-island");
            finish(done, null);
            return;
        }
        UUID islandId = island.id();
        Component name = ascension ? ascensionName(player) : masteryName(player, def);
        int current = rank(islandId, masteryId);
        if (current >= rank) {
            msg.send(player, "masteries.buy.already", Msg.c("mastery", name), Msg.p("rank", UpgradeRules.roman(rank)));
            finish(done, null);
            return;
        }
        MasteryRules.Status status = ascension ? ascensionStatus(player, islandId, -1) : status(player, islandId, def, -1);
        if (current != rank - 1 || status != MasteryRules.Status.READY) {
            refuse(player, name, def, current, status);
            finish(done, null);
            return;
        }
        String flight = islandId + ":" + masteryId;
        if (!inFlight.add(flight)) {
            msg.send(player, "upgrades.buy.busy");
            finish(done, null);
            return;
        }
        long cost = ascension ? ascensionCost(islandId) : def.rank(rank).cost();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        long now = System.currentTimeMillis();
        TxGuard guard = c -> store.buy(c, islandId, season, masteryId, rank, profile, now);
        CompletableFuture<TxResult> result;
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        if (cost > 0) {
            if (economy == null) {
                inFlight.remove(flight);
                msg.send(player, "masteries.unavailable");
                finish(done, null);
                return;
            }
            Transaction tx = Transaction.withdraw(AccountRef.island(islandId), Currency.MONEY, cost, MasteryRules.LEDGER_REASON)
                    .actor(Actor.player(player))
                    .reference(MasteryRules.reference(masteryId, rank))
                    .idempotencyKey(MasteryRules.idempotencyKey(season, islandId, masteryId, rank))
                    .detail("mastery", masteryId)
                    .detail("rank", rank)
                    .detail("island", islandId.toString())
                    .detail("target_name", island.name());
            result = economy.executeGuarded(tx, guard);
        } else {
            result = core.database().transaction(guard::holds)
                    .thenApply(ok -> TxResult.of(ok ? TxResult.Status.SUCCESS : TxResult.Status.DUPLICATE))
                    .exceptionally(e -> TxResult.of(TxResult.Status.ERROR));
        }
        result.whenComplete((r, error) -> core.scheduler().main("progression-mastery-buy", () -> {
            inFlight.remove(flight);
            TxResult.Status s = error != null || r == null ? TxResult.Status.ERROR : r.status();
            UpgradeManager.Outcome outcome = switch (s) {
                case SUCCESS -> UpgradeManager.Outcome.BOUGHT;
                case INSUFFICIENT_FUNDS -> UpgradeManager.Outcome.NO_MONEY;
                case DUPLICATE -> UpgradeManager.Outcome.ALREADY;
                default -> UpgradeManager.Outcome.FAILED;
            };
            upgrades.forgetBank(islandId);
            switch (outcome) {
                case BOUGHT -> bought(player, island, def, masteryId, rank, cost, r == null ? null : r.fromBalance());
                case NO_MONEY -> {
                    msg.send(player, "upgrades.buy.no-money", Msg.p("cost", Format.money(cost)));
                    core.sounds().play(player, "error");
                }
                case ALREADY -> {
                    msg.send(player, "upgrades.buy.changed");
                    refreshFromDatabase(islandId, masteryId);
                }
                case FAILED -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Progression: buying mastery " + masteryId + " " + rank
                                + " for island " + islandId + " failed", Database.cause(error));
                    }
                    msg.send(player, "upgrades.buy.failed");
                    core.sounds().play(player, "error");
                }
            }
            finish(done, outcome);
        }));
    }

    private static void finish(Consumer<UpgradeManager.Outcome> done, UpgradeManager.Outcome outcome) {
        if (done != null) {
            done.accept(outcome);
        }
    }

    /** Tells the player why the next rank can't be bought right now ({@code def} null = Ascension). */
    void refuse(Player player, Component name, MasteryCatalog.Def def, int current, MasteryRules.Status status) {
        MasteryCatalog.Rank next = def == null ? null : def.rank(current + 1);
        TagResolver m = Msg.c("mastery", name);
        switch (status) {
            case DISABLED, COMING_SOON -> msg.send(player, "masteries.buy.coming-soon", m);
            case MAXED -> msg.send(player, "masteries.buy.maxed", m);
            case NEEDS_MAXED -> msg.send(player, def == null ? "masteries.buy.needs-all" : "masteries.buy.needs-maxed", m,
                    Msg.c("upgrade", requiredUpgrades(player, def)));
            case PILLAR_LEVEL -> msg.send(player, "masteries.buy.pillar-level", m,
                    Msg.c("pillar", pillarName(player, def)), Msg.p("level", next == null ? 0 : next.pillarLevel()));
            case ISLAND_LEVEL -> msg.send(player, "masteries.buy.island-level", m, Msg.p("level", next == null ? 0 : next.islandLevel()));
            case TASKS_OPEN -> msg.send(player, "masteries.buy.tasks", m);
            case NO_PERMISSION -> msg.send(player, "upgrades.buy.no-permission");
            case NO_MONEY -> msg.send(player, "upgrades.buy.no-money", Msg.p("cost", Format.money(next == null ? 0 : next.cost())));
            case READY -> msg.send(player, "upgrades.buy.changed");
        }
        core.sounds().play(player, "error");
    }

    /** The names of a mastery's {@code requires-maxed} upgrades, joined ("Sell Multiplier"). */
    Component requiredUpgrades(Player viewer, MasteryCatalog.Def def) {
        if (def == null) {
            return Component.empty();
        }
        Component out = Component.empty();
        boolean first = true;
        for (String id : def.requiresMaxed()) {
            Component name = upgrades.catalog().get(id).map(u -> upgrades.upgradeName(viewer, u)).orElse(Component.text(TaskRules.pretty(id)));
            out = first ? name : out.append(Component.text(", ")).append(name);
            first = false;
        }
        return out;
    }

    Component pillarName(Player viewer, MasteryCatalog.Def def) {
        return def == null || def.pillar() == null ? Component.empty() : msg.component(viewer, "pillar." + def.pillar().id() + ".name");
    }

    /** After a refused (duplicate) purchase: the stored rank wins, e.g. another member bought it a moment earlier. */
    private void refreshFromDatabase(UUID islandId, String masteryId) {
        core.database().async(c -> store.level(c, islandId, season, masteryId)).whenComplete((rank, error) ->
                core.scheduler().main("progression-mastery-refresh", () -> {
                    if (error == null && rank != null && rank > 0) {
                        setRank(islandId, masteryId, rank);
                        levels.request(islandId);
                    }
                }));
    }

    private void setRank(UUID islandId, String masteryId, int rank) {
        bought.merge(islandId, Map.of(masteryId, rank), UpgradeManager::higher);
    }

    /** A purchase was saved: memory, island level, audit and notices. {@code def} null = Ascension. */
    private void bought(Player buyer, IslandService.IslandInfo island, MasteryCatalog.Def def, String masteryId, int rank, long cost,
                        Long bankAfter) {
        UUID islandId = island.id();
        setRank(islandId, masteryId, rank);
        levels.request(islandId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mastery", masteryId);
        details.put("rank", rank);
        details.put("cost", cost);
        details.put("buyer", buyer.getName());
        details.put("buyer_profile", ProfileService.of(core.services()).profileKey(buyer).toString());
        if (bankAfter != null) {
            details.put("bank_after", bankAfter);
        }
        details.put("season", season);
        core.audit().log(AuditCategory.ISLAND, AUDIT_BUY, Actor.player(buyer), islandId.toString(), island.name(), cost, null, details);
        Component buyerName = def == null ? ascensionName(buyer) : masteryName(buyer, def);
        msg.send(buyer, "masteries.buy.done", Msg.c("mastery", buyerName), Msg.p("rank", UpgradeRules.roman(rank)),
                Msg.p("cost", Format.money(cost)), Msg.p("bank", bankAfter == null ? "?" : Format.money(bankAfter)));
        for (Player p : upgrades.onlineMembers(islandId)) {
            Component name = def == null ? ascensionName(p) : masteryName(p, def);
            Component effect = def == null ? msg.component(p, "masteries.ascension.effect", Msg.p("points", UpgradeCatalog.trim(config.ascensionLevelPoints)))
                    : effectText(p, def, rank);
            TagResolver[] t = {Msg.c("mastery", name), Msg.p("rank", UpgradeRules.roman(rank)), Msg.c("effect", effect), Msg.p("player", buyer.getName())};
            if (config.notifyChat && !p.equals(buyer)) {
                msg.send(p, "masteries.bought.chat", t);
            }
            if (config.notifyActionBar) {
                msg.actionBar(p, "masteries.bought.action-bar", t);
            }
            core.sounds().play(p, config.buySound);
        }
    }

    // ------------------------------------------------------------------ hooks

    /**
     * BonusService source {@code masteries}: the island's mastery values for its members (never coop/visitors). Warrior
     * answers MOB_DAMAGE, which the enchants module applies to hits on mobs only (players are never asked), so PvE damage
     * never reaches PvP. Every family's cap is applied by the BonusService on the total.
     */
    double bonus(BonusFamily family, BonusContext context) {
        Player player = context == null ? null : context.player();
        if (player == null || !ready) {
            return 0;
        }
        UUID island = islands().flatMap(s -> s.islandOf(player.getUniqueId())).map(IslandService.IslandInfo::id).orElse(null);
        return island == null ? 0 : MasteryRules.bonusPercent(catalog, of(island), family, context.activity());
    }

    boolean hasWings(UUID islandId) {
        return ready && MasteryRules.value(catalog, of(islandId), MasteryEffect.WINGS) > 0;
    }

    int treasuryLevel(UUID islandId) {
        return ready ? MasteryRules.whole(catalog, of(islandId), MasteryEffect.TREASURY) : 0;
    }

    int extraHomes(UUID islandId) {
        return ready ? MasteryRules.extraHomes(catalog, of(islandId)) : 0;
    }

    /** Deep Pockets: percent of the satchel's capacity without this bonus (the satchel asks every source; no recursion). */
    int satchelBonus(Player player) {
        if (!ready || player == null || satchelReentry.get()) {
            return 0;
        }
        UUID island = islands().flatMap(s -> s.islandOf(player.getUniqueId())).map(IslandService.IslandInfo::id).orElse(null);
        double percent = island == null ? 0 : MasteryRules.value(catalog, of(island), MasteryEffect.DEEP_POCKETS);
        if (percent <= 0) {
            return 0;
        }
        SatchelService satchel = core.services().optional(SatchelService.class).orElse(null);
        if (satchel == null) {
            return 0;
        }
        satchelReentry.set(true);
        try {
            return MasteryRules.satchelBonus(satchel.capacity(player), percent);
        } finally {
            satchelReentry.set(false);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onIslandRemoved(IslandRemovedEvent event) {
        bought.remove(event.islandId());
    }

    /** "Ready to buy" line in island chat when the last task of a mastery's next rank was done (and the rest is met). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (!config.notifyReady || !available()) {
            return;
        }
        UUID islandId = event.islandId();
        Set<String> told = new HashSet<>();
        for (String key : event.unlocksCompleted()) {
            if (!key.startsWith("mastery.")) {
                continue;
            }
            int dot = key.lastIndexOf('.');
            String id = key.substring("mastery.".length(), Math.max("mastery.".length(), dot));
            int n;
            try {
                n = Integer.parseInt(key.substring(dot + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            MasteryCatalog.Def def = catalog.get(id).orElse(null);
            if (def == null || !def.buyable() || rank(islandId, id) != n - 1 || !told.add(id)) {
                continue;
            }
            if (!status(islandId, def, true, -1).unlocked()) {
                continue;
            }
            MasteryCatalog.Rank next = def.rank(n);
            for (Player p : upgrades.onlineMembers(islandId)) {
                msg.send(p, "masteries.ready.chat", Msg.c("mastery", masteryName(p, def)), Msg.p("rank", UpgradeRules.roman(n)),
                        Msg.p("cost", Format.money(next.cost())));
            }
        }
    }

    // ------------------------------------------------------------------ placeholders

    void registerPlaceholders(String module, java.util.function.Function<Player, Optional<UUID>> islandOf) {
        core.placeholders().registerText(module, "island_masteries_bought", p ->
                String.valueOf(islandOf.apply(p).map(this::totalRanks).orElse(0)));
        core.placeholders().registerText(module, "island_ascension", p ->
                String.valueOf(islandOf.apply(p).map(this::ascension).orElse(0)));
        for (MasteryCatalog.Def def : catalog.all()) {
            String id = def.id();
            core.placeholders().registerText(module, "island_mastery_" + id, p ->
                    String.valueOf(islandOf.apply(p).map(i -> rank(i, id)).orElse(0)));
        }
    }

    /** Masteries of the catalog shown in the menu, for tests and the tab: the live ones. */
    List<MasteryCatalog.Def> shown() {
        return catalog.shown();
    }
}
