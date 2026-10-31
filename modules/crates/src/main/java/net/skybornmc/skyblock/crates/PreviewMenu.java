package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One crate's rewards: every reward as its item with the V2 line {@code ★ Name (xN) | chance%} (★ = top band, chance =
 * its real share), your keys and pity in slot 4, and Open 1 / Open 10 / Open all buttons (Bedrock-safe: one tap each).
 * Money and crystal pouches show what they pay at your tier. M10.4: Withdraw 1 / Withdraw an amount turn tradeable keys
 * into key items.
 */
final class PreviewMenu extends Menu {

    static final int SLOT_STATUS = 4;
    static final int SLOT_OPEN_ONE = 38;
    static final int SLOT_OPEN_BULK = 40;
    static final int SLOT_OPEN_ALL = 42;
    /** M10.4: withdraw 1 tradeable key as a key item. */
    static final int SLOT_WITHDRAW_ONE = 37;
    /** M10.4: withdraw a chosen amount (number picker, Bedrock-safe). */
    static final int SLOT_WITHDRAW_AMOUNT = 43;
    static final int[] REWARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final CratesModule m;
    private final String crateId;
    private boolean loading;

    PreviewMenu(CratesModule module, CrateCatalog.Crate crate) {
        this.m = module;
        this.crateId = crate.id();
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        CrateCatalog.Crate crate = m.catalog().crate(crateId).orElse(null);
        Component name = crate == null ? Component.text(crateId) : m.mini(viewer, m.raw("crates." + crateId + ".name", crateId));
        return service.title(viewer, service.text(viewer, CratesModule.ID, "menu.title"), name);
    }

    @Override
    public String messageModule() {
        return CratesModule.ID;
    }

    @Override
    public String infoKey() {
        return "preview.info";
    }

    @Override
    public String helpTopic() {
        return "crates";
    }

    @Override
    protected void build() {
        frame();
        CrateCatalog.Crate crate = m.catalog().crate(crateId).orElse(null);
        if (crate == null || crate.hidden()) {
            set(22, service.item(Material.COBWEB, viewer, CratesModule.ID, "preview.gone"));
            return;
        }
        int tier = m.tier(viewer);
        List<CrateCatalog.Reward> rewards = crate.rewards();
        for (int i = 0; i < rewards.size() && i < REWARD_SLOTS.length; i++) {
            set(REWARD_SLOTS[i], rewardItem(crate, rewards.get(i), tier));
        }
        UUID profile = m.profiles().profileKey(viewer);
        CratesModule.ProfileView view = m.view(profile);
        if (view == null) {
            set(SLOT_STATUS, service.item(Material.CLOCK, viewer, CratesModule.ID, "menu.loading"));
            if (!loading) {
                loading = true;
                m.loadView(profile).whenComplete((v, e) -> refresh());
            }
            return;
        }
        CrateStore.Keys keys = view.keys(crateId);
        int counter = view.counter(crateId, m.season());
        String statusKey = crate.pity() > 0 && crate.hasTop() ? "preview.status" : "preview.status-no-pity";
        set(SLOT_STATUS, service.item(m.icon(crate), m.mini(viewer, m.crateMini(crate)), service.lines(viewer, CratesModule.ID,
                statusKey, Msg.number("keys", keys.total()), Msg.number("tradeable", keys.tradeable()), Msg.number("pity", counter),
                Msg.number("max", crate.pity()), Msg.p("top", CrateMath.percent(crate.topPercent())))));
        long all = Math.min(keys.total(), m.settings.maxPerClick);
        boolean has = keys.total() > 0;
        Material button = has ? Material.LIME_DYE : Material.GRAY_DYE;
        set(SLOT_OPEN_ONE, service.item(button, viewer, CratesModule.ID, has ? "preview.open-one" : "preview.no-keys",
                Msg.mm("key", m.keyMini(crateId))), click -> open(click.player(), 1));
        set(SLOT_OPEN_BULK, service.item(button, viewer, CratesModule.ID, has ? "preview.open-bulk" : "preview.no-keys",
                Msg.number("bulk", m.settings.bulkAmount), Msg.mm("key", m.keyMini(crateId))),
                click -> open(click.player(), m.settings.bulkAmount));
        set(SLOT_OPEN_ALL, service.item(button, viewer, CratesModule.ID, has ? "preview.open-all" : "preview.no-keys",
                Msg.number("all", Math.max(0, all)), Msg.number("max", m.settings.maxPerClick), Msg.mm("key", m.keyMini(crateId))),
                click -> open(click.player(), m.settings.maxPerClick));
        if (m.keyItems().enabled()) {
            withdrawButtons(crate, keys);
        }
    }

    /** M10.4: Withdraw 1 and Withdraw an amount (only tradeable keys; bound keys stay on the profile). */
    private void withdrawButtons(CrateCatalog.Crate crate, CrateStore.Keys keys) {
        if (keys.tradeable() < 1) {
            set(SLOT_WITHDRAW_ONE, service.item(Material.GRAY_DYE, viewer, CratesModule.ID, "preview.withdraw-none",
                    Msg.mm("key", m.keyMini(crateId))));
            return;
        }
        int max = (int) Math.min(keys.tradeable(), m.settings.keyItemMaxPerWithdraw);
        set(SLOT_WITHDRAW_ONE, service.item(Material.TRIPWIRE_HOOK, viewer, CratesModule.ID, "preview.withdraw-one",
                Msg.mm("key", m.keyMini(crateId)), Msg.number("tradeable", keys.tradeable())),
                click -> m.keyItems().withdraw(click.player(), crate, 1, this::refreshIfOpen));
        set(SLOT_WITHDRAW_AMOUNT, service.item(Material.NAME_TAG, viewer, CratesModule.ID, "preview.withdraw-amount",
                Msg.mm("key", m.keyMini(crateId)), Msg.number("tradeable", keys.tradeable()), Msg.number("max", max)),
                click -> openChild(new NumberPickerMenu(p -> service.text(p, CratesModule.ID, "preview.withdraw-title",
                        Msg.mm("key", m.keyMini(crateId))), 1, max, 1, new long[]{1, 10, 64},
                        (p, amount) -> m.keyItems().withdraw(p, crate, amount, () -> reopen(p)))));
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    /** After a withdraw from the number picker (it closed itself): back to this preview when nothing else is open. */
    private void reopen(Player player) {
        if (player.isOnline() && m.core.menus().openMenu(player) == null && m.problem(player).isEmpty()) {
            m.core.menus().open(player, this);
        }
    }

    private ItemStack rewardItem(CrateCatalog.Crate crate, CrateCatalog.Reward reward, int tier) {
        String name = m.rewardMini(crate, reward, m.previewAmount(reward, tier));
        List<Component> lore = new ArrayList<>();
        lore.add(m.msg.component(viewer, reward.top() ? "preview.line-top" : "preview.line", Msg.mm("reward", name),
                Msg.number("count", CratesModule.count(reward)), Msg.p("chance", CrateMath.percent(crate.percent(reward)))));
        if (reward.type() == RewardType.COMING_SOON) {
            lore.addAll(service.lines(viewer, CratesModule.ID, "preview.coming-soon", Msg.p("amount",
                    m.format(Currency.CRYSTALS, CrateMath.crystals(reward.fallback(), tier, m.settings.crystalGrowth)))));
        } else if (reward.type().cosmetic()) {
            lore.addAll(service.lines(viewer, CratesModule.ID, "preview.cosmetic"));
        } else if (reward.type() == RewardType.MONEY || reward.type() == RewardType.CRYSTALS) {
            lore.addAll(service.lines(viewer, CratesModule.ID, "preview.pouch"));
        }
        return service.item(new ItemStack(m.icon(reward)), m.mini(viewer, name), lore);
    }

    private void open(Player player, int amount) {
        CrateCatalog.Crate crate = m.catalog().crate(crateId).orElse(null);
        if (crate == null) {
            refresh();
            return;
        }
        m.opener().open(player, crate, amount, plan -> {
            if (plan != null && plan.grants().size() > 1 && isOpen()) {
                m.opener().showResult(player, plan, this);
            } else {
                refresh();
            }
        }, CrateShow.Source.menu(this));
    }
}
