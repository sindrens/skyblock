package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
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
                click -> service.navigate(click.player(), this, () -> s.reports().dispatchFor(click.player(), "reports player " + name)))));

        // QOL3: the tool row 28-34 keeps one shape for online and offline players and for every rank: a tool that
        // can't be used now is a locked item on its own slot (with the reason), never a gap or a moved button.
        Component offline = service.text(viewer, StaffFeature.MODULE, "menu.inspect.locked.offline", Msg.p("player", name));
        Component noAccess = service.text(viewer, StaffFeature.MODULE, "menu.inspect.locked.no-access");
        if (online != null) {
            button(28, Material.CHEST, "menu.inspect.inventory", "invsee " + name, name);
            button(29, Material.ENDER_CHEST, "menu.inspect.enderchest", "ecsee " + name, name);
            set(30, service.item(Material.ENDER_PEARL, viewer, StaffFeature.MODULE, "menu.inspect.teleport", Msg.p("player", name)),
                    click -> {
                        click.player().closeInventory();
                        s.reports().dispatchFor(click.player(), "tp " + name);
                    });
        } else {
            lockedTool(28, "inventory", offline);
            lockedTool(29, "enderchest", offline);
            lockedTool(30, "teleport", offline);
        }
        if (online == null) {
            lockedTool(32, "freeze", offline);
        } else if (!viewer.hasPermission(StaffPerms.FREEZE)) {
            lockedTool(32, "freeze", noAccess);
        } else {
            boolean frozen = s.freeze().isFrozen(online);
            set(32, service.item(frozen ? Material.BLUE_ICE : Material.PACKED_ICE, viewer, StaffFeature.MODULE,
                    frozen ? "menu.inspect.unfreeze" : "menu.inspect.freeze", Msg.p("player", name)),
                    click -> confirmFreeze(click.player(), id, name, frozen));
        }
        // Wardrobe (WD25): sets, chest; admins give/remove sets and tokens. Shown when the wardrobe module runs.
        s.core().services().optional(net.skybornmc.skyblock.common.wardrobe.WardrobeService.class)
                .ifPresent(w -> {
                    if (viewer.hasPermission("skyblock.wardrobe.staff")) {
                        set(31, service.item(Material.LEATHER_CHESTPLATE, viewer, StaffFeature.MODULE, "menu.inspect.wardrobe",
                                Msg.p("player", name)), click -> w.openStaffView(click.player(), id, name, this));
                    } else {
                        lockedTool(31, "wardrobe", noAccess);
                    }
                });
        // Item box (owner answer 7): admins view, remove and clear what waits in the player's /box (audited).
        s.core().services().optional(net.skybornmc.skyblock.common.box.ItemBox.class)
                .filter(b -> viewer.hasPermission("skyblock.box.admin"))
                .ifPresent(b -> set(40, service.item(Material.CHEST_MINECART, viewer, StaffFeature.MODULE, "menu.inspect.box",
                        Msg.p("player", name)), click -> b.openStaffView(click.player(), id, name, this)));
        // Join tutorial (TUTORIAL §9.2): step, times, skip and reset. Shown when the tutorial module runs.
        s.core().services().optional(net.skybornmc.skyblock.common.tutorial.TutorialService.class)
                .filter(t -> viewer.hasPermission("skyblock.tutorial.admin.status"))
                .ifPresent(t -> set(41, service.item(Material.WRITTEN_BOOK, viewer, StaffFeature.MODULE, "menu.inspect.tutorial",
                        Msg.p("player", name)), click -> t.openStaffView(click.player(), id, name, this)));
        // R3-13b: frozen stacked spawners (block lost) of this player; admins restore them to the player's /box.
        s.core().services().optional(net.skybornmc.skyblock.common.stacking.StackingService.class)
                .filter(st -> viewer.hasPermission("skyblock.spawner.restore"))
                .ifPresent(st -> set(42, service.item(Material.SPAWNER, viewer, StaffFeature.MODULE, "menu.inspect.frozen-spawners",
                        Msg.p("player", name)), click -> st.openFrozenSpawners(click.player(), id, name, this)));
        menuButton(33, Material.BREEZE_ROD, "menu.inspect.punish", "p " + name, name);
        menuButton(34, Material.CLOCK, "menu.inspect.logs", "logs player " + name, name);
    }

    /** QOL3: a tool that can't be used now, on its own slot ({@code menu.inspect.locked.<tool>} = its plain name). */
    private void lockedTool(int slot, String tool, Component why) {
        set(slot, service.locked(viewer, service.text(viewer, StaffFeature.MODULE, "menu.inspect.locked." + tool), why));
    }

    /**
     * QOL3 (GUI_DESIGN §4): Freeze and Unfreeze ask first, with the player, the place and what happens on the middle
     * item. The state is checked again on confirm, so a freeze someone else changed meanwhile is not flipped back.
     */
    private void confirmFreeze(Player staff, UUID id, String name, boolean frozen) {
        Player target = Bukkit.getPlayer(id);
        if (target == null) {
            refresh();
            return;
        }
        String crumb = frozen ? "menu.inspect.unfreeze-confirm.crumb" : "menu.inspect.freeze-confirm.crumb";
        String shownKey = frozen ? "menu.inspect.unfreeze-confirm.shown" : "menu.inspect.freeze-confirm.shown";
        String details = frozen ? "menu.inspect.unfreeze-confirm.details" : "menu.inspect.freeze-confirm.details";
        String where = target.getWorld().getName() + " " + target.getLocation().getBlockX() + " "
                + target.getLocation().getBlockY() + " " + target.getLocation().getBlockZ();
        ItemStack shown = service.item(frozen ? Material.BLUE_ICE : Material.PACKED_ICE, viewer, StaffFeature.MODULE,
                shownKey, Msg.p("player", name));
        openChild(new ConfirmMenu(v -> service.text(v, StaffFeature.MODULE, crumb, Msg.p("player", name)), shown,
                v -> service.lines(v, StaffFeature.MODULE, details, Msg.p("player", name), Msg.p("location", where)),
                p -> {
                    Player now = Bukkit.getPlayer(id);
                    if (now == null) {
                        s.messages().send(p, "inspect.went-offline", Msg.p("player", name));
                    } else if (s.freeze().isFrozen(now) == frozen && p.hasPermission(StaffPerms.FREEZE)) {
                        s.freeze().toggle(p, now);
                    }
                    s.core().menus().open(p, this); // back to Inspect, rebuilt with the new state
                }, p -> { }));
    }

    /** A tool that is not a menu (inventory views): closes this screen and runs the command. */
    private void button(int slot, Material icon, String key, String command, String name) {
        set(slot, service.item(icon, viewer, StaffFeature.MODULE, key, Msg.p("player", name)), click -> {
            click.player().closeInventory();
            s.reports().dispatchFor(click.player(), command);
        });
    }

    /**
     * GUI-4c: a tool that opens a menu (/p, /logs, /reports) keeps this screen as its Back target
     * ({@code MenuService#navigate}), so staff return here instead of typing /inspect again.
     */
    private void menuButton(int slot, Material icon, String key, String command, String name) {
        set(slot, service.item(icon, viewer, StaffFeature.MODULE, key, Msg.p("player", name)),
                click -> service.navigate(click.player(), this, () -> s.reports().dispatchFor(click.player(), command)));
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
