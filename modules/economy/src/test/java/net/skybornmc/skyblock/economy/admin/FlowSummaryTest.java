package net.skybornmc.skyblock.economy.admin;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowSummaryTest {

    private static final Map<String, String> TRANSIT = FlowSummary.transitGroups(
            List.of("auction ah_buy ah_sale", "notes note_withdraw note_redeem"));

    private static FlowSummary.FlowRow row(long hour, String reason, Flow flow, long amount) {
        return new FlowSummary.FlowRow(hour, reason, flow, amount, 1);
    }

    @Test
    void parsesTransitGroups() {
        assertEquals("auction", TRANSIT.get("ah_buy"));
        assertEquals("auction", TRANSIT.get("ah_sale"));
        assertEquals("notes", TRANSIT.get("note_redeem"));
        assertNull(TRANSIT.get("ah_fee"));
        assertTrue(FlowSummary.transitGroups(List.of("lonely", "")).isEmpty());
    }

    @Test
    void periodTotalsAndSinkRatio() {
        List<FlowSummary.FlowRow> rows = List.of(
                row(10, "sell", Flow.CREATED, 1_200),
                row(10, "shop_buy", Flow.REMOVED, 600),
                row(11, "depths_drop", Flow.CREATED, 800),
                row(11, "forge", Flow.REMOVED, 900),
                row(11, "pay", Flow.MOVED, 300),
                row(12, "sell", Flow.CREATED, 5_000)); // outside [10, 12)
        FlowSummary.Period p = FlowSummary.period(rows, 10, 12, TRANSIT);
        assertEquals(new FlowSummary.Totals(2_000, 1_500, 300), p.totals());
        assertEquals(75.0, p.totals().sinkPercent(), 1e-9);
        assertEquals(500, p.totals().net());
        assertEquals("sell", p.sources().getFirst().key());
        assertEquals("forge", p.sinks().getFirst().key());
    }

    @Test
    void auctionOnlyCountsItsTax() {
        List<FlowSummary.FlowRow> rows = List.of(
                row(10, "ah_buy", Flow.REMOVED, 10_000),   // buyers paid
                row(10, "ah_sale", Flow.CREATED, 9_500),   // sellers got (5% tax)
                row(10, "sell", Flow.CREATED, 1_000));
        FlowSummary.Period p = FlowSummary.period(rows, 0, 100, TRANSIT);
        assertEquals(1_000, p.totals().created());
        assertEquals(500, p.totals().removed());
        FlowSummary.ReasonTotals auction = p.reason("auction");
        assertTrue(auction.group());
        assertEquals(500, auction.removed());
        assertEquals(9_500, auction.transitIn());
        assertEquals(10_000, auction.transitOut());
        assertNull(p.reason("ah_buy"));
    }

    @Test
    void staffNotesBeyondThoseMadeAreASource() {
        List<FlowSummary.FlowRow> rows = List.of(row(10, "note_withdraw", Flow.REMOVED, 100),
                row(10, "note_redeem", Flow.CREATED, 300));
        FlowSummary.Period p = FlowSummary.period(rows, 0, 100, TRANSIT);
        assertEquals(200, p.totals().created());
        assertEquals(0, p.totals().removed());
    }

    @Test
    void noCreatedMoneyMeansNoRatio() {
        assertTrue(Double.isNaN(FlowSummary.Totals.ZERO.sinkPercent()));
    }

    @Test
    void dailyGroupsByDisplayDay() {
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        long midnight = Buckets.firstHour(LocalDate.of(2026, 10, 30), oslo);
        List<FlowSummary.FlowRow> rows = List.of(
                row(midnight - 1, "sell", Flow.CREATED, 10),
                row(midnight, "sell", Flow.CREATED, 20),
                row(midnight + 23, "shop_buy", Flow.REMOVED, 5));
        Map<LocalDate, FlowSummary.Totals> daily = FlowSummary.daily(rows, TRANSIT, oslo);
        assertEquals(10, daily.get(LocalDate.of(2026, 10, 29)).created());
        assertEquals(new FlowSummary.Totals(20, 5, 0), daily.get(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void percentChangeAndVelocity() {
        assertEquals(50.0, FlowSummary.percentChange(100, 150), 1e-9);
        assertEquals(-25.0, FlowSummary.percentChange(100, 75), 1e-9);
        assertTrue(Double.isNaN(FlowSummary.percentChange(-1, 75)));
        assertEquals(0.5, FlowSummary.velocity(50, 100), 1e-9);
        assertTrue(Double.isNaN(FlowSummary.velocity(50, 0)));
    }
}
