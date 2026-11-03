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
 * <p>Layout (I9 v2, {@link MenuLayout}): menus are 3, 4 or 6 rows; empty slots stay empty (no frame, no filler panes).
 * Call {@link #navBar()} first in {@code build()} for the minimal bottom row: Back (when there is somewhere to go back
 * to) and Close; paged menus add Previous/Next ({@link MenuService#pageItem}).</p>
 * <pre>
 *  Rows | Back | Actions | Close | Search (AH only) | Previous | Next | Danger corner | Summary
 *   6   |  45  |  46 47  |  49   |        51        |    52    |  53  |       8       |    4
 *   4   |  27  |  28 29  |  31   |        -         |    34    |  35  |       8       |    4
 *   3   |  18  |  19 20  |  22   |        -         |    -     |  -   |       8       |    4
 * </pre>
 * <p>Small menus centre their buttons with {@link Centered}; lists use the 7x4 grid. Build titles with
 * {@link MenuService#title} so every menu reads {@code ▸ Island » Upgrades}.</p>
 *
 * <p>Back returns to the menu that opened this one ({@link Navigation}); re-opening this menu
 * ({@code service.open(viewer, this)}, {@link #redraw()}) updates the open window in place.</p>
 */
public abstract class Menu implements InventoryHolder {

    public static final int SLOT_BACK = 45;
    /** Previous page (52, so Back at 45 works on every page). */
    public static final int SLOT_PREVIOUS = 52;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NEXT = 53;
    /** The optional summary tile of the top row (your head, balance, "what is this"). */
    public static final int SLOT_SUMMARY = 4;
    /** Free screen actions of the 6-row bottom row (shop: Sell 46, Undo 47). */
    public static final int SLOT_ACTION_A = 46;
    public static final int SLOT_ACTION_B = 47;
    /** The auction house's Search button (I9 section 11; nowhere else). */
    public static final int SLOT_SEARCH = 51;
    /** The red danger tile corner (I9 section 5). */
    public static final int SLOT_DANGER = 8;
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
    /** The Back and Close items built for this opening, reused by every refresh (PERF2). */
    private final ItemStack[] navItems = new ItemStack[2];
    /** The title the open window shows (to update it in place only when it changed). */
    Component shownTitle;
    /** The plain title of the last build ({@link #title()}), the source of a framed title (GUI-FONT). */
    Component plainTitle;
    /** True while the window shows a frame title (panel glyphs). */
    boolean framed;
    /** Top inventory slots that hold an item now. */
    private final java.util.BitSet filledMask = new java.util.BitSet();
    /** The filled slots the shown frame title was built for. */
    java.util.BitSet shownMask = new java.util.BitSet();
    /** A slot went empty or filled (or a title swap was throttled): the framework re-checks the frame title. */
    boolean tilesDirty;
    /** When the last frame title was sent (epoch ms; 0 = never), for {@code gui.font.title-resend-min-ms}. */
    long titleSentAt;

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

    /**
     * Ignored: there is no {@code ?} button any more (I9 section 4). Kept so the menus that still override it compile;
     * the overrides and their {@code *.info} texts go in the per-module pass.
     */
    @Deprecated
    public String infoKey() {
        return null;
    }

    /** Ignored (no {@code ?} button any more, I9 section 4); see {@link #infoKey()}. */
    @Deprecated
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

    /** True for the main menu (/menu): the bottom of the navigation stack (never gets a parent, so no Back). */
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

    // ------------------------------------------------------------------ frame layer (GUI-FONT 6.3)

    /**
     * Framed for Java viewers with the pack (panel, hidden empty slots, soft tiles). Override with {@code false} for
     * menus whose empty slots are real drop slots (the sell chest, deliver screens): they keep the plain chest.
     */
    public boolean panel() {
        return true;
    }

    /** The panel of this menu: empty when {@link #panel()} is false or the menu has 1, 2 or 5 rows. */
    public final java.util.Optional<net.skybornmc.skyblock.common.frame.PanelType> panelType() {
        if (!panel()) {
            return java.util.Optional.empty();
        }
        return net.skybornmc.skyblock.common.frame.PanelType.forRows(rows());
    }

    /**
     * Message key (in {@link #messageModule()}) of the plaque noun, e.g. {@code title.short} = {@code SHOP}; at most 16
     * letters, all caps. {@code null} = the panel shows the plain title text in its top band.
     */
    public String plaqueKey() {
        return null;
    }

    /** The messages module of {@link #plaqueKey()} (default {@link #messageModule()}). */
    public String plaqueModule() {
        return messageModule();
    }

    /** True only for the staff glyph test: framed for Java with the pack even when {@code gui.font.*} is off. */
    public boolean frameForced() {
        return false;
    }

    /** Top inventory slots that hold an item (a copy). */
    final java.util.BitSet filled() {
        return (java.util.BitSet) filledMask.clone();
    }

    /** Swaps the window's inventory for a new one with the same holder and contents (frame title update). */
    final void replaceInventory(Inventory next) {
        this.inventory = next;
    }

    private void track(int slot, ItemStack item) {
        boolean now = item != null && !item.getType().isAir();
        if (filledMask.get(slot) != now) {
            filledMask.set(slot, now);
            tilesDirty = true;
        }
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
            track(slot, null);
            return;
        }
        ItemStack item = service.mark(button.item());
        buttons.put(slot, button.withItem(item));
        inventory.setItem(slot, item);
        track(slot, item);
    }

    /** Framework only: puts an item that is already marked as a menu item (the cached Back and Close). */
    final void setMarked(int slot, ItemStack marked, Consumer<MenuClick> onClick) {
        buttons.put(slot, new MenuButton(marked, onClick));
        inventory.setItem(slot, marked);
        track(slot, marked);
    }

    /** The Back and Close items built for this opening, reused by every refresh (PERF2). */
    final ItemStack[] navItems() {
        return navItems;
    }

    protected final void set(int slot, ItemStack item, Consumer<MenuClick> onClick) {
        set(slot, new MenuButton(item, onClick));
    }

    /** Decoration only. */
    protected final void set(int slot, ItemStack item) {
        set(slot, new MenuButton(item, null));
    }

    /** True when nothing is on {@code slot} (no item and no button). Framework use (menu hints). */
    final boolean emptyAt(int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize() || buttons.containsKey(slot)) {
            return false;
        }
        ItemStack s = inventory.getItem(slot);
        return s == null || s.isEmpty();
    }

    /** Framework use: the tutorial hint (display only). */
    final void setHint(int slot, ItemStack item) {
        set(slot, item);
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

    /**
     * The minimal bottom row (I9 section 4): Back when there is somewhere to go back to (never on the main menu) and
     * Close, for this menu's row count. No panes, no {@code ?}, no Main menu button.
     */
    protected final void navBar() {
        service.applyNavBar(this);
    }

    /**
     * The wallet tile on {@link #SLOT_SUMMARY} (FORMAT 4, MENUS-V3): {@code currencyKey} {@code money}, {@code crystals},
     * {@code shards}, {@code credits} or {@code bank}; {@code amountText} the full amount; {@code noteKey} an optional
     * muted line from {@code messages/menus.yml} (null = none).
     */
    protected final void setWallet(String currencyKey, String amountText, String noteKey) {
        set(SLOT_SUMMARY, service.wallet(viewer, currencyKey, amountText, noteKey));
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
        if (!filledMask.isEmpty()) {
            filledMask.clear();
            tilesDirty = true;
        }
        build();
        if (service != null) {
            service.applyHint(this);
        }
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
        java.util.Arrays.fill(this.navItems, null);
        this.ticks = 0;
        this.open = true;
        this.filledMask.clear();
        this.shownMask = new java.util.BitSet();
        this.framed = false;
        this.tilesDirty = false;
        this.titleSentAt = 0;
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
