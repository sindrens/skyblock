package net.skybornmc.skyblock.stacking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

/**
 * Spawner menu (owner S21, S28; sneak-right-click or right-click with an empty hand, Bedrock-friendly: plain clicks
 * only). Shows the type, stack and limit, output per minute, next spawn and status; buttons add from and take to the
 * inventory, and the upgrade item explains the masteries. Refreshes every second. Take all removes the block, so it
 * asks first with the count on the middle item (GUI_DESIGN §4).
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
    record Shown(int amount, int limit, int carried, int tier, String rate, String multiplier, long tierCount,
                 String seconds, String mobs, String loot, int cap, int level, int prestige, boolean canPrestige) {
    }

    private Shown shown;

    private Shown current() {
        SpawnerManager manager = module.spawners();
        return new Shown(spawner.amount, manager.limit(spawner), SpawnerManager.countInInventory(viewer.getInventory(), spawner.type),
                module.tiers().tier(spawner.type).orElse(0), SpawnerManager.number(manager.mobsPerMinute(spawner)),
                SpawnerManager.number(manager.masteryMultiplier(spawner)), manager.islandCount(spawner),
                SpawnerManager.number(manager.seconds(spawner)), SpawnerManager.number(manager.mobsPerSpawn(spawner)),
                SpawnerManager.number(manager.loot(spawner)),
                spawner.island == null ? 0 : module.spawnerLimit(spawner.island, spawner.type),
                spawner.island == null ? 1 : module.spawnerLevel(spawner.island, spawner.type),
                spawner.island == null ? 0 : module.levels().peek(spawner.island, spawner.type).prestige, canPrestige());
    }

    /** Spawners v2: the island's mob type is at the top level and may prestige. */
    private boolean canPrestige() {
        if (spawner.island == null) {
            return false;
        }
        SpawnerLevels.State st = module.levels().peek(spawner.island, spawner.type);
        return SpawnerLevelRules.canPrestige(st.kills, module.levelBase(spawner.type), module.config().levelMax, st.prestige,
                module.config().prestigeMax);
    }

    /** Ten dots, the done part lit (the same dots as the quest bars, Bedrock-safe). Pure. */
    static String bar(double progress) {
        int lit = (int) Math.floor(Math.max(0, Math.min(1, progress)) * 10);
        return "<c:flame>" + "●".repeat(lit) + "</c:flame><c:locked>" + "●".repeat(10 - lit) + "</c:locked>";
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
        SpawnerLevels.State st = spawner.island == null ? new SpawnerLevels.State() : module.levels().peek(spawner.island, spawner.type);
        long base = module.levelBase(spawner.type);
        int maxLevel = module.config().levelMax;
        long need = s.level() >= maxLevel ? SpawnerLevelRules.killsFor(maxLevel, base) : SpawnerLevelRules.killsFor(s.level() + 1, base);
        double progress = SpawnerLevelRules.progress(st.kills, base, maxLevel);
        long placed = spawner.island == null ? spawner.amount : manager.typeCount(spawner.island, spawner.type);
        return new TagResolver[]{
                SpawnerManager.mob(spawner.type),
                // Spawners v2: level, kills, placed / limit, prestige
                Msg.number("level", s.level()),
                Msg.number("max-level", maxLevel),
                Msg.mm("bar", bar(progress)),
                Msg.number("percent", (long) Math.floor(progress * 100)),
                Msg.p("kills", Format.number(st.kills)),
                Msg.p("need", Format.number(need)),
                Msg.p("left", Format.number(spawner.island == null ? 0 : module.killsToNext(spawner.island, spawner.type))),
                Msg.number("more", module.config().limitPerLevel),
                Msg.p("placed", Format.number(placed)),
                Msg.p("limit", Format.number(s.cap())),
                Msg.number("prestige", s.prestige()),
                Msg.number("max-prestige", module.config().prestigeMax),
                Msg.p("bonus", SpawnerManager.number(s.prestige() * module.config().prestigeDropPercent)),
                Msg.p("next-bonus", SpawnerManager.number((s.prestige() + 1) * module.config().prestigeDropPercent)),
                Msg.p("tier", s.tier()),
                Msg.p("count", Format.number(s.amount())),
                Msg.p("max", Format.number(s.limit())),
                Msg.p("rate", s.rate()),
                Msg.p("multiplier", s.multiplier()),
                Msg.p("next", Format.number(manager.secondsToNext(spawner))),
                Msg.p("carried", Format.number(s.carried())),
                Msg.p("range", module.config().activationRange),
                Msg.p("absolute-max", Format.number(module.config().spawnerStackMax)),
                // Owner F2: "Tier 2 on this island: 38 = 27.7 at full output. Next one works at 35%."
                Msg.p("tier-count", Format.number(s.tierCount())),
                Msg.p("effective", SpawnerManager.number(module.efficiency().effective(s.tierCount()))),
                Msg.p("next-percent", percent(module.efficiency().nextPercent(s.tierCount()))),
                // ECON-SPAWNERS step 10: island steps and the cap of this tier (0 = no cap).
                Msg.p("seconds", s.seconds()),
                Msg.p("mobs", s.mobs()),
                Msg.p("loot", s.loot()),
                Msg.p("owned", Format.number(s.tierCount())),
                Msg.c("cap", s.cap() > 0 ? net.kyori.adventure.text.Component.text(Format.number(s.cap()))
                        : service.text(viewer, StackingModule.ID, "menu.no-cap")),
                Msg.c("status", service.text(viewer, StackingModule.ID, spawner.busy ? "menu.status.busy"
                        : manager.isActive(spawner) ? "menu.status.active" : "menu.status.paused"))
        };
    }

    /** 35 → "35", 12.5 → "12.5". */
    static String percent(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private org.bukkit.inventory.ItemStack info(TagResolver[] p) {
        return service.item(Material.SPAWNER, viewer, StackingModule.ID, "menu.spawner", p);
    }

    @Override
    protected void build() {
        navBar();
        SpawnerManager manager = module.spawners();
        shown = current();
        TagResolver[] p = placeholders(shown);
        set(SLOT_INFO_ITEM, info(p));
        set(SLOT_ADD_ONE, service.item(Material.LIME_DYE, viewer, StackingModule.ID, "menu.add-one", p),
                click -> act(() -> manager.add(viewer, spawner, 1)));
        set(SLOT_ADD_ALL, service.item(Material.EMERALD, viewer, StackingModule.ID, "menu.add-all", p),
                click -> act(() -> manager.add(viewer, spawner, Integer.MAX_VALUE)));
        // Spawners v2: the prestige of this mob type on the island (at the top level, with a confirm).
        boolean premium = module.premium(spawner.type);
        String key = premium ? "menu.prestige-premium" : shown.canPrestige() ? "menu.prestige-ready"
                : shown.prestige() >= module.config().prestigeMax ? "menu.prestige-max" : "menu.prestige-locked";
        set(SLOT_UPGRADES, service.item(shown.canPrestige() ? Material.NETHER_STAR : Material.EXPERIENCE_BOTTLE, viewer, StackingModule.ID, key, p),
                click -> {
                    if (!shown.canPrestige() || spawner.island == null) {
                        module.core().sounds().play(click.player(), "error");
                        return;
                    }
                    java.util.UUID island = spawner.island;
                    String mob = spawner.type;
                    openChild(new ConfirmMenu(v -> service.text(v, StackingModule.ID, "menu.prestige-confirm.crumb"), null,
                            v -> service.lines(v, StackingModule.ID, "menu.prestige-confirm.details", placeholders(current())),
                            player -> {
                                if (!module.prestigeSpawners(player, island, mob)) {
                                    module.core().sounds().play(player, "error");
                                }
                            }, player -> { }));
                });
        set(SLOT_TAKE_ONE, service.item(Material.RED_DYE, viewer, StackingModule.ID, "menu.take-one", p),
                click -> act(() -> manager.remove(viewer, spawner, 1, "menu")));
        set(SLOT_TAKE_ALL, service.item(Material.HOPPER, viewer, StackingModule.ID, "menu.take-all", p),
                click -> openChild(new ConfirmMenu(v -> service.text(v, StackingModule.ID, "menu.take-all-confirm.crumb"),
                        service.item(Material.SPAWNER, service.text(viewer, StackingModule.ID, "menu.take-all.name", p),
                                java.util.List.of()),
                        v -> service.lines(v, StackingModule.ID, "menu.take-all-confirm.details", placeholders(current())),
                        player -> manager.remove(player, spawner, spawner.amount, "menu"), player -> { })));
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
