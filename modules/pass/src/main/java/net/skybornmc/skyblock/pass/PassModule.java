package net.skybornmc.skyblock.pass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.pass.PassRewards;
import net.skybornmc.skyblock.common.pass.PassXp;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Battle pass (PHASE-10-REWARDS.md M10.8; P10-24, P10-25, P10-26 with REVIEW3 R3-44, P10-28; staff tools P10-41).
 * <ul>
 *   <li><b>Pass XP</b> per profile and season through {@link PassXp} (quests, votes, the daily calendar and playtime already
 *       call it), at most once per event key, with the RX30 catch-up bonus while far behind the day line.</li>
 *   <li><b>Levels</b>: 100 x 800 XP, then an overflow reward every 2,000 XP.</li>
 *   <li><b>Free track</b> per profile: a small reward every level, keys every 5, boosters and titles at milestones.</li>
 *   <li><b>Premium track</b> per account: cosmetics only (titles, tags). Premium = the {@code skyblock.pass.premium}
 *       permission or a {@code pass_premium} row (staff now, the store in Phase 11). No extra reroll (R3-44).</li>
 *   <li><b>Claims</b>: click a tier or "Claim all" in /pass (or the /menu Rewards button); one transaction per claim.</li>
 * </ul>
 */
public final class PassModule implements SkyblockModule, Listener {

    static final String ID = "pass";
    static final String PERM_USE = "skyblock.pass.use";
    static final String PERM_PREMIUM = "skyblock.pass.premium";
    static final String PERM_ADMIN = "skyblock.pass.admin";
    static final String PLACEHOLDER_LEVEL = "pass_level";
    private static final Pattern SEASON = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int COUNT_LIMIT = 999;

    /** On / off for {@code /pass admin premium}. */
    enum Switch { ON, OFF }

    /** Cached pass state of a profile in a season (main thread). */
    static final class ProfileState {
        final UUID profile;
        final String season;
        long xp;
        final Set<Integer> freeClaimed = new HashSet<>();

        ProfileState(UUID profile, String season) {
            this.profile = profile;
            this.season = season;
        }
    }

    /** Cached premium state of an account in a season (main thread). */
    static final class AccountState {
        final String season;
        boolean premiumRow;
        final Set<Integer> premiumClaimed = new HashSet<>();

        AccountState(String season) {
            this.season = season;
        }
    }

    final Core core;
    final ModuleMessages msg;
    final PassSettings settings = new PassSettings();
    private final PassGrants grants;
    private Executor main;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private final Map<UUID, ProfileState> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, AccountState> accounts = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    // Parsed from the settings (replaced as a whole on reload).
    private volatile PassTable table = PassTable.EMPTY;
    private volatile PassMath.Curve curve = new PassMath.Curve(100, 800, 2_000);
    private volatile long[] refPerMinute = new long[0];
    private volatile int[] tierLevels = {0};
    private volatile ZoneId zone = Format.DEFAULT_ZONE;
    private volatile LocalDate seasonStart;
    private volatile String season = "s1";

    public PassModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.grants = new PassGrants(this);
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
        registerCommands();
        core.services().register(ID, PassXp.class, this::addXp);
        core.services().register(ID, PassRewards.class, new PassRewards() {
            @Override
            public int claimable(Player player) {
                return claimableTiers(player, COUNT_LIMIT).size();
            }

            @Override
            public CompletableFuture<Integer> claimAll(Player player, boolean announce) {
                return claim(player, claimableTiers(player, settings.maxPerClaim), announce);
            }
        });
        core.placeholders().registerText(ID, PLACEHOLDER_LEVEL, p -> {
            ProfileState s = state(p);
            return s == null ? "0" : Format.number(curve.level(s.xp));
        });
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
        long retry = 20L * settings.retrySeconds;
        // The first run also recovers hand-outs a crash interrupted (after every module, crates included, is up).
        tasks.add(core.scheduler().repeat("pass:grants", 200L, retry, grants::retry));
    }

    @Override
    public void reload() {
        String before = season;
        core.configs().load(settings);
        core.messages().load(ID);
        parse();
        if (!before.equals(season)) {
            // Season reset hook (Phase 12 sets season.id): everyone starts the new season at level 0.
            profiles.clear();
            accounts.clear();
            for (Player p : Bukkit.getOnlinePlayers()) {
                load(p);
            }
            audit("season_changed", Actor.SYSTEM, null, null, null, Map.of("from", before, "to", season));
        }
        refreshMenus();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof PassMenu) {
                p.closeInventory();
            }
        }
        core.placeholders().unregisterAll(ID);
    }

    private void parse() {
        List<String> problems = new ArrayList<>();
        String id = settings.seasonId == null ? "" : settings.seasonId.trim();
        if (!SEASON.matcher(id).matches()) {
            problems.add("pass.yml -> season.id '" + settings.seasonId + "' may only use letters, digits, - and _ (1-32); using 's1'");
            id = "s1";
        }
        season = id;
        curve = new PassMath.Curve(settings.maxLevel, settings.xpPerLevel, settings.overflowXp);
        table = PassTable.parse(settings.freeEveryLevel, settings.freeMilestones, settings.freeOverflow, settings.premiumMilestones,
                settings.maxLevel, problems::add);
        refPerMinute = longs(settings.refPerMinute, "pouch.ref-per-minute", problems);
        long[] levels = longs(settings.tierLevels, "tier.tier-levels", problems);
        int[] lv = new int[levels.length];
        for (int i = 0; i < levels.length; i++) {
            lv[i] = (int) Math.min(Integer.MAX_VALUE, levels[i]);
        }
        tierLevels = lv.length == 0 ? new int[]{0} : lv;
        ZoneId z = Format.parseZone(settings.zone);
        if (z == null) {
            problems.add("pass.yml -> zone '" + settings.zone + "' is not a time zone; using Europe/Oslo");
            z = Format.DEFAULT_ZONE;
        }
        zone = z;
        LocalDate start = null;
        if (settings.seasonStart != null && !settings.seasonStart.isBlank()) {
            try {
                start = LocalDate.parse(settings.seasonStart.trim());
            } catch (DateTimeParseException e) {
                problems.add("pass.yml -> season.start '" + settings.seasonStart + "' is not a date like 2026-11-01 (no catch-up)");
            }
        }
        seasonStart = start;
        defineCosmetics(problems);
        for (String problem : problems) {
            core.plugin().getLogger().warning("Pass: " + problem);
        }
    }

    private static long[] longs(List<String> raw, String where, List<String> problems) {
        List<Long> out = new ArrayList<>();
        for (String s : raw) {
            try {
                long v = Long.parseLong(s.trim().replace("_", "").replace(",", ""));
                if (v < 0) {
                    throw new NumberFormatException();
                }
                out.add(v);
            } catch (NumberFormatException e) {
                problems.add("pass.yml -> " + where + ": '" + s + "' is not a whole number (skipped)");
            }
        }
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    /** Titles of the tables exist in tags-titles.yml (added once; staff edits stay); missing tags are reported. */
    private void defineCosmetics(List<String> problems) {
        Optional<Cosmetics> cosmetics = cosmetics();
        if (cosmetics.isEmpty()) {
            return;
        }
        for (PassTable.Reward r : table.cosmetics()) {
            if (r.kind() == PassTable.Kind.TITLE) {
                String display = core.messages().raw(ID, "titles." + r.ref());
                Rarity rarity = Rarity.EPIC;
                if (!r.rarity().isEmpty()) {
                    try {
                        rarity = Rarity.valueOf(r.rarity().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        // the parser accepts only known rarities
                    }
                }
                cosmetics.get().defineTitle(r.ref(), display == null ? r.ref() : display, rarity, ID, false);
            } else if (r.kind() == PassTable.Kind.TAG && !cosmetics.get().ids(Cosmetics.Kind.TAG).contains(r.ref())) {
                problems.add("pass.yml: tag '" + r.ref() + "' is not in tags-titles.yml; that tier waits until it exists");
            }
        }
    }

    // ================================================================== commands

    private void registerCommands() {
        Arg<Player> player = Args.onlinePlayer("player");
        Arg<Long> amount = Args.amount("xp");
        Arg<Switch> toggle = Args.choice("state", Switch.class);
        core.commands().register(CommandSpec.command("pass")
                .description("The battle pass: levels, free and premium rewards")
                .category("Rewards")
                .permission(PERM_USE)
                .playerOnly()
                .example("pass")
                .executes(ctx -> {
                    if (!settings.enabled) {
                        throw new CommandFailure(ID, "disabled");
                    }
                    open(ctx.player(), null);
                })
                .sub(CommandSpec.sub("admin")
                        .description("Staff tools for the battle pass: view, give XP, reset XP, premium (P10-41)")
                        .category("Staff")
                        .permission(PERM_ADMIN)
                        .example("pass admin view Steve")
                        .sub(CommandSpec.sub("view")
                                .description("A player's pass level, XP, claims and premium on their active profile")
                                .permission(PERM_ADMIN)
                                .arg(player)
                                .example("pass admin view Steve")
                                .executes(ctx -> view(ctx.sender(), ctx.get(player))))
                        .sub(CommandSpec.sub("xp")
                                .description("Give pass XP to a player's active profile (no catch-up bonus)")
                                .permission(PERM_ADMIN)
                                .arg(player).arg(amount)
                                .example("pass admin xp Steve 800")
                                .executes(ctx -> giveXp(ctx.sender(), ctx.get(player), ctx.get(amount))))
                        .sub(CommandSpec.sub("reset")
                                .description("Set a player's pass XP on the active profile to 0 (claimed tiers stay claimed)")
                                .permission(PERM_ADMIN)
                                .arg(player)
                                .example("pass admin reset Steve")
                                .executes(ctx -> resetXp(ctx.sender(), ctx.get(player))))
                        .sub(CommandSpec.sub("premium")
                                .description("Turn the premium pass on or off for a player's account this season")
                                .permission(PERM_ADMIN)
                                .arg(player).arg(toggle)
                                .example("pass admin premium Steve on")
                                .executes(ctx -> premium(ctx.sender(), ctx.get(player), ctx.get(toggle) == Switch.ON)))));
    }

    void open(Player player, Menu parent) {
        load(player);
        PassMenu menu = new PassMenu(this);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
    }

    private void view(CommandSender sender, Player target) {
        ProfileState s = state(target);
        AccountState a = accounts.get(target.getUniqueId());
        if (s == null || a == null) {
            load(target);
            msg.send(sender, "admin.loading", Msg.p("player", target.getName()));
            return;
        }
        msg.send(sender, "admin.view", Msg.p("player", target.getName()), Msg.p("profile", profiles().activeName(target)),
                Msg.p("season", season), Msg.number("level", curve.level(s.xp)), Msg.number("xp", s.xp),
                Msg.number("free", s.freeClaimed.size()), Msg.number("premium-claimed", a.premiumClaimed.size()),
                Msg.p("premium", hasPremium(target) ? "yes" : "no"), Msg.number("ready", claimableTiers(target, COUNT_LIMIT).size()));
    }

    private void giveXp(CommandSender sender, Player target, long amount) {
        if (amount <= 0 || amount > settings.maxXpPerEvent) {
            msg.send(sender, "admin.xp-range", Msg.number("max", settings.maxXpPerEvent));
            return;
        }
        UUID profile = profileKey(target);
        store(profile, target.getUniqueId(), (int) amount, "staff", null, false, Actor.of(sender)).whenComplete((ok, e) ->
                core.scheduler().ensureMain("pass:admin-xp", () -> msg.send(sender, Boolean.TRUE.equals(ok) ? "admin.xp-given" : "error",
                        Msg.number("xp", amount), Msg.p("player", target.getName()))));
    }

    private void resetXp(CommandSender sender, Player target) {
        UUID profile = profileKey(target);
        String s = season;
        long now = System.currentTimeMillis();
        core.database().transaction(c -> {
            long before = PassStore.lockXp(c, profile, s);
            PassStore.setXp(c, profile, s, 0, now);
            return before;
        }).whenCompleteAsync((before, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Pass: reset failed", Database.cause(e));
                msg.send(sender, "error");
                return;
            }
            ProfileState st = profiles.get(profile);
            if (st != null && st.season.equals(s)) {
                st.xp = 0;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("season", s);
            d.put("profile", profile.toString());
            d.put("xp_before", before);
            audit("staff_reset", Actor.of(sender), target.getUniqueId(), target.getName(), before, d);
            msg.send(sender, "admin.reset", Msg.p("player", target.getName()));
            refreshMenus();
        }, main);
    }

    private void premium(CommandSender sender, Player target, boolean on) {
        UUID player = target.getUniqueId();
        String s = season;
        long now = System.currentTimeMillis();
        String by = "staff:" + Actor.of(sender).name();
        core.database().transaction(c -> PassStore.setPremium(c, player, s, on, by, now)).whenCompleteAsync((changed, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Pass: premium change failed", Database.cause(e));
                msg.send(sender, "error");
                return;
            }
            AccountState a = accounts.get(player);
            if (a != null && a.season.equals(s)) {
                a.premiumRow = on;
            }
            if (Boolean.TRUE.equals(changed)) {
                audit(on ? "premium_granted" : "premium_removed", Actor.of(sender), player, target.getName(), null,
                        Map.of("season", s, "source", "staff"));
            }
            msg.send(sender, on ? "admin.premium-on" : "admin.premium-off", Msg.p("player", target.getName()), Msg.p("season", s));
            if (on && Boolean.TRUE.equals(changed)) {
                msg.send(target, "premium.unlocked");
                sound(target, settings.levelUpSound);
            }
            refreshMenus();
        }, main);
    }

    // ================================================================== state

    void load(Player player) {
        UUID profile = profileKey(player);
        UUID account = player.getUniqueId();
        String s = season;
        ProfileState have = profiles.get(profile);
        AccountState acc = accounts.get(account);
        if (have != null && have.season.equals(s) && acc != null && acc.season.equals(s)) {
            return;
        }
        if (!loading.add(profile)) {
            return;
        }
        core.database().async(c -> {
            ProfileState ps = new ProfileState(profile, s);
            ps.xp = PassStore.xp(c, profile, s);
            ps.freeClaimed.addAll(PassStore.claimed(c, profile, s, PassTable.Track.FREE));
            AccountState as = new AccountState(s);
            as.premiumRow = PassStore.premium(c, account, s);
            as.premiumClaimed.addAll(PassStore.claimed(c, account, s, PassTable.Track.PREMIUM));
            return new Object[]{ps, as};
        }).whenCompleteAsync((r, e) -> {
            loading.remove(profile);
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Pass: could not load the pass of " + player.getName(), Database.cause(e));
                return;
            }
            if (!s.equals(season)) {
                return;
            }
            profiles.put(profile, (ProfileState) r[0]);
            accounts.put(account, (AccountState) r[1]);
            refreshMenu(player);
        }, main);
    }

    /** The cached state of the player's active profile this season, or null while loading. Main thread. */
    ProfileState state(Player player) {
        ProfileState s = profiles.get(profileKey(player));
        return s != null && s.season.equals(season) ? s : null;
    }

    AccountState account(Player player) {
        AccountState a = accounts.get(player.getUniqueId());
        return a != null && a.season.equals(season) ? a : null;
    }

    boolean loading(Player player) {
        return loading.contains(profileKey(player));
    }

    /** Premium this season: the permission (store or rank later) or a {@code pass_premium} row. Main thread. */
    boolean hasPremium(Player player) {
        if (player.hasPermission(PERM_PREMIUM)) {
            return true;
        }
        AccountState a = account(player);
        return a != null && a.premiumRow;
    }

    /** Claimable tiers of the player's active profile right now (free first). Main thread, cached state only. */
    List<PassRules.TierRef> claimableTiers(Player player, int limit) {
        if (!settings.enabled || !player.hasPermission(PERM_USE)) {
            return List.of();
        }
        ProfileState s = state(player);
        AccountState a = account(player);
        if (s == null || a == null) {
            return List.of();
        }
        return PassRules.claimable(s.xp, curve, table, s.freeClaimed, a.premiumClaimed, hasPremium(player), this::available, limit);
    }

    /** False for a reward that can't be given right now and would be lost (unknown crate, tag or booster size). */
    boolean available(PassTable.Reward r) {
        return switch (r.kind()) {
            case MONEY, CRYSTALS -> economy().isPresent();
            case KEY -> core.services().optional(CrateKeys.class).map(k -> k.exists(r.ref())).orElse(true);
            case BOOSTER -> BoosterService.of(core.services()).map(b -> {
                String[] p = r.ref().split(":");
                Optional<BoosterType> type = BoosterType.byId(p[0]);
                return type.isPresent() && b.valid(type.get(), Integer.parseInt(p[1]), Integer.parseInt(p[2]), false);
            }).orElse(true);
            case TITLE -> cosmetics().map(c -> c.ids(Cosmetics.Kind.TITLE).contains(r.ref())).orElse(false);
            case TAG -> cosmetics().map(c -> c.ids(Cosmetics.Kind.TAG).contains(r.ref())).orElse(false);
        };
    }

    // ================================================================== XP (PassXp)

    /** {@link PassXp#addXp}: stored once per event key; the catch-up bonus is decided in the transaction. */
    CompletableFuture<Boolean> addXp(UUID profile, int xp, String source, String eventKey) {
        if (!settings.enabled || profile == null || xp <= 0) {
            return CompletableFuture.completedFuture(false); // the caller keeps it and tries again later
        }
        UUID owner = profiles().ownerOf(profile);
        return store(profile, owner, xp, source == null ? "unknown" : source, eventKey, true, Actor.system(ID));
    }

    private CompletableFuture<Boolean> store(UUID profile, UUID player, int xp, String source, String eventKey, boolean catchUp, Actor actor) {
        String s = season;
        PassMath.Curve c0 = curve;
        int day = catchUp ? PassMath.seasonDay(seasonStart, LocalDate.now(zone), settings.seasonDays) : 0;
        boolean on = catchUp && settings.catchUpEnabled;
        double perDay = settings.catchUpLevelsPerDay;
        int behind = settings.catchUpBehind;
        double mult = settings.catchUpMultiplier;
        int cap = catchUp ? settings.maxXpPerEvent : Integer.MAX_VALUE;
        String key = eventKey == null ? null : PassStore.cut(eventKey, 96);
        long now = System.currentTimeMillis();
        return core.database().transaction(c -> PassStore.addXp(c, profile, s, key, source, before ->
                        PassMath.applied(xp, PassMath.catchUp(on, day, c0.level(before), c0.max(), perDay, behind, mult), cap), now))
                .thenApplyAsync(r -> {
                    if (!r.duplicate()) {
                        applied(profile, player, s, r, source, actor);
                    }
                    return true;
                }, main)
                .exceptionally(e -> {
                    core.plugin().getLogger().log(Level.WARNING, "Pass: could not store " + xp + " pass XP (" + source + ")", Database.cause(e));
                    return false;
                });
    }

    /** After a committed XP add (main thread): cache, audit, level-up line, auto-claim. */
    private void applied(UUID profile, UUID player, String s, PassStore.XpResult r, String source, Actor actor) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("season", s);
        d.put("profile", profile.toString());
        d.put("source", source);
        d.put("xp_after", r.after());
        audit("xp", actor, player, null, (long) r.added(), d);
        ProfileState st = profiles.get(profile);
        if (st != null && st.season.equals(s)) {
            st.xp = Math.max(st.xp, r.after());
        }
        int from = curve.tier(r.before());
        int to = curve.tier(r.after());
        Player online = receiver(profile);
        if (to > from && online != null) {
            int level = curve.level(r.after());
            boolean overflow = to > curve.max();
            msg.send(online, overflow ? "level-up.overflow" : "level-up.line", Msg.number("level", level),
                    Msg.number("ready", claimableTiers(online, COUNT_LIMIT).size()));
            sound(online, settings.levelUpSound);
            if (settings.autoClaim) {
                List<PassRules.TierRef> tiers = claimableTiers(online, settings.maxPerClaim);
                if (!tiers.isEmpty()) {
                    claim(online, tiers, true);
                }
            }
        }
        refreshMenus();
    }

    // ================================================================== claims

    /**
     * Claims tiers for the player's active profile (still claimable ones only). Completes on the main thread with the number
     * of tiers claimed (0 = nothing ready, -1 = failed with a message).
     */
    CompletableFuture<Integer> claim(Player player, List<PassRules.TierRef> requested, boolean announce) {
        if (!settings.enabled) {
            fail(player, "disabled");
            return CompletableFuture.completedFuture(-1);
        }
        ProfileState s = state(player);
        AccountState a = account(player);
        if (s == null || a == null) {
            load(player);
            fail(player, "loading");
            return CompletableFuture.completedFuture(-1);
        }
        Set<PassRules.TierRef> now = new HashSet<>(claimableTiers(player, Integer.MAX_VALUE));
        List<PassRules.TierRef> tiers = new ArrayList<>();
        for (PassRules.TierRef t : requested) {
            if (now.contains(t) && tiers.size() < settings.maxPerClaim) {
                tiers.add(t);
            }
        }
        if (tiers.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        boolean items = tiers.stream().anyMatch(t -> table.rewards(t.track(), t.tier(), curve.max()).stream()
                .anyMatch(r -> r.kind() == PassTable.Kind.BOOSTER));
        if (items && ItemBox.refuses(core.services(), player)) {
            sound(player, "error");
            return CompletableFuture.completedFuture(-1);
        }
        UUID account = player.getUniqueId();
        if (!busy.add(account)) {
            fail(player, "busy");
            return CompletableFuture.completedFuture(-1);
        }
        String seasonNow = season;
        PassClaims.Plan plan = PassClaims.plan(new PassClaims.Claimer(s.profile, account, seasonNow, tier(player), hasPremium(player)),
                tiers, table, curve, refPerMinute, settings.crystalGrowth, Actor.player(player), System.currentTimeMillis());
        CompletableFuture<Integer> out = new CompletableFuture<>();
        PassClaims.commit(PassClaims.runner(core.database(), economy()), plan).whenCompleteAsync((status, error) -> {
            busy.remove(account);
            PassClaims.Status st = error != null || status == null ? PassClaims.Status.ERROR : status;
            switch (st) {
                case OK -> {
                    for (PassRules.TierRef t : plan.tiers()) {
                        (t.track() == PassTable.Track.FREE ? s.freeClaimed : a.premiumClaimed).add(t.tier());
                    }
                    grants.handOut(plan.grants());
                    auditClaim(player, s.profile, seasonNow, plan);
                    if (announce && player.isOnline()) {
                        announce(player, plan);
                    }
                    sound(player, "success");
                    refreshMenus();
                    out.complete(plan.tiers().size());
                }
                case CHANGED -> {
                    profiles.remove(s.profile);
                    accounts.remove(account);
                    load(player);
                    fail(player, "changed");
                    out.complete(-1);
                }
                case ERROR -> {
                    fail(player, "error");
                    out.complete(-1);
                }
            }
        }, main);
        return out;
    }

    private void auditClaim(Player player, UUID profile, String s, PassClaims.Plan plan) {
        List<String> free = new ArrayList<>();
        List<String> premium = new ArrayList<>();
        for (PassRules.TierRef t : plan.tiers()) {
            (t.track() == PassTable.Track.FREE ? free : premium).add(String.valueOf(t.tier()));
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("season", s);
        d.put("profile", profile.toString());
        d.put("free", String.join(",", free));
        d.put("premium", String.join(",", premium));
        d.put("money", plan.summary().money());
        d.put("crystals", plan.summary().crystals());
        d.put("keys", plan.summary().keys().toString());
        d.put("boosters", plan.summary().boosters());
        d.put("cosmetics", plan.summary().cosmetics().stream().map(PassTable.Reward::ref).toList().toString());
        audit("claim", Actor.player(player), player.getUniqueId(), player.getName(), plan.summary().money(), d);
    }

    private void announce(Player player, PassClaims.Plan plan) {
        PassClaims.Summary sum = plan.summary();
        msg.send(player, "claim.header", Msg.number("count", plan.tiers().size()));
        for (Component line : summaryLines(player, sum)) {
            player.sendMessage(line);
        }
    }

    /** One line per reward kind of a claim (chat). */
    List<Component> summaryLines(Player viewer, PassClaims.Summary sum) {
        List<Component> lines = new ArrayList<>();
        if (sum.money() > 0) {
            lines.add(msg.component(viewer, "reward.money", Msg.p("amount", money(sum.money()))));
        }
        if (sum.crystals() > 0) {
            lines.add(msg.component(viewer, "reward.crystals", Msg.p("amount", crystals(sum.crystals()))));
        }
        for (Map.Entry<String, Long> k : sum.keys().entrySet()) {
            lines.add(msg.component(viewer, "reward.key", Msg.number("amount", k.getValue()), Msg.p("crate", crateName(k.getKey()))));
        }
        if (sum.boosters() > 0) {
            lines.add(msg.component(viewer, "reward.boosters", Msg.number("amount", sum.boosters())));
        }
        for (PassTable.Reward r : sum.cosmetics()) {
            lines.add(msg.component(viewer, r.kind() == PassTable.Kind.TITLE ? "reward.title" : "reward.tag",
                    Msg.c("name", cosmeticName(viewer, r))));
        }
        return lines;
    }

    // ================================================================== helpers

    /** The player's stage tier: the highest level of the stage skills (same rule as crates, quests and the calendar). */
    int tier(Player player) {
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (skills.isEmpty()) {
            return 1;
        }
        int best = 0;
        for (String id : settings.stageSkills) {
            Optional<SkillType> type = SkillType.byId(id);
            if (type.isPresent() && skills.get().available(type.get())) {
                best = Math.max(best, skills.get().level(player, type.get()));
            }
        }
        return PassMath.tierFor(best, tierLevels);
    }

    String money(long amount) {
        return economy().map(e -> e.format(Currency.MONEY, amount)).orElse("$" + Format.number(amount));
    }

    String crystals(long amount) {
        return economy().map(e -> e.format(Currency.CRYSTALS, amount)).orElse(Format.number(amount));
    }

    String crateName(String crate) {
        String raw = core.messages().raw(ID, "crates." + crate);
        return raw == null ? crate : raw;
    }

    Component cosmeticName(Player viewer, PassTable.Reward r) {
        Cosmetics.Kind kind = r.kind() == PassTable.Kind.TITLE ? Cosmetics.Kind.TITLE : Cosmetics.Kind.TAG;
        return cosmetics().map(c -> c.display(viewer, kind, r.ref())).orElse(Component.text(r.ref()));
    }

    PassTable table() {
        return table;
    }

    PassMath.Curve curve() {
        return curve;
    }

    String season() {
        return season;
    }

    long[] refPerMinute() {
        return refPerMinute;
    }

    /** Days left in the season (0 = no start date set or over). */
    int daysLeft() {
        return PassMath.daysLeft(seasonStart, LocalDate.now(zone), settings.seasonDays);
    }

    /** Catch-up is active for this XP right now. */
    boolean catchUpActive(long xp) {
        int day = PassMath.seasonDay(seasonStart, LocalDate.now(zone), settings.seasonDays);
        return PassMath.catchUp(settings.catchUpEnabled, day, curve.level(xp), curve.max(), settings.catchUpLevelsPerDay,
                settings.catchUpBehind, settings.catchUpMultiplier) > 1;
    }

    Executor main() {
        return main;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    UUID profileKey(Player player) {
        return profiles().profileKey(player);
    }

    Optional<EconomyService> economy() {
        return core.services().optional(EconomyService.class);
    }

    Optional<Cosmetics> cosmetics() {
        return core.services().optional(Cosmetics.class);
    }

    /** The online player who is on this profile right now, or null. */
    Player receiver(UUID profile) {
        return profiles().onlinePlayer(profile).filter(p -> profiles().profileKey(p).equals(profile)).orElse(null);
    }

    void fail(Player player, String key, TagResolver... placeholders) {
        msg.send(player, key, placeholders);
        sound(player, "error");
    }

    void sound(Player player, String key) {
        if (player != null && key != null && !key.isBlank()) {
            core.sounds().play(player, key);
        }
    }

    void audit(String action, Actor actor, UUID player, String name, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.PASS, action, actor, player, name, amount, null, details);
    }

    void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshMenu(p);
        }
    }

    private void refreshMenu(Player p) {
        if (core.menus().openMenu(p) instanceof PassMenu menu) {
            menu.refresh();
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // The profile loads first (same delay as quests and the daily reminder).
        core.scheduler().later("pass:join", 40L, () -> {
            if (p.isOnline()) {
                load(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID account = event.getPlayer().getUniqueId();
        accounts.remove(account);
        profiles.values().removeIf(s -> account.equals(profiles().ownerOf(s.profile)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        profiles.remove(profile);
        core.database().transaction(c -> {
            PassStore.deleteProfile(c, profile);
            return true;
        }).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Pass: could not remove the pass of profile " + profile, Database.cause(e));
            } else {
                audit("profile_reset", Actor.SYSTEM, event.profile().owner(), null, null, Map.of("profile", profile.toString()));
            }
        });
    }
}
