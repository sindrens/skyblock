package net.skybornmc.skyblock.blackmarket;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Rotation windows and picks (PHASE-10 M10.11: every 12 h at 00:00 and 12:00 Europe/Oslo). Pure, unit-tested.
 *
 * <p>A rotation id is {@code epochDay * 100 + index}, where {@code index} is the position of the window's start time in
 * the sorted daily times. Ids only grow, so later rotations always have bigger ids. Start times are local wall-clock
 * times: a time that does not exist on a DST spring-forward day starts at the first moment after the gap, a time that
 * happens twice on a fall-back day starts at the first of the two (java.time's rules), so a window is never skipped or
 * started twice.</p>
 */
final class Rotation {

    static final int PER_DAY = 100;

    private final ZoneId zone;
    private final List<LocalTime> times;

    Rotation(ZoneId zone, List<LocalTime> times) {
        this.zone = zone;
        this.times = times.isEmpty() ? List.of(LocalTime.MIDNIGHT) : List.copyOf(new TreeSet<>(times));
    }

    /** Parses {@code ["00:00", "12:00"]}; bad entries are reported and skipped; none left = midnight only. */
    static List<LocalTime> parseTimes(List<String> raw, Consumer<String> problems) {
        TreeSet<LocalTime> out = new TreeSet<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            try {
                out.add(LocalTime.parse(s.trim()).withSecond(0).withNano(0));
            } catch (DateTimeParseException | NullPointerException e) {
                problems.accept("blackmarket.yml rotation.times: '" + s + "' is not a time like 00:00; skipped.");
            }
        }
        if (out.size() > PER_DAY - 1) {
            problems.accept("blackmarket.yml rotation.times: at most " + (PER_DAY - 1) + " times a day.");
            return new ArrayList<>(out).subList(0, PER_DAY - 1);
        }
        if (out.isEmpty()) {
            problems.accept("blackmarket.yml rotation.times: no valid time; using 00:00.");
            out.add(LocalTime.MIDNIGHT);
        }
        return new ArrayList<>(out);
    }

    ZoneId zone() {
        return zone;
    }

    List<LocalTime> times() {
        return times;
    }

    /** The moment a rotation starts (epoch millis). */
    long start(long id) {
        LocalDate day = LocalDate.ofEpochDay(Math.floorDiv(id, PER_DAY));
        int index = Math.floorMod(id, PER_DAY);
        LocalTime time = times.get(Math.min(index, times.size() - 1));
        return ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli();
    }

    /** The rotation that holds {@code now}. */
    long id(long nowMillis) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        for (int i = times.size() - 1; i >= 0; i--) {
            long id = today.toEpochDay() * PER_DAY + i;
            if (start(id) <= nowMillis) {
                return id;
            }
        }
        // Before today's first start: the last window of yesterday.
        return today.minusDays(1).toEpochDay() * PER_DAY + (times.size() - 1);
    }

    /** The rotation right after {@code id}. */
    long next(long id) {
        int index = Math.floorMod(id, PER_DAY);
        long day = Math.floorDiv(id, PER_DAY);
        return index + 1 < times.size() ? day * PER_DAY + index + 1 : (day + 1) * PER_DAY;
    }

    /** When the rotation holding {@code now} ends (the next restock), epoch millis. */
    long restockAt(long nowMillis) {
        return start(next(id(nowMillis)));
    }

    /** Milliseconds until the next restock (at least 0). */
    long untilRestock(long nowMillis) {
        return Math.max(0, restockAt(nowMillis) - nowMillis);
    }

    /** Key of a limit period: {@code r:<rotation>}, {@code d:<epoch day>}, {@code m:<yyyymm>}, {@code s:<season>}. */
    String periodKey(OfferCatalog.Period period, long nowMillis, int seasonId) {
        return switch (period) {
            case ROTATION -> "r:" + id(nowMillis);
            case DAY -> "d:" + Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay();
            case MONTH -> {
                ZonedDateTime t = Instant.ofEpochMilli(nowMillis).atZone(zone);
                yield String.format(Locale.ROOT, "m:%04d%02d", t.getYear(), t.getMonthValue());
            }
            case SEASON -> "s:" + seasonId;
        };
    }

    // ================================================================== picks

    /** One picked offer of a rotation; {@code stock} = server-wide stock (-1 = no shared stock). */
    record Pick(int index, String offerId, long price, int stock) {
    }

    /**
     * The rotating offers of one rotation: per slot group (headline, rare, common, in that order) up to {@code counts}
     * offers by weight without repeats, from the offers {@code live} accepts. The same rotation, salt and offers always
     * give the same picks. Headline picks get the shared {@code headlineStock} (0 = none).
     */
    static List<Pick> pick(long rotationId, long salt, OfferCatalog catalog, Map<OfferCatalog.Slot, Integer> counts,
                           Predicate<OfferCatalog.Offer> live, int headlineStock) {
        SplittableRandom rng = new SplittableRandom(rotationId * 0x9E3779B97F4A7C15L ^ salt);
        List<Pick> out = new ArrayList<>();
        for (OfferCatalog.Slot slot : List.of(OfferCatalog.Slot.HEADLINE, OfferCatalog.Slot.RARE, OfferCatalog.Slot.COMMON)) {
            List<OfferCatalog.Offer> pool = new ArrayList<>();
            for (OfferCatalog.Offer o : catalog.of(slot)) {
                if (o.rollable() && live.test(o)) {
                    pool.add(o);
                }
            }
            int want = Math.max(0, counts.getOrDefault(slot, 0));
            for (int n = 0; n < want && !pool.isEmpty(); n++) {
                double total = 0;
                for (OfferCatalog.Offer o : pool) {
                    total += o.weight();
                }
                double roll = rng.nextDouble() * total;
                int chosen = pool.size() - 1;
                for (int i = 0; i < pool.size(); i++) {
                    roll -= pool.get(i).weight();
                    if (roll < 0) {
                        chosen = i;
                        break;
                    }
                }
                OfferCatalog.Offer o = pool.remove(chosen);
                int stock = slot == OfferCatalog.Slot.HEADLINE && headlineStock > 0 ? headlineStock : -1;
                out.add(new Pick(out.size(), o.id(), o.price(), stock));
            }
        }
        return out;
    }
}
