package net.skybornmc.skyblock.common.crate;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Crate keys as seen by other modules (AFK pool, later votes, quests, events). Published by the crates module once it
 * exists: {@code core.services().optional(CrateKeys.class)}. Keys are earned only (DESIGN, owner decision on crates).
 *
 * <p>Until the crates module is there, modules that hand out keys keep them as pending grants and deliver them through
 * this service as soon as it is published (the AFK pool does this), so no key is ever lost.</p>
 *
 * <p>Owner answer 7: if keys or rewards are physical items, the crates module hands them out with
 * {@link net.skybornmc.skyblock.common.box.ItemBox} ({@code BoxSource.CRATE}, or {@code BoxSource.AFK} for AFK pool keys)
 * so a full inventory never drops them; for an offline profile it delivers straight into that profile's box.</p>
 */
public interface CrateKeys {

    /** True if a crate with this id ({@code ember}, {@code cinder} ...) exists. */
    boolean exists(String crateId);

    /**
     * Gives {@code amount} keys of a crate to a profile (saved, logged by the implementation). Completes with false if
     * the crate is unknown or the keys could not be stored; the caller keeps them and tries again later.
     *
     * @param profileKey the profile that earned the keys (never resolved to the active profile later)
     * @param reason     short snake_case source, e.g. {@code afk_pool}
     */
    CompletableFuture<Boolean> giveKeys(UUID profileKey, String crateId, int amount, String reason, Actor actor);

    /**
     * One reward of a crate as other modules may show it (M10.10 collection log).
     *
     * @param crate    crate id
     * @param id       reward id inside that crate ({@code cinder_key}, {@code spawner_t3} ...)
     * @param top      a star (top) reward
     * @param live     the reward really exists today (false for "coming soon" rewards that pay a crystal fallback)
     * @param icon     vanilla item id of its icon ({@code tripwire_hook})
     * @param nameMini its display name as MiniMessage with style tags (rarity color included)
     */
    record RewardInfo(String crate, String id, boolean top, boolean live, String icon, String nameMini) {
    }

    /** Ids of every crate in display order (empty when the crates module does not publish them). */
    default List<String> crateIds() {
        return List.of();
    }

    /** The rewards of one crate in catalog order (empty for an unknown crate). */
    default List<RewardInfo> rewards(String crateId) {
        return List.of();
    }

    /** Display name of a crate as MiniMessage with style tags ({@code crateId} when unknown). */
    default String crateNameMini(String crateId) {
        return crateId;
    }
}
