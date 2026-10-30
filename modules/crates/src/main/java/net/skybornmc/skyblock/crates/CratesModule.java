package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
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
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Crates core (PHASE-10-REWARDS.md M10.1). Keys live per <b>profile</b> and crate as two counters, bound and tradeable
 * (bound first when opening). {@code /crates} shows every crate with your keys and pity; left-click = preview with every
 * reward and its real chance (V2 lore {@code ★ Name (xN) | chance%}), right-click = open 1, shift-click = open 10
 * (Bedrock: the buttons in the preview). Opens are atomic ({@link OpenEngine}); item rewards go to the inventory and the
 * rest to {@code /box}. Publishes {@link CrateKeys}, so the AFK pool hands over its pending keys as soon as this runs.
 * Animations, physical crate blocks and win broadcasts come in M10.3; clicking a crate block at the crate yard already
 * opens / previews (cheap: build points {@code crates}).
 */
public final class CratesModule implements SkyblockModule, Listener {

    static final String ID = "crates";
    static final String PERM_USE = "skyblock.crates.use";

    final Core core;
    final ModuleMessages msg;
    final CratesSettings settings = new CratesSettings();
    private volatile CrateCatalog catalog = CrateCatalog.empty();
    private volatile long[] refPerMinute = new long[0];
    private volatile int[] tierLevels = new int[0];
    private volatile boolean ready;
    private Executor main;
    private CrateOpener opener;
    private PhysicalCrates physical;
    private CrateKeysService keysService;
    /** Keys and pity per profile for the menus (loaded on demand, refreshed after every change). */
    private final Map<UUID, ProfileView> views = new ConcurrentHashMap<>();

    /** What the menus show of one profile. */
    record ProfileView(Map<String, CrateStore.Keys> keys, Map<String, CrateStore.Pity> pity) {

        CrateStore.Keys keys(String crate) {
            return keys.getOrDefault(crate, CrateStore.Keys.NONE);
        }

        int counter(String crate, int season) {
            CrateStore.Pity p = pity.get(crate);
            return p == null ? 0 : p.counter(season);
        }
    }

    public CratesModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public java.util.Set<String> dependencies() {
        return java.util.Set.of("economy");
    }

    @Override
    public void enable() throws Exception {
        if (core.services().optional(EconomyService.class).isEmpty()) {
            throw new IllegalStateException("EconomyService is not available (economy module disabled?)");
        }
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(settings);
        loadRules();
        main = new MainThread(core.plugin());
        catalog = loadCatalog();
        opener = new CrateOpener(this);
        physical = new PhysicalCrates(this);
        keysService = new CrateKeysService(this);
        // Published right away (keys only need the database): the AFK pool hands over its pending keys on its first check.
        core.services().register(ID, CrateKeys.class, keysService);
        registerCommand();
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        Bukkit.getPluginManager().registerEvents(physical, core.plugin());
        core.database().async(c -> CrateStore.recover(c, System.currentTimeMillis())).whenCompleteAsync((lists, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Crates: startup recovery failed; waiting rewards are checked at the next start",
                        Database.cause(error));
            } else {
                opener.recovered(lists.get(0), lists.get(1));
            }
            ready = true;
        }, main);
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        loadRules();
        catalog = loadCatalog();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (physical != null) {
            HandlerList.unregisterAll(physical);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isCratesMenu(core.menus().openMenu(p))) {
                p.closeInventory();
            }
        }
        ready = false;
        views.clear();
    }

    // ================================================================== config

    private void loadRules() {
        List<String> problems = new ArrayList<>();
        long[] ref = CrateMath.longs(settings.refPerMinute, "crates-settings.yml → pouch.ref-per-minute", problems);
        int[] levels = CrateMath.levels(settings.tierLevels, "crates-settings.yml → tier.tier-levels", problems);
        problems.forEach(core.plugin().getLogger()::warning);
        refPerMinute = ref;
        tierLevels = levels.length == 0 ? new int[]{0} : levels;
    }

    /**
     * Reads {@code crates.yml}; a missing file is written from the bundled one and crates new in the jar are added to an
     * existing file (rewards are never added to an existing crate, so the owner's tables and odds stay as edited).
     */
    private CrateCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), CrateCatalog.FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning("crates.yml is not valid YAML, using the bundled crates. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return CrateCatalog.load(bundled, core.plugin().getLogger()::warning);
        }
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(CrateCatalog.FILE)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, file.toPath());
                    }
                }
                yaml = bundled;
            } else if (CatalogMerge.addMissing(yaml, bundled)) {
                yaml.save(file);
            }
        } catch (IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not write crates.yml", e);
        }
        CrateCatalog loaded = CrateCatalog.load(yaml, core.plugin().getLogger()::warning);
        core.plugin().getLogger().info("Crates: " + loaded.size() + " crates.");
        return loaded;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CrateCatalog.FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, "crates.yml in the jar is broken", e);
        }
        return yaml;
    }

    CrateCatalog catalog() {
        return catalog;
    }

    long[] refPerMinute() {
        return refPerMinute;
    }

    int season() {
        return settings.seasonId;
    }

    boolean ready() {
        return ready;
    }

    Executor main() {
        return main;
    }

    CrateOpener opener() {
        return opener;
    }

    // ================================================================== command

    private void registerCommand() {
        Arg<CrateCatalog.Crate> crate = Arg.word("crate", (sender, raw) -> catalog.crate(raw).filter(c -> !c.hidden())
                .orElseThrow(() -> new CommandFailure(ID, "unknown-crate", Msg.p("input", raw),
                        Msg.p("crates", String.join(", ", visibleIds())))), Suggest.of(this::visibleIds));
        core.commands().register(CommandSpec.command("crates")
                .description("Open crates with your keys and see every reward")
                .category("Rewards")
                .permission(PERM_USE)
                .playerOnly()
                .optional(crate)
                .example("crates ember")
                .executes(ctx -> open(ctx.player(), ctx.getOr(crate, null))));
    }

    List<String> visibleIds() {
        return catalog.visible().stream().map(CrateCatalog.Crate::id).toList();
    }

    /** {@code /crates [crate]}: the crates menu, or straight to one crate's preview. */
    void open(Player player, CrateCatalog.Crate crate) {
        Optional<String> problem = problem(player);
        if (problem.isPresent()) {
            throw new CommandFailure(ID, problem.get());
        }
        CratesMenu home = new CratesMenu(this);
        if (crate == null) {
            core.menus().open(player, home);
        } else {
            PreviewMenu preview = new PreviewMenu(this, crate);
            preview.parent(home);
            core.menus().open(player, preview);
        }
    }

    /** Why this player can't use crates right now (message key), or empty. Main thread. */
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

    // ================================================================== profile views (menus)

    /** The cached keys/pity of a profile, or null while loading (call {@link #loadView} then). */
    ProfileView view(UUID profile) {
        return views.get(profile);
    }

    /** Loads keys and pity of a profile; completes on the main thread. */
    CompletableFuture<ProfileView> loadView(UUID profile) {
        return core.database().async(c -> new ProfileView(CrateStore.keys(c, profile), CrateStore.pities(c, profile)))
                .thenApplyAsync(v -> {
                    views.put(profile, v);
                    return v;
                }, main);
    }

    /** Re-reads a profile's keys after a change and refreshes an open crates menu of its player. */
    void changed(UUID profile) {
        if (!ready) {
            return;
        }
        loadView(profile).whenComplete((v, e) -> {
            if (e != null) {
                return;
            }
            profiles().onlinePlayer(profile).ifPresent(p -> {
                Menu open = core.menus().openMenu(p);
                if (open instanceof CratesMenu || open instanceof PreviewMenu) {
                    open.refresh();
                }
            });
        });
    }

    // ================================================================== tier, names, helpers

    /** The opener's stage tier: highest stage skill level → tier (the AFK pool's rule). Main thread. */
    int tier(Player p) {
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (skills.isEmpty()) {
            return 1;
        }
        int best = 0;
        for (String id : settings.stageSkills) {
            Optional<SkillType> type = SkillType.byId(id);
            if (type.isPresent() && skills.get().available(type.get())) {
                best = Math.max(best, skills.get().level(p, type.get()));
            }
        }
        return CrateMath.tierFor(best, tierLevels);
    }

    /** MiniMessage name of a crate in its rarity color, e.g. {@code <rarity:rare>Blaze Crate}. */
    String crateMini(CrateCatalog.Crate crate) {
        return "<rarity:" + crate.rarity() + ">" + raw("crates." + crate.id() + ".name", capitalize(crate.id()) + " Crate");
    }

    /** MiniMessage name of a crate's key. */
    String keyMini(String crateId) {
        String rarity = catalog.crate(crateId).map(CrateCatalog.Crate::rarity).orElse("common");
        return "<rarity:" + rarity + ">" + raw("crates." + crateId + ".key", capitalize(crateId) + " Key");
    }

    /**
     * MiniMessage name of a reward in its rarity color; {@code <amount>} in the name becomes {@code amountText}.
     * Name: {@code rewards.<crate>.<id>}, else {@code reward-types.<type>}.
     */
    String rewardMini(CrateCatalog.Crate crate, CrateCatalog.Reward reward, String amountText) {
        String name = raw("rewards." + crate.id() + "." + reward.id(), null);
        if (name == null) {
            name = raw("reward-types." + reward.type().key(), capitalize(reward.id().replace('_', ' ')));
        }
        name = name.replace("<amount>", LoreBuilder.escape(amountText == null ? "" : amountText));
        if (reward.type() == RewardType.KEY) {
            name = name.replace("<key>", raw("crates." + reward.crate() + ".key", capitalize(reward.crate()) + " Key"));
        }
        return "<rarity:" + reward.rarity() + ">" + name;
    }

    /** A MiniMessage text as a component for a viewer (style tags resolved). */
    Component mini(Player viewer, String mini) {
        return msg.component(viewer, "text", Msg.mm("text", mini));
    }

    /** The amount text of a reward as the viewer would get it now (preview). */
    String previewAmount(CrateCatalog.Reward reward, int tier) {
        return switch (reward.type()) {
            case MONEY -> format(Currency.MONEY, CrateMath.money(reward.minutes(), tier, refPerMinute));
            case CRYSTALS -> format(Currency.CRYSTALS, CrateMath.crystals(reward.base(), tier, settings.crystalGrowth));
            case SHARDS -> format(Currency.SHARDS, reward.amount());
            case FUEL -> durationText(reward.minutes());
            case COMING_SOON -> format(Currency.CRYSTALS, CrateMath.crystals(reward.fallback(), tier, settings.crystalGrowth));
            default -> String.valueOf(reward.amount());
        };
    }

    /** "1h", "3h", "45m" for fuel. */
    static String durationText(int minutes) {
        if (minutes >= 60 && minutes % 60 == 0) {
            return (minutes / 60) + "h";
        }
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    /** The amount a reward shows as {@code (xN)} in the preview line. */
    static int count(CrateCatalog.Reward reward) {
        return switch (reward.type()) {
            case MONEY, CRYSTALS, FUEL, COMING_SOON, TAG, TITLE -> 1;
            default -> reward.amount();
        };
    }

    String format(Currency currency, long amount) {
        return core.services().optional(EconomyService.class).map(e -> e.format(currency, amount))
                .orElse(String.valueOf(amount));
    }

    Material icon(CrateCatalog.Reward reward) {
        if (reward.icon() != null) {
            Material m = Material.matchMaterial(reward.icon());
            if (m != null && m.isItem() && !m.isAir()) {
                return m;
            }
        }
        return switch (reward.type()) {
            case MONEY -> Material.GOLD_NUGGET;
            case CRYSTALS -> Material.PRISMARINE_CRYSTALS;
            case SHARDS -> Material.AMETHYST_SHARD;
            case KEY -> Material.TRIPWIRE_HOOK;
            case ENCHANT_BOOK -> Material.ENCHANTED_BOOK;
            case SCROLL -> Material.PAPER;
            case SET_TOKEN -> Material.LEATHER_CHESTPLATE;
            case SPAWNER -> Material.SPAWNER;
            case ITEM -> {
                Material m = Material.matchMaterial(reward.material());
                yield m != null && m.isItem() && !m.isAir() ? m : Material.CHEST;
            }
            case TAG, TITLE -> Material.NAME_TAG;
            case FUEL -> Material.BLAZE_POWDER;
            case COMING_SOON -> Material.BARRIER;
        };
    }

    Material icon(CrateCatalog.Crate crate) {
        Material m = Material.matchMaterial(crate.icon());
        return m != null && m.isItem() && !m.isAir() ? m : Material.CHEST;
    }

    String raw(String key, String fallback) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? fallback : raw;
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    Optional<Cosmetics> cosmetics() {
        return core.services().optional(Cosmetics.class);
    }

    boolean bedrock(Player player) {
        return player != null && core.bedrock().isBedrock(player.getUniqueId());
    }

    boolean flag(Player player, String placeholder) {
        return player != null && core.placeholders().has(placeholder)
                && core.placeholders().text(player, placeholder).map("true"::equalsIgnoreCase).orElse(false);
    }

    void fail(CommandSender to, String key, TagResolver... placeholders) {
        msg.send(to, key, placeholders);
        if (to instanceof Player p) {
            core.sounds().play(p, "error");
        }
    }

    void audit(String action, Actor actor, UUID player, String name, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.CRATES, action, actor, player, name, amount, null, details);
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("crates:view", settings.joinDelayTicks, () -> {
            if (player.isOnline() && ready) {
                loadView(profiles().profileKey(player));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        views.remove(profiles().profileKey(event.getPlayer()));
    }

    /** The inventory is about to be swapped: close crate menus first. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProfileSwitch(ProfileSwitchEvent event) {
        if (isCratesMenu(core.menus().openMenu(event.getPlayer()))) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        if (ready) {
            loadView(profiles().profileKey(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        views.remove(profile);
        core.database().run(c -> CrateStore.deleteProfile(c, profile)).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Crates: could not remove the keys of deleted profile " + profile,
                        Database.cause(e));
            } else {
                audit("crate_profile_reset", Actor.system(ID), event.profile().owner(), null, null,
                        Map.of("profile", profile.toString()));
            }
        });
    }

    static boolean isCratesMenu(Menu menu) {
        if (menu instanceof CratesMenu || menu instanceof PreviewMenu || menu instanceof ResultMenu) {
            return true;
        }
        return menu != null && menu.parent() != null && isCratesMenu(menu.parent());
    }
}
