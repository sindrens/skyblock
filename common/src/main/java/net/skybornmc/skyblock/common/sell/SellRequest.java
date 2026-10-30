package net.skybornmc.skyblock.common.sell;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One sale for {@link SellService#sell}. The caller has <b>already removed</b> {@code items} from the world (inventory,
 * satchel, chest ...): they exist only in this request until the result says what to do with them.
 *
 * @param player       the selling player (for history and audit)
 * @param playerName   their name (audit, logs)
 * @param profileKey   the account the money goes to: {@code ProfileService#profileKey(player)} at the time of removal
 * @param items        the removed stacks (copies are made; the request never changes them)
 * @param bonusPercent the summed sell bonus ({@link SellService#bonusPercent}); the cap is applied by the service
 * @param source       where the sale came from: {@code gui}, {@code hand}, {@code all}, {@code satchel}, {@code afk} ...
 *                     ({@code [a-z0-9_]{1,16}})
 * @param actor        who did it (audit)
 */
public record SellRequest(UUID player, String playerName, UUID profileKey, List<ItemStack> items, double bonusPercent,
                          String source, Actor actor) {

    public SellRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profileKey, "profileKey");
        playerName = playerName == null ? player.toString().substring(0, 8) : playerName;
        items = items.stream().filter(Objects::nonNull).filter(i -> !i.isEmpty()).map(ItemStack::clone).toList();
        source = source == null || !source.matches("[a-z0-9_]{1,16}") ? "other" : source;
        actor = actor == null ? Actor.SYSTEM : actor;
    }
}
