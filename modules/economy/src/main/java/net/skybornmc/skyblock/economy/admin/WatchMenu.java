package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Base of the economy dashboard menus (M3.8): six rows, texts in {@code messages/economy.yml}, and one guarded async load at
 * a time (a result that arrives after the viewer turned the page or closed the menu is dropped). Plain chest menus with
 * normal items only, so they work the same on Bedrock (Geyser). {@link #build()} always draws the framework frame first
 * (bottom bar 45 Back, 48 ?, 49 Close, 50 Main menu, pages 52/53), so no dashboard screen can ship without it.
 */
abstract class WatchMenu extends Menu {

    final EconomyWatch watch;
    boolean failed;
    private int generation;
    private int loading = -1;

    WatchMenu(EconomyWatch watch) {
        this.watch = watch;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String helpTopic() {
        return "money";
    }

    @Override
    protected final void build() {
        frame();
        content();
    }

    /** The screen's own content, drawn over the frame. */
    protected abstract void content();

    /** {@code ▸ Economy » <crumb>} */
    Component titleWith(String crumbKey, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        Component base = service.text(viewer, EconomyModule.ID, "watch.menu.title");
        if (crumbKey == null) {
            return service.title(viewer, List.of(base), null);
        }
        return service.title(viewer, List.of(base, service.text(viewer, EconomyModule.ID, crumbKey, tags)), null);
    }

    /**
     * {@code ▸ Economy » <crumb> (page/pages)} (GUI-4c: every paged dashboard screen shows its page like PagedMenu).
     * {@code pages} ≤ 1 shows no page.
     */
    Component pagedTitle(Component crumb, int page, int pages) {
        Component base = service.text(viewer, EconomyModule.ID, "watch.menu.title");
        Component suffix = pages > 1 ? service.text(viewer, "title.page", net.skybornmc.skyblock.common.text.Msg.p("page", page + 1),
                net.skybornmc.skyblock.common.text.Msg.p("pages", pages)) : null;
        return service.title(viewer, crumb == null ? List.of(base) : List.of(base, crumb), suffix);
    }

    /** Pages of {@code size} entries on the 28 content slots (at least 1). Pure. */
    static int pages(long size) {
        int per = PagedMenu.CONTENT_SLOTS.length;
        return size <= 0 ? 1 : (int) Math.min(Integer.MAX_VALUE, (size + per - 1) / per);
    }

    /** The standard Previous (52) / Next (53) buttons for {@code page} of {@code pages}; {@code turn} gets -1 or +1. */
    void pager(int page, int pages, java.util.function.IntConsumer turn) {
        if (page > 0) {
            set(SLOT_PREVIOUS, service.navItem("previous", org.bukkit.Material.ARROW, viewer, "frame.previous",
                    net.skybornmc.skyblock.common.text.Msg.p("page", page), net.skybornmc.skyblock.common.text.Msg.p("pages", pages)),
                    click -> turn.accept(-1));
        }
        if (page + 1 < pages) {
            set(SLOT_NEXT, service.navItem("next", org.bukkit.Material.ARROW, viewer, "frame.next",
                    net.skybornmc.skyblock.common.text.Msg.p("page", page + 2), net.skybornmc.skyblock.common.text.Msg.p("pages", pages)),
                    click -> turn.accept(1));
        }
    }

    /** Drops what was loaded and loads again on the next build. */
    void invalidate() {
        generation++;
        failed = false;
    }

    /** Shows the loading (or error) item in the middle. */
    void showLoading() {
        set(PagedMenu.SLOT_EMPTY, failed ? service.errorItem(viewer, null)
                : service.loadingItem(viewer));
    }

    /**
     * Runs {@code future} once per generation; {@code apply} gets the value on the main thread, then the menu is rebuilt
     * (if still open and nothing newer was asked for).
     */
    <T> void load(CompletableFuture<T> future, Consumer<T> apply) {
        int requested = generation;
        if (loading == requested) {
            return;
        }
        loading = requested;
        future.whenComplete((value, error) -> Bukkit.getScheduler().runTask(watch.core.plugin(), () -> {
            if (loading == requested) {
                loading = -1;
            }
            if (requested != generation) {
                return;
            }
            if (error != null) {
                watch.core.plugin().getLogger().warning("Economy watch: menu load failed: " + error.getMessage());
                failed = true;
            } else {
                apply.accept(value);
            }
            if (isOpen()) {
                redraw(); // GUI-4c: in place, and the title's page count follows the loaded list
            }
        }));
    }

    void click() {
        service.sound(viewer, "click");
    }
}
