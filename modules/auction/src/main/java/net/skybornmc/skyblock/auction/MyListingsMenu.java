package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** The player's own open listings (active, pending, being bought). Click an active one to cancel it (confirm dialog). */
final class MyListingsMenu extends PagedMenu<Listing> {

    private final AuctionModule module;
    private final List<Listing> rows;

    private MyListingsMenu(AuctionModule module, List<Listing> rows) {
        this.module = module;
        this.rows = rows;
    }

    /** Loads the listings, then opens the menu (as a child of {@code parent}, may be null). */
    static void open(AuctionModule module, Player player, Menu parent) {
        UUID uuid = player.getUniqueId();
        module.engine().store().open(uuid).whenComplete((rows, error) -> module.core.scheduler().main("auction:mine", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                return;
            }
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Auction: loading listings failed", Database.cause(error));
                module.send(p, "error");
                return;
            }
            MyListingsMenu menu = new MyListingsMenu(module, rows);
            menu.parent(parent);
            module.core.menus().open(p, menu);
        }));
    }

    @Override
    public String messageModule() {
        return AuctionModule.ID;
    }

    @Override
    public String infoKey() {
        return "mine.info";
    }

    @Override
    public String helpTopic() {
        return "auction";
    }

    @Override
    protected String emptyKey() {
        return "mine.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, AuctionModule.ID, "browse.title"), service.text(viewer, AuctionModule.ID, "mine.title"));
    }

    @Override
    protected List<Listing> entries() {
        return rows;
    }

    @Override
    protected void decorate() {
        set(4, service.item(org.bukkit.Material.BOOK, viewer, AuctionModule.ID, "mine.summary",
                Msg.number("used", rows.size()), Msg.number("slots", module.slots(viewer))));
    }

    @Override
    protected MenuButton entry(Listing l) {
        String action = switch (l.status()) {
            case ACTIVE -> l.remaining(System.currentTimeMillis()) > 0 ? "mine.entry.active" : "mine.entry.ending";
            case BUYING -> "mine.entry.buying";
            default -> "mine.entry.pending";
        };
        ItemStack item = AuctionMenu.listingItem(module, viewer, l, action);
        if (l.status() != ListingStatus.ACTIVE) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> openChild(cancelConfirm(module, viewer, l)));
    }

    /** "Cancel this listing?": the item goes to the collection bin (or straight into the inventory if it fits). */
    static ConfirmMenu cancelConfirm(AuctionModule module, Player viewer, Listing l) {
        TagResolver[] p = {Msg.p("item", l.item().name()), Msg.number("amount", l.item().amount()),
                module.moneyTag("price", viewer, l.price()), module.moneyTag("fee", viewer, l.fee())};
        String lines = module.config.feeRefundOnCancel ? "cancel.confirm-lines-refund" : "cancel.confirm-lines";
        return new ConfirmMenu(v -> module.core.menus().text(v, AuctionModule.ID, "cancel.crumb"), module.displayItem(l),
                v -> module.core.menus().lines(v, AuctionModule.ID, lines, p),
                player -> module.startCancel(player, l, false, null), null);
    }
}
