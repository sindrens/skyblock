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
 * {@code /menu → Warps} (UX3): a small menu of server places from {@code menu.yml → warps}. One click teleports through the
 * core {@link TeleportService} (warmup, cooldown, guards such as locked Depths tiers, combat block) or runs the entry's
 * command. Entries whose place does not exist here (build not applied, world not loaded, command missing) are hidden, so
 * the menu only ever shows places that work. Plain items only: Bedrock-safe.
 */
final class WarpsMenu extends net.skybornmc.skyblock.common.menu.Menu {

    private final Nav nav;

    WarpsMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "hub.title", "warps.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "warps.info";
    }

    @Override
    public String helpTopic() {
        return "menu";
    }

    @Override
    protected void build() {
        frame();
        int shown = 0;
        for (WarpLayout.Warp warp : nav.warps.warps()) {
            if (!available(warp)) {
                continue;
            }
            shown++;
            Component name = name(warp);
            List<Component> lore = new ArrayList<>(nav.lines(viewer, "warps.entries." + warp.id() + ".lore"));
            if (warp.permission() != null && !warp.permission().isBlank() && !viewer.hasPermission(warp.permission())) {
                lore.addAll(nav.lines(viewer, "hub.locked"));
                set(warp.slot(), nav.item(warp.icon(), name, lore));
                continue;
            }
            String actionKey = nav.core.messages().raw(Nav.TEXTS, "warps.entries." + warp.id() + ".action") != null
                    ? "warps.entries." + warp.id() + ".action" : "warps.action";
            lore.addAll(nav.lines(viewer, actionKey));
            set(warp.slot(), nav.item(warp.icon(), name, lore), click -> go(click.player(), warp, name));
        }
        if (shown == 0) {
            set(22, nav.item(Material.GRAY_DYE, nav.text(viewer, "warps.empty.name"), nav.lines(viewer, "warps.empty.lore")));
        }
    }

    private Component name(WarpLayout.Warp warp) {
        String key = "warps.entries." + warp.id() + ".name";
        if (nav.core.messages().raw(Nav.TEXTS, key) == null) {
            return nav.text(viewer, "warps.fallback-name", Nav.pre("name", pretty(warp.id())));
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
        player.closeInventory();
        switch (warp.target()) {
            case WarpLayout.Command c -> player.performCommand(c.command());
            case WarpLayout.Point p -> location(p).ifPresentOrElse(to -> teleport(player, warp, to, name), () -> gone(player));
            case WarpLayout.Place p -> {
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
        nav.msg.send(player, "warps.gone");
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

    private static String pretty(String id) {
        String spaced = id.replace('_', ' ').replace('-', ' ');
        return spaced.isEmpty() ? id : spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }
}
