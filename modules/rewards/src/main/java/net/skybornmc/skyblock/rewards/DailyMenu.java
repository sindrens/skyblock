package net.skybornmc.skyblock.rewards;

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
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /daily}, Calendar tab (PHASE-10 mockup): the days as items (claimed = green glass ✔, today = the reward with
 * "CLICK Claim", later days = gray glass with the reward in the lore; the stack size is the day number), the streak and
 * the tabs. Bedrock: every action is a plain tap. The "Save streak" button (R5) stays hidden until the store exists.
 */
final class DailyMenu extends Menu {

    static final int SLOT_TAB_CALENDAR = 2;
    static final int SLOT_SUMMARY = 4;
    static final int SLOT_TAB_PLAYTIME = 6;
    static final int SLOT_STREAK = 37;
    static final int SLOT_LOADING = 22;
    /** Day slots in order: rows 2-4 full, then the middle of row 5 (30 days). */
    static final int[] DAY_SLOTS = daySlots();

    private final RewardsModule m;

    DailyMenu(RewardsModule module) {
        this.m = module;
    }

    static int[] daySlots() {
        int[] out = new int[RewardTables.MAX_DAYS];
        int i = 0;
        for (int slot = 9; slot <= 35 && i < out.length; slot++) {
            out[i++] = slot;
        }
        for (int slot = 39; slot <= 41 && i < out.length; slot++) {
            out[i++] = slot;
        }
        return out;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, RewardsModule.ID, "menu.daily.title"));
    }

    @Override
    public String messageModule() {
        return RewardsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.daily.info";
    }

    @Override
    public String helpTopic() {
        return "daily";
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
        tabs(this, m, viewer, true);
        UUID profile = m.profiles().profileKey(viewer);
        Optional<DailyRules.State> loaded = m.daily().state(profile);
        if (loaded.isEmpty()) {
            set(SLOT_LOADING, service.item(Material.CLOCK, viewer, RewardsModule.ID, "menu.loading"));
            return;
        }
        DailyRules.State state = loaded.get();
        long now = System.currentTimeMillis();
        int today = DailyRules.today(now, m.zone());
        List<RewardSpec> calendar = m.calendar();
        boolean claimed = DailyRules.claimedToday(state, today);
        int upTo = DailyRules.claimedUpTo(state, today, calendar.size());
        int next = DailyRules.nextDay(state, calendar.size());
        String reset = Format.duration(DailyRules.untilReset(now, m.zone()));
        int streak = DailyRules.liveStreak(state, today);
        Payout.Context ctx = m.context(m.tier(viewer), profile);

        set(SLOT_SUMMARY, service.item(Material.SUNFLOWER, service.text(viewer, RewardsModule.ID, "menu.daily.summary.name"),
                service.lines(viewer, RewardsModule.ID, claimed ? "menu.daily.summary.claimed" : "menu.daily.summary.open",
                        Msg.number("day", next), Msg.p("time", reset), Msg.number("streak", streak),
                        Msg.p("profile", m.profiles().activeName(viewer)))));

        for (int i = 0; i < calendar.size() && i < DAY_SLOTS.length; i++) {
            int day = i + 1;
            RewardSpec spec = calendar.get(i);
            Component reward = RewardText.describe(m, viewer, spec, ctx);
            ItemStack item;
            if (day <= upTo) {
                item = service.item(Material.LIME_STAINED_GLASS_PANE, service.text(viewer, RewardsModule.ID, "menu.day.claimed.name",
                        Msg.number("day", day)), service.lines(viewer, RewardsModule.ID, "menu.day.claimed.lore", Msg.c("reward", reward)));
            } else if (day == next && !claimed) {
                item = service.item(icon(spec), service.text(viewer, RewardsModule.ID, "menu.day.today.name", Msg.number("day", day)),
                        service.lines(viewer, RewardsModule.ID, "menu.day.today.lore", Msg.c("reward", reward)));
                item.setAmount(Math.max(1, Math.min(64, day)));
                set(DAY_SLOTS[i], item, click -> {
                    m.daily().claim(click.player());
                    refresh();
                });
                continue;
            } else if (day == next) {
                item = service.item(icon(spec), service.text(viewer, RewardsModule.ID, "menu.day.tomorrow.name", Msg.number("day", day)),
                        service.lines(viewer, RewardsModule.ID, "menu.day.tomorrow.lore", Msg.c("reward", reward), Msg.p("time", reset)));
            } else {
                item = service.item(Material.GRAY_STAINED_GLASS_PANE, service.text(viewer, RewardsModule.ID, "menu.day.later.name",
                        Msg.number("day", day)), service.lines(viewer, RewardsModule.ID, "menu.day.later.lore", Msg.c("reward", reward)));
            }
            item.setAmount(Math.max(1, Math.min(64, day)));
            set(DAY_SLOTS[i], item);
        }
        set(SLOT_STREAK, streakItem(state, streak));
        // R5 streak save: hidden until the store exists (Phase 11); the lost streak is already stored for it.
    }

    private ItemStack streakItem(DailyRules.State state, int streak) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.streak.lore", Msg.number("streak", streak),
                Msg.number("best", state.best()), Msg.number("total", state.total())));
        if (m.settings.gemEvery > 0 && m.settings.gems > 0) {
            lore.addAll(service.lines(viewer, RewardsModule.ID, "menu.streak.gems", Msg.number("every", m.settings.gemEvery),
                    Msg.p("gems", RewardText.format(m, net.skybornmc.skyblock.common.economy.Currency.GEMS, m.settings.gems)),
                    Msg.number("left", DailyRules.untilGems(streak, m.settings.gemEvery))));
        }
        for (Map.Entry<Integer, String> t : m.streakTitles().entrySet()) {
            boolean owned = m.cosmetics().map(c -> c.owns(viewer.getUniqueId(), net.skybornmc.skyblock.common.cosmetics.Cosmetics.Kind.TITLE,
                    t.getValue())).orElse(false);
            Component title = m.cosmetics().map(c -> c.display(viewer, net.skybornmc.skyblock.common.cosmetics.Cosmetics.Kind.TITLE, t.getValue()))
                    .orElse(Component.text(t.getValue()));
            lore.add(m.msg.component(viewer, owned ? "menu.streak.title-owned" : "menu.streak.title", Msg.number("days", t.getKey()),
                    Msg.c("title", title)));
        }
        lore.addAll(service.lines(viewer, RewardsModule.ID, "menu.streak.footer"));
        return service.item(Material.BLAZE_POWDER, service.text(viewer, RewardsModule.ID, "menu.streak.name", Msg.number("streak", streak)),
                lore);
    }

    /** The tab buttons shared by both tabs. */
    static void tabs(Menu menu, RewardsModule m, Player viewer, boolean calendar) {
        ItemStack cal = m.core.menus().item(Material.SUNFLOWER, m.core.menus().text(viewer, RewardsModule.ID,
                calendar ? "menu.tabs.calendar-open" : "menu.tabs.calendar"), m.core.menus().lines(viewer, RewardsModule.ID, "menu.tabs.calendar-lore"));
        ItemStack play = m.core.menus().item(Material.CLOCK, m.core.menus().text(viewer, RewardsModule.ID,
                calendar ? "menu.tabs.playtime" : "menu.tabs.playtime-open"), m.core.menus().lines(viewer, RewardsModule.ID, "menu.tabs.playtime-lore"));
        if (menu instanceof DailyMenu d) {
            d.set(SLOT_TAB_CALENDAR, cal);
            d.set(SLOT_TAB_PLAYTIME, play, click -> {
                m.sound(click.player(), "click");
                PlaytimeMenu next = new PlaytimeMenu(m);
                if (d.parent() != null) {
                    next.parent(d.parent());
                }
                m.core.menus().open(click.player(), next);
            });
        } else if (menu instanceof PlaytimeMenu p) {
            p.setTab(SLOT_TAB_CALENDAR, cal, click -> {
                m.sound(click.player(), "click");
                DailyMenu next = new DailyMenu(m);
                if (p.parent() != null) {
                    next.parent(p.parent());
                }
                m.daily().load(click.player());
                m.core.menus().open(click.player(), next);
            });
            p.setTab(SLOT_TAB_PLAYTIME, play, null);
        }
    }

    /** Item of a reward kind. */
    static Material icon(RewardSpec spec) {
        return switch (spec.kind()) {
            case MONEY -> Material.GOLD_NUGGET;
            case CRYSTALS -> Material.PRISMARINE_CRYSTALS;
            case GEMS -> Material.EMERALD;
            case KEY -> Material.TRIPWIRE_HOOK;
            case FUEL -> Material.COAL;
            case BOOSTER -> switch (spec.ref()) {
                case "sell" -> Material.GOLD_INGOT;
                case "skill_xp" -> Material.EXPERIENCE_BOTTLE;
                case "tool_xp" -> Material.IRON_PICKAXE;
                default -> Material.GLOWSTONE_DUST;
            };
            case BOOK -> Material.ENCHANTED_BOOK;
            case SCROLL -> Material.PAPER;
            case TOME -> Material.BOOK;
        };
    }
}
