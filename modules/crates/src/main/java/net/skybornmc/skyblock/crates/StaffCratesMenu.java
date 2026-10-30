package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;

/**
 * {@code /crates admin view <player>} for staff in game (P10-41): the keys (bound and tradeable) and pity of one profile
 * per crate, read-only. The head button in the top middle switches to the player's next profile (the same picker as
 * the staff box view); it starts on the active profile. Bedrock-safe: plain taps only. Data is read fresh from the
 * database for every profile shown and never cached.
 */
final class StaffCratesMenu extends Menu {

    static final int SLOT_PROFILE = 4;
    static final int SLOT_LOADING = 22;

    private final CratesModule m;
    private CratesAdmin.Target target;
    private CratesModule.ProfileView view;
    private boolean failed;
    /** Bumped on every profile switch, so a slow load of the previous profile can't overwrite the new one. */
    private int generation;

    StaffCratesMenu(CratesModule module, CratesAdmin.Target target) {
        this.m = module;
        this.target = target;
        load();
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, CratesModule.ID, "admin.menu.title", Msg.p("player", target.name())));
    }

    @Override
    public String messageModule() {
        return CratesModule.ID;
    }

    @Override
    public String infoKey() {
        return "admin.menu.info";
    }

    @Override
    public String helpTopic() {
        return "crates";
    }

    private void load() {
        int mine = ++generation;
        view = null;
        failed = false;
        m.readView(target.key()).whenComplete((v, error) -> {
            if (mine != generation) {
                return;
            }
            view = v;
            failed = error != null;
            if (isOpen()) {
                refresh();
            }
        });
    }

    @Override
    protected void build() {
        frame();
        boolean many = target.keys().size() > 1;
        set(SLOT_PROFILE, service.item(Material.ARMOR_STAND, viewer, CratesModule.ID, many ? "admin.menu.profile-switch" : "admin.menu.profile",
                Msg.p("profile", target.profileName()), Msg.number("profiles", target.keys().size()), Msg.p("player", target.name())),
                click -> {
                    if (target.keys().size() > 1) {
                        target = target.next();
                        load();
                        refresh();
                    }
                });
        if (failed) {
            set(SLOT_LOADING, service.item(Material.BARRIER, viewer, CratesModule.ID, "admin.menu.failed"));
            return;
        }
        if (view == null) {
            set(SLOT_LOADING, service.item(Material.CLOCK, viewer, CratesModule.ID, "admin.menu.loading"));
            return;
        }
        List<CrateCatalog.Crate> crates = m.catalog().crates();
        for (int i = 0; i < crates.size() && i < CratesMenu.CRATE_SLOTS.length; i++) {
            CrateCatalog.Crate crate = crates.get(i);
            CrateStore.Keys k = view.keys(crate.id());
            boolean pity = crate.pity() > 0 && crate.hasTop();
            List<Component> lore = service.lines(viewer, CratesModule.ID, pity ? "admin.menu.crate" : "admin.menu.crate-no-pity",
                    Msg.number("bound", k.bound()), Msg.number("tradeable", k.tradeable()), Msg.number("keys", k.total()),
                    Msg.number("pity", view.counter(crate.id(), m.season())), Msg.number("max", crate.pity()),
                    Msg.p("profile", target.profileName()));
            set(CratesMenu.CRATE_SLOTS[i], service.item(m.icon(crate), m.mini(viewer, m.crateMini(crate)), lore));
        }
    }
}
