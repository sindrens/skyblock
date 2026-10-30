package net.skybornmc.skyblock.common.economy;

import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Banks (M3.3) for other modules: published by the economy module ({@code core.services().optional(BankService.class)}).
 * A personal bank belongs to one <b>profile</b> ({@link AccountRef#bank}, owner decision P2); an island bank belongs to
 * the island ({@link AccountRef#island(UUID)}). Money only. Balances change only through the bank itself (deposit,
 * withdraw, interest), always as ledger transactions.
 */
public interface BankService {

    /** Opens {@code /bank} (personal and island bank side by side) for {@code player}; {@code parent} = Back target or null. */
    void open(Player player, Menu parent);

    /** The island menu's Bank button: the same menu (the island side is the one asked for). Main thread. */
    default void openIslandBank(Player player, Menu parent) {
        open(player, parent);
    }

    /**
     * The personal bank tier (1-10) of a profile, for tier perks in other modules (AH slots, AH tax, sell bonus once the
     * owner's perk list exists). 1 for a profile that never bought a tier.
     */
    CompletableFuture<Integer> personalTier(UUID profileKey);

    /** The money in a profile's personal bank. */
    CompletableFuture<Long> personalBalance(UUID profileKey);

    /** The money in an island's bank. */
    CompletableFuture<Long> islandBalance(UUID islandId);
}
