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
 * normal items only, so they work the same on Bedrock (Geyser).
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

    /** {@code ▸ Economy » <crumb>} */
    Component titleWith(String crumbKey, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        Component base = service.text(viewer, EconomyModule.ID, "watch.menu.title");
        if (crumbKey == null) {
            return service.title(viewer, List.of(base), null);
        }
        return service.title(viewer, List.of(base, service.text(viewer, EconomyModule.ID, crumbKey, tags)), null);
    }

    /** Drops what was loaded and loads again on the next build. */
    void invalidate() {
        generation++;
        failed = false;
    }

    /** Shows the loading (or error) item in the middle. */
    void showLoading() {
        set(PagedMenu.SLOT_EMPTY, failed ? service.item(Material.RED_STAINED_GLASS, viewer, "error-item")
                : service.item(Material.CLOCK, viewer, "loading"));
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
                refresh();
            }
        }));
    }

    void click() {
        service.sound(viewer, "click");
    }
}
