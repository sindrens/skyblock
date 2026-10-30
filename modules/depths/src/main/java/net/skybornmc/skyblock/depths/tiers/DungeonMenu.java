package net.skybornmc.skyblock.depths.tiers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Zone;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dungeon} (PHASE-4 mockup, UI_STYLE §5): row 1 holds the wing tabs (Mine, Forest, Combat) in the frame, rows
 * 2-5 the tiers of the selected wing. Unlocked tiers show their ore/log/mob icon and "✔ Click to teleport"; locked
 * tiers a gray dye with "✘ Mining 15 needed · 9/15" and a ● progress bar. Refreshes live while open. No shift-click or
 * right-click needed anywhere (Bedrock).
 */
final class DungeonMenu extends Menu {

    /** Tab slots in {@link Aspect} order (centre of the top frame row). */
    static final int[] TAB_SLOTS = {3, 4, 5};
    static final int SLOT_EMPTY = 22;
    /** Satchel button (M4.4), bottom row left of the info item. */
    static final int SLOT_SATCHEL = 47;

    private final Dungeon dungeon;
    private Aspect tab;

    DungeonMenu(Dungeon dungeon, Aspect tab) {
        this.dungeon = dungeon;
        this.tab = tab;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("dungeon.title"), text("tiers.wing." + tab.id()));
    }

    @Override
    public String messageModule() {
        return Dungeon.MODULE;
    }

    @Override
    public String infoKey() {
        return "dungeon.info";
    }

    @Override
    public String helpTopic() {
        return "depths";
    }

    @Override
    public int tickInterval() {
        return dungeon.config().dungeonRefreshTicks;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        for (Aspect aspect : Aspect.values()) {
            set(TAB_SLOTS[aspect.ordinal()], tabItem(aspect), click -> {
                if (tab != aspect) {
                    tab = aspect;
                    // The title names the wing: reopen (same instance, Back still works) instead of a refresh.
                    service.open(viewer, this);
                }
            });
        }
        satchelButton();
        List<Zone> wing = dungeon.zones().zones(tab);
        if (wing.isEmpty()) {
            set(SLOT_EMPTY, service.item(DungeonIcons.locked(dungeon.config()), text("dungeon.empty.name"), lines("dungeon.empty.lore")));
            return;
        }
        int[] slots = DungeonLayout.slots(wing.size());
        String here = dungeon.gates().place(viewer.getUniqueId());
        for (int i = 0; i < slots.length; i++) {
            Zone zone = wing.get(i);
            boolean locked = dungeon.gates().locked(viewer, zone);
            if (locked) {
                set(slots[i], lockedItem(zone), click -> {
                    dungeon.messages().actionBar(click.player(), "tiers.locked", dungeon.gates().requirement(click.player(), zone));
                    service.sound(click.player(), "error");
                });
            } else {
                set(slots[i], openItem(zone, zone.id().equals(here)), click -> {
                    click.player().closeInventory();
                    dungeon.teleport(click.player(), zone);
                });
            }
        }
    }

    private void satchelButton() {
        var service0 = dungeon.satchelService();
        if (service0 == null || !viewer.hasPermission(net.skybornmc.skyblock.depths.DepthsPerms.SATCHEL)) {
            return;
        }
        long used = service0.used(viewer);
        long max = service0.capacity(viewer);
        org.bukkit.Material icon = org.bukkit.Material.matchMaterial(dungeon.config().satchelIcon);
        TagResolver tags = TagResolver.resolver(Msg.p("used", net.skybornmc.skyblock.common.util.Format.number(used)),
                Msg.p("max", net.skybornmc.skyblock.common.util.Format.number(max)));
        set(SLOT_SATCHEL, service.item(new ItemStack(icon == null || !icon.isItem() ? org.bukkit.Material.BUNDLE : icon),
                text("dungeon.satchel.name", tags), lines("dungeon.satchel.lore", tags)), click -> {
            var menu = dungeon.satchelMenu();
            if (menu != null) {
                openChild(menu);
            }
        });
    }

    private ItemStack tabItem(Aspect aspect) {
        List<Zone> wing = dungeon.zones().zones(aspect);
        int open = 0;
        for (Zone zone : wing) {
            if (!dungeon.gates().locked(viewer, zone)) {
                open++;
            }
        }
        Integer level = dungeon.gates().level(viewer, aspect);
        TagResolver tags = TagResolver.resolver(Msg.c("wing", text("tiers.wing." + aspect.id())),
                Msg.c("skill", text("zones.skill." + aspect.skill().id())), Msg.p("open", open), Msg.p("total", wing.size()),
                Msg.p("level", level == null ? 0 : level));
        List<Component> lore = new ArrayList<>(lines("dungeon.tab.lore", tags));
        Zone next = level == null ? null : TierRules.nextLocked(wing, level);
        if (next != null) {
            lore.addAll(lines("dungeon.tab.next", tags, Msg.p("tier", next.tier()), Msg.p("required", next.requiredLevel())));
        } else if (!wing.isEmpty()) {
            lore.addAll(lines("dungeon.tab.all-open", tags));
        }
        lore.add(Component.empty());
        lore.addAll(lines(aspect == tab ? "dungeon.tab.selected" : "dungeon.tab.click", tags));
        ItemStack item = service.item(new ItemStack(DungeonIcons.tab(dungeon.config(), aspect)), text("dungeon.tab.name", tags), lore);
        if (aspect == tab) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private ItemStack openItem(Zone zone, boolean here) {
        TagResolver tags = dungeon.gates().requirement(viewer, zone);
        List<Component> lore = new ArrayList<>(lines("dungeon.tier.subtitle", tags));
        lore.add(Component.empty());
        lore.addAll(lines("dungeon.tier.resource", tags, Msg.p("resource", zone.resourceName() == null ? "" : zone.resourceName())));
        lore.addAll(lines(zone.requiredLevel() > 0 ? "dungeon.tier.requirement-met" : "dungeon.tier.requirement-none", tags));
        lore.add(Component.empty());
        if (here) {
            lore.addAll(lines("dungeon.tier.here", tags));
        }
        lore.addAll(lines("dungeon.tier.teleport", tags));
        ItemStack item = service.item(new ItemStack(DungeonIcons.tier(dungeon.config(), zone)), text("dungeon.tier.name", tags), lore);
        if (here) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private ItemStack lockedItem(Zone zone) {
        TagResolver tags = dungeon.gates().requirement(viewer, zone);
        Integer level = dungeon.gates().level(viewer, zone.aspect());
        int length = dungeon.config().dungeonBarLength;
        int filled = TierRules.filled(level == null ? 0 : level, zone.requiredLevel(), length);
        String bar = raw("dungeon.bar.filled").repeat(filled) + raw("dungeon.bar.empty").repeat(length - filled);
        List<Component> lore = new ArrayList<>(lines("dungeon.tier.subtitle", tags));
        lore.add(Component.empty());
        lore.addAll(lines("dungeon.tier.resource", tags, Msg.p("resource", zone.resourceName() == null ? "" : zone.resourceName())));
        lore.addAll(lines("dungeon.tier.requirement-missing", tags));
        lore.addAll(lines("dungeon.tier.progress", tags, Msg.mm("bar", bar)));
        lore.add(Component.empty());
        lore.addAll(lines("dungeon.tier.how", tags));
        return service.item(new ItemStack(DungeonIcons.locked(dungeon.config())), text("dungeon.tier.locked-name", tags), lore);
    }

    private Component text(String key, TagResolver... tags) {
        return service.text(viewer, Dungeon.MODULE, key, tags);
    }

    private List<Component> lines(String key, TagResolver... tags) {
        return service.lines(viewer, Dungeon.MODULE, key, tags);
    }

    private String raw(String key) {
        String raw = service.messages().raw(Dungeon.MODULE, key);
        return raw == null ? "" : raw;
    }
}
