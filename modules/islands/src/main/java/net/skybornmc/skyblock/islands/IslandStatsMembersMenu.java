package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService.Snapshot;
import net.skybornmc.skyblock.common.island.IslandStatsService.Totals;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.islands.stats.ContributionShares;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each member's part of one category (M2.6): heads sorted by all-time amount with their share (whole percent, adds up to
 * 100), this week and today. Former members keep their part (REVIEW A5: what a member earned stays with the island).
 * Display only (Bedrock-safe). At most {@code menu.max-members} heads; the rest add up in one item.
 */
final class IslandStatsMembersMenu extends Menu {

    /** Inner slots of rows 2-5, in reading order. */
    static final List<Integer> SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);

    private final IslandStats stats;
    private final Island island;
    private final Snapshot snapshot;
    private final IslandStat stat;

    IslandStatsMembersMenu(IslandStats stats, Island island, Snapshot snapshot, IslandStat stat) {
        this.stats = stats;
        this.island = island;
        this.snapshot = snapshot;
        this.stat = stat;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("stats.crumb"), Component.text(stats.name(stat)));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "stats.members.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected void build() {
        frame();
        Map<UUID, Long> allTime = new HashMap<>();
        Map<UUID, Long> week = new HashMap<>();
        snapshot.members().forEach((member, values) -> {
            Totals t = values.getOrDefault(stat, Totals.ZERO);
            allTime.put(member, t.allTime());
            week.put(member, t.thisWeek());
        });
        List<ContributionShares.Share> shares = ContributionShares.of(allTime);
        Map<UUID, Integer> weekShares = ContributionShares.percents(week);
        if (shares.isEmpty()) {
            set(22, MenuButton.display(service.item(new ItemStack(Material.BARRIER), text("stats.members.empty-name"),
                    lines("stats.members.empty-lore"))));
            return;
        }
        int max = Math.min(SLOTS.size(), Math.max(1, stats.config().maxMembers));
        boolean overflow = shares.size() > max;
        int heads = overflow ? max - 1 : shares.size();
        for (int i = 0; i < heads; i++) {
            ContributionShares.Share share = shares.get(i);
            set(SLOTS.get(i), MenuButton.display(head(share, weekShares.getOrDefault(share.member(), 0), i + 1)));
        }
        if (overflow) {
            long rest = 0;
            for (int i = heads; i < shares.size(); i++) {
                rest += shares.get(i).amount();
            }
            int count = shares.size() - heads;
            set(SLOTS.get(heads), MenuButton.display(service.item(new ItemStack(Material.PAPER),
                    text("stats.members.others-name", Msg.p("count", count)),
                    lines("stats.members.others-lore", Msg.p("total", IslandStats.value(stat, rest))))));
        }
    }

    private ItemStack head(ContributionShares.Share share, int weekPercent, int place) {
        UUID member = share.member();
        Totals t = snapshot.member(member, stat);
        Role role = island.members().get(member);
        TagResolver[] p = {
                Msg.p("player", stats.islands().name(member)),
                Msg.p("place", place),
                Msg.p("total", IslandStats.value(stat, share.amount())),
                Msg.p("percent", share.percent()),
                Msg.p("week", IslandStats.value(stat, t.thisWeek())),
                Msg.p("week-percent", weekPercent),
                Msg.p("today", IslandStats.value(stat, t.today())),
                Msg.p("role", role == null ? stats.module().raw("stats.members.former") : stats.module().raw("role." + MemberStore.key(role)))};
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(stats.islands().person(member))));
        List<Component> lore = new ArrayList<>(lines("stats.members.lore", p));
        return service.item(head, text("stats.members.name", p), lore);
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
