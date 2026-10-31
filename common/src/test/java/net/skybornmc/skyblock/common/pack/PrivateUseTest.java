package net.skybornmc.skyblock.common.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrivateUseTest {

    @Test
    void stripsOnlyPrivateUse() {
        assertEquals("Hi Bob!", PrivateUse.strip("Hi Bob!"));
        assertEquals("ææ ✔ 😀", PrivateUse.strip("ææ ✔ 😀"));
        assertNull(PrivateUse.strip(null));
        String same = "plain";
        assertSame(same, PrivateUse.strip(same));
    }

    @Test
    void bounds() {
        assertTrue(PrivateUse.isPrivateUse(0xE000));
        assertTrue(PrivateUse.isPrivateUse(0xF8FF));
        assertFalse(PrivateUse.isPrivateUse(0xDFFF));
        assertFalse(PrivateUse.isPrivateUse(0xF900));
        assertTrue(PrivateUse.contains("a"));
        assertFalse(PrivateUse.contains("abc"));
    }
}
