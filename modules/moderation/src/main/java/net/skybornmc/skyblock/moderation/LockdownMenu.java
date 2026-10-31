package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

/**
 * {@code /lockdown} (spec 13.9), Bedrock-safe: 3 rows, plain clicks only. Left: chat lockdown, middle: status, right: join
 * lockdown. A click starts the lockdown with its default length, or ends it when it is running. Other lengths:
 * {@code /lockdown chat <time>}. Live: the time left updates every second.
 *
 * <p>GUI-4c (GUI_DESIGN §4): starting and ending go through a confirm with what happens on the middle item; the "?"
 * explains the screen.</p>
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
    public String infoKey() {
        return "lockdown.menu.info";
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
                if (!viewer.hasPermission(LockdownService.PERM_LOCKDOWN)) {
                    return;
                }
                openChild(confirm(kind, false, Material.RED_CONCRETE, p -> {
                    lockdown.end(p, kind);
                    service.open(p, this);
                }));
            });
            return;
        }
        Lockdown.Settings s = lockdown.settings();
        set(slot, service.item(kind == Lockdown.Kind.CHAT ? Material.WRITABLE_BOOK : Material.OAK_DOOR, viewer,
                PunishMenus.MODULE, base + "-off", Msg.p("length", PunishConfig.text(s.defaultFor(kind))),
                Msg.p("max", PunishConfig.text(s.maxFor(kind)))), click -> {
            if (!viewer.hasPermission(LockdownService.PERM_LOCKDOWN)) {
                return;
            }
            openChild(confirm(kind, true, kind == Lockdown.Kind.CHAT ? Material.WRITABLE_BOOK : Material.OAK_DOOR, p -> {
                String problem = lockdown.begin(p, kind, null);
                if (problem != null) {
                    lockdown.tellProblem(p, kind, problem);
                }
                service.open(p, this);
            }));
        });
    }

    /** "Start chat lockdown?" / "End join lockdown?" with who is affected and the length on the middle item. */
    private net.skybornmc.skyblock.common.menu.ConfirmMenu confirm(Lockdown.Kind kind, boolean start, Material icon,
                                                                   net.skybornmc.skyblock.common.menu.ConfirmMenu.Action action) {
        String key = "lockdown.menu.confirm." + (start ? "start-" : "end-") + kind.id();
        Lockdown.Settings s = lockdown.settings();
        TagResolver[] p = {Msg.p("length", PunishConfig.text(s.defaultFor(kind))),
                Msg.p("playtime", PunishConfig.text(s.newPlayerPlaytime())), Msg.p("age", PunishConfig.text(s.newPlayerAge()))};
        return new net.skybornmc.skyblock.common.menu.ConfirmMenu(v -> service.text(v, PunishMenus.MODULE, key + ".crumb"),
                service.item(new org.bukkit.inventory.ItemStack(icon), service.text(viewer, PunishMenus.MODULE, key + ".crumb"), List.of()),
                v -> service.lines(v, PunishMenus.MODULE, key + ".details", p), action, v -> { });
    }
}
