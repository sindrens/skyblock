package net.skybornmc.skyblock.common.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The one API for "click to answer" (GUI-5, GUI_DESIGN §5, UX_REVIEW §4): {@link #offer} sends the chat line (clickable
 * on Java) and, for Bedrock players who can't click chat, opens a small 3-row popup with the same choices. A popup never
 * opens over another open window: it waits until that window closes (then the next waiting popup opens, one by one).
 * A popup for something that expired says so instead of offering dead buttons. Settings: {@code menus.yml →
 * bedrock-popups.*}. Reach it with {@code core.menus().offers()}.
 */
public final class Offers implements Listener {

    /** What {@link #offer} does with an offer. */
    public enum Route {
        /** Only the chat line (Java, popups off, or nothing to click). */
        CHAT_ONLY,
        /** Chat line plus the popup (now, or when the open window closes). */
        POPUP
    }

    private final MenuService menus;
    private final OfferQueue<Offer> queue;
    /** Players with a drain already scheduled (one task per player at a time). */
    private final Set<UUID> scheduled = ConcurrentHashMap.newKeySet();

    Offers(MenuService menus) {
        this.menus = menus;
        MenuConfig c = menus.config();
        this.queue = new OfferQueue<>(c.bedrockPopupQueueMax, c.bedrockPopupMaxAgeSeconds * 1000L);
    }

    void reload() {
        MenuConfig c = menus.config();
        queue.configure(c.bedrockPopupQueueMax, c.bedrockPopupMaxAgeSeconds * 1000L);
    }

    /** Pure routing rule, unit-tested: popups only for Bedrock players, when enabled and the offer has something to click. */
    public static Route route(boolean enabled, boolean bedrock, boolean hasPopup) {
        return enabled && bedrock && hasPopup ? Route.POPUP : Route.CHAT_ONLY;
    }

    /** Pure rule, unit-tested: a popup waits while another window is open or the player is typing an answer in chat. */
    public static boolean mustWait(boolean windowOpen, boolean chatPrompt) {
        return windowOpen || chatPrompt;
    }

    /**
     * Sends the offer's chat line to {@code player} and, on Bedrock, shows the popup (now, or queued behind the open
     * window). Main thread or not: the popup itself always opens on the main thread.
     */
    public void offer(Player player, Offer offer) {
        if (player == null || offer == null) {
            return;
        }
        if (offer.chat() != null) {
            offer.chat().accept(player);
        }
        Route route = route(menus.config().bedrockPopups, menus.messages().isBedrock(player), offer.hasPopup());
        if (route == Route.CHAT_ONLY) {
            return;
        }
        queue.add(player.getUniqueId(), offer, System.currentTimeMillis());
        schedule(player.getUniqueId(), 1L);
    }

    /** True if {@code player} would see a popup for an offer with buttons (Bedrock and popups on). */
    public boolean popups(Player player) {
        return route(menus.config().bedrockPopups, menus.messages().isBedrock(player), true) == Route.POPUP;
    }

    /** Popups waiting for {@code player} (tests, debugging). */
    public int waiting(Player player) {
        return queue.size(player.getUniqueId());
    }

    private void schedule(UUID id, long delay) {
        if (!scheduled.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(menus.plugin(), () -> {
            scheduled.remove(id);
            drain(id);
        }, delay);
    }

    /** Opens the next waiting popup if the player has no other window open. */
    private void drain(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            queue.forget(id);
            return;
        }
        if (!queue.has(id)) {
            return;
        }
        boolean prompt = menus.hasPrompt(player);
        if (mustWait(player.getOpenInventory().getType() != InventoryType.CRAFTING, prompt)) {
            if (prompt) {
                // A chat prompt has no close event: look again a bit later.
                schedule(id, menus.config().bedrockPopupRetryTicks);
            }
            return; // the close event of the open window drains again
        }
        queue.next(id, System.currentTimeMillis()).ifPresent(offer -> {
            try {
                menus.open(player, new OfferMenu(offer));
            } catch (RuntimeException e) {
                menus.plugin().getLogger().log(Level.WARNING, "Could not open the popup " + offer.id() + " for " + player.getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (queue.has(id)) {
            schedule(id, 1L); // next tick: the window is really gone, and a menu opened right after closing counts
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queue.forget(event.getPlayer().getUniqueId());
    }

    void clear() {
        queue.clear();
        scheduled.clear();
    }
}
