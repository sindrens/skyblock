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

/** Paged list of online players (heads, sorted by name); clicking one runs {@code onPick}. */
public final class PlayerPickerMenu extends PagedMenu<Player> {

    private final Function<Player, Component> title;
    private final Predicate<Player> filter;
    private final BiConsumer<Player, Player> onPick;

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
    public Component title() {
        return title != null ? title.apply(viewer) : service.text(viewer, "players.title");
    }

    @Override
    public String infoKey() {
        return "players.info";
    }

    @Override
    protected String emptyKey() {
        return "players.empty";
    }

    @Override
    protected List<Player> entries() {
        Predicate<Player> f = filter != null ? filter : p -> !p.equals(viewer);
        return Bukkit.getOnlinePlayers().stream()
                .map(p -> (Player) p)
                .filter(f)
                .filter(viewer::canSee)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
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
