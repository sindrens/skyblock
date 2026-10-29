package net.skybornmc.skyblock.common.economy;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * One account owner: a player (their UUID) or an island (the island's UUID; numeric island ids use {@link #island(long)}).
 * An owner has one balance per {@link Currency}.
 */
public record AccountRef(AccountType type, UUID owner) implements Comparable<AccountRef> {

    /**
     * The order in which transfers lock account rows. Every transaction locks in this order, so two transfers between
     * the same accounts in opposite directions can never deadlock.
     */
    public static final Comparator<AccountRef> LOCK_ORDER = Comparator.comparing((AccountRef a) -> a.type().ordinal())
            .thenComparing(a -> a.owner().getMostSignificantBits(), Long::compareUnsigned)
            .thenComparing(a -> a.owner().getLeastSignificantBits(), Long::compareUnsigned);

    public AccountRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
    }

    public static AccountRef player(UUID uuid) {
        return new AccountRef(AccountType.PLAYER, uuid);
    }

    public static AccountRef island(UUID islandId) {
        return new AccountRef(AccountType.ISLAND, islandId);
    }

    /** An island with a numeric id: stored as the UUID {@code 00000000-0000-0000-<id>}. */
    public static AccountRef island(long islandId) {
        return new AccountRef(AccountType.ISLAND, new UUID(0L, islandId));
    }

    public boolean isPlayer() {
        return type == AccountType.PLAYER;
    }

    @Override
    public int compareTo(AccountRef other) {
        return LOCK_ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return type.key() + ":" + owner;
    }
}
