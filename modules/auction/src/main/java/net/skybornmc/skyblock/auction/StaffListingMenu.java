package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;

/**
 * Staff view of someone else's listing ({@code skyblock.staff.auction.remove}): buy it like a player, or remove it with
 * one of the configured reasons (the item goes to the seller's collection bin; logged as {@code ah_cancel} by staff).
 */
final class StaffListingMenu extends Menu {

    static final int SLOT_ITEM = 10;
    static final int SLOT_BUY = 11;
    static final int FIRST_REASON = 12;
    static final int LAST_REASON = 16;

    private final AuctionModule module;
    private final Listing listing;
    private boolean used;

    StaffListingMenu(AuctionModule module, Listing listing) {
        this.module = module;
        this.listing = listing;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, AuctionModule.ID, "browse.title"),
                service.text(viewer, AuctionModule.ID, "staff.crumb"));
    }

    @Override
    public String messageModule() {
        return AuctionModule.ID;
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_ITEM, AuctionMenu.listingItem(module, viewer, listing, "staff.entry"));
        set(SLOT_BUY, service.item(Material.LIME_CONCRETE, viewer, AuctionModule.ID, "staff.buy",
                module.moneyTag("price", viewer, listing.price())), click -> openChild(AuctionMenu.buyConfirm(module, viewer, listing)));
        List<String> reasons = module.config.staffRemoveReasons;
        for (int i = 0; i < reasons.size() && FIRST_REASON + i <= LAST_REASON; i++) {
            String reason = reasons.get(i);
            set(FIRST_REASON + i, service.item(module.config.icon("staff-remove", Material.LAVA_BUCKET), viewer, AuctionModule.ID,
                    "staff.remove", Msg.p("reason", reason), Msg.p("seller", listing.sellerName())), click -> {
                if (used) {
                    return;
                }
                used = true;
                viewer.closeInventory();
                module.startCancel(viewer, listing, true, reason);
            });
        }
    }
}
