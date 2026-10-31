package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.util.Format;
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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Rewards (PHASE-10-REWARDS.md M10.6; voting M10.5 joins this module later).
 * <ul>
 *   <li><b>Daily calendar</b> per profile (owner 128): {@code /daily}, the Daily Rewards NPC. One claim per day (reset
 *       00:00 Europe/Oslo); the calendar moves only on a claim; streak gems every 7 days, titles at 30 and 100.</li>
 *   <li><b>Playtime</b> per profile (R6): active time only (not AFK, not in the AFK pool); hourly Crystal Pouch S (+ pass
 *       XP once the pass exists), a bound Cinder key every 10 hours, titles at 50/100/250/500 hours.</li>
 *   <li>Every claim is one transaction with an idempotency key ({@link ClaimEngine}); keys, items, fuel and titles are
 *       handed out after the commit ({@link Grants}): items that don't fit go to {@code /box}; a crash can lose a
 *       hand-out (staff review) but never double it.</li>
 * </ul>
 */
public final class RewardsModule implements SkyblockModule, Listener {

    static final String ID = "rewards";
    static final String PERM_DAILY = "skyblock.rewards.daily";
    static final String PERM_PLAYTIME = "skyblock.rewards.playtime";
    static final String SETTING_REMINDER = "daily-reminder";

    /** Title rarities in threshold order (lowest first); the last one repeats. */
    private static final Rarity[] TITLE_RARITIES = {Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY};

    final Core core;
    final ModuleMessages msg;
    final RewardsSettings settings = new RewardsSettings();
    private final Grants grants;
    private final DailyService daily;
    private final PlaytimeService playtime;
    private final ActivityTracker activity;
    private Executor main;
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    // Parsed from the settings (replaced as a whole on reload).
    private volatile List<RewardSpec> calendar = List.of();
    private volatile NavigableMap<Integer, String> streakTitles = new TreeMap<>();
    private volatile NavigableMap<Integer, String> playtimeTitles = new TreeMap<>();
    private volatile long[] refPerMinute = new long[0];
    private volatile int[] tierLevels = {0};
    private volatile ZoneId zone = Format.DEFAULT_ZONE;

    public RewardsModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.grants = new Grants(this);
        this.daily = new DailyService(this);
        this.activity = new ActivityTracker();
        this.playtime = new PlaytimeService(this, activity);
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
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.register(new SettingToggle(ID, SETTING_REMINDER,
                SettingCategory.CHAT, Material.CLOCK, true, PERM_DAILY, ID, "settings.daily-reminder", false)));
        registerCommands();
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        Bukkit.getPluginManager().registerEvents(activity, core.plugin());
        for (Player p : Bukkit.getOnlinePlayers()) {
            activity.touch(p.getUniqueId());
            daily.load(p);
        }
        // PERF6: every tick, each player in their own slot once per playtime.tick-seconds (see PlaytimeService#tick).
        tasks.add(core.scheduler().repeat("rewards:playtime-tick", 1L, 1L, playtime::tick));
        long save = 20L * settings.saveSeconds;
        tasks.add(core.scheduler().repeat("rewards:playtime-save", save, save, playtime::flushAll));
        long retry = 20L * settings.retrySeconds;
        // The first run also recovers hand-outs a crash interrupted (after every module, crates included, is up).
        tasks.add(core.scheduler().repeat("rewards:grants", 200L, retry, grants::retry));
        grants.recoverLater();
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
        HandlerList.unregisterAll(activity);
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof DailyMenu) {
                p.closeInventory();
            }
        }
        playtime.flushAllBlocking();
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
    }

    private void parse() {
        List<String> problems = new ArrayList<>();
        calendar = RewardTables.calendar(settings.days, RewardTables.filler(settings.defaultFallback), problems::add);
        streakTitles = RewardTables.thresholds(settings.streakTitles, "daily.yml -> streak.titles", problems::add);
        playtimeTitles = RewardTables.thresholds(settings.playtimeTitles, "daily.yml -> playtime.titles", problems::add);
        long[] ref = RewardTables.longs(settings.refPerMinute, "daily.yml -> pouch.ref-per-minute", problems::add);
        if (ref.length == 0) {
            problems.add("daily.yml -> pouch.ref-per-minute is empty: money rewards pay nothing");
        }
        refPerMinute = ref;
        int[] levels = RewardTables.levels(settings.tierLevels, "daily.yml -> tier.tier-levels", problems::add);
        tierLevels = levels.length == 0 ? new int[]{0} : levels;
        ZoneId z = Format.parseZone(settings.zone);
        if (z == null) {
            problems.add("daily.yml -> calendar.zone '" + settings.zone + "' is not a time zone; using Europe/Oslo");
            z = Format.DEFAULT_ZONE;
        }
        zone = z;
        for (String p : problems) {
            core.plugin().getLogger().warning("Rewards: " + p);
        }
    }

    /** Streak and playtime titles exist in tags-titles.yml (added once; staff edits stay). */
    private void defineTitles() {
        Optional<Cosmetics> cosmetics = cosmetics();
        if (cosmetics.isEmpty()) {
            return;
        }
        for (NavigableMap<Integer, String> titles : List.of(streakTitles, playtimeTitles)) {
            int i = 0;
            for (String id : titles.values()) {
                String display = core.messages().raw(ID, "titles." + id);
                cosmetics.get().defineTitle(id, display == null ? id : display, TITLE_RARITIES[Math.min(i, TITLE_RARITIES.length - 1)],
                        "rewards", false);
                i++;
            }
        }
    }

    // ================================================================== commands

    private void registerCommands() {
        core.commands().register(CommandSpec.command("daily")
                .description("Open the daily reward calendar, your streak and your playtime rewards")
                .category("Rewards")
                .permission(PERM_DAILY)
                .playerOnly()
                .example("daily")
                .executes(ctx -> {
                    if (!settings.calendarEnabled) {
                        throw new CommandFailure(ID, "daily.disabled");
                    }
                    openDaily(ctx.player());
                }));
    }

    void openDaily(Player player) {
        daily.load(player);
        core.menus().open(player, new DailyMenu(this));
    }

    void openPlaytime(Player player) {
        core.menus().open(player, new DailyMenu(this, true));
    }

    // ================================================================== accessors

    Executor main() {
        return main;
    }

    Grants grants() {
        return grants;
    }

    DailyService daily() {
        return daily;
    }

    PlaytimeService playtime() {
        return playtime;
    }

    List<RewardSpec> calendar() {
        return calendar;
    }

    NavigableMap<Integer, String> streakTitles() {
        return streakTitles;
    }

    NavigableMap<Integer, String> playtimeTitles() {
        return playtimeTitles;
    }

    ZoneId zone() {
        return zone;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    Optional<Cosmetics> cosmetics() {
        return core.services().optional(Cosmetics.class);
    }

    Optional<EconomyService> economy() {
        return core.services().optional(EconomyService.class);
    }

    ClaimEngine.Runner runner() {
        return ClaimEngine.runner(core.database(), economy());
    }

    /** The online player who is on this profile right now, or null. */
    Player receiver(UUID profile) {
        return profiles().onlinePlayer(profile).filter(p -> profiles().profileKey(p).equals(profile)).orElse(null);
    }

    String raw(String key, String fallback) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? fallback : raw;
    }

    boolean bedrock(Player player) {
        return player != null && core.bedrock().isBedrock(player.getUniqueId());
    }

    boolean flag(Player player, String placeholder) {
        return player != null && core.placeholders().has(placeholder)
                && core.placeholders().text(player, placeholder).map("true"::equalsIgnoreCase).orElse(false);
    }

    void sound(Player p, String key) {
        if (p != null && key != null && !key.isBlank()) {
            core.sounds().play(p, key);
        }
    }

    void audit(String action, Actor actor, UUID player, String name, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.REWARDS, action, actor, player, name, amount, null, details);
    }

    /** Why this player can't claim now (message key), or empty. */
    Optional<String> problem(Player player) {
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

    /** The player's stage tier: the highest level of the stage skills (same rule as crates and the AFK pool). */
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
        return RewardTables.tierFor(best, tierLevels);
    }

    /** A payout snapshot for this player and profile: tier and what other modules can give now. Main thread. */
    Payout.Context context(int tier, UUID profile) {
        Optional<CrateKeys> keys = core.services().optional(CrateKeys.class);
        Optional<BoosterService> boosters = BoosterService.of(core.services());
        Optional<EnchantService> enchants = core.services().optional(EnchantService.class);
        boolean fuel = fuelSupported() && islands().flatMap(i -> i.islandOfProfile(profile)).isPresent();
        Map<String, List<String>> books = new HashMap<>();
        enchants.ifPresent(e -> {
            for (String id : e.ids()) {
                if (e.enabled(id)) {
                    books.computeIfAbsent(e.rarity(id).id(), k -> new ArrayList<>()).add(id);
                }
            }
        });
        return new Payout.Context(tier, refPerMinute, settings.crystalGrowth, settings.defaultFallback,
                spec -> switch (spec.kind()) {
                    case KEY -> keys.isPresent() && keys.get().exists(spec.ref());
                    case FUEL -> fuel;
                    case BOOSTER -> boosters.isPresent() && BoosterType.byId(spec.ref())
                            .map(t -> spec.amount() <= Integer.MAX_VALUE
                                    && boosters.get().valid(t, (int) spec.amount(), spec.minutes(), false)).orElse(false);
                    case BOOK, SCROLL -> enchants.isPresent();
                    case TOME -> false; // Wardrobe Tomes are not built yet: the fallback pays
                    default -> true;
                },
                rarity -> books.getOrDefault(rarity.toLowerCase(Locale.ROOT), List.of()), ThreadLocalRandom.current());
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** True when the islands module really implements Island Fuel (not the "no fuel yet" default), like crates. */
    boolean fuelSupported() {
        return islands().map(s -> {
            try {
                return s.getClass().getMethod("addIslandFuel", UUID.class, long.class, String.class, Actor.class)
                        .getDeclaringClass() != IslandService.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }).orElse(false);
    }

    boolean wantsReminder(Player p) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(p, SETTING_REMINDER)).orElse(true);
    }

    void refreshMenus(UUID profile) {
        Player p = receiver(profile);
        if (p != null) {
            if (core.menus().openMenu(p) instanceof DailyMenu menu) {
                menu.refresh();
            }
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        activity.touch(p.getUniqueId());
        core.scheduler().later("rewards:join", Math.max(1, settings.reminderDelayTicks), () -> {
            if (p.isOnline()) {
                daily.joined(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        playtime.quit(p);
        daily.quit(p);
        activity.forget(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        daily.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        playtime.forget(profile);
        daily.forget(profile);
        core.database().transaction(c -> {
            RewardStore.deleteProfile(c, profile);
            return true;
        }).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Rewards: could not remove the rewards of deleted profile " + profile, e);
            } else {
                audit("profile_reset", Actor.SYSTEM, event.profile().owner(), null, 0L, Map.of("profile", profile.toString()));
            }
        });
    }

    /** The Daily Rewards NPC opens /daily (sneak-click keeps its help text). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpc(NpcClickEvent event) {
        if (event.help() || event.role() == null || !settings.calendarEnabled
                || !event.role().equalsIgnoreCase(settings.npcRole.strip())) {
            return;
        }
        Player p = event.getPlayer();
        if (!p.hasPermission(PERM_DAILY)) {
            return;
        }
        event.setCancelled(true);
        sound(p, "click");
        openDaily(p);
    }
}
