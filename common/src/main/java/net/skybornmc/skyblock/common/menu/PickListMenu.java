package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small list where one click picks a value (GUI_DESIGN §5, owner answer G9): replaces the forward-only cycler
 * buttons (AH category and sort, filters, time ranges, island time and weather, colours). The current value glints
 * and says "✔ Selected"; a click on any other value runs {@code onPick} and goes back to the menu that opened the
 * list, which redraws in place. Size by {@link PickListLayout}.
 *
 * <pre>{@code
 * List<PickListMenu.Option<Sort>> options = ...;
 * openChild(new PickListMenu<>(List.of(text("browse.title"), text("browse.sort.crumb")), options, sort -> this.sort = sort));
 * }</pre>
 *
 * @param <T> the value type
 */
public final class PickListMenu<T> extends Menu {

    /**
     * One choice.
     *
     * @param value    the value handed to {@code onPick}
     * @param icon     the item shown (its own name/lore are replaced by {@code name} and {@code lore})
     * @param name     the key fact: the value's name (Bedrock reads names, not lore)
     * @param lore     optional description lines
     * @param selected the current value (glint, "✔ Selected", no click)
     */
    public record Option<T>(T value, ItemStack icon, Component name, List<Component> lore, boolean selected) {

        public Option {
            lore = lore == null ? List.of() : List.copyOf(lore);
        }

        public static <T> Option<T> of(T value, Material icon, Component name, boolean selected) {
            return new Option<>(value, new ItemStack(icon), name, List.of(), selected);
        }
    }

    private final List<Component> crumbs;
    private final List<Option<T>> options;
    private final Consumer<T> onPick;
    private final String module;
    private final String infoKey;
    private int page;

    /**
     * @param crumbs  title, e.g. {@code [Auction House, Sort]} (the title rule keeps the last two)
     * @param options the choices in display order
     * @param onPick  runs with the picked value before the list goes back to its parent
     */
    public PickListMenu(List<Component> crumbs, List<Option<T>> options, Consumer<T> onPick) {
        this(crumbs, options, onPick, MenuService.MODULE, "pick.info");
    }

    /** Same, with the "?" text from {@code infoKey} in {@code module} ({@code null} = no "?" item). */
    public PickListMenu(List<Component> crumbs, List<Option<T>> options, Consumer<T> onPick, String module, String infoKey) {
        this.crumbs = List.copyOf(crumbs);
        this.options = List.copyOf(options);
        this.onPick = onPick;
        this.module = module;
        this.infoKey = infoKey;
    }

    @Override
    public int rows() {
        return PickListLayout.rows(options.size());
    }

    @Override
    public String messageModule() {
        return module;
    }

    @Override
    public String infoKey() {
        return infoKey;
    }

    @Override
    public Component title() {
        Pagination pages = new Pagination(options.size(), Math.max(1, PickListLayout.perPage(options.size())));
        page = pages.clamp(page);
        Component suffix = pages.pages() > 1
                ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", pages.pages()))
                : null;
        return service.title(viewer, crumbs, suffix);
    }

    @Override
    protected void build() {
        frame();
        if (options.isEmpty()) {
            setEmpty();
            return;
        }
        Pagination pages = new Pagination(options.size(), Math.max(1, PickListLayout.perPage(options.size())));
        page = pages.clamp(page);
        int from = pages.from(page);
        int shown = pages.to(page) - from;
        for (int i = 0; i < shown; i++) {
            set(PickListLayout.slot(i, shown), button(options.get(from + i)));
        }
        if (rows() == 6) {
            if (pages.hasPrevious(page)) {
                set(SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous",
                        Msg.p("page", page), Msg.p("pages", pages.pages())), click -> turn(-1));
            }
            if (pages.hasNext(page)) {
                set(SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next",
                        Msg.p("page", page + 2), Msg.p("pages", pages.pages())), click -> turn(1));
            }
        }
    }

    private void turn(int delta) {
        page += delta;
        redraw();
    }

    private MenuButton button(Option<T> option) {
        List<Component> lore = new ArrayList<>(option.lore());
        lore.addAll(service.lines(viewer, option.selected() ? "pick.selected" : "pick.action"));
        ItemStack item = service.item(option.icon(), option.name(), lore);
        if (option.selected()) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.of(item, click -> {
            if (!option.selected()) {
                onPick.accept(option.value());
            }
            back();
        });
    }
}
