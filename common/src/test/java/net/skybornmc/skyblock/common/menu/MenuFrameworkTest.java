package net.skybornmc.skyblock.common.menu;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-1: the menu framework rules (GUI_DESIGN §1, §3-§5) without a server. */
class MenuFrameworkTest {

    // ------------------------------------------------------------------ bottom bar per row count

    @Test
    void sixRowBarFollowsTheDesign() {
        assertEquals(45, MenuLayout.back(6));
        assertEquals(48, MenuLayout.info(6));
        assertEquals(49, MenuLayout.close(6));
        assertEquals(50, MenuLayout.mainMenu(6));
        assertEquals(52, MenuLayout.previous(6));
        assertEquals(53, MenuLayout.next(6));
        assertEquals(22, MenuLayout.empty(6));
        // the constants menus use agree with the layout
        assertEquals(MenuLayout.back(6), Menu.SLOT_BACK);
        assertEquals(MenuLayout.info(6), Menu.SLOT_INFO);
        assertEquals(MenuLayout.close(6), Menu.SLOT_CLOSE);
        assertEquals(MenuLayout.mainMenu(6), Menu.SLOT_MAIN_MENU);
        assertEquals(MenuLayout.previous(6), Menu.SLOT_PREVIOUS);
        assertEquals(MenuLayout.next(6), Menu.SLOT_NEXT);
        assertEquals(PagedMenu.SLOT_EMPTY, MenuLayout.empty(6));
    }

    @Test
    void fourAndFiveRowBarsSitOnTheLastRow() {
        assertEquals(27, MenuLayout.back(4));
        assertEquals(30, MenuLayout.info(4));
        assertEquals(31, MenuLayout.close(4));
        assertEquals(32, MenuLayout.mainMenu(4));
        assertEquals(34, MenuLayout.previous(4));
        assertEquals(35, MenuLayout.next(4));
        assertEquals(36, MenuLayout.back(5));
        assertEquals(40, MenuLayout.close(5));
    }

    @Test
    void threeRowMenusHaveBackAndCloseOnly() {
        assertEquals(18, MenuLayout.back(3));
        assertEquals(22, MenuLayout.close(3));
        assertEquals(Menu.SLOT_SMALL_BACK, MenuLayout.back(3));
        assertEquals(Menu.SLOT_SMALL_CLOSE, MenuLayout.close(3));
        assertEquals(MenuLayout.NONE, MenuLayout.info(3));
        assertEquals(MenuLayout.NONE, MenuLayout.mainMenu(3));
        assertEquals(MenuLayout.NONE, MenuLayout.previous(3));
        assertEquals(13, MenuLayout.empty(3));
        for (int rows = 1; rows <= 2; rows++) {
            assertEquals(MenuLayout.NONE, MenuLayout.back(rows));
            assertEquals(MenuLayout.NONE, MenuLayout.close(rows));
        }
    }

    @Test
    void pagingNeverTakesTheBackSlotAndActionSlotsStayFree() {
        for (int rows = 3; rows <= 6; rows++) {
            int back = MenuLayout.back(rows);
            assertTrue(back != MenuLayout.NONE);
            assertTrue(back != MenuLayout.previous(rows), "Previous page must not replace Back (" + rows + " rows)");
            assertTrue(back != MenuLayout.next(rows));
        }
        // the free action slots of the 6-row bar
        for (int slot : new int[]{46, 47, 51}) {
            assertFalse(MenuLayout.standard(6, slot), "slot " + slot + " is free for screen actions");
        }
        for (int slot : new int[]{45, 48, 49, 50, 52, 53}) {
            assertTrue(MenuLayout.standard(6, slot));
        }
        // the confirm buttons (10-16) never collide with the 3-row standard buttons
        for (int slot = 10; slot <= 16; slot++) {
            assertFalse(MenuLayout.standard(3, slot));
        }
    }

    @Test
    void everyStandardSlotFitsItsMenu() {
        for (int rows = 1; rows <= 6; rows++) {
            for (int slot : new int[]{MenuLayout.back(rows), MenuLayout.info(rows), MenuLayout.close(rows),
                    MenuLayout.mainMenu(rows), MenuLayout.previous(rows), MenuLayout.next(rows), MenuLayout.empty(rows)}) {
                assertTrue(slot == MenuLayout.NONE || (slot >= 0 && slot < rows * 9), rows + " rows, slot " + slot);
            }
        }
    }

    // ------------------------------------------------------------------ navigation stack

    /** A stand-in menu: a parent link like {@link Menu}. */
    private static class Screen {
        final String name;
        Screen parent;

        Screen(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class OtherScreen extends Screen {
        OtherScreen(String name) {
            super(name);
        }
    }

    private static Screen open(Screen opening, Screen current, Screen origin, boolean root) {
        Screen parent = Navigation.resolve(opening, opening.parent, current, origin, root, s -> s.parent, Navigation::sameClass);
        opening.parent = parent;
        return parent;
    }

    @Test
    void backReturnsToTheMenuThatOpenedThisOne() {
        Screen hub = new Screen("hub");
        Screen island = new OtherScreen("island");
        // /menu → Island (the hub ran /is while it was open): Back goes to the hub
        assertSame(hub, open(island, hub, null, false));
        assertNull(hub.parent);
    }

    @Test
    void anExplicitParentWins() {
        Screen hub = new Screen("hub");
        Screen help = new Screen("help home");
        Screen topic = new OtherScreen("topic");
        topic.parent = help;
        assertSame(help, open(topic, hub, null, false));
    }

    @Test
    void theMainMenuIsTheBottomOfTheStack() {
        Screen shop = new Screen("shop");
        Screen hub = new OtherScreen("hub");
        hub.parent = shop; // even if code set one
        assertNull(open(hub, shop, null, true));
    }

    @Test
    void reopeningTheOpenMenuChangesNothing() {
        Screen hub = new Screen("hub");
        Screen list = new OtherScreen("list");
        list.parent = hub;
        assertSame(hub, open(list, list, null, false));
        Screen root = new Screen("root");
        assertNull(open(root, root, null, false));
    }

    @Test
    void goingBackToAnAncestorNeverLoops() {
        Screen hub = new Screen("hub");
        Screen island = new OtherScreen("island");
        island.parent = hub;
        // Back from island opens hub (the root, no parent): it must not get island as its parent
        assertNull(open(hub, island, null, false));
        assertFalse(Navigation.inChain(island, null, s -> s.parent));
        assertTrue(Navigation.inChain(island, hub, s -> s.parent));
    }

    @Test
    void aFreshCopyOfTheSameScreenReplacesIt() {
        Screen hub = new Screen("hub");
        Screen bank = new OtherScreen("bank");
        bank.parent = hub;
        Screen freshBank = new OtherScreen("bank (fresh)");
        // the bank reopened itself as a new copy: Back still goes to the hub, not to the stale copy
        assertSame(hub, open(freshBank, bank, null, false));
    }

    @Test
    void aMenuOpenedAfterAPromptOrCommandGoesBackToWhereItStarted() {
        Screen hub = new Screen("hub");
        Screen loaded = new OtherScreen("loaded later");
        // nothing open (the prompt closed the window / data was loading), the origin is remembered
        assertSame(hub, open(loaded, null, hub, false));
        // the origin itself re-opening is not its own child
        assertNull(open(hub, null, hub, false));
    }

    @Test
    void theStackIsClearedOnClose() {
        // after the player closed the menu nothing is open and no origin is left: a new stack starts
        Screen next = new Screen("next");
        assertNull(open(next, null, null, false));
    }

    @Test
    void longStacksAreTrimmedAndCyclesBroken() {
        List<Screen> chain = new ArrayList<>();
        Screen previous = null;
        for (int i = 0; i < 30; i++) {
            Screen s = new Screen("s" + i);
            s.parent = previous;
            chain.add(s);
            previous = s;
        }
        Screen top = chain.getLast();
        assertEquals(30, Navigation.depth(top, s -> s.parent));
        Navigation.trim(top, 16, s -> s.parent, (s, p) -> s.parent = p);
        assertEquals(16, Navigation.depth(top, s -> s.parent));

        Screen a = new Screen("a");
        Screen b = new OtherScreen("b");
        a.parent = b;
        b.parent = a; // a hand-made cycle
        Navigation.trim(a, 16, s -> s.parent, (s, p) -> s.parent = p);
        assertEquals(2, Navigation.depth(a, s -> s.parent));
        assertNull(b.parent);
    }

    // ------------------------------------------------------------------ confirm flow

    @Test
    void oneStepConfirmRunsOnce() {
        ConfirmFlow flow = new ConfirmFlow(false);
        assertArrayEquals(new int[]{10, 11, 12}, flow.confirmSlots());
        assertArrayEquals(new int[]{14, 15, 16}, flow.cancelSlots());
        assertEquals(ConfirmFlow.Result.RUN, flow.confirm());
        assertEquals(ConfirmFlow.Result.IGNORED, flow.confirm(), "a double click never runs twice");
        assertEquals(ConfirmFlow.Result.IGNORED, flow.cancel());
        assertEquals(13, ConfirmFlow.DETAILS_SLOT);
    }

    @Test
    void twoStepConfirmAsksAgainWithTheSidesSwapped() {
        ConfirmFlow flow = new ConfirmFlow(true);
        assertEquals(1, flow.step());
        assertEquals(2, flow.steps());
        int[] firstConfirm = flow.confirmSlots();
        assertEquals(ConfirmFlow.Result.NEXT_STEP, flow.confirm());
        assertEquals(2, flow.step());
        assertTrue(flow.swapped());
        // where Confirm was, Cancel is now: a double click lands on Cancel
        assertArrayEquals(firstConfirm, flow.cancelSlots());
        assertArrayEquals(new int[]{14, 15, 16}, flow.confirmSlots());
        assertEquals(ConfirmFlow.Result.RUN, flow.confirm());
        assertEquals(ConfirmFlow.Result.IGNORED, flow.confirm());
    }

    @Test
    void cancellingEndsTheFlow() {
        ConfirmFlow flow = new ConfirmFlow(true);
        assertEquals(ConfirmFlow.Result.NEXT_STEP, flow.confirm());
        assertEquals(ConfirmFlow.Result.CANCEL, flow.cancel());
        assertEquals(ConfirmFlow.Result.IGNORED, flow.confirm());
        assertTrue(flow.done());
        assertTrue(ConfirmFlow.bigButton(11));
        assertTrue(ConfirmFlow.bigButton(15));
        assertFalse(ConfirmFlow.bigButton(13));
    }

    // ------------------------------------------------------------------ clicks

    @Test
    void bothMouseButtonsRunThePlainAction() {
        for (ClickType type : new ClickType[]{ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT}) {
            assertEquals(ClickRoute.PLAIN, ClickRoute.of(type, false, false, false), type.name());
            assertEquals(ClickRoute.PLAIN, ClickRoute.of(type, true, false, false), type.name());
        }
    }

    @Test
    void javaShortcutsOnlyWhenMarked() {
        assertEquals(ClickRoute.PLAIN, ClickRoute.of(ClickType.LEFT, false, true, true));
        assertEquals(ClickRoute.RIGHT, ClickRoute.of(ClickType.RIGHT, false, true, false));
        assertEquals(ClickRoute.RIGHT, ClickRoute.of(ClickType.SHIFT_RIGHT, false, true, false));
        assertEquals(ClickRoute.SHIFT, ClickRoute.of(ClickType.SHIFT_LEFT, false, false, true));
        assertEquals(ClickRoute.SHIFT, ClickRoute.of(ClickType.SHIFT_RIGHT, false, true, true));
        assertEquals(ClickRoute.PLAIN, ClickRoute.of(ClickType.SHIFT_LEFT, false, true, false));
        // Bedrock always gets the plain action
        assertEquals(ClickRoute.PLAIN, ClickRoute.of(ClickType.RIGHT, true, true, true));
        assertEquals(ClickRoute.PLAIN, ClickRoute.of(ClickType.SHIFT_LEFT, true, true, true));
    }

    @Test
    void otherClickKindsNeverRunAnything() {
        for (ClickType type : new ClickType[]{ClickType.NUMBER_KEY, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.DOUBLE_CLICK,
                ClickType.MIDDLE, ClickType.SWAP_OFFHAND, ClickType.CREATIVE}) {
            assertEquals(ClickRoute.IGNORED, ClickRoute.of(type, false, true, true), type.name());
        }
        assertEquals(ClickRoute.IGNORED, ClickRoute.of(null, false, false, false));
    }

    @Test
    void buttonsPickTheRightHandler() {
        List<String> ran = new ArrayList<>();
        Consumer<MenuClick> plain = c -> ran.add("plain");
        Consumer<MenuClick> right = c -> ran.add("right");
        MenuButton button = MenuButton.of(null, plain).withRight(right);
        assertSame(plain, button.handler(ClickRoute.PLAIN));
        assertSame(right, button.handler(ClickRoute.RIGHT));
        assertSame(plain, button.handler(ClickRoute.SHIFT), "no shift shortcut: shift-click is a plain click");
        assertNull(button.handler(ClickRoute.IGNORED));
        assertTrue(button.clickable());
        assertFalse(MenuButton.display(null).clickable());
    }

    @Test
    void aShortcutWithoutAPlainActionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> MenuButton.display(null).withRight(c -> { }));
        assertThrows(IllegalArgumentException.class, () -> new MenuButton(null, null, null, c -> { }));
    }

    // ------------------------------------------------------------------ titles

    @Test
    void titlesKeepAtMostTwoParts() {
        List<String> crumbs = List.of("Island", "Upgrades", "Member Slots");
        assertEquals(1, MenuTitles.first(crumbs, 2, 2, 3, 40), "Feature » Screen: the leading part is dropped");
        assertEquals(0, MenuTitles.first(List.of("Island", "Members"), 2, 2, 3, 40));
        assertEquals(2, MenuTitles.first(crumbs, 1, 2, 3, 40));
        // the length rule still applies on top
        assertEquals(2, MenuTitles.first(crumbs, 2, 2, 3, 16));
        assertEquals(0, MenuTitles.first(List.of(), 2, 2, 3, 40));
    }
}
