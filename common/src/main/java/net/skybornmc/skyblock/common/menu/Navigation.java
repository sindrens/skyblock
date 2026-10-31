package net.skybornmc.skyblock.common.menu;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * The per-player navigation stack (GUI_DESIGN §0.3 "you can never get lost"). The stack is the chain of parents of the
 * open menu: Back pops it by opening the parent. These rules decide the parent of a menu that is being opened, so Back
 * returns to the screen the player came from even when the code that opened it did not say so (for example a command
 * run from /menu). Pure and generic so it is unit-tested without a server.
 *
 * <ol>
 *   <li>The main menu is the bottom of the stack: it never gets a parent.</li>
 *   <li>A parent set by the code ({@link Menu#openChild}, {@link Menu#parent(Menu)}) always wins.</li>
 *   <li>Re-opening the open menu (page turn, tab, refresh) changes nothing.</li>
 *   <li>Otherwise the menu the player has open (or, right after a chat prompt or a command started from a menu, that
 *       menu) becomes the parent, unless the new menu is that menu or one of its ancestors (going back never loops).</li>
 *   <li>A new screen of the same kind (a fresh copy of the same menu class) replaces the open one instead of stacking
 *       on it, so Back skips over refreshed copies.</li>
 * </ol>
 * The stack is cleared when the player closes the menu: nothing is open any more, so the next menu starts a new stack.
 */
public final class Navigation {

    /** Upper bound for walking a chain, in case code built a cycle by hand. */
    static final int WALK_LIMIT = 256;

    private Navigation() {
    }

    /**
     * The parent {@code opening} should get.
     *
     * @param opening        the menu being opened
     * @param explicitParent the parent the code set ({@code null} = none)
     * @param current        the menu the player has open now ({@code null} = none)
     * @param origin         the menu a prompt or command was started from, if nothing is open ({@code null} = none)
     * @param root           true for the main menu (bottom of the stack)
     * @param parentOf       reads a menu's parent
     * @param sameScreen     true if two menus are the same kind of screen
     */
    public static <M> M resolve(M opening, M explicitParent, M current, M origin, boolean root,
                                Function<M, M> parentOf, BiPredicate<M, M> sameScreen) {
        if (root) {
            return null;
        }
        if (explicitParent != null || opening == current) {
            return explicitParent;
        }
        M from = current != null ? current : origin;
        if (from == null || from == opening || inChain(from, opening, parentOf)) {
            return null;
        }
        if (sameScreen.test(from, opening)) {
            return parentOf.apply(from);
        }
        return from;
    }

    /** True if {@code target} is {@code start} or one of its ancestors. */
    public static <M> boolean inChain(M start, M target, Function<M, M> parentOf) {
        M node = start;
        for (int i = 0; node != null && i < WALK_LIMIT; i++) {
            if (node == target) {
                return true;
            }
            node = parentOf.apply(node);
        }
        return false;
    }

    /** Number of menus in the stack starting at {@code start} (itself included). */
    public static <M> int depth(M start, Function<M, M> parentOf) {
        int depth = 0;
        Set<M> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (M node = start; node != null && seen.add(node); node = parentOf.apply(node)) {
            depth++;
        }
        return depth;
    }

    /**
     * Keeps at most {@code maxDepth} menus in the stack (the oldest are dropped) and breaks any cycle, so a long session
     * of clicking forward can't grow the chain without limit.
     */
    public static <M> void trim(M start, int maxDepth, Function<M, M> parentOf, BiConsumer<M, M> setParent) {
        Set<M> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        M node = start;
        int depth = 1;
        while (node != null) {
            seen.add(node);
            M parent = parentOf.apply(node);
            if (parent == null) {
                return;
            }
            if (depth >= Math.max(1, maxDepth) || seen.contains(parent)) {
                setParent.accept(node, null);
                return;
            }
            node = parent;
            depth++;
        }
    }

    /** Default "same kind of screen": the same menu class. */
    public static boolean sameClass(Object a, Object b) {
        return a != null && b != null && a.getClass() == b.getClass();
    }
}
