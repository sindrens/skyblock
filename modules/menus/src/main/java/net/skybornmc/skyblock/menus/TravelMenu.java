package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /menu → Travel} (GUI_DESIGN §7.3, GUI-2; replaces the UX3 warps menu): 3 rows, places on 10-16 from
 * {@code menu.yml → travel}, 18 Back (to the hub), 22 Close. One click teleports through the core {@link TeleportService}
 * (warmup, cooldown, guards such as locked Depths tiers, combat block) or runs the place's command (My Island =
 * {@code is home}, Visit Islands = the {@code is visit} browser, which gets Back to this menu). Places that do not exist
 * here (build not applied, world not loaded, command missing) are hidden, so the menu only shows places that work.
 * Plain items only: Bedrock-safe.
 */
final class TravelMenu extends net.skybornmc.skyblock.common.menu.Menu {

    private final Nav nav;

    TravelMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 3;
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
        frame();
        int shown = 0;
        for (WarpLayout.Warp warp : nav.travel.warps()) {
            if (!available(warp)) {
                continue;
            }
            shown++;
            Component name = name(warp);
            List<Component> lore = new ArrayList<>(nav.lines(viewer, "travel.places." + warp.id() + ".lore"));
            if (warp.permission() != null && !warp.permission().isBlank() && !viewer.hasPermission(warp.permission())) {
                lore.addAll(nav.lines(viewer, "hub.locked"));
                set(warp.slot(), Nav.icon(nav.item(Nav.material(warp.icon(), Material.ENDER_PEARL), name, lore), warp.model()));
                continue;
            }
            String actionKey = nav.core.messages().raw(Nav.TEXTS, "travel.places." + warp.id() + ".action") != null
                    ? "travel.places." + warp.id() + ".action" : "travel.action";
            lore.addAll(nav.lines(viewer, actionKey));
            set(warp.slot(), Nav.icon(nav.item(Nav.material(warp.icon(), Material.ENDER_PEARL), name, lore), warp.model()), click -> go(click.player(), warp, name));
        }
        if (shown == 0) {
            setEmpty(Nav.TEXTS, "travel.empty");
        }
    }

    private Component name(WarpLayout.Warp warp) {
        String key = "travel.places." + warp.id() + ".name";
        if (nav.core.messages().raw(Nav.TEXTS, key) == null) {
            return nav.text(viewer, "travel.fallback-name", Nav.pre("name", pretty(warp.id())));
        }
        return nav.text(viewer, key);
    }

    private boolean available(WarpLayout.Warp warp) {
        return switch (warp.target()) {
            case WarpLayout.Command c -> HubLayout.commandExists(c.command());
            case WarpLayout.Point p -> location(p).isPresent();
            case WarpLayout.Place p -> Bukkit.getWorld(p.world()) != null;
        };
    }

    private void go(Player player, WarpLayout.Warp warp, Component name) {
        switch (warp.target()) {
            // A screen the command opens (the island browser) gets Back to Travel; a teleport closes the menu.
            case WarpLayout.Command c -> service.navigate(player, this, () -> player.performCommand(c.command()));
            case WarpLayout.Point p -> {
                player.closeInventory();
                location(p).ifPresentOrElse(to -> teleport(player, warp, to, name), () -> gone(player));
            }
            case WarpLayout.Place p -> {
                player.closeInventory();
                World world = Bukkit.getWorld(p.world());
                if (world == null) {
                    gone(player);
                } else {
                    teleport(player, warp, new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch()), name);
                }
            }
        }
    }

    private void teleport(Player player, WarpLayout.Warp warp, Location to, Component name) {
        TeleportService teleports = nav.core.services().optional(TeleportService.class).orElse(null);
        if (teleports == null) {
            gone(player);
            return;
        }
        teleports.request(player, to, reason(warp.reason()), TeleportOptions.builder()
                .destination(PlainTextComponentSerializer.plainText().serialize(name)).build());
    }

    private void gone(Player player) {
        nav.msg.send(player, "travel.gone");
        nav.core.sounds().play(player, "error");
    }

    private Optional<Location> location(WarpLayout.Point p) {
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
