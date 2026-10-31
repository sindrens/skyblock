package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /crates}: your keys (slot 4) and every crate in a row (hidden ones as "Coming soon"). Java: left-click =
 * preview, right-click = open 1, shift-click = open 10. Bedrock: a tap opens the preview, which has buttons for
 * everything (no right or shift clicks needed).
 */
final class CratesMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    /** R3-18: key fragments → Phoenix keys. */
    static final int SLOT_FRAGMENTS = 40;
    static final int[] CRATE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final CratesModule m;
    private boolean loading;

    CratesMenu(CratesModule module) {
        this.m = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, CratesModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return CratesModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "crates";
    }

    @Override
    protected void build() {
        frame();
        UUID profile = m.profiles().profileKey(viewer);
        CratesModule.ProfileView view = m.view(profile);
        if (view == null) {
            set(SLOT_SUMMARY, service.item(Material.CLOCK, viewer, CratesModule.ID, "menu.loading"));
            if (!loading) {
                loading = true;
                m.loadView(profile).whenComplete((v, e) -> refresh());
            }
            return;
        }
        set(SLOT_SUMMARY, summary(view));
        List<CrateCatalog.Crate> crates = m.catalog().crates();
        for (int i = 0; i < crates.size() && i < CRATE_SLOTS.length; i++) {
            CrateCatalog.Crate crate = crates.get(i);
            if (crate.hidden()) {
                set(CRATE_SLOTS[i], service.item(m.icon(crate), m.mini(viewer, m.crateMini(crate)),
                        service.lines(viewer, CratesModule.ID, "menu.crate.coming-soon")));
                continue;
            }
            set(CRATE_SLOTS[i], crateItem(crate, view), click -> click(click.player(), crate, click.isLeft(), click.isRight(),
                    click.isShift()));
        }
        if (crates.isEmpty()) {
            set(22, service.item(Material.COBWEB, viewer, CratesModule.ID, "menu.empty"));
        }
        if (m.fragments().enabled()) {
            set(SLOT_FRAGMENTS, fragmentsItem(), click -> {
                int want = click.isShift() || click.isRight() ? m.settings.fragmentsMaxKeysPerClick : 1;
                m.fragments().exchange(click.player(), want, r -> {
                    if (isOpen()) {
                        refresh();
                    }
                });
            });
        }
    }

    /** R3-18 tile: how many fragments the viewer carries and how to combine them (Bedrock: one tap = one key). */
    private org.bukkit.inventory.ItemStack fragmentsItem() {
        int have = m.fragments().count(viewer);
        int per = m.settings.fragmentsPerKey;
        List<Component> lore = new ArrayList<>(service.lines(viewer, CratesModule.ID, "menu.fragments.lore",
                Msg.number("have", have), Msg.number("need", per), Msg.mm("key", m.keyMini(m.settings.fragmentsCrate))));
        String actions = have < per ? "menu.fragments.actions-none" : m.bedrock(viewer) ? "menu.fragments.actions-bedrock"
                : "menu.fragments.actions";
        lore.addAll(service.lines(viewer, CratesModule.ID, actions, Msg.number("keys", FragmentExchange.keysFor(have, per,
                m.settings.fragmentsMaxKeysPerClick)), Msg.number("need", per)));
        return service.item(Material.TRIPWIRE_HOOK, service.text(viewer, CratesModule.ID, "menu.fragments.name"), lore);
    }

    private org.bukkit.inventory.ItemStack summary(CratesModule.ProfileView view) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, CratesModule.ID, "menu.summary.lore"));
        for (CrateCatalog.Crate crate : m.catalog().visible()) {
            CrateStore.Keys k = view.keys(crate.id());
            lore.add(m.msg.component(viewer, k.tradeable() > 0 ? "menu.summary.line-tradeable" : "menu.summary.line",
                    Msg.mm("key", m.keyMini(crate.id())), Msg.number("keys", k.total()), Msg.number("tradeable", k.tradeable())));
        }
        return service.item(Material.TRIPWIRE_HOOK, service.text(viewer, CratesModule.ID, "menu.summary.name"), lore);
    }

    private org.bukkit.inventory.ItemStack crateItem(CrateCatalog.Crate crate, CratesModule.ProfileView view) {
        CrateStore.Keys k = view.keys(crate.id());
        int counter = view.counter(crate.id(), m.season());
        String key = crate.pity() > 0 && crate.hasTop() ? "menu.crate.lore" : "menu.crate.lore-no-pity";
        List<Component> lore = new ArrayList<>(service.lines(viewer, CratesModule.ID, key, Msg.number("keys", k.total()),
                Msg.number("tradeable", k.tradeable()), Msg.number("pity", counter), Msg.number("max", crate.pity()),
                Msg.p("top", CrateMath.percent(crate.topPercent()))));
        lore.addAll(service.lines(viewer, CratesModule.ID, m.bedrock(viewer) ? "menu.crate.actions-bedrock" : "menu.crate.actions",
                Msg.number("bulk", m.settings.bulkAmount)));
        return service.item(m.icon(crate), m.mini(viewer, m.crateMini(crate)), lore);
    }

    private void click(Player player, CrateCatalog.Crate crate, boolean left, boolean right, boolean shift) {
        if (m.bedrock(player) || (left && !shift)) {
            openChild(new PreviewMenu(m, crate));
            return;
        }
        int amount = shift ? m.settings.bulkAmount : 1;
        if (right || shift) {
            m.opener().open(player, crate, amount, plan -> {
                if (plan != null && plan.grants().size() > 1 && isOpen()) {
                    m.opener().showResult(player, plan, this);
                } else {
                    refresh();
                }
            });
        }
    }
}
