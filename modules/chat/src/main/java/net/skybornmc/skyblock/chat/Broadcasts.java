package net.skybornmc.skyblock.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.afk.AfkPool;
import net.skybornmc.skyblock.common.text.ChatCard;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Broadcast cards (plan PLAN_TUTORIAL_CHAT section 2 + owner answer 3, 4 Nov 2026): a {@link ChatCard} about a feature
 * (shop, farm, mine, store, vote, ...) now and then, only while chat is active ({@link BroadcastPacing}), in a fair
 * random order ({@link BroadcastRotation}). Texts: {@code messages/broadcasts.yml → cards.<id>}; numbers: chat.yml
 * {@code broadcasts.*}. A player gets no card with the /settings Tips switch off, in the first minutes after joining,
 * while AFK, or when the card is about the place they are at ({@link BroadcastPlaces}).
 */
final class Broadcasts implements Listener {

    /** Messages file of the cards: {@code messages/broadcasts.yml}. */
    static final String TEXTS = "broadcasts";

    private final Core core;
    private final Supplier<ChatConfig> config;
    private final Display display;
    private final Predicate<UUID> tipsOn;
    private final BroadcastRotation rotation = new BroadcastRotation(new Random());
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private volatile BroadcastPacing pacing = new BroadcastPacing(System.currentTimeMillis());
    private volatile BroadcastPlaces.Parsed places = BroadcastPlaces.parse(List.of());
    private Scheduler.Task task = Scheduler.Task.NONE;
    private int period;

    Broadcasts(Core core, Supplier<ChatConfig> config, Display display, Predicate<UUID> tipsOn) {
        this.core = core;
        this.config = config;
        this.display = display;
        this.tipsOn = tipsOn;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        core.messages().load(TEXTS);
        pacing = new BroadcastPacing(System.currentTimeMillis());
        applyConfig();
    }

    /** {@code /skyblock reload chat}: texts, places and the check period; the chat count of this window is kept. */
    void reloaded() {
        core.messages().load(TEXTS);
        rotation.reset();
        applyConfig();
    }

    void stop() {
        task.cancel();
        task = Scheduler.Task.NONE;
        period = 0;
    }

    private void applyConfig() {
        ChatConfig c = config.get();
        BroadcastPlaces.Parsed parsed = BroadcastPlaces.parse(c.broadcastsPlaces);
        parsed.problems().forEach(p -> core.plugin().getLogger().warning("chat.yml → broadcasts.places: " + p + " (skipped)"));
        places = parsed;
        int ticks = c.broadcastsCheckSeconds * 20;
        if (ticks != period || task.cancelled()) {
            task.cancel();
            period = ticks;
            task = core.scheduler().repeat("chat:broadcasts", ticks, ticks, this::tick);
        }
    }

    // ------------------------------------------------------------------ the rule

    private void tick() {
        ChatConfig c = config.get();
        if (!c.broadcastsEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        BroadcastPacing p = pacing;
        if (!p.due(now, Bukkit.getOnlinePlayers().size(), c.broadcastRules())) {
            return;
        }
        String card = rotation.next(available(c));
        if (card == null) {
            return;
        }
        p.sent(now);
        long grace = c.broadcastsJoinGraceSeconds * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (receives(player, card, now, grace)) {
                player.sendMessage(render(player, card));
            }
        }
    }

    private boolean receives(Player player, String card, long now, long grace) {
        Long joined = joinedAt.get(player.getUniqueId());
        Location at = player.getLocation();
        return BroadcastPacing.receives(tipsOn.test(player.getUniqueId()), joined == null ? -1 : now - joined, grace,
                afk(player), places.at(card, at.getWorld() == null ? null : at.getWorld().getName(), at.getX()));
    }

    private boolean afk(Player player) {
        if (display.afk(player)) {
            return true;
        }
        return AfkPool.of(core.services()).map(pool -> pool.inPool(player)).orElse(false);
    }

    /** The cards of {@code broadcasts.cards} that can be shown now (text exists, command registered). */
    List<String> available(ChatConfig c) {
        List<String> out = new ArrayList<>();
        for (String id : c.broadcastsCards) {
            String card = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
            if (!card.isEmpty() && !out.contains(card) && usable(card)) {
                out.add(card);
            }
        }
        return out;
    }

    private boolean usable(String card) {
        Messages m = core.messages();
        if (!m.has(TEXTS, key(card, "title"))) {
            return false;
        }
        if (!blank(m.raw(TEXTS, key(card, "link")))) {
            return true;
        }
        String command = m.raw(TEXTS, key(card, "command"));
        if (blank(command)) {
            return true; // a plain tip without a click
        }
        String root = commandRoot(command);
        return root != null && core.plugin().getServer().getCommandMap().getCommand(root) != null;
    }

    // ------------------------------------------------------------------ rendering

    /** The card for one viewer: centred for Java, left-aligned for Bedrock. */
    Component render(Player viewer, String card) {
        Messages m = core.messages();
        Component title = m.component(viewer, TEXTS, key(card, "title"));
        List<Component> body = new ArrayList<>();
        String lines = m.raw(TEXTS, key(card, "lines"));
        if (!blank(lines)) {
            for (String line : lines.split("<newline>", -1)) {
                body.add(m.parse(viewer, line));
            }
        }
        String link = m.raw(TEXTS, key(card, "link"));
        String command = m.raw(TEXTS, key(card, "command"));
        Component action = null;
        ClickEvent click = null;
        Component hover = null;
        if (!blank(link)) {
            String url = link.strip();
            action = m.has(TEXTS, key(card, "action")) ? m.component(viewer, TEXTS, key(card, "action"))
                    : m.component(viewer, TEXTS, "format.link", Msg.p("link", url));
            click = ClickEvent.openUrl(url);
            hover = m.has(TEXTS, key(card, "hover")) ? m.component(viewer, TEXTS, key(card, "hover"))
                    : m.component(viewer, TEXTS, "format.hover-link");
        } else if (!blank(command)) {
            String typed = command.strip().startsWith("/") ? command.strip() : "/" + command.strip();
            action = m.has(TEXTS, key(card, "action")) ? m.component(viewer, TEXTS, key(card, "action"))
                    : m.component(viewer, TEXTS, "format.command", Msg.p("command", typed));
            click = ClickEvent.runCommand(typed);
            hover = m.has(TEXTS, key(card, "hover")) ? m.component(viewer, TEXTS, key(card, "hover"))
                    : m.component(viewer, TEXTS, "format.hover-command", Msg.p("command", typed));
        }
        return ChatCard.render(title, body, action, click, hover, !m.isBedrock(viewer));
    }

    private static String key(String card, String part) {
        return "cards." + card + "." + part;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /** {@code "/is upgrade"} → {@code "is"}; {@code "shop"} → {@code "shop"}; null for an empty command. */
    static String commandRoot(String command) {
        if (command == null) {
            return null;
        }
        String s = command.strip();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        String root = (space < 0 ? s : s.substring(0, space)).toLowerCase(Locale.ROOT);
        return root.isEmpty() ? null : root;
    }

    // ------------------------------------------------------------------ events

    /** Every public chat message that went out counts for the activity rule (staff and island chat are cancelled). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        pacing.chat(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        joinedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        joinedAt.remove(event.getPlayer().getUniqueId());
    }

    /** Card ids for {@code /chat card} (tab completion): every id in {@code broadcasts.cards}. */
    List<String> cardIds() {
        List<String> out = new ArrayList<>();
        for (String id : config.get().broadcastsCards) {
            if (id != null && !id.isBlank()) {
                out.add(id.strip().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** True if {@code messages/broadcasts.yml} has a card with this id. */
    boolean exists(String card) {
        return core.messages().has(TEXTS, key(card, "title"));
    }
}
