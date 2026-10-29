package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogsLogicTest {

    @Test
    void humanizeAndAgo() {
        assertEquals("island invite", LogText.humanize("island_invite"));
        assertEquals("just now", LogText.ago(1_000, 3_000));
        assertEquals("2h 15m ago", LogText.ago(0, Duration.ofMinutes(135).toMillis()));
        assertEquals("1970-01-01 00:00:00 UTC", LogText.utc(0));
    }

    @Test
    void ipsAreHiddenUnlessAllowed() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("IP", "1.2.3.4");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ip", "1.2.3.4");
        details.put("bedrock", false);
        details.put("whois", nested);
        Map<String, Object> hidden = LogText.redact(details, false);
        assertEquals(LogText.HIDDEN, hidden.get("ip"));
        assertEquals(false, hidden.get("bedrock"));
        assertEquals(LogText.HIDDEN, ((Map<?, ?>) hidden.get("whois")).get("IP"));
        assertEquals("1.2.3.4", LogText.redact(details, true).get("ip"));
    }

    @Test
    void csvQuotesOnlyWhenNeeded() {
        assertEquals("plain", LogText.csv("plain"));
        assertEquals("\"a,b\"", LogText.csv("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", LogText.csv("say \"hi\""));
        assertEquals("", LogText.csv(null));
    }

    @Test
    void detailLinesAreCappedAndShortened() {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            details.put("k" + i, "value " + "x".repeat(60));
        }
        List<String> lines = LogText.detailLines(details, 3, 20);
        assertEquals(4, lines.size());
        assertTrue(lines.getFirst().length() <= 20);
        assertTrue(lines.getLast().contains("2 more"));
    }

    @Test
    void prettyJsonWraps() {
        List<String> lines = LogText.prettyLines(Map.of("line", "/pay Alex " + "9".repeat(80)), 30);
        assertTrue(lines.size() > 3);
        lines.forEach(l -> assertTrue(l.length() <= 34, l));
    }

    @Test
    void mainTextPrefersCommandLineOrMessage() {
        assertEquals("/is home", LogText.mainText(Map.of("line", "/is home")));
        assertEquals("hi", LogText.mainText(Map.of("message", "hi")));
        assertNull(LogText.mainText(Map.of("amount", 5)));
    }

    @Test
    void filterOnlySearchesAllowedCategories() {
        UUID steve = UUID.randomUUID();
        LogFilter f = new LogFilter().player(steve, "Steve").categories(AuditCategory.ECONOMY, AuditCategory.STAFF);
        Set<AuditCategory> helper = EnumSet.of(AuditCategory.CHAT, AuditCategory.COMMAND);
        assertTrue(f.effectiveCategories(helper).isEmpty());
        assertNull(f.query(helper, 0));
        Set<AuditCategory> mod = EnumSet.of(AuditCategory.CHAT, AuditCategory.ECONOMY);
        assertEquals(EnumSet.of(AuditCategory.ECONOMY), f.effectiveCategories(mod));
        assertNotNull(f.query(mod, 0));
        LogFilter all = new LogFilter();
        assertEquals(helper, all.effectiveCategories(helper));
    }

    @Test
    void copyIsIndependent() {
        LogFilter f = new LogFilter().player(UUID.randomUUID(), "Steve").categories(AuditCategory.CHAT);
        f.range = Duration.ofDays(1);
        LogFilter c = f.copy();
        c.categories.clear();
        c.range = Duration.ZERO;
        assertFalse(f.categories.isEmpty());
        assertEquals(Duration.ofDays(1), f.range);
        assertEquals("Steve", c.playerName);
    }

    @Test
    void describeNamesTheSearch() {
        LogFilter f = new LogFilter().actor(UUID.randomUUID(), "Mod");
        Map<String, Object> d = f.describe(EnumSet.of(AuditCategory.STAFF));
        assertEquals("actor", d.get("subject"));
        assertEquals("Mod", d.get("player"));
        assertEquals("all", d.get("range"));
        assertEquals(List.of("staff"), d.get("categories"));
    }

    @Test
    void timeRangeCycles() {
        List<Duration> options = List.of(Duration.ofHours(1), Duration.ofDays(7), Duration.ZERO);
        assertEquals(Duration.ofDays(7), LogsHubMenu.next(options, Duration.ofHours(1)));
        assertEquals(Duration.ofHours(1), LogsHubMenu.next(options, Duration.ZERO));
        assertEquals(Duration.ofHours(1), LogsHubMenu.next(options, Duration.ofDays(3)));
    }
}
