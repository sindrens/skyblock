package net.skybornmc.skyblock.collection;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.collection.CollectionLog;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.crate.CrateOpenedEvent;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Collection log (PHASE-10-REWARDS.md M10.10): {@code /collection}, per profile (owner 130, principle 2).
 * <ul>
 *   <li><b>Finds</b> come only from things the profile got itself (P10-32): Depths block/log drops and rare finds, Depths
 *       mob drops, crate star rewards ({@link CrateOpenedEvent}); later sources call {@link CollectionLog#found}. Trades,
 *       AH buys and /pay never reach it.</li>
 *   <li><b>Pages</b> (tabs): Mine, Forest, Combat, Crates. Found entries glow; others are locked with how to get them.</li>
 *   <li><b>Milestones</b> (P10-33): 25/50/75/100% per page = 1 island level point each ("Collections" source, PLAN5); a
 *       full page = its title + a Blaze key; the whole log = Keeper of Ashes + a Phoenix key (+ the secret set token once
 *       one exists). Paid once: the milestone row and its hand-out rows are one transaction.</li>
 * </ul>
 */
public final class CollectionModule implements SkyblockModule, Listener, CollectionLog {

    static final String ID = "collection";
    static final String PERM_USE = "skyblock.collection.use";
    /** How often the Crates page is compared with the crates module's catalog (5 minutes). */
    static final long CATALOG_REFRESH_TICKS = 20L * 300;

    final Core core;
    final ModuleMessages msg;
    final CollectionSettings settings = new CollectionSettings();
    private final Recorder recorder;
    private final Grants grants;
    private final Notices notices;
    private DepthsHooks depthsHooks;
    private Executor main;
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    private volatile Catalog catalog = Catalog.EMPTY;
    /** PERF11: the periodic catalog refresh is built and compared off the main thread. */
    private final CatalogRefresh refresh = new CatalogRefresh();
    private volatile List<Integer> percents = List.of(Milestones.FULL);
    private volatile Milestones.Rewards rewards;

    public CollectionModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.recorder = new Recorder(this);
        this.grants = new Grants(this);
        this.notices = new Notices(this);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(settings);
        main = new MainThread(core.plugin());
        parse();
        defineTitles();
        recorder.loadCounts();
        registerCommands();
        registerPlaceholders();
        core.services().register(ID, CollectionLog.class, this);
        core.services().optional(ProgressionService.class).ifPresent(p -> p.levelPointsProvider(LevelSource.COLLECTIONS, recorder::islandPoints));
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        try {
            depthsHooks = new DepthsHooks(this);
            Bukkit.getPluginManager().registerEvents(depthsHooks, core.plugin());
        } catch (LinkageError e) {
            depthsHooks = null;
            core.plugin().getLogger().warning("Collection: the Depths module is missing, Depths finds are not recorded");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            recorder.load(p);
        }
        long save = 20L * settings.saveSeconds;
        tasks.add(core.scheduler().repeat("collection:save", save, save, recorder::flush));
        long retry = 20L * settings.retrySeconds;
        // The first run also recovers hand-outs a crash cut off (after every module, crates and cosmetics included, is up).
        tasks.add(core.scheduler().repeat("collection:grants", 200L, retry, grants::retry));
        // Crate rewards come from the crates module's catalog: build the page again once every module is up, then every few
        // minutes so a crates.yml reload shows up without reloading this module (only a changed catalog is logged).
        // PERF11: built and compared off the main thread; the main thread (collection:catalog) only applies a change.
        tasks.add(core.scheduler().asyncRepeat(100L, CATALOG_REFRESH_TICKS, this::refreshCatalogAsync));
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        parse();
        defineTitles();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (depthsHooks != null) {
            HandlerList.unregisterAll(depthsHooks);
            depthsHooks = null;
        }
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        refresh.invalidate();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof CollectionMenu) {
                p.closeInventory();
            }
        }
        recorder.flushBlocking();
        recorder.clear();
        core.services().optional(ProgressionService.class).ifPresent(p -> p.levelPointsProvider(LevelSource.COLLECTIONS, null));
        core.placeholders().unregisterAll(ID);
    }

    // ================================================================== config

    private void parse() {
        List<String> problems = new ArrayList<>();
        percents = Milestones.percents(settings.percents, problems::add);
        rewards = Milestones.Rewards.of(settings);
        catalog = buildCatalog(problems);
        refresh.shown(catalog);
        for (String p : problems) {
            core.plugin().getLogger().warning("Collection: " + p);
        }
        core.plugin().getLogger().info("Collection: " + catalog.total() + " entries on " + catalog.visible().size() + " pages.");
    }

    /**
     * Bukkit async pool (PERF11): builds the catalog from collection.yml and the crates module's catalog (both read-only
     * snapshots) and compares it with the shown one; only a change goes to the main thread.
     */
    private void refreshCatalogAsync() {
        int version = refresh.version();
        List<String> problems = new ArrayList<>();
        CatalogRefresh.Result changed = refresh.compare(buildCatalog(problems), problems, version);
        if (changed != null) {
            core.scheduler().main("collection:catalog", () -> applyCatalog(changed));
        }
    }

    /** Main thread: shows a changed catalog (dropped when a reload rebuilt it meanwhile) and checks milestones again. */
    private void applyCatalog(CatalogRefresh.Result changed) {
        if (!refresh.accept(changed)) {
            return;
        }
        Catalog fresh = changed.catalog();
        catalog = fresh;
        for (String p : changed.problems()) {
            core.plugin().getLogger().warning("Collection: " + p);
        }
        core.plugin().getLogger().info("Collection: " + fresh.total() + " entries on " + fresh.visible().size() + " pages.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            ProfileLog log = recorder.log(profiles().profileKey(p));
            if (log != null) {
                recorder.checkMilestones(log);
            }
        }
    }

    private Catalog buildCatalog(List<String> problems) {
        Optional<CrateKeys> keys = core.services().optional(CrateKeys.class);
        return Catalog.build(settings.pageOrder, Map.of("mine", settings.mine, "forest", settings.forest, "combat", settings.combat),
                settings.crates, settings.cratesTopOnly, crate -> keys.map(k -> k.rewards(crate)).orElse(List.of()),
                id -> core.items().get(id).isPresent(), problems::add);
    }

    /** Page titles and Keeper of Ashes exist in tags-titles.yml (added once; staff edits stay). */
    private void defineTitles() {
        Optional<Cosmetics> cosmetics = cosmetics();
        if (cosmetics.isEmpty()) {
            return;
        }
        for (Catalog.PageKind kind : Catalog.PageKind.values()) {
            String id = Milestones.pageTitle(kind.id());
            cosmetics.get().defineTitle(id, raw("titles." + kind.id(), id), Rarity.EPIC, ID, false);
        }
        if (!rewards.logTitle().isEmpty()) {
            cosmetics.get().defineTitle(rewards.logTitle(), raw("titles.log", rewards.logTitle()), Rarity.MYTHIC, ID, false);
        }
    }

    // ================================================================== commands and placeholders

    private void registerCommands() {
        core.commands().register(CommandSpec.command("collection")
                .description("Your collection log: every item you found yourself, page milestones and their rewards")
                .category("Progression")
                .permission(PERM_USE)
                .playerOnly()
                .example("collection")
                .executes(ctx -> {
                    if (!settings.enabled) {
                        throw new CommandFailure(ID, "disabled");
                    }
                    open(ctx.player(), null);
                }));
    }

    private void registerPlaceholders() {
        core.placeholders().register(ID, "collection_found", Placeholder.text(p -> p == null ? "0"
                : String.valueOf(foundCount(profiles().profileKey(p)))));
        core.placeholders().register(ID, "collection_total", Placeholder.text(p -> String.valueOf(totalCount())));
        core.placeholders().register(ID, "collection_percent", Placeholder.text(p -> {
            int total = totalCount();
            return p == null || total == 0 ? "0" : String.valueOf(foundCount(profiles().profileKey(p)) * 100 / total);
        }));
    }

    // ================================================================== CollectionLog

    @Override
    public void found(Player player, String key, String source) {
        if (!settings.enabled || player == null || key == null) {
            return;
        }
        recorder.found(profiles().profileKey(player), player, key, source == null ? "other" : source);
    }

    @Override
    public int foundCount(UUID profileKey) {
        ProfileLog log = recorder.log(profileKey);
        return log == null || !log.loaded() ? 0 : catalog.found(log.foundIds());
    }

    @Override
    public int totalCount() {
        return catalog.total();
    }

    @Override
    public void open(Player player, Menu parent) {
        recorder.load(player);
        CollectionMenu menu = new CollectionMenu(this);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
    }

    // ================================================================== accessors and helpers

    Executor main() {
        return main;
    }

    Catalog catalog() {
        return catalog;
    }

    List<Integer> percents() {
        return percents;
    }

    Milestones.Rewards rewards() {
        return rewards;
    }

    Recorder recorder() {
        return recorder;
    }

    Grants grants() {
        return grants;
    }

    Notices notices() {
        return notices;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    Optional<Cosmetics> cosmetics() {
        return core.services().optional(Cosmetics.class);
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** The online player who is on this profile right now, or null. */
    Player receiver(UUID profile) {
        return profiles().onlinePlayer(profile).filter(p -> profiles().profileKey(p).equals(profile)).orElse(null);
    }

    String raw(String key, String fallback) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? fallback : raw;
    }

    /** A MiniMessage text as a component for a viewer (style tags resolved). */
    Component mini(Player viewer, String mini) {
        return msg.component(viewer, "text", Msg.mm("text", mini == null ? "" : mini));
    }

    Component pageName(Player viewer, String page) {
        return msg.component(viewer, "pages." + page + ".name");
    }

    /** Display name of an entry: the custom item's own name, or the crate reward's name. */
    Component entryName(Player viewer, Catalog.Entry e) {
        if (e.nameMini() != null) {
            return mini(viewer, e.nameMini());
        }
        return mini(viewer, core.items().get(e.id()).map(ItemDefinition::name).orElse(e.id()));
    }

    /** Icon of an entry: the custom item's material, or the crate reward's icon. */
    Material entryIcon(Catalog.Entry e) {
        if (e.icon() != null) {
            Material m = Material.matchMaterial(e.icon());
            return m != null && m.isItem() && !m.isAir() ? m : Material.CHEST;
        }
        return core.items().get(e.id()).map(ItemDefinition::material).filter(m -> m.isItem() && !m.isAir()).orElse(Material.PAPER);
    }

    /** How to get a locked entry ("Mine tier 3 in the Depths", "Star reward of the Blaze crate"). */
    Component hint(Player viewer, Catalog.Entry e) {
        if (e.crate() != null) {
            String crate = core.services().optional(CrateKeys.class).map(k -> k.crateNameMini(e.crate())).orElse(e.crate());
            return msg.component(viewer, "hint.crates", Msg.c("crate", mini(viewer, crate)));
        }
        return msg.component(viewer, "hint." + e.page(), Msg.number("tier", e.tier()));
    }

    /** One milestone reward as text. */
    Component grantText(Player viewer, Milestones.Grant g) {
        return switch (g.kind()) {
            case Milestones.TITLE -> msg.component(viewer, "reward.title", Msg.c("title",
                    cosmetics().map(c -> c.display(viewer, Cosmetics.Kind.TITLE, g.ref())).orElse(Component.text(g.ref()))));
            case Milestones.KEY -> {
                String crate = core.services().optional(CrateKeys.class).map(k -> k.crateNameMini(g.ref())).orElse(g.ref());
                yield msg.component(viewer, "reward.key", Msg.number("amount", g.amount()), Msg.c("crate", mini(viewer, crate)));
            }
            case Milestones.SET -> msg.component(viewer, "reward.set");
            default -> Component.text(g.ref());
        };
    }

    void sound(Player p, String key) {
        if (p != null && key != null && !key.isBlank()) {
            core.sounds().play(p, key);
        }
    }

    void audit(String action, Actor actor, UUID player, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.COLLECTION, action, actor, player, null, amount, null, details);
    }

    void refreshMenu(UUID profile) {
        Player p = receiver(profile);
        if (p != null && core.menus().openMenu(p) instanceof CollectionMenu menu) {
            menu.refresh();
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later("collection:join", 1L, () -> {
            if (p.isOnline()) {
                recorder.load(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recorder.forget(profiles().profileKey(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        if (event.from() != null) {
            recorder.forget(event.from().id());
        }
        recorder.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        recorder.deleted(profile);
        core.database().transaction(c -> {
            CollectionStore.deleteProfile(c, profile);
            return true;
        }).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Collection: could not remove the log of deleted profile " + profile, e);
            } else {
                audit("profile_reset", Actor.SYSTEM, event.profile().owner(), 0L, Map.of("profile", profile.toString()));
            }
        });
    }

    /** Crate star rewards that were really given (not a crystal fallback). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrateOpened(CrateOpenedEvent event) {
        if (!settings.enabled) {
            return;
        }
        Player online = event.online();
        Player receiver = online != null && profiles().profileKey(online).equals(event.profile()) ? online : null;
        String crate = event.crate().toLowerCase(Locale.ROOT);
        for (CrateOpenedEvent.Won won : event.rewards()) {
            if (!won.fallback()) {
                recorder.found(event.profile(), receiver, Catalog.crateEntryId(crate, won.reward()), "crate");
            }
        }
    }
}
