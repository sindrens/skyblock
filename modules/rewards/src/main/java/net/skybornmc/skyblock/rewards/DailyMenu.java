package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.menu.MenuIcons;
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
 * {@code /daily}: one screen with two tabs in row 0 that switch in place (GUI_DESIGN §1.5, GUI-1 handoff: no new copy of
 * the menu, the cursor stays).
 * <ul>
 *   <li><b>Calendar</b> (PHASE-10 mockup): the days as items inside the frame (claimed = green glass ✔, today = the reward
 *       with "CLICK Claim", later days = gray glass with the reward in the lore; the stack size is the day number), the
 *       streak on 46. The content area has 28 slots, so a 30-day calendar shows a 28-day window that always holds the next
 *       day ({@link #firstShownDay}). The "Save streak" button (R5) stays hidden until the store exists.</li>
 *   <li><b>Playtime</b> (R6): active time of the profile you are on, whether it counts right now, the hourly reward, the
 *       next key and the playtime titles.</li>
 * </ul>
 * Updates every second. Bedrock: every action is a plain tap.
 */
final class DailyMenu extends Menu {

    static final int SLOT_TAB_CALENDAR = 2;
    static final int SLOT_SUMMARY = 4;
    static final int SLOT_TAB_PLAYTIME = 6;
    /** Streak: a screen-info slot of the bottom bar (GUI_DESIGN §1.1), outside the day grid. */
    static final int SLOT_STREAK = 46;
    static final int SLOT_LOADING = 22;
    /** Day slots in order: the 28 content slots inside the frame (10-16, 19-25, 28-34, 37-43). */
    static final int[] DAY_SLOTS = daySlots();

    static final int SLOT_TIME = 20;
    static final int SLOT_HOURLY = 22;
    static final int SLOT_KEY = 24;
    static final int[] TITLE_SLOTS = {28, 30, 32, 34, 29, 31, 33};

    private final RewardsModule m;
    private boolean playtime;

    DailyMenu(RewardsModule module) {
        this(module, false);
    }

    DailyMenu(RewardsModule module, boolean playtime) {
        this.m = module;
        this.playtime = playtime;
    }

    static int[] daySlots() {
        int[] out = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                out[i++] = row * 9 + col;
            }
        }
        return out;
    }

    /**
     * The first calendar day shown when only {@code slots} days fit: day 1, unless the next day to claim would fall off
     * the end, then the last {@code slots} days. Pure, unit-tested.
     */
    static int firstShownDay(int days, int next, int slots) {
        if (days <= slots || next <= slots) {
            return 1;
        }
        return days - slots + 1;
    }

    boolean playtimeTab() {
        return playtime;
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
        return playtime ? "menu.playtime.info" : "menu.daily.info";
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
        set(SLOT_TAB_CALENDAR, service.tab(viewer, new ItemStack(Material.SUNFLOWER),
                service.text(viewer, RewardsModule.ID, playtime ? "menu.tabs.calendar" : "menu.tabs.calendar-open"),
                service.lines(viewer, RewardsModule.ID, "menu.tabs.calendar-lore"), !playtime, () -> {
                    playtime = false;
                    m.daily().load(viewer);
                }));
        set(SLOT_TAB_PLAYTIME, service.tab(viewer, new ItemStack(Material.CLOCK),
                service.text(viewer, RewardsModule.ID, playtime ? "menu.tabs.playtime-open" : "menu.tabs.playtime"),
                service.lines(viewer, RewardsModule.ID, "menu.tabs.playtime-lore"), playtime, () -> playtime = true));
        if (playtime) {
            buildPlaytime();
        } else {
            buildCalendar();
        }
    }

    private void buildCalendar() {
        UUID profile = m.profiles().profileKey(viewer);
        Optional<DailyRules.State> loaded = m.daily().state(profile);
        if (loaded.isEmpty()) {
            setLoading(SLOT_LOADING);
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

        int first = firstShownDay(calendar.size(), next, DAY_SLOTS.length);
        for (int i = first - 1, slotIndex = 0; i < calendar.size() && slotIndex < DAY_SLOTS.length; i++, slotIndex++) {
            int day = i + 1;
            int slot = DAY_SLOTS[slotIndex];
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
                set(slot, item, click -> {
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
            set(slot, item);
        }
        set(SLOT_STREAK, streakItem(state, streak));
        // R5 streak save: hidden until the store exists (Phase 11); the lost streak is already stored for it.
    }

    private void buildPlaytime() {
        UUID profile = m.profiles().profileKey(viewer);
        set(SLOT_SUMMARY, service.item(Material.CLOCK, service.text(viewer, RewardsModule.ID, "menu.playtime.summary.name"),
                service.lines(viewer, RewardsModule.ID, "menu.playtime.summary.lore", Msg.number("idle", m.settings.idleMinutes),
                        Msg.p("profile", m.profiles().activeName(viewer)))));
        if (!m.settings.playtimeEnabled) {
            set(SLOT_HOURLY, service.item(Material.GRAY_DYE, viewer, RewardsModule.ID, "menu.playtime.disabled"));
            return;
        }
        Optional<PlaytimeRules.Row> view = m.playtime().view(profile);
        if (view.isEmpty()) {
            setLoading(SLOT_HOURLY);
            return;
        }
        PlaytimeRules.Row row = view.get();
        int tier = m.tier(viewer);
        long seconds = row.activeSeconds();
        String status = "menu.playtime.status." + m.playtime().status(viewer).name().toLowerCase(java.util.Locale.ROOT);
        List<Component> lore = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.playtime.time.lore",
                Msg.p("time", Format.duration(java.time.Duration.ofSeconds(seconds))), Msg.number("hours", seconds / PlaytimeRules.HOUR),
                Msg.p("next", Format.duration(java.time.Duration.ofSeconds(PlaytimeRules.untilNextHour(seconds))))));
        lore.add(m.msg.component(viewer, status, Msg.number("idle", m.settings.idleMinutes)));
        set(SLOT_TIME, service.item(Material.CLOCK, service.text(viewer, RewardsModule.ID, "menu.playtime.time.name"), lore));

        long crystals = RewardTables.crystals(m.settings.hourlyCrystals, tier, m.settings.crystalGrowth);
        List<Component> hourly = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.playtime.hourly.lore",
                Msg.p("crystals", RewardText.format(m, net.skybornmc.skyblock.common.economy.Currency.CRYSTALS, crystals))));
        if (m.settings.hourlyPassXp > 0) {
            hourly.addAll(service.lines(viewer, RewardsModule.ID, net.skybornmc.skyblock.common.pass.PassXp.of(m.core.services()).isPresent()
                    ? "menu.playtime.hourly.pass" : "menu.playtime.hourly.pass-soon", Msg.number("xp", m.settings.hourlyPassXp)));
        }
        set(SLOT_HOURLY, MenuIcons.tag(service.item(Material.PRISMARINE_CRYSTALS, service.text(viewer, RewardsModule.ID, "menu.playtime.hourly.name"),
                hourly), MenuIcons.currency("crystals")));

        if (m.settings.keyEveryHours > 0) {
            String crate = RewardText.crateName(m, m.settings.keyCrate.trim().toLowerCase(java.util.Locale.ROOT));
            set(SLOT_KEY, service.item(Material.TRIPWIRE_HOOK, service.text(viewer, RewardsModule.ID, "menu.playtime.key.name",
                    Msg.p("crate", crate)), service.lines(viewer, RewardsModule.ID, "menu.playtime.key.lore", Msg.p("crate", crate),
                    Msg.number("every", m.settings.keyEveryHours), Msg.number("left", PlaytimeRules.untilNextKey(seconds, m.settings.keyEveryHours)),
                    Msg.number("keys", row.tensPaid()))));
        }

        int i = 0;
        long hours = seconds / PlaytimeRules.HOUR;
        // GUI-4: milestones in reading order on row 4, centred (was 28, 30, 32, 34, then 29, 31, 33).
        int shownTitles = Math.min(TITLE_SLOTS.length, m.playtimeTitles().size());
        for (Map.Entry<Integer, String> t : m.playtimeTitles().entrySet()) {
            if (i >= TITLE_SLOTS.length) {
                break;
            }
            Component title = m.cosmetics().map(c -> c.display(viewer, net.skybornmc.skyblock.common.cosmetics.Cosmetics.Kind.TITLE, t.getValue())).orElse(Component.text(t.getValue()));
            boolean reached = row.titleHours() >= t.getKey();
            ItemStack item = reached
                    ? service.item(Material.NAME_TAG, service.text(viewer, RewardsModule.ID, "menu.playtime.title-reached.name", Msg.c("title", title)),
                    service.lines(viewer, RewardsModule.ID, "menu.playtime.title-reached.lore", Msg.number("hours", t.getKey())))
                    : service.item(Material.GRAY_DYE, service.text(viewer, RewardsModule.ID, "menu.playtime.title-locked.name", Msg.c("title", title)),
                    service.lines(viewer, RewardsModule.ID, "menu.playtime.title-locked.lore", Msg.number("hours", t.getKey()),
                            Msg.number("left", Math.max(0, t.getKey() - hours))));
            set(net.skybornmc.skyblock.common.menu.PickListLayout.slot(i++, shownTitles) + 18, item);
        }
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
