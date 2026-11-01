package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.pass.PassRewards;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /rewards} (spec section 5): one card per listed source (owner F3: Daily, Weekly, Monthly, Battle pass; Milestones
 * joins when that system is built), each with its state as icon and word (golden rule 8): {@code ✔ Ready}, {@code Claimed.
 * Resets in ...}, {@code ✘ Locked: ...}. A click on a ready card claims that one; Claim all (slot 38) is only shown when
 * something is ready and claims each source on its own (one failure never stops the others). Updates every second; every
 * click re-checks on the server (never trusts the open menu). Bedrock: every action is a plain tap.
 */
final class RewardsMenu extends Menu {

    static final int SLOT_DAILY = 10;
    static final int SLOT_WEEKLY = 11;
    static final int SLOT_MONTHLY = 12;
    static final int SLOT_PASS = 14;
    static final int SLOT_CLAIM_ALL = 38;
    static final int SLOT_SUMMARY = 4;

    private final RewardsModule m;

    RewardsMenu(RewardsModule module) {
        this.m = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, RewardsModule.ID, "menu.rewards.title"));
    }

    @Override
    public String messageModule() {
        return RewardsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.rewards.info";
    }

    @Override
    public String helpTopic() {
        return "daily"; // /help rewards is an alias of the daily topic
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
        navBar();
        long now = System.currentTimeMillis();
        int ready = m.service().readyCount(viewer);
        set(SLOT_SUMMARY, service.item(Material.CHEST_MINECART, service.text(viewer, RewardsModule.ID, "menu.rewards.summary.name"),
                service.lines(viewer, RewardsModule.ID, ready > 0 ? "menu.rewards.summary.ready" : "menu.rewards.summary.none",
                        Msg.number("count", ready))));
        daily(now);
        periodic(SLOT_WEEKLY, PeriodicRules.Period.WEEK, now);
        periodic(SLOT_MONTHLY, PeriodicRules.Period.MONTH, now);
        pass();
        if (ready > 0) {
            set(SLOT_CLAIM_ALL, glint(service.item(Material.LIME_DYE, service.text(viewer, RewardsModule.ID, "menu.rewards.claim-all.name"),
                    service.lines(viewer, RewardsModule.ID, "menu.rewards.claim-all.lore", Msg.number("count", ready)))),
                    click -> m.service().claimAll(viewer).thenAccept(summary -> {
                        if (summary.outcome() == net.skybornmc.skyblock.common.rewards.RewardsService.Outcome.CLAIMED) {
                            m.msg.send(viewer, "claim-all.done", Msg.c("rewards", summary.line()));
                        } else if (summary.outcome() == net.skybornmc.skyblock.common.rewards.RewardsService.Outcome.NOTHING_READY) {
                            m.msg.send(viewer, "rewards.just-reset");
                        }
                        refreshIfOpen();
                    }));
        }
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    private void daily(long now) {
        UUID profile = m.profiles().profileKey(viewer);
        Optional<DailyRules.State> state = m.daily().state(profile);
        if (!m.settings.calendarEnabled || !viewer.hasPermission(RewardsModule.PERM_DAILY)) {
            return;
        }
        if (state.isEmpty()) {
            setLoading(SLOT_DAILY);
            return;
        }
        int today = DailyRules.today(now, m.zone());
        boolean claimed = DailyRules.claimedToday(state.get(), today);
        String reset = Format.duration(DailyRules.untilReset(now, m.zone()));
        ItemStack item = card(Material.SUNFLOWER, "daily", claimed ? "claimed" : "ready", Msg.p("time", reset),
                Msg.number("day", DailyRules.nextDay(state.get(), m.calendar().size())));
        set(SLOT_DAILY, claimed ? item : glint(item), click -> {
            if (claimed) {
                m.openDaily(viewer);
            } else {
                m.daily().claim(viewer, false).thenAccept(r -> refreshIfOpen());
            }
        });
    }

    private void periodic(int slot, PeriodicRules.Period period, long now) {
        if (!m.periodic().enabled(period) || !viewer.hasPermission(RewardsModule.PERM_DAILY)) {
            return;
        }
        UUID profile = m.profiles().profileKey(viewer);
        Optional<PeriodicService.State> state = m.periodic().state(profile, period);
        if (state.isEmpty()) {
            setLoading(slot);
            m.periodic().load(viewer);
            return;
        }
        String reset = Format.duration(PeriodicRules.untilReset(period, now, m.zone()));
        String id = period.source();
        Material icon = period == PeriodicRules.Period.WEEK ? Material.CLOCK : Material.ENDER_CHEST;
        String status;
        long seconds = period == PeriodicRules.Period.WEEK ? m.periodic().weekSeconds(profile) : 0;
        if (state.get().claimed()) {
            status = "claimed";
        } else if (period == PeriodicRules.Period.WEEK && !PeriodicRules.weeklyUnlocked(seconds, m.settings.weeklyRequiredMinutes)) {
            status = "locked";
        } else {
            status = "ready";
        }
        Component contents = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(
                m.msg.component(viewer, "claim-all.separator")), m.rewards(period).stream()
                .map(spec -> RewardText.describe(m, viewer, spec, m.context(m.tier(viewer), profile))).toList());
        ItemStack item = card(status.equals("locked") ? Material.GRAY_DYE : icon, id, status, Msg.p("time", reset),
                Msg.c("contents", contents), Msg.p("played", Format.duration(Duration.ofSeconds(seconds))),
                Msg.p("needed", Format.duration(Duration.ofMinutes(m.settings.weeklyRequiredMinutes))));
        boolean ready = status.equals("ready");
        set(slot, ready ? glint(item) : item, click -> {
            if (ready) {
                m.periodic().claim(viewer, period, false).thenAccept(r -> refreshIfOpen());
            } else {
                m.sound(viewer, "error");
            }
        });
    }

    private void pass() {
        Optional<PassRewards> pass = PassRewards.of(m.core.services());
        if (pass.isEmpty()) {
            return;
        }
        int tiers = pass.get().claimable(viewer);
        ItemStack item = card(Material.NETHER_STAR, "pass", tiers > 0 ? "ready" : "none", Msg.number("count", tiers));
        set(SLOT_PASS, tiers > 0 ? glint(item) : item, click -> {
            if (tiers > 0) {
                pass.get().claimAll(viewer, true).thenAccept(n -> refreshIfOpen());
            } else {
                viewer.performCommand("pass");
            }
        });
    }

    /** A source card: name {@code menu.rewards.<id>.name}, lore {@code menu.rewards.<id>.<status>}. */
    private ItemStack card(Material icon, String id, String status, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        List<Component> lore = service.lines(viewer, RewardsModule.ID, "menu.rewards." + id + "." + status, tags);
        return service.item(icon, service.text(viewer, RewardsModule.ID, "menu.rewards." + id + ".name"), lore);
    }

    private static ItemStack glint(ItemStack item) {
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }
}
