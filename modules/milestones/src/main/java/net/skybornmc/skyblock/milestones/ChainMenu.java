package net.skybornmc.skyblock.milestones;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One chain's tiers (Complete Developer Spec §7.8): one tile per tier on row 2, as icon and word: {@code ✔} claimed,
 * {@code ★} ready (glows; a click claims it), {@code ▶} the current one with its progress bar, the next
 * {@code show-next-tiers} with their target, and everything after that hidden as {@code ???}. Slot 4 shows the active and
 * the next perk; island chains also show the viewer's contribution. Plain taps only (Bedrock-safe).
 */
final class ChainMenu extends Menu {

    static final int FIRST_TIER_SLOT = 20;

    private final MilestonesModule m;
    private final MilestoneModel.Chain chain;
    private MilestoneStore.Row row;
    private final ProgressBuffer.Scope scope;
    private final UUID scopeId;
    private int contribution = -1;

    ChainMenu(MilestonesModule module, MilestoneModel.Chain chain, MilestoneStore.Row row, ProgressBuffer.Scope scope, UUID scopeId) {
        this.m = module;
        this.chain = chain;
        this.row = row;
        this.scope = scope;
        this.scopeId = scopeId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, MilestonesModule.ID, "menu.chain-detail.title", Msg.p("chain", chain.name())));
    }

    @Override
    public String messageModule() {
        return MilestonesModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.chain-detail.info";
    }

    private void reload() {
        m.core().database().async(c -> {
            MilestoneStore.Row fresh = MilestoneStore.rows(c, scope, scopeId).stream().filter(r -> r.chainId().equals(chain.id()))
                    .findFirst().orElse(row);
            int share = scope == ProgressBuffer.Scope.ISLAND ? MilestoneStore.contribution(c, scopeId, chain.id(), m.claims().profileOf(viewer)) : -1;
            return new Object[]{fresh, share};
        }).whenCompleteAsync((r, error) -> {
            if (error == null) {
                row = (MilestoneStore.Row) r[0];
                contribution = (Integer) r[1];
                if (isOpen()) {
                    refresh();
                }
            }
        }, m.main());
    }

    @Override
    protected void build() {
        navBar();
        if (contribution == -1 && scope == ProgressBuffer.Scope.ISLAND) {
            contribution = -2; // asked once per open
            reload();
        }
        long total = row.total() + m.engine().pending(scope, scopeId, chain.id());
        int completed = Math.min(row.highestTier(), chain.maxTier());
        int claimed = row.claimedTier();
        int shownUpTo = completed + 1 + Math.max(0, m.config().showNextTiers);
        set(SLOT_SUMMARY, service.item(MilestonesMenu.icon(chain), viewer, MilestonesModule.ID,
                scope == ProgressBuffer.Scope.ISLAND && contribution >= 0 ? "menu.chain-detail.summary-island" : "menu.chain-detail.summary",
                Msg.p("chain", chain.name()), Msg.p("progress", Format.number(total)), Msg.c("perk", MilestonesMenu.perkText(service, viewer, chain, claimed)),
                Msg.c("next-perk", MilestonesMenu.perkText(service, viewer, chain, Math.max(claimed, completed) + 1)), Msg.number("percent", Math.max(0, contribution))));
        for (int tier = 1; tier <= chain.maxTier() && tier <= 5; tier++) {
            int slot = FIRST_TIER_SLOT + (tier - 1);
            long target = chain.target(tier);
            ItemStack tile;
            if (tier <= claimed) {
                tile = service.item(Material.LIME_DYE, viewer, MilestonesModule.ID, "menu.tier.claimed",
                        Msg.number("tier", tier), Msg.c("perk", MilestonesMenu.perkText(service, viewer, chain, tier)));
                set(slot, tile);
            } else if (tier <= completed) {
                tile = service.item(MilestonesMenu.icon(chain), viewer, MilestonesModule.ID, "menu.tier.ready",
                        Msg.number("tier", tier), Msg.c("perk", MilestonesMenu.perkText(service, viewer, chain, tier)));
                tile.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                int t = tier;
                set(slot, tile, click -> m.claims().claim(viewer, chain, t).thenAccept(o -> reload()));
            } else if (tier == completed + 1) {
                tile = service.item(Material.YELLOW_DYE, viewer, MilestonesModule.ID, "menu.tier.current",
                        Msg.number("tier", tier), Msg.p("progress", Format.number(total)), Msg.p("target", Format.number(target)),
                        Msg.p("bar", MilestonesMenu.bar(total, chain.target(tier - 1), target)), Msg.c("perk", MilestonesMenu.perkText(service, viewer, chain, tier)));
                set(slot, tile);
            } else if (tier <= shownUpTo) {
                tile = service.item(Material.GRAY_DYE, viewer, MilestonesModule.ID, "menu.tier.next",
                        Msg.number("tier", tier), Msg.p("target", Format.number(target)), Msg.c("perk", MilestonesMenu.perkText(service, viewer, chain, tier)));
                set(slot, tile);
            } else {
                set(slot, service.item(Material.GRAY_DYE, viewer, MilestonesModule.ID, "menu.tier.hidden", Msg.number("tier", tier)));
            }
        }
    }
}
