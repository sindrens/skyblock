package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4c: the /menu → Top screen (GUI_DESIGN §7.6): podium slots, tab slots, the shared board cache, the hub button. */
class TopScreenTest {

    @Test
    void podiumAndRowFollowTheDesign() {
        assertEquals(22, TopLayout.slot(1), "#1 stands in the middle of the podium");
        assertEquals(21, TopLayout.slot(2));
        assertEquals(23, TopLayout.slot(3));
        for (int place = 4; place <= 10; place++) {
            assertEquals(24 + place, TopLayout.slot(place), "#4-#10 on 28-34");
        }
        assertEquals(-1, TopLayout.slot(0));
        assertEquals(-1, TopLayout.slot(11));
        Set<Integer> slots = new HashSet<>();
        for (int place = 1; place <= TopLayout.PLACES; place++) {
            assertTrue(slots.add(TopLayout.slot(place)), "one slot per place");
        }
        for (int bar : List.of(TopLayout.SLOT_YOU, TopLayout.SLOT_SKILL, TopLayout.SLOT_FULL)) {
            assertTrue(bar == 46 || bar == 47 || bar == 51, "screen actions only on the free bottom-bar slots");
        }
    }

    @Test
    void tabsAreCentredOnTheMiddleSlot() {
        assertArrayEquals(new int[]{4}, TopLayout.tabSlots(1));
        assertArrayEquals(new int[]{3, 5}, TopLayout.tabSlots(2));
        assertArrayEquals(new int[]{3, 4, 5}, TopLayout.tabSlots(3));
        assertArrayEquals(new int[]{2, 3, 5, 6}, TopLayout.tabSlots(4));
        assertArrayEquals(new int[]{2, 3, 4, 5, 6}, TopLayout.tabSlots(5));
        assertEquals(0, TopLayout.tabSlots(0).length);
    }

    @Test
    void podiumNamesAndIcons() {
        assertEquals(".name-1", TopMenu.nameSuffix(1));
        assertEquals(".name-3", TopMenu.nameSuffix(3));
        assertEquals(".name", TopMenu.nameSuffix(4));
    }

    @Test
    void cacheSharesOneLoadUntilItIsOld() {
        AtomicLong now = new AtomicLong(1_000);
        AtomicInteger loads = new AtomicInteger();
        TopCache<String> cache = new TopCache<>(now::get);
        CompletableFuture<String> first = cache.get("money", 30_000, () -> {
            loads.incrementAndGet();
            return CompletableFuture.completedFuture("a");
        });
        now.addAndGet(29_999);
        CompletableFuture<String> second = cache.get("money", 30_000, () -> {
            loads.incrementAndGet();
            return CompletableFuture.completedFuture("b");
        });
        assertSame(first, second, "300 viewers within the TTL share one query");
        assertEquals(1, loads.get());
        now.addAndGet(1);
        assertEquals("c", cache.get("money", 30_000, () -> {
            loads.incrementAndGet();
            return CompletableFuture.completedFuture("c");
        }).join(), "an old board is loaded again");
        assertEquals(2, loads.get());
        assertEquals("d", cache.get("skill:mining", 30_000, () -> CompletableFuture.completedFuture("d")).join(), "boards are cached apart");
    }

    @Test
    void aFailedLoadIsTriedAgainAtOnce() {
        AtomicLong now = new AtomicLong(0);
        TopCache<String> cache = new TopCache<>(now::get);
        cache.get("money", 30_000, () -> CompletableFuture.failedFuture(new IllegalStateException("db down")));
        assertEquals("ok", cache.get("money", 30_000, () -> CompletableFuture.completedFuture("ok")).join());
        cache.get("x", 30_000, () -> {
            throw new IllegalStateException("loader threw");
        });
        assertEquals("ok", cache.get("x", 30_000, () -> CompletableFuture.completedFuture("ok")).join());
    }

    @Test
    void hubTopButtonOpensTheTopScreen() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = getClass().getClassLoader().getResourceAsStream("menu.yml")) {
            assertNotNull(in);
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        ButtonLayout hub = ButtonLayout.hub(yaml, HubButtonsTest.ITEMS, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(ButtonLayout.TOP, hub.buttons().stream().filter(b -> b.id().equals("top")).findFirst().orElseThrow().action());
    }

    @Test
    void aLiveFileWithTheOldCommandStillGetsTheScreen() {
        // A live menu.yml from before GUI-4c keeps `command: "is top"`; the new `action: top` key is merged in by YamlFiles.
        YamlConfiguration live = new YamlConfiguration();
        live.set("hub.top.slot", 41);
        live.set("hub.top.icon", "golden_helmet");
        live.set("hub.top.command", "is top");
        live.set("hub.top.action", "top");
        List<String> problems = new ArrayList<>();
        ButtonLayout hub = ButtonLayout.hub(live, HubButtonsTest.ITEMS, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        ButtonLayout.Button top = hub.buttons().getFirst();
        assertEquals(ButtonLayout.TOP, top.action());
        assertTrue(top.available(s -> true, c -> false), "a built-in screen needs no command");
    }
}
