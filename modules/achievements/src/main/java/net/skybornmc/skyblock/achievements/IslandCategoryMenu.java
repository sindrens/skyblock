package net.skybornmc.skyblock.achievements;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Island achievements of the viewer's island (AC3): tier, shared progress to the next goal, the goals, the boost points
 * of the next tier and the members' title. Team-only ones are left out on solo islands. Display only, refreshes every 2 s.
 */
final class IslandCategoryMenu extends PagedMenu<IslandAchievementDef> {

    private final AchievementsModule module;
    private final UUID islandId;
    private final boolean team;

    IslandCategoryMenu(AchievementsModule module, UUID islandId, boolean team) {
        this.module = module;
        this.islandId = islandId;
        this.team = team;
    }

    @Override
    protected List<IslandAchievementDef> entries() {
        List<IslandAchievementDef> out = new ArrayList<>();
        for (IslandAchievementDef def : module.registry().island()) {
            if (IslandProgression.applies(def, team)) {
                out.add(def);
            }
        }
        return out;
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, AchievementsModule.ID, "menu.title"), service.text(viewer, AchievementsModule.ID, "menu.tab.island"));
    }

    @Override
    public String messageModule() {
        return AchievementsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.island-info";
    }

    @Override
    public String helpTopic() {
        return "achievements";
    }

    @Override
    protected String emptyKey() {
        return "menu.empty";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected MenuButton entry(IslandAchievementDef def) {
        IslandProgress p = module.tracker().islands().find(islandId).orElseGet(() -> {
            IslandProgress empty = new IslandProgress(islandId);
            empty.load(Map.of(), Map.of());
            return empty;
        });
        int unlocked = Math.min(def.tiers(), p.unlocked(def.id()));
        if (def.hidden() && unlocked == 0) {
            return MenuButton.display(service.item(new ItemStack(module.material(module.config().hiddenIcon, Material.GRAY_DYE)),
                    text("menu.secret.name"), lines("menu.secret.lore")));
        }
        long value = p.value(def.metric());
        boolean complete = unlocked >= def.tiers();
        List<Component> lore = new ArrayList<>();
        lore.add(text(def.tiers() > 1 ? "menu.island-entry.subtitle-tiers" : "menu.island-entry.subtitle",
                Msg.p("tier", unlocked), Msg.p("tiers", def.tiers())));
        lore.add(Component.empty());
        for (String line : def.description()) {
            lore.add(module.core().messages().parse(viewer, line));
        }
        lore.add(Component.empty());
        Metric.Unit unit = def.metric().unit();
        if (complete) {
            lore.addAll(lines("menu.entry.done", Msg.p("value", ValueFormat.format(unit, value))));
        } else {
            long next = def.goal(unlocked + 1);
            double fraction = next <= 0 ? 0 : Math.min(1.0, (double) Math.max(0, value) / next);
            lore.addAll(lines("menu.entry.progress", Msg.mm("bar", module.bar(fraction)), Msg.p("percent", ProgressMath.percent(fraction)),
                    Msg.p("value", ValueFormat.format(unit, value)), Msg.p("goal", ValueFormat.format(unit, next))));
        }
        if (def.tiers() > 1) {
            lore.add(text("menu.entry.goals", Msg.mm("goals", goals(def, unlocked))));
        }
        lore.add(Component.empty());
        lore.add(text("menu.entry.rewards"));
        if (!complete) {
            lore.add(text("menu.entry.reward-points", Msg.p("points", BoostMath.tierPoints(module.config().perTier(), unlocked + 1))));
        }
        if (def.hasTitle()) {
            lore.add(text(titleOwned(def) ? "menu.entry.reward-owned" : "menu.island-entry.reward", Msg.c("title", titleDisplay(def)),
                    Msg.p("days", BoostMath.text(module.config().islandTitleMinDays))));
        }
        int shownTier = Math.max(1, unlocked);
        ItemStack item = service.item(new ItemStack(module.material(def.icon(), Material.BOOK)),
                text(complete ? "menu.entry.name-done" : unlocked > 0 ? "menu.entry.name" : "menu.entry.name-locked",
                        Msg.mm("name", def.name()), Msg.p("tier", Toasts.tierSuffix(def, shownTier))), lore);
        if (complete) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.display(item);
    }

    private String goals(IslandAchievementDef def, int unlocked) {
        String done = raw("menu.goal-done");
        String open = raw("menu.goal-open");
        String separator = raw("menu.goal-separator");
        StringBuilder out = new StringBuilder();
        for (int tier = 1; tier <= def.tiers(); tier++) {
            if (tier > 1) {
                out.append(separator);
            }
            String goal = ValueFormat.compact(def.metric().unit(), def.goal(tier));
            out.append((tier <= unlocked ? done : open).replace("<goal>", goal));
        }
        return out.toString();
    }

    private boolean titleOwned(IslandAchievementDef def) {
        return module.core().services().optional(Cosmetics.class)
                .map(c -> c.owns(viewer.getUniqueId(), Cosmetics.Kind.TITLE, def.title().id())).orElse(false);
    }

    private Component titleDisplay(IslandAchievementDef def) {
        Optional<Cosmetics> cosmetics = module.core().services().optional(Cosmetics.class);
        if (cosmetics.isPresent() && cosmetics.get().ids(Cosmetics.Kind.TITLE).contains(def.title().id())) {
            return cosmetics.get().display(viewer, Cosmetics.Kind.TITLE, def.title().id());
        }
        String display = def.title().display();
        return display == null ? Component.text(def.title().id()) : module.core().messages().parse(viewer, display);
    }

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
