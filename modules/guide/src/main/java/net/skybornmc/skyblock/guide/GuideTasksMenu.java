package net.skybornmc.skyblock.guide;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kindle's quests (story quest + errands) or his finds (trades). Each entry: what he asks (with how many you carry), what
 * he gives, and one click to hand in. Bedrock-safe: everything is in the lore, one click does one thing.
 * <pre>
 *  . . . . I . . . .      4 info (story progress / "new every few hours")
 *  . . . . S . . . .      13 the story quest (quests view)
 *  . . X . X . X . .      29-33 errands or finds (centred)
 *  nav bar
 * </pre>
 */
final class GuideTasksMenu extends Menu {

    static final int SLOT_INFO = 4;
    static final int SLOT_STORY = 13;
    static final int[] OFFER_SLOTS = {29, 31, 33, 30, 32, 28, 34};

    private final GuideModule m;
    private final UUID island;
    private final boolean trades;

    GuideTasksMenu(GuideModule m, UUID island, boolean trades) {
        this.m = m;
        this.island = island;
        this.trades = trades;
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"), text(trades ? "tasks.crumb-trades" : "tasks.crumb-quests"));
    }

    @Override
    public String messageModule() {
        return GuideModule.ID;
    }

    @Override
    public String infoKey() {
        return trades ? "tasks.info-trades" : "tasks.info-quests";
    }

    @Override
    public String helpTopic() {
        return "islands";
    }

    @Override
    protected void build() {
        navBar();
        GuideQuests q = m.quests();
        UUID profile = q.profile(viewer);
        long period = q.period();
        if (!trades) {
            int total = q.catalog().all(GuideCatalog.Kind.STORY).size();
            Set<String> done = q.storyDone(island, this::refreshIfOpen);
            set(SLOT_INFO, service.item(Material.BLAZE_POWDER, viewer, GuideModule.ID, "tasks.story-info",
                    Msg.number("done", done.size()), Msg.number("total", total)));
            GuideCatalog.Entry current = q.currentStory(island, this::refreshIfOpen);
            if (!q.storyLoaded(island)) {
                set(SLOT_STORY, service.item(Material.CLOCK, viewer, GuideModule.ID, "tasks.loading"));
            } else if (current == null) {
                set(SLOT_STORY, service.item(Material.NETHER_STAR, viewer, GuideModule.ID, "tasks.story-all-done"));
            } else {
                set(SLOT_STORY, entryItem(current, false, true), click -> handIn(click.player(), current));
            }
        } else {
            set(SLOT_INFO, service.item(Material.BUNDLE, viewer, GuideModule.ID, "tasks.trades-info"));
        }
        GuideCatalog.Kind kind = trades ? GuideCatalog.Kind.TRADE : GuideCatalog.Kind.ERRAND;
        Set<String> done = q.rotationDone(profile, kind, period, this::refreshIfOpen);
        List<GuideCatalog.Entry> offers = q.offers(viewer, island, kind);
        int n = Math.min(offers.size(), OFFER_SLOTS.length);
        int[] slots = java.util.Arrays.copyOf(OFFER_SLOTS, n);
        java.util.Arrays.sort(slots);
        for (int i = 0; i < n; i++) {
            GuideCatalog.Entry e = offers.get(i);
            boolean isDone = done.contains(e.id());
            if (isDone) {
                set(slots[i], entryItem(e, true, false));
            } else {
                set(slots[i], entryItem(e, false, false), click -> handIn(click.player(), e));
            }
        }
        if (offers.isEmpty()) {
            set(31, service.item(Material.GRAY_DYE, viewer, GuideModule.ID, "tasks.none"));
        }
    }

    private ItemStack entryItem(GuideCatalog.Entry e, boolean done, boolean story) {
        Material icon = Material.matchMaterial(e.cost().keySet().iterator().next());
        List<Component> lore = new ArrayList<>();
        lore.addAll(service.lines(viewer, GuideModule.ID, trades ? "tasks.price-header" : "tasks.ask-header"));
        boolean all = true;
        for (Map.Entry<String, Integer> c : e.cost().entrySet()) {
            Material mat = Material.matchMaterial(c.getKey());
            int have = mat == null ? 0 : GuideQuests.count(viewer.getInventory(), mat);
            boolean ok = have >= c.getValue();
            all &= ok;
            lore.add(m.msg().component(viewer, ok ? "tasks.cost-ok" : "tasks.cost-missing", Msg.p("item", pretty(c.getKey())),
                    Msg.number("amount", c.getValue()), Msg.number("have", Math.min(have, 99_999))));
        }
        lore.addAll(service.lines(viewer, GuideModule.ID, "tasks.reward-header"));
        for (GuideCatalog.Reward r : e.rewards()) {
            lore.add(rewardLine(r));
        }
        lore.add(Component.empty());
        String state = done ? "tasks.state-done" : all ? (trades ? "tasks.state-buy" : "tasks.state-ready") : "tasks.state-missing";
        lore.addAll(service.lines(viewer, GuideModule.ID, state));
        String nameKey = story ? "tasks.story-name" : trades ? "tasks.trade-name" : "tasks.errand-name";
        Component name = m.msg().component(viewer, nameKey, Msg.p("item", pretty(e.cost().keySet().iterator().next())),
                Msg.number("amount", e.cost().values().iterator().next()), Msg.p("reward", rewardPlain(e.rewards().getFirst())));
        ItemStack base = new ItemStack(done ? Material.LIME_STAINED_GLASS_PANE : icon == null ? Material.PAPER : icon);
        return service.item(base, name, lore);
    }

    private Component rewardLine(GuideCatalog.Reward r) {
        return switch (r.type()) {
            case MONEY -> m.msg().component(viewer, "tasks.reward-money", Msg.p("amount", Format.money(r.amount())));
            case CRYSTALS -> m.msg().component(viewer, "tasks.reward-crystals", Msg.number("amount", r.amount()));
            case ITEM -> m.msg().component(viewer, "tasks.reward-item", Msg.p("item", pretty(r.what())), Msg.number("amount", r.amount()));
            case KEY -> m.msg().component(viewer, "tasks.reward-key", Msg.p("crate", pretty(r.what())), Msg.number("amount", r.amount()));
        };
    }

    private static String rewardPlain(GuideCatalog.Reward r) {
        return switch (r.type()) {
            case MONEY -> Format.money(r.amount());
            case CRYSTALS -> Format.number(r.amount()) + " crystals";
            case ITEM -> r.amount() + " " + pretty(r.what());
            case KEY -> r.amount() + " " + pretty(r.what()) + " key";
        };
    }

    /** {@code melon_slice} → {@code Melon Slice}. */
    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            if (!w.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    private void handIn(Player p, GuideCatalog.Entry e) {
        m.quests().handIn(p, island, e, result -> {
            switch (result) {
                case OK -> {
                    m.msg().send(p, trades ? "tasks.bought" : "tasks.handed-in", Msg.p("reward", rewardPlain(e.rewards().getFirst())));
                    m.core().sounds().play(p, "success");
                }
                case MISSING -> {
                    m.msg().send(p, "tasks.missing");
                    m.core().sounds().play(p, "error");
                }
                case DONE -> m.msg().send(p, "tasks.already");
                case LOCKED -> m.msg().send(p, "tasks.locked", Msg.number("level", e.level()));
                case BUSY -> { }
                case ERROR -> {
                    m.msg().send(p, "tasks.error");
                    m.core().sounds().play(p, "error");
                }
            }
            refreshIfOpen();
        });
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    private Component text(String key) {
        return service.text(viewer, GuideModule.ID, key);
    }
}
