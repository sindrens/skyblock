package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The "Honorable" title (spec 14.11). Checked on join (after {@code join-delay}) and every {@code check} (6h) for online
 * players, never per tick; a new warn, mute, ban or kick hides an earned title at once. Granted and taken account-wide
 * through {@link Cosmetics}; the title is marked automatic, so staff can't give or take it. One message + sound when it
 * unlocks (the cosmetics module's own "new title" line is suppressed), at most one line when it is lost.
 */
final class CleanStreakService {

    private final Core core;
    private final ModuleMessages msg;
    private final CleanStreakStore store;
    private final Supplier<PunishConfig.Extras> extras;
    private Scheduler.Task task;
    private volatile long lastRound;
    private volatile String definedTitle;

    CleanStreakService(Core core, ModuleMessages msg, CleanStreakStore store, Supplier<PunishConfig.Extras> extras) {
        this.core = core;
        this.msg = msg;
        this.store = store;
        this.extras = extras;
    }

    private CleanStreak.Settings settings() {
        return extras.get().cleanStreak();
    }

    private ZoneId zone() {
        return extras.get().statsZone();
    }

    private Optional<Cosmetics> cosmetics() {
        return core.services().optional(Cosmetics.class);
    }

    /** Main thread, at enable. The cosmetics module enables after moderation, so the title is defined one tick later. */
    void start() {
        core.scheduler().later("moderation:clean-streak-title", 1L, this::ensureTitle);
        if (task == null) {
            lastRound = System.currentTimeMillis();
            // a light async tick; the real round runs every extras.clean-streak.check (re-read, so reloads apply)
            task = core.scheduler().asyncRepeat(20L * 60, 20L * 60 * 10, this::maybeRound);
        }
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Adds the title to tags-titles.yml when it is missing (texts from messages) and marks it automatic. Main thread. */
    void ensureTitle() {
        CleanStreak.Settings s = settings();
        Optional<Cosmetics> c = cosmetics();
        if (c.isEmpty() || !s.enabled()) {
            return;
        }
        String id = s.title();
        if (!c.get().ids(Cosmetics.Kind.TITLE).contains(id)) {
            c.get().defineTitle(id, core.messages().raw(ModerationModule.ID, "clean-streak.title.display"),
                    net.skybornmc.skyblock.common.item.Rarity.RARE,
                    core.messages().raw(ModerationModule.ID, "clean-streak.title.source"), false);
        }
        c.get().markAutomatic(Cosmetics.Kind.TITLE, id);
        definedTitle = id;
    }

    private void maybeRound() {
        long now = System.currentTimeMillis();
        if (now - lastRound < settings().check().toMillis()) {
            return;
        }
        lastRound = now;
        core.scheduler().ensureMain("moderation:clean-streak", () -> {
            if (!settings().title().equals(definedTitle)) {
                ensureTitle(); // the title id changed with a reload
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                check(p.getUniqueId(), p.getName());
            }
        });
    }

    /**
     * Join: one check after {@code join-delay}, so the unlock line never lands in the burst of join messages. SMALL2: the
     * delay and the whole check run off the main thread ('moderation:clean-streak-join' was logged at 46.7 ms); only an
     * actual grant / take hops back to the main thread.
     */
    void joined(Player player) {
        CleanStreak.Settings s = settings();
        if (!s.enabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        core.scheduler().asyncLater(Math.max(1L, s.joinDelay().toMillis() / 50L), () -> check(uuid, name));
    }

    /**
     * One check of an online player, safe from any thread: reads the cached player data (thread-safe map), writes
     * today's playtime sample and reads the streak state in one database transaction, decides off the main thread and
     * only hops to the main thread to grant or take the title (decided again there with the current owned titles).
     */
    void check(UUID uuid, String name) {
        CleanStreak.Settings s = settings();
        Optional<Cosmetics> cosmetics = cosmetics();
        Optional<PlayerData> data = core.players().get(uuid);
        if (!s.enabled() || cosmetics.isEmpty() || data.isEmpty()) {
            return; // disabled, cosmetics off, or the player left / is not loaded
        }
        cosmetics.get().markAutomatic(Cosmetics.Kind.TITLE, s.title()); // idempotent (concurrent set); survives a cosmetics reload
        long now = System.currentTimeMillis();
        long firstSeen = data.get().firstSeen();
        long total = data.get().playtimeMillis();
        ZoneId zone = zone();
        store.snapshot(uuid, CleanStreak.day(now, zone), CleanStreak.startDay(now, zone, s), total).thenAccept(snap -> {
            // The owned-titles cache uses concurrent sets, so the decision is made right here on the database thread.
            CleanStreak.Facts facts = new CleanStreak.Facts(now, firstSeen, snap.lastBreaking(), snap.played(),
                    cosmetics.get().owns(uuid, Cosmetics.Kind.TITLE, s.title()), snap.state());
            if (CleanStreak.decide(s, facts) == CleanStreak.Action.NONE) {
                return; // the usual case: no main-thread work at all
            }
            core.scheduler().main("moderation:clean-streak-apply", () -> {
                Optional<Cosmetics> c = cosmetics();
                if (c.isEmpty()) {
                    return;
                }
                // Decided again with the owned titles as they are now (a grant may have landed meanwhile).
                boolean owns = c.get().owns(uuid, Cosmetics.Kind.TITLE, s.title());
                act(uuid, CleanStreak.decide(s, facts.withOwns(owns)), s, snap.played());
            });
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Clean streak check failed for " + name, e);
            return null;
        });
    }

    private void act(UUID uuid, CleanStreak.Action action, CleanStreak.Settings s, long played) {
        Cosmetics cosmetics = cosmetics().orElse(null);
        if (cosmetics == null) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (action) {
            case NONE -> {
            }
            case REGRANT_QUIET -> cosmetics.grant(uuid, Cosmetics.Kind.TITLE, s.title(), "clean-streak", false);
            case GRANT -> store.markEarned(uuid, now).thenAccept(won -> {
                if (!won) {
                    return; // another check got there first
                }
                cosmetics.grant(uuid, Cosmetics.Kind.TITLE, s.title(), "clean-streak", false).whenComplete((isNew, e) -> {
                    if (e != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Could not grant the clean-streak title", e);
                        return;
                    }
                    core.audit().logPlayer(AuditCategory.PUNISHMENT, "clean_streak_earned", Actor.system("clean-streak"), uuid,
                            null, null, null, Map.<String, Object>of("title", s.title(), "days", s.days(), "played_minutes", played / 60_000));
                    core.scheduler().ensureMain("moderation:clean-streak", () -> {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && Boolean.TRUE.equals(isNew)) {
                            msg.send(p, "clean-streak.unlocked", net.skybornmc.skyblock.common.text.Msg.number("days", s.days()),
                                    net.skybornmc.skyblock.common.text.Msg.c("title",
                                            cosmetics.display(p, Cosmetics.Kind.TITLE, s.title())));
                            if (!s.sound().isBlank()) {
                                core.sounds().play(p, s.sound());
                            }
                        }
                    });
                });
            });
            case REVOKE -> lose(uuid, "check");
        }
    }

    /**
     * A punishment was stored (called from the single insert of the store; never throws). A warn, mute, ban or kick hides
     * an earned title at once (also for offline players).
     */
    void punished(Punishment p) {
        if (p == null || p.target() == null || !CleanStreak.breaks(p.type()) || !settings().enabled()) {
            return;
        }
        UUID uuid = p.target();
        core.scheduler().async(() -> lose(uuid, "punishment:" + p.type().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void lose(UUID uuid, String why) {
        CleanStreak.Settings s = settings();
        store.markLost(uuid, System.currentTimeMillis()).thenAccept(won -> {
            if (!won) {
                return; // not earned (or already lost)
            }
            Optional<Cosmetics> cosmetics = cosmetics();
            cosmetics.ifPresent(c -> c.revoke(uuid, Cosmetics.Kind.TITLE, s.title(), "clean-streak:lost"));
            core.audit().logPlayer(AuditCategory.PUNISHMENT, "clean_streak_lost", Actor.system("clean-streak"), uuid, null, null,
                    null, Map.<String, Object>of("title", s.title(), "cause", why));
            if (s.tellLost()) {
                core.scheduler().ensureMain("moderation:clean-streak", () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        msg.send(p, "clean-streak.lost", net.skybornmc.skyblock.common.text.Msg.number("days", s.days()));
                    }
                });
            }
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Clean streak update failed", e);
            return null;
        });
    }
}
