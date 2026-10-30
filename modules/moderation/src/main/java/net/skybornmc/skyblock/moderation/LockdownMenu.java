package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.Optional;

/**
 * {@code /lockdown} (spec 13.9), Bedrock-safe: 3 rows, plain clicks only. Left: chat lockdown, middle: status, right: join
 * lockdown. A click starts the lockdown with its default length, or ends it when it is running. Other lengths:
 * {@code /lockdown chat <time>}. Live: the time left updates every second.
 */
final class LockdownMenu extends net.skybornmc.skyblock.common.menu.Menu {

    private static final int SLOT_CHAT = 11;
    private static final int SLOT_STATUS = 13;
    private static final int SLOT_JOIN = 15;

    private final LockdownService lockdown;

    LockdownMenu(LockdownService lockdown) {
        this.lockdown = lockdown;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, PunishMenus.MODULE, "lockdown.menu.title"));
    }

    @Override
    public String messageModule() {
        return PunishMenus.MODULE;
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        long now = System.currentTimeMillis();
        button(SLOT_CHAT, Lockdown.Kind.CHAT, now);
        button(SLOT_JOIN, Lockdown.Kind.JOIN, now);
        boolean any = lockdown.state().any(now);
        set(SLOT_STATUS, service.item(any ? Material.REDSTONE_TORCH : Material.LEVER, viewer, PunishMenus.MODULE,
                any ? "lockdown.menu.status-on" : "lockdown.menu.status-off",
                Msg.p("playtime", PunishConfig.text(lockdown.settings().newPlayerPlaytime())),
                Msg.p("age", PunishConfig.text(lockdown.settings().newPlayerAge()))));
    }

    private void button(int slot, Lockdown.Kind kind, long now) {
        Optional<Lockdown.Active> live = lockdown.state().live(kind, now);
        String base = "lockdown.menu." + kind.id();
        if (live.isPresent()) {
            TagResolver parts = lockdown.parts(viewer, live.get(), now);
            set(slot, service.item(Material.RED_CONCRETE, viewer, PunishMenus.MODULE, base + "-on", parts), click -> {
                if (viewer.hasPermission(LockdownService.PERM_LOCKDOWN)) {
                    lockdown.end(viewer, kind);
                }
                refresh();
            });
            return;
        }
        Lockdown.Settings s = lockdown.settings();
        set(slot, service.item(kind == Lockdown.Kind.CHAT ? Material.WRITABLE_BOOK : Material.OAK_DOOR, viewer,
                PunishMenus.MODULE, base + "-off", Msg.p("length", PunishConfig.text(s.defaultFor(kind))),
                Msg.p("max", PunishConfig.text(s.maxFor(kind)))), click -> {
            if (viewer.hasPermission(LockdownService.PERM_LOCKDOWN)) {
                String problem = lockdown.begin(viewer, kind, null);
                if (problem != null) {
                    lockdown.tellProblem(viewer, kind, problem);
                }
            }
            refresh();
        });
    }
}
