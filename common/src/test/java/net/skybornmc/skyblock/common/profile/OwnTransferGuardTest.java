package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4: the guard decision every transfer path uses (pay, auction, notes, trade, islands, item pickup). */
class OwnTransferGuardTest {

    private final UUID steve = UUID.randomUUID();
    private final UUID steve2 = UUID.randomUUID();
    private final UUID steve3 = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final Map<UUID, UUID> owners = Map.of(steve2, steve, steve3, steve);

    private final ProfileService profiles = new ProfileService() {
        @Override
        public UUID profileKey(UUID player) {
            return player;
        }

        @Override
        public CompletableFuture<UUID> profileKeyAsync(UUID player) {
            return CompletableFuture.completedFuture(player);
        }

        @Override
        public UUID ownerOf(UUID profileKey) {
            return owners.getOrDefault(profileKey, profileKey);
        }

        @Override
        public boolean isLive(UUID profileKey) {
            return true;
        }

        @Override
        public Optional<Profile> active(UUID player) {
            return Optional.empty();
        }

        @Override
        public List<Profile> profiles(UUID player) {
            return List.of();
        }

        @Override
        public CompletableFuture<List<Profile>> loadProfiles(UUID player) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public String activeName(Player player) {
            return "";
        }

        @Override
        public boolean isSwitching(UUID player) {
            return false;
        }

        @Override
        public CompletableFuture<Integer> seasonResetSlots(Actor actor) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public boolean blockOwnTransfer(AuditCategory category, String path, UUID fromKey, UUID toKey, Actor actor) {
            return ownOtherProfile(fromKey, toKey);
        }
    };

    @Test
    void everyPairOfOwnProfilesIsBlocked() {
        // profile 1 (key = player UUID) with profile 2 and 3, in both directions
        for (UUID[] pair : new UUID[][]{{steve, steve2}, {steve2, steve}, {steve, steve3}, {steve2, steve3}, {steve3, steve2}}) {
            assertTrue(profiles.ownOtherProfile(pair[0], pair[1]));
            assertTrue(profiles.blockOwnTransfer(AuditCategory.ECONOMY, "pay", pair[0], pair[1], Actor.SYSTEM));
            assertTrue(profiles.sameOwner(pair[0], pair[1]));
        }
    }

    @Test
    void otherPlayersAndTheSameProfileAreNotBlocked() {
        assertFalse(profiles.ownOtherProfile(steve2, alex));
        assertFalse(profiles.ownOtherProfile(alex, steve));
        assertFalse(profiles.ownOtherProfile(steve2, steve2), "the same profile is not a transfer between profiles");
        assertFalse(profiles.ownOtherProfile(null, steve));
        assertFalse(profiles.sameOwner(steve, alex));
    }
}
