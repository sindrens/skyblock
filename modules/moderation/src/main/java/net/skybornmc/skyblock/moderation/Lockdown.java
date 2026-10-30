package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Raid lockdown rules (spec 13.9), pure and unit-tested. False positives are the main risk, so:
 * <ul>
 *   <li>only "new" accounts are ever affected: total playtime below {@code new-player-playtime} <b>or</b> first join less
 *       than {@code new-player-age} ago. Anyone with {@code skyblock.lockdown.bypass} (every rank above default) or
 *       {@code skyblock.staff.notify} (staff) never is;</li>
 *   <li>the join lockdown only stops accounts that <b>never joined before</b>; returning players always get in;</li>
 *   <li>manual by default; detection only alerts. With {@code raid.auto-lock} on: chat lockdown only, at most
 *       {@code auto-duration} (max 5 minutes), at most once per {@code auto-cooldown} (at least 30 minutes), never the join
 *       lockdown;</li>
 *   <li>a lockdown never creates punishments, ends by itself and lives in memory only (a restart clears it).</li>
 * </ul>
 * Bedrock (Floodgate) accounts use exactly the same rules: nothing here looks at the platform.
 */
final class Lockdown {

    private Lockdown() {
    }

    enum Kind {
        CHAT, JOIN;

        String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Hard limits from the spec that the config can't go past. */
    static final Duration AUTO_MAX = Duration.ofMinutes(5);
    static final Duration AUTO_COOLDOWN_MIN = Duration.ofMinutes(30);

    /**
     * {@code extras.lockdown}.
     *
     * @param newPlayerPlaytime below this total playtime an account counts as new
     * @param newPlayerAge      an account whose first join is younger than this counts as new
     * @param chatDefault       {@code /lockdown chat} without a duration
     * @param chatMax           longest chat lockdown
     * @param joinDefault       {@code /lockdown join} without a duration
     * @param joinMax           longest join lockdown
     * @param staffReminder     staff with skyblock.staff.notify get the status line this often while one is active
     * @param blockedCommands   command labels new players can't use during a chat lockdown (private messages, mail)
     */
    record Settings(Duration newPlayerPlaytime, Duration newPlayerAge, Duration chatDefault, Duration chatMax,
                    Duration joinDefault, Duration joinMax, Duration staffReminder, List<String> blockedCommands) {
        static final Settings DEFAULT = new Settings(Duration.ofMinutes(30), Duration.ofHours(24), Duration.ofMinutes(10),
                Duration.ofMinutes(60), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofSeconds(60),
                List.of("msg", "tell", "w", "whisper", "m", "pm", "r", "reply", "mail"));

        Duration defaultFor(Kind kind) {
            return kind == Kind.CHAT ? chatDefault : joinDefault;
        }

        Duration maxFor(Kind kind) {
            return kind == Kind.CHAT ? chatMax : joinMax;
        }
    }

    /**
     * {@code extras.raid}.
     *
     * @param detect       alert staff when many never-seen accounts join at once
     * @param accounts     more than this many ...
     * @param window       ... within this time is a possible raid
     * @param autoLock     also start a chat lockdown by itself (off by default)
     * @param autoDuration length of an automatic chat lockdown (max 5 minutes)
     * @param autoCooldown at most one automatic lockdown per this time (at least 30 minutes)
     */
    record Raid(boolean detect, int accounts, Duration window, boolean autoLock, Duration autoDuration, Duration autoCooldown) {
        static final Raid DEFAULT = new Raid(true, 15, Duration.ofSeconds(30), false, AUTO_MAX, AUTO_COOLDOWN_MIN);
    }

    /** One running lockdown. {@code by == null} = the system (automatic). */
    record Active(Kind kind, long started, long until, UUID by, String byName, boolean auto) {

        boolean live(long now) {
            return until > now;
        }

        /** Time left, at least one second while live. */
        Duration left(long now) {
            return Duration.ofMillis(Math.max(1000, until - now));
        }

        Duration length() {
            return Duration.ofMillis(Math.max(0, until - started));
        }
    }

    // ------------------------------------------------------------------ who is affected

    /**
     * Is this account "new"? {@code firstSeen == null} (never joined) is new. Playtime includes the current session.
     */
    static boolean isNew(Long firstSeen, long playtimeMillis, long now, Settings s) {
        if (firstSeen == null) {
            return true;
        }
        return playtimeMillis < s.newPlayerPlaytime().toMillis() || now - firstSeen < s.newPlayerAge().toMillis();
    }

    /**
     * Is a chat line / private message / sign / book of this player blocked right now? Only during a live chat lockdown,
     * only for new accounts, never for bypass holders or staff.
     */
    static boolean chatBlocked(Active chat, long now, boolean exempt, boolean isNew) {
        return chat != null && chat.live(now) && !exempt && isNew;
    }

    /**
     * Is this login refused? Only during a live join lockdown and only for an account that never joined before.
     * {@code knownAccount} = the account has a player record (it joined at least once).
     */
    static boolean joinBlocked(Active join, long now, boolean knownAccount) {
        return join != null && join.live(now) && !knownAccount;
    }

    /** Is {@code label} (no slash, maybe {@code plugin:label}) one of the blocked commands? */
    static boolean blockedCommand(String label, List<String> blocked) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        String l = label.toLowerCase(java.util.Locale.ROOT);
        int colon = l.indexOf(':');
        if (colon >= 0) {
            l = l.substring(colon + 1);
        }
        return blocked.contains(l);
    }

    // ------------------------------------------------------------------ durations

    /** A checked duration: {@code duration} or the message key of the problem. */
    record Length(Duration duration, String error) {
        boolean ok() {
            return error == null;
        }
    }

    /** {@code requested == null} = the default for this kind. Never above the kind's max, never zero. */
    static Length length(Kind kind, Duration requested, Settings s) {
        Duration d = requested == null ? s.defaultFor(kind) : requested;
        if (d.isZero() || d.isNegative()) {
            return new Length(null, "lockdown.bad-length");
        }
        if (d.compareTo(s.maxFor(kind)) > 0) {
            return new Length(null, "lockdown.too-long");
        }
        return new Length(d, null);
    }

    // ------------------------------------------------------------------ automatic lockdown

    enum AutoOutcome {
        /** auto-lock is off: only the alert. */
        OFF,
        /** Start a chat lockdown for {@link Auto#duration}. */
        START,
        /** A chat lockdown is already running: nothing to do. */
        ALREADY,
        /** The last automatic lockdown was less than auto-cooldown ago. */
        COOLDOWN
    }

    record Auto(AutoOutcome outcome, Duration duration) {
    }

    /**
     * What a detected raid does by itself. Chat lockdown only (never join), at most 5 minutes and never longer than the
     * chat max, at most once per cooldown (at least 30 minutes), counted from the last automatic start.
     */
    static Auto auto(Raid raid, Settings s, Active chat, Long lastAuto, long now) {
        if (!raid.autoLock()) {
            return new Auto(AutoOutcome.OFF, null);
        }
        if (chat != null && chat.live(now)) {
            return new Auto(AutoOutcome.ALREADY, null);
        }
        Duration cooldown = raid.autoCooldown().compareTo(AUTO_COOLDOWN_MIN) < 0 ? AUTO_COOLDOWN_MIN : raid.autoCooldown();
        if (lastAuto != null && now - lastAuto < cooldown.toMillis()) {
            return new Auto(AutoOutcome.COOLDOWN, null);
        }
        Duration d = raid.autoDuration();
        if (d.compareTo(AUTO_MAX) > 0) {
            d = AUTO_MAX;
        }
        if (d.compareTo(s.chatMax()) > 0) {
            d = s.chatMax();
        }
        return new Auto(AutoOutcome.START, d);
    }

    // ------------------------------------------------------------------ state (in memory only)

    /**
     * The running lockdowns. Thread-safe: chat and login checks read it off the main thread. Never persisted, so a
     * restart clears it (spec).
     */
    static final class State {

        private volatile Active chat;
        private volatile Active join;
        private volatile Long lastAuto;

        Active get(Kind kind) {
            return kind == Kind.CHAT ? chat : join;
        }

        /** The live lockdown of this kind, if any. */
        Optional<Active> live(Kind kind, long now) {
            Active a = get(kind);
            return a != null && a.live(now) ? Optional.of(a) : Optional.empty();
        }

        boolean any(long now) {
            return live(Kind.CHAT, now).isPresent() || live(Kind.JOIN, now).isPresent();
        }

        Long lastAuto() {
            return lastAuto;
        }

        /**
         * Starts (or replaces: a new start sets a new end) a lockdown and returns the one it replaced, if live. An automatic
         * start never replaces a running one (the caller checks with {@link Lockdown#auto} first).
         */
        synchronized Optional<Active> start(Active next, long now) {
            Active before = get(next.kind());
            if (next.kind() == Kind.CHAT) {
                chat = next;
            } else {
                join = next;
            }
            if (next.auto()) {
                lastAuto = next.started();
            }
            return before != null && before.live(now) ? Optional.of(before) : Optional.empty();
        }

        /** Ends one kind now; the ended lockdown if it was live. */
        synchronized Optional<Active> stop(Kind kind, long now) {
            Active before = get(kind);
            if (kind == Kind.CHAT) {
                chat = null;
            } else {
                join = null;
            }
            return before != null && before.live(now) ? Optional.of(before) : Optional.empty();
        }

        /** Ends both; the ones that were live. */
        synchronized List<Active> stopAll(long now) {
            List<Active> out = new ArrayList<>();
            stop(Kind.CHAT, now).ifPresent(out::add);
            stop(Kind.JOIN, now).ifPresent(out::add);
            return out;
        }

        /** Removes lockdowns whose time is up and returns them (each exactly once). */
        synchronized List<Active> expire(long now) {
            List<Active> out = new ArrayList<>();
            if (chat != null && !chat.live(now)) {
                out.add(chat);
                chat = null;
            }
            if (join != null && !join.live(now)) {
                out.add(join);
                join = null;
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ raid detection

    /**
     * Counts never-seen accounts joining within {@code window} (each account once). Fires once when the count goes
     * <b>over</b> {@code accounts}; it fires again only after the count dropped back to the limit or below (a quiet
     * window), so one raid gives one alert. Thread-safe.
     */
    static final class RaidDetector {

        private record Hit(long at, UUID who) {
        }

        private final Deque<Hit> hits = new ArrayDeque<>();
        private final Set<UUID> inWindow = new HashSet<>();
        private boolean alerted;

        /**
         * A never-seen account joined at {@code now}. Returns the count to alert with when this join made it a possible raid,
         * else empty. Detection off = always empty.
         */
        synchronized Optional<Integer> record(UUID who, long now, Raid raid) {
            prune(now, raid.window());
            if (!raid.detect()) {
                return Optional.empty();
            }
            if (inWindow.add(who)) {
                hits.addLast(new Hit(now, who));
            }
            int count = hits.size();
            if (count <= raid.accounts()) {
                alerted = false;
                return Optional.empty();
            }
            if (alerted) {
                return Optional.empty();
            }
            alerted = true;
            return Optional.of(count);
        }

        /** Accounts counted right now (for status lines). */
        synchronized int count(long now, Duration window) {
            prune(now, window);
            return hits.size();
        }

        private void prune(long now, Duration window) {
            long cutoff = now - window.toMillis();
            while (!hits.isEmpty() && hits.peekFirst().at() <= cutoff) {
                inWindow.remove(hits.removeFirst().who());
            }
            if (hits.isEmpty()) {
                alerted = false;
            }
        }
    }
}
