package net.skybornmc.skyblock.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

import java.time.Duration;
import java.util.List;

/**
 * Owner R3-6: the waiting {@code /eco} changes (above $1T) for owners and admins; the Bedrock way to Allow (Java players
 * can also click the chat alert). Tap one: Yes = Allow, No = Deny. Opened from the economy dashboard ({@code /eco admin}).
 */
final class PendingEcoMenu extends PagedMenu<EcoApprovals.Pending> {

    private final EconomyModule module;

    PendingEcoMenu(EconomyModule module) {
        this.module = module;
    }

    @Override
    protected List<EcoApprovals.Pending> entries() {
        return module.approvals().list(System.currentTimeMillis());
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, EconomyModule.ID, "eco.pending-menu.title"));
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "eco.pending-menu.info";
    }

    @Override
    public String helpTopic() {
        return "economy";
    }

    @Override
    protected String emptyKey() {
        return "eco.pending-menu.empty";
    }

    @Override
    public int tickInterval() {
        return 100;
    }

    @Override
    protected void tick() {
        refresh();
    }

    private TagResolver[] parts(EcoApprovals.Pending p) {
        long left = Math.max(0, p.expires() - System.currentTimeMillis());
        return new TagResolver[]{Msg.number("id", p.id()), Msg.p("staff", p.staffName()), Msg.p("player", p.targetName()),
                Msg.p("action", p.action().key()), module.exactAmountTag("amount", viewer, p.currency(), p.amount()),
                module.exactAmountTag("change", viewer, p.currency(), p.change()),
                Msg.c("currency", module.currencyName(viewer, p.currency())), Msg.p("reason", p.note()),
                Msg.p("left", Format.duration(Duration.ofMillis(left)))};
    }

    @Override
    protected MenuButton entry(EcoApprovals.Pending p) {
        TagResolver[] parts = parts(p);
        return MenuButton.of(service.item(Material.PAPER, viewer, EconomyModule.ID, "eco.pending-menu.entry", parts), click -> {
            ConfirmMenu confirm = new ConfirmMenu(v -> service.text(v, EconomyModule.ID, "eco.pending-menu.confirm-crumb"),
                    service.item(Material.PAPER, viewer, EconomyModule.ID, "eco.pending-menu.entry", parts),
                    v -> service.lines(v, EconomyModule.ID, "eco.pending-menu.confirm", parts),
                    pl -> module.decide(pl, p.id(), true),
                    pl -> module.decide(pl, p.id(), false));
            openChild(confirm);
        });
    }
}
