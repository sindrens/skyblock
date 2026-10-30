package net.skybornmc.skyblock.common.menu;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
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
 *   <li>only plain left/right (and shift) clicks on a button run its action, at most one per {@code click-debounce-ms};</li>
 *   <li>all menu items carry a marker; a marked item found outside a menu is deleted (defence in depth);</li>
 *   <li>live updates for menus with a tick interval; chat prompts for text/number input (Bedrock-friendly).</li>
 * </ul>
 */
public final class MenuService implements Listener {

    public static final String MODULE = "menus";

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Sounds sounds;
    private final ConfigManager configs;
    private final MenuConfig config = new MenuConfig();
    private final NamespacedKey menuItemKey;
    private final Debounce debounce;
    private final Map<UUID, Menu> open = new ConcurrentHashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final MenuHeads heads;
    private BukkitTask ticker;

    private record Prompt(Consumer<String> onInput, Runnable onCancel, int maxLength, BukkitTask timeout) {
    }

    public MenuService(JavaPlugin plugin, ConfigManager configs, Messages messages, Sounds sounds) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.sounds = sounds;
        this.menuItemKey = new NamespacedKey(plugin, "menu_item");
        this.debounce = new Debounce(config.clickDebounceMs);
        this.heads = new MenuHeads(plugin.getLogger());
        reload();
    }

    /** Registers the listener and the live-update task. */
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        prompts.values().forEach(p -> p.timeout().cancel());
        prompts.clear();
        HandlerList.unregisterAll(this);
    }

    /** Reloads {@code menus.yml} and {@code messages/menus.yml}. */
    public void reload() {
        configs.load(config);
        messages.load(MODULE);
        debounce.interval(config.clickDebounceMs);
        heads.load("back", config.headBack);
        heads.load("previous", config.headPrevious);
        heads.load("next", config.headNext);
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
        Menu previous = open.remove(player.getUniqueId());
        if (previous != null && previous != menu) {
            previous.detach(); // re-opening the same menu (e.g. page turn with a new title) is not a close
        }
        int rows = Math.max(1, Math.min(6, menu.rows()));
        // The holder must exist before the inventory; the title needs the viewer for Bedrock colors.
        menu.viewer = player;
        menu.service = this;
        Inventory inventory = Bukkit.createInventory(menu, rows * 9, menu.title());
        menu.attach(this, player, inventory);
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
    }

    /** The menu {@code player} has open, or {@code null}. */
    public Menu openMenu(Player player) {
        return open.get(player.getUniqueId());
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
        int max = config.titleMaxLength;
        int first = MenuTitles.firstKept(plainCrumbs, fixed, plain.serialize(separator).length(), max);
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

    /** Marks an item as a menu item (copy); marked items are deleted if they ever end up outside a menu. */
    ItemStack mark(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
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

    MenuButton loadingItem(Player viewer) {
        return MenuButton.display(item(Material.CLOCK, viewer, "loading"));
    }

    MenuButton errorItem(Player viewer) {
        return MenuButton.display(item(Material.RED_STAINED_GLASS, viewer, "error-item"));
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

    /** See {@link Menu#frame()}. */
    void applyFrame(Menu menu) {
        int rows = menu.rows();
        ItemStack border = filler(material(config.frameBorder, Material.BLACK_STAINED_GLASS_PANE));
        ItemStack accent = filler(material(config.frameAccent, Material.RED_STAINED_GLASS_PANE));
        ItemStack glow = filler(material(config.frameAccentGlow, Material.ORANGE_STAINED_GLASS_PANE));
        ItemStack inner = innerFiller();
        for (int slot = 0; slot < rows * 9; slot++) {
            switch (frameRole(slot, rows)) {
                case ACCENT -> menu.set(slot, accent);
                case GLOW -> menu.set(slot, glow);
                case BORDER -> menu.set(slot, border);
                case INNER -> {
                    if (rows <= 3) {
                        menu.set(slot, inner);
                    }
                }
            }
        }
        if (rows != 6) {
            return;
        }
        Player viewer = menu.viewer();
        if (menu.parent() != null) {
            menu.set(Menu.SLOT_BACK, navItem("back", Material.ARROW, viewer, "frame.back"), click -> click.menu().back());
        }
        if (menu.infoKey() != null) {
            List<Component> lore = new ArrayList<>(lines(viewer, menu.messageModule(), menu.infoKey()));
            lore.addAll(lines(viewer, "frame.info.footer"));
            menu.set(Menu.SLOT_INFO, item(Material.BOOK, text(viewer, "frame.info.name"), lore),
                    click -> runHelp(click.player(), menu.helpTopic()));
        }
        menu.set(Menu.SLOT_CLOSE, item(Material.BARRIER, viewer, "frame.close"), click -> click.player().closeInventory());
        menu.set(Menu.SLOT_HELP, item(Material.KNOWLEDGE_BOOK, viewer, "frame.help"), click -> runHelp(click.player(), null));
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

    private void runHelp(Player player, String topic) {
        player.closeInventory();
        player.performCommand(topic == null ? "help" : "help " + topic);
    }

    /**
     * A toggle button: shows the on/off state, flips on click, then refreshes the menu.
     * Name from {@code <key>.name}, description from {@code <key>.lore} (in {@code module}).
     */
    public MenuButton toggle(Player viewer, String module, String key, boolean state, Consumer<Boolean> change) {
        List<Component> lore = new ArrayList<>(lines(viewer, module, key + ".lore"));
        lore.addAll(lines(viewer, state ? "toggle.on" : "toggle.off"));
        lore.addAll(lines(viewer, "toggle.action"));
        ItemStack item = item(state ? Material.LIME_DYE : Material.RED_DYE, text(viewer, module, key + ".name"), lore);
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
        if (current != null) {
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
        int seconds = config.promptTimeoutSeconds;
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
        return prompts.containsKey(player.getUniqueId());
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
        sounds.playUi(player, "click");
        try {
            button.onClick().accept(new MenuClick(player, menu, slot, type));
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder(false) instanceof Menu || event.getDestination().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Same menu instance re-opened: its old inventory closes after the new one is attached; ignore that.
        if (menu.getInventory() == event.getInventory() && open.remove(player.getUniqueId(), menu)) {
            menu.detach();
            if (event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
                sounds.playUi(player, "menu-close");
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> purgeMenuItems(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (config.closeOnTeleport && open.containsKey(event.getPlayer().getUniqueId())) {
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
                menu.tickInternal();
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
