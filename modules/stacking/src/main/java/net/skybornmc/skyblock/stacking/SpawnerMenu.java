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
        update();
    }

    /**
     * What the buttons show. PERF2 ("stacking-menu-refresh" 15 ms): the menu used to clear and rebuild all 54 slots (frame,
     * fixed buttons and six items with 11 placeholders each) every second and after every click. Now only the info item
     * (next spawn, status) is redrawn each second; the buttons only when one of these values changed.
     */
    record Shown(int amount, int limit, int carried, int tier, String rate, String multiplier) {
    }

    private Shown shown;

    private Shown current() {
        SpawnerManager manager = module.spawners();
        return new Shown(spawner.amount, manager.limit(spawner), SpawnerManager.countInInventory(viewer.getInventory(), spawner.type),
                module.tiers().tier(spawner.type).orElse(0), SpawnerManager.number(manager.mobsPerMinute(spawner)),
                SpawnerManager.number(manager.rateMultiplier(spawner)));
    }

    /** Redraws what changed: everything when the buttons changed, else only the info item. */
    private void update() {
        if (spawner.removed) {
            viewer.closeInventory();
            return;
        }
        Shown now = current();
        if (!now.equals(shown)) {
            refresh();
            return;
        }
        set(SLOT_INFO_ITEM, info(placeholders(now)));
    }

    private TagResolver[] placeholders(Shown s) {
        SpawnerManager manager = module.spawners();
        return new TagResolver[]{
                SpawnerManager.mob(spawner.type),
                Msg.p("tier", s.tier()),
                Msg.p("count", Format.number(s.amount())),
                Msg.p("max", Format.number(s.limit())),
                Msg.p("rate", s.rate()),
                Msg.p("multiplier", s.multiplier()),
                Msg.p("next", Format.number(manager.secondsToNext(spawner))),
                Msg.p("carried", Format.number(s.carried())),
                Msg.p("range", module.config().activationRange),
                Msg.p("absolute-max", Format.number(module.config().spawnerStackMax)),
                Msg.c("status", service.text(viewer, StackingModule.ID, spawner.busy ? "menu.status.busy"
                        : manager.isActive(spawner) ? "menu.status.active" : "menu.status.paused"))
        };
    }

    private org.bukkit.inventory.ItemStack info(TagResolver[] p) {
        return service.item(Material.SPAWNER, viewer, StackingModule.ID, "menu.spawner", p);
    }

    @Override
    protected void build() {
        frame();
        SpawnerManager manager = module.spawners();
        shown = current();
        TagResolver[] p = placeholders(shown);
        set(SLOT_INFO_ITEM, info(p));
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

    /** Runs a change and updates a moment later (the database answers within a few ticks); only what changed is redrawn. */
    private void act(Runnable action) {
        action.run();
        module.core().scheduler().later("stacking-menu-refresh", 5L, () -> {
            if (isOpen()) {
                update();
            }
        });
    }
}
