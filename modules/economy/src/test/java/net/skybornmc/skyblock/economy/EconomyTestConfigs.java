package net.skybornmc.skyblock.economy;

/**
 * Money x100 (batch 10): the service tests check mechanics with small round numbers, so they run on the money values
 * from before the scale. The x100 defaults themselves are checked in {@code EconomyLogicTest.decidedDefaults}.
 */
public final class EconomyTestConfigs {

    private EconomyTestConfigs() {
    }

    public static EconomyConfig preScale(EconomyConfig c) {
        c.startMoney = 1_000;
        c.payMinMoney = 10;
        c.leavePayFlagAmount = 100_000;
        c.payConfirmMoney = 1_000_000;
        c.displayExactBelow = 100_000;
        c.noteMinMoney = 100;
        c.noteMaxMoney = 1_000_000_000_000L;
        c.ecoApproveMoney = 1_000_000_000_000L;
        c.payLimitTiers = java.util.List.of("0:20k", "3:100k", "6:500k", "12:2.5M", "24:-1");
        c.hotMinBase = 5.0;
        c.marketTipsMinBase = 5.0;
        c.sellBigSaleChatFrom = 100_000;
        return c;
    }

    /** The bank tables from before the scale (the bank mechanics tests use them). */
    public static net.skybornmc.skyblock.economy.bank.BankConfig preScale(net.skybornmc.skyblock.economy.bank.BankConfig c) {
        c.depositMin = 1;
        c.withdrawMin = 100;
        c.personalTiers = java.util.List.of("100k 0 600", "500k 10k 1.6k", "2M 40k 4.4k", "7.5M 150k 11.6k", "25M 500k 27.5k",
                "75M 1.5M 61k", "250M 5M 145k", "750M 15M 320k", "2.5B 50M 770k", "10B 200M 2.1M");
        c.islandLevels = java.util.List.of("1M 10k", "3M 24k", "10M 62k", "30M 150k", "100M 380k", "300M 900k", "1B 2.4M", "3B 5.7M",
                "8B 12M", "20B 26M", "50B 53M");
        return c;
    }
}
