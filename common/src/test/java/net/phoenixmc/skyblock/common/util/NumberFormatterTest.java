package net.phoenixmc.skyblock.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormatterTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "950, 950",
            "1000, 1k",
            "1250, 1.2k",
            "3499999, 3.4M",
            "5000000000, 5B",
            "-1500, -1.5k"
    })
    void formatsCompactly(double input, String expected) {
        assertEquals(expected, NumberFormatter.compact(input));
    }
}
