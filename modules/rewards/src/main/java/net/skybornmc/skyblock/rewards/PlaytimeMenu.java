package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.pass.PassXp;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /daily}, Playtime tab (R6, no new command): active time of the profile you are on, whether it counts right now,
 * the hourly reward, the next key and the playtime titles. Updates every second.
 */
final class PlaytimeMenu extends Menu {

    static final int SLOT_TIME = 20;
    static final int SLOT_HOURLY = 22;
    static final int SLOT_KEY = 24;
    static final int[] TITLE_SLOTS = {28, 30, 32, 34, 29, 31, 33};

    private final RewardsModule m;

    PlaytimeMenu(RewardsModule module) {
        this.m = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, RewardsModule.ID, "menu.daily.title"),
                service.text(viewer, RewardsModule.ID, "menu.playtime.title"));
    }

    @Override
    public String messageModule() {
        return RewardsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.playtime.info";
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

    void setTab(int slot, ItemStack item, Consumer<MenuClick> click) {
        set(slot, item, click);
    }

    @Override
    protected void build() {
        frame();
        DailyMenu.tabs(this, m, viewer, false);
        UUID profile = m.profiles().profileKey(viewer);
        set(DailyMenu.SLOT_SUMMARY, service.item(Material.CLOCK, service.text(viewer, RewardsModule.ID, "menu.playtime.summary.name"),
                service.lines(viewer, RewardsModule.ID, "menu.playtime.summary.lore", Msg.number("idle", m.settings.idleMinutes),
                        Msg.p("profile", m.profiles().activeName(viewer)))));
        if (!m.settings.playtimeEnabled) {
            set(SLOT_HOURLY, service.item(Material.GRAY_DYE, viewer, RewardsModule.ID, "menu.playtime.disabled"));
            return;
        }
        Optional<PlaytimeRules.Row> view = m.playtime().view(profile);
        if (view.isEmpty()) {
            set(SLOT_HOURLY, service.item(Material.CLOCK, viewer, RewardsModule.ID, "menu.loading"));
            return;
        }
        PlaytimeRules.Row row = view.get();
        int tier = m.tier(viewer);
        long seconds = row.activeSeconds();
        String status = "menu.playtime.status." + m.playtime().status(viewer).name().toLowerCase(java.util.Locale.ROOT);
        List<Component> lore = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.playtime.time.lore",
                Msg.p("time", Format.duration(Duration.ofSeconds(seconds))), Msg.number("hours", seconds / PlaytimeRules.HOUR),
                Msg.p("next", Format.duration(Duration.ofSeconds(PlaytimeRules.untilNextHour(seconds))))));
        lore.add(m.msg.component(viewer, status, Msg.number("idle", m.settings.idleMinutes)));
        set(SLOT_TIME, service.item(Material.CLOCK, service.text(viewer, RewardsModule.ID, "menu.playtime.time.name"), lore));

        long crystals = RewardTables.crystals(m.settings.hourlyCrystals, tier, m.settings.crystalGrowth);
        List<Component> hourly = new ArrayList<>(service.lines(viewer, RewardsModule.ID, "menu.playtime.hourly.lore",
                Msg.p("crystals", RewardText.format(m, Currency.CRYSTALS, crystals))));
        if (m.settings.hourlyPassXp > 0) {
            hourly.addAll(service.lines(viewer, RewardsModule.ID, PassXp.of(m.core.services()).isPresent()
                    ? "menu.playtime.hourly.pass" : "menu.playtime.hourly.pass-soon", Msg.number("xp", m.settings.hourlyPassXp)));
        }
        set(SLOT_HOURLY, service.item(Material.PRISMARINE_CRYSTALS, service.text(viewer, RewardsModule.ID, "menu.playtime.hourly.name"),
                hourly));

        if (m.settings.keyEveryHours > 0) {
            String crate = RewardText.crateName(m, m.settings.keyCrate.trim().toLowerCase(java.util.Locale.ROOT));
            set(SLOT_KEY, service.item(Material.TRIPWIRE_HOOK, service.text(viewer, RewardsModule.ID, "menu.playtime.key.name",
                    Msg.p("crate", crate)), service.lines(viewer, RewardsModule.ID, "menu.playtime.key.lore", Msg.p("crate", crate),
                    Msg.number("every", m.settings.keyEveryHours), Msg.number("left", PlaytimeRules.untilNextKey(seconds, m.settings.keyEveryHours)),
                    Msg.number("keys", row.tensPaid()))));
        }

        int i = 0;
        long hours = seconds / PlaytimeRules.HOUR;
        for (Map.Entry<Integer, String> t : m.playtimeTitles().entrySet()) {
            if (i >= TITLE_SLOTS.length) {
                break;
            }
            Component title = m.cosmetics().map(c -> c.display(viewer, Cosmetics.Kind.TITLE, t.getValue())).orElse(Component.text(t.getValue()));
            boolean reached = row.titleHours() >= t.getKey();
            ItemStack item = reached
                    ? service.item(Material.NAME_TAG, service.text(viewer, RewardsModule.ID, "menu.playtime.title-reached.name", Msg.c("title", title)),
                    service.lines(viewer, RewardsModule.ID, "menu.playtime.title-reached.lore", Msg.number("hours", t.getKey())))
                    : service.item(Material.GRAY_DYE, service.text(viewer, RewardsModule.ID, "menu.playtime.title-locked.name", Msg.c("title", title)),
                    service.lines(viewer, RewardsModule.ID, "menu.playtime.title-locked.lore", Msg.number("hours", t.getKey()),
                            Msg.number("left", Math.max(0, t.getKey() - hours))));
            set(TITLE_SLOTS[i++], item);
        }
    }
}
