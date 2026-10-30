package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Edit one preset (WARDROBE §7.5): preview of everything it holds, Apply, Save current (confirm), Rename (chat),
 * Icon (pick from a list), Delete (confirm). Click-only, Bedrock-safe.
 * <pre>
 *  13 preview
 *  20 apply · 21 save current · 22 rename · 23 icon · 24 delete
 * </pre>
 */
final class LoadoutEditMenu extends Menu {

    private final WardrobeModule module;
    private final UUID loadoutId;
    private final Set<UUID> chest;

    LoadoutEditMenu(WardrobeModule module, UUID loadoutId, Set<UUID> chest) {
        this.module = module;
        this.loadoutId = loadoutId;
        this.chest = Set.copyOf(chest);
    }

    @Override
    public int rows() {
        return 6;
    }

    private Optional<Loadout> preset() {
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        return s == null ? Optional.empty() : s.loadout(loadoutId);
    }

    @Override
    public Component title() {
        String name = preset().map(Loadout::name).orElse("?");
        return service.title(viewer, module.menus().text(viewer, "menu.loadout.title"), module.menus().text(viewer, "menu.loadout.edit-title",
                Msg.p("name", name)));
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.loadout.edit-info";
    }

    @Override
    public String helpTopic() {
        return "loadout";
    }

    @Override
    protected void build() {
        frame();
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        Optional<Loadout> found = preset();
        if (s == null || found.isEmpty()) {
            set(22, service.item(Material.BARRIER, viewer, WardrobeModule.ID, "menu.loadout.gone"));
            return;
        }
        Loadout l = found.get();
        boolean usable = l.index() < module.loadouts().slots(viewer, s);
        List<Component> preview = new ArrayList<>(LoadoutMenu.contents(module, viewer, s, l, chest));
        set(13, service.item(LoadoutMenu.icon(l.icon(), module.config().loadoutDefaultIcon),
                module.menus().text(viewer, "menu.loadout.preset.name", Msg.p("name", l.name())), preview));
        set(20, service.item(Material.NETHER_STAR, viewer, WardrobeModule.ID, usable ? "menu.loadout.apply" : "menu.loadout.apply-locked"),
                click -> {
                    if (usable) {
                        module.loadouts().apply(viewer, l);
                    } else {
                        module.fail(viewer, "loadout.locked");
                    }
                });
        WardrobeMenus m = module.menus();
        set(21, service.item(Material.CHEST, viewer, WardrobeModule.ID, "menu.loadout.save"), click -> openChild(new ConfirmMenu(
                v -> m.text(v, "confirm.loadout-save.crumb"), service.item(Material.CHEST, viewer, WardrobeModule.ID, "menu.loadout.save"),
                v -> m.lines(v, "confirm.loadout-save.details", Msg.p("name", l.name())),
                p -> {
                    module.loadouts().overwrite(p, l);
                    module.loadouts().open(p, null);
                }, p -> { })));
        set(22, service.item(Material.NAME_TAG, viewer, WardrobeModule.ID, "menu.loadout.rename", Msg.p("max", module.config().loadoutNameMax)),
                click -> module.loadouts().promptRename(viewer, l, () -> module.loadouts().open(viewer, null)));
        set(23, service.item(Material.ITEM_FRAME, viewer, WardrobeModule.ID, "menu.loadout.icon"),
                click -> openChild(new LoadoutIconMenu(module, l.id())));
        set(24, service.item(Material.TNT, viewer, WardrobeModule.ID, "menu.loadout.delete"), click -> openChild(new ConfirmMenu(
                v -> m.text(v, "confirm.loadout-delete.crumb"), service.item(Material.TNT, viewer, WardrobeModule.ID, "menu.loadout.delete"),
                v -> m.lines(v, "confirm.loadout-delete.details", Msg.p("name", l.name())),
                p -> module.loadouts().delete(p, l), p -> { })));
    }
}
