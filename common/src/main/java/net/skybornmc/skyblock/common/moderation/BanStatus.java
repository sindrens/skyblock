package net.skybornmc.skyblock.common.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A small ban-status query (owner answer 522), published by the moderation module:
 * {@code core.services().optional(BanStatus.class)}. The islands module uses it to close a banned leader's island to
 * visitors while the team keeps playing. A new ban is also announced right away with {@link PlayerBannedEvent}; ended or
 * lifted bans are noticed by asking again (callers refresh on a timer).
 */
public interface BanStatus {

    /** True if this account has an active (not ended, not lifted) ban on this server. Async, completes off the main thread. */
    CompletableFuture<Boolean> isBanned(UUID player);

    /** Every account with an active ban right now (account bans; IP-wide bans count for their target). Async. */
    CompletableFuture<Set<UUID>> bannedAccounts();
}
