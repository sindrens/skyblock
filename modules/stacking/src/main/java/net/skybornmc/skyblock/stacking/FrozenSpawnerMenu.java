package net.skybornmc.skyblock.stacking;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * R3-13b staff restore of frozen spawners (the "Frozen spawners" button in {@code /inspect}, permission
 * {@code skyblock.spawner.restore}): the stacked spawners a player placed whose block was lost (crash, world edit) and
 * that wait in {@code stacking_spawners_frozen}. Click one, confirm, and the spawners go to the placer's /box. Plain menu
 * buttons (Bedrock-safe); every restore is audited ({@code staff/spawner_restore}).
 */
final class FrozenSpawnerMenu extends PagedMenu<SpawnerStore.Frozen> {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StackingModule m;
    private final UUID player;
    private final String name;
    private List<SpawnerStore.Frozen> rows;

    private FrozenSpawnerMenu(StackingModule module, UUID player, String name, List<SpawnerStore.Frozen> rows) {
        this.m = module;
        this.player = player;
        this.name = name;
        this.rows = rows;
    }

    static void open(StackingModule m, Player staff, UUID player, String name, Menu back) {
        m.spawners().frozenPlacedBy(player, m.config().frozenMenuLimit).whenComplete((rows, error) -> m.core().scheduler().main("stacking:frozen", () -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Stacking: loading frozen spawners failed", error);
                m.msg().send(staff, "spawner.error");
                return;
            }
            if (staff.isOnline()) {
                FrozenSpawnerMenu menu = new FrozenSpawnerMenu(m, player, name, rows);
                if (back != null) {
                    menu.parent(back);
                }
                m.core().menus().open(staff, menu);
            }
        }));
    }

    private void reload() {
        m.spawners().frozenPlacedBy(player, m.config().frozenMenuLimit).whenComplete((fresh, error) -> m.core().scheduler().main("stacking:frozen", () -> {
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
        return StackingModule.ID;
    }

    @Override
    public String infoKey() {
        return "frozen.info";
    }

    @Override
    public String helpTopic() {
        return "spawners";
    }

    @Override
    protected String emptyKey() {
        return "frozen.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, StackingModule.ID, "frozen.title", Msg.p("player", name)));
    }

    @Override
    protected List<SpawnerStore.Frozen> entries() {
        return rows;
    }

    @Override
    protected MenuButton entry(SpawnerStore.Frozen f) {
        SpawnerStore.Row r = f.row();
        String type = SpawnerTiers.normalize(r.type());
        ItemStack icon = service.item(Material.SPAWNER, viewer, StackingModule.ID, "frozen.entry",
                SpawnerManager.mob(type), Msg.p("amount", r.amount()), Msg.p("id", f.id()), Msg.p("reason", f.reason()),
                Msg.p("world", r.world()), Msg.p("x", r.x()), Msg.p("y", r.y()), Msg.p("z", r.z()),
                Msg.p("date", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(f.frozenAt()))));
        return MenuButton.of(icon, click -> openChild(new ConfirmMenu(v -> service.text(v, StackingModule.ID, "frozen.confirm-crumb"), icon,
                v -> service.lines(v, StackingModule.ID, "frozen.confirm-details", Msg.p("player", name), Msg.p("amount", r.amount()),
                        SpawnerManager.mob(type)),
                staff -> {
                    if (!staff.hasPermission(StackingModule.PERM_RESTORE)) {
                        m.msg().send(staff, "frozen.no-permission");
                        return;
                    }
                    m.spawners().restore(staff, f, ok -> reload());
                }, staff -> {
                })));
    }
}
