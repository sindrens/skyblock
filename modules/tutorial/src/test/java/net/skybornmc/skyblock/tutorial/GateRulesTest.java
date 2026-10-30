package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.Gates;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TUTORIAL §4 locks: parsing the gate lines and the open/locked rules. */
class GateRulesTest {

    private static final TutorialFlow FLOW = TutorialFlow.parse(new TutorialConfig().steps);
    private static final Map<String, String> GATES = GateRules.parse(new TutorialConfig().gates, FLOW).required();

    private static boolean open(String gate, State state, Set<String> done) {
        return GateRules.open(gate, GATES, FLOW, state, done, false);
    }

    @Test
    void defaultGatesParseWithoutProblems() {
        GateRules.Parsed parsed = GateRules.parse(new TutorialConfig().gates, FLOW);
        assertEquals(List.of(), parsed.problems());
        assertEquals("crop", parsed.required().get(Gates.ISLAND_TEAM));
        assertEquals("menu", parsed.required().get(Gates.DEPTHS));
        assertEquals("sell", parsed.required().get(Gates.AUCTION));
        assertEquals("bank", parsed.required().get(Gates.PAY));
        assertEquals("bank", parsed.required().get(Gates.WITHDRAW));
        assertEquals("finish", parsed.required().get(Gates.TRADE));
        assertEquals("finish", parsed.required().get(Gates.WARZONE));
        assertEquals("finish", parsed.required().get(Gates.PROFILE_CREATE));
    }

    @Test
    void parseAcceptsEveryWrittenFormAndReportsBadLines() {
        GateRules.Parsed parsed = GateRules.parse(List.of("pay:bank", "trade = finish", "{auction=sell}", "nonsense", "fly: bank",
                "pay2: x", "depths: teleport"), FLOW);
        assertEquals("bank", parsed.required().get("pay"));
        assertEquals("finish", parsed.required().get("trade"));
        assertEquals("sell", parsed.required().get("auction"));
        assertEquals(4, parsed.problems().size());
    }

    @Test
    void aGateWhoseStepLeftTheFlowOpensAtTheFinish() {
        TutorialFlow shortFlow = TutorialFlow.parse(List.of("portal", "arrive", "chest"));
        GateRules.Parsed parsed = GateRules.parse(List.of("pay: bank"), shortFlow);
        assertEquals("finish", parsed.required().get("pay"));
        assertEquals(1, parsed.problems().size());
    }

    @Test
    void staffBypassAlwaysOpens() {
        assertTrue(GateRules.open(Gates.PAY, GATES, FLOW, State.NEW, Set.of(), true));
        assertTrue(GateRules.open(Gates.PAY, GATES, FLOW, null, Set.of(), true));
    }

    @Test
    void notLoadedYetIsLockedButUnlistedFeaturesAreOpen() {
        assertFalse(open(Gates.PAY, null, Set.of()));
        assertTrue(GateRules.open(Gates.PAY, Map.of(), FLOW, null, Set.of(), false));
        assertTrue(open("something_else", State.NEW, Set.of()));
    }

    @Test
    void completeStatesOpenEverything() {
        for (State s : List.of(State.FINISHED, State.LEGACY, State.STAFF_SKIPPED)) {
            for (String gate : Gates.ALL) {
                assertTrue(open(gate, s, Set.of()), s + " " + gate);
            }
        }
    }

    @Test
    void newPlayersHaveEveryListedFeatureLocked() {
        for (String gate : GATES.keySet()) {
            assertFalse(open(gate, State.NEW, Set.of()), gate);
        }
    }

    @Test
    void featuresOpenStepByStep() {
        Set<String> done = Set.of("portal", "arrive", "chest");
        assertFalse(open(Gates.ISLAND_TEAM, State.ACTIVE, done));
        done = Set.of("portal", "arrive", "chest", "crop");
        assertTrue(open(Gates.ISLAND_TEAM, State.ACTIVE, done));
        assertFalse(open(Gates.DEPTHS, State.ACTIVE, done));
        done = Set.of("portal", "arrive", "chest", "crop", "menu");
        assertTrue(open(Gates.DEPTHS, State.ACTIVE, done));
        assertFalse(open(Gates.AUCTION, State.ACTIVE, done));
        done = Set.copyOf(FLOW.steps());
        assertTrue(open(Gates.PAY, State.ACTIVE, done));
        assertTrue(open(Gates.WITHDRAW, State.ACTIVE, done));
        assertFalse(open(Gates.TRADE, State.ACTIVE, done), "finish-only gates wait for the finish itself");
    }

    @Test
    void aMoneyStepDoneEarlyDoesNotOpenMoneyFeaturesWhileEarlierStepsAreMissing() {
        Set<String> done = Set.of("portal", "arrive", "chest", "bank");
        assertFalse(open(Gates.PAY, State.ACTIVE, done));
        assertFalse(open(Gates.AUCTION, State.ACTIVE, Set.of("portal", "sell")));
    }
}
