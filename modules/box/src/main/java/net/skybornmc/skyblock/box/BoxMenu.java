package net.skybornmc.skyblock.box;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /box}: the stacks waiting for the player's active profile, oldest first, 28 per page. Click one to take it, or
 * "Take all that fit". Plain left clicks only (Bedrock-safe); the lore says where each stack came from and when.
 */
final class BoxMenu extends PagedMenu<BoxEntry> {

    static final int SLOT_ALL = 4;

    private final BoxModule m;
    private final UUID profile;
    private List<BoxEntry> rows;
    private boolean busy;

    private BoxMenu(BoxModule module, UUID profile, List<BoxEntry> rows) {
        this.m = module;
        this.profile = profile;
        this.rows = rows;
    }

    static void open(BoxModule module, Player player) {
        UUID uuid = player.getUniqueId();
        UUID profile = module.service().key(player);
        load(module, profile, rows -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && module.service().key(p).equals(profile)) {
                module.core().menus().open(p, new BoxMenu(module, profile, rows));
            }
        }, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                module.msg().send(p, "error");
            }
        });
    }

    private static void load(BoxModule m, UUID profile, Consumer<List<BoxEntry>> then, Runnable failed) {
        m.service().engine().list(profile, m.config().loadLimit).whenComplete((rows, error) -> m.core().scheduler().main("box:load", () -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Box: loading the box of " + profile + " failed", Database.cause(error));
                failed.run();
                return;
            }
            then.accept(rows);
        }));
    }

    /** Loads the rows again and rebuilds (after a take or a new delivery). */
    void reload() {
        load(m, profile, fresh -> {
            busy = false;
            rows = fresh;
            if (isOpen()) {
                refresh();
            }
        }, () -> busy = false);
    }

    @Override
    public String messageModule() {
        return BoxModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "box";
    }

    @Override
    protected String emptyKey() {
        return "menu.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, BoxModule.ID, "menu.title"));
    }

    @Override
    protected List<BoxEntry> entries() {
        return rows;
    }

    @Override
    protected void decorate() {
        if (!rows.isEmpty()) {
            int count = m.service().count(profile).orElse(rows.size());
            set(SLOT_ALL, service.item(Material.HOPPER, viewer, BoxModule.ID, "menu.take-all",
                    Msg.number("count", count), Msg.number("max", m.config().maxEntries)), click -> takeAll());
        }
    }

    @Override
    protected MenuButton entry(BoxEntry e) {
        ItemStack shown = m.displayItem(viewer, e, "menu.entry");
        if (shown == null) {
            return MenuButton.display(service.item(Material.BARRIER, viewer, BoxModule.ID, "menu.unreadable"));
        }
        return MenuButton.of(shown, click -> take(e));
    }

    private void take(BoxEntry e) {
        if (busy) {
            return;
        }
        busy = true;
        m.service().take(viewer, List.of(e), false, this::reload);
        if (!m.service().isTaking(profile)) {
            busy = false; // refused before the database (no space, cooldown …)
        }
    }

    private void takeAll() {
        if (busy) {
            return;
        }
        busy = true;
        m.service().take(viewer, new ArrayList<>(rows), true, this::reload);
        if (!m.service().isTaking(profile)) {
            busy = false;
        }
    }
}
