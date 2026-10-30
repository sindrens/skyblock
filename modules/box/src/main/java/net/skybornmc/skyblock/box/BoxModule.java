package net.skybornmc.skyblock.box;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Item mailbox (owner answer 7): items are never dropped on the ground. Whatever does not fit a player's inventory (sell
 * chest returns, shop buys, Forge/Enchanter/Wardrobe returns, trades, auction collection, staff gives, later crates and
 * gifts) waits in {@code /box}, per profile, until the player takes it. Publishes {@link ItemBox} for every module.
 *
 * <p>Settings: {@code box.yml}; texts: {@code messages/box.yml}; data: {@code db/box}; spool: {@code box/spool*.txt}.</p>
 */
public final class BoxModule implements SkyblockModule {

    static final String ID = "box";
    static final String PERM_USE = "skyblock.box.use";
    static final String PERM_ADMIN = "skyblock.box.admin";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    private final Core core;
    private final BoxConfig config = new BoxConfig();
    private ModuleMessages msg;
    private BoxService service;
    private Scheduler.Task spoolTask;

    public BoxModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        BoxEngine engine = new BoxEngine(core.database(), new BoxStore(), new BoxSpool(new File(core.plugin().getDataFolder(), "box")),
                core.plugin().getLogger(), () -> config.retries, () -> config.retryDelayMs);
        service = new BoxService(this, engine);
        core.services().register(ID, ItemBox.class, service);
        Bukkit.getPluginManager().registerEvents(service, core.plugin());
        registerCommands();
        scheduleSpool();
        // Players already online (reload): load their counts quietly.
        core.scheduler().later("box:start", 1, () -> {
            service.importSpool();
            for (Player p : Bukkit.getOnlinePlayers()) {
                service.loadQuietly(p);
            }
        });
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        if (service != null) {
            service.reloadSettings();
        }
        scheduleSpool();
    }

    @Override
    public void disable() {
        if (spoolTask != null) {
            spoolTask.cancel();
            spoolTask = null;
        }
        if (service != null) {
            HandlerList.unregisterAll(service);
            core.services().unregister(ID, ItemBox.class);
            service = null;
        }
    }

    private void scheduleSpool() {
        if (spoolTask != null) {
            spoolTask.cancel();
        }
        long every = 20L * config.spoolImportSeconds;
        spoolTask = core.scheduler().asyncRepeat(every, every, () -> {
            BoxService s = service;
            if (s != null) {
                s.importSpool();
            }
        });
    }

    private void registerCommands() {
        core.commands().register(CommandSpec.command("box")
                .description("Your item mailbox: items that did not fit your inventory")
                .category("Economy")
                .permission(PERM_USE)
                .playerOnly()
                .example("box")
                .executes(ctx -> BoxMenu.open(this, ctx.player())));
    }

    // ------------------------------------------------------------------ shared helpers

    Core core() {
        return core;
    }

    BoxConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    BoxService service() {
        return service;
    }

    /** Shown name of a source key ({@code sources.<key>}, else the key in words). MiniMessage. */
    String sourceName(String source) {
        String raw = core.messages().raw(ID, "sources." + source);
        return raw != null && !raw.isBlank() ? raw : "<c:value>" + BoxItems.words(source);
    }

    /**
     * The stored stack as a menu icon (display copy) with the box lines ({@code <key>}: source, arrival time, id) under its
     * own lore; {@code null} when the stored data is unreadable.
     */
    ItemStack displayItem(Player viewer, BoxEntry e, String key) {
        ItemStack item;
        try {
            item = BoxItems.display(BoxItems.decode(e.bytes()));
        } catch (RuntimeException ex) {
            core.plugin().getLogger().log(Level.WARNING, "Box: entry " + e.id() + " is unreadable", ex);
            return null;
        }
        List<Component> lore = new ArrayList<>();
        List<Component> existing = item.lore();
        if (existing != null) {
            lore.addAll(existing);
        }
        long age = Math.max(0, System.currentTimeMillis() - e.createdAt());
        lore.addAll(core.menus().lines(viewer, ID, key,
                Msg.mm("source", sourceName(e.source())),
                Msg.p("date", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(e.createdAt()))),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, age)))),
                Msg.number("id", e.id())));
        item.lore(lore);
        return item;
    }
}
