package net.skybornmc.skyblock.box;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Admin view of a player's box (the Box button in {@code /inspect}, permission {@code skyblock.box.admin}): the stacks of
 * one profile (switch profile with the head button), remove one stack or clear the whole box. Removed stacks move to the
 * {@code box_cleared} table (restorable by hand) and every action is audited.
 */
final class StaffBoxMenu extends PagedMenu<BoxEntry> {

    static final int SLOT_CLEAR = 3;
    static final int SLOT_PROFILE = 5;

    /** The inspected player, their profile keys/names and the shown one. */
    record Target(UUID player, String name, List<UUID> keys, List<String> profileNames, int index) {

        UUID key() {
            return keys.get(index);
        }

        String profileName() {
            return profileNames.get(index);
        }

        Target next() {
            return new Target(player, name, keys, profileNames, (index + 1) % keys.size());
        }
    }

    private final BoxModule m;
    private Target target;
    private List<BoxEntry> rows;

    private StaffBoxMenu(BoxModule module, Target target, List<BoxEntry> rows) {
        this.m = module;
        this.target = target;
        this.rows = rows;
    }

    static void open(BoxModule m, Player staff, Target target, Menu back) {
        m.service().engine().list(target.key(), m.config().loadLimit).whenComplete((rows, error) -> m.core().scheduler().main("box:staff", () -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Box: loading the staff view failed", Database.cause(error));
                m.msg().send(staff, "error");
                return;
            }
            if (staff.isOnline()) {
                StaffBoxMenu menu = new StaffBoxMenu(m, target, rows);
                if (back != null) {
                    menu.parent(back);
                }
                m.core().menus().open(staff, menu);
            }
        }));
    }

    private void reload() {
        m.service().engine().list(target.key(), m.config().loadLimit).whenComplete((fresh, error) -> m.core().scheduler().main("box:staff", () -> {
            if (error == null) {
                rows = fresh;
            }
            if (isOpen()) {
                refresh();
            } else if (viewer.isOnline()) {
                service.open(viewer, this);
            }
        }));
    }

    @Override
    public String messageModule() {
        return BoxModule.ID;
    }

    @Override
    public String infoKey() {
        return "staff.info";
    }

    @Override
    public String helpTopic() {
        return "box";
    }

    @Override
    protected String emptyKey() {
        return "staff.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, BoxModule.ID, "staff.title", Msg.p("player", target.name())));
    }

    @Override
    protected List<BoxEntry> entries() {
        return rows;
    }

    @Override
    protected void decorate() {
        if (!rows.isEmpty()) {
            set(SLOT_CLEAR, service.item(Material.LAVA_BUCKET, viewer, BoxModule.ID, "staff.clear",
                    Msg.number("count", rows.size()), Msg.p("player", target.name())), click -> confirmClear());
        }
        set(SLOT_PROFILE, service.item(Material.ARMOR_STAND, viewer, BoxModule.ID, target.keys().size() > 1 ? "staff.profile-switch" : "staff.profile",
                Msg.p("profile", target.profileName()), Msg.number("profiles", target.keys().size())), click -> {
            if (target.keys().size() > 1) {
                target = target.next();
                page(0);
                reload();
            }
        });
    }

    @Override
    protected MenuButton entry(BoxEntry e) {
        ItemStack shown = m.displayItem(viewer, e, "staff.entry");
        if (shown == null) {
            shown = service.item(Material.BARRIER, viewer, BoxModule.ID, "staff.unreadable", Msg.number("id", e.id()));
        }
        ItemStack icon = shown;
        return MenuButton.of(shown, click -> openChild(new ConfirmMenu(v -> service.text(v, BoxModule.ID, "staff.remove-crumb"), icon,
                v -> service.lines(v, BoxModule.ID, "staff.remove-details", Msg.p("player", target.name()), Msg.number("id", e.id())),
                staff -> m.service().archive(staff, target, List.of(e.id()), this::reload), staff -> {
                })));
    }

    private void confirmClear() {
        Target t = target;
        openChild(new ConfirmMenu(v -> service.text(v, BoxModule.ID, "staff.clear-crumb"),
                service.item(Material.LAVA_BUCKET, viewer, BoxModule.ID, "staff.clear", Msg.number("count", rows.size()), Msg.p("player", t.name())),
                v -> service.lines(v, BoxModule.ID, "staff.clear-details", Msg.number("count", rows.size()), Msg.p("player", t.name()),
                        Msg.p("profile", t.profileName())),
                staff -> m.service().archive(staff, t, null, this::reload), staff -> {
                }));
    }
}
