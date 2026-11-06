package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;

import java.util.List;

/**
 * A 6-row menu listing entries in the 7x4 grid (slots 10-16, 19-25, 28-34, 37-43), filled left to right; a half
 * empty last row stays left aligned (lists are not centred, I9 section 2). Empty slots stay empty. Subclasses may
 * place entries differently with {@link #pageLayout()} (grouped shop grid, podium boards).
 * <ul>
 *   <li>The standard bottom row ({@link MenuLayout}): 45 Back on every page (if there is somewhere to go back to),
 *       49 Close, 52 Previous page, 53 Next page (paper, count = target page; only when there is more than one page).
 *       Free action slots for {@link #decorate()}: 46, 47.</li>
 *   <li>The title shows the page when there is more than one: {@code ▸ Island » Warps (2/5)}.</li>
 *   <li>An empty list shows one "nothing here" item in the middle (slot 22).</li>
 *   <li>Turning a page updates the open window in place (no flicker).</li>
 * </ul>
 */
public abstract class PagedMenu<T> extends Menu {

    /** The 28 content slots (the 7x4 list grid). */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Middle slot for the empty-state item. */
    public static final int SLOT_EMPTY = 22;

    private int page;
    /** Rows chosen on the first build of a {@link #compact()} menu (0 = not chosen yet); kept while this menu lives. */
    private int fittedRows;

    /** All entries (called on every build and for the title; keep it cheap or cache). */
    protected abstract List<T> entries();

    /** The button for one entry. */
    protected abstract MenuButton entry(T value);

    /** Breadcrumbs of the title, e.g. {@code [Island, Warps]} (see {@link MenuService#title}). */
    protected abstract List<Component> titleCrumbs();

    /** Extra items (e.g. header buttons in the top row), placed after the bottom row and before the entries. */
    protected void decorate() {
    }

    /**
     * How entries are split into pages and placed (MENUS-V3): default {@link PageLayout#grid()}, the 7x4 list filled
     * left to right. Override for grouped grids (shop) or podium boards ({@link PodiumPages}).
     */
    protected PageLayout<T> pageLayout() {
        return PageLayout.grid();
    }

    /** Message key (in {@link #messageModule()}) of the item shown when there are no entries ({@code .name}/{@code .lore}). */
    protected String emptyKey() {
        return "empty";
    }

    /**
     * Menu layout pass (owner 2 Nov 2026: "smaller GUIs, no empty slots"): true = the window shrinks to the rows its
     * entries need (3 to 6; a list that needs more than one page stays at 6). Only for menus whose extra buttons are all
     * in the top row (slots 0-8): the bottom row moves up. The size is chosen once, on the first build, so the window
     * never resizes while open; a list that grows later pages on the smaller grid.
     */
    protected boolean compact() {
        return false;
    }

    @Override
    public final int rows() {
        if (!compact()) {
            return 6;
        }
        if (fittedRows == 0) {
            fittedRows = fitRows(entries().size());
        }
        return fittedRows;
    }

    /** Pure: rows for {@code entries} on the 7-wide grid plus the top and bottom row, at least 3, at most 6. */
    public static int fitRows(int entries) {
        if (entries > CONTENT_SLOTS.length) {
            return 6;
        }
        int contentRows = Math.max(1, (entries + 6) / 7);
        return Math.max(3, contentRows + 2);
    }

    /** The layout in use: a compact menu pages on its own centred grid, others use {@link #pageLayout()}. */
    private PageLayout<T> layout() {
        return compact() ? PageLayout.centered((rows() - 2) * 7) : pageLayout();
    }

    @Override
    public final Component title() {
        int total = layout().pages(entries()).size();
        page = Math.max(0, Math.min(page, total - 1));
        Component suffix = total > 1
                ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", total))
                : null;
        return service.title(viewer, titleCrumbs(), suffix);
    }

    @Override
    protected final void build() {
        navBar();
        decorate();
        int previousSlot = MenuLayout.previous(rows());
        int nextSlot = MenuLayout.next(rows());
        MenuButton atPrevious = button(previousSlot); // a clickable item here came from decorate()
        boolean customPrevious = atPrevious != null && atPrevious.clickable();
        List<T> list = entries();
        List<PageLayout.Page<T>> pages = layout().pages(list);
        int total = Math.max(1, pages.size());
        page = Math.max(0, Math.min(page, total - 1));
        if (!pages.isEmpty()) {
            PageLayout.Page<T> current = pages.get(page);
            for (int i = 0; i < current.entries().size(); i++) {
                set(current.slots()[i], entry(current.entries().get(i)));
            }
        }
        if (list.isEmpty()) {
            set(MenuLayout.empty(rows()), service.emptyItem(viewer, messageModule(), emptyKey()));
        }
        int shown = page + 1;
        if (page > 0) {
            if (customPrevious) {
                service.warnStandardSlot(this, previousSlot);
            }
            set(previousSlot, service.pageItem(viewer, false, shown - 1, total), click -> turn(-1));
        }
        if (page < total - 1) {
            set(nextSlot, service.pageItem(viewer, true, shown + 1, total), click -> turn(1));
        }
    }

    /** Shows the new page in the same window; the title's page indicator updates in place (plays the page sound). */
    private void turn(int delta) {
        page += delta;
        redraw();
    }

    public final int page() {
        return page;
    }

    /** Jumps to a page (clamped on the next build). */
    public final void page(int page) {
        this.page = Math.max(0, page);
    }
}
