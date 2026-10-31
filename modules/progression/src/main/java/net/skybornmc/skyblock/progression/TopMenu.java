package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /is top} (PHASE-5 M5.2, M5.7): the island boards. Tabs Level, Weekly gain, Value and Pillars; Weekly gain and
 * Pillars open a pick list (points or pillar levels gained; which pillar), the board button a Solo/Team pick list
 * (GUI-1: pick lists instead of cyclers). Places 1-28 and your own island's place. The boards are ranked off the main
 * thread every {@code top.refresh-seconds} ({@link TopBoards}); this menu only reads the ready lists. One plain click each
 * (Bedrock-safe).
 * <pre>
 *  R O ✦ ⌚ ◇ ⛏ ⚑ O R      2 Level, 3 Weekly gain, 4 Value, 5 Pillars, 6 Solo/Team
 *  B 1 2 3 4 5 6 7 B      places 1-28 (10-16, 19-25, 28-34, 37-43)
 *  B . . . . . . . B
 *  B . . . . . . . B
 *  B . . . . . . . B
 *  ← O ⌂ ? ✘ H B O R      45 back, 47 your island, 48 info, 49 close, 50 main menu
 * </pre>
 */
final class TopMenu extends Menu {

    static final int SLOT_LEVEL = 2;
    static final int SLOT_WEEKLY = 3;
    static final int SLOT_VALUE = 4;
    static final int SLOT_PILLARS = 5;
    static final int SLOT_BOARD = 6;
    static final int SLOT_YOU = 47;
    static final List<Integer> PLACES = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final LevelMenus menus;
    private final UUID ownIsland;
    private IslandTop.Kind kind = IslandTop.Kind.LEVEL;
    /** Non-null while a pillar board is shown (then {@link #kind} is ignored). */
    private Pillar pillar;
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
        set(SLOT_LEVEL, simpleTab(IslandTop.Kind.LEVEL, Material.NETHER_STAR, "top-menu.tab.level"));
        set(SLOT_WEEKLY, weeklyTab());
        set(SLOT_VALUE, simpleTab(IslandTop.Kind.VALUE, Material.DIAMOND_BLOCK, "top-menu.tab.value"));
        set(SLOT_PILLARS, pillarsTab());
        set(SLOT_BOARD, boardButton());
        List<IslandTop.Entry> rows = rows(PLACES.size());
        for (int i = 0; i < PLACES.size(); i++) {
            set(PLACES.get(i), MenuButton.display(i < rows.size() ? entry(rows.get(i)) : empty(i + 1)));
        }
        set(SLOT_YOU, MenuButton.display(you()));
    }

    private List<IslandTop.Entry> rows(int limit) {
        TopBoards boards = menus.boards();
        return pillar != null ? boards.pillarTop(pillar, board, limit) : boards.top(kind, board, limit);
    }

    private boolean weekly() {
        return pillar == null && (kind == IslandTop.Kind.WEEKLY_GAIN || kind == IslandTop.Kind.WEEKLY_PILLARS);
    }

    // ------------------------------------------------------------------ top row

    private MenuButton simpleTab(IslandTop.Kind tab, Material icon, String key) {
        return service.tab(viewer, new ItemStack(icon), text(key + ".name"), lines(key + ".lore"), pillar == null && kind == tab, () -> {
            kind = tab;
            pillar = null;
        });
    }

    /** A tab that opens a pick list (also while it is selected, to pick another board of the same tab). */
    private MenuButton pickTab(Material icon, String key, boolean selected, TagResolver[] p, Runnable open) {
        List<Component> lore = new ArrayList<>(lines(key + (selected ? ".lore-active" : ".lore"), p));
        lore.addAll(lines("top-menu.tab.pick"));
        ItemStack item = service.item(new ItemStack(icon), text(key + ".name"), lore);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.of(item, click -> open.run());
    }

    private MenuButton weeklyTab() {
        TagResolver[] p = {Msg.p("left", left()), Msg.c("board", text(kind == IslandTop.Kind.WEEKLY_PILLARS
                ? "top-menu.weekly.pillars-name" : "top-menu.weekly.points-name"))};
        return pickTab(Material.CLOCK, "top-menu.tab.weekly", weekly(), p, () -> {
            List<PickListMenu.Option<IslandTop.Kind>> options = List.of(
                    option(IslandTop.Kind.WEEKLY_GAIN, Material.EXPERIENCE_BOTTLE, "top-menu.weekly.points-name", "top-menu.weekly.points-lore"),
                    option(IslandTop.Kind.WEEKLY_PILLARS, Material.QUARTZ_PILLAR, "top-menu.weekly.pillars-name",
                            "top-menu.weekly.pillars-lore"));
            openChild(new PickListMenu<>(List.of(text("top-menu.crumb"), text("top-menu.weekly.crumb")), options, picked -> {
                kind = picked;
                pillar = null;
            }));
        });
    }

    private PickListMenu.Option<IslandTop.Kind> option(IslandTop.Kind value, Material icon, String nameKey, String loreKey) {
        return new PickListMenu.Option<>(value, new ItemStack(icon), text(nameKey), lines(loreKey, Msg.p("left", left())),
                weekly() && kind == value);
    }

    private MenuButton pillarsTab() {
        TagResolver[] p = {Msg.c("pillar", pillar == null ? Component.empty() : text("pillar." + pillar.id() + ".name"))};
        return pickTab(Material.IRON_PICKAXE, "top-menu.tab.pillars", pillar != null, p, () -> {
            List<PickListMenu.Option<Pillar>> options = new ArrayList<>();
            for (Pillar each : menus.pillars().shownPillars()) {
                options.add(new PickListMenu.Option<>(each, new ItemStack(icon(each)), text("pillar." + each.id() + ".name"),
                        lines("top-menu.pillar-pick.lore"), each == pillar));
            }
            openChild(new PickListMenu<>(List.of(text("top-menu.crumb"), text("top-menu.pillar-pick.crumb")), options, picked -> pillar = picked));
        });
    }

    private static Material icon(Pillar pillar) {
        return switch (pillar) {
            case MINING -> Material.IRON_PICKAXE;
            case FORAGING -> Material.IRON_AXE;
            case FARMING -> Material.WHEAT;
            case COMBAT -> Material.IRON_SWORD;
            case CONQUEST -> Material.WHITE_BANNER;
        };
    }

    private MenuButton boardButton() {
        boolean solo = board == IslandTop.Board.SOLO;
        ItemStack item = service.item(new ItemStack(solo ? Material.PLAYER_HEAD : Material.SHIELD),
                text(solo ? "top-menu.board.solo-name" : "top-menu.board.team-name"), lines("top-menu.board.lore"));
        return MenuButton.of(item, click -> {
            List<PickListMenu.Option<IslandTop.Board>> options = List.of(
                    new PickListMenu.Option<>(IslandTop.Board.SOLO, new ItemStack(Material.PLAYER_HEAD), text("top-menu.board.pick-solo"),
                            lines("top-menu.board.pick-solo-lore"), solo),
                    new PickListMenu.Option<>(IslandTop.Board.TEAM, new ItemStack(Material.SHIELD), text("top-menu.board.pick-team"),
                            lines("top-menu.board.pick-team-lore"), !solo));
            openChild(new PickListMenu<>(List.of(text("top-menu.crumb"), text("top-menu.board.crumb")), options, picked -> board = picked));
        });
    }

    // ------------------------------------------------------------------ places

    private ItemStack entry(IslandTop.Entry e) {
        TagResolver[] p = placeholders(e);
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
        List<Component> lore = new ArrayList<>(lines(entryLore(), p));
        if (e.islandId().equals(ownIsland)) {
            lore.addAll(lines("top-menu.entry.yours"));
        }
        return service.item(new ItemStack(icon), text(nameKey, p), lore);
    }

    private String entryLore() {
        if (pillar != null) {
            return "top-menu.entry.lore-pillar";
        }
        return switch (kind) {
            case LEVEL -> "top-menu.entry.lore-level";
            case VALUE -> "top-menu.entry.lore-value";
            case WEEKLY_GAIN -> "top-menu.entry.lore-weekly-points";
            case WEEKLY_PILLARS -> "top-menu.entry.lore-weekly-pillars";
        };
    }

    private TagResolver[] placeholders(IslandTop.Entry e) {
        String island = e.name() == null || e.name().isBlank() ? "?" : e.name();
        return new TagResolver[]{Msg.p("place", e.place()), Msg.p("island", island), Msg.p("level", e.level()),
                Msg.p("points", LevelMenu.whole(e.points())), Msg.p("worth", Format.money(Math.round(e.blockWorth()))),
                Msg.p("leader", e.leader().isBlank() ? "?" : e.leader()), Msg.p("gain", LevelMenu.whole(e.value())),
                Msg.p("pillar-level", LevelMenu.whole(e.value())),
                Msg.c("pillar", pillar == null ? Component.empty() : text("pillar." + pillar.id() + ".name")), Msg.p("left", left())};
    }

    private ItemStack empty(int place) {
        return service.item(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), text("top-menu.empty.name", Msg.p("place", place)),
                lines(weekly() ? "top-menu.empty.lore-weekly" : "top-menu.empty.lore"));
    }

    private ItemStack you() {
        if (ownIsland == null) {
            return service.item(new ItemStack(Material.OAK_SAPLING), text("top-menu.you.name"), lines("top-menu.you.no-island"));
        }
        TopBoards boards = menus.boards();
        int place = 0;
        for (IslandTop.Entry e : rows(0)) {
            if (e.islandId().equals(ownIsland)) {
                place = e.place();
                break;
            }
        }
        TopBoard.Candidate c = boards.view(ownIsland);
        String value;
        if (c == null) {
            value = "0";
        } else if (pillar != null) {
            value = String.valueOf(c.pillarLevelOf(pillar));
        } else {
            value = switch (kind) {
                case LEVEL -> String.valueOf(c.level());
                case VALUE -> Format.money(Math.round(c.blockWorth()));
                case WEEKLY_GAIN -> LevelMenu.whole(c.earnedGain());
                case WEEKLY_PILLARS -> String.valueOf(c.pillarGain());
            };
        }
        TagResolver[] p = {Msg.p("place", place), Msg.p("value", value), Msg.p("left", left()),
                Msg.c("label", text(youLabel()))};
        String key;
        if (boards.hiddenIsland(ownIsland)) {
            key = "top-menu.you.hidden";
        } else {
            key = place > 0 ? "top-menu.you.ranked" : "top-menu.you.unranked";
        }
        List<Component> lore = new ArrayList<>(lines(key, p));
        if (weekly()) {
            lore.addAll(lines("top-menu.you.week-left", p));
        }
        return service.item(new ItemStack(Material.LIME_BED), text("top-menu.you.name"), lore);
    }

    private String youLabel() {
        if (pillar != null) {
            return "top-menu.you.label.pillar";
        }
        return switch (kind) {
            case LEVEL -> "top-menu.you.label.level";
            case VALUE -> "top-menu.you.label.value";
            case WEEKLY_GAIN -> "top-menu.you.label.weekly-points";
            case WEEKLY_PILLARS -> "top-menu.you.label.weekly-pillars";
        };
    }

    /** Time until the weekly boards start again, e.g. "2d 4h". */
    private String left() {
        return Format.duration(Duration.ofMillis(menus.boards().weekMillisLeft()));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
