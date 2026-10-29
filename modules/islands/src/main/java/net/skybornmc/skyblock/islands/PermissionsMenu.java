package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The island permission matrix (M2.3, UI_STYLE §5). Rows = roles, columns = the 14 {@link IslandPerm} actions,
 * 7 per page (2 pages):
 * <pre>
 *  R [p1][p2][p3][p4][p5][p6][p7] R     row 0: the actions of this page (headers)
 *  Mod   ■  ■  ■  ■  ■  ■  ■  B         rows 1-4: Mod, Member, Coop, Visitor; lime = allowed, red = denied
 *  Member■  ■  ■  ■  ■  ■  ■  B         concrete = you can click it, glass = locked for you
 *  Coop  ■  ■  ■  ■  ■  ■  ■  B
 *  Visit ■  ■  ■  ■  ■  ■  ■  B
 *  ← . . ? X ⓘ . . →                    45 back/previous, 48 info, 49 close, 50 help, 53 next
 * </pre>
 * One plain click toggles a cell (Bedrock-safe, no right/shift clicks); a value equal to the server default is stored
 * as "follow the default" ({@link MemberRules#toggledOverride}). The leader always has everything (shown in the info
 * item). Who may click: the leader every row, a mod Member/Coop/Visitor; members see the matrix read-only.
 */
final class PermissionsMenu extends Menu {

    static final int PER_PAGE = 7;
    static final PermRole[] ROWS = {PermRole.MOD, PermRole.MEMBER, PermRole.COOP, PermRole.VISITOR};
    static final Map<IslandPerm, Material> ICONS = new EnumMap<>(IslandPerm.class);
    static final Map<PermRole, Material> ROLE_ICONS = new EnumMap<>(PermRole.class);

    static {
        ICONS.put(IslandPerm.BREAK, Material.IRON_PICKAXE);
        ICONS.put(IslandPerm.PLACE, Material.GRASS_BLOCK);
        ICONS.put(IslandPerm.INTERACT, Material.OAK_DOOR);
        ICONS.put(IslandPerm.CONTAINERS, Material.CHEST);
        ICONS.put(IslandPerm.REDSTONE, Material.REDSTONE);
        ICONS.put(IslandPerm.ANIMALS, Material.WHEAT);
        ICONS.put(IslandPerm.VILLAGERS, Material.EMERALD);
        ICONS.put(IslandPerm.PICKUP, Material.HOPPER);
        ICONS.put(IslandPerm.DROP, Material.DROPPER);
        ICONS.put(IslandPerm.ARMOR_STANDS, Material.ARMOR_STAND);
        ICONS.put(IslandPerm.VEHICLES, Material.OAK_BOAT);
        ICONS.put(IslandPerm.FIRE, Material.FLINT_AND_STEEL);
        ICONS.put(IslandPerm.ENDER_PEARLS, Material.ENDER_PEARL);
        ICONS.put(IslandPerm.SPAWNERS, Material.SPAWNER);
        ROLE_ICONS.put(PermRole.MOD, Material.GOLDEN_HELMET);
        ROLE_ICONS.put(PermRole.MEMBER, Material.IRON_HELMET);
        ROLE_ICONS.put(PermRole.COOP, Material.LEATHER_HELMET);
        ROLE_ICONS.put(PermRole.VISITOR, Material.SPYGLASS);
    }

    private final IslandsModule module;
    private final IslandManager islands;
    private final Island island;
    private final Set<Long> pending = new HashSet<>();
    private int page;

    PermissionsMenu(IslandsModule module, IslandManager islands, Island island) {
        this.module = module;
        this.islands = islands;
        this.island = island;
    }

    static int pages() {
        return (IslandPerm.cached().length + PER_PAGE - 1) / PER_PAGE;
    }

    /** Slot of a matrix cell: row 0-3 (roles), column 0-6 (actions of the page). */
    static int cellSlot(int row, int column) {
        return (row + 1) * 9 + 1 + column;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        Component suffix = pages() > 1 ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", pages())) : null;
        return service.title(viewer, List.of(text("menu.crumb"), text("perms.crumb")), suffix);
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "perms.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    private Role viewerRole() {
        return island.roleOrNull(viewer.getUniqueId());
    }

    @Override
    protected void build() {
        frame();
        IslandPerm[] perms = IslandPerm.cached();
        int from = page * PER_PAGE;
        for (int column = 0; column < PER_PAGE && from + column < perms.length; column++) {
            IslandPerm perm = perms[from + column];
            set(1 + column, service.item(new ItemStack(ICONS.getOrDefault(perm, Material.PAPER)),
                    text("perms.header.name", Msg.p("perm", permName(perm))),
                    lines("perms.header.lore", Msg.p("perm", permName(perm)), Msg.p("action", module.raw("protection.actions." + perm.key())))));
        }
        Role editor = viewerRole();
        for (int row = 0; row < ROWS.length; row++) {
            PermRole role = ROWS[row];
            boolean editable = MemberRules.canEditPerms(editor, role);
            set((row + 1) * 9, service.item(new ItemStack(ROLE_ICONS.get(role)), text("perms.role.name", Msg.p("role", roleName(role))),
                    lines(editable ? "perms.role.lore-edit" : "perms.role.lore-locked", Msg.p("role", roleName(role)))));
            for (int column = 0; column < PER_PAGE && from + column < perms.length; column++) {
                set(cellSlot(row, column), cell(role, perms[from + column], editable));
            }
        }
        if (page > 0) {
            set(SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous", Msg.p("page", page),
                    Msg.p("pages", pages())), click -> turn(-1));
        }
        if (page < pages() - 1) {
            set(SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next", Msg.p("page", page + 2),
                    Msg.p("pages", pages())), click -> turn(1));
        }
    }

    private net.skybornmc.skyblock.common.menu.MenuButton cell(PermRole role, IslandPerm perm, boolean editable) {
        RolePermissions defaults = islands.rolePermissions();
        boolean allowed = defaults.allows(island.perms(), role, perm);
        boolean byDefault = defaults.defaultAllows(role, perm);
        boolean changed = island.perms().override(role, perm) != null;
        Material material = editable
                ? (allowed ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                : (allowed ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS);
        TagResolver[] p = {Msg.p("role", roleName(role)), Msg.p("perm", permName(perm)),
                Msg.p("action", module.raw("protection.actions." + perm.key()))};
        List<Component> lore = new ArrayList<>(lines("perms.cell.lore", p));
        lore.addAll(lines(allowed ? "perms.cell.allowed" : "perms.cell.denied", p));
        lore.addAll(lines(changed ? "perms.cell.changed" : "perms.cell.default", Msg.p("default",
                module.raw(byDefault ? "perms.word.allowed" : "perms.word.denied"))));
        lore.add(Component.empty());
        long key = ((long) role.ordinal() << 32) | perm.ordinal();
        if (!editable) {
            lore.addAll(lines(role == PermRole.MOD && viewerRole() == Role.MOD ? "perms.cell.locked-mod" : "perms.cell.locked", p));
            return net.skybornmc.skyblock.common.menu.MenuButton.display(service.item(new ItemStack(material),
                    text("perms.cell.name", p), lore));
        }
        lore.addAll(lines(allowed ? "perms.cell.click-deny" : "perms.cell.click-allow", p));
        ItemStack item = service.item(new ItemStack(material), text("perms.cell.name", p), lore);
        return net.skybornmc.skyblock.common.menu.MenuButton.of(item, click -> {
            if (pending.contains(key)) {
                return;
            }
            // Re-check on click: the viewer's role or the island may have changed since the menu was built.
            if (islands.byId(island.id()) != island || !MemberRules.canEditPerms(viewerRole(), role)) {
                islands.fail(viewer, "perms.no-longer");
                refresh();
                return;
            }
            boolean now = islands.rolePermissions().allows(island.perms(), role, perm);
            Boolean next = MemberRules.toggledOverride(now, islands.rolePermissions().defaultAllows(role, perm));
            pending.add(key);
            islands.setRolePermission(island.id(), role, perm, next, Actor.player(viewer)).whenComplete((ok, error) ->
                    Bukkit.getScheduler().runTask(module.core().plugin(), () -> {
                        pending.remove(key);
                        if (error != null || !Boolean.TRUE.equals(ok)) {
                            islands.fail(viewer, "error.database");
                        } else {
                            module.core().sounds().play(viewer, "click");
                        }
                        refresh();
                    }));
        });
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        service.open(viewer, this);
    }

    private String roleName(PermRole role) {
        return module.raw("role." + role.key());
    }

    private String permName(IslandPerm perm) {
        return module.raw("perms.names." + perm.key());
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
