package net.skybornmc.skyblock.common.menu;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLogicTest {

    @Test
    void paginationBasics() {
        Pagination p = new Pagination(60, 28);
        assertEquals(3, p.pages());
        assertEquals(0, p.from(0));
        assertEquals(28, p.to(0));
        assertEquals(56, p.from(2));
        assertEquals(60, p.to(2));
        assertFalse(p.hasPrevious(0));
        assertTrue(p.hasNext(1));
        assertFalse(p.hasNext(2));
        assertEquals(2, p.clamp(99));
        assertEquals(0, p.clamp(-5));
    }

    @Test
    void paginationEdges() {
        Pagination empty = new Pagination(0, 28);
        assertEquals(1, empty.pages());
        assertEquals(0, empty.from(0));
        assertEquals(0, empty.to(0));
        Pagination exact = new Pagination(28, 28);
        assertEquals(1, exact.pages());
        assertFalse(exact.hasNext(0));
        assertThrows(IllegalArgumentException.class, () -> new Pagination(5, 0));
    }

    @Test
    void debounceBlocksFastClicks() {
        Debounce d = new Debounce(120);
        UUID id = UUID.randomUUID();
        assertTrue(d.tryClick(id, 1_000));
        assertFalse(d.tryClick(id, 1_050));
        assertFalse(d.tryClick(id, 1_119));
        assertTrue(d.tryClick(id, 1_120));
        assertTrue(d.tryClick(UUID.randomUUID(), 1_121));
        d.forget(id);
        assertTrue(d.tryClick(id, 1_121));
    }

    @Test
    void debounceToleratesClockGoingBack() {
        Debounce d = new Debounce(120);
        UUID id = UUID.randomUUID();
        assertTrue(d.tryClick(id, 5_000));
        assertTrue(d.tryClick(id, 1_000));
    }

    @Test
    void numberPickerMathIsClampedAndOverflowSafe() {
        assertEquals(10, NumberPickerMenu.add(5, 100, 1, 10));
        assertEquals(1, NumberPickerMenu.add(5, -100, 1, 10));
        assertEquals(Long.MAX_VALUE, NumberPickerMenu.add(Long.MAX_VALUE - 1, 100, 0, Long.MAX_VALUE));
        assertEquals(7, NumberPickerMenu.clamp(7, 1, 10));
    }

    @Test
    void frameRolesFollowTheStyleGuide() {
        assertEquals(MenuService.FrameRole.ACCENT, MenuService.frameRole(0, 6));
        assertEquals(MenuService.FrameRole.GLOW, MenuService.frameRole(1, 6));
        assertEquals(MenuService.FrameRole.BORDER, MenuService.frameRole(4, 6));
        assertEquals(MenuService.FrameRole.BORDER, MenuService.frameRole(9, 6));
        assertEquals(MenuService.FrameRole.INNER, MenuService.frameRole(10, 6));
        assertEquals(MenuService.FrameRole.ACCENT, MenuService.frameRole(53, 6));
        assertEquals(MenuService.FrameRole.GLOW, MenuService.frameRole(46, 6));
        assertEquals(MenuService.FrameRole.ACCENT, MenuService.frameRole(18, 3));
        assertEquals(MenuService.FrameRole.INNER, MenuService.frameRole(13, 3));
    }

    @Test
    void longTitlesDropLeadingCrumbsFirst() {
        java.util.List<String> crumbs = java.util.List.of("Island", "Upgrades", "Member Slots");
        // marker "▸ " = 2, separator " » " = 3
        assertEquals(0, MenuTitles.firstKept(crumbs, 2, 3, 40));
        assertEquals(1, MenuTitles.firstKept(crumbs, 2, 3, 25));
        assertEquals(2, MenuTitles.firstKept(crumbs, 2, 3, 10));
        assertEquals("Member S..", MenuTitles.shorten("Member Slots", 10));
        assertEquals("Short", MenuTitles.shorten("Short", 10));
    }

    @Test
    void headSourcesAreRecognised() {
        assertTrue(MenuHeads.isPlayerName("MHF_ArrowLeft"));
        assertFalse(MenuHeads.isPlayerName("not a name"));
        assertTrue(MenuHeads.looksLikeTexture("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjIn19fQ=="));
        assertFalse(MenuHeads.looksLikeTexture("MHF_ArrowLeft"));
    }
}
