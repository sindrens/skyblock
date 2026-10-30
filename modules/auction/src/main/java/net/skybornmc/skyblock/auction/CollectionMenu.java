package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The collection bin: items that came back (expired, cancelled, removed, fee not paid) or were bought while the
 * inventory was full. Click one to take it, or "Take all". Each item leaves the database exactly once.
 */
final class CollectionMenu extends PagedMenu<CollectionEntry> {

    static final int SLOT_ALL = 4;

    private final AuctionModule module;
    private List<CollectionEntry> rows;
    private boolean busy;

    private CollectionMenu(AuctionModule module, List<CollectionEntry> rows) {
        this.module = module;
        this.rows = rows;
    }

    static void open(AuctionModule module, Player player, Menu parent) {
        UUID uuid = module.key(player);
        load(module, uuid, rows -> {
            Player p = module.online(uuid);
            if (p == null) {
                return;
            }
            CollectionMenu menu = new CollectionMenu(module, rows);
            menu.parent(parent);
            module.core.menus().open(p, menu);
        });
    }

    private static void load(AuctionModule module, UUID uuid, java.util.function.Consumer<List<CollectionEntry>> then) {
        module.engine().store().collection(uuid).whenComplete((rows, error) -> module.core.scheduler().main("auction:collection", () -> {
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Auction: loading the collection bin failed", Database.cause(error));
                Player p = module.online(uuid);
                if (p != null) {
                    module.send(p, "error");
                }
                return;
            }
            then.accept(rows);
        }));
    }

    @Override
    public String messageModule() {
        return AuctionModule.ID;
    }

    @Override
    public String infoKey() {
        return "collection.info";
    }

    @Override
    public String helpTopic() {
        return "auction";
    }

    @Override
    protected String emptyKey() {
        return "collection.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, AuctionModule.ID, "browse.title"), service.text(viewer, AuctionModule.ID, "collection.title"));
    }

    @Override
    protected List<CollectionEntry> entries() {
        return rows;
    }

    @Override
    protected void decorate() {
        if (!rows.isEmpty()) {
            set(SLOT_ALL, service.item(Material.HOPPER, viewer, AuctionModule.ID, "collection.take-all", Msg.number("count", rows.size())),
                    click -> takeAll());
        }
    }

    @Override
    protected MenuButton entry(CollectionEntry e) {
        ItemStack item;
        try {
            item = ItemCodec.display(ItemCodec.decode(e.bytes()));
        } catch (RuntimeException ex) {
            module.core.plugin().getLogger().log(Level.WARNING, "Auction: collection entry " + e.id() + " is unreadable", ex);
            return MenuButton.display(service.item(Material.BARRIER, viewer, AuctionModule.ID, "collection.unreadable"));
        }
        long age = Math.max(0, System.currentTimeMillis() - e.createdAt());
        List<Component> lore = new ArrayList<>();
        List<Component> existing = item.lore();
        if (existing != null) {
            lore.addAll(existing);
        }
        lore.addAll(service.lines(viewer, AuctionModule.ID, "collection.entry",
                Msg.c("source", service.text(viewer, AuctionModule.ID, "collection.source." + e.source())),
                Msg.p("ago", Format.duration(Duration.ofMillis(age)))));
        item.lore(lore);
        return MenuButton.of(item, click -> take(e));
    }

    private void take(CollectionEntry e) {
        if (busy) {
            return;
        }
        if (!ItemCodec.fits(viewer.getInventory(), ItemCodec.decode(e.bytes()))) {
            module.send(viewer, "collection.no-space");
            service.sound(viewer, "error");
            return;
        }
        busy = true;
        module.claim(viewer, e.id(), ok -> reload());
    }

    /** Takes items one after another while they fit. */
    private void takeAll() {
        if (busy) {
            return;
        }
        busy = true;
        Iterator<CollectionEntry> it = new ArrayList<>(rows).iterator();
        next(it, 0);
    }

    private void next(Iterator<CollectionEntry> it, int taken) {
        while (it.hasNext()) {
            CollectionEntry e = it.next();
            if (ItemCodec.fits(viewer.getInventory(), ItemCodec.decode(e.bytes()))) {
                module.claim(viewer, e.id(), ok -> next(it, ok ? taken + 1 : taken));
                return;
            }
            module.send(viewer, "collection.no-space");
            break;
        }
        module.send(viewer, "collection.took", Msg.number("count", taken));
        reload();
    }

    private void reload() {
        load(module, module.key(viewer), fresh -> {
            busy = false;
            rows = fresh;
            if (isOpen()) {
                refresh();
            }
        });
    }
}
