package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatTest {

    @Test
    void numbersAndMoney() {
        assertEquals("1,234,567", Format.number(1_234_567));
        assertEquals("$12,500", Format.money(12_500));
        assertEquals("$1.2M", Format.money(1_299_999));
        assertEquals("-$500", Format.money(-500));
    }

    @Test
    void durations() {
        assertEquals("2h 15m", Format.duration(Duration.ofMinutes(135)));
        assertEquals("3d 4h", Format.duration(Duration.ofHours(76)));
        assertEquals("45s", Format.duration(Duration.ofSeconds(45)));
        assertEquals("1d", Format.duration(Duration.ofDays(1)));
    }

    @ParameterizedTest
    @CsvSource({"1500,1500", "1.5k,1500", "2M,2000000", "3b,3000000000", "'1,000',1000", "0.5k,500"})
    void parsesAmounts(String input, long expected) {
        assertEquals(expected, Format.parseAmount(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-5", "0", "1.5", "1.2345k", "99999999999q"})
    void rejectsBadAmounts(String input) {
        assertThrows(IllegalArgumentException.class, () -> Format.parseAmount(input));
    }
}
