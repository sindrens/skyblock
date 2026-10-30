package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /is settings} (ISL2, UI_STYLE §5): one button per island setting, one plain click changes it (toggles flip,
 * weather and time move to the next value; Bedrock-safe, no right/shift clicks).
 * <pre>
 *  R B B B ⓘ B B B R      4 summary (who can change)
 *  B ☠ 🐄 🔥 💣 🍃 🌾 ⬇ B   10-16 mob spawning, animal spawning, fire spread, explosions, leaf decay, trampling, pickup
 *  B . 🔔 🪶 . ☀ ⏰ . B   20 notifications, 21 visitor fly, 23 weather, 24 time
 *  ...
 *  ← . . ? X ⓘ . . .      45 back, 48 info, 49 close, 50 help
 * </pre>
 * Members without the {@code settings} permission see the values read-only.
 */
final class SettingsMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final Map<IslandFlag, Integer> SLOTS = new EnumMap<>(IslandFlag.class);
    static final Map<IslandFlag, Material> ICONS = new EnumMap<>(IslandFlag.class);

    static {
        SLOTS.put(IslandFlag.MOB_SPAWNING, 10);
        SLOTS.put(IslandFlag.ANIMAL_SPAWNING, 11);
        SLOTS.put(IslandFlag.FIRE_SPREAD, 12);
        SLOTS.put(IslandFlag.EXPLOSIONS, 13);
        SLOTS.put(IslandFlag.LEAF_DECAY, 14);
        SLOTS.put(IslandFlag.CROP_TRAMPLING, 15);
        SLOTS.put(IslandFlag.VISITOR_PICKUP, 16);
        SLOTS.put(IslandFlag.NOTIFICATIONS, 20);
        SLOTS.put(IslandFlag.VISITOR_FLY, 21);
        SLOTS.put(IslandFlag.WEATHER, 23);
        SLOTS.put(IslandFlag.TIME, 24);
        ICONS.put(IslandFlag.MOB_SPAWNING, Material.ZOMBIE_HEAD);
        ICONS.put(IslandFlag.ANIMAL_SPAWNING, Material.COW_SPAWN_EGG);
        ICONS.put(IslandFlag.FIRE_SPREAD, Material.CAMPFIRE);
        ICONS.put(IslandFlag.EXPLOSIONS, Material.TNT);
        ICONS.put(IslandFlag.LEAF_DECAY, Material.OAK_LEAVES);
        ICONS.put(IslandFlag.CROP_TRAMPLING, Material.FARMLAND);
        ICONS.put(IslandFlag.VISITOR_PICKUP, Material.HOPPER);
        ICONS.put(IslandFlag.NOTIFICATIONS, Material.BELL);
        ICONS.put(IslandFlag.VISITOR_FLY, Material.FEATHER);
        ICONS.put(IslandFlag.WEATHER, Material.SUNFLOWER);
        ICONS.put(IslandFlag.TIME, Material.CLOCK);
    }

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandSettingsManager settings;
    private final Island island;
    private boolean pending;

    SettingsMenu(IslandsModule module, IslandManager islands, IslandSettingsManager settings, Island island) {
        this.module = module;
        this.islands = islands;
        this.settings = settings;
        this.island = island;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"), text("flags.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "flags.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        if (islands.byId(island.id()) != island || island.role(viewer.getUniqueId()).isEmpty()) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    @Override
    protected void build() {
        frame();
        boolean editable = settings.canChange(island, viewer);
        set(SLOT_SUMMARY, service.item(new ItemStack(Material.REPEATER), text("flags.summary.name",
                Msg.p("island", islands.displayName(island))), lines(editable ? "flags.summary.lore-edit" : "flags.summary.lore-view")));
        for (IslandFlag flag : IslandFlag.cached()) {
            Integer slot = SLOTS.get(flag);
            if (slot != null) {
                set(slot, button(flag, editable));
            }
        }
    }

    private MenuButton button(IslandFlag flag, boolean editable) {
        String value = islands.flag(island, flag);
        String defaultValue = flag.virtual()
                ? (islands.rolePermissions().defaultAllows(PermRole.VISITOR, IslandPerm.PICKUP) ? IslandFlag.ON : IslandFlag.OFF)
                : islands.flagDefaults().value(flag);
        TagResolver[] p = {Msg.p("setting", settings.name(flag)), Msg.p("value", settings.valueName(value)),
                Msg.p("default", settings.valueName(defaultValue))};
        List<Component> lore = new ArrayList<>(lines("flags." + flag.key() + ".lore"));
        lore.add(Component.empty());
        if (flag.toggle()) {
            lore.addAll(lines(IslandFlag.ON.equals(value) ? "flags.state.on" : "flags.state.off", p));
        } else {
            lore.addAll(lines("flags.state.choice", p));
        }
        lore.addAll(lines(value.equals(defaultValue) ? "flags.state.default" : "flags.state.changed", p));
        lore.add(Component.empty());
        String next = flag.next(value);
        if (editable) {
            lore.addAll(lines(flag.toggle() ? (IslandFlag.ON.equals(value) ? "flags.click.off" : "flags.click.on") : "flags.click.next",
                    Msg.p("next", settings.valueName(next))));
        } else {
            lore.addAll(lines("flags.click.locked"));
        }
        ItemStack icon = new ItemStack(icon(flag, value));
        ItemStack item = service.item(icon, text(flag.toggle() && IslandFlag.ON.equals(value) ? "flags.name-on" : "flags.name",
                Msg.p("setting", settings.name(flag)), Msg.p("value", settings.valueName(value))), lore);
        if (!editable) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            if (pending) {
                return;
            }
            pending = true;
            boolean started = settings.change(viewer, island, flag, flag.next(islands.flag(island, flag)), () -> {
                pending = false;
                refresh();
            });
            if (!started) {
                pending = false;
                refresh();
            }
        });
    }

    /** The icon of a setting; weather shows its current value. */
    static Material icon(IslandFlag flag, String value) {
        if (flag == IslandFlag.WEATHER) {
            return switch (value) {
                case "rain" -> Material.WATER_BUCKET;
                case "storm" -> Material.TRIDENT;
                default -> Material.SUNFLOWER;
            };
        }
        return ICONS.getOrDefault(flag, Material.PAPER);
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
