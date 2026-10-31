package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff view of someone else's listing ({@code skyblock.staff.auction.remove}): buy it like a player, or remove it with
 * one of the configured reasons (the item goes to the seller's collection bin; logged as {@code ah_cancel} by staff).
 *
 * <p>GUI-4c: Remove opens a pick list of every reason in {@code auction.yml → staff.remove-reasons} (was 5 fixed slots,
 * a sixth reason was dropped without notice), then a confirm with what happens on the middle item (GUI_DESIGN §4).</p>
 * <pre>
 *  R O B B B B B O R
 *  B I . B . X . . B     10 the listing, 12 Buy, 14 Remove
 *  ← O B B ✘ B B O R     18 Back, 22 Close
 * </pre>
 */
final class StaffListingMenu extends Menu {

    static final int SLOT_ITEM = 10;
    static final int SLOT_BUY = 12;
    static final int SLOT_REMOVE = 14;

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
        if (reasons.isEmpty()) {
            return;
        }
        set(SLOT_REMOVE, service.item(module.config.icon("staff-remove", Material.LAVA_BUCKET), viewer, AuctionModule.ID,
                "staff.remove-button", Msg.number("count", reasons.size()), Msg.p("seller", listing.sellerName())), click -> {
            List<PickListMenu.Option<String>> options = new ArrayList<>();
            for (String reason : reasons) {
                options.add(new PickListMenu.Option<>(reason, new ItemStack(Material.PAPER),
                        service.text(viewer, AuctionModule.ID, "staff.reason.name", Msg.p("reason", reason)), List.of(), false));
            }
            openChild(new PickListMenu<>(List.of(service.text(viewer, AuctionModule.ID, "staff.crumb"),
                    service.text(viewer, AuctionModule.ID, "staff.reason.crumb")), options, reason -> { })
                    .thenOpen(this::confirm));
        });
    }

    /** "Remove listing?" with the seller and the reason on the middle item; one-shot. */
    private Menu confirm(String reason) {
        ItemStack shown = AuctionMenu.listingItem(module, viewer, listing, "staff.entry");
        return new ConfirmMenu(v -> service.text(v, AuctionModule.ID, "staff.confirm.crumb"), shown,
                v -> service.lines(v, AuctionModule.ID, "staff.confirm.details", Msg.p("reason", reason), Msg.p("seller", listing.sellerName())),
                player -> {
                    if (used) {
                        return;
                    }
                    used = true;
                    module.startCancel(player, listing, true, reason);
                }, player -> { });
    }
}
