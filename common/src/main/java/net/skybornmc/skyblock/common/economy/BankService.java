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

    /**
     * What an island bank holds at a Bank Capacity upgrade level (0 = not bought), from {@code bank.yml → island.levels}
     * (the only place the amounts live; /is upgrade shows them). -1 if unknown. Any thread.
     */
    default long islandCapacity(int capacityLevel) {
        return -1;
    }

    /** The most interest an island bank earns per payout at a Bank Interest upgrade level (0 = not bought); -1 if unknown. */
    default long islandPayoutCap(int interestLevel) {
        return -1;
    }

    /** Hours between interest payouts ({@code bank.yml → interest.period-hours}); 0 if unknown. */
    default int interestPeriodHours() {
        return 0;
    }

    /**
     * The bank withdrawal fee in percent at a bank-fee mastery level (BANK2, owner #169; the Treasury mastery's rank):
     * level 0 = {@code withdraw.fee-percent}, then one step of {@code withdraw.mastery-fee-percent} per level. -1 if
     * unknown. Any thread.
     */
    default double withdrawFeePercent(int masteryLevel) {
        return -1;
    }
}
