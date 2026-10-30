package net.skybornmc.skyblock.profiles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.SlotRules;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /profiles}: one big button per slot (11, 13, 15) so it works with taps on Bedrock. A profile opens its own
 * screen (switch, rename, icon, delete); an empty unlocked slot creates a profile; a locked slot can be bought with gems.
 */
final class ProfilesMenu extends Menu {

    static final int[] SLOTS = {11, 13, 15};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final ProfilesModule module;

    ProfilesMenu(ProfilesModule module) {
        this.module = module;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, ProfilesModule.ID, "menu.title");
    }

    @Override
    public String messageModule() {
        return ProfilesModule.ID;
    }

    @Override
    public String helpTopic() {
        return "profiles";
    }

    @Override
    protected void build() {
        frame();
        ProfileServiceImpl profiles = module.service();
        Optional<ProfileServiceImpl.Session> session = profiles.session(viewer.getUniqueId());
        if (session.isEmpty()) {
            set(13, service.item(Material.CLOCK, viewer, ProfilesModule.ID, "menu.loading"));
            return;
        }
        for (int slot = 1; slot <= SlotRules.MAX_SLOTS; slot++) {
            int menuSlot = SLOTS[slot - 1];
            Optional<Profile> profile = session.get().inSlot(slot);
            if (profile.isPresent()) {
                Profile p = profile.get();
                set(menuSlot, profileItem(module, viewer, p, session.get().active.equals(p.id()), "menu.profile"),
                        click -> openChild(new ProfileMenu(module, p.id())));
                continue;
            }
            int s = slot;
            if (profiles.slotUnlocked(viewer, slot)) {
                set(menuSlot, service.item(module.defaultIcon(slot), viewer, ProfilesModule.ID, "menu.empty", Msg.number("slot", slot)),
                        click -> profiles.create(click.player(), s, this::refresh));
                continue;
            }
            long price = module.config.price(slot);
            String key = price > 0 ? "menu.locked" : "menu.locked-rank";
            set(menuSlot, service.item(Material.GRAY_DYE, viewer, ProfilesModule.ID, key, Msg.number("slot", slot),
                    Msg.p("price", Format.number(price)), Msg.c("rank", module.msg.component(viewer, "menu.rank-" + slot))),
                    click -> {
                        if (price > 0) {
                            buy(s, price);
                        } else {
                            module.fail(click.player(), "slots.not-for-sale");
                        }
                    });
        }
    }

    private void buy(int slot, long price) {
        ItemStack shown = service.item(Material.EMERALD, viewer, ProfilesModule.ID, "slots.confirm.shown", Msg.number("slot", slot),
                Msg.p("price", Format.number(price)));
        openChild(new ConfirmMenu(v -> module.msg.component(v, "slots.confirm.crumb"), shown,
                v -> service.lines(v, ProfilesModule.ID, "slots.confirm.details", Msg.number("slot", slot), Msg.p("price", Format.number(price))),
                p -> module.service().buySlot(p, slot, () -> module.open(p)),
                p -> { }));
    }

    /** The item of a profile with name, island, level, dates and playtime (also used on the profile's own screen). */
    static ItemStack profileItem(ProfilesModule module, org.bukkit.entity.Player viewer, Profile p, boolean active, String key) {
        ProfileServiceImpl profiles = module.service();
        Optional<IslandService.IslandInfo> island = module.core.services().optional(IslandService.class)
                .flatMap(s -> s.islandOfProfile(p.id()));
        String islandName = island.map(IslandService.IslandInfo::name).orElse(module.core.messages().raw(ProfilesModule.ID, "menu.no-island"));
        String level = island.isEmpty() ? "-" : active
                ? module.core.placeholders().text(viewer, "island_level").filter(v -> !v.isBlank()).orElse("-") : "-";
        List<TagResolver> tags = new ArrayList<>();
        tags.add(Msg.p("profile", profiles.name(p)));
        tags.add(Msg.number("slot", p.slot()));
        tags.add(Msg.p("island", islandName));
        tags.add(Msg.p("level", level));
        tags.add(Msg.p("created", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(p.createdAt()))));
        tags.add(Msg.p("playtime", Format.duration(Duration.ofMillis(profiles.playtime(viewer.getUniqueId(), p)))));
        tags.add(Msg.c("status", module.msg.component(viewer, active ? "menu.status-active"
                : profiles.slotUnlocked(viewer, p.slot()) ? "menu.status-switch" : "menu.status-locked")));
        ItemStack item = module.core.menus().item(module.icon(p), viewer, ProfilesModule.ID, key, tags.toArray(TagResolver[]::new));
        if (active) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }
}
