package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** {@link ProfileService#IDENTITY}: one profile per player, keyed by the player UUID (the state before profiles). */
final class IdentityProfiles implements ProfileService {

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
        return profileKey;
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
    public boolean blockOwnTransfer(AuditCategory category, String path, UUID fromKey, UUID toKey, Actor actor) {
        return false;
    }
}
