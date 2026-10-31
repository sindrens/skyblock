package net.skybornmc.skyblock.rewards;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The rules of voting (pure, unit-tested; PHASE-10 M10.5): sites, vote ids, Floodgate name variants, one reward per site
 * per account per 24 h, the address limit, the vote streak (Oslo days) and the vote party counter.
 */
final class VoteRules {

    static final int NEVER = -1;
    static final long HOUR = 3_600_000L;

    /** Vote row states ({@code vote_log.vote_state}). */
    static final String QUEUED = "queued";
    static final String PAID = "paid";
    static final String DUPLICATE = "duplicate";
    static final String LIMITED = "limited";
    static final String EXPIRED = "expired";

    private static final Pattern SITE_ID = Pattern.compile("[a-z0-9_\\-]{1,32}");

    private VoteRules() {
    }

    // ================================================================== sites

    /** One vote site from {@code votes.yml → sites}. */
    record Site(String id, String name, List<String> services, String url) {

        /** True when a vote's service name belongs to this site (case, dots, dashes and "www." don't matter). */
        boolean matches(String service) {
            String n = normalize(service);
            return !n.isEmpty() && services.stream().anyMatch(s -> normalize(s).equals(n));
        }
    }

    /** Parses {@code id | name | services | url} lines; broken lines are reported and skipped. */
    static List<Site> sites(List<String> lines, Consumer<String> problem) {
        List<Site> out = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : lines) {
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4) {
                problem.accept("votes.yml -> sites: '" + raw + "' needs 4 parts: id | name | service names | link (skipped)");
                continue;
            }
            String id = parts[0].strip().toLowerCase(Locale.ROOT);
            String name = parts[1].strip();
            List<String> services = new ArrayList<>();
            for (String s : parts[2].split(",")) {
                if (!s.isBlank()) {
                    services.add(s.strip());
                }
            }
            String url = parts[3].strip();
            if (!SITE_ID.matcher(id).matches() || name.isEmpty() || services.isEmpty() || !url.startsWith("http")) {
                problem.accept("votes.yml -> sites: '" + raw + "' has an empty or bad id, name, service or link (skipped)");
                continue;
            }
            if (!ids.add(id)) {
                problem.accept("votes.yml -> sites: the id '" + id + "' is used twice (second one skipped)");
                continue;
            }
            out.add(new Site(id, name, List.copyOf(services), url));
        }
        return List.copyOf(out);
    }

    static Optional<Site> site(List<Site> sites, String service) {
        return sites.stream().filter(s -> s.matches(service)).findFirst();
    }

    /** Lower case, without "www." and anything that is not a letter or digit. */
    static String normalize(String service) {
        if (service == null) {
            return "";
        }
        String s = service.strip().toLowerCase(Locale.ROOT);
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        return s.replaceAll("[^a-z0-9]", "");
    }

    // ================================================================== ids and names

    /**
     * The idempotency key of a vote: the same packet twice (a retry, a replay) gives the same id and is stored only once.
     * Sites without a usable timestamp are keyed by the minute it arrived.
     */
    static String voteId(String service, String username, long siteTimestamp, long receivedAt) {
        String time = siteTimestamp > 0 ? "t" + siteTimestamp : "r" + (receivedAt / 60_000L);
        return sha256(normalize(service) + "|" + nameKey(username) + "|" + time).substring(0, 40);
    }

    /** How names are compared: lower case, spaces as '_' (Floodgate's default). */
    static String nameKey(String name) {
        return name == null ? "" : name.strip().replace(' ', '_').toLowerCase(Locale.ROOT);
    }

    /**
     * Names to look up for a vote, best first: the name as sent, then (when it has no Floodgate prefix) the Bedrock name
     * with the prefix, because vote sites often drop it.
     */
    static List<String> voteNames(String username, String prefix) {
        Set<String> out = new LinkedHashSet<>();
        String name = username.strip().replace(' ', '_');
        out.add(name);
        if (prefix != null && !prefix.isEmpty() && !name.startsWith(prefix)) {
            out.add(prefix + name);
        }
        return List.copyOf(out);
    }

    /**
     * Name keys of waiting votes a joining player may take: the own name and, for a Bedrock name with the prefix, the
     * name without it. A Java player never takes a vote sent with the Bedrock prefix.
     */
    static List<String> joinNames(String playerName, String prefix) {
        Set<String> out = new LinkedHashSet<>();
        out.add(nameKey(playerName));
        if (prefix != null && !prefix.isEmpty() && playerName.startsWith(prefix) && playerName.length() > prefix.length()) {
            out.add(nameKey(playerName.substring(prefix.length())));
        }
        return List.copyOf(out);
    }

    /** SHA-256 of a voter address (only the hash is stored), empty for an empty address. */
    static String addressHash(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        return sha256("vote-address|" + address.strip().toLowerCase(Locale.ROOT));
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================================================================== rules

    /** The day of a moment in the server zone (epoch days; a day starts at 00:00 Europe/Oslo). */
    static int date(long millis, ZoneId zone) {
        return DailyRules.today(millis, zone);
    }

    /**
     * One reward per site per account per {@code cooldown}: true when another paid vote on the same site is closer than
     * that to this one (before or after it, so late offline votes follow the same rule).
     */
    static boolean onCooldown(Collection<Long> paidSameSite, long receivedAt, long cooldownMillis) {
        for (long t : paidSameSite) {
            if (Math.abs(t - receivedAt) < cooldownMillis) {
                return true;
            }
        }
        return false;
    }

    /** Milliseconds until the next rewarded vote on a site (0 = vote now). */
    static long untilNext(long lastPaid, long now, long cooldownMillis) {
        if (lastPaid <= 0) {
            return 0;
        }
        return Math.max(0, lastPaid + cooldownMillis - now);
    }

    /** The address limit: at most {@code max} accounts per address per day (an account already counted always passes). */
    static boolean addressAllowed(Collection<UUID> accountsToday, UUID account, int max) {
        return max <= 0 || accountsToday.contains(account) || accountsToday.size() < max;
    }

    // ================================================================== streak

    /** A vote streak (per account): days in a row with at least one vote, the last vote day and the best streak. */
    record Streak(int days, int lastDate, int best) {
        static final Streak NONE = new Streak(0, NEVER, 0);
    }

    /**
     * The streak after a paid vote on {@code voteDate}: same day = no change; the day after the last = +1; a gap = starts
     * at 1. A late (offline) vote from before the last vote day changes nothing.
     */
    static Streak advance(Streak s, int voteDate) {
        if (s.lastDate() != NEVER && voteDate <= s.lastDate()) {
            return s;
        }
        int days = s.lastDate() != NEVER && voteDate == s.lastDate() + 1 ? s.days() + 1 : 1;
        return new Streak(days, voteDate, Math.max(s.best(), days));
    }

    /** The streak shown today: 0 once a whole day was missed. */
    static int liveStreak(Streak s, int today) {
        return s.lastDate() != NEVER && s.lastDate() >= today - 1 ? s.days() : 0;
    }

    static boolean votedOn(Streak s, int date) {
        return s.lastDate() == date;
    }

    /** The day in the reward cycle (day 37 of a 30-day cycle is day 7). */
    static int cycleDay(int days, int cycle) {
        if (days <= 0) {
            return 0;
        }
        return cycle > 0 ? (days - 1) % cycle + 1 : days;
    }

    /** The crate a streak that just reached {@code days} pays, if any. */
    static Optional<String> streakReward(int days, NavigableMap<Integer, String> rewards, int cycle) {
        return Optional.ofNullable(rewards.get(cycleDay(days, cycle)));
    }

    /**
     * The next streak reward after {@code days}: [days still needed, cycle day of it], or empty when there is none.
     * {@code days} = the live streak (today's vote may still be missing).
     */
    static Optional<int[]> nextStreakReward(int days, NavigableMap<Integer, String> rewards, int cycle) {
        if (rewards.isEmpty()) {
            return Optional.empty();
        }
        int limit = cycle > 0 ? cycle : rewards.lastKey();
        for (int step = 1; step <= limit + 1; step++) {
            int d = cycleDay(days + step, cycle);
            if (rewards.containsKey(d) && (cycle > 0 || days + step <= rewards.lastKey())) {
                return Optional.of(new int[]{step, d});
            }
        }
        return Optional.empty();
    }

    /** {@code '<day>: <crate>'} lines → day → crate (broken lines reported and skipped). */
    static NavigableMap<Integer, String> streakRewards(List<String> lines, Consumer<String> problem) {
        NavigableMap<Integer, String> out = new TreeMap<>();
        for (String raw : lines) {
            int colon = raw.indexOf(':');
            Long day = colon > 0 ? RewardSpec.number(raw.substring(0, colon).strip()) : null;
            String crate = colon > 0 ? raw.substring(colon + 1).strip().toLowerCase(Locale.ROOT) : "";
            if (day == null || day < 1 || day > 3_650 || crate.isEmpty() || crate.contains(" ")) {
                problem.accept("votes.yml -> streak.rewards: '" + raw + "' should look like '7: cinder' (skipped)");
                continue;
            }
            out.put(day.intValue(), crate);
        }
        return out;
    }

    // ================================================================== party

    /** The party counter after one more vote: {@code filled} when it reached the size (the counter starts over). */
    record PartyStep(int counter, boolean filled) {
    }

    static PartyStep addVote(int counter, int size) {
        int s = Math.max(1, size);
        int c = Math.max(0, counter) + 1;
        return c >= s ? new PartyStep(c - s, true) : new PartyStep(c, false);
    }

    /** Who gets the party reward: voted today, finished the tutorial, not AFK (and allowed to vote). */
    static boolean partyEligible(boolean permitted, boolean votedToday, boolean tutorialDone, boolean afk) {
        return permitted && votedToday && tutorialDone && !afk;
    }

    /** A text progress bar of {@code width} cells. */
    static int filledCells(int counter, int size, int width) {
        if (size <= 0 || width <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(width, (long) counter * width / size));
    }
}
