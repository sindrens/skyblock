package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner RW3: one earning account per IP address, plus the per-IP daily cap. */
class IpGateTest {

    private final UUID main = UUID.randomUUID();
    private final UUID alt = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void firstInHoldsTheIpOthersArePaused() {
        IpGate gate = new IpGate();
        Set<UUID> blocked = gate.update(List.of(new IpGate.Candidate(alt, "1.2.3.4", 200), new IpGate.Candidate(main, "1.2.3.4", 100),
                new IpGate.Candidate(other, "5.6.7.8", 300)));
        assertEquals(Set.of(alt), blocked);
        assertEquals(main, gate.holder("1.2.3.4"));
    }

    @Test
    void holderKeepsTheIpUntilItLeavesThenTheNextTakesOver() {
        IpGate gate = new IpGate();
        gate.update(List.of(new IpGate.Candidate(main, "1.2.3.4", 100)));
        // An alt that "entered earlier" (e.g. a rejoin with an old timestamp) does not steal a held IP.
        assertEquals(Set.of(alt), gate.update(List.of(new IpGate.Candidate(main, "1.2.3.4", 100), new IpGate.Candidate(alt, "1.2.3.4", 50))));
        assertEquals(Set.of(), gate.update(List.of(new IpGate.Candidate(alt, "1.2.3.4", 50))));
        assertEquals(alt, gate.holder("1.2.3.4"));
        assertEquals(Set.of(main), gate.update(List.of(new IpGate.Candidate(main, "1.2.3.4", 400), new IpGate.Candidate(alt, "1.2.3.4", 50))));
    }

    @Test
    void ipDailyCapSharedByAllAccountsAndResetNextDay() {
        IpGate gate = new IpGate();
        long cap = 480 * 60_000L;
        gate.add("1.2.3.4", 20261030, 300 * 60_000L);
        gate.add("1.2.3.4", 20261030, 180 * 60_000L);
        assertEquals(0, gate.remaining("1.2.3.4", 20261030, cap));
        assertEquals(cap, gate.remaining("5.6.7.8", 20261030, cap));
        assertEquals(cap, gate.remaining("1.2.3.4", 20261031, cap));
        assertEquals(Long.MAX_VALUE, gate.remaining("1.2.3.4", 20261030, 0), "0 = off");
        gate.prune(20261031);
        gate.add("1.2.3.4", 20261031, 60_000);
        assertEquals(cap - 60_000, gate.remaining("1.2.3.4", 20261031, cap));
    }
}
