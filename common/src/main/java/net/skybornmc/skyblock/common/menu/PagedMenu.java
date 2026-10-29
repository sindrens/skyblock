package net.skybornmc.skyblock.common.menu;

import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;

/**
 * A 6-row menu listing entries in the 28 inner slots (rows 2-5 inside the frame), with previous (45) and next (53)
 * page buttons. On page 1 slot 45 is Back (if there is a parent); on later pages it is Previous.
 */
public abstract class PagedMenu<T> extends Menu {

    /** The 28 content slots inside the frame. */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private int page;

    /** All entries (called on every build; keep it cheap or cache). */
    protected abstract List<T> entries();

    /** The button for one entry. */
    protected abstract MenuButton entry(T value);

    /** Extra items (e.g. header buttons in the top row), placed after the frame and before the entries. */
    protected void decorate() {
    }

    /** Message key (in "menus") of the item shown when there are no entries. */
    protected String emptyKey() {
        return "empty";
    }

    @Override
    public final int rows() {
        return 6;
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
            set(22, service.item(Material.STRUCTURE_VOID, viewer, emptyKey()));
        }
        int shown = page + 1;
        int total = pages.pages();
        if (pages.hasPrevious(page)) {
            set(SLOT_PREVIOUS, service.item(Material.ARROW, viewer, "frame.previous", Msg.p("page", shown - 1), Msg.p("pages", total)),
                    click -> turn(-1));
        }
        if (pages.hasNext(page)) {
            set(SLOT_NEXT, service.item(Material.ARROW, viewer, "frame.next", Msg.p("page", shown + 1), Msg.p("pages", total)),
                    click -> turn(1));
        }
    }

    private void turn(int delta) {
        page += delta;
        service.sound(viewer, "page");
        refresh();
    }

    public final int page() {
        return page;
    }

    /** Jumps to a page (clamped on the next build). */
    public final void page(int page) {
        this.page = Math.max(0, page);
    }
}
