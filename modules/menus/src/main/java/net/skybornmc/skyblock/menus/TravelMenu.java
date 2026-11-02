package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.menu.Centered;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /menu → Travel} (G9 v3, I9 v2; replaces the UX3 warps menu): 4 rows (owner 3 Nov 2026: one free row so it is
 * not cramped), the places of {@code menu.yml → travel} centred on row 1 in file order ({@code Centered}: 7 places =
 * slots 10-16; fewer re-centre, hidden ones leave no hole), row 2 free, 27 Back (to the hub), 31 Close. One click teleports through the core {@link TeleportService}
 * (warmup, cooldown, guards such as locked Depths tiers, combat block) or runs the place's command (My Island =
 * {@code is home}, Visit Islands = the {@code is visit} browser, which gets Back to this menu). Places that do not exist
 * here (build not applied, world not loaded, command missing) are hidden, so the menu only shows places that work.
 * Icons come from the catalogue (plain base items without the pack): Bedrock-safe.
 */
final class TravelMenu extends net.skybornmc.skyblock.common.menu.Menu {

    private final Nav nav;

    TravelMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "hub.title", "travel.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "travel.info";
    }

    @Override
    public String helpTopic() {
        return "menu";
    }

    @Override
    protected void build() {
        navBar();
        List<WarpLayout.Warp> shown = new ArrayList<>();
        for (WarpLayout.Warp warp : nav.travel.warps()) {
            if (available(nav, warp) && shown.size() < WarpLayout.MAX_PLACES) {
                shown.add(warp);
            }
        }
        if (shown.isEmpty()) {
            setEmpty(Nav.TEXTS, "travel.empty");
            return;
        }
        int[] slots = Centered.layout(shown.size()).slots();
        for (int i = 0; i < shown.size(); i++) {
            WarpLayout.Warp warp = shown.get(i);
            Component name = name(warp);
            List<Component> lore = new ArrayList<>(nav.lines(viewer, "travel.places." + warp.id() + ".lore"));
            if (warp.permission() != null && !warp.permission().isBlank() && !viewer.hasPermission(warp.permission())) {
                lore.addAll(nav.lines(viewer, "hub.locked"));
                set(slots[i], nav.icon(viewer, warp.icon(), name, lore));
                continue;
            }
            String actionKey = nav.core.messages().raw(Nav.TEXTS, "travel.places." + warp.id() + ".action") != null
                    ? "travel.places." + warp.id() + ".action" : "travel.action";
            List<Component> action = nav.lines(viewer, actionKey);
            Optional<Highlights.Rule> lit = nav.highlight(viewer, "travel." + warp.id());
            if (lit.isPresent()) {
                set(slots[i], nav.highlighted(viewer, lit.get(), name, lore, action), click -> go(click.player(), warp, name));
                continue;
            }
            lore.addAll(action);
            set(slots[i], nav.icon(viewer, warp.icon(), name, lore), click -> go(click.player(), warp, name));
        }
    }

    private Component name(WarpLayout.Warp warp) {
        return name(nav, viewer, warp);
    }

    static Component name(Nav nav, Player viewer, WarpLayout.Warp warp) {
        String key = "travel.places." + warp.id() + ".name";
        if (nav.core.messages().raw(Nav.TEXTS, key) == null) {
            return nav.text(viewer, "travel.fallback-name", Nav.pre("name", pretty(warp.id())));
        }
        return nav.text(viewer, key);
    }

    private void go(Player player, WarpLayout.Warp warp, Component name) {
        if (warp.target() instanceof WarpLayout.Command c) {
            // A screen the command opens (the island browser) gets Back to Travel; a teleport closes the menu.
            service.navigate(player, this, () -> player.performCommand(c.command()));
            return;
        }
        player.closeInventory();
        travel(nav, player, warp, name);
    }

    /** Teleports to a place (or runs its command). Used by the menu and by {@code /warp <place>}. */
    static void travel(Nav nav, Player player, WarpLayout.Warp warp, Component name) {
        switch (warp.target()) {
            case WarpLayout.Command c -> player.performCommand(c.command());
            case WarpLayout.Point p -> location(nav, p).ifPresentOrElse(to -> teleport(nav, player, warp, to, name), () -> gone(nav, player));
            case WarpLayout.Place p -> {
                World world = Bukkit.getWorld(p.world());
                if (world == null) {
                    gone(nav, player);
                } else {
                    teleport(nav, player, warp, new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch()), name);
                }
            }
        }
    }

    /** True when the place works on this server (build applied, world loaded, command there). */
    static boolean available(Nav nav, WarpLayout.Warp warp) {
        return switch (warp.target()) {
            case WarpLayout.Command c -> HubLayout.commandExists(c.command());
            case WarpLayout.Point p -> location(nav, p).isPresent();
            case WarpLayout.Place p -> Bukkit.getWorld(p.world()) != null;
        };
    }

    private static void teleport(Nav nav, Player player, WarpLayout.Warp warp, Location to, Component name) {
        TeleportService teleports = nav.core.services().optional(TeleportService.class).orElse(null);
        if (teleports == null) {
            gone(nav, player);
            return;
        }
        teleports.request(player, to, reason(warp.reason()), TeleportOptions.builder()
                .destination(PlainTextComponentSerializer.plainText().serialize(name)).build());
    }

    private static void gone(Nav nav, Player player) {
        nav.msg.send(player, "travel.gone");
        nav.core.sounds().play(player, "error");
    }

    private static Optional<Location> location(Nav nav, WarpLayout.Point p) {
        Optional<BuildPoints> points = nav.core.services().optional(BuildPoints.class);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        Optional<BuildPoint> point = points.get().point(p.category(), p.id());
        if (point.isEmpty()) {
            return Optional.empty();
        }
        BuildPoint b = point.get();
        World world = Bukkit.getWorld(b.world());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, WarpLayout.centre(b.x()), b.y(), WarpLayout.centre(b.z()), b.yaw(), b.pitch()));
    }

    static TeleportReason reason(String id) {
        for (TeleportReason r : TeleportReason.values()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return TeleportReason.SPAWN;
    }

    static String pretty(String id) {
        String spaced = id.replace('_', ' ').replace('-', ' ');
        return spaced.isEmpty() ? id : spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }
}
