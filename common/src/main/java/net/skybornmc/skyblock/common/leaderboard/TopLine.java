package net.skybornmc.skyblock.common.leaderboard;

import java.util.Objects;
import java.util.UUID;

/**
 * One leaderboard row from a service ({@code EconomyService#top}, {@code SkillService#top}): the player (owner UUID,
 * best profile already chosen, owner decision P7), their last known name and the ranked value (whole money, whole XP).
 *
 * @param name last known name, or {@code null} when the players table does not know it
 */
public record TopLine(UUID player, String name, long value) {

    public TopLine {
        Objects.requireNonNull(player, "player");
    }
}
