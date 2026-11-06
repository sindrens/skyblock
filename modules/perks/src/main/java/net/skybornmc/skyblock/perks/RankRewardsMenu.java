package net.skybornmc.skyblock.perks;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /rankrewards} (RANKS-V2 E2): 3 rows, the weekly kit on slot 11 and the monthly tag on slot 15 (each with its state
 * as word and glint), Back and Close in the bottom row. Plain taps, Bedrock-safe.
 */
final class RankRewardsMenu extends Menu {

    static final int SLOT_KIT = 11;
    static final int SLOT_MONTHLY = 15;

    private final RankRewards rewards;
    private RankRewards.Status status;

    RankRewardsMenu(RankRewards rewards, RankRewards.Status status) {
        this.rewards = rewards;
        this.status = status;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, PerksModule.ID, "rank-rewards.title");
    }

    @Override
    public String messageModule() {
        return PerksModule.ID;
    }

    private void reload() {
        rewards.status(viewer).thenAccept(s -> {
            status = s;
            if (isOpen()) {
                refresh();
            }
        });
    }

    @Override
    protected void build() {
        navBar();
        boolean kitReady = status.size() != null && !status.kitClaimed();
        ItemStack kit = service.item(Material.CHEST, viewer, PerksModule.ID, status.size() == null ? "rank-rewards.weekly.none"
                : status.kitClaimed() ? "rank-rewards.weekly.claimed" : "rank-rewards.weekly.ready",
                Msg.p("size", status.size() == null ? "-" : status.size()));
        if (kitReady) {
            kit.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        set(SLOT_KIT, kit, click -> {
            if (kitReady) {
                rewards.claimKit(viewer, this::reload);
            }
        });
        boolean monthlyReady = !status.monthlyClaimed() && status.nextCosmetic() != null;
        ItemStack monthly = service.item(Material.NAME_TAG, viewer, PerksModule.ID, status.monthlyClaimed() ? "rank-rewards.monthly.claimed"
                : status.nextCosmetic() == null ? "rank-rewards.monthly.nothing" : "rank-rewards.monthly.ready", Msg.p("month", status.month()));
        if (monthlyReady) {
            monthly.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        set(SLOT_MONTHLY, monthly, click -> {
            if (monthlyReady) {
                rewards.claimMonthly(viewer, this::reload);
            }
        });
    }
}
