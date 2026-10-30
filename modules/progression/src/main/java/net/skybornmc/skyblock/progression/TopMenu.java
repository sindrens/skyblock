package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /is top} (PHASE-5 M5.2): the island boards, Level and Value tabs, Solo/Team toggle (separate boards), places
 * 1-28 and your own island's place. Weekly gain and per-pillar boards come with M5.7 (gray "Coming soon" tabs). Boards
 * are ranked every {@code top.refresh-seconds} from memory. One plain click each (Bedrock-safe).
 * <pre>
 *  R O ✦ ◇ ⌚ ⛏ ⚑ O R      2 Level, 3 Value, 4 Weekly (soon), 5 Pillars (soon), 6 Solo/Team
 *  B 1 2 3 4 5 6 7 B      places 1-28 (10-16, 19-25, 28-34, 37-43)
 *  B . . . . . . . B
 *  B . . . . . . . B
 *  B . . . . . . . B
 *  ← O ⌂ ? ✘ H B O R      45 back, 47 your island, 48 info, 49 close, 50 help
 * </pre>
 */
final class TopMenu extends Menu {

    static final int SLOT_LEVEL = 2;
    static final int SLOT_VALUE = 3;
    static final int SLOT_WEEKLY = 4;
    static final int SLOT_PILLARS = 5;
    static final int SLOT_BOARD = 6;
    static final int SLOT_YOU = 47;
    static final List<Integer> PLACES = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final LevelMenus menus;
    private final UUID ownIsland;
    private IslandTop.Kind kind = IslandTop.Kind.LEVEL;
    private IslandTop.Board board;

    TopMenu(LevelMenus menus, UUID ownIsland) {
        this.menus = menus;
        this.ownIsland = ownIsland;
        boolean team = ownIsland != null && menus.core().services().optional(IslandService.class).flatMap(i -> i.island(ownIsland))
                .map(IslandService.IslandInfo::team).orElse(false);
        this.board = team ? IslandTop.Board.TEAM : IslandTop.Board.SOLO;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("level-menu.crumb-island"), text("top-menu.crumb"));
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "top-menu.info";
    }

    @Override
    public String helpTopic() {
        return "island-level";
    }

    @Override
    public int tickInterval() {
        return 100;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_LEVEL, tab(IslandTop.Kind.LEVEL, Material.NETHER_STAR, "top-menu.tab.level"));
        set(SLOT_VALUE, tab(IslandTop.Kind.VALUE, Material.DIAMOND_BLOCK, "top-menu.tab.value"));
        set(SLOT_WEEKLY, MenuButton.display(service.item(new ItemStack(Material.CLOCK), text("top-menu.tab.weekly.name"),
                lines("top-menu.tab.weekly.lore"))));
        set(SLOT_PILLARS, MenuButton.display(hideAttributes(service.item(new ItemStack(Material.IRON_PICKAXE),
                text("top-menu.tab.pillars.name"), lines("top-menu.tab.pillars.lore")))));
        boolean solo = board == IslandTop.Board.SOLO;
        set(SLOT_BOARD, MenuButton.of(service.item(new ItemStack(solo ? Material.PLAYER_HEAD : Material.SHIELD),
                text(solo ? "top-menu.board.solo-name" : "top-menu.board.team-name"), lines(solo ? "top-menu.board.solo-lore" : "top-menu.board.team-lore")),
                click -> {
                    board = solo ? IslandTop.Board.TEAM : IslandTop.Board.SOLO;
                    refresh();
                }));
        List<IslandTop.Entry> rows = menus.levels().top(kind, board, PLACES.size());
        for (int i = 0; i < PLACES.size(); i++) {
            set(PLACES.get(i), MenuButton.display(i < rows.size() ? entry(rows.get(i)) : empty(i + 1)));
        }
        set(SLOT_YOU, MenuButton.display(you()));
    }

    private MenuButton tab(IslandTop.Kind tab, Material icon, String key) {
        boolean active = kind == tab;
        ItemStack item = service.item(new ItemStack(icon), text(key + ".name"), lines(active ? key + ".lore-active" : key + ".lore"));
        if (active) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            kind = tab;
            refresh();
        });
    }

    private ItemStack entry(IslandTop.Entry e) {
        TagResolver[] p = {Msg.p("place", e.place()), Msg.p("island", e.name() == null || e.name().isBlank() ? "?" : e.name()),
                Msg.p("level", e.level()), Msg.p("points", LevelMenu.whole(e.points())),
                Msg.p("worth", Format.money(Math.round(e.blockWorth()))), Msg.p("leader", leader(e.ownerKey()))};
        String nameKey = switch (e.place()) {
            case 1 -> "top-menu.entry.name-1";
            case 2 -> "top-menu.entry.name-2";
            case 3 -> "top-menu.entry.name-3";
            default -> "top-menu.entry.name";
        };
        Material icon = switch (e.place()) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.GRASS_BLOCK;
        };
        List<Component> lore = new ArrayList<>(lines(kind == IslandTop.Kind.LEVEL ? "top-menu.entry.lore-level" : "top-menu.entry.lore-value", p));
        if (e.islandId().equals(ownIsland)) {
            lore.addAll(lines("top-menu.entry.yours"));
        }
        return service.item(new ItemStack(icon), text(nameKey, p), lore);
    }

    private ItemStack empty(int place) {
        return service.item(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), text("top-menu.empty.name", Msg.p("place", place)),
                lines("top-menu.empty.lore"));
    }

    private ItemStack you() {
        if (ownIsland == null) {
            return service.item(new ItemStack(Material.OAK_SAPLING), text("top-menu.you.name"), lines("top-menu.you.no-island"));
        }
        int place = 0;
        for (IslandTop.Entry e : menus.levels().top(kind, board, 0)) {
            if (e.islandId().equals(ownIsland)) {
                place = e.place();
                break;
            }
        }
        LevelManager.State s = menus.levels().view(ownIsland);
        TagResolver[] p = {Msg.p("place", place), Msg.p("level", s == null ? 0 : s.level),
                Msg.p("worth", Format.money(Math.round(s == null ? 0 : s.blockWorth)))};
        return service.item(new ItemStack(Material.LIME_BED), text("top-menu.you.name"),
                lines(place > 0 ? "top-menu.you.ranked" : "top-menu.you.unranked", p));
    }

    private String leader(UUID ownerKey) {
        if (ownerKey == null) {
            return "?";
        }
        UUID account = ProfileService.of(menus.core().services()).ownerOf(ownerKey);
        String name = account == null ? null : Bukkit.getOfflinePlayer(account).getName();
        return name == null ? "?" : name;
    }

    private static ItemStack hideAttributes(ItemStack item) {
        item.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        return item;
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
