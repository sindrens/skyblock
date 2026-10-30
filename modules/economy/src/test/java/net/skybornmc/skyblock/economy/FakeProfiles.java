package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.ProfileService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** In-memory profiles for tests: profile keys, owners, the active profile and recorded P4 blocks. */
final class FakeProfiles implements ProfileService {

    final Map<UUID, UUID> owners = new HashMap<>();
    final Map<UUID, UUID> active = new HashMap<>();
    final Set<UUID> deleted = new HashSet<>();
    final List<String> blocked = new ArrayList<>();

    /** Adds a profile of {@code owner}; returns its key. */
    UUID add(UUID owner, UUID key) {
        owners.put(key, owner);
        owners.putIfAbsent(owner, owner);
        active.putIfAbsent(owner, owner);
        return key;
    }

    void switchTo(UUID owner, UUID key) {
        active.put(owner, key);
    }

    @Override
    public UUID profileKey(UUID player) {
        return active.getOrDefault(player, player);
    }

    @Override
    public CompletableFuture<UUID> profileKeyAsync(UUID player) {
        return CompletableFuture.completedFuture(profileKey(player));
    }

    @Override
    public UUID ownerOf(UUID profileKey) {
        return owners.getOrDefault(profileKey, profileKey);
    }

    @Override
    public boolean isLive(UUID profileKey) {
        return !deleted.contains(profileKey);
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
        if (ownOtherProfile(fromKey, toKey)) {
            blocked.add(path);
            return true;
        }
        return false;
    }
}
