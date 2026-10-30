package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.economy.Currency;
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
        assertEquals(2, config.version());
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
    void oldTokensIconEntryStillAppliesToCrystals() {
        EconomyConfig config = new EconomyConfig();
        config.icons = List.of("tokens:AMETHYST_SHARD");
        assertEquals("AMETHYST_SHARD", config.iconName(Currency.CRYSTALS));
        assertNull(config.iconName(Currency.GEMS));
    }
}
