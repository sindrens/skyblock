package net.skybornmc.skyblock.afk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.afk.AfkPool;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The AFK pool (O10, RW3): a small box at spawn where standing still pays. Every {@code time.reward-minutes} of
 * credited time: a little money (grows with the session, scaled to the player's stage so it stays at most ~5% of an
 * active player's income, ECONOMY_BALANCE §7.5) and island fuel; crate keys at daily milestones; a small chance of a
 * banked personal sell booster (kept by the boosters module since M10.2; it starts when the player leaves the pool).
 * Max {@code time.daily-cap-minutes} per account per day (reset at midnight Oslo time),
 * one earning account per IP address. No command: status is on the action bar and the scoreboard
 * ({@code afk_line}, {@code afk_pool}, {@code afk_today} placeholders).
 *
 * <p>Settings: {@code afk.yml}; texts: {@code messages/afk.yml}; data: {@code db/afk}.</p>
 */
public final class AfkModule implements SkyblockModule {

    static final String ID = "afk";
    static final String PERM_EARN = "skyblock.afk";

    private final Core core;
    private final AfkConfig config = new AfkConfig();
    private final AfkStore store = new AfkStore();
    private volatile AfkRules rules;
    private ModuleMessages msg;
    private AfkTracker tracker;
    private volatile boolean fuelSupported;

    public AfkModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        loadRules();
        tracker = new AfkTracker(this);
        AfkTracker holder = tracker;
        // M10.2: banked sell boosters wait while the player is inside the pool (never wasted while AFK).
        BoosterService.of(core.services()).ifPresent(b -> {
            b.hold(ID, holder::holdsBoosters);
            moveLegacyBoosters(b);
        });
        Bukkit.getPluginManager().registerEvents(tracker, core.plugin());
        // M10.6: playtime rewards count only active time outside the pool.
        core.services().register(ID, AfkPool.class, holder::holdsBoosters);
        core.placeholders().register(ID, "afk_pool", Placeholder.text(p -> tracker.poolLine(p)));
        core.placeholders().register(ID, "afk_today", Placeholder.text(p -> tracker.todayText(p)));
        core.placeholders().register(ID, "afk_line", p -> {
            String status = tracker.poolLine(p);
            return status.isEmpty() ? Component.empty() : msg.component(p, "scoreboard.line", Msg.p("status", status),
                    Msg.p("today", tracker.todayText(p)));
        });
        // Services published by modules enabled after this one (crates later) are looked up when used. PERF3: started
        // right here instead of one tick later; it only reads the area from the worlds module's build points (loaded
        // before this module) and starts the timers, so the separate first-tick task ("afk-start") is gone.
        tracker.start();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        loadRules();
        if (tracker != null) {
            tracker.schedule();
        }
    }

    @Override
    public void disable() {
        if (tracker == null) {
            return;
        }
        HandlerList.unregisterAll(tracker);
        tracker.shutdown();
        BoosterService.of(core.services()).ifPresent(b -> b.release(ID));
        core.placeholders().unregisterAll(ID);
        tracker = null;
    }

    /**
     * Moves sell boosters from the old {@code afk_boosters} table into the boosters module's bank, once (banked time plus
     * what was left of a running one; each row is moved and deleted in one transaction). They start when the player is
     * outside the pool, as before.
     */
    private void moveLegacyBoosters(BoosterService boosters) {
        long now = System.currentTimeMillis();
        core.database().transaction(c -> {
            List<long[]> moved = new ArrayList<>();
            List<UUID> profiles = new ArrayList<>();
            for (AfkStore.LegacyBooster b : store.legacyBoosters(c)) {
                long left = b.left(now);
                if (left > 0) {
                    long after = boosters.bank(c, b.profileKey(), BoosterType.SELL, b.percent(), left, 0, "afk_pool_migrated");
                    moved.add(new long[]{b.percent(), left, after});
                    profiles.add(b.profileKey());
                }
                store.deleteLegacyBooster(c, b.profileKey());
            }
            return java.util.Map.entry(profiles, moved);
        }).whenComplete((r, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "AFK pool: could not move old sell boosters to the boosters module "
                        + "(tried again at the next start)", error);
                return;
            }
            for (int i = 0; i < r.getKey().size(); i++) {
                long[] v = r.getValue().get(i);
                boosters.banked(r.getKey().get(i), BoosterType.SELL, (int) v[0], v[1], v[2], "afk_pool_migrated", Actor.SYSTEM);
            }
            if (!r.getKey().isEmpty()) {
                core.plugin().getLogger().info("AFK pool: moved " + r.getKey().size() + " sell booster(s) to the boosters module.");
            }
        });
    }

    private void loadRules() {
        AfkRules parsed = AfkRules.from(config);
        parsed.problems().forEach(core.plugin().getLogger()::warning);
        rules = parsed;
        fuelSupported = detectFuel();
    }

    /** True when the islands module really implements Island Fuel (not just the default "no fuel yet" hook). */
    private boolean detectFuel() {
        return core.services().optional(IslandService.class).map(s -> {
            try {
                return s.getClass().getMethod("addIslandFuel", UUID.class, long.class, String.class, Actor.class)
                        .getDeclaringClass() != IslandService.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }).orElse(false);
    }

    // ------------------------------------------------------------------ shared helpers

    Core core() {
        return core;
    }

    AfkConfig config() {
        return config;
    }

    AfkRules rules() {
        return rules;
    }

    AfkStore store() {
        return store;
    }

    ModuleMessages msg() {
        return msg;
    }

    boolean fuelSupported() {
        if (!fuelSupported) {
            fuelSupported = detectFuel();
        }
        return fuelSupported;
    }

    /** Display name of a crate ({@code crate-names.<id>} in messages/afk.yml, else the id capitalized). MiniMessage. */
    String crateName(String crateId) {
        String raw = core.messages().raw(ID, "crate-names." + crateId);
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        String id = crateId.replace('_', ' ');
        return "<c:value>" + id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1) + " Key";
    }

    /** A message as plain text (scoreboard values are inserted as text). */
    String plain(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? "" : MiniMessage.miniMessage().stripTags(raw);
    }
}
