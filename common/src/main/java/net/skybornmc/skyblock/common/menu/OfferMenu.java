package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;

/**
 * The Bedrock popup of an {@link Offer} (GUI-5): a 3-row menu, big buttons on the middle row.
 * <pre>
 *  R O B B i B B O R     i = what this is (4); with no buttons it sits in the middle (13)
 *  B . ✔ . ✘ . ✘ . B     buttons: 1 → 13, 2 → 11 + 15, 3 → 11 + 13 + 15
 *  ← O B B ✘ B B O R     18 Back, 22 Close
 * </pre>
 * When the offer expires while open, the buttons give way to one "Expired" item, so nobody taps a dead answer.
 */
final class OfferMenu extends Menu {

    private static final int SLOT_INFO_TOP = 4;
    private static final int SLOT_MIDDLE = 13;

    private final Offer offer;
    private boolean used;
    private boolean shownExpired;

    OfferMenu(Offer offer) {
        this.offer = offer;
    }

    /** Button slots for {@code count} buttons (pure, unit-tested). */
    static int[] buttonSlots(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{13};
            case 2 -> new int[]{11, 15};
            case 3 -> new int[]{11, 13, 15};
            default -> throw new IllegalArgumentException("At most " + Offer.MAX_BUTTONS + " buttons");
        };
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public String messageModule() {
        return offer.module();
    }

    @Override
    public String menuId() {
        return "offer";
    }

    @Override
    public net.kyori.adventure.text.Component title() {
        return service.title(viewer, service.text(viewer, offer.module(), offer.titleKey(), tags()));
    }

    @Override
    public int tickInterval() {
        return 20; // the "<left>" countdown and the expiry check, once a second
    }

    @Override
    protected void tick() {
        boolean expired = offer.expired(System.currentTimeMillis());
        if (expired != shownExpired || (!expired && offer.expiresAt() > 0)) {
            refresh();
        }
    }

    @Override
    protected void build() {
        frame();
        TagResolver[] tags = tags();
        boolean expired = offer.expired(System.currentTimeMillis());
        shownExpired = expired;
        List<Offer.Button> buttons = offer.buttons();
        ItemStack info = service.item(offer.icon(), service.text(viewer, offer.module(), offer.nameKey(), tags),
                service.lines(viewer, offer.module(), offer.loreKey(), tags));
        int infoSlot = buttons.isEmpty() ? SLOT_MIDDLE : SLOT_INFO_TOP;
        if (offer.onInfoClick() != null && !expired) {
            set(infoSlot, info, click -> answer(click.player(), () -> offer.onInfoClick().accept(click.player())));
        } else {
            set(infoSlot, info);
        }
        if (expired) {
            if (!buttons.isEmpty()) {
                set(SLOT_MIDDLE, service.item(Material.BARRIER, viewer, "offer.expired-item", tags),
                        click -> service.sound(click.player(), "error"));
            }
            return;
        }
        int[] slots = buttonSlots(buttons.size());
        for (int i = 0; i < buttons.size(); i++) {
            Offer.Button b = buttons.get(i);
            String module = b.module() == null ? MenuService.MODULE : b.module();
            TagResolver[] withCommand = with(tags, "command", Msg.p("command", "/" + b.command()));
            ItemStack item = service.item(b.material(), service.text(viewer, module, b.key() + ".name", withCommand),
                    service.lines(viewer, module, b.key() + ".lore", withCommand));
            set(slots[i], item, click -> answer(click.player(), () -> click.player().performCommand(b.command())));
        }
    }

    /** One answer per popup: closes it, then runs the action (the command checks everything again). */
    private void answer(Player player, Runnable action) {
        if (used) {
            return;
        }
        if (offer.expired(System.currentTimeMillis())) {
            service.messages().send(player, MenuService.MODULE, "offer.expired");
            service.sound(player, "error");
            refresh();
            return;
        }
        used = true;
        player.closeInventory();
        action.run();
    }

    private TagResolver[] tags() {
        long left = offer.expiresAt() <= 0 ? 0 : Math.max(0, offer.expiresAt() - System.currentTimeMillis());
        return with(offer.placeholders(), "left", Msg.p("left", Format.duration(Duration.ofMillis(left))));
    }

    /** Adds {@code extra} and drops any other placeholder with its name, so the live {@code <left>} beats a fixed one. */
    static TagResolver[] with(TagResolver[] base, String name, TagResolver extra) {
        List<TagResolver> out = new java.util.ArrayList<>(base.length + 1);
        out.add(extra);
        for (TagResolver r : base) {
            if (!(r instanceof TagResolver.Single single && single.key().equals(name))) {
                out.add(r);
            }
        }
        return out.toArray(TagResolver[]::new);
    }
}
