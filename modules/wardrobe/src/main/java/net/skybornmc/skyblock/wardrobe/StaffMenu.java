package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff view of a player's wardrobe (the Wardrobe button in {@code /inspect}, WD25): their active profile's sets and
 * chest size; admins ({@code skyblock.wardrobe.admin}) can remove a set (confirm), give a set or give a Set Token. All
 * audited. Clicking an entry opens its admin actions (set level, remove).
 */
final class StaffMenu extends PagedMenu<WardrobeStore.Entry> {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);

    private final WardrobeModule module;
    final UUID target;
    final String targetName;
    final UUID profile;
    private final List<WardrobeStore.Entry> entries;
    private final int stored;

    StaffMenu(WardrobeModule module, UUID target, String targetName, UUID profile, List<WardrobeStore.Entry> entries, int stored) {
        this.module = module;
        this.target = target;
        this.targetName = targetName;
        this.profile = profile;
        this.entries = List.copyOf(entries);
        this.stored = stored;
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.staff.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected String emptyKey() {
        return "menu.staff.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.staff.title", Msg.p("player", targetName)));
    }

    @Override
    protected List<WardrobeStore.Entry> entries() {
        return entries;
    }

    private boolean admin() {
        return viewer.hasPermission(WardrobeModule.PERM_ADMIN);
    }

    @Override
    protected MenuButton entry(WardrobeStore.Entry e) {
        Optional<SetDef> def = module.registry().get(e.setId());
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.staff.entry",
                Msg.p("origin", e.origin()), Msg.p("date", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(e.createdAt()))),
                Msg.p("level", e.level())));
        if (admin()) {
            lore.addAll(module.menus().lines(viewer, "menu.staff.manage-click"));
        }
        Component name = module.core.messages().parse(viewer, module.text().entryColored(module.registry(), e));
        var look = module.registry().get(e.look()).or(() -> def);
        var icon = look.map(d -> module.items().icon(d, ArmorPiece.CHEST)).orElse(new org.bukkit.inventory.ItemStack(Material.LEATHER_CHESTPLATE));
        return MenuButton.of(service.item(icon, name, lore), admin() ? click -> openChild(new StaffEntryMenu(module, this, e)) : null);
    }

    @Override
    protected void decorate() {
        set(4, service.item(Material.CHEST, viewer, WardrobeModule.ID, "menu.staff.chest", Msg.p("count", stored)));
        if (admin()) {
            set(47, service.item(Material.EMERALD, viewer, WardrobeModule.ID, "menu.staff.give-set"),
                    click -> openChild(new StaffPickMenu(module, this, false)));
            set(51, service.item(Material.ARMOR_STAND, viewer, WardrobeModule.ID, "menu.staff.give-token"),
                    click -> openChild(new StaffPickMenu(module, this, true)));
            for (String kind : List.of(Tomes.NORMAL, Tomes.GREATER)) {
                int slot = Tomes.NORMAL.equals(kind) ? 52 : 46;
                long xp = module.tomes().xpOf(kind);
                set(slot, service.item(Material.BOOK, viewer, WardrobeModule.ID, "menu.staff.give-tome." + kind, Msg.p("xp", WardrobeText.number(xp))),
                        click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "confirm.give-tome.crumb"), null,
                                v -> module.menus().lines(v, "confirm.give-tome.details", Msg.p("xp", WardrobeText.number(xp)), Msg.p("player", targetName)),
                                p -> module.staff().giveTome(p, this, kind), p -> {
                        })));
            }
        }
    }
}
