package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.3 B40 spinner: the frames slow down and stop on the reward that was decided before the spin. */
class SpinnerMathTest {

    @Test
    void scheduleStartsAtZeroEndsAtTheDurationAndSlowsDown() {
        for (int frames : new int[]{2, 5, 14, 19, 26, 60}) {
            for (int duration : new int[]{10, 60, 200}) {
                int[] s = SpinnerMath.schedule(frames, duration);
                assertEquals(frames, s.length);
                assertEquals(0, s[0]);
                assertEquals(Math.max(frames - 1, duration), s[frames - 1], "the last frame lands at the end");
                for (int i = 1; i < s.length; i++) {
                    assertTrue(s[i] > s[i - 1], "times strictly increase: " + java.util.Arrays.toString(s));
                }
            }
        }
        int[] s = SpinnerMath.schedule(26, 60);
        int firstGap = s[1] - s[0];
        int lastGap = s[25] - s[24];
        assertTrue(lastGap > firstGap, "slows down onto the middle slot: " + java.util.Arrays.toString(s));
        // The first half of the frames take much less time than the second half.
        assertTrue(s[13] < 60 - s[13], "most of the time is spent on the last steps: " + java.util.Arrays.toString(s));
    }

    @Test
    void lastFramePutsTheDecidedRewardInTheMiddleSlot() {
        List<String> pool = List.of("money", "crystals", "book", "key");
        for (int frames = 2; frames <= 40; frames++) {
            for (int seed = 0; seed < 20; seed++) {
                List<String> strip = SpinnerMath.strip(pool, x -> 1, "WON", frames, new Random(seed));
                assertEquals(SpinnerMath.stripLength(frames), strip.size());
                List<String> last = SpinnerMath.window(strip, frames - 1);
                assertEquals(SpinnerMath.WINDOW, last.size());
                assertEquals("WON", last.get(SpinnerMath.CENTER), "frames " + frames + " seed " + seed);
            }
        }
    }

    @Test
    void eachFrameMovesTheRowByOneSlot() {
        List<Integer> strip = new ArrayList<>();
        for (int i = 0; i < SpinnerMath.stripLength(10); i++) {
            strip.add(i);
        }
        for (int f = 1; f < 10; f++) {
            List<Integer> before = SpinnerMath.window(strip, f - 1);
            List<Integer> now = SpinnerMath.window(strip, f);
            assertEquals(before.subList(1, 7), now.subList(0, 6));
        }
    }

    @Test
    void emptyPoolShowsOnlyTheReward() {
        List<String> strip = SpinnerMath.strip(List.of(), x -> 1, "WON", 5, new Random(1));
        assertTrue(strip.stream().allMatch("WON"::equals));
        List<String> zero = SpinnerMath.strip(List.of("a"), x -> 0, "WON", 5, new Random(1));
        assertTrue(zero.stream().allMatch("WON"::equals), "all weights 0 = nothing to pick");
    }

    @Test
    void bedrockStaysWithinTwentyUpdates() {
        for (int configured : new int[]{1, 5, 14, 19, 20, 25, 60, 1000}) {
            int frames = SpinnerMath.frames(true, 26, configured);
            assertTrue(frames + 1 <= SpinnerMath.BEDROCK_MAX_UPDATES, "frames + final screen <= 20, got " + frames);
            assertTrue(frames >= 2);
        }
        assertEquals(26, SpinnerMath.frames(false, 26, 14), "Java uses its own frame count");
        assertEquals(14, SpinnerMath.frames(true, 26, 14));
        assertTrue(new CratesSettings().spinnerBedrockFrames + 1 <= SpinnerMath.BEDROCK_MAX_UPDATES);
    }

    @Test
    void frameAtFollowsTheSchedule() {
        int[] s = SpinnerMath.schedule(10, 60);
        assertEquals(0, SpinnerMath.frameAt(s, 0));
        int last = -1;
        for (int t = 0; t <= 80; t++) {
            int f = SpinnerMath.frameAt(s, t);
            assertTrue(f >= last, "never goes back");
            last = f;
        }
        assertEquals(9, SpinnerMath.frameAt(s, 60));
        assertEquals(9, SpinnerMath.frameAt(s, 1000));
    }

    /** The spinner shows a plan that was rolled (and saved) before; its strip always stops on that plan's reward. */
    @Test
    void spinnerStripStopsOnThePlansGrant() {
        CrateCatalog.Crate ember = Fixtures.bundled().crate("ember").orElseThrow();
        for (int seed = 0; seed < 50; seed++) {
            OpenPlan.Plan plan = OpenPlan.build(ember, new CrateMath.KeyUse(1, 0), 0, Fixtures.context(1, k -> false),
                    new Random(seed), UUID::randomUUID);
            OpenPlan.Grant decided = plan.grants().getFirst();
            int frames = SpinnerMath.frames(seed % 2 == 0, 26, 14);
            List<Integer> strip = SpinnerMenu.strip(ember, decided, frames);
            assertEquals(-1, SpinnerMath.window(strip, frames - 1).get(SpinnerMath.CENTER), "the middle slot is the decided grant");
            for (int i = 0; i < strip.size(); i++) {
                if (i != SpinnerMath.winnerIndex(frames)) {
                    assertTrue(strip.get(i) >= 0 && strip.get(i) < ember.rewards().size(), "decoration comes from the crate");
                }
            }
        }
    }
}
