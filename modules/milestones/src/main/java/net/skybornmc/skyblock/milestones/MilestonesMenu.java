package net.skybornmc.skyblock.milestones;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code /milestones} ({@code /ms}), Complete Developer Spec §7.2 and §7.8. Without a menu kind: the overview with the two
 * big buttons Island and Personal. With a kind: the discovered chains of that scope (a chain appears after the first
 * matching action), 21 per page, each with its tier, a progress bar and its state as icon and word; a ready tier glows and
 * a click claims it, otherwise a click opens the chain's tiers ({@link ChainMenu}). Bedrock: plain taps only.
 */
final class MilestonesMenu extends Menu {

    static final int SLOT_ISLAND = 20;
    static final int SLOT_PERSONAL = 24;
    /** F13a: the milestone leaderboard, between the two big buttons. */
    static final int SLOT_TOP = 22;
    static final int[] CHAIN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final MilestonesModule m;
    private final MilestoneModel.Menu kind;
    private List<MilestoneStore.Row> rows;
    private boolean loading;
    private int page;

    MilestonesMenu(MilestonesModule module, MilestoneModel.Menu kind) {
        this.m = module;
        this.kind = kind;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        String key = kind == null ? "menu.title" : kind == MilestoneModel.Menu.ISLAND ? "menu.island.title" : "menu.personal.title";
        return service.title(viewer, service.text(viewer, MilestonesModule.ID, key));
    }

    @Override
    public String messageModule() {
        return MilestonesModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    /** Scope of this list (island id or profile key); null = no island for the island list. */
    UUID scopeId() {
        return kind == MilestoneModel.Menu.ISLAND ? m.claims().islandOf(viewer) : m.claims().profileOf(viewer);
    }

    ProgressBuffer.Scope scope() {
        return kind == MilestoneModel.Menu.ISLAND ? ProgressBuffer.Scope.ISLAND : ProgressBuffer.Scope.PROFILE;
    }

    /** Reads the scope's rows again (after a claim, and when opened). */
    void reload() {
        UUID id = scopeId();
        if (id == null || loading) {
            return;
        }
        loading = true;
        ProgressBuffer.Scope scope = scope();
        m.core().database().async(c -> MilestoneStore.rows(c, scope, id)).whenCompleteAsync((r, error) -> {
            loading = false;
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: could not load " + scope + " " + id, Database.cause(error));
                return;
            }
            rows = r;
            if (isOpen()) {
                refresh();
            }
        }, m.main());
    }

    @Override
    protected void build() {
        navBar();
        if (kind == null) {
            buildOverview();
            return;
        }
        UUID id = scopeId();
        if (id == null) {
            setEmpty(MilestonesModule.ID, "menu.no-island");
            return;
        }
        if (rows == null) {
            setLoading(22);
            reload();
            return;
        }
        List<MilestoneStore.Row> shown = new ArrayList<>();
        for (MilestoneStore.Row r : rows) {
            if (m.catalog().chain(r.chainId()).filter(c -> c.menu() == kind).isPresent()) {
                shown.add(r);
            }
        }
        if (shown.isEmpty()) {
            setEmpty(MilestonesModule.ID, kind == MilestoneModel.Menu.ISLAND ? "menu.island.empty" : "menu.personal.empty");
            return;
        }
        int pages = (shown.size() + CHAIN_SLOTS.length - 1) / CHAIN_SLOTS.length;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < CHAIN_SLOTS.length; i++) {
            int index = page * CHAIN_SLOTS.length + i;
            if (index >= shown.size()) {
                break;
            }
            MilestoneStore.Row row = shown.get(index);
            MilestoneModel.Chain chain = m.catalog().chain(row.chainId()).orElseThrow();
            set(CHAIN_SLOTS[i], chainItem(chain, row), click -> {
                int ready = readyTier(chain, row);
                if (ready > 0) {
                    m.claims().claim(viewer, chain, ready).thenAccept(o -> reload());
                } else {
                    openChild(new ChainMenu(m, chain, row, scope(), scopeId()));
                }
            });
        }
        if (page > 0) {
            set(SLOT_PREVIOUS, service.pageItem(viewer, false, page, pages), click -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, service.pageItem(viewer, true, page + 2, pages), click -> {
                page++;
                refresh();
            });
        }
    }

    private void buildOverview() {
        set(SLOT_ISLAND, service.item(Material.GRASS_BLOCK, viewer, MilestonesModule.ID, "menu.overview.island"),
                click -> openChild(new MilestonesMenu(m, MilestoneModel.Menu.ISLAND)));
        set(SLOT_PERSONAL, service.item(Material.DEEPSLATE_DIAMOND_ORE, viewer, MilestonesModule.ID, "menu.overview.personal"),
                click -> openChild(new MilestonesMenu(m, MilestoneModel.Menu.PERSONAL)));
        set(SLOT_TOP, service.item(Material.GOLDEN_HELMET, viewer, MilestonesModule.ID, "menu.overview.top"),
                click -> openChild(new MilestoneTopMenu(m, true)));
    }

    /** The highest completed tier not claimed yet (0 = nothing to claim). */
    static int readyTier(MilestoneModel.Chain chain, MilestoneStore.Row row) {
        return row.highestTier() > row.claimedTier() ? Math.min(row.highestTier(), chain.maxTier()) : 0;
    }

    private ItemStack chainItem(MilestoneModel.Chain chain, MilestoneStore.Row row) {
        long total = row.total() + m.engine().pending(scope(), scopeId(), chain.id());
        int tier = Math.min(row.highestTier(), chain.maxTier());
        int ready = readyTier(chain, row);
        long next = chain.target(tier + 1);
        List<TagResolver> tags = new ArrayList<>(List.of(Msg.p("chain", chain.name()), Msg.number("tier", tier),
                Msg.number("max", chain.maxTier()), Msg.p("progress", Format.number(total)),
                Msg.p("target", next < 0 ? "" : Format.number(next)), Msg.p("bar", bar(total, chain.target(tier), next)),
                Msg.number("percent", next < 0 ? 100 : percent(total, chain.target(tier), next))));
        String state = ready > 0 ? "ready" : next < 0 ? "maxed" : "progress";
        tags.add(Msg.c("perk", perkText(chain, row.claimedTier())));
        tags.add(Msg.c("next-perk", perkText(chain, tier + 1)));
        ItemStack item = service.item(icon(chain), viewer, MilestonesModule.ID, "menu.chain." + state, tags.toArray(TagResolver[]::new));
        if (ready > 0) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    static Material icon(MilestoneModel.Chain chain) {
        Material mat = Material.matchMaterial(chain.icon());
        return mat != null && mat.isItem() && !mat.isAir() ? mat : Material.PAPER;
    }

    /** Progress inside the current tier step, in percent. */
    static int percent(long total, long from, long to) {
        long start = Math.max(0, from);
        if (to <= start) {
            return 100;
        }
        return (int) Math.max(0, Math.min(100, (total - start) * 100 / (to - start)));
    }

    /** Spec §7.7 {@code progress-bar}: 10 cells, filled up to the share of the current step. */
    static String bar(long total, long from, long to) {
        int filled = to < 0 ? 10 : percent(total, from, to) / 10;
        return "█".repeat(filled) + "░".repeat(10 - filled);
    }

    /** Plain-language perks of one tier ("Extra Wheat drops +6%"), joined; empty text when the tier has none. */
    Component perkText(MilestoneModel.Chain chain, int tier) {
        return perkText(service, viewer, chain, tier);
    }

    static Component perkText(net.skybornmc.skyblock.common.menu.MenuService service, org.bukkit.entity.Player viewer,
                              MilestoneModel.Chain chain, int tier) {
        if (tier < 1 || tier > chain.maxTier()) {
            return service.text(viewer, MilestonesModule.ID, "perk.none");
        }
        List<Component> parts = new ArrayList<>();
        for (MilestoneModel.Perk p : chain.perksOf(tier)) {
            parts.add(service.text(viewer, MilestonesModule.ID, "perk." + p.family(), Msg.p("value", value(p.value())),
                    Msg.p("target", pretty(Optional.ofNullable(p.target()).orElse("")))));
        }
        return parts.isEmpty() ? service.text(viewer, MilestonesModule.ID, "perk.none")
                : Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")), parts);
    }

    /** 6.0 -> "6", 0.5 -> "0.5". */
    static String value(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** WHEAT -> Wheat, ZOMBIFIED_PIGLIN -> Zombified Piglin. */
    static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String w : key.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (!w.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return out.toString();
    }
}
