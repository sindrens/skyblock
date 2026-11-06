package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.nextstep.NextStepService;
import net.skybornmc.skyblock.common.npc.NpcDirectory;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tutorial.TutorialService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The guided tour {@code /tutorial} (docs/planning/PLAN_TUTORIAL_CHAT.md section 3, PLAN_4NOV_C.md section 4; owner
 * 4 Nov 2026: "a new player must always know what to do"). Seven chapters from {@code tutorial.yml}, progress and rewards
 * per account, a boss bar with the current step, the shared Next step (scoreboard, Kindle, /help), talking NPCs and the
 * pages of Rowan's {@code /start}. Main thread unless noted.
 */
public final class TutorialModule implements SkyblockModule, TutorialService {

    public static final String ID = "tutorial";
    static final String PERM_USE = "skyblock.tutorial.use";
    static final String PERM_ADMIN = "skyblock.tutorial.admin";

    private final Core core;
    private final TutorialConfig config = new TutorialConfig();
    private ModuleMessages msg;
    private TutorialStore store;
    private Catalog catalog = Catalog.empty();
    private Places places;
    private Dialogs dialogs;
    private TutorialListener listener;
    private Set<String> auditWanted = Set.of();
    private Runnable stopAudit = () -> { };
    private final Map<UUID, Progress> progress = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** When the player's bar was last (re)shown because something changed (for {@code bossbar.hide-after-seconds}). */
    private final Map<UUID, Long> barSince = new HashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    public TutorialModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        File file = new File(core.plugin().getDataFolder(), Catalog.FILE);
        if (!file.exists()) {
            core.plugin().saveResource(Catalog.FILE, false); // the chapters; the settings are added below
        }
        core.configs().load(config);
        msg = core.messages(ID);
        store = new TutorialStore(core.database());
        dialogs = new Dialogs(this);
        loadCatalog();
        listener = new TutorialListener(this);
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        core.services().register(ID, TutorialService.class, this);
        NextStepService.of(core.services()).register(ID, config.nextStepPriority, this::nextStep);
        stopAudit = core.audit().listen(entry -> AuditSignals.map(entry, auditWanted).ifPresent(hit ->
                Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    Player p = Bukkit.getPlayer(hit.player());
                    if (p != null) {
                        signal(p, hit.signal());
                    }
                })));
        tasks.add(core.scheduler().repeat("tutorial:dialogs", 1L, 1L, dialogs::tick));
        tasks.add(core.scheduler().repeat("tutorial:second", 20L, 20L, this::everySecond));
        tasks.add(core.scheduler().repeat("tutorial:save", 600L, 600L, this::saveDirty));
        new TutorialCommands(this).register();
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
    }

    @Override
    public void reload() throws Exception {
        core.configs().load(config);
        loadCatalog();
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    @Override
    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        stopAudit.run();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (dialogs != null) {
            dialogs.endAll();
        }
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bars.clear();
        for (Progress pr : progress.values()) {
            if (pr.dirty()) {
                try {
                    store.saveNow(pr.player(), snapshot(pr));
                } catch (RuntimeException ex) {
                    core.plugin().getLogger().log(Level.WARNING, "Tutorial: could not save " + pr.player(), ex);
                }
            }
        }
        progress.clear();
        NextStepService.of(core.services()).unregisterAll(ID);
        core.services().unregister(ID, TutorialService.class);
    }

    /** Shards x10 (owner 7 Nov 2026, docs/plans/SHARDS_X10_BREAKTHROUGHS.md): the chapter shard rewards of a live file, once. */
    static final java.util.Map<Integer, java.util.Map<String, net.skybornmc.skyblock.common.config.CatalogUpdate.Change>> CATALOG_STEPS =
            java.util.Map.of(
                    // v2 (FINISH_PLAN batch A): the invite step no longer blocks a solo player (visiting or liking an island counts)
                    2, java.util.Map.of(
                    "chapters.together.steps.invite.target", net.skybornmc.skyblock.common.config.CatalogUpdate.Change.of(
                            "island:island_invite|island_coop", "island:island_invite|island_coop|island_visit|island_like"),
                    "chapters.together.steps.invite.text", net.skybornmc.skyblock.common.config.CatalogUpdate.Change.of(
                            "Invite a friend to your island", "Invite or visit a friend"),
                    "chapters.together.steps.invite.hint", net.skybornmc.skyblock.common.config.CatalogUpdate.Change.of(
                            "Use /is invite <name> or /is coop <name>.", "Use /is invite <name>, or /is visit to see others.")),
                    1, java.util.Map.of(
                    "chapters.farm.rewards", change(List.of("shards 100"), List.of("shards 1000")),
                    "chapters.mine.rewards", change(List.of("mining_shards 100"), List.of("mining_shards 1000")),
                    "chapters.farm_pro.rewards", change(List.of("shards 500", "key ember 1"), List.of("shards 5000", "key ember 1")),
                    "chapters.mine_pro.rewards", change(List.of("mining_shards 500", "key ember 1"), List.of("mining_shards 5000", "key ember 1")),
                    "chapters.fish_pro.rewards", change(List.of("fishing_shards 500", "key ember 1"), List.of("fishing_shards 5000", "key ember 1")),
                    "chapters.pve_pro.rewards", change(List.of("combat_shards 500", "key ember 1"), List.of("combat_shards 5000", "key ember 1"))));

    private static net.skybornmc.skyblock.common.config.CatalogUpdate.Change change(List<String> from, List<String> to) {
        return net.skybornmc.skyblock.common.config.CatalogUpdate.Change.of(from, to);
    }

    private void loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), Catalog.FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Money x100 (batch 10): the money rewards of a live file from before are scaled once.
        if (!yaml.getKeys(false).isEmpty()) {
            net.skybornmc.skyblock.common.config.CatalogScale.applyToFile(file, yaml, java.util.List.of(net.skybornmc.skyblock.common.config.CatalogScale.Path.of("chapters.*.rewards", net.skybornmc.skyblock.common.config.CatalogScale.moneyLines())),
                    core.plugin().getLogger());
        }
        if (!yaml.getKeys(false).isEmpty()) {
            int before = net.skybornmc.skyblock.common.config.CatalogUpdate.version(yaml);
            for (String line : net.skybornmc.skyblock.common.config.CatalogUpdate.apply(yaml, CATALOG_STEPS)) {
                core.plugin().getLogger().info(Catalog.FILE + ": " + line);
            }
            if (net.skybornmc.skyblock.common.config.CatalogUpdate.version(yaml) != before) {
                try {
                    yaml.save(file);
                } catch (java.io.IOException e) {
                    core.plugin().getLogger().log(Level.WARNING, "Could not write " + Catalog.FILE, e);
                }
            }
        }
        catalog = Catalog.load(yaml, w -> core.plugin().getLogger().warning(w));
        places = Places.parse(config.places, w -> core.plugin().getLogger().warning(w));
        auditWanted = AuditSignals.wanted(catalog);
        for (Catalog.Chapter c : catalog.chapters()) {
            Rewards.parse(c.rewards(), w -> core.plugin().getLogger().warning(w + " (chapter " + c.id() + ")"));
        }
    }

    // ------------------------------------------------------------------ accessors

    Core core() {
        return core;
    }

    TutorialConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    Catalog catalog() {
        return catalog;
    }

    Places places() {
        return places;
    }

    Dialogs dialogs() {
        return dialogs;
    }

    TutorialStore store() {
        return store;
    }

    Optional<Progress> progress(Player p) {
        return Optional.ofNullable(progress.get(p.getUniqueId()));
    }

    // ------------------------------------------------------------------ availability

    /**
     * True when a step can be done on this server: its command exists and (NPC steps) an NPC with its role is placed.
     * An unavailable step counts as done, so a missing feature never blocks a new player.
     */
    boolean available(Catalog.Step s) {
        if (!s.command().isEmpty() && !commandExists(s.command())) {
            return false;
        }
        if (s.type() == StepType.NPC) {
            return core.services().optional(NpcDirectory.class).flatMap(d -> d.npcByRole(s.target())).isPresent();
        }
        if (s.type() == StepType.PLACE) {
            return places.get(s.target()).isPresent();
        }
        return true;
    }

    Predicate<Catalog.Step> availability() {
        return this::available;
    }

    boolean commandExists(String command) {
        String root = command.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return !root.isEmpty() && Bukkit.getCommandMap().getCommand(root) != null;
    }

    // ------------------------------------------------------------------ loading and saving

    void load(Player p) {
        UUID id = p.getUniqueId();
        store.load(id).whenComplete((saved, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tutorial: could not load " + p.getName() + " (tour paused for them)", error);
                return;
            }
            if (!p.isOnline()) {
                return;
            }
            Progress pr = new Progress(id);
            pr.restore(saved.done(), saved.counters(), saved.chapters(), saved.skippedAt(), saved.hidden());
            progress.put(id, pr);
            // Chapters whose steps are all done or unavailable here finish now (no reward for nothing done twice: the
            // reward is claimed once per account in the database).
            List<Catalog.Chapter> finished = pr.settle(catalog, availability(), System.currentTimeMillis());
            finished.forEach(c -> reward(p, pr, c));
            barSince.put(id, System.currentTimeMillis());
            refresh(p);
            syncToolLevels(p);
        }));
    }

    /** FINISH_PLAN batch A (owner 7 Nov 2026): a "Reach hoe level 10" step counts the level the tool already has. */
    void syncToolLevels(Player p) {
        core.services().optional(net.skybornmc.skyblock.common.tools.IslandToolLevels.class).ifPresent(tools -> {
            for (String kind : List.of("hoe", "pickaxe", "rod", "sword")) {
                int level = tools.level(p, kind);
                if (level > 0) {
                    signal(p, Signal.of(StepType.TOOL_LEVEL, kind, level));
                }
            }
        });
    }

    void unload(Player p) {
        UUID id = p.getUniqueId();
        dialogs.end(p, false);
        bars.remove(id);
        barSince.remove(id);
        Progress pr = progress.remove(id);
        if (pr != null && pr.dirty()) {
            store.save(id, snapshot(pr));
        }
    }

    /** Staff reset: forget the cached progress (the database rows are already gone) so the next load starts fresh. */
    void unloadWithoutSave(Player p) {
        dialogs.end(p, false);
        hideBar(p);
        barSince.remove(p.getUniqueId());
        progress.remove(p.getUniqueId());
    }

    private void saveDirty() {
        for (Progress pr : progress.values()) {
            if (pr.dirty()) {
                pr.clean();
                store.save(pr.player(), snapshot(pr));
            }
        }
    }

    static TutorialStore.Saved snapshot(Progress pr) {
        return new TutorialStore.Saved(pr.doneKeys(), pr.counters(), pr.chapterIds(), pr.skippedAt(), pr.hidden());
    }

    // ------------------------------------------------------------------ signals

    /** One real thing the player did. */
    void signal(Player p, Signal signal) {
        if (!config.enabled) {
            return;
        }
        Progress pr = progress.get(p.getUniqueId());
        if (pr == null || pr.finished(catalog)) {
            return;
        }
        Progress.Change change = pr.apply(catalog, signal, availability(), System.currentTimeMillis());
        if (!change.any()) {
            return;
        }
        announce(p, pr, change);
    }

    /** True when an open step of the current chapter listens to this signal (the NPC click decides on its dialog). */
    List<Catalog.Step> listening(Player p, Signal signal) {
        Progress pr = progress.get(p.getUniqueId());
        if (pr == null || !config.enabled || pr.finished(catalog)) {
            return List.of();
        }
        return pr.listening(catalog, signal, availability());
    }

    void announce(Player p, Progress pr, Progress.Change change) {
        for (Catalog.Step s : change.stepsDone()) {
            msg.actionBar(p, "step.done", Msg.p("step", s.text()));
            core.sounds().play(p, "success");
        }
        for (Catalog.Chapter c : change.chaptersDone()) {
            reward(p, pr, c);
        }
        if (!change.stepsDone().isEmpty() || !change.chaptersDone().isEmpty()) {
            barSince.put(p.getUniqueId(), System.currentTimeMillis());
            next(p).ifPresent(n -> msg.send(p, "step.next", Msg.p("step", n.text()), Msg.p("hint", n.hint())));
            Bukkit.getScheduler().runTask(core.plugin(), () -> { if (p.isOnline()) { syncToolLevels(p); } }); // the next step may be a level
        }
        refresh(p);
    }

    // ------------------------------------------------------------------ rewards

    /** Pays a finished chapter once per account (database claim + money in one transaction; keys and tags after). */
    private void reward(Player p, Progress pr, Catalog.Chapter chapter) {
        if (pr.skipped()) {
            return;
        }
        List<Rewards.Reward> rewards = Rewards.parse(chapter.rewards(), w -> { });
        UUID player = p.getUniqueId();
        UUID profile = core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(player);
        List<Transaction> tx = new ArrayList<>();
        for (Rewards.Reward r : rewards) {
            if (r.kind() == Rewards.Kind.CURRENCY) {
                tx.add(Transaction.deposit(AccountRef.player(profile), r.currency(), r.amount(), TxReason.TUTORIAL_REWARD)
                        .actor(Actor.player(p)).reference("tutorial:" + chapter.id())
                        .idempotencyKey("tutorial:" + player + ":" + chapter.id() + ":" + r.currency().key())
                        .detail("chapter", chapter.id()));
            }
        }
        boolean[] claimed = new boolean[1];
        long now = System.currentTimeMillis();
        TxGuard guard = c -> {
            claimed[0] = TutorialStore.claim(c, player, chapter.id(), now);
            return claimed[0];
        };
        CompletableFuture<Boolean> run;
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (tx.isEmpty() || economy.isEmpty()) {
            run = core.database().transaction(guard::holds);
        } else {
            run = economy.get().executeBatch(tx, guard).thenApply(BatchResult::ok);
        }
        run.whenComplete((ok, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tutorial: reward of " + chapter.id() + " for " + p.getName() + " failed", error);
                return;
            }
            if (!claimed[0] || !Boolean.TRUE.equals(ok)) {
                return; // paid before (another profile, a reset): never twice
            }
            for (Rewards.Reward r : rewards) {
                switch (r.kind()) {
                    case KEY -> core.services().optional(CrateKeys.class).ifPresent(k ->
                            k.giveKeys(profile, r.id(), (int) r.amount(), "tutorial", Actor.player(p)));
                    case TAG -> core.services().optional(Cosmetics.class).ifPresent(cs ->
                            cs.grant(player, Cosmetics.Kind.TAG, r.id(), "tutorial:" + chapter.id()));
                    default -> { }
                }
            }
            core.audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_chapter", Actor.player(p), player, p.getName(), null,
                    p.getLocation(), Map.of("chapter", chapter.id()));
            if (p.isOnline()) {
                msg.title(p, "chapter.done", Msg.p("chapter", chapter.name()));
                msg.send(p, "chapter.reward", Msg.p("chapter", chapter.name()), Msg.p("rewards", rewardText(chapter)));
                core.sounds().play(p, "levelup");
            }
        }));
    }

    /** Plain reward text for lore and chat: "$500, 100 Farming Shards". */
    String rewardText(Catalog.Chapter chapter) {
        List<String> parts = new ArrayList<>();
        for (Rewards.Reward r : Rewards.parse(chapter.rewards(), w -> { })) {
            String key = switch (r.kind()) {
                case CURRENCY -> "reward." + r.currency().key();
                case KEY -> "reward.key";
                case TAG -> "reward.tag";
            };
            String raw = core.messages().raw(ID, key);
            String text = raw == null ? r.amount() + " " + (r.id() == null ? r.currency().key() : r.id()) : raw
                    .replace("<amount>", String.format(Locale.ROOT, "%,d", r.amount()))
                    .replace("<id>", r.id() == null ? "" : r.id());
            parts.add(text);
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    // ------------------------------------------------------------------ the next step

    @Override
    public Optional<TutorialService.NextStep> next(Player player) {
        Progress pr = progress.get(player.getUniqueId());
        if (pr == null || !config.enabled) {
            return Optional.empty();
        }
        Optional<Catalog.Chapter> chapter = pr.current(catalog);
        return pr.next(catalog, availability()).map(s -> new TutorialService.NextStep(
                chapter.map(Catalog.Chapter::name).orElse(""), s.text(), s.hint(), commandOf(s)));
    }

    /** The command a click on the step runs: its place's command, else the command it teaches, else /tutorial. */
    String commandOf(Catalog.Step s) {
        if (!s.place().isEmpty()) {
            Optional<Places.Place> place = places.get(s.place());
            if (place.isPresent()) {
                return place.get().command();
            }
        }
        return s.command().isEmpty() ? "tutorial" : s.command();
    }

    private Optional<net.skybornmc.skyblock.common.nextstep.NextStep> nextStep(Player player) {
        // Fully qualified: TutorialService.NextStep (inherited member type) shadows the import here.
        return next(player).map(n -> new net.skybornmc.skyblock.common.nextstep.NextStep(n.text(), n.command()));
    }

    @Override
    public void openStart(Player player) {
        core.menus().open(player, new TutorialMenu(this, true));
    }

    @Override
    public void open(Player player) {
        if (core.bedrock().isBedrock(player.getUniqueId())) {
            core.menus().open(player, new TutorialMenu(this, false));
        } else {
            TutorialBook.open(this, player);
        }
    }

    @Override
    public boolean ownsStartBar() {
        return config.enabled && config.bossbarEnabled;
    }

    // ------------------------------------------------------------------ boss bar and places

    private void everySecond() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Progress pr = progress.get(p.getUniqueId());
            if (pr == null || pr.finished(catalog)) {
                continue;
            }
            Location at = p.getLocation();
            if (at.getWorld() != null) {
                places.at(at.getWorld().getName(), at.getX(), at.getZ()).ifPresent(pl -> signal(p, Signal.of(StepType.PLACE, pl.id(), 1)));
            }
            if (onOwnIsland(p)) {
                signal(p, Signal.of(StepType.ISLAND));
            }
            int hide = config.bossbarHideSeconds;
            if (hide > 0 && bars.containsKey(p.getUniqueId()) && now - barSince.getOrDefault(p.getUniqueId(), now) > hide * 1000L) {
                hideBar(p);
            }
        }
    }

    boolean onOwnIsland(Player p) {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        if (islands.isEmpty()) {
            return false;
        }
        UUID key = core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
        Optional<IslandService.IslandInfo> own = islands.get().islandOf(key);
        Optional<IslandService.IslandInfo> here = islands.get().islandAt(p.getLocation());
        return own.isPresent() && here.isPresent() && own.get().id().equals(here.get().id());
    }

    /** Shows, updates or hides the boss bar after a change. */
    void refresh(Player p) {
        Progress pr = progress.get(p.getUniqueId());
        Optional<Catalog.Step> step = pr == null ? Optional.empty() : pr.next(catalog, availability());
        Optional<Catalog.Chapter> chapter = pr == null ? Optional.empty() : pr.current(catalog);
        long now = System.currentTimeMillis();
        boolean recent = config.bossbarHideSeconds <= 0
                || now - barSince.getOrDefault(p.getUniqueId(), 0L) <= config.bossbarHideSeconds * 1000L;
        if (!config.enabled || !config.bossbarEnabled || pr == null || pr.hidden() || step.isEmpty() || chapter.isEmpty() || !recent) {
            hideBar(p);
            return;
        }
        Catalog.Chapter c = chapter.get();
        float fill = c.steps().isEmpty() ? 1f : Math.min(1f, (float) pr.doneCount(c, availability()) / c.steps().size());
        Component text = msg.component(p, "bossbar", Msg.p("step", step.get().text()), Msg.p("chapter", c.name()));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(text, fill, color(config.bossbarColor), overlay(config.bossbarOverlay));
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(text);
            bar.progress(fill);
        }
    }

    /** Shows the bar again (a step changed, /tutorial show, a click on the book). */
    void showBar(Player p) {
        barSince.put(p.getUniqueId(), System.currentTimeMillis());
        refresh(p);
    }

    void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static BossBar.Color color(String raw) {
        try {
            return BossBar.Color.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return BossBar.Color.YELLOW;
        }
    }

    private static BossBar.Overlay overlay(String raw) {
        try {
            return BossBar.Overlay.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
