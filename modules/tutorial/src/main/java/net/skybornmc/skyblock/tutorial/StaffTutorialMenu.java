package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff view from {@code /inspect} (TUTORIAL.md §9.2, TQ23): the player's state, step, times and done steps (helper+),
 * and Skip / Reset with a confirm screen (mod+, audited). Works for offline players.
 */
final class StaffTutorialMenu extends Menu {

    static final String PERM_STATUS = "skyblock.tutorial.admin.status";
    static final String PERM_ADMIN = "skyblock.tutorial.admin";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    private final TutorialModule module;
    private final UUID player;
    private final String name;

    StaffTutorialMenu(TutorialModule module, UUID player, String name) {
        this.module = module;
        this.player = player;
        this.name = name;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, TutorialModule.ID, "staff.menu.title", Msg.p("player", name)));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    public String infoKey() {
        return "staff.menu.info";
    }

    @Override
    public String helpTopic() {
        return TutorialModule.ID;
    }

    @Override
    protected void build() {
        frame();
        setAsync(13, module.engine().lookup(player).thenApply(found -> MenuButton.display(status(found))));
        if (!viewer.hasPermission(PERM_ADMIN)) {
            return;
        }
        set(29, service.item(Material.FEATHER, viewer, TutorialModule.ID, "staff.menu.skip", Msg.p("player", name)),
                click -> click.menu().openChild(new ConfirmMenu(v -> service.text(v, TutorialModule.ID, "staff.menu.skip-crumb"), null,
                        v -> service.lines(v, TutorialModule.ID, "staff.menu.skip-confirm", Msg.p("player", name)),
                        staff -> run(staff, "skip"), staff -> { })));
        set(33, service.item(Material.TNT_MINECART, viewer, TutorialModule.ID, "staff.menu.reset", Msg.p("player", name)),
                click -> click.menu().openChild(new ConfirmMenu(v -> service.text(v, TutorialModule.ID, "staff.menu.reset-crumb"), null,
                        v -> service.lines(v, TutorialModule.ID, "staff.menu.reset-confirm", Msg.p("player", name)),
                        staff -> run(staff, "reset"), staff -> { })));
    }

    private void run(Player staff, String what) {
        if (!staff.hasPermission(PERM_ADMIN)) {
            return;
        }
        var future = what.equals("skip") ? module.engine().staffSkip(Actor.player(staff), player, name)
                : module.engine().staffReset(Actor.player(staff), player, name);
        future.thenAccept(ok -> {
            module.msg().send(staff, ok ? "staff." + what + "-done" : "staff.nothing", Msg.p("player", name));
            module.core().sounds().play(staff, ok ? "success" : "error");
        });
    }

    private org.bukkit.inventory.ItemStack status(Optional<Progress> found) {
        if (found.isEmpty()) {
            return service.item(Material.GRAY_DYE, viewer, TutorialModule.ID, "staff.menu.no-data", Msg.p("player", name));
        }
        Progress p = found.get();
        TutorialFlow flow = module.engine().flow();
        String step = p.step == null ? "-" : p.step + " (" + flow.number(p.step) + "/" + flow.size() + ")";
        return service.item(Material.WRITTEN_BOOK, viewer, TutorialModule.ID, "staff.menu.status",
                Msg.p("player", name),
                Msg.c("state", service.text(viewer, TutorialModule.ID, "states." + TutorialStore.key(p.state))),
                Msg.p("step", step),
                Msg.number("done", p.done.size()), Msg.number("total", flow.size()),
                Msg.p("started", time(p.startedAt)), Msg.p("finished", time(p.finishedAt)),
                Msg.p("rewarded", p.rewarded ? "yes" : "no"), Msg.number("replays", p.replays));
    }

    private static String time(Long at) {
        return at == null || at <= 0 ? "-" : DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(at));
    }
}
