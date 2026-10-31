package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Paged list of online players (heads, sorted by name); clicking one runs {@code onPick}. GUI-4b (MENU_FIXES
 * common.player-picker): a Search button (47) narrows the list by part of a name (typed in chat, or in a form popup with
 * the on-screen keyboard on Bedrock); Show everyone (46) clears it. Search shows when the list is longer than one page
 * or a search is on, so small servers keep the plain list.
 */
public final class PlayerPickerMenu extends PagedMenu<Player> {

    static final int SLOT_CLEAR = 46;
    static final int SLOT_SEARCH = 47;
    /** Longest search text (Minecraft names are at most 16 characters, plus the Bedrock prefix). */
    static final int SEARCH_MAX = 17;

    private final Function<Player, Component> title;
    private final Predicate<Player> filter;
    private final BiConsumer<Player, Player> onPick;
    private String search;

    /**
     * @param title  chest title ({@code null} = default "Choose a player")
     * @param filter which players to list ({@code null} = everyone except the viewer)
     * @param onPick runs with (viewer, picked player); the menu stays open unless the action opens/closes something
     */
    public PlayerPickerMenu(Function<Player, Component> title, Predicate<Player> filter, BiConsumer<Player, Player> onPick) {
        this.title = title;
        this.filter = filter;
        this.onPick = onPick;
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(title != null ? title.apply(viewer) : service.text(viewer, "players.crumb"));
    }

    @Override
    public String infoKey() {
        return "players.info";
    }

    @Override
    protected String emptyKey() {
        return search != null ? "players.empty-search" : "players.empty";
    }

    /** True if {@code name} contains {@code search} (any case); no search matches everyone. Pure. */
    static boolean matches(String name, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains(search.toLowerCase(java.util.Locale.ROOT));
    }

    /** Everyone this picker offers, before the search. */
    private List<Player> candidates() {
        Predicate<Player> f = filter != null ? filter : p -> !p.equals(viewer);
        return Bukkit.getOnlinePlayers().stream()
                .map(p -> (Player) p)
                .filter(f)
                .filter(viewer::canSee)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    protected List<Player> entries() {
        List<Player> all = candidates();
        return search == null ? all : all.stream().filter(p -> matches(p.getName(), search)).toList();
    }

    @Override
    protected void decorate() {
        if (search == null && candidates().size() <= CONTENT_SLOTS.length) {
            return;
        }
        set(SLOT_SEARCH, search == null
                ? service.item(Material.SPYGLASS, viewer, "players.search")
                : service.item(Material.SPYGLASS, viewer, "players.search-on", Msg.p("search", search)), click -> askSearch());
        if (search != null) {
            set(SLOT_CLEAR, service.item(Material.HOPPER, viewer, "players.search-clear"), click -> {
                search = null;
                page(0);
                redraw();
            });
        }
    }

    private void askSearch() {
        Player who = viewer;
        service.input(who, MenuService.MODULE, "players.search-prompt", SEARCH_MAX, search, text -> {
            search = text;
            page(0);
            service.open(who, this);
        }, () -> service.open(who, this), Msg.p("max", SEARCH_MAX));
    }

    @Override
    protected MenuButton entry(Player target) {
        ItemStack head = service.item(Material.PLAYER_HEAD, viewer, "players.entry", Msg.p("player", target.getName()));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
        return MenuButton.of(head, click -> {
            if (!target.isOnline()) {
                service.messages().send(click.player(), MenuService.MODULE, "players.offline", Msg.p("player", target.getName()));
                refresh();
                return;
            }
            onPick.accept(click.player(), target);
        });
    }
}
