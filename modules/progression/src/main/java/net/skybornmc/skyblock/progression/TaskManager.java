package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.IslandLevelUpEvent;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.PillarLevelUpEvent;
import net.skybornmc.skyblock.common.progression.TaskCompletedEvent;
import net.skybornmc.skyblock.common.progression.TaskInfo;
import net.skybornmc.skyblock.common.progression.TaskService;
import net.skybornmc.skyblock.common.progression.TaskStorage;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.skills.SkillLevelUpEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The task engine and challenge book (PHASE-5 M5.3, O6) and the published {@link TaskService}.
 *
 * <ul>
 *   <li><b>Counting:</b> {@link TaskSources} (and the Depths listener) call {@link #count} after the anti-abuse checks;
 *       counters are shared per island, kept in memory and written as deltas in batches ({@link TaskBuffer}).</li>
 *   <li><b>Completion:</b> exactly once per island, task and season. The done row and every member's crystals commit in
 *       one database transaction (with per-member idempotency keys); pillar XP is added after the commit. A failed save
 *       is tried again by the next progress or the periodic check.</li>
 *   <li><b>Deliveries:</b> {@link Delivery} (inventory: take → save player file → write progress) or the island storage
 *       hook {@link TaskStorage} (storage rows and progress in one transaction).</li>
 * </ul>
 * Everything runs on the main thread except the database work. Coop players and visitors never count.
 */
final class TaskManager implements TaskService, Listener {

    static final String TOGGLE = "task-notices";
    static final String LEDGER_REASON = "island_task";
    static final String PERM_TASKS = "skyblock.progression.tasks";

    private final Core core;
    private final TasksConfig config;
    private final ModuleMessages msg;
    private final PillarManager pillars;
    private final LevelManager levels;
    private final int season;
    private final TaskStore store = new TaskStore();
    private final TaskBuffer buffer = new TaskBuffer();
    private final TaskProgress progress = new TaskProgress(TaskCatalog.empty());
    private final Executor main;
    private volatile TaskStorage storage;
    private TaskMenus menus;
    private boolean ready;
    private boolean closed;
    private Scheduler.Task flushTask;
    private Scheduler.Task checkTask;
    private Scheduler.Task loadRetry;
    private CompletableFuture<?> inFlight = CompletableFuture.completedFuture(null);

    TaskManager(Core core, TasksConfig config, ModuleMessages msg, PillarManager pillars, LevelManager levels, int season) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.pillars = pillars;
        this.levels = levels;
        this.season = season;
        this.main = r -> core.scheduler().ensureMain("progression-tasks", r);
        progress.catalog(loadCatalog());
    }

    void menus(TaskMenus menus) {
        this.menus = menus;
    }

    Core core() {
        return core;
    }

    ModuleMessages msg() {
        return msg;
    }

    TasksConfig config() {
        return config;
    }

    TaskCatalog catalog() {
        return progress.catalog();
    }

    TaskStorage storageSource() {
        return storage;
    }

    boolean enabled() {
        return config.enabled && !closed;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        schedule();
        load();
    }

    /** Reload: settings and tasks.yml. Progress stays; new tasks that are already met complete on the next check. */
    void applyConfig() {
        progress.catalog(loadCatalog());
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (checkTask != null) {
            checkTask.cancel();
        }
        schedule();
    }

    private void schedule() {
        long flush = Math.max(2, config.flushSeconds) * 20L;
        flushTask = core.scheduler().repeat("progression-task-flush", flush, flush, this::flush);
        long check = Math.max(5, config.checkSeconds) * 20L;
        checkTask = core.scheduler().repeat("progression-task-check", check, check, this::checkOnline);
    }

    private void load() {
        core.database().async(c -> store.load(c, season)).whenComplete((loaded, error) -> core.scheduler().main("progression-task-load", () -> {
            if (closed) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: loading the island tasks failed; retrying in 30 s",
                        Database.cause(error));
                loadRetry = core.scheduler().later("progression-task-load-retry", 30 * 20L, this::load);
                return;
            }
            for (TaskStore.Row row : loaded.rows()) {
                progress.addStored(row.island(), row.counter(), row.amount());
            }
            loaded.done().forEach((island, done) -> done.forEach(id -> progress.markDone(island, id)));
            ready = true;
            core.plugin().getLogger().info("Progression: loaded the tasks of " + progress.islandIds().size() + " islands ("
                    + catalog().size() + " tasks, season " + season + ").");
        }));
    }

    void shutdown() {
        closed = true;
        for (Scheduler.Task t : new Scheduler.Task[]{flushTask, checkTask, loadRetry}) {
            if (t != null) {
                t.cancel();
            }
        }
        try {
            inFlight.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // a failed write merged its batch back; it is written below
        }
        Map<TaskBuffer.Key, Long> batch = buffer.drain();
        if (batch.isEmpty()) {
            return;
        }
        try {
            core.database().blockingTransaction(c -> store.write(c, season, batch, System.currentTimeMillis()));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Progression: " + batch.size() + " task counters could not be saved on shutdown", e);
        }
    }

    /** Writes the buffered counters. Waits for the load first, so a load never misses a write. */
    void flush() {
        if (!ready || closed || !inFlight.isDone() || buffer.isEmpty()) {
            return;
        }
        Map<TaskBuffer.Key, Long> batch = buffer.drain();
        long now = System.currentTimeMillis();
        inFlight = core.database().transaction(c -> store.write(c, season, batch, now)).whenComplete((rows, error) -> {
            if (error != null) {
                buffer.mergeBack(batch);
                core.plugin().getLogger().log(Level.WARNING, "Progression: writing task counters failed; retrying with the next flush",
                        Database.cause(error));
            }
        });
    }

    // ------------------------------------------------------------------ tasks.yml

    /**
     * Reads {@code tasks.yml} from the data folder. A missing file is written from the bundled one; tasks that are new in
     * the bundled file are added to an existing file (turn one off with {@code enabled: false} instead of deleting it).
     */
    private TaskCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), TaskCatalog.FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        TaskCatalog.Checks checks = new TaskCatalog.Checks(TaskManager::isBlock, TaskManager::isEntity, TaskManager::isItem);
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning(TaskCatalog.FILE + " is not valid YAML, using the bundled tasks. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return TaskCatalog.load(bundled, checks, w -> core.plugin().getLogger().warning(w));
        }
        boolean changed = false;
        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(TaskCatalog.FILE)) {
                file.getParentFile().mkdirs();
                if (in != null) {
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + TaskCatalog.FILE, e);
            }
            yaml = bundled;
        } else if (bundled.getConfigurationSection(TaskCatalog.SECTION) != null) {
            for (String id : bundled.getConfigurationSection(TaskCatalog.SECTION).getKeys(false)) {
                String path = TaskCatalog.SECTION + "." + id;
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
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + TaskCatalog.FILE, e);
            }
        }
        TaskCatalog loaded = TaskCatalog.load(yaml, checks, w -> core.plugin().getLogger().warning(w));
        core.plugin().getLogger().info("Progression: " + loaded.size() + " task(s) in " + loaded.tiers().size() + " tiers.");
        return loaded;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(TaskCatalog.FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, TaskCatalog.FILE + " in the jar is broken", e);
        }
        return yaml;
    }

    private static boolean isBlock(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && m.isBlock() && !m.isAir();
    }

    private static boolean isItem(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir();
    }

    private static boolean isEntity(String id) {
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(id);
        return key != null && org.bukkit.Registry.ENTITY_TYPE.get(key) != null;
    }

    // ------------------------------------------------------------------ counting

    /**
     * Adds an action of a counting member to their island (the caller did the anti-abuse checks). Adds to the object's
     * counter and the {@code any} counter when a task reads them; finished tasks complete at once.
     *
     * @param cause the member's profile key
     */
    void count(UUID islandId, TaskType type, String object, long amount, UUID cause) {
        if (!enabled() || islandId == null || amount <= 0) {
            return;
        }
        for (String key : TaskRules.keys(type.id(), object)) {
            if (progress.add(islandId, key, amount)) {
                buffer.add(islandId, key, amount);
                if (ready) {
                    for (TaskDef def : progress.readyFor(islandId, key)) {
                        complete(islandId, def, cause);
                    }
                }
            }
        }
    }

    /** True if some task reads this type and object (lets hot listeners return early). */
    boolean tracks(TaskType type, String object) {
        TaskCatalog c = catalog();
        for (String key : TaskRules.keys(type.id(), object)) {
            if (c.tracks(key)) {
                return true;
            }
        }
        return false;
    }

    /** Checks every island with an online member: reach tasks, and tasks a reload or the load made ready. */
    void checkOnline() {
        if (!ready || !enabled()) {
            return;
        }
        IslandService islands = islands();
        if (islands == null) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            islands.islandOf(p.getUniqueId()).map(IslandService.IslandInfo::id).ifPresent(id -> {
                if (seen.add(id)) {
                    check(id);
                }
            });
        }
    }

    /** Completes everything that is ready on one island now. */
    void check(UUID islandId) {
        if (!ready || !enabled() || islandId == null) {
            return;
        }
        List<Player> online = onlineMembers(islandId);
        for (TaskDef def : progress.readyAll(islandId, d -> reachValue(islandId, d.info(), online))) {
            complete(islandId, def, null);
        }
    }

    /** Live value of a reach task: best online member's skill, the pillar level, the island level. */
    long reachValue(UUID islandId, TaskInfo info, List<Player> online) {
        return switch (info.type()) {
            case SKILL_LEVEL -> {
                SkillService skills = core.services().optional(SkillService.class).orElse(null);
                SkillType skill = SkillType.byId(info.target()).orElse(null);
                int best = 0;
                if (skills != null && skill != null) {
                    for (Player p : online) {
                        best = Math.max(best, skills.level(p, skill));
                    }
                }
                yield best;
            }
            case PILLAR_LEVEL -> Pillar.byId(info.target()).map(p -> pillars.pillarLevel(islandId, p)).orElse(0);
            case ISLAND_LEVEL -> levels.hasLevel(islandId) ? levels.level(islandId) : 0;
            default -> 0;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPillarLevel(PillarLevelUpEvent event) {
        core.scheduler().later("progression-task-pillar", 1L, () -> check(event.islandId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandLevel(IslandLevelUpEvent event) {
        if (event.rose()) {
            check(event.islandId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkillLevel(SkillLevelUpEvent event) {
        IslandService islands = islands();
        if (islands != null) {
            islands.islandOf(event.getPlayer().getUniqueId()).ifPresent(i -> core.scheduler().later("progression-task-skill", 1L,
                    () -> check(i.id())));
        }
    }

    @EventHandler
    public void onIslandRemoved(IslandRemovedEvent event) {
        progress.forget(event.islandId());
        buffer.forget(event.islandId());
    }

    // ------------------------------------------------------------------ completion

    /** Saves the completion and pays the members' crystals in one transaction, then rewards and tells the island. */
    private void complete(UUID islandId, TaskDef def, UUID cause) {
        TaskInfo info = def.info();
        IslandService islands = islands();
        List<UUID> members = islands == null ? List.of() : new ArrayList<>(islands.members(islandId).keySet());
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        long now = System.currentTimeMillis();
        long crystals = info.crystals();
        CompletableFuture<Boolean> saved;
        if (crystals > 0 && economy != null && !members.isEmpty()) {
            List<Transaction> txs = new ArrayList<>();
            for (UUID member : members) {
                txs.add(Transaction.deposit(AccountRef.player(member), Currency.CRYSTALS, crystals, LEDGER_REASON)
                        .actor(Actor.system("progression"))
                        .reference("task:" + info.id())
                        .idempotencyKey(TaskRules.idempotencyKey(season, islandId, info.id(), member))
                        .detail("island", islandId.toString()));
            }
            saved = economy.executeBatch(txs, c -> store.markDone(c, islandId, season, info.id(), cause, now))
                    .thenApply(TaskManager::outcome);
        } else {
            if (crystals > 0 && economy == null) {
                core.plugin().getLogger().warning("Progression: no economy service; task " + info.id() + " crystals not paid");
            }
            saved = core.database().transaction(c -> store.markDone(c, islandId, season, info.id(), cause, now));
        }
        long paid = crystals > 0 && economy != null && !members.isEmpty() ? crystals : 0;
        saved.whenComplete((ok, error) -> core.scheduler().main("progression-task-done", () -> {
            if (error != null || ok == null) {
                progress.finish(islandId, def, false);
                core.plugin().getLogger().log(Level.WARNING, "Progression: saving task " + info.id() + " of island " + islandId
                        + " failed; it is tried again on the next check", error == null ? null : Database.cause(error));
                return;
            }
            List<String> unlocked = progress.finish(islandId, def, true);
            if (!ok) {
                return; // was saved before (another server start): nothing paid or shown twice
            }
            if (info.pillar() != null && info.pillarXp() > 0) {
                pillars.gain(islandId, info.pillar(), info.pillarXp(), cause, "task", null);
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("task", info.id());
            details.put("tier", info.tier());
            details.put("crystals_per_member", paid);
            details.put("members", members.size());
            details.put("pillar_xp", info.pillarXp());
            details.put("unlocked", String.join(",", unlocked));
            if (cause != null) {
                details.put("cause", cause.toString());
            }
            core.audit().log(AuditCategory.ISLAND, "task_complete", Actor.system("progression"), islandId.toString(),
                    info.amount(), null, details);
            Bukkit.getPluginManager().callEvent(new TaskCompletedEvent(islandId, info, cause, unlocked));
            notifyDone(islandId, def, paid, cause);
        }));
    }

    /** True = saved and paid; false = already saved before; null = failed (try again later). */
    private static Boolean outcome(BatchResult result) {
        if (result.ok()) {
            return true;
        }
        return result.status() == TxResult.Status.DUPLICATE ? Boolean.FALSE : null;
    }

    // ------------------------------------------------------------------ deliveries

    /** Plain items of the target in the player's main inventory (custom: the custom item id). */
    long countItems(Player player, String target) {
        long n = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(stack, target)) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    /**
     * Only plain items hand in: {@code custom:<id>} = that custom item; a material id = the vanilla item with no custom id,
     * name, lore or enchants (tools, notes, keys and named items are never taken).
     */
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

    /** Hand in from the inventory (one click). Tells the player the result. */
    void deliverFromInventory(Player player, String taskId, Runnable after) {
        Optional<Context> ctx = deliverContext(player, taskId);
        if (ctx.isEmpty()) {
            after.run();
            return;
        }
        UUID islandId = ctx.get().islandId();
        TaskDef def = ctx.get().def();
        String target = def.info().target();
        String counter = def.info().counterKey();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        String name = player.getName();
        long have = countItems(player, target);
        List<ItemStack> taken = new ArrayList<>();
        Delivery.Steps steps = new Delivery.Steps() {
            @Override
            public long take(long max) {
                PlayerInventory inv = player.getInventory();
                ItemStack[] contents = inv.getStorageContents();
                int[] amounts = new int[contents.length];
                for (int i = 0; i < contents.length; i++) {
                    amounts[i] = matches(contents[i], target) ? contents[i].getAmount() : 0;
                }
                int[] plan = TaskRules.plan(amounts, max);
                long total = 0;
                for (int i = 0; i < plan.length; i++) {
                    if (plan[i] <= 0) {
                        continue;
                    }
                    ItemStack stack = contents[i];
                    ItemStack copy = stack.clone();
                    copy.setAmount(plan[i]);
                    taken.add(copy);
                    int left = stack.getAmount() - plan[i];
                    if (left <= 0) {
                        contents[i] = null;
                    } else {
                        stack.setAmount(left);
                    }
                    total += plan[i];
                }
                inv.setStorageContents(contents);
                return total;
            }

            @Override
            public boolean save() {
                return PlayerSaves.save(player, "a task delivery");
            }

            @Override
            public CompletableFuture<Void> commit(long amount) {
                long now = System.currentTimeMillis();
                return core.database().transaction(c -> {
                    store.add(c, islandId, season, counter, amount, now);
                    return null;
                });
            }

            @Override
            public void giveBack(long amount) {
                if (player.isOnline()) {
                    ItemBox.give(core.services(), player, BoxSource.RETURNED, taken);
                    PlayerSaves.save(player, "a task delivery give-back");
                } else {
                    core.plugin().getLogger().severe("Progression: " + amount + " x " + target + " of " + name
                            + " could not be given back after a failed task delivery (player offline); staff can give them back");
                }
            }
        };
        Delivery.run(progress, islandId, def, have, steps, main).whenComplete((outcome, error) -> {
            if (error != null || outcome == null) {
                core.plugin().getLogger().log(Level.SEVERE, "Progression: task delivery by " + name + " failed unexpectedly", error);
                after.run();
                return;
            }
            delivered(player, profile, islandId, def, outcome, "inventory");
            after.run();
        });
    }

    /** Hand in from the island storage (STORE2 hook): storage rows and progress in one transaction. */
    void deliverFromStorage(Player player, String taskId, Runnable after) {
        TaskStorage source = storage;
        Optional<Context> ctx = deliverContext(player, taskId);
        if (ctx.isEmpty()) {
            after.run();
            return;
        }
        if (source == null) {
            msg.send(player, "tasks.deliver.no-storage");
            after.run();
            return;
        }
        UUID islandId = ctx.get().islandId();
        TaskDef def = ctx.get().def();
        String counter = def.info().counterKey();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        long reserved = progress.reserve(islandId, def, source.count(islandId, def.info().target()));
        if (reserved <= 0) {
            msg.send(player, progress.room(islandId, def) <= 0 ? "tasks.deliver.not-needed" : "tasks.deliver.none-in-storage");
            after.run();
            return;
        }
        long now = System.currentTimeMillis();
        CompletableFuture<Long> take;
        try {
            take = source.take(islandId, def.info().target(), reserved, Actor.player(player),
                    (c, taken) -> store.add(c, islandId, season, counter, taken, now));
        } catch (RuntimeException e) {
            take = CompletableFuture.failedFuture(e);
        }
        take.whenCompleteAsync((taken, error) -> {
            if (error != null || taken == null) {
                progress.release(islandId, def, reserved, 0);
                core.plugin().getLogger().log(Level.WARNING, "Progression: storage delivery for task " + def.id() + " failed",
                        error == null ? null : Database.cause(error));
                delivered(player, profile, islandId, def, new Delivery.Outcome(Delivery.Status.FAILED, 0, List.of(), error), "storage");
            } else {
                long committed = Math.min(reserved, Math.max(0, taken));
                List<TaskDef> ready = progress.release(islandId, def, reserved, committed);
                delivered(player, profile, islandId, def, new Delivery.Outcome(committed > 0 ? Delivery.Status.DELIVERED
                        : Delivery.Status.NO_ITEMS, committed, ready, null), "storage");
            }
            after.run();
        }, main);
    }

    private record Context(UUID islandId, TaskDef def) {
    }

    private Optional<Context> deliverContext(Player player, String taskId) {
        if (!enabled() || !ready) {
            msg.send(player, "tasks.not-ready");
            return Optional.empty();
        }
        TaskDef def = catalog().get(taskId).orElse(null);
        if (def == null || def.info().type() != TaskType.DELIVER) {
            msg.send(player, "tasks.deliver.unknown");
            return Optional.empty();
        }
        IslandService islands = islands();
        UUID islandId = islands == null ? null : islands.islandOf(player.getUniqueId()).map(IslandService.IslandInfo::id).orElse(null);
        if (islandId == null) {
            msg.send(player, "tasks.no-island");
            return Optional.empty();
        }
        if (!islands.countsForIsland(islandId, player.getUniqueId())) {
            msg.send(player, "tasks.deliver.not-member");
            return Optional.empty();
        }
        if (progress.done(islandId, def.id()) || progress.room(islandId, def) <= 0) {
            msg.send(player, "tasks.deliver.not-needed");
            return Optional.empty();
        }
        return Optional.of(new Context(islandId, def));
    }

    private void delivered(Player player, UUID profile, UUID islandId, TaskDef def, Delivery.Outcome outcome, String from) {
        boolean online = player.isOnline();
        switch (outcome.status()) {
            case NOT_NEEDED -> {
                if (online) {
                    msg.send(player, "tasks.deliver.not-needed");
                }
            }
            case NO_ITEMS -> {
                if (online) {
                    msg.send(player, from.equals("storage") ? "tasks.deliver.none-in-storage" : "tasks.deliver.none",
                            Msg.c("item", objectName(player, def.info())));
                }
            }
            case FAILED -> {
                core.plugin().getLogger().log(Level.WARNING, "Progression: task delivery (" + from + ") by " + player.getName()
                        + " for " + def.id() + " was not saved; items returned", outcome.error() == null ? null : Database.cause(outcome.error()));
                if (online) {
                    msg.send(player, "tasks.deliver.failed");
                }
            }
            case DELIVERED -> {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("task", def.id());
                details.put("item", def.info().target());
                details.put("from", from);
                details.put("island", islandId.toString());
                details.put("profile", profile.toString());
                core.audit().log(AuditCategory.ISLAND, "task_deliver", Actor.player(player), islandId.toString(),
                        outcome.taken(), null, details);
                if (online) {
                    long now = progress.counted(islandId, def);
                    msg.send(player, "tasks.deliver.done", Msg.number("amount", outcome.taken()),
                            Msg.c("item", objectName(player, def.info())), Msg.number("progress", now),
                            Msg.number("goal", def.info().amount()));
                    core.sounds().play(player, config.deliverSound);
                }
                for (TaskDef ready : outcome.ready()) {
                    complete(islandId, ready, profile);
                }
            }
        }
    }

    // ------------------------------------------------------------------ notices

    private IslandService islands() {
        return core.services().optional(IslandService.class).orElse(null);
    }

    List<Player> onlineMembers(UUID islandId) {
        IslandService islands = islands();
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

    private boolean wantsChat(Player player) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(player, TOGGLE)).orElse(true);
    }

    private void notifyDone(UUID islandId, TaskDef def, long crystals, UUID cause) {
        String causeName = cause == null ? null : ProfileService.of(core.services()).onlinePlayer(cause).map(Player::getName).orElse(null);
        for (Player p : onlineMembers(islandId)) {
            Component name = taskName(p, def);
            if (config.notifyActionBar) {
                msg.actionBar(p, "tasks.done.action-bar", Msg.c("task", name));
            }
            if (config.notifyChat && wantsChat(p)) {
                Component by = causeName == null ? Component.empty() : msg.component(p, "tasks.done.by", Msg.p("player", causeName));
                msg.send(p, "tasks.done.chat", Msg.c("task", name), Msg.c("by", by));
                if (crystals > 0) {
                    msg.send(p, "tasks.done.reward", Msg.number("crystals", crystals));
                }
            }
            core.sounds().play(p, config.doneSound);
        }
    }

    // ------------------------------------------------------------------ names

    Component taskName(Player viewer, TaskDef def) {
        if (def.name() != null) {
            return core.messages().parse(viewer, def.name());
        }
        TaskInfo info = def.info();
        String key = TaskType.ANY.equals(info.target()) && !info.type().anyOnly()
                ? "tasks.name-any." + info.type().id() : "tasks.name." + info.type().id();
        return msg.component(viewer, key, Msg.number("amount", info.amount()), Msg.c("object", objectName(viewer, info)));
    }

    /** Display name of the target: pillar names from the messages, custom items by their name, else from the id. */
    Component objectName(Player viewer, TaskInfo info) {
        String target = info.target();
        if (info.type() == TaskType.PILLAR_LEVEL) {
            return msg.component(viewer, "pillar." + target + ".name");
        }
        if (target.startsWith("custom:")) {
            Optional<String> custom = core.items().get(target.substring("custom:".length())).map(d -> d.name());
            if (custom.isPresent() && custom.get() != null && !custom.get().isBlank()) {
                return core.messages().parse(viewer, custom.get());
            }
        }
        String pretty = TaskRules.pretty(target);
        if (info.type() == TaskType.KILL || info.type() == TaskType.CHOP) {
            pretty = TaskRules.plural(pretty);
        }
        return Component.text(pretty);
    }

    // ------------------------------------------------------------------ TaskService

    @Override
    public boolean ready() {
        return ready;
    }

    @Override
    public List<TaskInfo> tasks() {
        return catalog().all().stream().map(TaskDef::info).toList();
    }

    @Override
    public Optional<TaskInfo> task(String id) {
        return catalog().get(id).map(TaskDef::info);
    }

    @Override
    public List<TaskInfo> tasksFor(String unlockKey) {
        return catalog().byUnlock(unlockKey).stream().map(TaskDef::info).toList();
    }

    @Override
    public long progress(UUID islandId, String taskId) {
        TaskDef def = catalog().get(taskId).orElse(null);
        if (def == null || islandId == null) {
            return 0;
        }
        return progressOf(islandId, def);
    }

    long progressOf(UUID islandId, TaskDef def) {
        if (progress.done(islandId, def.id())) {
            return def.info().amount();
        }
        if (def.info().type().counted()) {
            return progress.counted(islandId, def);
        }
        return Math.min(def.info().amount(), reachValue(islandId, def.info(), onlineMembers(islandId)));
    }

    @Override
    public boolean completed(UUID islandId, String taskId) {
        return islandId != null && taskId != null && progress.done(islandId, taskId);
    }

    @Override
    public boolean unlocked(UUID islandId, String unlockKey) {
        return islandId != null && progress.unlocked(islandId, unlockKey);
    }

    @Override
    public int completedCount(UUID islandId) {
        return progress.doneCount(islandId);
    }

    @Override
    public Component taskName(Player viewer, String taskId) {
        return catalog().get(taskId).map(d -> taskName(viewer, d)).orElse(Component.empty());
    }

    @Override
    public List<Component> taskLines(Player viewer, UUID islandId, String unlockKey) {
        List<Component> out = new ArrayList<>();
        for (TaskDef def : catalog().byUnlock(unlockKey)) {
            out.add(taskLine(viewer, islandId, def));
        }
        return out;
    }

    Component taskLine(Player viewer, UUID islandId, TaskDef def) {
        boolean done = progress.done(islandId, def.id());
        TagResolver[] p = {Msg.c("task", taskName(viewer, def)), Msg.number("progress", progressOf(islandId, def)),
                Msg.number("goal", def.info().amount())};
        return msg.component(viewer, done ? "tasks.line.done" : "tasks.line.open", p);
    }

    @Override
    public void openBook(Player player, Menu parent) {
        if (menus != null) {
            menus.openBook(player, parent);
        }
    }

    @Override
    public void openTask(Player player, String taskId, Menu parent) {
        if (menus != null) {
            menus.openTask(player, taskId, parent);
        }
    }

    @Override
    public void storage(TaskStorage storage) {
        this.storage = storage;
    }

    // ------------------------------------------------------------------ menus

    TaskProgress state() {
        return progress;
    }
}
