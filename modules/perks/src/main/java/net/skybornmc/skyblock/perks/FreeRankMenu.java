package net.skybornmc.skyblock.perks;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;

/**
 * The {@code /freerank} tag picker: 3 rows, one big button per tag (slots 11, 13, 15) so it works with taps on Bedrock.
 * Closing it without picking is fine: {@code /freerank} opens it again until a tag is chosen. The pick is permanent, so a
 * click first shows a confirm with the tag on the middle item (GUI_DESIGN §4).
 */
final class FreeRankMenu extends Menu {

    private static final int[] SLOTS = {11, 13, 15};

    private final FreeRankService freeRank;
    private final List<String> choices;

    FreeRankMenu(FreeRankService freeRank, List<String> choices) {
        this.freeRank = freeRank;
        this.choices = List.copyOf(choices);
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, PerksModule.ID, "freerank.menu.title");
    }

    @Override
    public String messageModule() {
        return PerksModule.ID;
    }

    @Override
    protected void build() {
        frame();
        Cosmetics cosmetics = freeRank.cosmetics();
        for (int i = 0; i < choices.size() && i < SLOTS.length; i++) {
            String tag = choices.get(i);
            Component shown = cosmetics == null ? Component.text(tag) : cosmetics.display(viewer, Cosmetics.Kind.TAG, tag);
            set(SLOTS[i], service.item(Material.NAME_TAG, viewer, PerksModule.ID, "freerank.menu.tag", Msg.c("tag", shown)),
                    click -> openChild(confirm(tag, shown)));
        }
    }

    /** "Pick this tag?": the tag on the middle item, "yours forever, can't be changed" as the details. */
    private ConfirmMenu confirm(String tag, Component shown) {
        return new ConfirmMenu(v -> service.text(v, PerksModule.ID, "freerank.confirm.crumb"),
                service.item(Material.NAME_TAG, shown, List.of()),
                v -> service.lines(v, PerksModule.ID, "freerank.confirm.details", Msg.c("tag", shown)),
                player -> freeRank.pick(player, tag), player -> { });
    }
}
