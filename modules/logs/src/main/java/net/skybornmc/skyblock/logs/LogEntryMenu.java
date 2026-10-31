package net.skybornmc.skyblock.logs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

/**
 * One log entry (GUI-4c): what a plain click on a {@code /logs} result opens. It replaces the Java-only right-click for
 * the full details and puts the actions behind a second, clearly named click (no more instant teleport or /p from a
 * dense list of 28 look-alike entries). Bedrock players read the full details in a popup.
 * <pre>
 *  R O B B B B B O R
 *  B T P B E B C R B     10 Teleport, 11 Punish (chat line), 13 the entry, 15 Show in chat, 16 Read (Bedrock popup)
 *  ← O B B ✘ B B O R     18 Back (to the results, same page), 22 Close
 * </pre>
 */
final class LogEntryMenu extends Menu {

    static final int SLOT_TELEPORT = 10;
    static final int SLOT_PUNISH = 11;
    static final int SLOT_ENTRY = 13;
    static final int SLOT_CHAT = 15;
    static final int SLOT_READ = 16;

    private final LogsModule module;
    private final AuditEntry entry;

    LogEntryMenu(LogsModule module, AuditEntry entry) {
        this.module = module;
        this.entry = entry;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.render.text(viewer, "title.logs"), module.render.text(viewer, "entry-menu.crumb",
                Msg.p("id", entry.id())));
    }

    @Override
    public String messageModule() {
        return LogsModule.ID;
    }

    @Override
    public String helpTopic() {
        return "logs";
    }

    @Override
    protected void build() {
        frame();
        long now = System.currentTimeMillis();
        set(SLOT_ENTRY, module.render.item(viewer, entry, now, false));
        if (entry.hasLocation() && module.canTeleport(viewer)) {
            set(SLOT_TELEPORT, service.item(Material.ENDER_PEARL, viewer, LogsModule.ID, "entry-menu.teleport",
                    Msg.p("world", entry.world()), Msg.p("x", entry.x()), Msg.p("y", entry.y()), Msg.p("z", entry.z())), click -> teleport());
        }
        if (LogPunish.punishable(entry) && LogPunish.allowed(viewer)) {
            set(SLOT_PUNISH, service.item(Material.BREEZE_ROD, viewer, LogsModule.ID, "entry-menu.punish",
                    Msg.p("player", entry.actor() == null || entry.actor().name() == null ? "?" : entry.actor().name())), click -> {
                viewer.closeInventory();
                module.punishFrom(viewer, entry);
            });
        }
        set(SLOT_CHAT, service.item(Material.WRITABLE_BOOK, viewer, LogsModule.ID, "entry-menu.chat"), click -> {
            viewer.sendMessage(module.render.chatLine(viewer, entry, System.currentTimeMillis()));
            module.sound(viewer, "click");
        });
        if (service.usesForms(viewer)) {
            set(SLOT_READ, service.item(Material.BOOK, viewer, LogsModule.ID, "entry-menu.read"), click -> read());
        }
    }

    private void teleport() {
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            module.msg.send(viewer, "errors.world-unloaded", Msg.p("world", entry.world()));
            module.sound(viewer, "error");
            return;
        }
        viewer.closeInventory();
        module.teleport(viewer, new Location(world, entry.x() + 0.5, entry.y(), entry.z() + 0.5));
    }

    /** The full details (the chat hover card) in a Bedrock popup; Back or closing it reopens this screen. */
    private void read() {
        List<Component> lines = module.render.hoverLines(viewer, entry, System.currentTimeMillis());
        boolean shown = service.showText(viewer, module.render.text(viewer, "entry-menu.crumb", Msg.p("id", entry.id())), lines,
                List.of(module.render.text(viewer, "entry-menu.popup-back")), pressed -> service.open(viewer, this),
                () -> service.open(viewer, this));
        if (!shown) {
            viewer.sendMessage(module.render.chatLine(viewer, entry, System.currentTimeMillis()));
        }
    }
}
