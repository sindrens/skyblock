package net.skybornmc.skyblock.skills;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /skills [player]} (UI_STYLE §5): a head with the summary on top, then one item per skill (level, progress bar
 * with ● segments, XP in the level, total XP, next unlock, how to earn XP). Skills that aren't open (Fishing; Farming while
 * farming is off) are hidden.
 * Live for online targets (refreshes every 2 s), a snapshot for offline ones.
 */
final class SkillsMenu extends Menu {

    static final int SLOT_HEAD = 4;
    /** Slot per skill, in {@link SkillType} order. */
    static final int[] SLOTS = {20, 21, 22, 23, 24};
    static final Map<SkillType, Material> ICONS = Map.of(
            SkillType.MINING, Material.IRON_PICKAXE,
            SkillType.FORAGING, Material.OAK_LOG,
            SkillType.COMBAT, Material.IRON_SWORD,
            SkillType.FARMING, Material.WHEAT,
            SkillType.FISHING, Material.FISHING_ROD);

    private final SkillsModule module;
    private final UUID target;
    private final String targetName;
    /** Totals of an offline target; {@code null} = read live from the cache. */
    private final Map<SkillType, Double> snapshot;

    SkillsMenu(SkillsModule module, UUID target, String targetName, Map<SkillType, Double> snapshot) {
        this.module = module;
        this.target = target;
        this.targetName = targetName;
        this.snapshot = snapshot == null ? null : new EnumMap<>(snapshot);
    }

    private boolean self() {
        return viewer.getUniqueId().equals(target);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        if (self()) {
            return service.titleKeys(viewer, SkillsModule.ID, "menu.title");
        }
        return service.title(viewer, service.text(viewer, SkillsModule.ID, "menu.title"),
                service.text(viewer, SkillsModule.ID, "menu.title-other", Msg.p("player", targetName)));
    }

    @Override
    public String messageModule() {
        return SkillsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "skills";
    }

    @Override
    public int tickInterval() {
        return snapshot == null ? 40 : 0;
    }

    @Override
    protected void tick() {
        refresh();
    }

    private double totalXp(SkillType skill) {
        if (snapshot != null) {
            return snapshot.getOrDefault(skill, 0.0);
        }
        return module.manager().xp(target, skill);
    }

    @Override
    protected void build() {
        frame();
        SkillManager manager = module.manager();
        SkillCurve curve = manager.curve();
        int totalLevels = 0;
        for (SkillType skill : SkillType.values()) {
            if (manager.available(skill)) {
                totalLevels += curve.levelFor(totalXp(skill));
            }
        }
        set(SLOT_HEAD, head(totalLevels));
        for (SkillType skill : SkillType.values()) {
            if (manager.available(skill)) { // GUI-4 (GUI_DESIGN §3): skills that aren't open yet are hidden, not grey
                set(SLOTS[skill.ordinal()], skillItem(skill, curve));
            }
        }
    }

    private ItemStack head(int totalLevels) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(target)));
        return service.item(head, text("menu.head.name", Msg.p("player", targetName)),
                lines("menu.head.lore", Msg.p("total", totalLevels), Msg.p("player", targetName)));
    }

    private ItemStack skillItem(SkillType skill, SkillCurve curve) {
        SkillManager manager = module.manager();
        double xp = totalXp(skill);
        int level = curve.levelFor(xp);
        boolean maxed = level >= curve.maxLevel();
        Component name = manager.skillName(viewer, skill);
        List<Component> lore = new ArrayList<>(lines("menu.skill.subtitle", Msg.p("level", level), Msg.p("max", curve.maxLevel())));
        lore.add(Component.empty());
        lore.addAll(lines("skill." + skill.id() + ".description"));
        lore.add(Component.empty());
        if (maxed) {
            lore.addAll(lines("menu.skill.maxed"));
        } else {
            double progress = curve.progress(xp);
            int length = module.config().barLength;
            int filled = XpFormat.filled(progress, length);
            String bar = raw("menu.bar.filled").repeat(filled) + raw("menu.bar.empty").repeat(length - filled);
            lore.addAll(lines("menu.skill.progress", Msg.p("level", level), Msg.mm("bar", bar),
                    Msg.p("percent", XpFormat.percent(progress))));
            lore.addAll(lines("menu.skill.xp", Msg.p("into", XpFormat.xp(curve.intoLevel(xp))),
                    Msg.p("step", XpFormat.xp(curve.step(level))), Msg.p("next", level + 1)));
        }
        lore.addAll(lines("menu.skill.total", Msg.p("total", XpFormat.xp(xp))));
        int next = manager.nextUnlock(skill, level);
        lore.add(Component.empty());
        if (next > 0 && next <= curve.maxLevel()) {
            lore.addAll(lines("menu.skill.next-unlock", Msg.c("unlock", manager.unlockText(viewer, skill, next)),
                    Msg.p("level", next), Msg.p("missing", next - level)));
        } else {
            lore.addAll(lines("menu.skill.all-unlocked"));
        }
        lore.add(Component.empty());
        lore.addAll(lines("menu.skill.how-header"));
        lore.addAll(lines("skill." + skill.id() + ".how"));
        ItemStack item = service.item(new ItemStack(ICONS.get(skill)),
                text("menu.skill.name", Msg.c("skill", name), Msg.p("level", level)), lore);
        if (maxed) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private Component text(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        return service.text(viewer, SkillsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        return service.lines(viewer, SkillsModule.ID, key, placeholders);
    }

    private String raw(String key) {
        String raw = service.messages().raw(SkillsModule.ID, key);
        return raw == null ? "" : raw;
    }
}
