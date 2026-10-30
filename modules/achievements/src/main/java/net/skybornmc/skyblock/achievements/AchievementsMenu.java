package net.skybornmc.skyblock.achievements;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The achievements menu (6 rows, taps only: Bedrock-safe): a head with the summary on top and one item per category
 * (slots from {@code achievements.yml}) with its progress; a tap opens the category. Shows the viewer's active profile,
 * refreshes every 2 s.
 */
final class AchievementsMenu extends Menu {

    static final int SLOT_HEAD = 4;
    static final int SLOT_LOADING = 22;

    private final AchievementsModule module;

    AchievementsMenu(AchievementsModule module) {
        this.module = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, AchievementsModule.ID, "menu.title");
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

    @Override
    protected void build() {
        frame();
        Optional<ProfileProgress> progress = module.tracker().progress(viewer.getUniqueId()).filter(ProfileProgress::loaded);
        if (progress.isEmpty()) {
            set(SLOT_LOADING, service.item(Material.CLOCK, viewer, AchievementsModule.ID, "menu.loading"));
            return;
        }
        ProfileProgress p = progress.get();
        AchievementRegistry registry = module.registry();
        set(SLOT_HEAD, head(p, registry));
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
                        Msg.p("all-tiers", tiers[1]), Msg.mm("bar", bar(fraction)), Msg.p("percent", ProgressMath.percent(fraction)),
                        Msg.p("profile", module.profileName(viewer))));
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
        lore.addAll(lines("menu.category.lore", Msg.p("done", done), Msg.p("total", defs.size()), Msg.mm("bar", bar(fraction)),
                Msg.p("percent", ProgressMath.percent(fraction))));
        ItemStack item = service.item(new ItemStack(module.material(category.icon(), Material.BOOK)),
                module.core().messages().parse(viewer, category.name()), lore);
        if (done == defs.size()) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private String bar(double fraction) {
        return module.bar(fraction);
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, AchievementsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, AchievementsModule.ID, key, placeholders);
    }
}
