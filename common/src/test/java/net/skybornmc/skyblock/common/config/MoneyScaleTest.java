package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Money x100 (batch 10): scaled exactly once, owner values too; new files never. */
class MoneyScaleTest {

    static final class MoneyConfig extends ModuleConfig {
        @Setting(path = "start.money", desc = "Start money") long start = 100_000;
        @Setting(path = "limits", desc = "Limits") List<String> limits = List.of("0:2M", "24:-1");
        @Setting(path = "price", desc = "A price") double price = 50.0;
        @Setting(path = "shards", desc = "Not money") long shards = 5;

        @Override public String fileName() { return "money.yml"; }

        @Override
        public Map<String, MoneyScale.Rule> moneyKeys() {
            return Map.of("start.money", MoneyScale.number(), "limits", MoneyScale.field(1, ":"), "price", MoneyScale.number());
        }
    }

    /** A value change that writes today's default (x100 already) is not scaled a second time. */
    static final class ChangedConfig extends ModuleConfig {
        @Setting(path = "cost", desc = "Cost") long cost = 500_000;

        @Override public String fileName() { return "changed.yml"; }
        @Override public int version() { return 2; }
        @Override public Map<Integer, Map<String, ValueChange>> valueChanges() {
            return Map.of(2, Map.of("cost", ValueChange.of(1_000L, new ChangedConfig().cost)));
        }
        @Override public Map<String, MoneyScale.Rule> moneyKeys() { return Map.of("cost", MoneyScale.number()); }
    }

    @Test
    void aValueChangeToTheNewDefaultIsNotScaledTwice(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("changed.yml"), "config-version: 1\ncost: 1000\n");
        ChangedConfig c = new ChangedConfig();
        new ConfigManager(dir.toFile(), log).load(c);
        assertEquals(500_000, c.cost);
        Files.writeString(dir.resolve("changed.yml"), "config-version: 1\ncost: 2000\n");
        ChangedConfig owner = new ChangedConfig();
        new ConfigManager(dir.toFile(), log).load(owner);
        assertEquals(200_000, owner.cost, "an owner value is scaled once");
    }

    private final Logger log = Logger.getLogger("test");

    @Test
    void aNewFileKeepsTheNewDefaultsAndIsMarked(@TempDir Path dir) {
        MoneyConfig c = new MoneyConfig();
        new ConfigManager(dir.toFile(), log).load(c);
        assertEquals(100_000, c.start);
        assertEquals(100, YamlConfiguration.loadConfiguration(dir.resolve("money.yml").toFile()).getLong(MoneyScale.KEY));
        MoneyConfig again = new MoneyConfig();
        new ConfigManager(dir.toFile(), log).load(again);
        assertEquals(100_000, again.start, "a reload never scales twice");
    }

    @Test
    void anOldFileIsScaledOnceOwnerValuesToo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("money.yml"), "config-version: 1\nstart:\n  money: 2500\nlimits:\n- '0:20k'\n- '3:100k'\n- '24:-1'\nprice: 0.5\nshards: 5\n");
        MoneyConfig c = new MoneyConfig();
        new ConfigManager(dir.toFile(), log).load(c);
        assertEquals(250_000, c.start, "the owner's 2,500 becomes 250,000");
        assertEquals(List.of("0:2M", "3:10M", "24:-1"), c.limits);
        assertEquals(50.0, c.price, 1e-9);
        assertEquals(5, c.shards, "shards are not money");
        assertTrue(Files.exists(dir.resolve("money.yml.pre-money-x100.bak")));
        MoneyConfig again = new MoneyConfig();
        new ConfigManager(dir.toFile(), log).load(again);
        assertEquals(250_000, again.start, "once");
        assertEquals(List.of("0:2M", "3:10M", "24:-1"), again.limits);
    }

    @Test
    void amountTexts() {
        assertEquals("2M", MoneyScale.scaleAmountText("20k", 100));
        assertEquals("150000", MoneyScale.scaleAmountText("1500", 100));
        assertEquals("150M", MoneyScale.scaleAmountText("1.5M", 100));
        assertEquals("-1", MoneyScale.scaleAmountText("-1", 100));
        assertEquals(null, MoneyScale.scaleAmountText("abc", 100));
        assertEquals("10M 0 600", MoneyScale.scaleField("100k 0 600", 0, " ", 100));
        assertEquals("shallows:0:15000:cod@85", MoneyScale.scaleField("shallows:0:150:cod@85", 2, ":", 100));
        assertEquals(List.of("money:200000", "shards:50"), MoneyScale.onlyLines(MoneyScale.startsWith("money:"), MoneyScale.field(1, ":"))
                .apply(List.of("money:2000", "shards:50"), 100));
        assertEquals(List.of(2000, 3000L * 100), MoneyScale.numbers().apply(List.of(20, 3000L), 100));
    }
}
