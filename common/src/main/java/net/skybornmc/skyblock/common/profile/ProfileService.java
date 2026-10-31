package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Island profiles (owner decisions P1–P12 + round 3 PR1–PR5): a player has up to 3 profiles; a profile is one island and
 * everything that belongs to it. Published by the profiles module; get it with {@link #of(ServiceRegistry)} (falls back
 * to {@link #IDENTITY}, i.e. "every player has exactly one profile", when the module is off).
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li><b>Player UUID</b> (account): rank, credits, titles/tags, cosmetics, nickname, punishments, island join limits,
 *       likes/ratings, audit identity.</li>
 *   <li><b>Profile key</b> ({@link #profileKey}): money, crystals, shards, skills, Depths unlocks, island membership and
 *       homes, auction listings and collection bin, money notes, settings, playtime, inventory/ender chest/XP.
 *       The first profile of every account uses the player UUID as its key, so all data from before profiles is
 *       profile 1 without moving a single row.</li>
 * </ul>
 * Code that stores per-profile data stores the profile key; code that stores account data stores the player UUID.
 * Anything that is <b>paid out later</b> (auction sales, notes) stores the profile key it belongs to and never resolves
 * the player's active profile at payout time.
 *
 * <h2>No transfers between own profiles (P4)</h2>
 * Every path that moves value between two parties (pay, auction buy, note redeem, trade, island invite/coop …) checks
 * {@link #blockOwnTransfer}; it refuses and audits {@code profile_transfer_blocked} when both sides are profiles of the
 * same player.
 */
public interface ProfileService {

    /** Audit action written when a transfer between two profiles of the same player was refused. */
    String AUDIT_BLOCKED = "profile_transfer_blocked";

    /** Fallback when the profiles module is off: every player has one profile whose key is the player UUID. */
    ProfileService IDENTITY = new IdentityProfiles();

    /** The published service, or {@link #IDENTITY}. Cheap: look it up per call instead of caching it. */
    static ProfileService of(ServiceRegistry services) {
        return services.optional(ProfileService.class).orElse(IDENTITY);
    }

    // ------------------------------------------------------------------ keys

    /**
     * The active profile key of an <b>online</b> player. For a player who is offline or still loading this is the player
     * UUID; use {@link #profileKeyAsync} for offline players.
     */
    UUID profileKey(UUID player);

    default UUID profileKey(Player player) {
        return profileKey(player.getUniqueId());
    }

    /** The active (for offline players: last active) profile key, from the database when needed. */
    CompletableFuture<UUID> profileKeyAsync(UUID player);

    /** The player (account) a profile key belongs to; an unknown key is treated as a player UUID and returned as is. */
    UUID ownerOf(UUID profileKey);

    /** True if both keys belong to the same player (two profiles, or the same profile). */
    default boolean sameOwner(UUID a, UUID b) {
        return a != null && b != null && ownerOf(a).equals(ownerOf(b));
    }

    /** True if {@code a} and {@code b} are two <b>different</b> profiles of the same player. */
    default boolean ownOtherProfile(UUID a, UUID b) {
        return a != null && b != null && !a.equals(b) && sameOwner(a, b);
    }

    /** The online player whose <b>active</b> profile is {@code profileKey}. */
    default Optional<Player> onlinePlayer(UUID profileKey) {
        Player p = Bukkit.getPlayer(ownerOf(profileKey));
        return p != null && profileKey(p.getUniqueId()).equals(profileKey) ? Optional.of(p) : Optional.empty();
    }

    /** False for deleted profiles. Unknown keys (player UUIDs without a profile row yet) count as live. */
    boolean isLive(UUID profileKey);

    // ------------------------------------------------------------------ profiles

    /** The active profile of an online player (empty while loading or when the module is off). */
    Optional<Profile> active(UUID player);

    /** Live profiles of an online player, by slot (empty while loading). */
    List<Profile> profiles(UUID player);

    /** Live profiles of any player, by slot, from the database when needed. */
    CompletableFuture<List<Profile>> loadProfiles(UUID player);

    /** Shown name of the active profile ({@code <ph:profile_name>}), e.g. "Profile 1" or the player's own name for it. */
    String activeName(Player player);

    /** True while a switch of this player runs (their inventory is being saved/loaded: refuse item actions). */
    boolean isSwitching(UUID player);

    // ------------------------------------------------------------------ guards and leaderboards

    /**
     * Owner decision P4: refuses a transfer between two different profiles of the same player. Returns true (= refuse)
     * and writes the audit row {@code profile_transfer_blocked} (details: path, both keys) when blocked.
     *
     * @param category audit category of the path (economy, auction, island, trade …)
     * @param path     short id of the path, e.g. {@code "pay"}, {@code "ah_buy"}, {@code "note_redeem"}, {@code "island_join"}
     */
    boolean blockOwnTransfer(AuditCategory category, String path, UUID fromKey, UUID toKey, Actor actor);

    /**
     * Owner decision P8, for the season reset (Phase 10): drops every stored slot unlock that was not bought with credits
     * (credit-bought slots are kept; rank slots are live permissions anyway). Audited. Completes with the number removed.
     */
    CompletableFuture<Integer> seasonResetSlots(Actor actor);

    /**
     * Owner decision P7: one leaderboard entry per player from their best live profile, highest first.
     *
     * @param valuesByProfile a value per profile key (e.g. money balance per account row)
     */
    default List<BestProfile.Entry> bestPerOwner(Map<UUID, Long> valuesByProfile) {
        return BestProfile.collapse(valuesByProfile, this::ownerOf, this::isLive);
    }
}
