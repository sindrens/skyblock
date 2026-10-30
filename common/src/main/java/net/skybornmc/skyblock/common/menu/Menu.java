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
 * <p>Layout (docs/UI_STYLE.md §5): call {@link #frame()} first in {@code build()} for the standard frame (black border,
 * red corners with ember glow) and the fixed buttons (45 back, 48 info, 49 close, 50 help). Build titles with
 * {@link MenuService#title} so every menu reads {@code ▸ Island » Upgrades}.</p>
 */
public abstract class Menu implements InventoryHolder {

    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREVIOUS = 45;
    public static final int SLOT_INFO = 48;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_HELP = 50;
    public static final int SLOT_NEXT = 53;

    private final Map<Integer, MenuButton> buttons = new HashMap<>();
    protected MenuService service;
    protected Player viewer;
    private Inventory inventory;
    private Menu parent;
    private boolean open;
    private long ticks;

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
        ItemStack item = service.mark(button.item());
        buttons.put(slot, new MenuButton(item, button.onClick()));
        inventory.setItem(slot, item);
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
                set(slot, service.errorItem(viewer));
            } else {
                set(slot, button);
            }
        }));
    }

    /** Standard frame: black border, red corners, ember glow next to them; for 6-row menus also the fixed buttons. */
    protected final void frame() {
        service.applyFrame(this);
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
