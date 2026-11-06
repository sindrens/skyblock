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
        assertEquals(15, c.all().size(), "owner 7 Nov 2026 (Q-71): Sell Wand, Auto-Sell Chest and Chunk Collector are off");
        assertEquals(4, c.of(OfferCatalog.Slot.FIXED).size(), "scroll + 3 premium spawners (ECON-SPAWNERS)");
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
        assertEquals(1, c.get("phoenix_key").orElseThrow().limit(), "ECON-SHARDS: 1 per rotation");
        assertTrue(c.get("spawner_t5").isEmpty(), "the random tier 5 spawner is gone");
        OfferCatalog.Offer golem = c.get("spawner_iron_golem").orElseThrow();
        assertEquals("5:iron_golem", golem.ref());
        assertEquals(400, golem.price());
        assertEquals(OfferCatalog.Period.DAY, golem.period());
        assertEquals(8, c.get("scroll").orElseThrow().price());
        assertTrue(c.get("sell_wand").isEmpty(), "owner 7 Nov 2026 (Q-71): off the Black Market until tested and approved");
        assertTrue(c.get("chunk_collector").isEmpty());
        assertTrue(c.get("autosell_chest").isEmpty());
        assertFalse(c.get("wardrobe_tome").orElseThrow().rollable(), "coming soon is never rolled");
        OfferCatalog old = OfferCatalog.parse(BlackMarketSettings.OFFERS_V4, w -> { });
        assertEquals(OfferCatalog.Kind.SELL_WAND, old.get("sell_wand").orElseThrow().kind(), "the offer kinds still parse");
        assertEquals(OfferCatalog.Kind.CHUNK_COLLECTOR, old.get("chunk_collector").orElseThrow().kind());
        assertFalse(c.get("scroll").orElseThrow().rollable(), "the fixed row is not rolled");
        assertTrue(c.get("elytra").orElseThrow().rollable());
        assertFalse(c.liveIds().contains("blueprint"));
    }

    @Test
    void spawnerOffersTakeAnOptionalMob() {
        assertEquals("5:evoker", OfferCatalog.parseLine("x: slot=fixed price=1 reward=spawner:5:evoker").ref());
        assertEquals("3", OfferCatalog.parseLine("x: slot=rare price=1 reward=spawner:3").ref());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OfferCatalog.parseLine("x: slot=fixed price=1 reward=spawner:6:evoker"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OfferCatalog.parseLine("x: slot=fixed price=1 reward=spawner:5:Evoker!"));
        java.util.function.IntFunction<List<String>> tiers = t -> t == 5 ? List.of("iron_golem", "wither_skeleton", "evoker") : List.of();
        assertEquals(List.of("evoker"), BlackMarketModule.spawnerTypes("5:evoker", tiers));
        assertEquals(List.of(), BlackMarketModule.spawnerTypes("5:shulker", tiers), "a mob outside the tier is not offered");
        assertEquals(3, BlackMarketModule.spawnerTypes("5", tiers).size());
        assertEquals(List.of(), BlackMarketModule.spawnerTypes("x", tiers));
    }

    @Test
    void anUntouchedOfferListMigrates() {
        var change = new BlackMarketSettings().valueChanges().get(2).get("offers");
        assertEquals(BlackMarketSettings.OFFERS_V1, change.from());
        assertEquals(BlackMarketSettings.OFFERS, change.to());
        var v3 = new BlackMarketSettings().valueChanges().get(3).get("offers");
        assertEquals(BlackMarketSettings.OFFERS_V2, v3.from());
        assertEquals(new BlackMarketSettings().offers, v3.to());
        var v4 = new BlackMarketSettings().valueChanges().get(4).get("offers");
        assertEquals(BlackMarketSettings.OFFERS_V3, v4.from());
        assertEquals(BlackMarketSettings.OFFERS, v4.to());
        var v5 = new BlackMarketSettings().valueChanges().get(5).get("offers");
        assertEquals(BlackMarketSettings.OFFERS_V4, v5.from());
        assertEquals(BlackMarketSettings.OFFERS, v5.to());
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
