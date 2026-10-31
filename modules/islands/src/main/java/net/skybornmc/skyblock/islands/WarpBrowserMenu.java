package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Warps (M2.4). Two modes:
 * <ul>
 *   <li><b>Browser</b> ({@code island == null}, {@code /is warp}): every public warp of unlocked islands, most visited
 *   first (then the island's rating). Top row 2-6: one button per category (click again = all categories). Bottom: 47
 *   the island browser, 51 your island's warps.</li>
 *   <li><b>One island</b> ({@code /is warp <player>} with several warps, or "Your warps"): the warps the viewer may use.
 *   The island's leader and mods click a warp to edit it (teleport, icon, public/private, category, delete).</li>
 * </ul>
 * One plain click everywhere (Bedrock-safe).
 */
final class WarpBrowserMenu extends PagedMenu<IslandWarp> {

    static final int[] CATEGORY_SLOTS = {2, 3, 4, 5, 6};
    static final int SLOT_ISLANDS = 47;
    static final int SLOT_OWN = 51;

    private final IslandsModule module;
    private final IslandManager islands;
    private final VisitManager visits;
    private final WarpManager warps;
    private final Island island;
    private WarpCategory category;

    WarpBrowserMenu(IslandsModule module, IslandManager islands, VisitManager visits, WarpManager warps, Island island) {
        this.module = module;
        this.islands = islands;
        this.visits = visits;
        this.warps = warps;
        this.island = island;
    }

    @Override
    protected List<IslandWarp> entries() {
        if (island != null) {
            return warps.usable(island, viewer);
        }
        List<IslandWarp> list = new ArrayList<>(warps.browsable(category));
        list.sort(Comparator.comparingInt(IslandWarp::visits).reversed()
                .thenComparing(Comparator.comparingDouble((IslandWarp w) -> visits.stats(w.islandId()).average()).reversed())
                .thenComparing(IslandWarp::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    protected List<Component> titleCrumbs() {
        if (island != null) {
            return List.of(text("menu.crumb"), text("warps.crumb-island", Msg.p("island", islands.displayName(island))));
        }
        return category == null
                ? List.of(text("menu.crumb"), text("warps.crumb"))
                : List.of(text("menu.crumb"), text("warps.crumb"), text("warps.crumb-category", Msg.p("category", warps.categoryName(category))));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return island == null ? "warps.info" : "warps.info-island";
    }

    @Override
    public String helpTopic() {
        return "visiting";
    }

    @Override
    protected String emptyKey() {
        return island == null ? "warps.empty" : "warps.empty-island";
    }

    @Override
    protected void decorate() {
        Optional<Island> own = islands.islandOfPlayer(islands.key(viewer));
        if (island == null) {
            WarpCategory[] categories = WarpCategory.values();
            for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
                WarpCategory tab = categories[i];
                boolean selected = tab == category;
                List<Component> lore = new ArrayList<>(lines("warps.filter.lore", Msg.p("count", warps.browsable(tab).size())));
                lore.add(Component.empty());
                lore.addAll(lines(selected ? "warps.filter.selected" : "warps.filter.click"));
                ItemStack item = service.item(new ItemStack(Optional.ofNullable(Material.matchMaterial(module.config().categoryIcon(tab)))
                        .orElse(Material.ENDER_PEARL)), text("warps.filter.name", Msg.p("category", warps.categoryName(tab))), lore);
                if (selected) {
                    item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                }
                set(CATEGORY_SLOTS[i], item, click -> {
                    category = category == tab ? null : tab;
                    page(0);
                    service.open(viewer, this);
                });
            }
            set(SLOT_ISLANDS, service.item(Material.GRASS_BLOCK, viewer, IslandsModule.ID, "warps.islands"),
                    click -> openChild(new VisitMenu(module, islands, visits, warps, Showcase.Sort.DISCOVER)));
        }
        if (own.isPresent() && own.get() != island) {
            Island mine = own.get();
            set(SLOT_OWN, service.item(new ItemStack(Material.LODESTONE), text("warps.own.name"),
                    lines("warps.own.lore", Msg.p("count", warps.warps(mine.id()).size()), Msg.p("slots", warps.slots(mine)))),
                    click -> openChild(new WarpBrowserMenu(module, islands, visits, warps, mine)));
        } else if (island != null && own.isPresent()) {
            List<Component> how = new ArrayList<>(lines("warps.how.lore", Msg.p("count", warps.warps(island.id()).size()),
                    Msg.p("slots", warps.slots(island)), Msg.p("tag", module.config().warpSignTag)));
            if (!warps.canManage(viewer, island)) {
                set(SLOT_OWN, service.item(new ItemStack(Material.OAK_SIGN), text("warps.how.name"), how));
            } else {
                // GUI-4b: set a warp here without typing a command (a form popup with the keyboard on Bedrock).
                how.addAll(lines("warps.how.click"));
                Island here = island;
                set(SLOT_OWN, service.item(new ItemStack(Material.OAK_SIGN), text("warps.how.name"), how),
                        click -> service.input(viewer, IslandsModule.ID, "warp.new-prompt", module.config().warpNameMaxLength, null,
                                typed -> warps.setWarp(viewer, typed, null), () -> service.open(viewer, this),
                                Msg.p("count", warps.warps(here.id()).size()), Msg.p("slots", warps.slots(here))));
            }
        }
    }

    @Override
    protected MenuButton entry(IslandWarp warp) {
        Island owner = island != null ? island : islands.byId(warp.islandId());
        if (owner == null) {
            return MenuButton.display(service.item(Material.BARRIER, viewer, IslandsModule.ID, "browser.gone"));
        }
        Showcase.Stats stats = visits.stats(owner.id());
        TagResolver[] p = {
                Msg.p("warp", warp.name()),
                Msg.p("island", islands.displayName(owner)),
                Msg.p("owner", islands.ownerName(owner)),
                Msg.p("category", warps.categoryName(warp.category())),
                Msg.p("visits", warp.visits()),
                Msg.p("rating", String.format(java.util.Locale.ROOT, "%.1f", stats.average())),
                Msg.p("ratings", stats.ratingCount())};
        List<Component> lore = new ArrayList<>(lines("warps.entry.lore", p));
        lore.addAll(lines(stats.ratingCount() > 0 ? "warps.entry.rated" : "warps.entry.unrated", p));
        if (!warp.isPublic()) {
            lore.addAll(lines("warps.entry.private"));
        }
        if (warp.sign() != null) {
            lore.addAll(lines("warps.entry.sign"));
        }
        boolean edit = island != null && warps.canManage(viewer, owner);
        lore.add(Component.empty());
        lore.addAll(lines(edit ? "warps.entry.click-edit" : "warps.entry.click"));
        ItemStack item = service.item(new ItemStack(warps.icon(warp)), text("warps.entry.name", p), lore);
        return MenuButton.of(item, click -> {
            if (edit) {
                openChild(new WarpEditMenu(module, islands, warps, owner, warp));
                return;
            }
            viewer.closeInventory();
            warps.teleport(viewer, owner, warp);
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
