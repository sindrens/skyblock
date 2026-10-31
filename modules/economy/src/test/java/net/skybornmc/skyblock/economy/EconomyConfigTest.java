package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.MultiplierCap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tokens were replaced by island crystals (O8 / CR3): defaults, renamed keys and old config values. */
class EconomyConfigTest {

    private static Set<String> settingPaths() {
        return Arrays.stream(EconomyConfig.class.getFields())
                .filter(f -> f.isAnnotationPresent(Setting.class))
                .map(f -> f.getAnnotation(Setting.class).path())
                .collect(Collectors.toSet());
    }

    @Test
    void crystalsAreNotTradeableByDefault() {
        EconomyConfig config = new EconomyConfig();
        assertFalse(config.tradeable(Currency.CRYSTALS), "CR3: crystals can't be paid or sold");
        assertFalse(NoteRules.allowed(config, Currency.CRYSTALS));
    }

    @Test
    void everyOldTokensKeyMovesToAnExistingCrystalsSetting() {
        EconomyConfig config = new EconomyConfig();
        assertEquals(3, config.version());
        Set<String> paths = settingPaths();
        var renames = config.migrations().get(2);
        assertEquals(6, renames.size());
        renames.forEach((from, to) -> {
            assertTrue(from.endsWith(".tokens"), from);
            assertTrue(to.endsWith(".crystals"), to);
            assertTrue(paths.contains(to), "no setting " + to);
            assertFalse(paths.contains(from), "old key still a setting: " + from);
        });
        assertTrue(paths.stream().noneMatch(p -> p.contains("token")));
    }

    @Test
    void newAccountLimitIsGone() {
        // Owner answer 131 (round 1): the 30-minute limit was removed entirely.
        assertFalse(settingPaths().contains("pay.new-account-minutes"));
        assertFalse(settingPaths().contains("notes.new-account-minutes"));
    }

    @Test
    void sellCapIsPlus300PercentAndAServiceSetting() {
        EconomyConfig config = new EconomyConfig();
        assertEquals(300.0, config.sellMultiplierCapPercent);
        assertTrue(settingPaths().contains("sell.multiplier-cap-percent"));
        assertEquals(400, MultiplierCap.apply(100, 1_000, config.sellMultiplierCapPercent));
    }

    @Test
    void oldTokensIconEntryStillAppliesToCrystals() {
        EconomyConfig config = new EconomyConfig();
        config.icons = List.of("tokens:AMETHYST_SHARD");
        assertEquals("AMETHYST_SHARD", config.iconName(Currency.CRYSTALS));
        assertNull(config.iconName(Currency.CREDITS_EARNED));
        assertEquals("EMERALD", new EconomyConfig().iconName(Currency.CREDITS_EARNED));
    }
}
