package net.skybornmc.skyblock.pass;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which tiers are claimable: reached, not claimed, premium only with premium, unavailable rewards wait. */
class PassRulesTest {

    private final PassMath.Curve curve = new PassMath.Curve(100, 800, 2_000);
    private final PassTable table = PassTable.parse(List.of("money 2", "crystals 20"), List.of("5: key ember 2", "10: tag rare_tag"),
            List.of("crystals 60", "key ember"), List.of("5: title pass_spark", "10: title pass_kindled"), 100, p -> { });

    @Test
    void reachedTiersAreClaimableOnce() {
        long xp = 5 * 800 + 10;
        List<PassRules.TierRef> c = PassRules.claimable(xp, curve, table, Set.of(), Set.of(), false, r -> true, 100);
        assertEquals(List.of(1, 2, 3, 4, 5), c.stream().map(PassRules.TierRef::tier).toList());
        List<PassRules.TierRef> after = PassRules.claimable(xp, curve, table, Set.of(1, 2, 3), Set.of(), false, r -> true, 100);
        assertEquals(List.of(4, 5), after.stream().map(PassRules.TierRef::tier).toList());
        assertEquals(PassRules.Block.CLAIMED, PassRules.block(PassTable.Track.FREE, 1, xp, curve, table, Set.of(1), false, r -> true));
        assertEquals(PassRules.Block.LOCKED, PassRules.block(PassTable.Track.FREE, 6, xp, curve, table, Set.of(), false, r -> true));
        assertEquals(2, PassRules.claimable(xp, curve, table, Set.of(), Set.of(), false, r -> true, 2).size(), "limit");
    }

    @Test
    void premiumTiersNeedPremium() {
        long xp = 10 * 800;
        assertEquals(PassRules.Block.NO_PREMIUM, PassRules.block(PassTable.Track.PREMIUM, 5, xp, curve, table, Set.of(), false, r -> true));
        assertNull(PassRules.block(PassTable.Track.PREMIUM, 5, xp, curve, table, Set.of(), true, r -> true));
        assertEquals(PassRules.Block.NOTHING, PassRules.block(PassTable.Track.PREMIUM, 6, xp, curve, table, Set.of(), true, r -> true));
        List<PassRules.TierRef> free = PassRules.claimable(xp, curve, table, Set.of(), Set.of(), false, r -> true, 100);
        assertTrue(free.stream().allMatch(t -> t.track() == PassTable.Track.FREE), "no premium tier without premium");
        List<PassRules.TierRef> both = PassRules.claimable(xp, curve, table, Set.of(), Set.of(5), true, r -> true, 100);
        assertEquals(List.of(new PassRules.TierRef(PassTable.Track.PREMIUM, 10)),
                both.stream().filter(t -> t.track() == PassTable.Track.PREMIUM).toList(), "level 5 premium claimed on another profile");
    }

    @Test
    void unavailableRewardsWaitInsteadOfBeingLost() {
        long xp = 10 * 800;
        List<PassRules.TierRef> c = PassRules.claimable(xp, curve, table, Set.of(), Set.of(), false,
                r -> r.kind() != PassTable.Kind.TAG, 100);
        assertTrue(c.stream().noneMatch(t -> t.tier() == 10), "the unknown tag keeps tier 10 waiting");
        assertEquals(PassRules.Block.UNAVAILABLE, PassRules.block(PassTable.Track.FREE, 10, xp, curve, table, Set.of(), false,
                r -> r.kind() != PassTable.Kind.TAG));
    }

    @Test
    void overflowTiersAfterTheLastLevel() {
        long xp = 80_000 + 2 * 2_000;
        Set<Integer> all = new java.util.HashSet<>();
        for (int i = 1; i <= 100; i++) {
            all.add(i);
        }
        List<PassRules.TierRef> c = PassRules.claimable(xp, curve, table, all, Set.of(), false, r -> true, 100);
        assertEquals(List.of(101, 102), c.stream().map(PassRules.TierRef::tier).toList());
        assertEquals(PassRules.Block.NOTHING, PassRules.block(PassTable.Track.PREMIUM, 101, xp, curve, table, Set.of(), true, r -> true));
    }
}
