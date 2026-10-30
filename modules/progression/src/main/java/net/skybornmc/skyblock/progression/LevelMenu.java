package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /is level} (PHASE-5 M5.2): the island level with the points of every source, how to raise each one and its
 * cap, the Recalculate button (block worth rescan, members only) and the pillars. One plain click each (Bedrock-safe).
 * Refreshes every 2 s while open.
 * <pre>
 *  R O B B ✦ B B O R      4 level, progress, board position
 *  B ▥ ⚒ ◆ ▤ . . 📖 B      10 pillars, 11 upgrades, 12 achievements, 13 collections, 16 challenge book (M5.3)
 *  B ⚗ ⛏ $ ◇ . ⟳ . B      19 skills, 20 Depths, 21 bank, 22 blocks, 24 recalculate
 *  B ⛏ 🪓 🌾 ⚔ ⚑ . . B      28-32 Mining, Foraging, Farming, Combat, Conquest
 *  B . . . . . . . B
 *  ← O B ? ✘ H B O R      45 back, 48 info, 49 close, 50 help
 * </pre>
 */
final class LevelMenu extends Menu {

    static final int SLOT_HEADER = 4;
    static final int SLOT_RECALCULATE = 24;
    static final int SLOT_TASKS = 16;
    static final List<LevelSource> SOURCES = List.of(LevelSource.PILLARS, LevelSource.UPGRADES, LevelSource.ACHIEVEMENTS,
            LevelSource.COLLECTIONS, LevelSource.SKILLS, LevelSource.DEPTHS, LevelSource.BANK, LevelSource.BLOCKS);
    static final List<Integer> SOURCE_SLOTS = List.of(10, 11, 12, 13, 19, 20, 21, 22);
    static final List<Integer> PILLAR_SLOTS = List.of(28, 29, 30, 31, 32);
    static final List<SkillType> WINGS = List.of(SkillType.MINING, SkillType.FORAGING, SkillType.COMBAT);

    private final LevelMenus menus;
    private final UUID islandId;

    LevelMenu(LevelMenus menus, UUID islandId) {
        this.menus = menus;
        this.islandId = islandId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("level-menu.crumb-island"), text("level-menu.crumb"));
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "level-menu.info";
    }

    @Override
    public String helpTopic() {
        return "island-level";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        IslandService islands = menus.core().services().optional(IslandService.class).orElse(null);
        if (islands == null || islands.island(islandId).isEmpty()) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    @Override
    protected void build() {
        frame();
        LevelManager levels = menus.levels();
        LevelManager.State s = levels.view(islandId);
        set(SLOT_HEADER, MenuButton.display(header(s)));
        for (int i = 0; i < SOURCES.size(); i++) {
            LevelSource source = SOURCES.get(i);
            ItemStack item = source(source, s);
            UpgradeMenus upgrades = menus.upgrades();
            if (source == LevelSource.UPGRADES && upgrades != null && upgrades.upgrades().available()
                    && viewer.hasPermission(UpgradeManager.PERM_UPGRADE)) {
                // M5.4: the Upgrades source opens /is upgrade.
                item.editMeta(meta -> {
                    List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lines("level-menu.source.upgrades.click").forEach(l ->
                            lore.add(l.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
                    meta.lore(lore);
                });
                set(SOURCE_SLOTS.get(i), MenuButton.of(item, click -> upgrades.open(viewer, this)));
            } else {
                set(SOURCE_SLOTS.get(i), MenuButton.display(item));
            }
        }
        set(SLOT_RECALCULATE, recalculate());
        set(SLOT_TASKS, tasks());
        Pillar[] all = Pillar.values();
        for (int i = 0; i < all.length && i < PILLAR_SLOTS.size(); i++) {
            set(PILLAR_SLOTS.get(i), MenuButton.display(pillar(all[i])));
        }
    }

    private ItemStack header(LevelManager.State s) {
        LevelManager levels = menus.levels();
        int level = s == null ? 0 : s.level;
        int percent = PillarManager.percent(s == null ? 0 : s.progress(levels.divisor()));
        int place = levels.place(islandId);
        boolean team = menus.core().services().optional(IslandService.class).flatMap(i -> i.island(islandId))
                .map(IslandService.IslandInfo::team).orElse(false);
        TagResolver[] p = {Msg.p("level", level), Msg.p("next", level + 1), Msg.p("percent", percent),
                Msg.p("points", whole(s == null ? 0 : s.total)), Msg.p("place", place),
                Msg.p("board", menus.msg().messages().raw(ProgressionModule.ID, team ? "level-menu.board.team" : "level-menu.board.solo"))};
        List<Component> lore = new ArrayList<>(lines(s == null || !s.calculated ? "level-menu.header.lore-new" : "level-menu.header.lore", p));
        lore.addAll(lines(place > 0 ? "level-menu.header.ranked" : "level-menu.header.unranked", p));
        return service.item(new ItemStack(Material.NETHER_STAR), text("level-menu.header.name", p), lore);
    }

    private ItemStack source(LevelSource source, LevelManager.State s) {
        String key = "level-menu.source." + source.id();
        ItemStack icon = new ItemStack(icon(source));
        icon.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        if (!menus.levels().hasProvider(source)) {
            return service.item(icon, text("level-menu.soon-name", Msg.c("source", text(key + ".name"))), lines(key + ".soon"));
        }
        double points = s == null ? 0 : s.points.getOrDefault(source, 0.0);
        Double limit = s == null ? null : s.limits.get(source);
        double raw = s == null ? 0 : s.raw.getOrDefault(source, 0.0);
        TagResolver[] p = {Msg.c("source", text(key + ".name")), Msg.p("points", whole(points))};
        List<Component> lore = new ArrayList<>(lines("level-menu.points", p));
        if (limit != null && Double.isFinite(limit)) {
            lore.addAll(lines(raw > limit + 1e-9 ? "level-menu.capped" : "level-menu.cap",
                    Msg.p("limit", whole(limit)), Msg.p("percent", Format.number(Math.round(capPercent(source))))));
        }
        lore.addAll(details(source, s));
        lore.add(Component.empty());
        lore.addAll(lines(key + ".how"));
        return service.item(icon, text("level-menu.source-name", p), lore);
    }

    private double capPercent(LevelSource source) {
        LevelConfig c = menus.levels().config();
        return switch (source) {
            case ACHIEVEMENTS -> c.capAchievements;
            case COLLECTIONS -> c.capCollections;
            case SKILLS -> c.capSkills;
            case DEPTHS -> c.capDepths;
            case BANK -> c.capBank;
            case BLOCKS -> c.capBlocks;
            default -> 0;
        };
    }

    /** The numbers behind a source (only known after the first calculation since the server started). */
    private List<Component> details(LevelSource source, LevelManager.State s) {
        String key = "level-menu.source." + source.id();
        LevelConfig c = menus.levels().config();
        return switch (source) {
            case PILLARS -> lines(key + ".detail", Msg.p("levels", menus.pillars().totalPillarLevels(islandId)));
            case SKILLS -> s == null || !s.detailed ? lines("level-menu.detail-pending")
                    : lines(key + ".detail", Msg.p("counted", s.countedMembers), Msg.p("members", s.members),
                    Msg.p("average", whole(s.averageSkill)), Msg.p("days", whole(c.memberMinDays)));
            case DEPTHS -> s == null || !s.detailed ? lines("level-menu.detail-pending")
                    : lines(key + ".detail", Msg.p("mine", s.depthsBest.getOrDefault(SkillType.MINING, 0)),
                    Msg.p("forest", s.depthsBest.getOrDefault(SkillType.FORAGING, 0)),
                    Msg.p("combat", s.depthsBest.getOrDefault(SkillType.COMBAT, 0)), Msg.p("days", whole(c.memberMinDays)));
            case BANK -> s == null || !s.detailed ? lines("level-menu.detail-pending")
                    : s.bankObserved ? lines(key + ".detail", Msg.p("lowest", Format.money(s.bankLow)), Msg.p("hours", c.bankWindowHours))
                    : lines(key + ".detail-new", Msg.p("hours", c.bankWindowHours));
            case BLOCKS -> menus.levels().scanning(islandId)
                    ? lines(key + ".detail-scanning", Msg.p("worth", Format.money(Math.round(worth(s)))))
                    : lines(key + ".detail", Msg.p("worth", Format.money(Math.round(worth(s)))));
            default -> List.of();
        };
    }

    private double worth(LevelManager.State s) {
        return s == null ? 0 : s.blockWorth;
    }

    private MenuButton recalculate() {
        IslandService islands = menus.core().services().optional(IslandService.class).orElse(null);
        UUID key = ProfileService.of(menus.core().services()).profileKey(viewer);
        boolean member = islands != null && islands.countsForIsland(islandId, key);
        if (!member || !viewer.hasPermission(LevelMenus.PERM_RECALCULATE)) {
            return MenuButton.display(service.item(new ItemStack(Material.GRAY_DYE), text("level-menu.recalculate.name"),
                    lines("level-menu.recalculate.lore-locked")));
        }
        LevelManager levels = menus.levels();
        String state = levels.scanning(islandId) ? "lore-running" : levels.cooldownMinutesLeft(islandId) > 0 ? "lore-cooldown" : "lore";
        ItemStack item = service.item(new ItemStack(Material.CLOCK), text("level-menu.recalculate.name"),
                lines("level-menu.recalculate." + state, Msg.p("minutes", levels.cooldownMinutesLeft(islandId)),
                        Msg.p("cooldown", levels.config().recalculateCooldownMinutes)));
        if (!state.equals("lore")) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> menus.recalculate(viewer, islandId, this::refresh));
    }

    /** The challenge book button (M5.3): tasks done on this island. */
    private MenuButton tasks() {
        TaskMenus book = menus.tasks();
        if (book == null || !book.tasks().config().enabled || !viewer.hasPermission(TaskManager.PERM_TASKS)) {
            return MenuButton.display(service.item(new ItemStack(Material.BOOK), text("level-menu.tasks.name"),
                    lines("level-menu.tasks.lore-locked")));
        }
        TagResolver[] p = {Msg.number("done", book.tasks().completedCount(islandId)), Msg.number("total", book.tasks().catalog().size())};
        ItemStack item = service.item(new ItemStack(Material.WRITABLE_BOOK), text("level-menu.tasks.name"), lines("level-menu.tasks.lore", p));
        return MenuButton.of(item, click -> book.openBook(viewer, this));
    }

    private ItemStack pillar(Pillar pillar) {
        PillarManager pillars = menus.pillars();
        Component name = text("pillar." + pillar.id() + ".name");
        ItemStack icon = new ItemStack(pillarIcon(pillar));
        icon.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        if (!pillars.shownPillars().contains(pillar)) {
            return service.item(icon, text("level-menu.pillar.soon-name", Msg.c("pillar", name)), lines("level-menu.pillar.soon"));
        }
        TagResolver[] p = {Msg.c("pillar", name), Msg.p("level", pillars.pillarLevel(islandId, pillar)),
                Msg.p("percent", PillarManager.percent(pillars.pillarProgress(islandId, pillar)))};
        return service.item(icon, text("level-menu.pillar.name", p), lines("level-menu.pillar.lore", p));
    }

    static Material icon(LevelSource source) {
        return switch (source) {
            case PILLARS -> Material.QUARTZ_PILLAR;
            case UPGRADES -> Material.ANVIL;
            case ACHIEVEMENTS -> Material.EMERALD;
            case COLLECTIONS -> Material.BOOKSHELF;
            case SKILLS -> Material.EXPERIENCE_BOTTLE;
            case DEPTHS -> Material.DEEPSLATE_DIAMOND_ORE;
            case BANK -> Material.GOLD_INGOT;
            case BLOCKS -> Material.DIAMOND_BLOCK;
        };
    }

    static Material pillarIcon(Pillar pillar) {
        return switch (pillar) {
            case MINING -> Material.IRON_PICKAXE;
            case FORAGING -> Material.IRON_AXE;
            case FARMING -> Material.WHEAT;
            case COMBAT -> Material.IRON_SWORD;
            case CONQUEST -> Material.WHITE_BANNER;
        };
    }

    static String whole(double v) {
        return Format.number(Math.round(v));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }

    static Map<LevelSource, Integer> slots() {
        Map<LevelSource, Integer> out = new java.util.EnumMap<>(LevelSource.class);
        for (int i = 0; i < SOURCES.size(); i++) {
            out.put(SOURCES.get(i), SOURCE_SLOTS.get(i));
        }
        return out;
    }
}
