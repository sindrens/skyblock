package net.skybornmc.skyblock.profiles;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * One profile: switch (10), rename (12), the profile (13), icon (14), delete (16); Back 18, Close 22. Big targets for Bedrock.
 * Delete asks twice: a confirm screen, then the profile's name typed in chat.
 */
final class ProfileMenu extends Menu {

    private final ProfilesModule module;
    private final UUID profileId;

    ProfileMenu(ProfilesModule module, UUID profileId) {
        this.module = module;
        this.profileId = profileId;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        String name = profile().map(p -> module.service().name(p)).orElse("?");
        return service.title(viewer, module.msg.component(viewer, "menu.crumb"), Component.text(name));
    }

    @Override
    public String messageModule() {
        return ProfilesModule.ID;
    }

    @Override
    public String helpTopic() {
        return "profiles";
    }

    private Optional<Profile> profile() {
        return module.service().session(viewer.getUniqueId()).flatMap(s -> s.profile(profileId));
    }

    @Override
    protected void build() {
        frame();
        Optional<Profile> found = profile();
        if (found.isEmpty()) {
            set(13, service.item(Material.BARRIER, viewer, ProfilesModule.ID, "menu.gone"));
            return;
        }
        Profile p = found.get();
        ProfileServiceImpl profiles = module.service();
        boolean active = profiles.profileKey(viewer.getUniqueId()).equals(p.id());
        String name = profiles.name(p);
        set(13, ProfilesMenu.profileItem(module, viewer, p, active, "manage.shown"));
        if (active) {
            set(10, service.item(Material.LIME_DYE, viewer, ProfilesModule.ID, "manage.playing", Msg.p("profile", name)));
        } else {
            set(10, service.item(Material.ENDER_PEARL, viewer, ProfilesModule.ID, "manage.switch", Msg.p("profile", name)),
                    click -> {
                        click.player().closeInventory();
                        profiles.switchTo(click.player(), p.id());
                    });
        }
        set(12, service.item(Material.NAME_TAG, viewer, ProfilesModule.ID, "manage.rename", Msg.p("profile", name)),
                click -> rename(click.player(), p));
        set(14, service.item(module.icon(p), viewer, ProfilesModule.ID, "manage.icon", Msg.p("profile", name)),
                click -> openChild(new IconMenu(module, p.id())));
        if (active) {
            set(16, service.item(Material.GRAY_DYE, viewer, ProfilesModule.ID, "manage.delete-active", Msg.p("profile", name)));
        } else {
            set(16, service.item(Material.TNT, viewer, ProfilesModule.ID, "manage.delete", Msg.p("profile", name)),
                    click -> confirmDelete(p, name));
        }
    }

    private void rename(Player player, Profile p) {
        service.prompt(player, ProfilesModule.ID, "rename.type", module.config.nameMaxLength + 8,
                input -> module.service().rename(player, p.id(), input, () -> module.open(player)),
                () -> { }, Msg.number("max", module.config.nameMaxLength));
    }

    private void confirmDelete(Profile p, String name) {
        ItemStack shown = service.item(Material.TNT, viewer, ProfilesModule.ID, "delete.shown", Msg.p("profile", name));
        // GUI-1: a profile delete can't be undone, so the confirm asks twice (second screen swaps the buttons).
        openChild(new ConfirmMenu(v -> module.msg.component(v, "delete.crumb"), shown,
                v -> service.lines(v, ProfilesModule.ID, "delete.details", Msg.p("profile", name)),
                player -> module.service().delete(player, p.id()),
                player -> { }).twoStep());
    }
}
