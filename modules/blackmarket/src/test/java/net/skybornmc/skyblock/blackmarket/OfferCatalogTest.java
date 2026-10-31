package net.skybornmc.skyblock.blackmarket;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The offer lines of blackmarket.yml. */
class OfferCatalogTest {

    @Test
    void theDefaultOffersParseWithoutProblems() {
        List<String> problems = new ArrayList<>();
        OfferCatalog c = OfferCatalog.parse(new BlackMarketSettings().offers, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(16, c.all().size());
        assertEquals(1, c.of(OfferCatalog.Slot.FIXED).size());
        assertEquals(3, c.of(OfferCatalog.Slot.HEADLINE).size());
    }

    @Test
    void theDefaultsFollowThePlanPricesAndLimits() {
        OfferCatalog c = OfferCatalog.parse(new BlackMarketSettings().offers, p -> { });
        OfferCatalog.Offer regalia = c.get("regalia").orElseThrow();
        assertEquals(400, regalia.price());
        assertEquals(1, regalia.limit());
        assertEquals(OfferCatalog.Period.SEASON, regalia.period());
        OfferCatalog.Offer eclipse = c.get("eclipse_key").orElseThrow();
        assertEquals(75, eclipse.price());
        assertEquals(OfferCatalog.Period.MONTH, eclipse.period());
        assertEquals(2, c.get("phoenix_key").orElseThrow().limit());
        assertEquals(8, c.get("scroll").orElseThrow().price());
        assertFalse(c.get("sell_wand").orElseThrow().rollable(), "coming soon is never rolled");
        assertFalse(c.get("scroll").orElseThrow().rollable(), "the fixed row is not rolled");
        assertTrue(c.get("elytra").orElseThrow().rollable());
        assertFalse(c.liveIds().contains("blueprint"));
    }

    @Test
    void aLineIsReadKeyByKey() {
        OfferCatalog.Offer o = OfferCatalog.parseLine("Book_X: slot=rare price=1_000 weight=2.5 reward=enchant-book:EPIC amount=3 level=2 "
                + "limit=4/day icon=book");
        assertEquals("book_x", o.id());
        assertEquals(OfferCatalog.Slot.RARE, o.slot());
        assertEquals(1000, o.price());
        assertEquals(2.5, o.weight());
        assertEquals(OfferCatalog.Kind.ENCHANT_BOOK, o.kind());
        assertEquals("epic", o.ref());
        assertEquals(3, o.amount());
        assertEquals(2, o.level());
        assertEquals(4, o.limit());
        assertEquals(OfferCatalog.Period.DAY, o.period());
        assertEquals("book", o.icon());
    }

    @Test
    void noLimitMeansUnlimitedAndAPlainNumberIsPerRotation() {
        assertEquals(0, OfferCatalog.parseLine("a: slot=common price=1 reward=item:dirt").limit());
        OfferCatalog.Offer o = OfferCatalog.parseLine("a: slot=common price=1 reward=item:dirt limit=3");
        assertEquals(3, o.limit());
        assertEquals(OfferCatalog.Period.ROTATION, o.period());
    }

    @Test
    void brokenLinesAreReportedAndSkipped() {
        List<String> problems = new ArrayList<>();
        OfferCatalog c = OfferCatalog.parse(List.of(
                "ok: slot=common price=5 reward=item:elytra",
                "noslot: price=5 reward=item:elytra",
                "badprice: slot=rare price=-1 reward=scroll",
                "badkind: slot=rare price=1 reward=pony",
                "noref: slot=rare price=1 reward=key",
                "tier: slot=rare price=1 reward=spawner:9",
                "tag: slot=rare price=1 reward=tag:x amount=2",
                "period: slot=rare price=1 reward=scroll limit=1/week",
                "ok: slot=rare price=1 reward=scroll",
                "no colon here"), problems::add);
        assertEquals(List.of("ok"), c.all().stream().map(OfferCatalog.Offer::id).toList());
        assertEquals(9, problems.size());
    }

    @Test
    void badIdsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> OfferCatalog.parseLine("bad id!: slot=rare price=1 reward=scroll"));
        assertThrows(IllegalArgumentException.class, () -> OfferCatalog.parseLine("x: slot=rare price=1 reward=scroll junk"));
    }
}
