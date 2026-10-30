package net.skybornmc.skyblock.cosmetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TagStyleTest {

    @Test
    void removesOuterBracketsAroundTags() {
        assertEquals("<c:brand><grad:phoenix>Phoenix</grad></c:brand>",
                TagStyle.unbracket("<c:brand>[<grad:phoenix>Phoenix</grad>]</c:brand>"));
        assertEquals("<c:flame>OG", TagStyle.unbracket("<c:flame>[OG]"));
        assertEquals("<grad:skyborn>Skyborn</grad>", TagStyle.unbracket("<grad:skyborn>[Skyborn]</grad>"));
        assertEquals("&6OG", TagStyle.unbracket("&6[OG]"));
        assertEquals("&#FF8A1FEmber", TagStyle.unbracket("&#FF8A1F[Ember]"));
    }

    @Test
    void leavesOtherDisplaysAlone() {
        assertEquals("<c:flame>OG", TagStyle.unbracket("<c:flame>OG"));
        assertEquals("<c:flame>[OG] fan", TagStyle.unbracket("<c:flame>[OG] fan"));
        assertEquals("", TagStyle.unbracket(""));
        assertEquals(null, TagStyle.unbracket(null));
        assertEquals("", TagStyle.unbracket("[]"));
    }

    @Test
    void wrapsWithTheConfiguredFormat() {
        assertEquals("<c:muted>(</c:muted><c:flame>OG<c:muted>)</c:muted>",
                TagStyle.wrapRaw("<c:muted>(</c:muted><tag><c:muted>)</c:muted>", "<c:flame>OG"));
        assertEquals("<c:flame>OG", TagStyle.wrapRaw("", "<c:flame>OG"));
        assertEquals("<c:flame>OG", TagStyle.wrapRaw("no placeholder", "<c:flame>OG"));
    }
}
