package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reward lines, the calendar list, thresholds and the payout math. */
class RewardTablesTest {

    private static RewardSpec spec(String line) {
        List<String> problems = new ArrayList<>();
        RewardSpec s = RewardSpec.parse(line, problems::add).orElse(null);
        assertEquals(List.of(), problems, line);
        return s;
    }

    private static List<String> problems(String line) {
        List<String> problems = new ArrayList<>();
        assertEquals(Optional.empty(), RewardSpec.parse(line, problems::add));
        return problems;
    }

    @Test
    void parsesEveryRewardType() {
        assertEquals(new RewardSpec(RewardSpec.Kind.MONEY, "", 3, 0, -1), spec("money 3"));
        assertEquals(new RewardSpec(RewardSpec.Kind.CRYSTALS, "", 20, 0, -1), spec("crystals 20"));
        assertEquals(new RewardSpec(RewardSpec.Kind.CREDITS, "", 5, 0, -1), spec("gems 5"));
        assertEquals(new RewardSpec(RewardSpec.Kind.KEY, "ember", 1, 0, -1), spec("key ember"));
        assertEquals(new RewardSpec(RewardSpec.Kind.KEY, "cinder", 2, 0, -1), spec("Key Cinder 2"));
        assertEquals(new RewardSpec(RewardSpec.Kind.FUEL, "", 120, 0, 20), spec("fuel 120 fallback 20"));
        assertEquals(new RewardSpec(RewardSpec.Kind.BOOSTER, "skill_xp", 25, 15, -1), spec("booster skill_xp 25 15"));
        assertEquals(new RewardSpec(RewardSpec.Kind.BOOK, "common", 1, 0, 30), spec("book common fallback 30"));
        assertEquals(new RewardSpec(RewardSpec.Kind.BOOK, "rare", 2, 0, -1), spec("book rare 2"));
        assertEquals(new RewardSpec(RewardSpec.Kind.SCROLL, "", 1, 0, -1), spec("scroll"));
        assertEquals(new RewardSpec(RewardSpec.Kind.TOME, "", 1, 0, 120), spec("tome fallback 120"));
    }

    @Test
    void reportsBrokenLines() {
        assertFalse(problems("").isEmpty());
        assertFalse(problems("cake 3").isEmpty());
        assertFalse(problems("money").isEmpty());
        assertFalse(problems("money -5").isEmpty());
        assertFalse(problems("money 0").isEmpty());
        assertFalse(problems("booster sell 25").isEmpty());
        assertFalse(problems("fuel 60 fallback").isEmpty());
        assertFalse(problems("fuel 60 fallback x").isEmpty());
        assertFalse(problems("tome 3").isEmpty());
        assertFalse(problems("money 99999999999").isEmpty());
    }

    @Test
    void bundledCalendarMatchesThePlan() {
        List<String> problems = new ArrayList<>();
        RewardsSettings s = new RewardsSettings();
        List<RewardSpec> cal = RewardTables.calendar(s.days, RewardTables.filler(s.defaultFallback), problems::add);
        assertEquals(List.of(), problems);
        assertEquals(30, cal.size());
        assertEquals(RewardSpec.Kind.MONEY, cal.get(0).kind());
        assertEquals(3, cal.get(0).amount());
        assertEquals("ember", cal.get(2).ref());
        assertEquals("cinder", cal.get(6).ref());
        assertEquals("blaze", cal.get(13).ref());
        assertEquals("inferno", cal.get(27).ref());
        assertEquals("eclipse", cal.get(29).ref());
        assertEquals(RewardSpec.Kind.TOME, cal.get(24).kind());
        assertEquals(RewardSpec.Kind.SCROLL, cal.get(19).kind());
        // P10-17: keys on days 3, 7, 10, 14, 17, 21, 24, 28, 30.
        List<Integer> keyDays = new ArrayList<>();
        for (int i = 0; i < cal.size(); i++) {
            if (cal.get(i).kind() == RewardSpec.Kind.KEY) {
                keyDays.add(i + 1);
            }
        }
        assertEquals(List.of(3, 7, 10, 14, 17, 21, 24, 28, 30), keyDays);
    }

    @Test
    void missingAndBrokenDaysAreFilled() {
        List<String> problems = new ArrayList<>();
        RewardSpec filler = RewardTables.filler(30);
        List<RewardSpec> cal = RewardTables.calendar(List.of("1: money 3", "3: cake", "4: key ember", "4: money 1", "x: y", "99: gems 1"),
                filler, problems::add);
        assertEquals(4, cal.size());
        assertEquals(filler, cal.get(1));
        assertEquals(filler, cal.get(2));
        assertEquals(RewardSpec.Kind.KEY, cal.get(3).kind());
        assertEquals(5, problems.size(), problems.toString());
        assertEquals(List.of(filler), RewardTables.calendar(List.of(), filler, p -> { }));
    }

    @Test
    void thresholdsAndCrossing() {
        List<String> problems = new ArrayList<>();
        NavigableMap<Integer, String> t = RewardTables.thresholds(List.of("100: veteran", "50: regular", "bad", "50: again"), "x", problems::add);
        assertEquals(2, problems.size());
        assertEquals(List.of(50, 100), List.copyOf(t.keySet()));
        assertEquals(List.of(50), RewardTables.crossed(t, 0, 99));
        assertEquals(List.of(50, 100), RewardTables.crossed(t, 0, 100));
        assertEquals(List.of(), RewardTables.crossed(t, 100, 300));
        assertEquals(List.of(100), RewardTables.crossed(t, 50, 100));
    }

    @Test
    void pouchMathScalesWithTier() {
        long[] ref = {827, 1467, 2560};
        assertEquals(3 * 827, RewardTables.money(3, 1, ref));
        assertEquals(3 * 1467, RewardTables.money(3, 2, ref));
        assertEquals(3 * 2560, RewardTables.money(3, 9, ref), "above the list the last value is used");
        assertEquals(0, RewardTables.money(3, 1, new long[0]));
        assertEquals(20, RewardTables.crystals(20, 1, 1.3));
        assertEquals(26, RewardTables.crystals(20, 2, 1.3));
        assertEquals(34, RewardTables.crystals(20, 3, 1.3));
        assertEquals(3, RewardTables.tierFor(12, new int[]{0, 5, 10, 15}));
        assertEquals(1, RewardTables.tierFor(0, new int[]{0, 5}));
    }

    private static Payout.Context ctx(int tier, boolean live, List<String> books) {
        return new Payout.Context(tier, new long[]{100, 200}, 1.3, 30, s -> live, r -> books, new Random(1));
    }

    @Test
    void payoutSplitsCurrenciesAndGrants() {
        Payout p = new Payout().add(spec("money 3"), ctx(2, true, List.of()))
                .add(spec("crystals 20"), ctx(2, true, List.of()))
                .add(spec("key ember 2"), ctx(2, true, List.of()))
                .add(spec("booster sell 25 15"), ctx(2, true, List.of()))
                .credits(10);
        assertEquals(600, p.total(Currency.MONEY));
        assertEquals(26, p.total(Currency.CRYSTALS));
        assertEquals(10, p.total(Currency.CREDITS_EARNED));
        assertEquals(List.of(new Payout.Grant(Payout.KEY, "ember", 2), new Payout.Grant(Payout.BOOSTER, "sell:25:15", 1)), p.grants());
        assertEquals(5, p.lines().size());
    }

    @Test
    void missingSystemsPayTheirFallback() {
        Payout p = new Payout().add(spec("fuel 120 fallback 20"), ctx(1, false, List.of()))
                .add(spec("tome"), ctx(1, true, List.of()))
                .add(spec("book common"), ctx(1, true, List.of()));
        assertTrue(p.grants().isEmpty());
        assertEquals(20 + 30 + 30, p.total(Currency.CRYSTALS));
        assertTrue(p.lines().stream().allMatch(Payout.Line::fallback));
    }

    @Test
    void booksArePickedFromThePool() {
        Payout p = new Payout().add(spec("book common 2"), ctx(1, true, List.of("swiftness")));
        assertEquals(List.of(new Payout.Grant(Payout.BOOK, "swiftness:2", 1)), p.grants());
        assertTrue(p.totals().isEmpty());
    }
}
