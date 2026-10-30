package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /inspect <player>} (and the Inspect tool): profile (rank, playtime, first/last seen, Java/Bedrock + device,
 * ping, location), report counts, and shortcuts: inventory, ender chest, teleport, freeze, punish, reports, logs.
 * Works for offline players too (profile + reports + logs).
 */
final class InspectMenu extends Menu {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    private final StaffModule.Services s;
    private final PlayerData.Snapshot profile;

    InspectMenu(StaffModule.Services services, PlayerData.Snapshot profile) {
        this.s = services;
        this.profile = profile;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, StaffFeature.MODULE, "menu.inspect.title", Msg.p("player", profile.name())));
    }

    @Override
    public String messageModule() {
        return StaffFeature.MODULE;
    }

    @Override
    public String infoKey() {
        return "menu.inspect.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected void build() {
        frame();
        UUID id = profile.uuid();
        Player online = Bukkit.getPlayer(id);
        String name = profile.name();
        long playtime = online != null
                ? s.core().players().get(id).map(PlayerData::playtimeMillis).orElse(profile.playtimeMillis())
                : profile.playtimeMillis();
        String platform = profile.bedrock()
                ? "Bedrock" + s.core().bedrock().device(id).map(d -> " (" + d + ")").orElse("")
                : "Java";
        String location = online == null ? "-" : online.getWorld().getName() + " " + online.getLocation().getBlockX() + " "
                + online.getLocation().getBlockY() + " " + online.getLocation().getBlockZ();
        TagResolver[] p = {
                Msg.p("player", name),
                Msg.p("rank", online != null ? s.core().ranks().groupDisplayName(online) : "-"),
                Msg.p("playtime", Format.duration(Duration.ofMillis(playtime))),
                Msg.p("first", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(profile.firstSeen()))),
                Msg.p("last", online != null ? "online" : ReportService.ago(profile.lastSeen()) + " ago"),
                Msg.p("platform", platform),
                Msg.p("ping", online != null ? online.getPing() + " ms" : "-"),
                Msg.p("location", location),
                Msg.p("gamemode", online != null ? online.getGameMode().name().toLowerCase(Locale.ROOT) : "-"),
                Msg.p("status", online != null ? "online" : "offline")};
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            head.setItemMeta(meta);
        }
        set(13, service.item(head, service.text(viewer, StaffFeature.MODULE, "menu.inspect.profile.name", p),
                service.lines(viewer, StaffFeature.MODULE, "menu.inspect.profile.lore", p)));

        // Report counts load in the background.
        setAsync(22, s.store().targetCounts(id).thenApply(c -> MenuButton.of(
                service.item(Material.WRITABLE_BOOK, viewer, StaffFeature.MODULE, "menu.inspect.reports",
                        Msg.p("open", c[0]), Msg.p("total", c[1]), Msg.p("player", name)),
                click -> s.reports().dispatchFor(click.player(), "reports player " + name))));

        if (online != null) {
            button(28, Material.CHEST, "menu.inspect.inventory", "invsee " + name, name);
            button(29, Material.ENDER_CHEST, "menu.inspect.enderchest", "ecsee " + name, name);
            set(30, service.item(Material.ENDER_PEARL, viewer, StaffFeature.MODULE, "menu.inspect.teleport", Msg.p("player", name)),
                    click -> {
                        click.player().closeInventory();
                        s.reports().dispatchFor(click.player(), "tp " + name);
                    });
            boolean frozen = s.freeze().isFrozen(online);
            set(32, service.item(frozen ? Material.BLUE_ICE : Material.PACKED_ICE, viewer, StaffFeature.MODULE,
                    frozen ? "menu.inspect.unfreeze" : "menu.inspect.freeze", Msg.p("player", name)), click -> {
                Player target = Bukkit.getPlayer(id);
                if (target != null && click.player().hasPermission(StaffPerms.FREEZE)) {
                    s.freeze().toggle(click.player(), target);
                    refresh();
                }
            });
        } else {
            set(29, service.item(Material.GRAY_DYE, viewer, StaffFeature.MODULE, "menu.inspect.offline", Msg.p("player", name)));
        }
        // Wardrobe (WD25): sets, chest; admins give/remove sets and tokens. Shown when the wardrobe module runs.
        s.core().services().optional(net.skybornmc.skyblock.common.wardrobe.WardrobeService.class)
                .filter(w -> viewer.hasPermission("skyblock.wardrobe.staff"))
                .ifPresent(w -> set(31, service.item(Material.LEATHER_CHESTPLATE, viewer, StaffFeature.MODULE, "menu.inspect.wardrobe",
                        Msg.p("player", name)), click -> w.openStaffView(click.player(), id, name, this)));
        // Item box (owner answer 7): admins view, remove and clear what waits in the player's /box (audited).
        s.core().services().optional(net.skybornmc.skyblock.common.box.ItemBox.class)
                .filter(b -> viewer.hasPermission("skyblock.box.admin"))
                .ifPresent(b -> set(40, service.item(Material.CHEST_MINECART, viewer, StaffFeature.MODULE, "menu.inspect.box",
                        Msg.p("player", name)), click -> b.openStaffView(click.player(), id, name, this)));
        button(33, Material.BREEZE_ROD, "menu.inspect.punish", "punish " + name, name);
        button(34, Material.CLOCK, "menu.inspect.logs", "logs player " + name, name);
    }

    private void button(int slot, Material icon, String key, String command, String name) {
        set(slot, service.item(icon, viewer, StaffFeature.MODULE, key, Msg.p("player", name)), click -> {
            click.player().closeInventory();
            s.reports().dispatchFor(click.player(), command);
        });
    }

    /** Opens the inspect menu for a name (online or offline). */
    static void open(StaffModule.Services s, Player staff, String name) {
        Player online = Bukkit.getPlayerExact(name);
        CompletableFuture<java.util.Optional<PlayerData.Snapshot>> lookup = online != null
                ? CompletableFuture.completedFuture(s.core().players().get(online.getUniqueId()).map(PlayerData::snapshot))
                : s.core().players().offline(name);
        lookup.thenAcceptAsync(profile -> {
            if (profile.isEmpty()) {
                s.messages().send(staff, "inspect.unknown", Msg.p("player", name));
                return;
            }
            s.core().menus().open(staff, new InspectMenu(s, profile.get()));
            s.reports().audit("inspect", staff, profile.get().uuid(), profile.get().name(), java.util.Map.of());
        }, new net.skybornmc.skyblock.common.db.MainThread(s.core().plugin()));
    }
}
