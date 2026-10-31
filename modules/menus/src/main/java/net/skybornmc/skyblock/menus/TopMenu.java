package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.leaderboard.TopLine;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /menu → Top} (GUI_DESIGN §7.6, GUI-4c): the screen behind the hub's Top button. No command of its own
 * ({@code /top} is the staff teleport); {@code /is top} keeps the full island boards (28 places, value, pillars) and is
 * one click away on 51.
 *
 * <p>Tabs (row 0, in place): Solo islands, Team islands, This week (weekly island gain, both boards), Richest players
 * ({@code /baltop} data) and Top skills (one skill at a time, picked on 47). A tab whose feature is not built is hidden.
 * #1-#3 stand on a gold/silver/bronze podium (21-23), #4-#10 follow on 28-34; the key fact (place, name, value) is in the
 * item name so Bedrock players read it without hovering. One plain click on an island visits it. Island boards come from
 * {@link ProgressionService} (ranked off the main thread every few minutes); the player boards are read off the main
 * thread and shared for {@code navigation.yml → top.cache-seconds} ({@link TopCache}).</p>
 */
final class TopMenu extends Menu {

    enum Tab { SOLO, TEAM, WEEK, MONEY, SKILLS }

    private final Nav nav;
    private Tab tab;
    private SkillType skill;

    TopMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "top.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "top.info";
    }

    @Override
    public String helpTopic() {
        return "top";
    }

    // ------------------------------------------------------------------ services

    private Optional<ProgressionService> progression() {
        return nav.core.services().optional(ProgressionService.class);
    }

    private Optional<EconomyService> economy() {
        return nav.core.services().optional(EconomyService.class);
    }

    private Optional<SkillService> skills() {
        return nav.core.services().optional(SkillService.class);
    }

    /** The tabs whose feature is built, in display order. */
    List<Tab> visibleTabs() {
        List<Tab> tabs = new ArrayList<>();
        if (progression().isPresent()) {
            tabs.add(Tab.SOLO);
            tabs.add(Tab.TEAM);
            tabs.add(Tab.WEEK);
        }
        if (economy().isPresent()) {
            tabs.add(Tab.MONEY);
        }
        if (skills().isPresent() && !availableSkills().isEmpty()) {
            tabs.add(Tab.SKILLS);
        }
        return tabs;
    }

    private List<SkillType> availableSkills() {
        List<SkillType> out = new ArrayList<>();
        skills().ifPresent(s -> {
            for (SkillType type : SkillType.values()) {
                if (s.available(type)) {
                    out.add(type);
                }
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        frame();
        List<Tab> tabs = visibleTabs();
        if (tabs.isEmpty()) {
            setEmpty(Nav.TEXTS, "top.none");
            return;
        }
        if (tab == null || !tabs.contains(tab)) {
            tab = defaultTab(tabs);
        }
        int[] slots = TopLayout.tabSlots(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            Tab each = tabs.get(i);
            set(slots[i], service.tab(viewer, new ItemStack(tabIcon(each)), text("top.tabs." + key(each) + ".name"),
                    lines("top.tabs." + key(each) + ".lore"), each == tab, () -> tab = each));
        }
        switch (tab) {
            case SOLO, TEAM, WEEK -> islands();
            case MONEY -> players(nav.tops.get("money", ttl(), () -> economy().map(e -> e.top(Currency.MONEY, TopLayout.PLACES))
                    .orElse(CompletableFuture.completedFuture(List.of()))), Tab.MONEY, null);
            case SKILLS -> {
                List<SkillType> available = availableSkills();
                if (skill == null || !available.contains(skill)) {
                    skill = available.getFirst();
                }
                SkillType shown = skill;
                set(TopLayout.SLOT_SKILL, skillButton(available));
                players(nav.tops.get("skill:" + shown.id(), ttl(), () -> skills().map(s -> s.top(shown, TopLayout.PLACES))
                        .orElse(CompletableFuture.completedFuture(List.of()))), Tab.SKILLS, shown);
            }
        }
    }

    /** Your own island's board first (a team player starts on Team islands), else the first tab. */
    private Tab defaultTab(List<Tab> tabs) {
        if (tabs.contains(Tab.SOLO)) {
            boolean team = nav.core.services().optional(IslandService.class).flatMap(i -> i.islandOf(viewer.getUniqueId()))
                    .map(IslandService.IslandInfo::team).orElse(false);
            return team && tabs.contains(Tab.TEAM) ? Tab.TEAM : Tab.SOLO;
        }
        return tabs.getFirst();
    }

    private long ttl() {
        return nav.topCacheSeconds * 1000L;
    }

    private static String key(Tab tab) {
        return tab.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Material tabIcon(Tab tab) {
        return switch (tab) {
            case SOLO -> Material.PLAYER_HEAD;
            case TEAM -> Material.SHIELD;
            case WEEK -> Material.CLOCK;
            case MONEY -> Material.GOLD_INGOT;
            case SKILLS -> Material.EXPERIENCE_BOTTLE;
        };
    }

    // ------------------------------------------------------------------ islands

    private void islands() {
        ProgressionService progression = progression().orElseThrow();
        IslandTop.Kind kind = tab == Tab.WEEK ? IslandTop.Kind.WEEKLY_GAIN : IslandTop.Kind.LEVEL;
        IslandTop.Board board = switch (tab) {
            case SOLO -> IslandTop.Board.SOLO;
            case TEAM -> IslandTop.Board.TEAM;
            default -> IslandTop.Board.ALL;
        };
        List<IslandTop.Entry> all = progression.islandTop(kind, board, 0);
        UUID own = nav.core.services().optional(IslandService.class).flatMap(i -> i.islandOf(viewer.getUniqueId()))
                .map(IslandService.IslandInfo::id).orElse(null);
        if (all.isEmpty()) {
            setEmpty(Nav.TEXTS, tab == Tab.WEEK ? "top.empty-week" : "top.empty");
        }
        int yours = 0;
        for (IslandTop.Entry e : all) {
            if (own != null && e.islandId().equals(own)) {
                yours = e.place();
            }
            int slot = TopLayout.slot(e.place());
            if (slot < 0) {
                continue;
            }
            set(slot, islandEntry(e, own));
        }
        set(TopLayout.SLOT_YOU, MenuButton.display(you(own == null ? "top.you.no-island" : yours > 0 ? "top.you.ranked" : "top.you.unranked",
                Msg.p("place", yours), Msg.c("board", text("top.tabs." + key(tab) + ".name")))));
        List<Component> fullLore = new ArrayList<>(lines("top.full.lore"));
        fullLore.addAll(lines("top.full.action"));
        set(TopLayout.SLOT_FULL, nav.item(Material.SPYGLASS, text("top.full.name"), fullLore),
                click -> progression.openTopMenu(click.player(), this));
        if (tab == Tab.WEEK) {
            set(4, MenuButton.display(nav.item(Material.CLOCK, text("top.week-left.name", Msg.p("left",
                    Format.duration(java.time.Duration.ofMillis(Math.max(0, weekMillisLeft()))))), lines("top.week-left.lore"))));
        }
    }

    /** Time until the weekly boards start again (Monday 00:00 Europe/Oslo). */
    private static long weekMillisLeft() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(Format.zone());
        java.time.ZonedDateTime next = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                .atStartOfDay(Format.zone());
        return java.time.Duration.between(now, next).toMillis();
    }

    private MenuButton islandEntry(IslandTop.Entry e, UUID own) {
        String name = e.name() == null || e.name().isBlank() ? (e.leader().isBlank() ? "?" : e.leader()) : e.name();
        String value = tab == Tab.WEEK ? Format.number(Math.round(e.value())) : String.valueOf(e.level());
        TagResolver[] p = {Msg.p("place", e.place()), Msg.p("name", name), Msg.p("value", value),
                Msg.p("leader", e.leader().isBlank() ? "?" : e.leader()), Msg.p("level", e.level())};
        String valueKey = tab == Tab.WEEK ? "top.entry.week" : "top.entry.level";
        List<Component> lore = new ArrayList<>(lines("top.entry.island-lore", p));
        if (e.islandId().equals(own)) {
            lore.addAll(lines("top.entry.yours"));
        }
        boolean canVisit = !e.leader().isBlank() && !e.islandId().equals(own);
        if (canVisit) {
            lore.addAll(lines("top.entry.visit"));
        }
        ItemStack item = nav.item(placeIcon(e.place(), Material.GRASS_BLOCK), text(valueKey + nameSuffix(e.place()), p), lore);
        if (!canVisit) {
            return MenuButton.display(item);
        }
        String leader = e.leader();
        return MenuButton.of(item, click -> service.navigate(click.player(), this, () -> click.player().performCommand("is visit " + leader)));
    }

    // ------------------------------------------------------------------ players

    private void players(CompletableFuture<List<TopLine>> future, Tab shownTab, SkillType shownSkill) {
        if (!future.isDone()) {
            setLoading(TopLayout.SLOT_MIDDLE);
            future.whenComplete((rows, error) -> nav.core.scheduler().ensureMain("menus:top-loaded", () -> {
                if (isOpen() && tab == shownTab && skill == shownSkill) {
                    refresh();
                }
            }));
            return;
        }
        if (future.isCompletedExceptionally()) {
            setError(TopLayout.SLOT_MIDDLE, this::refresh);
            return;
        }
        List<TopLine> rows = future.join();
        if (rows.isEmpty()) {
            setEmpty(Nav.TEXTS, "top.empty-players");
        }
        int yours = 0;
        for (int i = 0; i < rows.size() && i < TopLayout.PLACES; i++) {
            TopLine line = rows.get(i);
            int place = i + 1;
            if (line.player().equals(viewer.getUniqueId())) {
                yours = place;
            }
            set(TopLayout.slot(place), MenuButton.display(playerEntry(line, place, shownTab, shownSkill)));
        }
        set(TopLayout.SLOT_YOU, MenuButton.display(you(yours > 0 ? "top.you.ranked" : "top.you.not-top",
                Msg.p("place", yours), Msg.c("board", shownTab == Tab.SKILLS ? skillName(shownSkill) : text("top.tabs.money.name")))));
    }

    private ItemStack playerEntry(TopLine line, int place, Tab shownTab, SkillType shownSkill) {
        String name = line.name() == null || line.name().isBlank() ? "?" : line.name();
        String value;
        String key;
        if (shownTab == Tab.MONEY) {
            value = economy().map(e -> e.format(Currency.MONEY, line.value())).orElse(Format.money(line.value()));
            key = "top.entry.money";
        } else {
            int level = skills().map(s -> s.levelFor(line.value())).orElse(0);
            value = String.valueOf(level);
            key = "top.entry.skill";
        }
        TagResolver[] p = {Msg.p("place", place), Msg.p("name", name), Msg.p("value", value),
                Msg.p("xp", Format.number(line.value())), Msg.c("skill", shownSkill == null ? Component.empty() : skillName(shownSkill))};
        List<Component> lore = new ArrayList<>(lines(shownTab == Tab.MONEY ? "top.entry.money-lore" : "top.entry.skill-lore", p));
        if (line.player().equals(viewer.getUniqueId())) {
            lore.addAll(lines("top.entry.you"));
        }
        ItemStack item = nav.item(placeIcon(place, Material.PLAYER_HEAD), text(key + nameSuffix(place), p), lore);
        if (place > 3) {
            item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(line.player())));
        }
        return item;
    }

    private MenuButton skillButton(List<SkillType> available) {
        List<Component> lore = new ArrayList<>(lines("top.skill.lore"));
        lore.addAll(lines("top.skill.action"));
        ItemStack item = nav.item(skillIcon(skill), text("top.skill.name", Msg.c("skill", skillName(skill))), lore);
        return MenuButton.of(item, click -> {
            List<PickListMenu.Option<SkillType>> options = new ArrayList<>();
            for (SkillType type : available) {
                options.add(new PickListMenu.Option<>(type, new ItemStack(skillIcon(type)), skillName(type), List.of(), type == skill));
            }
            openChild(new PickListMenu<>(List.of(text("top.title"), text("top.skill.crumb")), options, picked -> skill = picked));
        });
    }

    private Component skillName(SkillType type) {
        return text("top.skills." + type.id());
    }

    private static Material skillIcon(SkillType type) {
        return switch (type) {
            case MINING -> Material.IRON_PICKAXE;
            case FORAGING -> Material.IRON_AXE;
            case COMBAT -> Material.IRON_SWORD;
            case FARMING -> Material.WHEAT;
            case FISHING -> Material.FISHING_ROD;
        };
    }

    // ------------------------------------------------------------------ shared

    private ItemStack you(String key, TagResolver... p) {
        ItemStack head = nav.item(Material.PLAYER_HEAD, text("top.you.name", p), lines(key, p));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        return head;
    }

    /** {@code .name-1}..{@code .name-3} for the podium (gold, silver, bronze), {@code .name} for the rest. */
    static String nameSuffix(int place) {
        return place >= 1 && place <= 3 ? ".name-" + place : ".name";
    }

    static Material placeIcon(int place, Material rest) {
        return switch (place) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> rest;
        };
    }

    private Component text(String key, TagResolver... placeholders) {
        return nav.text(viewer, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return nav.lines(viewer, key, placeholders);
    }
}
