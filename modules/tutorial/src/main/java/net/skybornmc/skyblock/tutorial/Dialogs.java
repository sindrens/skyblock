package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Talking NPCs (plan T section 3): a typewriter text above the NPC that only this player sees (a TextDisplay hidden from
 * everyone else), the finished line in chat as {@code NAME: text} with clickable commands. Click the NPC again: the line
 * finishes; click once more: the dialog is skipped. Walking away ends it. Bedrock (Geyser shows text displays badly): the
 * line types in the action bar instead. One dialog per player; main thread only; the tick runs only while one is open.
 */
final class Dialogs {

    static final String TAG = "phoenix_tutorial_dialog";

    private final TutorialModule m;
    private final Map<UUID, Session> sessions = new HashMap<>();

    /** One open dialog. */
    private final class Session {
        final Player player;
        final String npcId;
        final Location at;
        final String name;
        final List<String> lines;
        final boolean bedrock;
        /** Clicks on the NPC do not steer it (an idle line while the NPC's menu opens). */
        final boolean passive;
        /** Each line goes to chat when it is typed (false: the caller already sent it). */
        final boolean chat;
        final Consumer<Boolean> onEnd;
        int line;
        List<String> pieces = List.of();
        int piece;
        long ticks;
        long hold;
        boolean typing = true;
        boolean sent;
        boolean skipArmed;
        int lastTyped;
        TextDisplay display;

        Session(Player player, String npcId, Location at, String name, List<String> lines, boolean passive, boolean chat,
                Consumer<Boolean> onEnd) {
            this.player = player;
            this.npcId = npcId;
            this.at = at;
            this.name = name;
            this.lines = lines;
            this.passive = passive;
            this.chat = chat;
            this.onEnd = onEnd;
            this.bedrock = m.core().bedrock().isBedrock(player.getUniqueId());
            startLine(0);
        }

        void startLine(int index) {
            line = index;
            pieces = DialogText.pieces(DialogText.plain(lines.get(index)), m.config().wrap, m.config().maxLines);
            piece = 0;
            ticks = 0;
            typing = true;
            sent = false;
            skipArmed = false;
            lastTyped = 0;
        }

        String currentPiece() {
            return pieces.get(piece);
        }
    }

    Dialogs(TutorialModule m) {
        this.m = m;
    }

    boolean active(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** True when the player's open dialog belongs to this NPC and clicks steer it. */
    boolean steers(Player p, String npcId) {
        Session s = sessions.get(p.getUniqueId());
        return s != null && !s.passive && s.npcId != null && s.npcId.equalsIgnoreCase(npcId);
    }

    /**
     * Starts a dialog (ends the player's previous one without completing it).
     *
     * @param at      the NPC's feet; the text floats {@code dialog.height} above
     * @param name    the NPC's name (MiniMessage from our own config)
     * @param onEnd   called once: true when all lines were shown or skipped, false when it was cut off (walked away)
     */
    void start(Player p, String npcId, Location at, String name, List<String> lines, boolean passive, boolean chat,
               Consumer<Boolean> onEnd) {
        end(p, false);
        if (lines.isEmpty()) {
            if (onEnd != null) {
                onEnd.accept(true);
            }
            return;
        }
        Session s = new Session(p, npcId, at.clone(), name, List.copyOf(lines), passive, chat, onEnd);
        if (!s.bedrock && at.getWorld() != null) {
            Location spot = at.clone().add(0, m.config().height, 0);
            s.display = spot.getWorld().spawn(spot, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setVisibleByDefault(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setLineWidth(240);
                t.setShadowed(true);
                t.setDefaultBackground(false);
                t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                t.addScoreboardTag(TAG);
            });
            p.showEntity(m.core().plugin(), s.display);
        }
        sessions.put(p.getUniqueId(), s);
        render(s, 0);
    }

    /** A click on the NPC of the open dialog: finish the line, or (second click) skip the rest. */
    void click(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.passive) {
            return;
        }
        if (s.typing && !s.skipArmed) {
            s.piece = s.pieces.size() - 1;
            s.ticks = DialogText.typingTicks(s.currentPiece().length(), m.config().charsPerSecond) + 1;
            s.skipArmed = true;
            return; // the next tick shows the full line and sends it to chat
        }
        // Skip: the rest of the dialog goes to chat so nothing is lost.
        if (!s.sent && s.chat) {
            chat(s, s.lines.get(s.line));
        }
        for (int i = s.line + 1; i < s.lines.size(); i++) {
            if (s.chat) {
                chat(s, s.lines.get(i));
            }
        }
        end(p, true);
    }

    /** Ends the player's dialog (quit, reload, walked away). */
    void end(Player p, boolean completed) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        if (s.display != null) {
            s.display.remove();
        }
        if (s.bedrock && p.isOnline()) {
            p.sendActionBar(Component.empty());
        }
        if (s.onEnd != null) {
            s.onEnd.accept(completed);
        }
    }

    void endAll() {
        for (Session s : List.copyOf(sessions.values())) {
            end(s.player, false);
        }
    }

    /** Every tick while dialogs are open. */
    void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        double max = m.config().maxDistance;
        for (Session s : new ArrayList<>(sessions.values())) {
            Player p = s.player;
            if (!p.isOnline() || p.getWorld() != s.at.getWorld() || p.getLocation().distanceSquared(s.at) > max * max) {
                end(p, false);
                continue;
            }
            s.ticks++;
            String piece = s.currentPiece();
            if (s.typing) {
                int n = DialogText.typed(s.ticks, m.config().charsPerSecond, piece.length());
                if (n != s.lastTyped) {
                    sound(s, n);
                    s.lastTyped = n;
                    render(s, n);
                }
                if (n >= piece.length()) {
                    s.typing = false;
                    s.hold = Math.round(m.config().holdSeconds * 20);
                    if (s.piece == s.pieces.size() - 1 && !s.sent) {
                        s.sent = true;
                        if (s.chat) {
                            chat(s, s.lines.get(s.line));
                        }
                    }
                }
                continue;
            }
            if (--s.hold > 0) {
                continue;
            }
            if (s.piece + 1 < s.pieces.size()) {
                s.piece++;
                s.ticks = 0;
                s.typing = true;
                s.lastTyped = 0;
            } else if (s.line + 1 < s.lines.size()) {
                s.startLine(s.line + 1);
            } else {
                end(p, true);
            }
        }
    }

    private void sound(Session s, int typed) {
        String key = m.config().sound;
        if (key != null && !key.isBlank() && typed / 3 != s.lastTyped / 3) {
            m.core().sounds().play(s.player, key);
        }
    }

    private void render(Session s, int typed) {
        String text = s.currentPiece().substring(0, Math.min(typed, s.currentPiece().length()));
        if (s.bedrock) {
            s.player.sendActionBar(m.msg().component(s.player, "dialog.actionbar", Msg.mm("name", s.name),
                    Msg.p("text", text.replace('\n', ' '))));
        } else if (s.display != null && s.display.isValid()) {
            s.display.text(m.msg().component(s.player, "dialog.display", Msg.mm("name", s.name), Msg.p("text", text)));
        }
    }

    /** {@code NAME: text} in chat, commands clickable. */
    void chat(Player p, String name, String line) {
        p.sendMessage(m.msg().component(p, "dialog.chat", Msg.mm("name", name), Msg.c("line", line(p, line))));
    }

    private void chat(Session s, String line) {
        chat(s.player, s.name, line);
    }

    private Component line(Player p, String line) {
        Component out = Component.empty();
        for (DialogText.Segment seg : DialogText.segments(line)) {
            if (!seg.isCommand()) {
                out = out.append(Component.text(seg.text()));
                continue;
            }
            String command = "/" + seg.command();
            out = out.append(m.msg().component(p, "dialog.command", Msg.p("command", command))
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(m.msg().component(p, "dialog.command-hover", Msg.p("command", command)))));
        }
        return out;
    }
}
