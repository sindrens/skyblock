package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;

/**
 * A 6-row menu listing entries in the 28 inner slots (rows 2-5 inside the frame).
 * <ul>
 *   <li>Slot 45: Back on page 1 (if there is a parent), Previous page on page 2+. Slot 53: Next page.</li>
 *   <li>The title shows the page when there is more than one: {@code ▸ Island » Warps (2/5)}.</li>
 *   <li>An empty list shows one "nothing here" item in the middle (slot 22).</li>
 * </ul>
 */
public abstract class PagedMenu<T> extends Menu {

    /** The 28 content slots inside the frame. */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Middle slot for the empty-state item. */
    public static final int SLOT_EMPTY = 22;

    private int page;

    /** All entries (called on every build and for the title; keep it cheap or cache). */
    protected abstract List<T> entries();

    /** The button for one entry. */
    protected abstract MenuButton entry(T value);

    /** Breadcrumbs of the title, e.g. {@code [Island, Warps]} (see {@link MenuService#title}). */
    protected abstract List<Component> titleCrumbs();

    /** Extra items (e.g. header buttons in the top row), placed after the frame and before the entries. */
    protected void decorate() {
    }

    /** Message key (in {@link #messageModule()}) of the item shown when there are no entries ({@code .name}/{@code .lore}). */
    protected String emptyKey() {
        return "empty";
    }

    @Override
    public final int rows() {
        return 6;
    }

    @Override
    public final Component title() {
        Pagination pages = new Pagination(entries().size(), CONTENT_SLOTS.length);
        page = pages.clamp(page);
        Component suffix = pages.pages() > 1
                ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", pages.pages()))
                : null;
        return service.title(viewer, titleCrumbs(), suffix);
    }

    @Override
    protected final void build() {
        frame();
        decorate();
        List<T> list = entries();
        Pagination pages = new Pagination(list.size(), CONTENT_SLOTS.length);
        page = pages.clamp(page);
        int slot = 0;
        for (int i = pages.from(page); i < pages.to(page); i++) {
            set(CONTENT_SLOTS[slot++], entry(list.get(i)));
        }
        if (list.isEmpty()) {
            set(SLOT_EMPTY, service.item(Material.COBWEB, viewer, messageModule(), emptyKey()));
        }
        int shown = page + 1;
        int total = pages.pages();
        if (pages.hasPrevious(page)) {
            set(SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous",
                    Msg.p("page", shown - 1), Msg.p("pages", total)), click -> turn(-1));
        }
        if (pages.hasNext(page)) {
            set(SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next",
                    Msg.p("page", shown + 1), Msg.p("pages", total)), click -> turn(1));
        }
    }

    /** Re-opens with the new page so the title shows it (plays the page sound). */
    private void turn(int delta) {
        page += delta;
        service.open(viewer, this);
    }

    public final int page() {
        return page;
    }

    /** Jumps to a page (clamped on the next build). */
    public final void page(int page) {
        this.page = Math.max(0, page);
    }
}
