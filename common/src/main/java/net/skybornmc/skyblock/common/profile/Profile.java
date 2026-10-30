package net.skybornmc.skyblock.common.profile;

import java.util.Objects;
import java.util.UUID;

/**
 * One island profile of a player (owner decisions P1–P12, docs/DECISIONS_2026-10-30.md): one island and everything that
 * belongs to it. Immutable snapshot; ask {@link ProfileService} again for fresh values.
 *
 * @param id            the profile key every per-profile table stores (money, skills, island membership …). The first
 *                      profile of an account uses the player's own UUID, so all data from before profiles already
 *                      belongs to profile 1 (migration-safe); later profiles get a random UUID.
 * @param owner         the player (account) UUID: rank, gems, cosmetics, punishments and the audit identity live here
 * @param slot          1 to {@link SlotRules#MAX_SLOTS}
 * @param name          the player's own name for it, {@code null} = default name ("Profile 2") from the messages
 * @param icon          material key of the menu icon, {@code null} = the slot's default icon
 * @param createdAt     epoch millis
 * @param lastPlayedAt  epoch millis of the last switch to or from it (0 = never played)
 * @param playtimeMillis playtime on this profile (excluding the running session)
 */
public record Profile(UUID id, UUID owner, int slot, String name, String icon, long createdAt, long lastPlayedAt,
                      long playtimeMillis) {

    public Profile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
    }

    /** True if this is the account's legacy/first profile whose key is the player UUID. */
    public boolean usesOwnerKey() {
        return id.equals(owner);
    }

    public Profile withName(String newName) {
        return new Profile(id, owner, slot, newName, icon, createdAt, lastPlayedAt, playtimeMillis);
    }

    public Profile withIcon(String newIcon) {
        return new Profile(id, owner, slot, name, newIcon, createdAt, lastPlayedAt, playtimeMillis);
    }

    public Profile withPlay(long lastPlayed, long playtime) {
        return new Profile(id, owner, slot, name, icon, createdAt, lastPlayed, playtime);
    }
}
