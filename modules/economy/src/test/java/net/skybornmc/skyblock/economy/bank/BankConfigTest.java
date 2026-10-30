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
        assertEquals(3, fresh.version());
    }

    @Test
    void bank2TierTablesBecomeTheUx3Ones() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 2);
        yaml.set("interest.period-hours", 8);
        BankConfig.BANK2_TIERS.forEach(yaml::set);
        assertEquals(List.of("personal.tiers", "island.levels"), BankConfig.upgradeValues(yaml));
        BankConfig fresh = new BankConfig();
        assertEquals(fresh.personalTiers, yaml.getStringList("personal.tiers"));
        assertEquals(fresh.islandLevels, yaml.getStringList("island.levels"));

        YamlConfiguration owner = new YamlConfiguration();
        owner.set("config-version", 2);
        owner.set("personal.tiers", List.of("100k 0 600", "1M 20k 5k"));
        owner.set("island.levels", BankConfig.BANK2_TIERS.get("island.levels"));
        assertEquals(List.of("island.levels"), BankConfig.upgradeValues(owner), "owner-chosen tiers stay");
        assertEquals(List.of("100k 0 600", "1M 20k 5k"), owner.getStringList("personal.tiers"));

        YamlConfiguration done = new YamlConfiguration();
        done.set("config-version", 3);
        BankConfig.BANK2_TIERS.forEach(done::set);
        assertTrue(BankConfig.upgradeValues(done).isEmpty(), "version 3: chosen on purpose");
    }

    /** UX3 balance rules of the default tables (ECONOMY_BALANCE.md 6.1/6.2). */
    @Test
    void ux3TablesAreSensibleSteps() {
        BankConfig fresh = new BankConfig();
        BankTiers t = BankTiers.parse(fresh);
        assertTrue(t.problems().isEmpty(), t.problems().toString());
        assertEquals(10, t.maxTier());
        assertEquals(11, t.island().size());
        double lastRate = Double.MAX_VALUE;
        for (int i = 0; i < t.personal().size(); i++) {
            BankTiers.PersonalTier tier = t.personal().get(i);
            assertEquals(i == 0 ? 0 : tier.capacity() / 50, tier.cost(), "tier " + tier.tier() + " costs 2% of its capacity (owner #42)");
            if (i > 0) {
                double step = (double) tier.capacity() / t.personal().get(i - 1).capacity();
                assertTrue(step >= 3 && step <= 5, "tier " + tier.tier() + " is a x3-5 step, was x" + step);
                assertTrue(tier.payoutCap() > t.personal().get(i - 1).payoutCap());
            }
            double rate = (double) tier.payoutCap() / tier.capacity();
            assertTrue(rate < lastRate, "the effective rate at full falls with every tier");
            assertTrue(rate <= fresh.personalRatePercent / 100 + 1e-12, "the cap is never above the plain rate");
            lastRate = rate;
        }
        assertEquals(10_000_000_000L, t.tier(10).capacity(), "tier 10 holds $10B, not $195.3B");
        lastRate = Double.MAX_VALUE;
        for (BankTiers.IslandLevel level : t.island()) {
            double rate = (double) level.payoutCap() / level.capacity();
            assertTrue(rate < lastRate && rate <= fresh.islandRatePercent / 100 + 1e-12, "island level " + level.level());
            lastRate = rate;
        }
        assertEquals(50_000_000_000L, t.islandCapacity(10));
    }
}
