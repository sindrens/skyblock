package net.skybornmc.skyblock.common.profile;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** P7: leaderboards show one entry per player, from their best live profile. */
class BestProfileTest {

    private final UUID steve = UUID.randomUUID();
    private final UUID steve2 = UUID.randomUUID();
    private final UUID steve3 = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID alex2 = UUID.randomUUID();
    private final Map<UUID, UUID> owners = Map.of(steve, steve, steve2, steve, steve3, steve, alex, alex, alex2, alex);

    private UUID owner(UUID key) {
        return owners.getOrDefault(key, key);
    }

    @Test
    void onePlayerOneEntryWithTheBestProfile() {
        Map<UUID, Long> values = new HashMap<>();
        values.put(steve, 100L);
        values.put(steve2, 5_000L);
        values.put(steve3, 10L);
        values.put(alex, 3_000L);
        values.put(alex2, 4_000L);
        List<BestProfile.Entry> top = BestProfile.collapse(values, this::owner, k -> true);
        assertEquals(2, top.size());
        assertEquals(new BestProfile.Entry(steve, steve2, 5_000L), top.get(0));
        assertEquals(new BestProfile.Entry(alex, alex2, 4_000L), top.get(1));
    }

    @Test
    void deletedProfilesDoNotCount() {
        Map<UUID, Long> values = Map.of(steve, 100L, steve2, 9_999L);
        Set<UUID> deleted = Set.of(steve2);
        List<BestProfile.Entry> top = BestProfile.collapse(values, this::owner, k -> !deleted.contains(k));
        assertEquals(List.of(new BestProfile.Entry(steve, steve, 100L)), top);
    }

    @Test
    void unknownKeysArePlayersAndTopIsCut() {
        UUID legacy = UUID.randomUUID();
        Map<UUID, Long> values = Map.of(legacy, 50L, steve, 70L, alex, 60L);
        List<BestProfile.Entry> top = BestProfile.top(values, this::owner, k -> true, 2);
        assertEquals(2, top.size());
        assertEquals(steve, top.get(0).owner());
        assertEquals(alex, top.get(1).owner());
        assertEquals(legacy, BestProfile.collapse(values, this::owner, k -> true).get(2).owner());
    }

    @Test
    void identityServiceUsesTheHelperToo() {
        Map<UUID, Long> values = Map.of(steve, 1L, alex, 2L);
        List<BestProfile.Entry> top = ProfileService.IDENTITY.bestPerOwner(values);
        assertEquals(alex, top.get(0).owner());
        assertEquals(steve, top.get(1).owner());
    }
}
