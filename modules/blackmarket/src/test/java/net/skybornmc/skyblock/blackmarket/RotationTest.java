package net.skybornmc.skyblock.blackmarket;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rotation windows (00:00 and 12:00 Oslo, DST days) and the weighted picks. */
class RotationTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private final Rotation twice = new Rotation(OSLO, List.of(LocalTime.of(12, 0), LocalTime.MIDNIGHT));

    private static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, OSLO).toInstant().toEpochMilli();
    }

    private static long day(int y, int mo, int d) {
        return LocalDate.of(y, mo, d).toEpochDay();
    }

    @Test
    void twoWindowsADay() {
        assertEquals(day(2026, 6, 10) * 100, twice.id(at(2026, 6, 10, 0, 0)));
        assertEquals(day(2026, 6, 10) * 100, twice.id(at(2026, 6, 10, 11, 59)));
        assertEquals(day(2026, 6, 10) * 100 + 1, twice.id(at(2026, 6, 10, 12, 0)));
        assertEquals(day(2026, 6, 10) * 100 + 1, twice.id(at(2026, 6, 10, 23, 59)));
        assertEquals(at(2026, 6, 10, 12, 0), twice.restockAt(at(2026, 6, 10, 3, 0)));
        assertEquals(at(2026, 6, 11, 0, 0), twice.restockAt(at(2026, 6, 10, 13, 0)));
        assertEquals(3_600_000L, twice.untilRestock(at(2026, 6, 10, 11, 0)));
    }

    @Test
    void beforeTheFirstTimeOfTheDayIsYesterdaysLastWindow() {
        Rotation late = new Rotation(OSLO, List.of(LocalTime.of(6, 0), LocalTime.of(18, 0)));
        assertEquals(day(2026, 6, 9) * 100 + 1, late.id(at(2026, 6, 10, 5, 0)));
        assertEquals(at(2026, 6, 10, 6, 0), late.restockAt(at(2026, 6, 10, 5, 0)));
    }

    @Test
    void idsAlwaysGrowAndNextFollowsTheDay() {
        long id = twice.id(at(2026, 6, 10, 13, 0));
        assertEquals(day(2026, 6, 11) * 100, twice.next(id));
        assertEquals(id, twice.next(twice.id(at(2026, 6, 10, 1, 0))));
        long previous = Long.MIN_VALUE;
        for (long t = at(2026, 3, 27, 0, 0); t < at(2026, 4, 2, 0, 0); t += 900_000L) {
            long now = twice.id(t);
            assertTrue(now >= previous);
            previous = now;
        }
    }

    @Test
    void springForwardStillGivesTwelveHourWindowsAtLocalTime() {
        // 29 March 2026: 02:00 -> 03:00 in Oslo. The day has 23 hours; restocks stay at 00:00 and 12:00 local time.
        long midnight = at(2026, 3, 29, 0, 0);
        long noon = at(2026, 3, 29, 12, 0);
        assertEquals(11 * 3_600_000L, noon - midnight);
        assertEquals(noon, twice.restockAt(midnight + 1));
        assertEquals(day(2026, 3, 29) * 100 + 1, twice.id(noon));
    }

    @Test
    void aTimeInsideTheSpringGapStartsRightAfterIt() {
        Rotation gap = new Rotation(OSLO, List.of(LocalTime.of(2, 30)));
        long start = gap.start(day(2026, 3, 29) * 100);
        assertEquals(at(2026, 3, 29, 3, 30), start);
        assertEquals(day(2026, 3, 28) * 100, gap.id(at(2026, 3, 29, 1, 59)));
        assertEquals(day(2026, 3, 29) * 100, gap.id(at(2026, 3, 29, 3, 31)));
    }

    @Test
    void aTimeThatHappensTwiceOnFallBackStartsOnce() {
        // 25 October 2026: 03:00 -> 02:00 in Oslo, so 02:30 happens twice; the window starts at the first one.
        Rotation twiceAt = new Rotation(OSLO, List.of(LocalTime.of(2, 30)));
        long id = day(2026, 10, 25) * 100;
        long first = twiceAt.start(id);
        assertEquals(ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, OSLO).withEarlierOffsetAtOverlap().toInstant().toEpochMilli(), first);
        assertEquals(id, twiceAt.id(first + 3_600_000L), "the second 02:30 is the same window");
        assertEquals(25 * 3_600_000L, twiceAt.start(twiceAt.next(id)) - first, "the fall-back window is 25 hours");
    }

    @Test
    void periodKeys() {
        long t = at(2026, 10, 31, 23, 30);
        assertEquals("r:" + (day(2026, 10, 31) * 100 + 1), twice.periodKey(OfferCatalog.Period.ROTATION, t, 3));
        assertEquals("d:" + day(2026, 10, 31), twice.periodKey(OfferCatalog.Period.DAY, t, 3));
        assertEquals("m:202610", twice.periodKey(OfferCatalog.Period.MONTH, t, 3));
        assertEquals("m:202611", twice.periodKey(OfferCatalog.Period.MONTH, at(2026, 11, 1, 0, 0), 3));
        assertEquals("s:3", twice.periodKey(OfferCatalog.Period.SEASON, t, 3));
    }

    @Test
    void badTimesAreReported() {
        List<String> problems = new ArrayList<>();
        assertEquals(List.of(LocalTime.of(6, 0), LocalTime.of(18, 0)),
                Rotation.parseTimes(List.of("18:00", "nope", "06:00", "06:00"), problems::add));
        assertEquals(1, problems.size());
        problems.clear();
        assertEquals(List.of(LocalTime.MIDNIGHT), Rotation.parseTimes(List.of(), problems::add));
        assertEquals(1, problems.size());
    }

    // ================================================================== picks

    private final OfferCatalog catalog = OfferCatalog.parse(new BlackMarketSettings().offers, p -> { });
    private final Map<OfferCatalog.Slot, Integer> counts = new EnumMap<>(Map.of(OfferCatalog.Slot.HEADLINE, 1,
            OfferCatalog.Slot.RARE, 3, OfferCatalog.Slot.COMMON, 4));

    @Test
    void picksFollowTheSlotCountsWithoutRepeats() {
        List<Rotation.Pick> picks = Rotation.pick(42, 0, catalog, counts, o -> true, 25);
        // Live commons in the defaults: bm_tag and elytra only (blueprint, sell wand and tome are coming soon).
        assertEquals(1 + 3 + 2, picks.size());
        Set<String> ids = new HashSet<>();
        for (Rotation.Pick p : picks) {
            assertTrue(ids.add(p.offerId()));
            OfferCatalog.Offer o = catalog.get(p.offerId()).orElseThrow();
            assertTrue(o.rollable());
            assertEquals(o.price(), p.price());
        }
        assertEquals(OfferCatalog.Slot.HEADLINE, catalog.get(picks.get(0).offerId()).orElseThrow().slot());
        assertEquals(25, picks.get(0).stock(), "the headline has the shared stock");
        assertTrue(picks.subList(1, picks.size()).stream().allMatch(p -> p.stock() == -1));
        for (int i = 0; i < picks.size(); i++) {
            assertEquals(i, picks.get(i).index());
        }
    }

    @Test
    void theSameRotationAlwaysPicksTheSameOffers() {
        assertEquals(Rotation.pick(7, 0, catalog, counts, o -> true, 25), Rotation.pick(7, 0, catalog, counts, o -> true, 25));
        Set<List<Rotation.Pick>> different = new HashSet<>();
        for (long id = 0; id < 30; id++) {
            different.add(Rotation.pick(id, 0, catalog, counts, o -> true, 25));
        }
        assertTrue(different.size() > 5, "rotations differ");
        int changed = 0;
        for (long id = 0; id < 30; id++) {
            if (!Rotation.pick(id, 0, catalog, counts, o -> true, 25).equals(Rotation.pick(id, 99, catalog, counts, o -> true, 25))) {
                changed++;
            }
        }
        assertTrue(changed > 0, "a staff reroll (new salt) gives other offers");
    }

    @Test
    void offersThatCantBeHandedOutAreNeverPicked() {
        List<Rotation.Pick> picks = Rotation.pick(1, 0, catalog, counts, o -> o.kind() != OfferCatalog.Kind.SET_TOKEN, 0);
        assertTrue(picks.stream().noneMatch(p -> catalog.get(p.offerId()).orElseThrow().kind() == OfferCatalog.Kind.SET_TOKEN));
        assertEquals("mythic_book", picks.get(0).offerId(), "the only live headline");
        assertEquals(-1, picks.get(0).stock(), "stock 0 = no shared stock");
    }

    @Test
    void weightsDecideHowOftenAnOfferComes() {
        OfferCatalog two = OfferCatalog.parse(List.of("heavy: slot=headline price=1 weight=9 reward=scroll",
                "light: slot=headline price=1 weight=1 reward=scroll"), p -> { });
        Map<OfferCatalog.Slot, Integer> one = Map.of(OfferCatalog.Slot.HEADLINE, 1);
        int heavy = 0;
        for (long id = 0; id < 2000; id++) {
            if (Rotation.pick(id, 0, two, one, o -> true, 0).get(0).offerId().equals("heavy")) {
                heavy++;
            }
        }
        assertTrue(heavy > 1650 && heavy < 1950, "about 90%: " + heavy);
    }
}
