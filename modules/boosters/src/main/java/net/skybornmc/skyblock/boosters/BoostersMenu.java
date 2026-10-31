package net.skybornmc.skyblock.boosters;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /boosters}: your personal boosters per type (running, time left, banked), the global boosters per type (who
 * started it, time left, the queue; click to thank) and the booster items in your inventory (click, then confirm, to
 * start: a mis-tap must never waste a booster, GUI_DESIGN §4). Updates every second. Bedrock: every action is a plain tap.
 */
final class BoostersMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int[] PERSONAL_SLOTS = {10, 12, 14, 16};
    static final int[] GLOBAL_SLOTS = {28, 30, 32, 34};
    static final int[] OWNED_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    private static final int QUEUE_LINES = 3;

    private final BoostersModule m;

    BoostersMenu(BoostersModule module) {
        this.m = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, BoostersModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return BoostersModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "boosters";
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
        set(SLOT_SUMMARY, service.item(Material.NETHER_STAR, viewer, BoostersModule.ID, "menu.summary"));
        PersonalBoosters.State state = m.personal().state(viewer);
        BoosterType[] types = BoosterType.values();
        for (int i = 0; i < types.length && i < PERSONAL_SLOTS.length; i++) {
            set(PERSONAL_SLOTS[i], personalItem(types[i], state));
            set(GLOBAL_SLOTS[i], globalItem(types[i]), click -> thank(click.player()));
        }
        List<ItemStack> owned = m.owned(viewer);
        int shown = owned.size() > OWNED_SLOTS.length ? OWNED_SLOTS.length - 1 : owned.size();
        for (int i = 0; i < shown; i++) {
            Optional<BoosterItems.Spec> spec = BoosterItems.read(owned.get(i));
            if (spec.isEmpty()) {
                continue;
            }
            UUID itemUuid = spec.get().itemUuid();
            BoosterItems.Spec s = spec.get();
            ItemStack stack = owned.get(i);
            set(OWNED_SLOTS[i], ownedItem(stack, s), click -> openChild(confirmStart(stack, s, itemUuid)));
        }
        if (owned.size() > OWNED_SLOTS.length) {
            set(OWNED_SLOTS[OWNED_SLOTS.length - 1], service.item(Material.CHEST, viewer, BoostersModule.ID, "menu.owned.more",
                    Msg.number("more", owned.size() - shown)));
        } else if (owned.isEmpty()) {
            set(40, service.item(Material.GRAY_DYE, viewer, BoostersModule.ID, "menu.owned.none"));
        }
    }

    private ItemStack personalItem(BoosterType type, PersonalBoosters.State state) {
        String name = m.typeName(type);
        if (state == null) {
            return service.item(Material.CLOCK, viewer, BoostersModule.ID, "menu.loading");
        }
        PersonalBoosters.Run run = state.run(type);
        List<Component> lore = new ArrayList<>();
        Material icon;
        String title;
        if (run != null) {
            icon = m.material(type);
            title = "menu.personal.running";
            lore.addAll(service.lines(viewer, BoostersModule.ID, "menu.personal.running.lore", Msg.p("type", name),
                    Msg.number("percent", run.percent), Msg.p("time", BoosterRules.time(run.remaining))));
        } else {
            icon = Material.GRAY_DYE;
            title = "menu.personal.idle";
            lore.addAll(service.lines(viewer, BoostersModule.ID, "menu.personal.idle.lore", Msg.p("type", name)));
        }
        var bank = state.banks.get(type);
        if (bank != null && bank.banked() > 0) {
            lore.addAll(service.lines(viewer, BoostersModule.ID, m.personal().held(viewer) ? "menu.personal.banked-held"
                    : "menu.personal.banked", Msg.number("percent", bank.percent()), Msg.p("time", BoosterRules.time(bank.banked()))));
        }
        int globalPercent = m.global().percent(type);
        if (globalPercent > 0) {
            lore.addAll(service.lines(viewer, BoostersModule.ID, "menu.personal.global", Msg.number("percent", globalPercent)));
        }
        lore.addAll(service.lines(viewer, BoostersModule.ID, "menu.personal.footer",
                Msg.p("max", BoosterRules.time(m.settings.personalMaxMillis()))));
        return service.item(icon, service.text(viewer, BoostersModule.ID, title + ".name", Msg.p("type", name)), lore);
    }

    private ItemStack globalItem(BoosterType type) {
        String name = m.typeName(type);
        Optional<GlobalQueue.Entry> active = m.global().active(type);
        if (active.isEmpty()) {
            return service.item(Material.GRAY_STAINED_GLASS_PANE, service.text(viewer, BoostersModule.ID, "menu.global.idle.name",
                    Msg.p("type", name)), service.lines(viewer, BoostersModule.ID, "menu.global.idle.lore", Msg.p("type", name)));
        }
        GlobalQueue.Entry e = active.get();
        List<Component> lore = new ArrayList<>(service.lines(viewer, BoostersModule.ID, "menu.global.running.lore",
                Msg.p("type", name), Msg.number("percent", e.percent), Msg.p("time", BoosterRules.time(e.remaining)),
                Msg.p("name", e.starterName), Msg.number("thanks", e.thanked.size())));
        List<GlobalQueue.Entry> queued = m.global().queued(type);
        for (int i = 0; i < queued.size() && i < QUEUE_LINES; i++) {
            GlobalQueue.Entry q = queued.get(i);
            lore.add(m.msg.component(viewer, "menu.global.queue-line", Msg.p("name", q.starterName), Msg.number("percent", q.percent),
                    Msg.p("time", BoosterRules.time(q.remaining)), Msg.p("wait", BoosterRules.time(m.global().waitBefore(q)))));
        }
        if (queued.size() > QUEUE_LINES) {
            lore.add(m.msg.component(viewer, "menu.global.queue-more", Msg.number("more", queued.size() - QUEUE_LINES)));
        }
        boolean own = e.starter.equals(viewer.getUniqueId());
        String action = own ? "menu.global.own" : e.thanked.contains(viewer.getUniqueId()) ? "menu.global.thanked" : "menu.global.thank";
        lore.addAll(service.lines(viewer, BoostersModule.ID, action, Msg.p("name", e.starterName), Msg.number("gems", m.settings.thanksGems)));
        return service.item(m.globalMaterial(),
                service.text(viewer, BoostersModule.ID, "menu.global.running.name", Msg.p("type", name), Msg.number("percent", e.percent)), lore);
    }

    private ItemStack ownedItem(ItemStack stack, BoosterItems.Spec spec) {
        String key = spec.global() ? "menu.owned.global" : "menu.owned.personal";
        List<Component> lore = new ArrayList<>(service.lines(viewer, BoostersModule.ID, key + ".lore", Msg.p("type", m.typeName(spec.type())),
                Msg.number("percent", spec.percent()), Msg.p("time", BoosterRules.time(spec.millis()))));
        lore.addAll(service.lines(viewer, BoostersModule.ID, "menu.owned.action"));
        return service.item(stack.getType(), service.text(viewer, BoostersModule.ID, key + ".name", Msg.p("type", m.typeName(spec.type())),
                Msg.number("percent", spec.percent())), lore);
    }

    /** "Start this booster?": the booster on the middle item, what it gives and for how long as the details. */
    private ConfirmMenu confirmStart(ItemStack stack, BoosterItems.Spec spec, UUID itemUuid) {
        String key = spec.global() ? "menu.owned.global" : "menu.owned.personal";
        ItemStack shown = service.item(stack.getType(), service.text(viewer, BoostersModule.ID, key + ".name",
                Msg.p("type", m.typeName(spec.type())), Msg.number("percent", spec.percent())), List.of());
        return new ConfirmMenu(v -> service.text(v, BoostersModule.ID, "menu.confirm.crumb"), shown,
                v -> service.lines(v, BoostersModule.ID, spec.global() ? "menu.confirm.global" : "menu.confirm.personal",
                        Msg.p("type", m.typeName(spec.type())), Msg.number("percent", spec.percent()),
                        Msg.p("time", BoosterRules.time(spec.millis()))),
                player -> {
                    start(player, itemUuid);
                    service.open(player, this);
                }, player -> { });
    }

    private void start(Player player, UUID itemUuid) {
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && ItemRegistry.uuidOf(s).filter(itemUuid::equals).isPresent()) {
                m.use(player, s);
                refresh();
                return;
            }
        }
        m.msg.send(player, "moved");
        refresh();
    }

    private void thank(Player player) {
        if (m.global().ready()) {
            m.global().thank(player, false);
        }
        refresh();
    }
}
