package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pick a preset's icon: its own tools and sets first, then the icons in {@code loadout.icons}. Click-only. */
final class LoadoutIconMenu extends PagedMenu<Material> {

    private final WardrobeModule module;
    private final UUID loadoutId;

    LoadoutIconMenu(WardrobeModule module, UUID loadoutId) {
        this.module = module;
        this.loadoutId = loadoutId;
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.loadout.icon-info";
    }

    @Override
    public String helpTopic() {
        return "loadout";
    }

    @Override
    protected String emptyKey() {
        return "menu.loadout.icon-empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.loadout.title"), module.menus().text(viewer, "menu.loadout.icon-title"));
    }

    private Optional<Loadout> preset() {
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        return s == null ? Optional.empty() : s.loadout(loadoutId);
    }

    @Override
    protected List<Material> entries() {
        Set<Material> out = new LinkedHashSet<>();
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        Optional<Loadout> l = preset();
        if (s != null && l.isPresent()) {
            for (Loadout.Tool t : l.get().hotbar().values()) {
                add(out, t.material());
            }
            for (UUID entry : l.get().entries()) {
                s.entry(entry).flatMap(e -> module.registry().get(e.look()))
                        .ifPresent(def -> out.add(module.items().icon(def, ArmorPiece.CHEST).getType()));
            }
        }
        for (String id : module.config().loadoutIcons) {
            add(out, id);
        }
        return new ArrayList<>(out);
    }

    private static void add(Set<Material> out, String id) {
        Material m = id == null || id.isBlank() ? null : Material.matchMaterial(id.trim());
        if (m != null && m.isItem() && !m.isAir()) {
            out.add(m);
        }
    }

    @Override
    protected MenuButton entry(Material material) {
        return MenuButton.of(service.item(material, module.menus().text(viewer, "menu.loadout.icon-pick.name"),
                module.menus().lines(viewer, "menu.loadout.icon-pick.lore")), click -> preset().ifPresent(l ->
                module.loadouts().setIcon(viewer, l, material.getKey().getKey(), () -> {
                    if (isOpen()) {
                        back();
                    }
                })));
    }
}
