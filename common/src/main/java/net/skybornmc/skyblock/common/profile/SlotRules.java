package net.skybornmc.skyblock.common.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * Profile slot rules (owner decisions P6, P8 and round 3 PR5). Pure logic, unit-tested.
 *
 * <ul>
 *   <li>Slot 1 is free for everyone.</li>
 *   <li>Slot 2: 500 credits or rank Knight+ ({@code skyblock.profiles.slots.2}); slot 3: 1,500 credits or Warlord+
 *       ({@code skyblock.profiles.slots.3}). Prices are config values; the slots are independent of each other.</li>
 *   <li>A season reset keeps credit-bought slots and drops every other stored unlock; rank slots are live permissions.</li>
 * </ul>
 */
public final class SlotRules {

    /** Most profiles an account can have. */
    public static final int MAX_SLOTS = 3;

    /** Permission prefix of rank slots: {@code skyblock.profiles.slots.2}, {@code .3}. */
    public static final String RANK_PERMISSION_PREFIX = "skyblock.profiles.slots.";

    /** A stored slot unlock. */
    public record Unlock(int slot, SlotSource source, int seasonId, long at) {
    }

    private SlotRules() {
    }

    public static boolean validSlot(int slot) {
        return slot >= 1 && slot <= MAX_SLOTS;
    }

    public static String rankPermission(int slot) {
        return RANK_PERMISSION_PREFIX + slot;
    }

    /**
     * How a slot is unlocked right now, or empty if it is locked. Stored unlocks win over the rank (a bought slot stays
     * when the rank is lost).
     *
     * @param stored  the account's stored unlocks (credits, grants)
     * @param rankHas whether the player has the rank permission of a slot (live)
     */
    public static Optional<SlotSource> source(int slot, Collection<Unlock> stored, IntPredicate rankHas) {
        if (!validSlot(slot)) {
            return Optional.empty();
        }
        if (slot == 1) {
            return Optional.of(SlotSource.FREE);
        }
        for (Unlock u : stored) {
            if (u.slot() == slot) {
                return Optional.of(u.source());
            }
        }
        return rankHas.test(slot) ? Optional.of(SlotSource.RANK) : Optional.empty();
    }

    public static boolean unlocked(int slot, Collection<Unlock> stored, IntPredicate rankHas) {
        return source(slot, stored, rankHas).isPresent();
    }

    /** Why a slot can't be bought with credits, or empty if it can. */
    public enum BuyProblem { INVALID_SLOT, FREE_SLOT, ALREADY_UNLOCKED, NOT_FOR_SALE }

    /**
     * Whether {@code slot} can be bought now. A slot already unlocked (bought, granted or by rank) can't be bought, so
     * nobody pays for something they have. A price of 0 or less means "not for sale".
     */
    public static Optional<BuyProblem> canBuy(int slot, Collection<Unlock> stored, IntPredicate rankHas, long price) {
        if (!validSlot(slot)) {
            return Optional.of(BuyProblem.INVALID_SLOT);
        }
        if (slot == 1) {
            return Optional.of(BuyProblem.FREE_SLOT);
        }
        if (unlocked(slot, stored, rankHas)) {
            return Optional.of(BuyProblem.ALREADY_UNLOCKED);
        }
        if (price <= 0) {
            return Optional.of(BuyProblem.NOT_FOR_SALE);
        }
        return Optional.empty();
    }

    /** Owner decision P8: at a season reset only credit-bought slots are kept. */
    public static List<Unlock> afterSeasonReset(Collection<Unlock> stored) {
        List<Unlock> kept = new ArrayList<>();
        for (Unlock u : stored) {
            if (u.source() == SlotSource.CREDITS) {
                kept.add(u);
            }
        }
        return kept;
    }

    /** Number of usable slots right now (1 to {@link #MAX_SLOTS}). */
    public static int usable(Collection<Unlock> stored, IntPredicate rankHas) {
        int n = 0;
        for (int slot = 1; slot <= MAX_SLOTS; slot++) {
            if (unlocked(slot, stored, rankHas)) {
                n++;
            }
        }
        return n;
    }
}
