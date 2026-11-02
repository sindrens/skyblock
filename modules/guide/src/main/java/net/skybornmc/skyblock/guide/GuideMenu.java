package net.skybornmc.skyblock.guide;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.milestones.MilestoneRewards;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.progression.TaskService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Kindle's menu (docs/planning/ISLAND_GUIDE.md): one place for everything an island does. A button whose module is off
 * is left out (no dead buttons). One click each (Bedrock-safe).
 * <pre>
 *  . . . . K . . . .      4 Kindle (greeting, news)
 *  . . N . Q . F . .      11 next step, 13 Kindle's quests, 15 Kindle's finds
 *  . . C . D . M . .      20 challenges, 22 daily quests, 24 milestones
 *  . . U . S . L . .      29 upgrades, 31 masteries, 33 island level
 *  . . D . B . H . .      38 the Depths, 40 guide book, 42 help
 *  nav bar
 * </pre>
 */
final class GuideMenu extends Menu {

    static final int SLOT_KINDLE = 4;
    static final int SLOT_NEXT = 11;
    static final int SLOT_KINDLE_QUESTS = 13;
    static final int SLOT_FINDS = 15;
    static final int SLOT_CHALLENGES = 20;
    static final int SLOT_QUESTS = 22;
    static final int SLOT_MILESTONES = 24;
    static final int SLOT_UPGRADES = 29;
    static final int SLOT_MASTERIES = 31;
    static final int SLOT_LEVEL = 33;
    static final int SLOT_DEPTHS = 38;
    static final int SLOT_BOOK = 40;
    static final int SLOT_HELP = 42;

    private final GuideModule m;
    private final UUID island;
    private final boolean news;

    GuideMenu(GuideModule m, UUID island, boolean news) {
        this.m = m;
        this.island = island;
        this.news = news;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb-island"), text("menu.crumb"));
    }

    @Override
    public String messageModule() {
        return GuideModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "islands";
    }

    @Override
    protected void build() {
        navBar();
        String level = ph("island_level", "0");
        set(SLOT_KINDLE, service.item(Material.BLAZE_POWDER, viewer, GuideModule.ID, news ? "menu.kindle-news" : "menu.kindle",
                Msg.p("player", viewer.getName()), Msg.p("level", level)));
        set(SLOT_NEXT, service.item(Material.COMPASS, viewer, GuideModule.ID, "menu.next",
                Msg.p("next", ph("objective", m.core().messages().raw(GuideModule.ID, "menu.next-none")))), click -> {
            click.player().closeInventory();
            m.msg().send(click.player(), "menu.next-chat", Msg.p("next", ph("objective", "")));
        });
        GuideCatalog.Entry story = m.quests().currentStory(island, () -> { if (isOpen()) refresh(); });
        set(SLOT_KINDLE_QUESTS, service.item(Material.WHEAT, viewer, GuideModule.ID, story != null ? "menu.kindle-quests-story" : "menu.kindle-quests",
                Msg.p("ask", story == null ? "" : story.cost().values().iterator().next() + " "
                        + GuideTasksMenu.pretty(story.cost().keySet().iterator().next()))),
                click -> openChild(new GuideTasksMenu(m, island, false)));
        set(SLOT_FINDS, service.item(Material.BUNDLE, viewer, GuideModule.ID, "menu.finds"),
                click -> openChild(new GuideTasksMenu(m, island, true)));
        set(SLOT_BOOK, service.item(Material.WRITTEN_BOOK, viewer, GuideModule.ID, "menu.book"), click -> {
            click.player().closeInventory();
            m.openBook(click.player());
        });
        Optional<TaskService> tasks = m.core().services().optional(TaskService.class);
        tasks.ifPresent(t -> set(SLOT_CHALLENGES, service.item(Material.WRITABLE_BOOK, viewer, GuideModule.ID, "menu.challenges",
                Msg.number("done", t.completedCount(island)), Msg.number("total", t.tasks().size())),
                click -> t.openBook(click.player(), this)));
        if (commandExists("quests")) {
            set(SLOT_QUESTS, service.item(Material.MAP, viewer, GuideModule.ID, "menu.quests"), click -> run(click.player(), "quests"));
        }
        Optional<MilestoneRewards> ms = m.core().services().optional(MilestoneRewards.class);
        ms.ifPresent(r -> {
            int ready = r.ready(viewer);
            set(SLOT_MILESTONES, service.item(ready > 0 ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE, viewer, GuideModule.ID,
                    ready > 0 ? "menu.milestones-ready" : "menu.milestones", Msg.number("ready", ready)), click -> r.open(click.player()));
        });
        Optional<ProgressionService> prog = m.core().services().optional(ProgressionService.class);
        prog.ifPresent(p -> {
            set(SLOT_UPGRADES, service.item(Material.ANVIL, viewer, GuideModule.ID, "menu.upgrades"), click -> p.openUpgradeMenu(click.player(), this));
            set(SLOT_MASTERIES, service.item(Material.ENCHANTING_TABLE, viewer, GuideModule.ID, "menu.masteries"),
                    click -> p.openMasteryMenu(click.player(), this));
            set(SLOT_LEVEL, service.item(Material.NETHER_STAR, viewer, GuideModule.ID, "menu.level", Msg.p("level", level)),
                    click -> p.openLevelMenu(click.player(), this));
        });
        if (commandExists("dungeon")) {
            set(SLOT_DEPTHS, service.item(Material.DEEPSLATE_DIAMOND_ORE, viewer, GuideModule.ID, "menu.depths",
                    Msg.p("tier", ph("depths_tier", "-"))), click -> run(click.player(), "dungeon"));
        }
        set(SLOT_HELP, service.item(Material.BOOK, viewer, GuideModule.ID, "menu.help"), click -> run(click.player(), "help"));
    }

    private static boolean commandExists(String label) {
        return org.bukkit.Bukkit.getCommandMap().getCommand(label) != null;
    }

    private void run(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private String ph(String key, String fallback) {
        if (!m.core().placeholders().has(key)) {
            return fallback;
        }
        return m.core().placeholders().text(viewer, key).filter(s -> !s.isBlank()).orElse(fallback);
    }

    private Component text(String key) {
        return service.text(viewer, GuideModule.ID, key);
    }
}
