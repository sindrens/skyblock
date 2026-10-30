package net.skybornmc.skyblock.achievements;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.achievements.IslandBoost;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /achievements} (owner 356, RTA-37): one 6-row menu with three tabs in the top row (taps only, Bedrock-safe).
 * <pre>
 *  R O B [P][I][H] B O R      tabs: Personal, Island, History (the open one glows)
 *  B . . . S . . . B          S = summary (13)
 *  B . C C C C C . B          C = personal categories (slots from achievements.yml) / island boosts + achievements
 *  B . . . C . . . B
 *  B . . . . . . . B
 *  ← O B ? ✘ H B O →
 * </pre>
 * Personal: one item per category with "points x / boost y% of cap z%". Island: island points, the four island boosts,
 * the island achievements list and the members' top 5. History: one item per finished season. Refreshes every 2 s.
 */
final class AchievementsMenu extends Menu {

    enum Tab { PERSONAL, ISLAND, HISTORY }

    static final int FIRST_TAB_SLOT = 3;
    static final int SLOT_SUMMARY = AchievementRegistry.SUMMARY_SLOT;
    static final int SLOT_LOADING = 22;
    static final int SLOT_ISLAND_LIST = 22;
    static final int SLOT_TOP = 31;
    static final int[] BOOST_SLOTS = {20, 21, 23, 24};
    /** History seasons: the three lower content rows. */
    static final int[] HISTORY_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final AchievementsModule module;
    private Tab tab;
    private List<SeasonSummary.Entry> history;
    private boolean historyLoading;
    private boolean historyFailed;

    AchievementsMenu(AchievementsModule module, Tab tab) {
        this.module = module;
        this.tab = tab == null ? Tab.PERSONAL : tab;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, AchievementsModule.ID, "menu.title"),
                service.text(viewer, AchievementsModule.ID, "menu.tab." + key(tab)));
    }

    @Override
    public String messageModule() {
        return AchievementsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "achievements";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        refresh();
    }

    private static String key(Tab tab) {
        return tab.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    protected void build() {
        frame();
        tabs();
        switch (tab) {
            case PERSONAL -> personal();
            case ISLAND -> island();
            case HISTORY -> history();
        }
    }

    private void tabs() {
        Tab[] tabs = Tab.values();
        Material[] icons = {Material.PLAYER_HEAD, Material.GRASS_BLOCK, Material.WRITABLE_BOOK};
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            ItemStack icon = service.item(icons[i], viewer, AchievementsModule.ID, t == tab ? "menu.tab-selected" : "menu.tab-other",
                    Msg.mm("tab", raw("menu.tab." + key(t))), Msg.mm("hint", raw("menu.tab-hint." + key(t))));
            if (t == tab) {
                icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            set(FIRST_TAB_SLOT + i, icon, click -> {
                if (tab != t) {
                    tab = t;
                    module.core().sounds().play(viewer, "click");
                    refresh();
                }
            });
        }
    }

    // ------------------------------------------------------------------ personal

    private void personal() {
        Optional<ProfileProgress> progress = module.tracker().progress(viewer.getUniqueId()).filter(ProfileProgress::loaded);
        if (progress.isEmpty()) {
            set(SLOT_LOADING, service.item(Material.CLOCK, viewer, AchievementsModule.ID, "menu.loading"));
            return;
        }
        ProfileProgress p = progress.get();
        AchievementRegistry registry = module.registry();
        set(SLOT_SUMMARY, head(p, registry));
        for (Category category : registry.categories()) {
            List<AchievementDef> defs = registry.inCategory(category.id());
            if (defs.isEmpty()) {
                continue;
            }
            set(category.slot(), categoryItem(category, defs, p), click -> openChild(new CategoryMenu(module, category)));
        }
    }

    private ItemStack head(ProfileProgress p, AchievementRegistry registry) {
        int done = Progression.completed(p, registry);
        int[] tiers = Progression.tierCounts(p, registry);
        double fraction = tiers[1] == 0 ? 0 : (double) tiers[0] / tiers[1];
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        return service.item(head, text("menu.head.name", Msg.p("player", viewer.getName())),
                lines("menu.head.lore", Msg.p("done", done), Msg.p("total", registry.size()), Msg.p("tiers", tiers[0]),
                        Msg.p("all-tiers", tiers[1]), Msg.mm("bar", module.bar(fraction)), Msg.p("percent", ProgressMath.percent(fraction)),
                        Msg.p("profile", module.profileName(viewer)), Msg.p("points", p.totalPoints())));
    }

    private ItemStack categoryItem(Category category, List<AchievementDef> defs, ProfileProgress p) {
        int done = 0;
        int tiersDone = 0;
        int tiers = 0;
        for (AchievementDef def : defs) {
            int unlocked = Math.min(def.tiers(), p.unlocked(def.id()));
            if (unlocked >= def.tiers()) {
                done++;
            }
            tiersDone += unlocked;
            tiers += def.tiers();
        }
        double fraction = tiers == 0 ? 0 : (double) tiersDone / tiers;
        List<Component> lore = new ArrayList<>();
        if (!category.description().isBlank()) {
            lore.add(module.core().messages().parse(viewer, category.description()));
            lore.add(Component.empty());
        }
        lore.addAll(boostLines(category.boost(), p.categoryPoints(category.id()), p.boostPoints(category.boost())));
        lore.addAll(lines("menu.category.lore", Msg.p("done", done), Msg.p("total", defs.size()), Msg.mm("bar", module.bar(fraction)),
                Msg.p("percent", ProgressMath.percent(fraction))));
        ItemStack item = service.item(new ItemStack(module.material(category.icon(), Material.BOOK)),
                module.core().messages().parse(viewer, category.name()), lore);
        if (done == defs.size()) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    /** "| Boost" section of a category: points, the boost now and its cap (AH slots as a count). */
    private List<Component> boostLines(PersonalBoost boost, int categoryPoints, int boostPoints) {
        if (boost == PersonalBoost.NONE) {
            return List.of();
        }
        AchievementsConfig c = module.config();
        List<Component> out = new ArrayList<>();
        out.add(text("menu.boost.header"));
        out.add(text("menu.boost.points", Msg.p("points", categoryPoints)));
        String name = raw("menu.boost.name." + boost.id());
        if (boost == PersonalBoost.AH_SLOTS) {
            int slots = c.boostsEnabled ? BoostMath.slots(boostPoints, c.ahSlotFirst, c.ahSlotSecond) : 0;
            out.add(text("menu.boost.slots", Msg.mm("boost", name), Msg.p("value", slots), Msg.p("cap", (int) c.cap(boost))));
            int next = slots == 0 ? c.ahSlotFirst : slots == 1 ? c.ahSlotSecond : 0;
            if (next > boostPoints) {
                out.add(text("menu.boost.next", Msg.p("points", next)));
            }
        } else {
            double now = c.boostsEnabled ? BoostMath.percent(boostPoints, c.step(boost), c.cap(boost)) : 0;
            out.add(text(now >= c.cap(boost) && c.cap(boost) > 0 ? "menu.boost.value-max" : "menu.boost.value", Msg.mm("boost", name),
                    Msg.p("value", BoostMath.text(now)), Msg.p("cap", BoostMath.text(c.cap(boost)))));
            out.add(text("menu.boost.step", Msg.p("step", BoostMath.text(c.step(boost)))));
        }
        if (!c.boostsEnabled) {
            out.add(text("menu.boost.disabled"));
        }
        out.add(Component.empty());
        return out;
    }

    // ------------------------------------------------------------------ island

    private void island() {
        IslandTracker islands = module.tracker().islands();
        Optional<IslandService> service = module.core().services().optional(IslandService.class);
        UUID key = ProfileService.of(module.core().services()).profileKey(viewer.getUniqueId());
        Optional<IslandService.IslandInfo> info = service.flatMap(s -> s.islandOfProfile(key))
                .filter(i -> service.get().isMember(i.id(), key));
        if (!module.config().islandEnabled) {
            set(SLOT_ISLAND_LIST, this.service.item(Material.BARRIER, viewer, AchievementsModule.ID, "menu.island.disabled"));
            return;
        }
        if (info.isEmpty()) {
            set(SLOT_ISLAND_LIST, this.service.item(Material.OAK_SAPLING, viewer, AchievementsModule.ID, "menu.island.none"));
            return;
        }
        if (!islands.loaded()) {
            set(SLOT_LOADING, this.service.item(Material.CLOCK, viewer, AchievementsModule.ID, "menu.loading"));
            return;
        }
        UUID islandId = info.get().id();
        boolean team = info.get().team();
        IslandProgress p = islands.find(islandId).orElseGet(() -> {
            IslandProgress empty = new IslandProgress(islandId);
            empty.load(Map.of(), Map.of());
            return empty;
        });
        int[] tiers = IslandProgression.tierCounts(p, module.registry(), team);
        double fraction = tiers[1] == 0 ? 0 : (double) tiers[0] / tiers[1];
        set(SLOT_SUMMARY, this.service.item(Material.GRASS_BLOCK, viewer, AchievementsModule.ID, "menu.island.summary",
                Msg.p("island", info.get().name() == null ? "" : info.get().name()), Msg.p("points", p.points()),
                Msg.p("tiers", tiers[0]), Msg.p("all-tiers", tiers[1]), Msg.mm("bar", module.bar(fraction)),
                Msg.p("percent", ProgressMath.percent(fraction)),
                Msg.p("level-points", BoostMath.text(p.points() * module.config().levelPointsPerPoint))));
        IslandBoost[] boosts = IslandBoost.values();
        Material[] icons = {Material.HOPPER, Material.AMETHYST_SHARD, Material.EXPERIENCE_BOTTLE, Material.BLAZE_POWDER};
        for (int i = 0; i < boosts.length && i < BOOST_SLOTS.length; i++) {
            set(BOOST_SLOTS[i], islandBoostItem(boosts[i], icons[i], p.points()));
        }
        set(SLOT_ISLAND_LIST, this.service.item(Material.BOOKSHELF, viewer, AchievementsModule.ID, "menu.island.list",
                        Msg.p("tiers", tiers[0]), Msg.p("all-tiers", tiers[1])),
                click -> openChild(new IslandCategoryMenu(module, islandId, team)));
        set(SLOT_TOP, topMembers(islandId));
    }

    private ItemStack islandBoostItem(IslandBoost boost, Material icon, int points) {
        AchievementsConfig c = module.config();
        double now = BoostMath.islandPercent(points, boost, c);
        double cap = c.islandCap(boost);
        List<Component> lore = new ArrayList<>(lines("menu.island.boost.lore." + boost.id()));
        lore.add(Component.empty());
        lore.add(text("menu.boost.header"));
        lore.add(text(now >= cap && cap > 0 ? "menu.island.boost.value-max" : "menu.island.boost.value",
                Msg.p("value", BoostMath.text(now)), Msg.p("cap", BoostMath.text(cap))));
        lore.add(text("menu.boost.step", Msg.p("step", BoostMath.text(c.islandStep(boost)))));
        lore.add(text("menu.island.boost.full", Msg.p("points", BoostMath.pointsToCap(c.islandStep(boost), cap))));
        if (boost != IslandBoost.ISLAND_XP) {
            lore.add(Component.empty());
            lore.addAll(lines("menu.island.boost.later." + boost.id()));
        }
        return service.item(new ItemStack(icon), text("menu.island.boost.name." + boost.id()), lore);
    }

    private ItemStack topMembers(UUID islandId) {
        List<Map.Entry<UUID, Long>> top = module.tracker().islands().topMembers(islandId);
        List<Component> lore = new ArrayList<>(lines("menu.island.top.intro"));
        ProfileService profiles = ProfileService.of(module.core().services());
        if (top.isEmpty()) {
            lore.addAll(lines("menu.island.top.empty"));
        }
        int place = 1;
        for (Map.Entry<UUID, Long> e : top) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(profiles.ownerOf(e.getKey()));
            String name = owner.getName() == null ? "?" : owner.getName();
            lore.add(text("menu.island.top.line", Msg.p("place", place++), Msg.p("player", name),
                    Msg.p("amount", ValueFormat.format(Metric.Unit.COUNT, e.getValue()))));
        }
        return service.item(new ItemStack(Material.GOLDEN_HELMET), text("menu.island.top.name"), lore);
    }

    // ------------------------------------------------------------------ history

    private void history() {
        set(SLOT_SUMMARY, service.item(Material.WRITABLE_BOOK, viewer, AchievementsModule.ID, "menu.history.header"));
        if (history == null) {
            set(SLOT_LOADING, service.item(historyFailed ? Material.RED_STAINED_GLASS : Material.CLOCK, viewer, AchievementsModule.ID,
                    historyFailed ? "menu.history.error" : "menu.loading"));
            loadHistory();
            return;
        }
        if (history.isEmpty()) {
            set(SLOT_LOADING, service.item(Material.COBWEB, viewer, AchievementsModule.ID, "menu.history.empty"));
            return;
        }
        Map<UUID, String> names = profileNames();
        for (int i = 0; i < history.size() && i < HISTORY_SLOTS.length; i++) {
            set(HISTORY_SLOTS[i], seasonItem(history.get(i), names));
        }
    }

    private void loadHistory() {
        if (historyLoading || historyFailed) {
            return;
        }
        historyLoading = true;
        UUID owner = viewer.getUniqueId();
        module.tracker().queue(c -> new AchievementStore().history(c, owner)).whenComplete((rows, error) ->
                module.core().scheduler().main("achievements-history", () -> {
                    historyLoading = false;
                    if (error != null) {
                        historyFailed = true;
                        module.core().plugin().getLogger().warning("Achievements: history of " + owner + " could not be loaded: "
                                + Database.cause(error).getMessage());
                    } else {
                        history = rows;
                    }
                    if (isOpen()) {
                        refresh();
                    }
                }));
    }

    private Map<UUID, String> profileNames() {
        Map<UUID, String> out = new java.util.HashMap<>();
        for (Profile profile : ProfileService.of(module.core().services()).profiles(viewer.getUniqueId())) {
            out.put(profile.id(), profile.name());
        }
        return out;
    }

    private ItemStack seasonItem(SeasonSummary.Entry e, Map<UUID, String> names) {
        List<Component> lore = new ArrayList<>(lines("menu.history.season.lore", Msg.p("profile", names.getOrDefault(e.profileKey(), "-")),
                Msg.p("tiers", e.tiers()), Msg.p("done", e.completed()), Msg.p("points", e.points()),
                Msg.p("island-tiers", e.islandTiers()), Msg.p("island-points", e.islandPoints())));
        if (!e.titles().isEmpty()) {
            lore.add(Component.empty());
            lore.add(text("menu.history.season.titles"));
            Optional<Cosmetics> cosmetics = module.core().services().optional(Cosmetics.class);
            for (String title : e.titles()) {
                Component display = cosmetics.isPresent() && cosmetics.get().ids(Cosmetics.Kind.TITLE).contains(title)
                        ? cosmetics.get().display(viewer, Cosmetics.Kind.TITLE, title) : Component.text(title);
                lore.add(text("menu.history.season.title", Msg.c("title", display), Msg.p("season", e.season())));
            }
        }
        return service.item(new ItemStack(Material.BOOK), text("menu.history.season.name", Msg.p("season", e.season())), lore);
    }

    // ------------------------------------------------------------------ helpers

    private String raw(String key) {
        String raw = service.messages().raw(AchievementsModule.ID, key);
        return raw == null ? "" : raw;
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, AchievementsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, AchievementsModule.ID, key, placeholders);
    }
}
