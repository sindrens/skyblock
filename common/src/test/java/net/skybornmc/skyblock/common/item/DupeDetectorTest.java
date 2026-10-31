package net.skybornmc.skyblock.common.item;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PERF2 rolling scan: remembered copies only count when they are still in place, one per place. */
class DupeDetectorTest {

    private final UUID item = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID sam = UUID.randomUUID();

    private DupeDetector.Copy cursor(UUID player, String where) {
        return new DupeDetector.Copy(item, "x", "s", where, player, null, -1);
    }

    @Test
    void twoPlacesStillThereAreADupe() {
        List<DupeDetector.Copy> copies = List.of(cursor(alex, "alex cursor"), cursor(sam, "sam cursor"));
        assertEquals(2, DupeDetector.confirm(copies, c -> true).size());
    }

    @Test
    void aRememberedCopyThatMovedAwayIsDropped() {
        DupeDetector.Copy stale = cursor(alex, "alex cursor (old read)");
        DupeDetector.Copy fresh = cursor(sam, "sam cursor");
        List<DupeDetector.Copy> real = DupeDetector.confirm(List.of(stale, fresh), c -> c != stale);
        assertEquals(List.of(fresh), real, "the item was handed from alex to sam: no alarm");
    }

    @Test
    void theSamePlaceSeenTwiceIsOneItem() {
        DupeDetector.Copy first = cursor(alex, "read 1");
        DupeDetector.Copy second = cursor(alex, "read 2");
        assertEquals(1, DupeDetector.confirm(List.of(first, second), c -> true).size());
    }

    @Test
    void orderIsKept() {
        UUID third = UUID.randomUUID();
        List<DupeDetector.Copy> copies = List.of(cursor(sam, "s"), cursor(alex, "a"), cursor(third, "t"));
        List<DupeDetector.Copy> real = DupeDetector.confirm(copies, c -> true);
        assertEquals(List.of("s", "a", "t"), real.stream().map(DupeDetector.Copy::where).toList());
        assertEquals(Set.of(sam, alex, third), Set.copyOf(real.stream().map(DupeDetector.Copy::player).toList()));
    }

    @Test
    void onlyPlayersWithTheirOwnCopyAreHolders() {
        UUID viewer = UUID.randomUUID();
        DupeDetector.Copy own = cursor(alex, "alex inventory");
        DupeDetector.Copy looking = new DupeDetector.Copy(item, "x", "s", "viewer open chest", viewer, null, 3, false);
        DupeDetector.Copy second = cursor(sam, "sam ender chest");
        assertEquals(List.of(alex, sam), DupeDetector.holderIds(List.of(own, looking, second)),
                "R3-28: a player only looking into a chest is not trade-blocked");
        DupeReport report = new DupeReport(item, "x", "s", List.of("a"), List.of(alex, viewer), 1L);
        assertEquals(List.of(alex, viewer), report.holderIds(), "old constructor: every player is a holder");
    }
}
