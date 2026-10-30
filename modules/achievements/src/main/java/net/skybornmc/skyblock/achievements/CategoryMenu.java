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
import java.util.Optional;

/**
 * One category: every achievement with its tier, progress bar to the next goal, the goals (done ones green) and the
 * reward title. Secret achievements show "???" until their first tier. Display only (taps do nothing), refreshes every 2 s.
 */
final class CategoryMenu extends PagedMenu<AchievementDef> {

    private final AchievementsModule module;
    private final Category category;

    CategoryMenu(AchievementsModule module, Category category) {
        this.module = module;
        this.category = category;
    }

    @Override
    protected List<AchievementDef> entries() {
        return module.registry().inCategory(category.id());
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, AchievementsModule.ID, "menu.title"),
                Component.text(AchievementRegistry.visible(category.name())));
    }

    @Override
    public String messageModule() {
        return AchievementsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.category-info";
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
    protected MenuButton entry(AchievementDef def) {
        Optional<ProfileProgress> progress = module.tracker().progress(viewer.getUniqueId()).filter(ProfileProgress::loaded);
        if (progress.isEmpty()) {
            return MenuButton.display(service.item(Material.CLOCK, viewer, AchievementsModule.ID, "menu.loading"));
        }
        ProfileProgress p = progress.get();
        int unlocked = Math.min(def.tiers(), p.unlocked(def.id()));
        if (def.hidden() && unlocked == 0) {
            return MenuButton.display(service.item(new ItemStack(module.material(module.config().hiddenIcon, Material.GRAY_DYE)),
                    text("menu.secret.name"), lines("menu.secret.lore")));
        }
        long value = p.value(def.metric());
        boolean complete = unlocked >= def.tiers();
        List<Component> lore = new ArrayList<>();
        lore.add(text(def.tiers() > 1 ? "menu.entry.subtitle-tiers" : "menu.entry.subtitle",
                Msg.c("category", module.core().messages().parse(viewer, category.name())), Msg.p("tier", unlocked), Msg.p("tiers", def.tiers())));
        lore.add(Component.empty());
        for (String line : def.description()) {
            lore.add(module.core().messages().parse(viewer, line));
        }
        lore.add(Component.empty());
        if (complete) {
            lore.addAll(lines("menu.entry.done", Msg.p("value", ValueFormat.format(def.metric(), value))));
        } else {
            long next = def.goal(unlocked + 1);
            double fraction = next <= 0 ? 0 : Math.min(1.0, (double) Math.max(0, value) / next);
            lore.addAll(lines("menu.entry.progress", Msg.mm("bar", module.bar(fraction)), Msg.p("percent", ProgressMath.percent(fraction)),
                    Msg.p("value", ValueFormat.format(def.metric(), value)), Msg.p("goal", ValueFormat.format(def.metric(), next))));
        }
        if (def.tiers() > 1) {
            lore.add(text("menu.entry.goals", Msg.mm("goals", goals(def, unlocked))));
        }
        if (!complete || def.hasTitle()) {
            lore.add(Component.empty());
            lore.add(text("menu.entry.rewards"));
        }
        if (!complete) {
            // AC2: the next tier's boost points (1/2/3/5/8 by default) and what they feed.
            PersonalBoost boost = module.registry().boostOf(def);
            lore.add(text(boost == PersonalBoost.NONE ? "menu.entry.reward-points" : "menu.entry.reward-points-boost",
                    Msg.p("points", BoostMath.tierPoints(module.config().perTier(), unlocked + 1)),
                    Msg.mm("boost", raw("menu.boost.name." + boost.id()))));
        }
        if (def.hasTitle()) {
            lore.add(text(titleOwned(def) ? "menu.entry.reward-owned" : "menu.entry.reward", Msg.c("title", titleDisplay(def))));
        }
        int shownTier = Math.max(1, unlocked);
        ItemStack item = service.item(new ItemStack(module.material(def.icon(), Material.BOOK)),
                text(complete ? "menu.entry.name-done" : unlocked > 0 ? "menu.entry.name" : "menu.entry.name-locked",
                        Toasts.placeholders(def, shownTier)), lore);
        if (complete) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.display(item);
    }

    /** "10 · 25 · 50" with the reached goals in the success color. */
    private String goals(AchievementDef def, int unlocked) {
        String done = raw("menu.goal-done");
        String open = raw("menu.goal-open");
        String separator = raw("menu.goal-separator");
        StringBuilder out = new StringBuilder();
        for (int tier = 1; tier <= def.tiers(); tier++) {
            if (tier > 1) {
                out.append(separator);
            }
            String goal = ValueFormat.compact(def.metric(), def.goal(tier));
            out.append((tier <= unlocked ? done : open).replace("<goal>", goal));
        }
        return out.toString();
    }

    private boolean titleOwned(AchievementDef def) {
        return module.core().services().optional(Cosmetics.class)
                .map(c -> c.owns(viewer.getUniqueId(), Cosmetics.Kind.TITLE, def.title().id())).orElse(false);
    }

    private Component titleDisplay(AchievementDef def) {
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
