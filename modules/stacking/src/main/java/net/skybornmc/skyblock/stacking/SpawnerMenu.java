package net.skybornmc.skyblock.stacking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

/**
 * Spawner menu (owner S21, S28; sneak-right-click or right-click with an empty hand, Bedrock-friendly: plain clicks
 * only). Shows the type, stack and limit, output per minute, next spawn and status; buttons add from and take to the
 * inventory, and the upgrade item explains the masteries. Refreshes every second.
 */
final class SpawnerMenu extends Menu {

    static final int SLOT_INFO_ITEM = 13;
    static final int SLOT_ADD_ONE = 29;
    static final int SLOT_ADD_ALL = 30;
    static final int SLOT_UPGRADES = 31;
    static final int SLOT_TAKE_ONE = 32;
    static final int SLOT_TAKE_ALL = 33;

    private final StackingModule module;
    private final SpawnerManager.Spawner spawner;

    SpawnerMenu(StackingModule module, SpawnerManager.Spawner spawner) {
        this.module = module;
        this.spawner = spawner;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, StackingModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return StackingModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "spawners";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        if (spawner.removed) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    @Override
    protected void build() {
        frame();
        SpawnerManager manager = module.spawners();
        int limit = manager.limit(spawner);
        int carried = SpawnerManager.countInInventory(viewer.getInventory(), spawner.type);
        TagResolver[] p = {
                SpawnerManager.mob(spawner.type),
                Msg.p("tier", module.tiers().tier(spawner.type).orElse(0)),
                Msg.p("count", Format.number(spawner.amount)),
                Msg.p("max", Format.number(limit)),
                Msg.p("rate", SpawnerManager.number(manager.mobsPerMinute(spawner))),
                Msg.p("multiplier", SpawnerManager.number(manager.rateMultiplier(spawner))),
                Msg.p("next", Format.number(manager.secondsToNext(spawner))),
                Msg.p("carried", Format.number(carried)),
                Msg.p("range", module.config().activationRange),
                Msg.p("absolute-max", Format.number(module.config().spawnerStackMax)),
                Msg.c("status", service.text(viewer, StackingModule.ID, spawner.busy ? "menu.status.busy"
                        : manager.isActive(spawner) ? "menu.status.active" : "menu.status.paused"))
        };
        set(SLOT_INFO_ITEM, service.item(Material.SPAWNER, viewer, StackingModule.ID, "menu.spawner", p));
        set(SLOT_ADD_ONE, service.item(Material.LIME_DYE, viewer, StackingModule.ID, "menu.add-one", p),
                click -> act(() -> manager.add(viewer, spawner, 1)));
        set(SLOT_ADD_ALL, service.item(Material.EMERALD, viewer, StackingModule.ID, "menu.add-all", p),
                click -> act(() -> manager.add(viewer, spawner, Integer.MAX_VALUE)));
        set(SLOT_UPGRADES, service.item(Material.EXPERIENCE_BOTTLE, viewer, StackingModule.ID, "menu.upgrades", p),
                click -> module.msg().send(viewer, "menu.upgrades-soon"));
        set(SLOT_TAKE_ONE, service.item(Material.RED_DYE, viewer, StackingModule.ID, "menu.take-one", p),
                click -> act(() -> manager.remove(viewer, spawner, 1, "menu")));
        set(SLOT_TAKE_ALL, service.item(Material.HOPPER, viewer, StackingModule.ID, "menu.take-all", p),
                click -> act(() -> manager.remove(viewer, spawner, spawner.amount, "menu")));
    }

    /** Runs a change and refreshes a moment later (the database answers within a few ticks). */
    private void act(Runnable action) {
        action.run();
        module.core().scheduler().later("stacking-menu-refresh", 5L, () -> {
            if (isOpen()) {
                if (spawner.removed) {
                    viewer.closeInventory();
                } else {
                    refresh();
                }
            }
        });
    }
}
