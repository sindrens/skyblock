package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /is level} in island level v2 (owner 7 Nov 2026, plans/TOOLS_V5.md section 3), in the island tool's style: the
 * level with its XP bar, where the island XP comes from (one tile per kind of work) and the next five rewards. Refreshes
 * every 2 s while open (the level follows the XP live).
 * <pre>
 *  . . . . ★ . . . ⬆      4 level, 8 island upgrades
 *  . . . . . . . . .
 *  . . 🌾 ⛏ ⚔ 🪓 . . .      20-23 farming, mining, combat, foraging
 *  . . . . . . . . .
 *  . . ① ② ③ ④ ⑤ . .      38-42 the next five rewards
 *  ← . . ? ✘ H . . .      bottom bar
 * </pre>
 */
final class IslandXpMenu extends Menu {

    static final int SLOT_HEADER = 4;
    static final int SLOT_UPGRADES = 8;
    static final List<Pillar> SOURCES = List.of(Pillar.FARMING, Pillar.MINING, Pillar.COMBAT, Pillar.FORAGING);
    static final int FIRST_SOURCE = 20;
    static final int FIRST_REWARD = 38;
    static final int REWARDS = 5;

    private final LevelMenus menus;
    private final UUID islandId;

    IslandXpMenu(LevelMenus menus, UUID islandId) {
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
        return "xp-menu.info";
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
        navBar();
        LevelManager levels = menus.levels();
        LevelConfig c = levels.config();
        LevelManager.State s = levels.view(islandId);
        double xp = menus.pillars().totalXp(islandId);
        int level = IslandXp.level(xp, c.xpBase, c.xpExponent);
        set(SLOT_HEADER, MenuButton.display(header(level, xp, c)));
        UpgradeMenus upgrades = menus.upgrades();
        if (upgrades != null && upgrades.upgrades().available() && viewer.hasPermission(UpgradeManager.PERM_UPGRADE)) {
            set(SLOT_UPGRADES, MenuButton.of(service.item(Material.ANVIL, viewer, ProgressionModule.ID, "xp-menu.upgrades"),
                    click -> upgrades.open(viewer, this)));
        }
        for (int i = 0; i < SOURCES.size(); i++) {
            set(FIRST_SOURCE + i, MenuButton.display(source(SOURCES.get(i), xp)));
        }
        for (int i = 0; i < REWARDS; i++) {
            set(FIRST_REWARD + i, MenuButton.display(reward(level + 1 + i, c)));
        }
    }

    private ItemStack header(int level, double xp, LevelConfig c) {
        double from = IslandXp.totalFor(level, c.xpBase, c.xpExponent);
        double to = IslandXp.totalFor(level + 1, c.xpBase, c.xpExponent);
        double progress = IslandXp.progress(xp, c.xpBase, c.xpExponent);
        int place = menus.boards().place(islandId);
        TagResolver[] p = {Msg.number("level", level), Msg.number("next", level + 1),
                Msg.p("xp", whole(xp - from)), Msg.p("need", whole(to - from)), Msg.number("percent", (long) Math.floor(progress * 100)),
                Msg.mm("bar", bar(progress)), Msg.p("total", whole(xp)), Msg.number("place", place)};
        List<Component> lore = new ArrayList<>(lines("xp-menu.header.lore", p));
        lore.addAll(lines(place > 0 ? "xp-menu.header.ranked" : "xp-menu.header.unranked", p));
        return service.item(new ItemStack(Material.NETHER_STAR), text("xp-menu.header.name", p), lore);
    }

    private ItemStack source(Pillar pillar, double total) {
        double xp = menus.pillars().xp(islandId, pillar);
        String key = "xp-menu.source." + pillar.id();
        TagResolver[] p = {Msg.p("xp", whole(xp)), Msg.number("percent", total > 0 ? Math.round(xp / total * 100) : 0)};
        ItemStack icon = new ItemStack(switch (pillar) {
            case FARMING -> Material.WHEAT;
            case MINING -> Material.IRON_PICKAXE;
            case COMBAT -> Material.IRON_SWORD;
            case FORAGING -> Material.OAK_LOG;
            default -> Material.PAPER;
        });
        icon.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        return service.item(icon, text(key + ".name"), lines(key + ".lore", p));
    }

    private ItemStack reward(int level, LevelConfig c) {
        long money = IslandXp.money(level, c.rewardMoneyPerLevel);
        long crystals = IslandXp.crystals(level, c.rewardCrystalsPerLevel);
        boolean big = crystals > 0 || IslandXp.key(level);
        TagResolver[] p = {Msg.number("level", level), Msg.p("money", Format.money(money)), Msg.p("crystals", Format.number(crystals)),
                Msg.p("crate", c.rewardKeyCrate == null ? "" : c.rewardKeyCrate)};
        List<Component> lore = new ArrayList<>(lines("xp-menu.reward.lore", p));
        if (big) {
            lore.addAll(lines("xp-menu.reward.big", p));
        }
        if (level % Math.max(1, menus.levels().config().globalEvery) == 0) {
            lore.addAll(lines("xp-menu.reward.server", p));
        }
        return service.item(new ItemStack(big ? Material.CHEST : Material.GOLD_NUGGET), text(big ? "xp-menu.reward.name-big"
                : "xp-menu.reward.name", p), lore);
    }

    /** Ten dots, the done part lit (the same dots as the spawner and quest bars, Bedrock-safe). Pure. */
    static String bar(double progress) {
        int lit = (int) Math.floor(Math.max(0, Math.min(1, progress)) * 10);
        return "<c:flame>" + "●".repeat(lit) + "</c:flame><c:locked>" + "●".repeat(10 - lit) + "</c:locked>";
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
}
