package net.skybornmc.skyblock.afk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.Locale;
import java.util.UUID;

/**
 * The AFK pool (O10, RW3): a small box at spawn where standing still pays. Every {@code time.reward-minutes} of
 * credited time: a little money (grows with the session, scaled to the player's stage so it stays at most ~5% of an
 * active player's income, ECONOMY_BALANCE §7.5) and island fuel; crate keys at daily milestones; a small chance of a
 * banked personal sell booster. Max {@code time.daily-cap-minutes} per account per day (reset at midnight Oslo time),
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
    private AfkBoosters boosters;
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
        boosters = new AfkBoosters(this);
        core.services().optional(BonusService.class).ifPresent(b -> b.register(ID, boosters));
        tracker = new AfkTracker(this);
        Bukkit.getPluginManager().registerEvents(tracker, core.plugin());
        core.placeholders().register(ID, "afk_pool", Placeholder.text(p -> tracker.poolLine(p)));
        core.placeholders().register(ID, "afk_today", Placeholder.text(p -> tracker.todayText(p)));
        core.placeholders().register(ID, "afk_line", p -> {
            String status = tracker.poolLine(p);
            return status.isEmpty() ? Component.empty() : msg.component(p, "scoreboard.line", Msg.p("status", status),
                    Msg.p("today", tracker.todayText(p)));
        });
        // Services published by modules enabled after this one (crates later) are looked up when used.
        core.scheduler().later("afk-start", 1, tracker::start);
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
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister(ID));
        boosters.clear();
        core.placeholders().unregisterAll(ID);
        tracker = null;
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

    AfkBoosters boosters() {
        return boosters;
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
