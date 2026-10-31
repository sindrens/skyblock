package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Base class of every chest menu. One instance per viewer: subclasses describe the content in {@link #build()} with
 * {@link #set}; the {@link MenuService} handles opening, click protection, debounce, sounds and live updates.
 *
 * <p>Layout (docs/design/GUI_DESIGN.md §1, {@link MenuLayout}): call {@link #frame()} first in {@code build()} for the
 * standard frame (black border, red corners with ember glow) and the standard buttons: on 6 rows 45 Back, 48 ? (what is
 * this), 49 Close, 50 Main menu, 52/53 pages; on 3 rows 18 Back and 22 Close. Free action slots on the bottom bar: 46,
 * 47 and 51. Build titles with {@link MenuService#title} so every menu reads {@code ▸ Island » Upgrades}.</p>
 *
 * <p>Back returns to the menu that opened this one ({@link Navigation}); re-opening this menu
 * ({@code service.open(viewer, this)}, {@link #redraw()}) updates the open window in place.</p>
 */
public abstract class Menu implements InventoryHolder {

    public static final int SLOT_BACK = 45;
    /** Previous page (52, so Back at 45 works on every page). */
    public static final int SLOT_PREVIOUS = 52;
    public static final int SLOT_INFO = 48;
    public static final int SLOT_CLOSE = 49;
    /** Main menu (on the main menu itself: Help & guides). */
    public static final int SLOT_MAIN_MENU = 50;
    public static final int SLOT_NEXT = 53;
    /** Back on 3-row menus. */
    public static final int SLOT_SMALL_BACK = 18;
    /** Close on 3-row menus. */
    public static final int SLOT_SMALL_CLOSE = 22;

    private final Map<Integer, MenuButton> buttons = new HashMap<>();
    protected MenuService service;
    protected Player viewer;
    private Inventory inventory;
    private Menu parent;
    private boolean open;
    private long ticks;
    private final ItemStack[] frameButtons = new ItemStack[4];
    /** The title the open window shows (to update it in place only when it changed). */
    Component shownTitle;

    /** Number of rows (1-6). */
    public abstract int rows();

    /** The chest title: use {@code service.title(viewer, crumbs...)} (length-safe for Bedrock). */
    public abstract Component title();

    /** Fills the menu with {@link #set}. Called on open and on every {@link #refresh()}. */
    protected abstract void build();

    /** The messages module of this menu's texts ({@code messages/<module>.yml}). */
    public String messageModule() {
        return "menus";
    }

    /** Message key (in {@link #messageModule()}) of the 2-4 info lines of the "?" item, or {@code null} for no info item. */
    public String infoKey() {
        return null;
    }

    /** The /help topic the "?" item opens. */
    public String helpTopic() {
        return "menus";
    }

    /**
     * Short id of this menu for {@link MenuOpenedEvent} (tutorial steps, later quests): the class name without the
     * {@code Menu} suffix in kebab-case ({@code IslandMenu} → {@code island}, {@code HubMenu} → {@code hub}). Override to
     * keep an id stable when a class is renamed.
     */
    public String menuId() {
        return idOf(getClass().getSimpleName());
    }

    /** {@code IslandStatsMenu} → {@code island-stats}; {@code Menu} alone → {@code menu}. Pure, unit-tested. */
    public static String idOf(String simpleName) {
        String name = simpleName == null ? "" : simpleName;
        if (name.endsWith("Menu") && name.length() > 4) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.isEmpty()) {
            return "menu";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('-');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    /**
     * True for the main menu (/menu): the bottom of the navigation stack (never gets a parent) and its slot 50 shows
     * Help & guides instead of Main menu.
     */
    public boolean isMainMenu() {
        return false;
    }

    /** Ticks between {@link #tick()} calls while open; 0 = no live updates. */
    public int tickInterval() {
        return 0;
    }

    /** Live update hook (main thread), e.g. refresh a timer slot with {@link #set}. */
    protected void tick() {
    }

    /** Called once when the menu closes (any reason). */
    protected void onClose() {
    }

    // ------------------------------------------------------------------ API for subclasses

    /** Puts a button in a slot (replaces what was there). */
    protected final void set(int slot, MenuButton button) {
        if (slot < 0 || slot >= rows() * 9) {
            throw new IllegalArgumentException("Slot " + slot + " is outside a " + rows() + "-row menu");
        }
        if (button == null) {
            buttons.remove(slot);
            inventory.setItem(slot, null);
            return;
        }
        ItemStack item = service.mark(service.render(viewer, button.item()));
        buttons.put(slot, button.withItem(item));
        inventory.setItem(slot, item);
    }

    /** Framework only: puts an item that is already marked as a menu item (cached frame panes and buttons). */
    final void setMarked(int slot, ItemStack marked, Consumer<MenuClick> onClick) {
        buttons.put(slot, new MenuButton(marked, onClick));
        inventory.setItem(slot, marked);
    }

    /** The fixed frame buttons built for this opening (back, info, close, help), reused by every refresh (PERF2). */
    final ItemStack[] frameButtons() {
        return frameButtons;
    }

    protected final void set(int slot, ItemStack item, Consumer<MenuClick> onClick) {
        set(slot, new MenuButton(item, onClick));
    }

    /** Decoration only. */
    protected final void set(int slot, ItemStack item) {
        set(slot, new MenuButton(item, null));
    }

    /**
     * Shows a "Loading…" item now and the real button when {@code future} completes (on the main thread, only if
     * this menu is still open). A failed future shows an error item.
     */
    protected final void setAsync(int slot, CompletableFuture<MenuButton> future) {
        set(slot, service.loadingItem(viewer));
        future.whenComplete((button, error) -> Bukkit.getScheduler().runTask(service.plugin(), () -> {
            if (!open) {
                return;
            }
            if (error != null) {
                service.plugin().getLogger().log(Level.WARNING, "Menu " + getClass().getSimpleName() + " failed to load slot " + slot, error);
                set(slot, service.errorItem(viewer, null));
            } else {
                set(slot, button);
            }
        }));
    }

    /** Standard frame: black border, red corners, ember glow next to them, plus the standard buttons ({@link MenuLayout}). */
    protected final void frame() {
        service.applyFrame(this);
    }

    /** The one "nothing here yet" item (cobweb, framework texts) in the middle of the content. */
    protected final void setEmpty() {
        set(MenuLayout.empty(rows()), service.emptyItem(viewer));
    }

    /** The "nothing here yet" item with this menu's own texts ({@code <key>.name} / {@code <key>.lore} in {@code module}). */
    protected final void setEmpty(String module, String key) {
        set(MenuLayout.empty(rows()), service.emptyItem(viewer, module, key));
    }

    /** The standard "Loading..." item. */
    protected final void setLoading(int slot) {
        set(slot, service.loadingItem(viewer));
    }

    /** The standard "Couldn't load this" item; a click runs {@code retry} ({@code null} = display only). */
    protected final void setError(int slot, Runnable retry) {
        set(slot, service.errorItem(viewer, retry));
    }

    /**
     * Rebuilds this menu and updates the window title in place when it changed (tabs, pages, filters). Same as
     * {@code service.open(viewer, this)}: no close/reopen, the cursor stays where it is.
     */
    public final void redraw() {
        if (open) {
            service.open(viewer, this);
        }
    }

    /** Clears and rebuilds the content in place (no flicker, no reopen). */
    public final void refresh() {
        if (!open) {
            return;
        }
        buttons.clear();
        inventory.clear();
        build();
    }

    /** Opens {@code menu} for the same viewer with this menu as its parent (Back returns here). */
    public final void openChild(Menu menu) {
        menu.parent(this);
        service.open(viewer, menu);
    }

    /** Back to the parent menu, or close if there is none. */
    public final void back() {
        if (parent != null) {
            service.open(viewer, parent);
        } else {
            viewer.closeInventory();
        }
    }

    public final Menu parent(Menu parent) {
        this.parent = parent;
        return this;
    }

    public final Menu parent() {
        return parent;
    }

    public final Player viewer() {
        return viewer;
    }

    public final boolean isOpen() {
        return open;
    }

    public final long ticksOpen() {
        return ticks;
    }

    // ------------------------------------------------------------------ framework internals

    final void attach(MenuService service, Player viewer, Inventory inventory) {
        this.service = service;
        this.viewer = viewer;
        this.inventory = inventory;
        this.buttons.clear();
        java.util.Arrays.fill(this.frameButtons, null);
        this.ticks = 0;
        this.open = true;
    }

    final void detach() {
        if (open) {
            open = false;
            onClose();
        }
    }

    final void tickInternal() {
        ticks++;
        int interval = tickInterval();
        if (interval > 0 && ticks % interval == 0) {
            tick();
        }
    }

    final MenuButton button(int slot) {
        return buttons.get(slot);
    }

    final boolean hasButton(int slot) {
        return buttons.containsKey(slot);
    }

    @Override
    public final @NotNull Inventory getInventory() {
        return inventory;
    }
}
