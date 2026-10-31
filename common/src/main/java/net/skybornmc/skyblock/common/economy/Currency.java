package net.skybornmc.skyblock.common.economy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The currencies of Phoenix Skyblock (DESIGN §4.1, card C1). Every balance is a whole-unit {@code long} (max ≈ 9.2
 * quintillion). Whether a currency can be paid/traded between players is configured in {@code economy.yml} (decided:
 * shards yes, crystals no). Crystals and earned credits are never transferable, whatever a config says
 * ({@link #transferable()}, owner R3-5b and C1-R2-02).
 *
 * <p><b>Credits (C1):</b> two ledger currencies, one thing for the player. {@link #CREDITS} are the <i>bought</i> credits
 * (created only by a store grant or the owner's {@code /eco give credits}, reason {@code admin_buy}); {@link #CREDITS_EARNED}
 * are earned by votes, thanks, streaks and the pass and can never move to another account. Both belong to the player (all
 * profiles, never lost). Players see the total ({@link CreditView#total()}); only the owner of the balance sees the split.
 * Spend with {@link CreditSpend} (earned first, then bought, one database transaction).</p>
 *
 * <p>Old keys: {@code tokens} resolves to {@link #CRYSTALS}; {@code gems} (renamed credits, C1-R1-03) resolves to
 * {@link #CREDITS_EARNED} until the date set with {@link #legacyGemsAliasUntil(long)} (economy.yml
 * {@code legacy-gems-alias-until}). Nothing was ever sold for gems, so every old gem is an earned credit. Store grants must
 * use {@link #byCanonicalKey(String)}, which never accepts an alias.</p>
 */
public enum Currency {
    MONEY,
    CRYSTALS,
    /** Bought credits: created only by store grants and the owner's {@code admin_buy}; the tradeable half of credits. */
    CREDITS,
    /** Earned credits (vote, thanks, streak, pass, event, admin give): never transferable (hard rule in code). */
    CREDITS_EARNED,
    SHARDS;

    /** Lowercase key used in the database, configs, messages, commands and style tags ({@code <cur:money>}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Owner R3-5b and C1-R2-02: false for island crystals and earned credits. They can never move from one account to
     * another (no /pay, trade, notes, AH), regardless of {@code economy.yml → tradeable.*}; {@link Transaction#problem()}
     * refuses such a move.
     */
    public boolean transferable() {
        return this != CRYSTALS && this != CREDITS_EARNED;
    }

    /** True for the two credit ledgers ({@link #CREDITS} and {@link #CREDITS_EARNED}). */
    public boolean isCredits() {
        return this == CREDITS || this == CREDITS_EARNED;
    }

    /**
     * True when the balance belongs to the player (account) and is shared by all of their profiles: the credits (owner
     * PR2, C1-R1-01). Every other currency belongs to a profile.
     */
    public boolean accountWide() {
        return isCredits();
    }

    /**
     * True for the currencies players see as their own line ({@code /bal}, {@code /baltop}, menus): everything except
     * {@link #CREDITS_EARNED}, which is shown as part of the credits total.
     */
    public boolean listed() {
        return this != CREDITS_EARNED;
    }

    /** The currencies players see as their own line, in display order ({@link #listed()}). */
    public static List<Currency> listedValues() {
        return Arrays.stream(values()).filter(Currency::listed).toList();
    }

    /** Removed currency key that now means {@link #CRYSTALS}. */
    public static final String LEGACY_TOKENS_KEY = "tokens";

    /** Renamed currency key (gems became credits, C1-R1-03); resolves to {@link #CREDITS_EARNED} while the alias lasts. */
    public static final String LEGACY_GEMS_KEY = "gems";

    /** Until when (epoch millis) {@code gems} still resolves. Unlimited until the economy module sets the configured date. */
    private static volatile long gemsAliasUntil = Long.MAX_VALUE;
    private static volatile LongSupplier clock = System::currentTimeMillis;

    /** Sets the end of the {@code gems} alias (epoch millis); the economy module calls it from economy.yml. */
    public static void legacyGemsAliasUntil(long epochMillis) {
        gemsAliasUntil = epochMillis;
    }

    /** The end of the {@code gems} alias (epoch millis; {@link Long#MAX_VALUE} = not set). */
    public static long legacyGemsAliasUntil() {
        return gemsAliasUntil;
    }

    /** Test hook: the clock the alias end is compared with ({@code null} = the system clock). */
    public static void aliasClock(LongSupplier source) {
        clock = source == null ? System::currentTimeMillis : source;
    }

    /** True while the old key {@code gems} still resolves to {@link #CREDITS_EARNED}. */
    public static boolean legacyGemsAliasActive() {
        return clock.getAsLong() < gemsAliasUntil;
    }

    /**
     * The currency of a key (case-insensitive, trimmed). The legacy key {@code tokens} maps to {@link #CRYSTALS}; the
     * legacy key {@code gems} maps to {@link #CREDITS_EARNED} while {@link #legacyGemsAliasActive()}.
     */
    public static Optional<Currency> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.equals(LEGACY_TOKENS_KEY)) {
            return Optional.of(CRYSTALS);
        }
        if (k.equals(LEGACY_GEMS_KEY)) {
            return legacyGemsAliasActive() ? Optional.of(CREDITS_EARNED) : Optional.empty();
        }
        return byCanonicalKey(k);
    }

    /**
     * The currency of a current key only: no {@code tokens} and no {@code gems} alias. Store grants use it, so an old
     * {@code gems} package can never create credits (C1-R2-02: the alias is refused for store grants).
     */
    public static Optional<Currency> byCanonicalKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (Currency c : values()) {
            if (c.key().equals(k)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
