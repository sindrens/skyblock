package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingUtilsTest {

    @Test
    void cooldownLifecycle() {
        AtomicLong now = new AtomicLong(1_000);
        Cooldowns cooldowns = new Cooldowns(now::get);
        UUID id = UUID.randomUUID();
        assertTrue(cooldowns.tryUse(id, "tpa", Duration.ofSeconds(10)));
        assertFalse(cooldowns.tryUse(id, "tpa", Duration.ofSeconds(10)));
        assertEquals(Duration.ofSeconds(10), cooldowns.remaining(id, "tpa"));
        now.addAndGet(4_000);
        assertEquals(Duration.ofSeconds(6), cooldowns.remaining(id, "tpa"));
        now.addAndGet(6_000);
        assertTrue(cooldowns.ready(id, "tpa"));
        assertTrue(cooldowns.ready(id, "other"));
        cooldowns.set(id, "x", Duration.ofMinutes(1));
        cooldowns.clear(id);
        assertTrue(cooldowns.ready(id, "x"));
    }

    @Test
    void slowOperationWarnsOncePerMinute() {
        Logger log = Logger.getLogger("slow-test");
        log.setUseParentHandlers(false);
        List<String> warnings = new ArrayList<>();
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        AtomicLong clock = new AtomicLong(0);
        SlowOperationTimer timer = new SlowOperationTimer(log, () -> 5, clock::get);
        assertFalse(timer.record("fast", 4_000_000));
        assertTrue(timer.record("tick", 12_000_000));
        assertTrue(timer.record("tick", 20_000_000));
        assertEquals(1, warnings.size());
        clock.addAndGet(SlowOperationTimer.WARN_INTERVAL_NANOS);
        timer.record("tick", 8_000_000);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(1).contains("1 more slow runs"));
        assertTrue(warnings.get(1).contains("20.0 ms"));
    }
}
