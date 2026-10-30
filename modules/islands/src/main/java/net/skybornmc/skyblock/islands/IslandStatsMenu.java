package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService.Snapshot;
import net.skybornmc.skyblock.common.island.IslandStatsService.Totals;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.islands.stats.ContributionShares;
import net.skybornmc.skyblock.islands.stats.StatsClock;
import net.skybornmc.skyblock.islands.stats.StatsTrend;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /is stats} (M2.6): one button per category with total, today, this week and last week and the trend against last
 * week; categories without a feature yet show "-" ("Coming soon"). Clicking a per-member category opens each member's
 * part ({@link IslandStatsMembersMenu}). One plain click each (Bedrock-safe). Reloads every 10 s while open.
 * <pre>
 *  R O B B ⛑ B B O R      4 island + today / this week
 *  B ⛏ 🪓 🌾 ▣ ▤ ⚔ ⌛ B   10-16 blocks mined, logs, crops, placed, generator, mobs, playtime
 *  B . ◆ $ 🚪 ⚑ ☠ . B   20-24 items sold, money earned, visitors, KOTH time, bosses
 *  B . . . . . . . B
 *  B . . . 👤 . . . B      40 your part (members only)
 *  ← O B ? ✘ H B O R
 * </pre>
 */
final class IslandStatsMenu extends Menu {

    static final int SLOT_HEADER = 4;
    static final int SLOT_YOU = 40;
    /** Category buttons in this order. */
    static final List<IslandStat> ORDER = List.of(IslandStat.BLOCKS_MINED, IslandStat.LOGS_CHOPPED, IslandStat.CROPS_HARVESTED,
            IslandStat.BLOCKS_PLACED, IslandStat.GENERATOR_OUTPUT, IslandStat.MOBS_KILLED, IslandStat.PLAYTIME,
            IslandStat.ITEMS_SOLD, IslandStat.MONEY_EARNED, IslandStat.VISITORS, IslandStat.KOTH_TIME, IslandStat.BOSSES_KILLED);
    static final List<Integer> SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24);
    private static final int RELOAD_TICKS = 200;

    private final IslandStats stats;
    private final IslandsModule module;
    private final Island island;
    private final boolean staff;
    private Snapshot snapshot;
    private boolean loading;

    IslandStatsMenu(IslandStats stats, Island island, Snapshot snapshot, boolean staff) {
        this.stats = stats;
        this.module = stats.module();
        this.island = island;
        this.snapshot = snapshot;
        this.staff = staff;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"), text("stats.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "stats.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    public int tickInterval() {
        return RELOAD_TICKS;
    }

    @Override
    protected void tick() {
        if (stats.islands().byId(island.id()) != island) {
            viewer.closeInventory();
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        stats.load(viewer, island, fresh -> {
            loading = false;
            if (isOpen()) {
                snapshot = fresh;
                refresh();
            }
        });
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_HEADER, MenuButton.display(header()));
        for (int i = 0; i < ORDER.size(); i++) {
            set(SLOTS.get(i), category(ORDER.get(i)));
        }
        UUID me = stats.islands().key(viewer);
        if (!staff && island.members().containsKey(me)) {
            set(SLOT_YOU, MenuButton.display(you(me)));
        }
    }

    private ItemStack header() {
        java.time.ZoneId zone = stats.tracker().clock().zone();
        DateTimeFormatter dates = module.config().dateFormatter().withZone(zone);
        TagResolver[] p = {
                Msg.p("island", stats.islands().displayName(island)),
                Msg.p("today", dates.format(Instant.ofEpochMilli(StatsClock.startOfDay(snapshot.today(), zone)))),
                Msg.p("week", dates.format(Instant.ofEpochMilli(StatsClock.startOfDay(snapshot.weekStart(), zone)))),
                Msg.p("flush", stats.config().flushSeconds)};
        return service.item(new ItemStack(Material.GOLDEN_HELMET), text("stats.header.name", p), lines("stats.header.lore", p));
    }

    private MenuButton category(IslandStat stat) {
        String name = stats.name(stat);
        ItemStack icon = new ItemStack(stats.icon(stat));
        if (!stats.shown(stat, snapshot)) {
            return MenuButton.display(service.item(icon, text("stats.item.soon-name", Msg.p("category", name)), lines("stats.item.soon-lore")));
        }
        Totals t = snapshot.totals(stat);
        TagResolver[] p = {
                Msg.p("category", name),
                Msg.p("total", IslandStats.value(stat, t.allTime())),
                Msg.p("today", IslandStats.value(stat, t.today())),
                Msg.p("week", IslandStats.value(stat, t.thisWeek())),
                Msg.p("last-week", IslandStats.value(stat, t.lastWeek()))};
        List<Component> lore = new ArrayList<>(lines("stats.item.lore", p));
        StatsTrend.Trend trend = StatsTrend.of(t.thisWeek(), t.lastWeek());
        lore.addAll(lines("stats.item.trend." + trend.kind().name().toLowerCase(java.util.Locale.ROOT), Msg.p("percent", trend.percent())));
        boolean perMember = stat.perMember();
        if (!perMember) {
            lore.addAll(lines("stats.item.island-wide"));
        }
        boolean clickable = perMember && t.allTime() > 0;
        if (clickable) {
            lore.add(Component.empty());
            lore.addAll(lines("stats.item.click"));
        }
        ItemStack item = service.item(icon, text("stats.item.name", p), lore);
        item.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        if (!clickable) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> openChild(new IslandStatsMembersMenu(stats, island, snapshot, stat)));
    }

    /** Your part of every per-member category (all time, share of the island). */
    private ItemStack you(UUID me) {
        List<Component> lore = new ArrayList<>(lines("stats.you.lore"));
        boolean any = false;
        for (IslandStat stat : ORDER) {
            if (!stat.perMember() || !stats.shown(stat, snapshot)) {
                continue;
            }
            long mine = snapshot.member(me, stat).allTime();
            if (mine <= 0) {
                continue;
            }
            Map<UUID, Long> amounts = new HashMap<>();
            snapshot.members().forEach((member, values) -> amounts.put(member, values.getOrDefault(stat, Totals.ZERO).allTime()));
            int percent = ContributionShares.percents(amounts).getOrDefault(me, 0);
            lore.addAll(lines("stats.you.line", Msg.p("category", stats.name(stat)), Msg.p("amount", IslandStats.value(stat, mine)),
                    Msg.p("percent", percent)));
            any = true;
        }
        if (!any) {
            lore.addAll(lines("stats.you.none"));
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        return service.item(head, text("stats.you.name"), lore);
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
