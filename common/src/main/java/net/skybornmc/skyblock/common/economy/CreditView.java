package net.skybornmc.skyblock.common.economy;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * A player's credits as the two ledger halves (card C1): {@code bought} ({@link Currency#CREDITS}, tradeable) and
 * {@code earned} ({@link Currency#CREDITS_EARNED}, never tradeable). Everyone sees {@link #total()}; only the owner of the
 * balance sees the split ({@code /credit bal}). Every display (scoreboard, menus, {@code /bal}, baltop, placeholders) uses
 * {@link #total()}, never one half alone. Pure.
 */
public record CreditView(long bought, long earned) {

    public static final CreditView NONE = new CreditView(0, 0);

    public CreditView {
        bought = Math.max(0, bought);
        earned = Math.max(0, earned);
    }

    /** Bought plus earned; saturates at {@link Long#MAX_VALUE} instead of overflowing. */
    public long total() {
        long sum = bought + earned;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    /** The credits in a balances map ({@link EconomyService#balances}); a missing half counts as 0. */
    public static CreditView of(Map<Currency, Long> balances) {
        if (balances == null) {
            return NONE;
        }
        return new CreditView(balances.getOrDefault(Currency.CREDITS, 0L), balances.getOrDefault(Currency.CREDITS_EARNED, 0L));
    }

    /** The cached credits of an online player ({@link EconomyService#cached}); empty while neither half is loaded. */
    public static Optional<CreditView> cached(EconomyService economy, UUID player) {
        if (economy == null || player == null) {
            return Optional.empty();
        }
        OptionalLong bought = economy.cached(player, Currency.CREDITS);
        OptionalLong earned = economy.cached(player, Currency.CREDITS_EARNED);
        if (bought.isEmpty() && earned.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CreditView(bought.orElse(0), earned.orElse(0)));
    }

    /**
     * The balance a player sees for {@code currency}: for either credit ledger the cached credits total, for every other
     * currency the cached balance. Empty while not loaded.
     */
    public static OptionalLong cachedShown(EconomyService economy, UUID player, Currency currency) {
        if (economy == null || player == null || currency == null) {
            return OptionalLong.empty();
        }
        if (currency.isCredits()) {
            return cached(economy, player).map(v -> OptionalLong.of(v.total())).orElse(OptionalLong.empty());
        }
        return economy.cached(player, currency);
    }
}
