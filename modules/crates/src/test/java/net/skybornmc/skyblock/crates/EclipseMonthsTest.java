package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eclipse month tables, teaser and staff warning (owner M1-M5). */
class EclipseMonthsTest {

    @AfterEach
    void resetClock() {
        CrateCatalog.clock = Clock.system(ZoneId.of("Europe/Oslo"));
    }

    @Test
    void aMonthTableReplacesTheRewardsOnlyInThatMonth() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration yaml = Fixtures.resource(CrateCatalog.FILE);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String band : List.of("common", "mid", "rare")) {
            for (int i = 0; i < 9; i++) {
                rows.add(Map.of("id", band + i, "type", "crystals", "band", band, "chance", 1, "base", 10));
            }
        }
        for (int i = 0; i < 5; i++) {
            rows.add(Map.of("id", "jp" + i, "type", "crystals", "band", "jackpot", "chance", 1, "base", 99, "top", true));
        }
        yaml.set("crates.eclipse.months.2026-12.rewards", rows);
        List<String> problems = new ArrayList<>();
        CrateCatalog c = CrateCatalog.load(yaml, problems::add);
        assertEquals(List.of(), problems);
        assertTrue(c.monthlyCrate("eclipse"));
        assertTrue(c.hasMonth("eclipse", "2026-12"));
        CrateCatalog.clock = Clock.fixed(Instant.parse("2026-12-05T12:00:00Z"), ZoneId.of("Europe/Oslo"));
        assertTrue(c.crate("eclipse").orElseThrow().reward("jp0").isPresent(), "December: its own table");
        CrateCatalog.clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("Europe/Oslo"));
        assertTrue(c.crate("eclipse").orElseThrow().reward("jp0").isEmpty(), "November: the base table");
        assertTrue(c.crate("eclipse").orElseThrow().reward("mythic_book").isPresent());
    }

    @Test
    void teaserFromThe25th() {
        assertFalse(EclipseFeature.teaser(LocalDate.of(2026, 11, 24), 25));
        assertTrue(EclipseFeature.teaser(LocalDate.of(2026, 11, 25), 25));
        assertTrue(EclipseFeature.teaser(LocalDate.of(2026, 2, 28), 31), "short month: the last day");
        assertFalse(EclipseFeature.teaser(LocalDate.of(2026, 11, 30), 0));
    }

    @Test
    void staffWarnedSevenDaysBeforeAMonthWithoutTable() {
        assertFalse(EclipseFeature.warnStaff(LocalDate.of(2026, 11, 23), 7, true, false));
        assertTrue(EclipseFeature.warnStaff(LocalDate.of(2026, 11, 24), 7, true, false));
        assertFalse(EclipseFeature.warnStaff(LocalDate.of(2026, 11, 28), 7, true, true), "next month has its table");
        assertFalse(EclipseFeature.warnStaff(LocalDate.of(2026, 11, 28), 7, false, false), "no month tables at all");
    }
}
