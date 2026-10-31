package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /is} » Permissions (GUI-4, owner answer U8): three presets instead of the 115-cell matrix, which moves behind
 * "Advanced".
 * <pre>
 *  R O B B ⓘ B B O R      4 summary (current setup)
 *  B . S . N . O . B      11 Strict, 13 Normal, 15 Open (the active one glints)
 *  B . . . A . . . B      22 Advanced (the full matrix)
 *  ← O B ? ✘ H B O R      27 Back, 30 ?, 31 Close, 32 Main menu
 * </pre>
 * A preset changes only the cells the viewer may change (an Admin never touches the Admin column). Replacing a custom
 * setup asks first (G8: the island's own changes are lost); switching between presets is one click.
 */
final class PermPresetsMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_STRICT = 11;
    static final int SLOT_NORMAL = 13;
    static final int SLOT_OPEN = 15;
    static final int SLOT_ADVANCED = 22;

    private final IslandsModule module;
    private final IslandManager islands;
    private final Island island;
    private boolean pending;

    PermPresetsMenu(IslandsModule module, IslandManager islands, Island island) {
        this.module = module;
        this.islands = islands;
        this.island = island;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"), text("perms.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "perms.presets.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected void build() {
        frame();
        Optional<PermPresets.Preset> active = presets().active(island.perms(), islands.rolePermissions());
        Component current = active.map(p -> text("perms.presets." + p.key() + ".word")).orElse(text("perms.presets.custom"));
        boolean canEdit = canEditAny();
        set(SLOT_SUMMARY, service.item(new ItemStack(Material.REPEATER), text("perms.presets.summary.name", Msg.c("preset", current)),
                lines(canEdit ? "perms.presets.summary.lore-edit" : "perms.presets.summary.lore-view")));
        set(SLOT_STRICT, preset(PermPresets.Preset.STRICT, Material.IRON_DOOR, active, canEdit));
        set(SLOT_NORMAL, preset(PermPresets.Preset.NORMAL, Material.OAK_DOOR, active, canEdit));
        set(SLOT_OPEN, preset(PermPresets.Preset.OPEN, Material.OAK_FENCE_GATE, active, canEdit));
        set(SLOT_ADVANCED, service.item(Material.COMPARATOR, viewer, IslandsModule.ID, "perms.presets.advanced"),
                click -> openChild(new PermissionsMenu(module, islands, island)));
    }

    private PermPresets presets() {
        return module.permPresets();
    }

    private Role viewerRole() {
        return island.roleOrNull(islands.key(viewer));
    }

    /** The cells the viewer may change now (same rule as the matrix). */
    private PermPresets.CellFilter editable() {
        Role editor = viewerRole();
        return (role, perm) -> MemberRules.canEditCell(editor, role, perm, islands.roleAllows(island, islands.key(viewer), perm));
    }

    private boolean canEditAny() {
        Role editor = viewerRole();
        for (PermRole role : PermissionsMenu.COLUMNS) {
            if (MemberRules.canEditPerms(editor, role)) {
                return true;
            }
        }
        return false;
    }

    private MenuButton preset(PermPresets.Preset preset, Material icon, Optional<PermPresets.Preset> active, boolean canEdit) {
        boolean selected = active.isPresent() && active.get() == preset;
        String key = "perms.presets." + preset.key();
        List<Component> lore = new ArrayList<>(lines(key + ".lore"));
        lore.add(Component.empty());
        if (selected) {
            lore.addAll(lines("perms.presets.active"));
        } else if (canEdit) {
            lore.addAll(lines("perms.presets.click"));
        } else {
            lore.addAll(lines("perms.presets.locked"));
        }
        ItemStack item = service.item(new ItemStack(icon), text(selected ? "perms.presets.name-active" : "perms.presets.name",
                Msg.c("preset", text(key + ".word"))), lore);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return MenuButton.display(item);
        }
        if (!canEdit) {
            return MenuButton.of(item, click -> {
                islands.fail(viewer, "perms.presets.not-allowed");
            });
        }
        return MenuButton.of(item, click -> choose(preset, active.isEmpty()));
    }

    private void choose(PermPresets.Preset preset, boolean custom) {
        if (pending) {
            return;
        }
        if (islands.byId(island.id()) != island || !canEditAny()) {
            islands.fail(viewer, "perms.no-longer");
            refresh();
            return;
        }
        List<PermPresets.Change> changes = presets().changes(preset, island.perms(), islands.rolePermissions(), editable());
        if (changes.isEmpty()) {
            module.msg().send(viewer, "perms.presets.already", Msg.c("preset", text("perms.presets." + preset.key() + ".word")));
            return;
        }
        if (!custom) {
            apply(preset);
            return;
        }
        // G8: a custom setup is the island's own work; ask before replacing it.
        TagResolver[] p = {Msg.c("preset", text("perms.presets." + preset.key() + ".word")), Msg.number("cells", changes.size())};
        openChild(new ConfirmMenu(v -> text("perms.presets.confirm.crumb", p),
                service.item(new ItemStack(Material.REPEATER), text("perms.presets.confirm.shown", p), List.of()),
                v -> lines("perms.presets.confirm.details", p),
                player -> {
                    apply(preset);
                    service.open(player, this);
                }, null));
    }

    private void apply(PermPresets.Preset preset) {
        if (islands.byId(island.id()) != island) {
            islands.fail(viewer, "perms.no-longer");
            return;
        }
        // Recomputed now: the island or the viewer's role may have changed since the menu was drawn.
        List<PermPresets.Change> changes = presets().changes(preset, island.perms(), islands.rolePermissions(), editable());
        pending = true;
        islands.applyPermPreset(island.id(), preset, changes, Actor.player(viewer)).whenComplete((cells, error) ->
                module.core().scheduler().ensureMain("island-perm-preset", () -> {
                    pending = false;
                    if (error != null || cells == null || cells < 0) {
                        islands.fail(viewer, "error.database");
                    } else {
                        module.msg().send(viewer, "perms.presets.applied", Msg.c("preset", text("perms.presets." + preset.key() + ".word")),
                                Msg.number("cells", cells));
                        module.core().sounds().play(viewer, "success");
                    }
                    if (isOpen()) {
                        refresh();
                    }
                }));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
