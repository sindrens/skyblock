package net.skybornmc.skyblock.common.menu;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Opens menus and protects them. One listener for every menu:
 * <ul>
 *   <li>every click/drag/move touching a menu is cancelled, so nothing can leave or enter it (shift, number keys,
 *       offhand swap, double-click collect, drop, creative middle-click, hoppers);</li>
 *   <li>only plain left/right (and shift) clicks on a button run its action, at most one per {@code click-debounce-ms};
 *       both mouse buttons run the plain action unless the button marks a Java-only shortcut ({@link ClickRoute});</li>
 *   <li>all menu items carry a marker; a marked item found outside a menu is deleted (defence in depth);</li>
 *   <li>live updates for menus with a tick interval; chat prompts for text/number input (Bedrock-friendly);</li>
 *   <li>navigation (GUI_DESIGN §0.3): Back returns to the menu that opened this one ({@link Navigation}); re-opening the
 *       open menu updates the window in place (tabs, pages: no flicker).</li>
 * </ul>
 */
public final class MenuService implements Listener {

    public static final String MODULE = "menus";

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Sounds sounds;
    private final ConfigManager configs;
    private final MenuConfig config = new MenuConfig();
    /**
     * PDC key name on every GUI button. An item with this key in a player's own inventory is a leaked button and is
     * deleted on join and after a menu closes, so nothing else may use this key (FIXES1: the hotbar menu item did).
     */
    public static final String BUTTON_KEY = "menu_item";
    private final NamespacedKey menuItemKey;
    private final Debounce debounce;
    private final Map<UUID, Menu> open = new ConcurrentHashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    /** Players with a Bedrock text form open ({@link #input}); counts as a prompt for popups. */
    private final java.util.Set<UUID> forms = ConcurrentHashMap.newKeySet();
    private BedrockForms bedrockForms = BedrockForms.NONE;
    private static final LegacyComponentSerializer FORM_TEXT = LegacyComponentSerializer.legacySection();
    /** The menu a chat prompt or a command was started from: the Back target of a menu that opens a moment later. */
    private final Map<UUID, Origin> origins = new ConcurrentHashMap<>();
    /** Menu classes already warned about a button on a standard slot (logged once per class). */
    private final java.util.Set<String> slotWarnings = ConcurrentHashMap.newKeySet();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final MenuHeads heads;
    private final Offers offers;
    private BukkitTask ticker;

    private record Prompt(Consumer<String> onInput, Runnable onCancel, int maxLength, BukkitTask timeout) {
    }

    private record Origin(Menu menu, long until) {
    }

    public MenuService(JavaPlugin plugin, ConfigManager configs, Messages messages, Sounds sounds) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.sounds = sounds;
        this.menuItemKey = new NamespacedKey(plugin, BUTTON_KEY);
        this.debounce = new Debounce(config.clickDebounceMs);
        this.heads = new MenuHeads(plugin.getLogger());
        reload();
        this.offers = new Offers(this);
    }

    /** GUI-5: chat offers that Bedrock players also get as a popup (TPA, invites, links, staff alerts). */
    public Offers offers() {
        return offers;
    }

    private net.skybornmc.skyblock.common.util.SlowOperationTimer timer;

    /** Task names per menu class for the slow-operation log ({@code menu-tick:UpgradeMenu}). */
    private static final ClassValue<String> TICK_NAMES = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return "menu-tick:" + type.getSimpleName();
        }
    };

    /**
     * PERF6: times each open menu's live update by its class name, so a menu that rebuilds itself expensively on every
     * refresh shows up in the slow-operation log (it used to run untimed). Set before {@link #enable()}.
     */
    public void timer(net.skybornmc.skyblock.common.util.SlowOperationTimer timer) {
        this.timer = timer;
    }

    /** Registers the listener and the live-update task. */
    public void enable() {
        bedrockForms = BedrockForms.detect(plugin.getLogger());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(offers, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    /** Closes every open menu and cancels prompts. Call on plugin disable (before modules save). */
    public void disable() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (UUID id : List.copyOf(open.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
        open.clear();
        origins.clear();
        prompts.values().forEach(p -> p.timeout().cancel());
        prompts.clear();
        offers.clear();
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(offers);
    }

    /** Reloads {@code menus.yml} and {@code messages/menus.yml}. */
    public void reload() {
        configs.load(config);
        messages.load(MODULE);
        debounce.interval(config.clickDebounceMs);
        framePanes = null;
        heads.load("back", config.headBack);
        heads.load("previous", config.headPrevious);
        heads.load("next", config.headNext);
        if (offers != null) {
            offers.reload();
        }
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public MenuConfig config() {
        return config;
    }

    // ------------------------------------------------------------------ opening

    /** Opens {@code menu} for {@code player} (main thread). Replaces any open menu without a close sound. */
    public void open(Player player, Menu menu) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> open(player, menu));
            return;
        }
        cancelPrompt(player, false);
        UUID id = player.getUniqueId();
        Menu current = open.get(id);
        if (current == menu && redrawInPlace(player, menu)) {
            return;
        }
        Origin origin = origins.remove(id);
        Menu from = origin != null && origin.until() >= System.currentTimeMillis() ? origin.menu() : null;
        Menu parent = Navigation.resolve(menu, menu.parent(), current, from, menu.isMainMenu(), Menu::parent, Navigation::sameClass);
        if (parent != menu.parent()) {
            menu.parent(parent);
        }
        Navigation.trim(menu, config.navigationMaxDepth, Menu::parent, Menu::parent);
        Menu previous = open.remove(id);
        if (previous != null && previous != menu) {
            previous.detach(); // re-opening the same menu (e.g. after a prompt) is not a close
        }
        int rows = Math.max(1, Math.min(6, menu.rows()));
        // The holder must exist before the inventory; the title needs the viewer for Bedrock colors.
        menu.viewer = player;
        menu.service = this;
        Component title = menu.title();
        Inventory inventory = Bukkit.createInventory(menu, rows * 9, title);
        menu.attach(this, player, inventory);
        menu.shownTitle = title;
        try {
            menu.build();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Menu " + menu.getClass().getSimpleName() + " failed to build", e);
            menu.detach();
            messages.send(player, MODULE, "error");
            sounds.playUi(player, "error");
            return;
        }
        open.put(player.getUniqueId(), menu);
        player.openInventory(inventory);
        sounds.playUi(player, previous == null ? "menu-open" : "page");
        if (previous != menu) {
            try {
                Bukkit.getPluginManager().callEvent(new MenuOpenedEvent(player, menu.menuId(),
                        menu.parent() == null ? null : menu.parent().menuId()));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "A MenuOpenedEvent listener failed for " + menu.menuId(), e);
            }
        }
    }

    /**
     * Re-opening the open menu: rebuild it in the same window and update the title in place when it changed (tabs,
     * pages, filters), so the window does not close and reopen (no flicker, no cursor jump). Returns false when a full
     * reopen is needed (window not open any more, or the title changed and in-place titles are off or failed).
     */
    private boolean redrawInPlace(Player player, Menu menu) {
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (!menu.isOpen() || top.getHolder(false) != menu || !top.equals(menu.getInventory())
                || top.getSize() != Math.max(1, Math.min(6, menu.rows())) * 9) {
            return false;
        }
        Component title = menu.title();
        boolean retitle = !title.equals(menu.shownTitle);
        if (retitle && !config.titleUpdateInPlace) {
            return false;
        }
        try {
            menu.refresh();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Menu " + menu.getClass().getSimpleName() + " failed to build", e);
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            messages.send(player, MODULE, "error");
            sounds.playUi(player, "error");
            return true;
        }
        if (retitle) {
            try {
                setTitle(view, title);
                menu.shownTitle = title;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "In-place title update failed; reopening " + menu.getClass().getSimpleName(), e);
                return false;
            }
        }
        sounds.playUi(player, "page");
        return true;
    }

    /**
     * Paper has no Component setter for an open window's title; the legacy one (deprecated since 1.21.1, not for removal)
     * re-sends the window title without closing it. Hex colors survive the legacy format. Failures fall back to a reopen.
     */
    @SuppressWarnings("deprecation")
    private static void setTitle(InventoryView view, Component title) {
        view.setTitle(LEGACY.serialize(title));
    }

    /** The menu {@code player} has open, or {@code null}. */
    public Menu openMenu(Player player) {
        return open.get(player.getUniqueId());
    }

    /**
     * Runs {@code action} (usually a command, e.g. from /menu) while {@code from} stays open, so a menu the action opens
     * gets {@code from} as its Back target. A menu that opens a moment later (after loading data) still gets it for
     * {@code navigation.origin-seconds}. If nothing opened, {@code from} is closed so the player sees the chat output.
     */
    public void navigate(Player player, Menu from, Runnable action) {
        UUID id = player.getUniqueId();
        origins.put(id, new Origin(from, System.currentTimeMillis() + config.navigationOriginSeconds * 1000L));
        try {
            action.run();
        } finally {
            if (open.get(id) == from) {
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    /** Closes the open menu and forgets where it came from (the Close button). */
    public void close(Player player) {
        origins.remove(player.getUniqueId());
        player.closeInventory();
    }

    /** Opens the main menu (/menu) as a new navigation stack: the Main menu button. */
    public void openMainMenu(Player player) {
        origins.remove(player.getUniqueId());
        String command = config.mainMenuCommand == null ? "" : config.mainMenuCommand.strip();
        Menu before = open.get(player.getUniqueId());
        player.performCommand(command.startsWith("/") ? command.substring(1) : command);
        if (before != null && open.get(player.getUniqueId()) == before) {
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    // ------------------------------------------------------------------ items and text

    /** Text of {@code key} in {@code module} for {@code viewer}, never italic. */
    public Component text(Player viewer, String module, String key, TagResolver... placeholders) {
        return messages.component(viewer, module, key, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    public Component text(Player viewer, String key, TagResolver... placeholders) {
        return text(viewer, MODULE, key, placeholders);
    }

    /** Lore lines of {@code key} (a string or a list in the messages file); empty if the key does not exist. */
    public List<Component> lines(Player viewer, String module, String key, TagResolver... placeholders) {
        String raw = messages.raw(module, key);
        List<Component> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("<newline>", -1)) {
            out.add(messages.parse(viewer, line, placeholders).decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    public List<Component> lines(Player viewer, String key, TagResolver... placeholders) {
        return lines(viewer, MODULE, key, placeholders);
    }

    /** An item named {@code <key>.name} with lore {@code <key>.lore} from {@code module}. */
    public ItemStack item(Material material, Player viewer, String module, String key, TagResolver... placeholders) {
        return item(material, text(viewer, module, key + ".name", placeholders), lines(viewer, module, key + ".lore", placeholders));
    }

    public ItemStack item(Material material, Player viewer, String key, TagResolver... placeholders) {
        return item(material, viewer, MODULE, key, placeholders);
    }

    /** An item with a name and lore, no italics, vanilla tooltips hidden. */
    public ItemStack item(Material material, Component name, List<Component> lore) {
        return item(new ItemStack(material), name, lore);
    }

    /** Names {@code base} (a copy) with a name and lore, no italics, vanilla tooltips hidden. */
    public ItemStack item(ItemStack base, Component name, List<Component> lore) {
        ItemStack item = base.clone();
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });
        return item;
    }

    /**
     * A navigation button: textured head ({@code heads.<headKey>} in menus.yml) with {@code fallback} until it is loaded,
     * named {@code <key>.name} / {@code <key>.lore}.
     */
    public ItemStack navItem(String headKey, Material fallback, Player viewer, String key, TagResolver... placeholders) {
        return item(heads.item(headKey, fallback), text(viewer, key + ".name", placeholders), lines(viewer, key + ".lore", placeholders));
    }

    /**
     * The standard chest title: {@code ▸ Island » Upgrades}. Crumbs are shown in title gray (readable on the light chest
     * background); if the title is too long for Bedrock, the leading crumbs are dropped first.
     */
    public Component title(Player viewer, Component... crumbs) {
        return title(viewer, List.of(crumbs), null);
    }

    /** Title from message keys, e.g. {@code titleKeys(p, "island", "title.island", "title.upgrades")}. */
    public Component titleKeys(Player viewer, String module, String... keys) {
        List<Component> crumbs = new ArrayList<>();
        for (String key : keys) {
            crumbs.add(text(viewer, module, key));
        }
        return title(viewer, crumbs, null);
    }

    /** Title with an optional suffix (e.g. the page indicator {@code (2/5)}). */
    public Component title(Player viewer, List<Component> crumbs, Component suffix) {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        Component separator = text(viewer, "title.separator");
        Component end = suffix == null ? Component.empty() : suffix;
        int fixed = plain.serialize(text(viewer, "title.format", Msg.c("crumbs", Component.empty()), Msg.c("suffix", end))).length();
        List<String> plainCrumbs = crumbs.stream().map(plain::serialize).toList();
        if (MenuTitles.blank(plainCrumbs)) {
            // A missing or empty title key: never send an empty chest title (Bedrock then shows "Large Chest", UX3).
            crumbs = List.of(text(viewer, "title.fallback"));
            plainCrumbs = crumbs.stream().map(plain::serialize).toList();
        }
        int max = config.titleMaxLength;
        int first = MenuTitles.first(plainCrumbs, config.titleMaxParts, fixed, plain.serialize(separator).length(), max);
        Component joined = Component.empty();
        for (int i = first; i < crumbs.size(); i++) {
            Component crumb = crumbs.get(i);
            if (i == crumbs.size() - 1 && MenuTitles.length(plainCrumbs, first, fixed, plain.serialize(separator).length()) > max) {
                int room = max - (MenuTitles.length(plainCrumbs, first, fixed, plain.serialize(separator).length()) - plainCrumbs.get(i).length());
                crumb = Component.text(MenuTitles.shorten(plainCrumbs.get(i), Math.max(3, room)));
            }
            joined = i == first ? crumb : joined.append(separator).append(crumb);
        }
        return text(viewer, "title.format", Msg.c("crumbs", joined), Msg.c("suffix", end));
    }

    /** A filler pane with no tooltip at all. */
    public ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }

    /**
     * Marks an item as a menu item; marked items are deleted if they ever end up outside a menu. Returns the marked copy of {@code item} ({@code item} itself when it is marked already). Public for menus that cache
     * their built items (PERF6): a cached, marked item is put into the inventory without another copy.
     */
    public ItemStack mark(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        if (item.getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE)) {
            return item; // already a menu item (cached button): no copy needed, the inventory copies it anyway
        }
        ItemStack copy = item.clone();
        copy.editMeta(meta -> meta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1));
        return copy;
    }

    boolean isMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ states (GUI_DESIGN §3: one look each, everywhere)

    /** "⌛ Loading..." (clock), display only. */
    public MenuButton loadingItem(Player viewer) {
        return MenuButton.display(item(Material.CLOCK, viewer, "loading"));
    }

    /** "✘ Couldn't load this" (red glass). With {@code retry} a click tries again; {@code null} = display only. */
    public MenuButton errorItem(Player viewer, Runnable retry) {
        if (retry == null) {
            return MenuButton.display(item(Material.RED_STAINED_GLASS, viewer, "error-item"));
        }
        return MenuButton.of(item(Material.RED_STAINED_GLASS, viewer, "error-retry"), click -> retry.run());
    }

    /** "Nothing here yet" (cobweb) with the framework texts. Put it in {@link MenuLayout#empty}. */
    public MenuButton emptyItem(Player viewer) {
        return emptyItem(viewer, MODULE, "empty");
    }

    /** "Nothing here yet" (cobweb) with a menu's own texts: {@code <key>.name} (the key fact) and {@code <key>.lore} (one hint). */
    public MenuButton emptyItem(Player viewer, String module, String key) {
        return MenuButton.display(item(Material.COBWEB, viewer, module, key));
    }

    /**
     * The locked look: gray, {@code ✘ Name}, then how to unlock (e.g. {@code Mining 15 ▸ 9/15}) in the lore. A click
     * shows the unlock hint in the action bar with the error sound (Bedrock players read it there too).
     */
    public MenuButton locked(Player viewer, Component name, Component howToUnlock) {
        List<Component> lore = new ArrayList<>();
        lore.add(howToUnlock);
        lore.addAll(lines(viewer, "state.locked.lore"));
        ItemStack item = item(Material.GRAY_DYE, text(viewer, "state.locked.name", Msg.c("name", name)), lore);
        return MenuButton.of(item, click -> {
            messages.actionBar(click.player(), MODULE, "state.locked.actionbar", Msg.c("hint", howToUnlock));
            sounds.playUi(click.player(), "error");
        });
    }

    /**
     * A tab (row 0, GUI_DESIGN §1.5). The selected tab glints and says "✔ You are here" (no click); any other tab runs
     * {@code onSelect} and then redraws the menu in place (same window, the cursor stays).
     */
    public MenuButton tab(Player viewer, ItemStack icon, Component name, List<Component> description, boolean selected, Runnable onSelect) {
        List<Component> lore = new ArrayList<>(description);
        lore.addAll(lines(viewer, selected ? "tab.selected" : "tab.action"));
        ItemStack item = item(icon, name, lore);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            onSelect.run();
            click.menu().redraw();
        });
    }

    /** Filler for empty slots inside small menus (confirm, number picker). */
    ItemStack innerFiller() {
        return filler(material(config.frameInner, Material.GRAY_STAINED_GLASS_PANE));
    }

    /** Material from a config id, or {@code fallback} (logged) if unknown. */
    Material material(String id, Material fallback) {
        Material m = Material.matchMaterial(id == null ? "" : id.trim());
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning("menus.yml: '" + id + "' is not an item id, using " + fallback.name().toLowerCase(Locale.ROOT));
            return fallback;
        }
        return m;
    }

    /** The marked frame panes (border, accent, glow, inner), built once per reload instead of on every redraw. */
    private volatile ItemStack[] framePanes;

    private ItemStack[] framePanes() {
        ItemStack[] panes = framePanes;
        if (panes == null) {
            panes = new ItemStack[]{
                    mark(filler(material(config.frameBorder, Material.BLACK_STAINED_GLASS_PANE))),
                    mark(filler(material(config.frameAccent, Material.RED_STAINED_GLASS_PANE))),
                    mark(filler(material(config.frameAccentGlow, Material.ORANGE_STAINED_GLASS_PANE))),
                    mark(innerFiller())};
            framePanes = panes;
        }
        return panes;
    }

    /**
     * See {@link Menu#frame()}. PERF2 (P-22, "stacking-menu-refresh"): the panes are cached per reload and the fixed
     * buttons per menu opening, so a live refresh no longer clones, tags and re-parses ~50 items every time.
     */
    void applyFrame(Menu menu) {
        int rows = menu.rows();
        ItemStack[] panes = framePanes();
        for (int slot = 0; slot < rows * 9; slot++) {
            switch (frameRole(slot, rows)) {
                case ACCENT -> menu.setMarked(slot, panes[1], null);
                case GLOW -> menu.setMarked(slot, panes[2], null);
                case BORDER -> menu.setMarked(slot, panes[0], null);
                case INNER -> {
                    if (rows <= 3) {
                        menu.setMarked(slot, panes[3], null);
                    }
                }
            }
        }
        Player viewer = menu.viewer();
        ItemStack[] fixed = menu.frameButtons();
        int back = MenuLayout.back(rows);
        if (back != MenuLayout.NONE && menu.parent() != null) {
            if (fixed[0] == null) {
                fixed[0] = mark(navItem("back", Material.ARROW, viewer, "frame.back"));
            }
            menu.setMarked(back, fixed[0], click -> click.menu().back());
        }
        int info = MenuLayout.info(rows);
        if (info != MenuLayout.NONE && menu.infoKey() != null) {
            if (fixed[1] == null) {
                List<Component> lore = new ArrayList<>(lines(viewer, menu.messageModule(), menu.infoKey()));
                lore.addAll(lines(viewer, "frame.info.footer"));
                fixed[1] = mark(item(Material.BOOK, text(viewer, "frame.info.name"), lore));
            }
            menu.setMarked(info, fixed[1], click -> runHelp(click.player(), click.menu(), menu.helpTopic()));
        }
        int close = MenuLayout.close(rows);
        if (close != MenuLayout.NONE) {
            if (fixed[2] == null) {
                fixed[2] = mark(item(Material.BARRIER, viewer, "frame.close"));
            }
            menu.setMarked(close, fixed[2], click -> close(click.player()));
        }
        int main = MenuLayout.mainMenu(rows);
        if (main != MenuLayout.NONE) {
            if (menu.isMainMenu()) {
                if (fixed[3] == null) {
                    fixed[3] = mark(item(Material.KNOWLEDGE_BOOK, viewer, "frame.help"));
                }
                menu.setMarked(main, fixed[3], click -> runHelp(click.player(), click.menu(), null));
            } else {
                if (fixed[3] == null) {
                    fixed[3] = mark(item(Material.NETHER_STAR, viewer, "frame.main-menu"));
                }
                menu.setMarked(main, fixed[3], click -> openMainMenu(click.player()));
            }
        }
    }

    /**
     * Logs once per menu class when a menu puts its own button on a standard slot (e.g. a filter on 52, where Previous
     * page goes): the standard button wins on pages where it shows. Called by {@link PagedMenu}.
     */
    void warnStandardSlot(Menu menu, int slot) {
        if (slotWarnings.add(menu.getClass().getName() + "#" + slot)) {
            plugin.getLogger().warning("Menu " + menu.getClass().getSimpleName() + " uses slot " + slot
                    + ", a standard bottom-bar slot (GUI_DESIGN §1.1); move its button to 46, 47 or 51.");
        }
    }

    /** What a slot of the standard frame shows (docs/UI_STYLE.md §5.1). */
    enum FrameRole { ACCENT, GLOW, BORDER, INNER }

    /** Corners = red accent, the slot next to each corner on the top/bottom row = ember glow, rest of the edge = border. */
    static FrameRole frameRole(int slot, int rows) {
        int row = slot / 9;
        int col = slot % 9;
        boolean edgeRow = row == 0 || row == rows - 1;
        if (edgeRow && (col == 0 || col == 8)) {
            return FrameRole.ACCENT;
        }
        if (edgeRow && (col == 1 || col == 7)) {
            return FrameRole.GLOW;
        }
        if (edgeRow || col == 0 || col == 8) {
            return FrameRole.BORDER;
        }
        return FrameRole.INNER;
    }

    /** Opens a help topic (or the help home) with {@code from} as its Back target. */
    private void runHelp(Player player, Menu from, String topic) {
        navigate(player, from, () -> player.performCommand(topic == null ? "help" : "help " + topic));
    }

    /**
     * A toggle button: shows the on/off state, flips on click, then refreshes the menu.
     * Name from {@code <key>.name}, description from {@code <key>.lore} (in {@code module}).
     */
    public MenuButton toggle(Player viewer, String module, String key, boolean state, Consumer<Boolean> change) {
        List<Component> lore = new ArrayList<>(lines(viewer, module, key + ".lore"));
        lore.addAll(lines(viewer, state ? "toggle.on" : "toggle.off"));
        lore.addAll(lines(viewer, "toggle.action"));
        // GUI-4c: the state is in the name too ("Chat colors ✔ On"), not only in the dye colour and the lore.
        Component name = text(viewer, state ? "toggle.name-on" : "toggle.name-off", Msg.c("name", text(viewer, module, key + ".name")));
        ItemStack item = item(state ? Material.LIME_DYE : Material.RED_DYE, name, lore);
        return MenuButton.of(item, click -> {
            change.accept(!state);
            click.menu().refresh();
        });
    }

    // ------------------------------------------------------------------ chat prompts

    /**
     * Closes the menu and asks the player to type a value in chat ({@code <key>} in {@code module}; placeholders
     * {@code <cancel>} and {@code <seconds>} are added). {@code onInput} runs on the main thread with the trimmed text;
     * {@code onCancel} runs if the player types the cancel word, times out or quits (not on quit).
     */
    public void prompt(Player player, String module, String key, int maxLength, Consumer<String> onInput, Runnable onCancel,
                       TagResolver... placeholders) {
        cancelPrompt(player, false);
        Menu current = open.get(player.getUniqueId());
        int seconds = config.promptTimeoutSeconds;
        if (current != null) {
            // A menu the answer opens gets this one as its Back target (the prompt closes the window meanwhile).
            origins.put(player.getUniqueId(), new Origin(current, System.currentTimeMillis() + (seconds + 5) * 1000L));
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Prompt p = prompts.remove(player.getUniqueId());
            if (p != null) {
                messages.send(player, MODULE, "prompt.timeout");
                sounds.playUi(player, "error");
                p.onCancel().run();
            }
        }, seconds * 20L);
        prompts.put(player.getUniqueId(), new Prompt(onInput, onCancel, Math.max(1, maxLength), timeout));
        TagResolver[] all = new TagResolver[placeholders.length + 2];
        System.arraycopy(placeholders, 0, all, 0, placeholders.length);
        all[placeholders.length] = Msg.p("cancel", config.promptCancelWord);
        all[placeholders.length + 1] = Msg.p("seconds", seconds);
        messages.send(player, module, key, all);
        sounds.playUi(player, "notify");
    }

    public boolean hasPrompt(Player player) {
        return prompts.containsKey(player.getUniqueId()) || forms.contains(player.getUniqueId());
    }

    /**
     * Asks for a line of text (GUI-4b, GUI_DESIGN §5): Bedrock players get a form popup with a text field and the
     * on-screen keyboard (Floodgate; {@code menus.yml → bedrock-forms.enabled}), everyone else the chat prompt
     * {@link #prompt}. Form texts: {@code <key>-form.title} and {@code <key>-form.label} in {@code module}; chat text:
     * {@code <key>}. {@code onInput} runs on the main thread with the cleaned text ({@link TypedText}: no colour codes,
     * trimmed, 1..{@code maxLength}); an empty answer or closing the form runs {@code onCancel}. Both texts get the
     * same {@code placeholders} (pass {@code <max>} yourself when the text shows it).
     *
     * @param current text already in the form field ({@code null} = empty); the chat prompt shows it via placeholders
     */
    public void input(Player player, String module, String key, int maxLength, String current, Consumer<String> onInput,
                      Runnable onCancel, TagResolver... placeholders) {
        input(player, module, key, maxLength, current, false, onInput, onCancel, placeholders);
    }

    /**
     * Same as {@link #input(Player, String, String, int, String, Consumer, Runnable, TagResolver...)}; {@code keepCodes}
     * keeps {@code &} colour codes in the answer (staff texts: display formats, tag displays, config values; GUI-4c).
     */
    public void input(Player player, String module, String key, int maxLength, String current, boolean keepCodes,
                      Consumer<String> onInput, Runnable onCancel, TagResolver... placeholders) {
        if (TypedText.useForm(config.bedrockForms, messages.isBedrock(player), bedrockForms != BedrockForms.NONE)
                && sendForm(player, module, key, maxLength, keepCodes, current, onInput, onCancel, placeholders)) {
            return;
        }
        prompt(player, module, key, maxLength, typed -> {
            TypedText.Result result = TypedText.clean(typed, maxLength, keepCodes);
            if (result.check() == TypedText.Check.OK) {
                onInput.accept(result.text());
            } else {
                messages.send(player, MODULE, "prompt.cancelled");
                onCancel.run();
            }
        }, onCancel, placeholders);
    }

    /**
     * True when {@code player} gets Bedrock popups ({@link #input} forms, {@link #showText}): a Bedrock player with
     * Floodgate's form API and {@code menus.yml → bedrock-forms.enabled}.
     */
    public boolean usesForms(Player player) {
        return TypedText.useForm(config.bedrockForms, messages.isBedrock(player), bedrockForms != BedrockForms.NONE);
    }

    /**
     * A read-only text popup for Bedrock players (GUI-4c, GUI_DESIGN §0.6 "no information only in hover text"): the
     * title, the text lines and one button per label. Closes the open menu first (Bedrock can't show a popup over a
     * chest). {@code onButton} (index of the button) or {@code onClosed} then runs on the main thread, e.g. to reopen
     * the menu. Returns false (nothing happens) for Java players or when the popup could not be sent.
     */
    public boolean showText(Player player, Component title, List<Component> lines, List<Component> buttons,
                            java.util.function.IntConsumer onButton, Runnable onClosed) {
        if (!usesForms(player)) {
            return false;
        }
        UUID id = player.getUniqueId();
        StringBuilder content = new StringBuilder();
        for (Component line : lines) {
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(FORM_TEXT.serialize(line));
        }
        List<String> labels = new ArrayList<>();
        for (Component button : buttons) {
            labels.add(FORM_TEXT.serialize(button));
        }
        forms.add(id);
        boolean sent = bedrockForms.sendText(id, FORM_TEXT.serialize(title), content.toString(), labels,
                index -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (forms.remove(id) && player.isOnline()) {
                        onButton.accept(index);
                    }
                }),
                () -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (forms.remove(id) && player.isOnline()) {
                        onClosed.run();
                    }
                }));
        if (!sent) {
            forms.remove(id);
            return false;
        }
        if (open.containsKey(id)) {
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
        sounds.playUi(player, "page");
        return true;
    }

    private boolean sendForm(Player player, String module, String key, int maxLength, boolean keepCodes, String current,
                             Consumer<String> onInput, Runnable onCancel, TagResolver... placeholders) {
        UUID id = player.getUniqueId();
        String title = FORM_TEXT.serialize(text(player, module, key + "-form.title", placeholders));
        String label = FORM_TEXT.serialize(text(player, module, key + "-form.label", placeholders));
        Menu menu = open.get(id);
        if (menu != null) {
            // Like the chat prompt: the menu the answer reopens keeps this one as its Back target.
            origins.put(id, new Origin(menu, System.currentTimeMillis() + (config.promptTimeoutSeconds + 5) * 1000L));
        }
        forms.add(id);
        boolean sent = bedrockForms.send(id, title, label, current == null ? "" : current,
                typed -> Bukkit.getScheduler().runTask(plugin, () -> formAnswer(player, module, key, maxLength, keepCodes, typed, onInput, onCancel,
                        placeholders)),
                () -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (forms.remove(id) && player.isOnline()) {
                        onCancel.run();
                    }
                }));
        if (!sent) {
            forms.remove(id);
            return false;
        }
        if (menu != null) {
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
        sounds.playUi(player, "notify");
        return true;
    }

    private void formAnswer(Player player, String module, String key, int maxLength, boolean keepCodes, String typed,
                            Consumer<String> onInput, Runnable onCancel, TagResolver... placeholders) {
        if (!forms.remove(player.getUniqueId()) || !player.isOnline()) {
            return;
        }
        TypedText.Result result = TypedText.clean(typed, maxLength, keepCodes);
        switch (result.check()) {
            case EMPTY -> {
                messages.send(player, MODULE, "prompt.cancelled");
                onCancel.run();
            }
            case TOO_LONG -> {
                messages.send(player, MODULE, "prompt.too-long", Msg.p("max", maxLength));
                sounds.playUi(player, "error");
                // Ask again with the text kept, so the player only has to shorten it.
                if (!sendForm(player, module, key, maxLength, keepCodes, result.text(), onInput, onCancel, placeholders)) {
                    onCancel.run();
                }
            }
            case OK -> {
                try {
                    onInput.accept(result.text());
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Form answer handler failed", e);
                    messages.send(player, MODULE, "error");
                }
            }
        }
    }

    private void cancelPrompt(Player player, boolean runCancel) {
        Prompt p = prompts.remove(player.getUniqueId());
        if (p != null) {
            p.timeout().cancel();
            if (runCancel) {
                p.onCancel().run();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!prompts.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Prompt p = prompts.get(player.getUniqueId());
            if (p == null) {
                return;
            }
            if (text.length() > p.maxLength()) {
                messages.send(player, MODULE, "prompt.too-long", Msg.p("max", p.maxLength()));
                sounds.playUi(player, "error");
                return; // keep waiting
            }
            prompts.remove(player.getUniqueId());
            p.timeout().cancel();
            if (text.isEmpty() || text.equalsIgnoreCase(config.promptCancelWord)) {
                messages.send(player, MODULE, "prompt.cancelled");
                p.onCancel().run();
                return;
            }
            try {
                p.onInput().accept(text);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Chat prompt handler failed", e);
                messages.send(player, MODULE, "error");
            }
        });
    }

    // ------------------------------------------------------------------ protection

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType type = event.getClick();
        boolean plain = type == ClickType.LEFT || type == ClickType.RIGHT
                || type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
        if (!plain) {
            // Number keys, offhand swap, double-click, drop, middle/creative: blocked; resync the client (Bedrock).
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        int slot = event.getRawSlot();
        MenuButton button = menu.button(slot);
        if (button == null || !button.clickable() || open.get(player.getUniqueId()) != menu) {
            return;
        }
        if (!debounce.tryClick(player.getUniqueId(), System.currentTimeMillis())) {
            return;
        }
        ClickRoute route = ClickRoute.of(type, messages.isBedrock(player), button.onRight() != null, button.onShift() != null);
        Consumer<MenuClick> handler = button.handler(route);
        if (handler == null) {
            return;
        }
        sounds.playUi(player, "click");
        try {
            handler.accept(new MenuClick(player, menu, slot, type));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Menu " + menu.getClass().getSimpleName() + " click on slot " + slot + " failed", e);
            messages.send(player, MODULE, "error");
            sounds.playUi(player, "error");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    // QUICK1 (PERFORMANCE P-13): no InventoryMoveItemEvent listener. Menus are virtual inventories that no hopper can
    // reach, and with no listener registered Paper skips building that event for every hopper transfer.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Same menu instance re-opened: its old inventory closes after the new one is attached; ignore that.
        if (menu.getInventory() == event.getInventory() && open.remove(player.getUniqueId(), menu)) {
            menu.detach();
            if (event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
                origins.remove(player.getUniqueId()); // the player closed it: the navigation stack ends here
                sounds.playUi(player, "menu-close");
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> purgeMenuItems(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (config.closeOnTeleport && open.containsKey(event.getPlayer().getUniqueId())) {
            origins.remove(event.getPlayer().getUniqueId()); // a teleport ends the navigation stack (e.g. /menu → Home)
            event.getPlayer().closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            plugin.getLogger().warning(event.getPlayer().getName() + " dropped a menu item; removed.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        purgeMenuItems(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Menu menu = open.remove(player.getUniqueId());
        if (menu != null) {
            menu.detach();
        }
        cancelPrompt(player, false);
        forms.remove(player.getUniqueId());
        origins.remove(player.getUniqueId());
        debounce.forget(player.getUniqueId());
    }

    /** Deletes menu items from a player's own inventory (should never exist; logged if it happens). */
    private void purgeMenuItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int removed = 0;
        for (int i = 0; i < contents.length; i++) {
            if (isMenuItem(contents[i])) {
                player.getInventory().setItem(i, null);
                removed++;
            }
        }
        if (isMenuItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed + " menu item(s) from " + player.getName() + "'s inventory.");
        }
    }

    private void tickAll() {
        for (Menu menu : open.values()) {
            try {
                if (timer == null) {
                    menu.tickInternal();
                } else {
                    timer.time(TICK_NAMES.get(menu.getClass()), menu::tickInternal);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Menu " + menu.getClass().getSimpleName() + " tick failed", e);
            }
        }
    }

    /** Plays a named sound (sounds.yml) for menu code (a UI sound: muted for players who turned menu sounds off). */
    public void sound(Player player, String key) {
        sounds.playUi(player, key);
    }

    public Messages messages() {
        return messages;
    }
}
