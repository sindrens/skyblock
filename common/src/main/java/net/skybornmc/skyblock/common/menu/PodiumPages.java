package net.skybornmc.skyblock.common.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * The leaderboard layout of both Top menus (MENUS-V3 section 5, owner V3-Q13 a): page 1 = places 1-10 as a podium
 * (#1 on 13, #2 on 21, #3 on 23, #4-#10 on 28-34); page 2 and later = 18 places each as centred rows of 6
 * ({@link Centered#split}) on screen rows 1-3. Pure.
 */
public final class PodiumPages<T> implements PageLayout<T> {

    /** Slots of places 1-10 on page 1. */
    public static final int[] PODIUM = {13, 21, 23, 28, 29, 30, 31, 32, 33, 34};
    /** Places on every later page. */
    public static final int PER_PAGE = 18;

    @Override
    public List<Page<T>> pages(List<T> entries) {
        List<Page<T>> out = new ArrayList<>();
        int first = Math.min(PODIUM.length, entries.size());
        int[] slots = new int[first];
        System.arraycopy(PODIUM, 0, slots, 0, first);
        out.add(new Page<>(entries.subList(0, first), slots));
        for (int from = first; from < entries.size(); from += PER_PAGE) {
            List<T> chunk = entries.subList(from, Math.min(entries.size(), from + PER_PAGE));
            out.add(new Page<>(chunk, Centered.grid(Centered.split(chunk.size()))));
        }
        return out;
    }

    /** The podium slot of a 1-based place on page 1, or -1 when the place is not on page 1. */
    public static int podiumSlot(int place) {
        return place >= 1 && place <= PODIUM.length ? PODIUM[place - 1] : -1;
    }
}
