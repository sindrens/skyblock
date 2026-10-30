package net.skybornmc.skyblock.economy.admin;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InflationRiskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    private static Map<LocalDate, FlowSummary.Totals> days(int count, long created, long removed) {
        Map<LocalDate, FlowSummary.Totals> out = new HashMap<>();
        for (int i = 0; i < count; i++) {
            out.put(TODAY.minusDays(i), new FlowSummary.Totals(created, removed, 0));
        }
        return out;
    }

    private static InflationRisk.Risk eval(Map<LocalDate, FlowSummary.Totals> daily) {
        return InflationRisk.evaluate(daily, TODAY, 14, 75, 60, 3, 3);
    }

    @Test
    void healthySinkIsOk() {
        InflationRisk.Risk r = eval(days(20, 100, 85));
        assertEquals(InflationRisk.Level.OK, r.level());
        assertEquals(85.0, r.ratio(), 1e-9);
    }

    @Test
    void betweenTheLinesIsWatch() {
        assertEquals(InflationRisk.Level.WATCH, eval(days(20, 100, 70)).level());
    }

    @Test
    void belowTheActLineForThreeDaysIsAct() {
        InflationRisk.Risk r = eval(days(20, 100, 50));
        assertEquals(InflationRisk.Level.ACT, r.level());
        assertEquals(3 + 14, r.daysBelowAct(), "counted back while the rolling ratio stays low");
    }

    @Test
    void oneBadDayIsOnlyWatch() {
        // 20 good days, then two days that pull the 14-day ratio under 60 only on the last two days
        Map<LocalDate, FlowSummary.Totals> daily = days(20, 100, 80);
        daily.put(TODAY, new FlowSummary.Totals(2_000, 0, 0));
        daily.put(TODAY.minusDays(1), new FlowSummary.Totals(2_000, 0, 0));
        InflationRisk.Risk r = eval(daily);
        assertEquals(2, r.daysBelowAct());
        assertEquals(InflationRisk.Level.WATCH, r.level());
    }

    @Test
    void tooLittleDataSaysSo() {
        InflationRisk.Risk r = eval(days(2, 100, 10));
        assertEquals(InflationRisk.Level.NO_DATA, r.level());
        assertEquals(2, r.daysWithData());
    }

    @Test
    void rollingRatioUsesTheWindowOnly() {
        Map<LocalDate, FlowSummary.Totals> daily = days(14, 100, 100);
        daily.put(TODAY.minusDays(14), new FlowSummary.Totals(1_000_000, 0, 0)); // just outside the window
        assertEquals(100.0, InflationRisk.ratio(daily, TODAY, 14), 1e-9);
    }
}
