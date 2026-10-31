package net.skybornmc.skyblock.quests;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pool lines, live-content filtering and how a set is picked. */
class QuestPoolTest {

    private static final QuestPool.Unlocks NOTHING = new QuestPool.Unlocks(false, 0, 0, 0, false, false, false, false, false);
    private static final QuestPool.Unlocks EVERYTHING = new QuestPool.Unlocks(true, 5, 4, 3, true, true, true, true, true);

    private static List<QuestPool.Entry> pool(String... lines) {
        List<String> problems = new ArrayList<>();
        List<QuestPool.Entry> out = QuestPool.parse(List.of(lines), "test", problems::add);
        assertEquals(List.of(), problems);
        return out;
    }

    @Test
    void parsesAutoAndFixedTargets() {
        assertEquals(new QuestPool.Entry(QuestType.MINE, 0), QuestPool.parse("mine").orElseThrow());
        assertEquals(new QuestPool.Entry(QuestType.SELL, 0), QuestPool.parse(" SELL ").orElseThrow());
        assertEquals(new QuestPool.Entry(QuestType.VISIT, 3), QuestPool.parse("visit 3").orElseThrow());
        assertEquals(new QuestPool.Entry(QuestType.MINE, 1500), QuestPool.parse("mine 1,500").orElseThrow());
        assertTrue(QuestPool.parse("visit").isEmpty(), "visit needs a number");
        assertTrue(QuestPool.parse("forge 0").isEmpty());
        assertTrue(QuestPool.parse("fishing 30").isEmpty(), "no fishing before fishing exists");
        assertTrue(QuestPool.parse("mine 3 extra").isEmpty());
        assertTrue(QuestPool.parse("").isEmpty());
    }

    @Test
    void reportsBrokenAndDoubleLines() {
        List<String> problems = new ArrayList<>();
        List<QuestPool.Entry> out = QuestPool.parse(List.of("mine", "mine 400", "boss 1", "like 1"), "daily.pool", problems::add);
        assertEquals(List.of(QuestType.MINE, QuestType.LIKE), out.stream().map(QuestPool.Entry::type).toList());
        assertEquals(2, problems.size());
    }

    @Test
    void bundledDefaultsParseCleanly() {
        QuestsSettings s = new QuestsSettings();
        for (List<String> lines : List.of(s.dailyPool, s.weeklyPool, s.islandPool)) {
            List<String> problems = new ArrayList<>();
            QuestPool.parse(lines, "defaults", problems::add);
            assertEquals(List.of(), problems);
        }
    }

    @Test
    void onlyLiveContentIsOffered() {
        List<QuestPool.Entry> pool = pool("mine", "chop", "kill", "sell", "tool_levels 1", "forge 1", "enchant 1", "visit 3", "like 1");
        assertEquals(List.of(), QuestPool.eligible(pool, NOTHING));
        assertEquals(pool, QuestPool.eligible(pool, EVERYTHING));

        // A new player: the tutorial has not opened the Depths or island visits yet, no tool, can sell.
        QuestPool.Unlocks fresh = new QuestPool.Unlocks(false, 1, 1, 1, true, false, true, false, false);
        assertEquals(List.of(QuestType.SELL), QuestPool.eligible(pool, fresh).stream().map(QuestPool.Entry::type).toList());

        // The Depths are open but the combat wing has no open tier: no kill quests.
        QuestPool.Unlocks noCombat = new QuestPool.Unlocks(true, 2, 1, 0, true, true, true, true, false);
        List<QuestType> types = QuestPool.eligible(pool, noCombat).stream().map(QuestPool.Entry::type).toList();
        assertTrue(types.containsAll(List.of(QuestType.MINE, QuestType.CHOP, QuestType.VISIT, QuestType.ENCHANT)));
        assertFalse(types.contains(QuestType.KILL));
        assertFalse(types.contains(QuestType.LIKE), "likes need an hour of playtime first");

        // Enchant quests need a tool to enchant.
        QuestPool.Unlocks noTool = new QuestPool.Unlocks(true, 2, 1, 1, true, false, true, true, true);
        assertFalse(QuestPool.eligible(pool, noTool).stream().anyMatch(e -> e.type() == QuestType.ENCHANT));
    }

    @Test
    void pickNeverRepeatsATypeAndTakesOneSocialQuest() {
        List<QuestPool.Entry> pool = pool("mine", "chop", "kill", "sell", "visit 3", "like 1");
        for (int seed = 0; seed < 200; seed++) {
            List<QuestPool.Entry> picked = QuestPool.pick(pool, 5, Set.of(), new Random(seed));
            assertEquals(5, picked.size());
            Set<QuestType> types = EnumSet.noneOf(QuestType.class);
            int social = 0;
            for (QuestPool.Entry e : picked) {
                assertTrue(types.add(e.type()), "type twice: " + picked);
                social += e.type().social() ? 1 : 0;
            }
            assertTrue(social <= 1, "two social quests: " + picked);
        }
    }

    @Test
    void pickGivesFewerWhenThePoolIsSmall() {
        List<QuestPool.Entry> pool = pool("visit 3", "like 1", "sell");
        assertEquals(2, QuestPool.pick(pool, 3, Set.of(), new Random(1)).size());
        assertEquals(0, QuestPool.pick(List.of(), 3, Set.of(), new Random(1)).size());
    }

    @Test
    void swapPicksAnotherTypeThanTheSetHas() {
        List<QuestPool.Entry> pool = pool("mine", "chop", "kill", "visit 3", "like 1");
        for (int seed = 0; seed < 100; seed++) {
            List<QuestPool.Entry> one = QuestPool.pick(pool, 1, EnumSet.of(QuestType.MINE, QuestType.CHOP, QuestType.VISIT), new Random(seed));
            assertEquals(List.of(QuestType.KILL), one.stream().map(QuestPool.Entry::type).toList(), "like is social like visit");
        }
        assertEquals(0, QuestPool.pick(pool, 1, EnumSet.of(QuestType.MINE, QuestType.CHOP, QuestType.KILL, QuestType.LIKE),
                new Random(3)).size());
    }
}
