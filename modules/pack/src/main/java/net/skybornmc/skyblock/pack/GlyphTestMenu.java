package net.skybornmc.skyblock.pack;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuLayout;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /pack glyphtest} (GUI-FONT section 8, the spike): a framed test menu for staff on Java with the pack LOADED,
 * framed even when {@code gui.font.*} is off ({@link #frameForced()}). Every content item is a numbered paper (amount
 * and name = its slot) so offsets can be read off a screenshot. Bottom row: switch panel type (R+1), switch fill
 * (R+2), switch plaque sample (R+3), Close (R+4).
 *
 * <p>Reading it: vanilla grey or black at the window edge or a slot cell behind an item means the panel is offset;
 * every soft tile must sit exactly under its item; the plaque must be fully on screen.</p>
 */
final class GlyphTestMenu extends Menu {

    /** Panel types the test cycles through ({@code list} = P6 with the 28 grid slots). */
    static final List<String> TYPES = List.of("p3", "p4", "p6", "list");
    /** Fill patterns. */
    static final List<String> FILLS = List.of("full", "sparse", "empty");
    /** Plaque samples: small, medium, large. */
    static final List<String> SAMPLES = List.of("s", "m", "l");

    private String type;
    private String fill;
    private String sample = "s";

    GlyphTestMenu(String type, String fill) {
        this.type = TYPES.contains(type) ? type : "p6";
        this.fill = FILLS.contains(fill) ? fill : "full";
    }

    @Override
    public int rows() {
        return switch (type) {
            case "p3" -> 3;
            case "p4" -> 4;
            default -> 6;
        };
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, PackModule.ID, "glyphtest.title");
    }

    @Override
    public String messageModule() {
        return PackModule.ID;
    }

    @Override
    public String plaqueKey() {
        return "glyphtest.sample." + sample;
    }

    @Override
    public boolean frameForced() {
        return true;
    }

    @Override
    protected void build() {
        navBar();
        int rows = rows();
        for (int slot : contentSlots(type, fill, rows)) {
            ItemStack paper = service.item(Material.PAPER, service.text(viewer, PackModule.ID, "glyphtest.slot", Msg.p("slot", slot)),
                    List.of());
            paper.setAmount(Math.max(1, Math.min(64, slot)));
            set(slot, MenuButton.display(paper));
        }
        int r = MenuLayout.back(rows);
        set(r + 1, button("glyphtest.type", Msg.p("type", type.toUpperCase(Locale.ROOT))), click -> {
            type = next(TYPES, type);
            redraw();
        });
        set(r + 2, button("glyphtest.fill", Msg.p("fill", fill)), click -> {
            fill = next(FILLS, fill);
            redraw();
        });
        set(r + 3, button("glyphtest.plaque", Msg.p("size", sample.toUpperCase(Locale.ROOT))), click -> {
            sample = next(SAMPLES, sample);
            redraw();
        });
    }

    private ItemStack button(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver tag) {
        Material icon = switch (key) {
            case "glyphtest.type" -> Material.ITEM_FRAME;
            case "glyphtest.fill" -> Material.BUNDLE;
            default -> Material.OAK_HANGING_SIGN;
        };
        return service.item(icon, viewer, PackModule.ID, key, tag);
    }

    static String next(List<String> values, String current) {
        int i = values.indexOf(current);
        return values.get((i + 1) % values.size());
    }

    /** The container slots that get a numbered paper (pure: the nav row is never filled). */
    static List<Integer> contentSlots(String type, String fill, int rows) {
        List<Integer> out = new ArrayList<>();
        int content = (rows - 1) * 9;
        switch (fill) {
            case "empty" -> {
                return out;
            }
            case "sparse" -> {
                int last = rows - 2;
                int[] corners = {0, 8, last * 9, last * 9 + 8};
                for (int s : corners) {
                    add(out, s, content);
                }
                add(out, (last / 2) * 9 + 4, content);
                for (int r = 0; r <= last; r++) {
                    add(out, r * 9 + Math.min(8, r * 2), content);
                }
            }
            default -> {
                if ("list".equals(type)) {
                    for (int r = 1; r <= 4; r++) {
                        for (int c = 1; c <= 7; c++) {
                            out.add(r * 9 + c);
                        }
                    }
                } else {
                    for (int s = 0; s < content; s++) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    private static void add(List<Integer> out, int slot, int max) {
        if (slot >= 0 && slot < max && !out.contains(slot)) {
            out.add(slot);
        }
    }
}
