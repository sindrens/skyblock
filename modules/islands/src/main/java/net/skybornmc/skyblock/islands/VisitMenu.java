package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The visit browser ({@code /is visit} without a name, M2.4): every built, unlocked island as its owner's head with
 * visits, rating, likes and warps. Tabs in the top row (one plain click each, Bedrock-safe): Most visited (2), Top rated
 * (3), Most liked (4), Newest (5); slot 6 opens the warp browser. Click an island to visit it.
 */
final class VisitMenu extends PagedMenu<Showcase.Entry> {

    static final int[] TAB_SLOTS = {2, 3, 4, 5};
    static final int SLOT_WARPS = 6;

    private final IslandsModule module;
    private final IslandManager islands;
    private final VisitManager visits;
    private final WarpManager warps;
    private Showcase.Sort sort;
    /** Entries of the current build (sorted once per open/refresh, not per call). */
    private List<Showcase.Entry> cached;

    VisitMenu(IslandsModule module, IslandManager islands, VisitManager visits, WarpManager warps, Showcase.Sort sort) {
        this.module = module;
        this.islands = islands;
        this.visits = visits;
        this.warps = warps;
        this.sort = sort;
    }

    @Override
    protected List<Showcase.Entry> entries() {
        if (cached == null) {
            cached = Showcase.sort(Showcase.entries(visits.listed(), visits.allStats()), sort, module.config().browserMinRatings);
        }
        return cached;
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("menu.crumb"), text("browser.crumb"), text("browser.sort." + sort.key() + ".crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "browser.info";
    }

    @Override
    public String helpTopic() {
        return "visiting";
    }

    @Override
    protected String emptyKey() {
        return sort == Showcase.Sort.RATING ? "browser.empty-rated" : "browser.empty";
    }

    @Override
    protected void decorate() {
        Showcase.Sort[] sorts = Showcase.Sort.values();
        for (int i = 0; i < sorts.length && i < TAB_SLOTS.length; i++) {
            Showcase.Sort tab = sorts[i];
            boolean selected = tab == sort;
            List<Component> lore = new ArrayList<>(lines("browser.sort." + tab.key() + ".lore",
                    Msg.p("days", module.config().browserVisitsDays), Msg.p("min", module.config().browserMinRatings)));
            lore.add(Component.empty());
            lore.addAll(lines(selected ? "browser.tab-selected" : "browser.tab-click"));
            ItemStack item = service.item(new ItemStack(tabIcon(tab)), text("browser.sort." + tab.key() + ".name"), lore);
            if (selected) {
                item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            set(TAB_SLOTS[i], item, click -> {
                if (sort != tab) {
                    sort = tab;
                    cached = null;
                    page(0);
                    service.open(viewer, this);
                }
            });
        }
        set(SLOT_WARPS, service.item(Material.ENDER_PEARL, viewer, IslandsModule.ID, "browser.warps"),
                click -> openChild(new WarpBrowserMenu(module, islands, visits, warps, null)));
    }

    private static Material tabIcon(Showcase.Sort sort) {
        return switch (sort) {
            case VISITS -> Material.OAK_BOAT;
            case RATING -> Material.NETHER_STAR;
            case LIKES -> Material.POPPY;
            case NEWEST -> Material.OAK_SAPLING;
        };
    }

    @Override
    protected MenuButton entry(Showcase.Entry entry) {
        Island island = islands.byId(entry.islandId());
        if (island == null) {
            return MenuButton.display(service.item(Material.BARRIER, viewer, IslandsModule.ID, "browser.gone"));
        }
        Showcase.Stats stats = entry.stats();
        TagResolver[] p = {
                Msg.p("island", islands.displayName(island)),
                Msg.p("owner", islands.ownerName(island)),
                Msg.p("members", island.memberCount()),
                Msg.p("visits", stats.visits()),
                Msg.p("days", module.config().browserVisitsDays),
                Msg.p("rating", String.format(java.util.Locale.ROOT, "%.1f", stats.average())),
                Msg.p("ratings", stats.ratingCount()),
                Msg.p("likes", stats.likes()),
                Msg.p("warps", warps.usable(island, viewer).size())};
        List<Component> lore = new ArrayList<>(lines("browser.entry.lore", p));
        if (stats.ratingCount() > 0) {
            lore.addAll(lines("browser.entry.rated", p));
        } else {
            lore.addAll(lines("browser.entry.unrated", p));
        }
        boolean own = island.roleOrNull(viewer.getUniqueId()) != null;
        boolean banned = visits.isBanned(island.id(), viewer.getUniqueId()) && !visits.bypass(viewer);
        lore.add(Component.empty());
        lore.addAll(lines(own ? "browser.entry.own" : banned ? "browser.entry.banned" : "browser.entry.click", p));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(island.owner())));
        ItemStack item = service.item(head, text("browser.entry.name", p), lore);
        return MenuButton.of(item, click -> {
            viewer.closeInventory();
            visits.visit(viewer, island);
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
