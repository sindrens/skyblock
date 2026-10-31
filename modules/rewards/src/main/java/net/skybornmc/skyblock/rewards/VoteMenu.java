package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListLayout;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /vote} (PHASE-10 M10.5, RX42): one button per vote site (today's ✔/✘ and when the next rewarded vote is
 * possible; a click gives the link: clickable in chat on Java, a popup with the address on Bedrock, GUI-5), the vote page
 * with every site, your vote streak and the vote party progress. Every action is a plain click or tap; updates every
 * second.
 */
final class VoteMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_STREAK = 29;
    static final int SLOT_PAGE = 31;
    static final int SLOT_PARTY = 33;
    /** Sites: row 2, centred (up to 7). */
    static final int SITE_ROW_OFFSET = 9;
    static final int MAX_SITES = 7;

    private final RewardsModule m;

    VoteMenu(RewardsModule module) {
        this.m = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, RewardsModule.ID, "menu.vote.title"));
    }

    @Override
    public String messageModule() {
        return RewardsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.vote.info";
    }

    @Override
    public String helpTopic() {
        return "vote";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        refresh();
    }

    /** The slot of site {@code index} of {@code shown}: row 2, centred. Pure, unit-tested. */
    static int siteSlot(int index, int shown) {
        return PickListLayout.slot(index, Math.min(MAX_SITES, shown)) + SITE_ROW_OFFSET;
    }

    @Override
    protected void build() {
        frame();
        VoteService votes = m.votes();
        UUID account = viewer.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = votes.cooldownMillis();
        List<VoteRules.Site> sites = votes.sites();
        Map<String, Long> last = votes.lastPaid(account);
        boolean loaded = votes.loaded(account);
        int today = votes.today();
        VoteRules.Streak streak = votes.streak(account);
        int live = VoteRules.liveStreak(streak, today);

        int open = 0;
        int shown = Math.min(MAX_SITES, sites.size());
        Component reward = rewardLine();
        for (int i = 0; i < shown; i++) {
            VoteRules.Site site = sites.get(i);
            long wait = VoteRules.untilNext(last.getOrDefault(site.id(), 0L), now, cooldown);
            boolean done = loaded && wait > 0;
            if (!done) {
                open++;
            }
            ItemStack item = done
                    ? service.item(Material.LIME_STAINED_GLASS_PANE, service.text(viewer, RewardsModule.ID, "menu.vote.site.done.name",
                    Msg.p("site", site.name())), service.lines(viewer, RewardsModule.ID, "menu.vote.site.done.lore", Msg.c("reward", reward),
                    Msg.p("time", Format.duration(Duration.ofMillis(wait)))))
                    : service.item(Material.PAPER, service.text(viewer, RewardsModule.ID, "menu.vote.site.open.name", Msg.p("site", site.name())),
                    service.lines(viewer, RewardsModule.ID, m.bedrock(viewer) ? "menu.vote.site.open.lore-bedrock" : "menu.vote.site.open.lore",
                            Msg.c("reward", reward)));
            set(siteSlot(i, shown), item, click -> link(click.player(), site.name(), site.url(), "site"));
        }

        set(SLOT_SUMMARY, service.item(Material.TRIPWIRE_HOOK, service.text(viewer, RewardsModule.ID, "menu.vote.summary.name"),
                service.lines(viewer, RewardsModule.ID, "menu.vote.summary.lore", Msg.number("done", shown - open), Msg.number("sites", shown),
                        Msg.number("streak", live), Msg.p("profile", m.profiles().activeName(viewer)),
                        Msg.number("waiting", votes.queued(account)))));
        set(SLOT_PAGE, service.item(Material.BOOK, service.text(viewer, RewardsModule.ID, "menu.vote.page.name"),
                service.lines(viewer, RewardsModule.ID, "menu.vote.page.lore", Msg.p("url", m.votesSettings.pageUrl))),
                click -> link(click.player(), m.raw("menu.vote.page.label", "Vote page"), m.votesSettings.pageUrl, "page"));
        set(SLOT_STREAK, streakItem(streak, live, today));
        if (m.votesSettings.partyEnabled) {
            set(SLOT_PARTY, partyItem(VoteRules.votedOn(streak, today)));
        }
    }

    private Component rewardLine() {
        VotesSettings s = m.votesSettings;
        List<Component> parts = new ArrayList<>();
        if (s.keys > 0) {
            parts.add(m.msg.component(viewer, "reward.key", Msg.number("amount", s.keys),
                    Msg.p("crate", RewardText.crateName(m, s.keyCrate.strip().toLowerCase(java.util.Locale.ROOT)))));
        }
        if (s.credits > 0) {
            parts.add(m.msg.component(viewer, "reward.credits", Msg.p("amount", RewardText.format(m, Currency.CREDITS_EARNED, s.credits))));
        }
        if (s.passXp > 0 && m.core.services().has(net.skybornmc.skyblock.common.pass.PassXp.class)) {
            parts.add(m.msg.component(viewer, "reward.pass-xp", Msg.number("amount", s.passXp)));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(m.msg.component(viewer, "claim-all.separator")), parts);
    }

    private ItemStack streakItem(VoteRules.Streak streak, int live, int today) {
        VotesSettings s = m.votesSettings;
        List<Component> lore = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.vote.streak.lore", Msg.number("streak", live),
                Msg.number("best", streak.best())));
        lore.add(m.msg.component(viewer, VoteRules.votedOn(streak, today) ? "menu.vote.streak.today-yes" : "menu.vote.streak.today-no"));
        Optional<int[]> next = VoteRules.nextStreakReward(live, m.votes().streakRewards(), s.cycleDays);
        if (next.isPresent()) {
            String crate = m.votes().streakRewards().get(next.get()[1]);
            lore.add(m.msg.component(viewer, "menu.vote.streak.next", Msg.number("left", next.get()[0]),
                    Msg.p("crate", RewardText.crateName(m, crate))));
        }
        lore.add(Component.empty());
        int cycleDay = VoteRules.cycleDay(live, s.cycleDays);
        for (Map.Entry<Integer, String> e : m.votes().streakRewards().entrySet()) {
            boolean reached = live > 0 && cycleDay >= e.getKey();
            lore.add(m.msg.component(viewer, reached ? "menu.vote.streak.reward-reached" : "menu.vote.streak.reward",
                    Msg.number("day", e.getKey()), Msg.p("crate", RewardText.crateName(m, e.getValue()))));
        }
        lore.addAll(service.lines(viewer, RewardsModule.ID, "menu.vote.streak.footer"));
        return service.item(Material.BLAZE_POWDER, service.text(viewer, RewardsModule.ID, "menu.vote.streak.name", Msg.number("streak", live)),
                lore);
    }

    private ItemStack partyItem(boolean votedToday) {
        VotesSettings s = m.votesSettings;
        Optional<VoteStore.Party> party = m.votes().party();
        if (party.isEmpty()) {
            return service.loadingItem(viewer).item();
        }
        int count = party.get().counter();
        List<Component> lore = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.vote.party.lore", Msg.number("count", count),
                Msg.number("size", s.partySize), Msg.number("left", Math.max(0, s.partySize - count)),
                Msg.p("crate", RewardText.crateName(m, s.partyCrate.strip().toLowerCase(java.util.Locale.ROOT))),
                Msg.number("parties", party.get().total())));
        lore.add(m.votes().bar(viewer, count, s.partySize));
        lore.add(Component.empty());
        lore.add(m.msg.component(viewer, votedToday ? "menu.vote.party.you-in" : "menu.vote.party.you-out"));
        return service.item(Material.CAKE, service.text(viewer, RewardsModule.ID, "menu.vote.party.name"), lore);
    }

    /** Closes the menu and gives the link: a clickable chat line (Java) and a popup with the address (Bedrock, GUI-5). */
    private void link(Player p, String name, String url, String kind) {
        m.sound(p, "click");
        service.close(p);
        m.sendLink(p, name, url, kind);
    }
}
