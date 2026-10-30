package net.skybornmc.skyblock.profiles;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Island profiles (PROF, owner decisions P1–P12 and round 3 PR1–PR5): up to 3 profiles per player, each with its own
 * island, money, crystals, shards, inventory, ender chest, XP, health/food/effects, skills, Depths unlocks, auction
 * listings, notes, settings and playtime. {@code /profiles} is the only way to switch (P5).
 *
 * <p>Publishes {@link ProfileService}. Settings: {@code profiles.yml}; texts: {@code messages/profiles.yml};
 * data: {@code db/profiles}. Other modules store per-profile data under {@link ProfileService#profileKey}.</p>
 */
public final class ProfilesModule implements SkyblockModule {

    static final String ID = "profiles";
    static final String PERM_USE = "skyblock.profiles.use";

    final Core core;
    final ModuleMessages msg;
    final ProfilesConfig config = new ProfilesConfig();
    private ProfileServiceImpl service;
    private ProfileGuards guards;

    public ProfilesModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        service = new ProfileServiceImpl(this, new ProfileStore(), new MainThread(core.plugin()));
        service.start();
        core.services().register(ID, ProfileService.class, service);
        core.plugin().getServer().getPluginManager().registerEvents(service, core.plugin());
        guards = new ProfileGuards(this, service);
        core.plugin().getServer().getPluginManager().registerEvents(guards, core.plugin());
        core.placeholders().registerText(ID, "profile_name", p -> p == null ? "" : service.activeName(p));
        core.placeholders().registerText(ID, "profile_slot",
                p -> p == null ? "" : service.active(p.getUniqueId()).map(pr -> String.valueOf(pr.slot())).orElse(""));
        core.commands().register(CommandSpec.command("profiles")
                .description("Your island profiles: switch, create, rename, delete")
                .category("Island")
                .permission(PERM_USE)
                .playerOnly()
                .aliases("profile")
                .executes(ctx -> open(ctx.player())));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
    }

    @Override
    public void disable() {
        if (service != null) {
            HandlerList.unregisterAll(service);
            service.stop();
        }
        if (guards != null) {
            HandlerList.unregisterAll(guards);
        }
    }

    ProfileServiceImpl service() {
        return service;
    }

    void open(Player player) {
        core.menus().open(player, new ProfilesMenu(this));
    }

    void fail(Player player, String key, TagResolver... placeholders) {
        if (player.isOnline()) {
            msg.send(player, key, placeholders);
            core.sounds().play(player, "error");
        }
    }

    /** The icon of a profile: its own pick, else the slot's default. */
    Material icon(Profile profile) {
        Material own = profile.icon() == null ? null : Material.matchMaterial(profile.icon());
        if (own != null && own.isItem() && !own.isAir()) {
            return own;
        }
        return defaultIcon(profile.slot());
    }

    Material defaultIcon(int slot) {
        List<String> defaults = config.defaultIcons;
        String key = slot >= 1 && slot <= defaults.size() ? defaults.get(slot - 1) : "grass_block";
        Material m = key == null ? null : Material.matchMaterial(key.trim());
        return m == null || !m.isItem() || m.isAir() ? Material.GRASS_BLOCK : m;
    }

    List<Material> iconChoices() {
        List<Material> list = new ArrayList<>();
        for (String key : config.iconChoices) {
            Material m = key == null ? null : Material.matchMaterial(key.trim());
            if (m != null && m.isItem() && !m.isAir() && !list.contains(m)) {
                list.add(m);
            }
        }
        return list;
    }
}
