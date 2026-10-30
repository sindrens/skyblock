package net.skybornmc.skyblock.economy.bank;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BANK2: an existing bank.yml with the untouched M3.3 defaults gets the owner #169 values; owner-chosen values stay. */
class BankConfigTest {

    private static YamlConfiguration m33File() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 1);
        BankConfig.OLD_DEFAULTS.forEach(yaml::set);
        return yaml;
    }

    @Test
    void untouchedOldDefaultsAreUpgraded() {
        YamlConfiguration yaml = m33File();
        List<String> changed = BankConfig.upgradeValues(yaml);
        assertEquals(6, changed.size(), changed.toString());
        BankConfig fresh = new BankConfig();
        assertEquals(8, yaml.getInt("interest.period-hours"));
        assertEquals(0.6, yaml.getDouble("interest.personal-rate-percent"));
        assertEquals(1.0, yaml.getDouble("interest.island-rate-percent"));
        assertEquals(3, yaml.getInt("interest.catch-up-periods"));
        assertEquals(fresh.personalTiers, yaml.getStringList("personal.tiers"));
        assertEquals(fresh.islandLevels, yaml.getStringList("island.levels"));
    }

    @Test
    void ownerValuesAndNewFilesAreLeftAlone() {
        YamlConfiguration yaml = m33File();
        yaml.set("interest.period-hours", 4);
        yaml.set("interest.personal-rate-percent", 0.2);
        yaml.set("personal.tiers", List.of("100k 0 999"));
        List<String> changed = BankConfig.upgradeValues(yaml);
        assertEquals(List.of("interest.island-rate-percent", "interest.catch-up-periods", "island.levels"), changed);
        assertEquals(4, yaml.getInt("interest.period-hours"));
        assertEquals(0.2, yaml.getDouble("interest.personal-rate-percent"));
        assertEquals(List.of("100k 0 999"), yaml.getStringList("personal.tiers"));

        YamlConfiguration current = m33File();
        current.set("config-version", 2);
        assertTrue(BankConfig.upgradeValues(current).isEmpty(), "version 2: 3 h was chosen by the owner");
        assertTrue(BankConfig.upgradeValues(new YamlConfiguration()).isEmpty(), "a new file gets the defaults");
    }

    @Test
    void oldDefaultsDifferFromTheNewOnes() {
        BankConfig fresh = new BankConfig();
        assertEquals(3, BankConfig.OLD_DEFAULTS.get("interest.period-hours"));
        assertTrue(fresh.periodHours != 3 && fresh.catchUpPeriods != 8);
        assertEquals(2, fresh.version());
    }
}
