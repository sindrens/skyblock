package net.skybornmc.skyblock.profiles;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/** Picks a profile icon from {@code icons.choices}. One click sets it and goes back. */
final class IconMenu extends PagedMenu<Material> {

    private final ProfilesModule module;
    private final UUID profileId;

    IconMenu(ProfilesModule module, UUID profileId) {
        this.module = module;
        this.profileId = profileId;
    }

    @Override
    protected List<Material> entries() {
        return module.iconChoices();
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.msg.component(viewer, "menu.crumb"), module.msg.component(viewer, "icon.crumb"));
    }

    @Override
    public String messageModule() {
        return ProfilesModule.ID;
    }

    @Override
    public String infoKey() {
        return "icon.info";
    }

    @Override
    public String helpTopic() {
        return "profiles";
    }

    @Override
    protected String emptyKey() {
        return "icon.empty";
    }

    @Override
    protected MenuButton entry(Material icon) {
        Material current = module.service().session(viewer.getUniqueId()).flatMap(s -> s.profile(profileId))
                .map(module::icon).orElse(null);
        boolean selected = current == icon;
        ItemStack item = service.item(new ItemStack(icon), module.msg.component(viewer, selected ? "icon.current" : "icon.pick"), List.of());
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> module.service().setIcon(click.player(), profileId, icon, () -> {
            if (isOpen()) {
                back();
            }
        }));
    }
}
