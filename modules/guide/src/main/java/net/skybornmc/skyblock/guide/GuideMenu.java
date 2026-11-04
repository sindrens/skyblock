package net.skybornmc.skyblock.guide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.milestones.MilestoneRewards;
import net.skybornmc.skyblock.common.nextstep.NextStep;
import net.skybornmc.skyblock.common.nextstep.NextStepService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.progression.TaskService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Kindle's menu (plan 4 Nov C §2: clean, vanilla, spacious). Three groups, each centred on its own row; a button whose
 * module or command is missing is left out and its row re-centres ({@link GuideLayout}). One click each (Bedrock-safe).
 * <pre>
 *  . . . . K . . . .      4 Kindle (greeting, news; a click opens the guide book)
 *  . . Q . N . F . .      row 1: Kindle's quests · Next step · Kindle's finds
 *  . . . . . . . . .      air
 *  . L . U . T . M .      row 3: Island level (progress) · Upgrades · Island tasks · Milestones
 *  . . T . F . M . .      row 4: Island tools (/tool) · Farm (/farm) · Mine (/mine)
 *  ←  . . . ✕ ? . . .      Back 45 · Close 49 · Help 50
 * </pre>
 * The Next step button shows the shared step ({@link NextStepService}, the same as /help and the scoreboard) and a click
 * does it (runs its command: a teleport or a menu).
 */
final class GuideMenu extends Menu {

    static final int SLOT_KINDLE = 4;
    static final int ROW_KINDLE = 1;
    static final int ROW_ISLAND = 3;
    static final int ROW_GRIND = 4;
    /** Help on the bottom row (same slot as the /menu hub). */
    static final int SLOT_HELP = 50;

    /** One button of a row: its item and click. */
    private record Tile(ItemStack item, Consumer<net.skybornmc.skyblock.common.menu.MenuClick> click) {
    }

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
    protected void build() {
        navBar();
        String level = ph("island_level", "0");
        set(SLOT_KINDLE, item(Material.BLAZE_POWDER, news ? "menu.kindle-news" : "menu.kindle",
                Msg.p("player", viewer.getName()), Msg.p("level", level)), click -> {
            click.player().closeInventory();
            m.openBook(click.player());
        });
        place(ROW_KINDLE, kindleRow());
        place(ROW_ISLAND, islandRow(level));
        place(ROW_GRIND, grindRow());
        if (commandExists("help")) {
            set(SLOT_HELP, item(Material.BOOK, "menu.help"), click -> run(click.player(), "help"));
        }
    }

    private void place(int row, List<Tile> tiles) {
        int[] slots = GuideLayout.row(row, tiles.size());
        for (int i = 0; i < slots.length; i++) {
            Tile t = tiles.get(i);
            set(slots[i], t.item(), t.click());
        }
    }

    // ------------------------------------------------------------------ rows

    private List<Tile> kindleRow() {
        List<Tile> out = new ArrayList<>();
        GuideCatalog.Entry story = m.quests().currentStory(island, () -> {
            if (isOpen()) {
                refresh();
            }
        });
        out.add(new Tile(item(Material.WHEAT, story != null ? "menu.kindle-quests-story" : "menu.kindle-quests",
                Msg.p("ask", story == null ? "" : story.cost().values().iterator().next() + " "
                        + GuideTasksMenu.pretty(story.cost().keySet().iterator().next()))),
                click -> openChild(new GuideTasksMenu(m, island, false))));
        out.add(nextStepTile());
        out.add(new Tile(item(Material.BUNDLE, "menu.finds"), click -> openChild(new GuideTasksMenu(m, island, true))));
        return out;
    }

    private List<Tile> islandRow(String level) {
        List<Tile> out = new ArrayList<>();
        Optional<ProgressionService> prog = m.core().services().optional(ProgressionService.class);
        prog.ifPresent(p -> {
            int percent = GuideRules.percent(ph("island_level_progress", "0"));
            int length = Math.max(1, m.config().progressLength);
            int filled = GuideRules.filled(percent, length);
            String dot = raw("menu.progress-dot", "●");
            out.add(new Tile(item(Material.NETHER_STAR, "menu.level", Msg.p("level", level),
                    Msg.p("filled", dot.repeat(filled)), Msg.p("empty", dot.repeat(length - filled)), Msg.number("percent", percent)),
                    click -> p.openLevelMenu(click.player(), this)));
            if (p.upgradesAvailable()) {
                out.add(new Tile(item(Material.ANVIL, "menu.upgrades", Msg.p("ready", ph("island_upgrades_ready", "0"))),
                        click -> p.openUpgradeMenu(click.player(), this)));
            }
        });
        m.core().services().optional(TaskService.class).ifPresent(t -> out.add(new Tile(item(Material.WRITABLE_BOOK,
                "menu.challenges", Msg.number("done", t.completedCount(island)), Msg.number("total", t.tasks().size())),
                click -> t.openBook(click.player(), this))));
        m.core().services().optional(MilestoneRewards.class).ifPresent(r -> {
            int ready = r.ready(viewer);
            out.add(new Tile(item(ready > 0 ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                    ready > 0 ? "menu.milestones-ready" : "menu.milestones", Msg.number("ready", ready)), click -> r.open(click.player())));
        });
        return out;
    }

    private List<Tile> grindRow() {
        List<Tile> out = new ArrayList<>();
        if (commandExists("tool")) {
            out.add(new Tile(item(Material.SMITHING_TABLE, "menu.tools"), click -> run(click.player(), "tool")));
        }
        if (commandExists("farm")) {
            out.add(new Tile(item(Material.WHEAT_SEEDS, "menu.farm"), click -> run(click.player(), "farm")));
        }
        if (commandExists("mine")) {
            out.add(new Tile(item(Material.IRON_PICKAXE, "menu.mine"), click -> run(click.player(), "mine")));
        }
        return out;
    }

    /**
     * The shared next step (plan 4 Nov C §2): its text in the lore, a click runs its command (teleport or menu) with
     * Back to Kindle. With no step: {@code guide.yml → next-step.none-command} (the main menu).
     */
    private Tile nextStepTile() {
        Optional<NextStep> step = NextStepService.of(m.core().services()).current(viewer);
        String fallback = raw("menu.next-none", "Explore the main menu");
        String text = step.map(NextStep::text).orElse(fallback);
        String command = step.filter(NextStep::hasAction).map(NextStep::command).orElse(m.config().nextNoneCommand);
        boolean runs = command != null && !command.isBlank() && commandExists(command);
        ItemStack icon = item(Material.COMPASS, runs ? "menu.next" : "menu.next-info", Msg.p("next", text),
                Msg.p("command", runs ? "/" + command.strip() : ""));
        icon.editMeta(meta -> meta.setEnchantmentGlintOverride(step.isPresent()));
        return new Tile(icon, click -> {
            Player p = click.player();
            if (runs) {
                run(p, command.strip());
            } else {
                p.closeInventory();
                m.msg().send(p, "menu.next-chat", Msg.p("next", text));
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private static boolean commandExists(String command) {
        String label = command.strip().split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        return !label.isEmpty() && org.bukkit.Bukkit.getCommandMap().getCommand(label) != null;
    }

    /** Runs a command with Back to Kindle for a menu it opens (a teleport closes the menu as usual). */
    private void run(Player player, String command) {
        service.navigate(player, this, () -> player.performCommand(command));
    }

    private ItemStack item(Material material, String key, TagResolver... tags) {
        return service.item(material, viewer, GuideModule.ID, key, tags);
    }

    private String ph(String key, String fallback) {
        if (!m.core().placeholders().has(key)) {
            return fallback;
        }
        return m.core().placeholders().text(viewer, key).filter(s -> !s.isBlank()).orElse(fallback);
    }

    private String raw(String key, String fallback) {
        String raw = m.core().messages().raw(GuideModule.ID, key);
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    private Component text(String key) {
        return service.text(viewer, GuideModule.ID, key);
    }
}
