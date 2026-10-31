package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner R3-3: staff family whitelist (several accounts earn together on one connection). */
class FamilyGroupsTest {

    private final UUID mum = UUID.randomUUID();
    private final UUID kid = UUID.randomUUID();
    private final UUID kid2 = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void parsesLabelledAndUnlabelledEntries() {
        FamilyGroups f = FamilyGroups.parse(List.of("smith: " + mum + ", " + kid, kid2 + " " + stranger));
        assertEquals("smith", f.of(mum));
        assertEquals("smith", f.of(kid));
        assertEquals("family-2", f.of(kid2));
        assertEquals("family-2", f.of(stranger));
        assertTrue(f.problems().isEmpty());
    }

    @Test
    void badEntriesAreReportedAndSkipped() {
        FamilyGroups f = FamilyGroups.parse(List.of("a: " + mum + ", not-a-uuid, " + kid, "b: " + mum + ", " + kid2, "c: " + stranger));
        assertEquals("a", f.of(mum)); // first family wins
        assertEquals("b", f.of(kid2));
        // bad uuid, mum twice, b keeps only kid2 (fewer than 2), c has one account
        assertEquals(4, f.problems().size());
    }

    @Test
    void emptyIsNone() {
        assertNull(FamilyGroups.parse(List.of()).of(mum));
        assertNull(FamilyGroups.parse(null).of(mum));
    }

    @Test
    void familyEarnsTogetherStrangerWaits() {
        FamilyGroups f = FamilyGroups.parse(List.of("smith: " + mum + ", " + kid + ", " + kid2));
        IpGate gate = new IpGate();
        Set<UUID> blocked = gate.update(List.of(new IpGate.Candidate(mum, "1.2.3.4", 100), new IpGate.Candidate(kid, "1.2.3.4", 200),
                new IpGate.Candidate(stranger, "1.2.3.4", 150)), f::of);
        assertEquals(Set.of(stranger), blocked);
    }

    @Test
    void strangerHoldingTheIpKeepsTheFamilyOut() {
        FamilyGroups f = FamilyGroups.parse(List.of("smith: " + mum + ", " + kid));
        IpGate gate = new IpGate();
        Set<UUID> blocked = gate.update(List.of(new IpGate.Candidate(stranger, "1.2.3.4", 50), new IpGate.Candidate(mum, "1.2.3.4", 100),
                new IpGate.Candidate(kid, "1.2.3.4", 200)), f::of);
        assertEquals(Set.of(mum, kid), blocked);
        // The stranger leaves: the family takes over together.
        assertEquals(Set.of(), gate.update(List.of(new IpGate.Candidate(mum, "1.2.3.4", 100), new IpGate.Candidate(kid, "1.2.3.4", 200)), f::of));
    }

    @Test
    void familyMembersGetTheirOwnIpCap() {
        assertEquals("1.2.3.4", IpGate.capKey("1.2.3.4", mum, false));
        assertEquals("1.2.3.4#" + mum, IpGate.capKey("1.2.3.4", mum, true));
        IpGate gate = new IpGate();
        long cap = 480 * 60_000L;
        gate.add(IpGate.capKey("1.2.3.4", mum, true), 1, cap);
        assertEquals(0, gate.remaining(IpGate.capKey("1.2.3.4", mum, true), 1, cap));
        assertEquals(cap, gate.remaining(IpGate.capKey("1.2.3.4", kid, true), 1, cap));
    }
}
