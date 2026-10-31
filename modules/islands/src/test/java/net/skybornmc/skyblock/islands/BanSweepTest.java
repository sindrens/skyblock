package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanSweepTest {

    private record Isle(String name, UUID leader) {
    }

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void newlyBannedKeepsListOrder() {
        List<UUID> next = List.of(c, a, b);
        assertEquals(List.of(c, b), List.copyOf(BanSweep.newlyBanned(Set.of(a), next)));
        assertTrue(BanSweep.newlyBanned(Set.of(a, b, c), next).isEmpty());
        assertEquals(List.of(c, a, b), List.copyOf(BanSweep.newlyBanned(Set.of(), next)), "first refresh: everybody is new");
    }

    @Test
    void groupsIslandsPerLeaderInTheOldOrder() {
        List<Isle> isles = List.of(new Isle("1", a), new Isle("2", null), new Isle("3", b), new Isle("4", a), new Isle("5", c));
        Map<UUID, List<Isle>> out = BanSweep.byLeader(isles, Isle::leader, new LinkedHashSet<>(List.of(b, a)));
        assertEquals(List.of(b, a), List.copyOf(out.keySet()), "bans in list order");
        assertEquals(List.of(isles.get(2)), out.get(b));
        assertEquals(List.of(isles.get(0), isles.get(3)), out.get(a), "islands in island order");
        assertTrue(BanSweep.byLeader(isles, Isle::leader, Set.of()).isEmpty());
        assertTrue(BanSweep.byLeader(isles, Isle::leader, Set.of(UUID.randomUUID())).isEmpty(), "a ban without islands");
    }

    /** Same islands, same order as the old loop (per ban: walk every island) on random data. */
    @Test
    void matchesTheOldPerBanWalk() {
        Random rnd = new Random(8);
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            players.add(UUID.randomUUID());
        }
        for (int round = 0; round < 200; round++) {
            List<Isle> isles = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                isles.add(new Isle("i" + i, rnd.nextInt(5) == 0 ? null : players.get(rnd.nextInt(players.size()))));
            }
            Set<UUID> banned = new LinkedHashSet<>();
            for (int i = 0; i < 10; i++) {
                banned.add(players.get(rnd.nextInt(players.size())));
            }
            List<String> old = new ArrayList<>();
            for (UUID player : banned) {
                for (Isle isle : isles) {
                    if (isle.leader() != null && player.equals(isle.leader())) {
                        old.add(player + "/" + isle.name());
                    }
                }
            }
            List<String> now = new ArrayList<>();
            BanSweep.byLeader(isles, Isle::leader, banned).forEach((player, list) -> list.forEach(i -> now.add(player + "/" + i.name())));
            assertEquals(old, now);
        }
    }
}
