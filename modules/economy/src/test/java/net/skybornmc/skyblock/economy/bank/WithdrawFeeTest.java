package net.skybornmc.skyblock.economy.bank;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BANK2 (owner #169): 2% withdrawal fee, lowered one step per island bank-fee mastery level; rounding of the fee. */
class WithdrawFeeTest {

    @Test
    void shippedStepsAreTwoThenOneAndAHalfThenOneThenAHalf() {
        WithdrawFee fee = WithdrawFee.of(new BankConfig());
        assertTrue(fee.problems().isEmpty(), fee.problems().toString());
        assertEquals(2.0, fee.percent(0), "no mastery: the base 2%");
        assertEquals(1.5, fee.percent(1));
        assertEquals(1.0, fee.percent(2));
        assertEquals(0.5, fee.percent(3));
        assertEquals(0.5, fee.percent(99), "levels above the list keep the last step");
        assertEquals(2.0, fee.percent(-1));
        assertEquals(3, fee.maxLevel());
        assertEquals(0, fee.discount(0));
        assertEquals(0.5, fee.discount(1));
        assertEquals(1.5, fee.discount(3));
    }

    @Test
    void feeAmountsPerStepRoundUp() {
        WithdrawFee fee = WithdrawFee.of(new BankConfig());
        assertEquals(20_000, InterestMath.fee(1_000_000, fee.percent(0)));
        assertEquals(15_000, InterestMath.fee(1_000_000, fee.percent(1)));
        assertEquals(10_000, InterestMath.fee(1_000_000, fee.percent(2)));
        assertEquals(5_000, InterestMath.fee(1_000_000, fee.percent(3)));
        assertEquals(2, InterestMath.fee(101, fee.percent(1)), "1.515 rounds up");
        assertEquals(1, InterestMath.fee(100, fee.percent(3)), "0.5 rounds up: never a free withdrawal through rounding");
        assertEquals(2, InterestMath.fee(150, fee.percent(2)), "1% of 150 = 1.5 rounds up to 2");
    }

    @Test
    void stepsNeverRaiseTheFeeAndBadEntriesAreReported() {
        WithdrawFee fee = WithdrawFee.parse(2.0, List.of("1.5", "abc", "1.8", "-1", "0.25%"));
        assertEquals(List.of(1.5, 1.5, 0.25), fee.steps(), "1.8 is lowered to 1.5; abc and -1 are skipped; '%' is fine");
        assertEquals(3, fee.problems().size(), fee.problems().toString());
        WithdrawFee none = WithdrawFee.parse(2.0, List.of());
        assertEquals(2.0, none.percent(5), "no steps: always the base");
        assertEquals(0, none.maxLevel());
        WithdrawFee nullList = WithdrawFee.parse(3.0, null);
        assertEquals(3.0, nullList.percent(1));
        assertEquals(1.0, WithdrawFee.parse(1.0, List.of("2.0")).percent(1), "a step above the base is lowered to it");
    }

    @Test
    void discountIsExactInDecimals() {
        WithdrawFee fee = WithdrawFee.parse(2.0, List.of("1.7"));
        assertEquals(0.3, fee.discount(1));
        assertEquals("0.3", BankMenu.percent(fee.discount(1)));
        assertEquals("1.7", BankMenu.percent(1.7000000000000002));
        assertEquals("2", BankMenu.percent(2.0));
        assertEquals("0.6", BankMenu.percent(0.6));
    }
}
