package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * M1.11: titles &amp; tags (one menu, two tabs), tag items, staff give/take/item commands, the in-game registry editor
 * and the display format editor ({@code /display}, also reachable from {@code /staff}). Publishes {@link Cosmetics}
 * and the placeholders {@code title} and {@code tag} (the chat/tab module shows them automatically).
 */
public final class CosmeticsModule implements SkyblockModule {

    static final String ID = "cosmetics";

    static final String PERM_MENU = "skyblock.cosmetics";
    static final String PERM_GIVE = "skyblock.staff.cosmetics.give";
    static final String PERM_ITEM = "skyblock.admin.cosmetics.item";
    static final String PERM_EDIT = "skyblock.admin.cosmetics.edit";
    static final String PERM_DISPLAY = "skyblock.admin.display";

    private final Core core;
    private final ModuleMessages msg;
    private final CosmeticsConfig config = new CosmeticsConfig();
    private final CosmeticRegistry registry;
    private final CosmeticsService service;
    private final FormatPreview preview;
    private final TagItems tagItems;

    public CosmeticsModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.registry = new CosmeticRegistry(core.plugin());
        this.service = new CosmeticsService(core, registry, msg);
        this.preview = new FormatPreview(core, service);
        this.tagItems = new TagItems(this);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        registry.load(this::problem);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        service.autoTitle(config.autoTitle);
        service.tagStyle(config.tagFormat, config.tagStripBrackets);
        core.services().register(ID, Cosmetics.class, service);
        core.placeholders().register(ID, "title", (Placeholder) p -> service.placeholder(p, Cosmetics.Kind.TITLE));
        core.placeholders().register(ID, "tag", (Placeholder) p -> service.placeholder(p, Cosmetics.Kind.TAG));
        core.placeholders().registerText(ID, "title_id", p -> p == null ? "" : orEmpty(service.equippedId(p.getUniqueId(), Cosmetics.Kind.TITLE)));
        core.placeholders().registerText(ID, "tag_id", p -> p == null ? "" : orEmpty(service.equippedId(p.getUniqueId(), Cosmetics.Kind.TAG)));
        tagItems.register();
        Bukkit.getPluginManager().registerEvents(service, core.plugin());
        Bukkit.getPluginManager().registerEvents(tagItems, core.plugin());
        service.loadOnline();
        registerCommands();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        registry.load(this::problem);
        service.autoTitle(config.autoTitle);
        service.tagStyle(config.tagFormat, config.tagStripBrackets);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(service);
        HandlerList.unregisterAll(tagItems);
        core.items().unregister(TagItems.ITEM_ID);
    }

    private void problem(String text) {
        core.plugin().getLogger().warning(text);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ shared helpers for the menus

    Core core() {
        return core;
    }

    ModuleMessages msg() {
        return msg;
    }

    CosmeticRegistry registry() {
        return registry;
    }

    CosmeticsService service() {
        return service;
    }

    FormatPreview preview() {
        return preview;
    }

    Component text(Player viewer, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        return core.menus().text(viewer, ID, key, placeholders);
    }

    List<Component> lines(Player viewer, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        return core.menus().lines(viewer, ID, key, placeholders);
    }

    String raw(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? "[missing: cosmetics:" + key + "]" : raw;
    }

    /** Saves a registry change from the editor (logged). Returns false and tells the staff member on failure. */
    boolean save(Player staff, Cosmetic cosmetic, String field) {
        Optional<Cosmetic> before = registry.get(cosmetic.kind(), cosmetic.id());
        try {
            registry.put(cosmetic);
        } catch (IOException e) {
            msg.send(staff, "editor.save-failed", Msg.p("reason", e.getMessage()));
            core.sounds().play(staff, "error");
            return false;
        }
        core.audit().log(AuditCategory.CONFIG, "cosmetic_edit", Actor.player(staff),
                cosmetic.kind().name().toLowerCase() + ":" + cosmetic.id(), null, null, null,
                Map.of("field", field, "old", before.map(Object::toString).orElse("(new)"), "new", cosmetic.toString()));
        msg.send(staff, "editor.saved", Msg.c("display", service.display(staff, cosmetic)));
        core.sounds().play(staff, "success");
        return true;
    }

    void delete(Player staff, Cosmetics.Kind kind, String id) {
        try {
            if (registry.remove(kind, id)) {
                core.audit().log(AuditCategory.CONFIG, "cosmetic_delete", Actor.player(staff), kind.name().toLowerCase() + ":" + id,
                        null, null, null, Map.of());
                msg.send(staff, "editor.deleted", Msg.p("id", id));
            }
        } catch (IOException e) {
            msg.send(staff, "editor.save-failed", Msg.p("reason", e.getMessage()));
        }
    }

    /** Creates tag items for {@code target} (inventory, rest dropped at their feet). Logged per item. */
    void giveTagItems(CommandSender staff, Player target, String tagId, int amount) {
        if (registry.get(Cosmetics.Kind.TAG, tagId).isEmpty()) {
            throw new CommandFailure(ID, "unknown-tag", Msg.p("id", tagId));
        }
        int count = Math.max(1, Math.min(config.tagItemMaxAmount, amount));
        for (int i = 0; i < count; i++) {
            ItemStack item = tagItems.create(tagId, "staff:" + staff.getName());
            target.getInventory().addItem(item).values()
                    .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
            core.audit().logPlayer(AuditCategory.STAFF, "tag_item_create", Actor.of(staff), target.getUniqueId(), target.getName(),
                    null, target.getLocation(), Map.of("tag", tagId,
                            "item_uuid", net.skybornmc.skyblock.common.item.ItemRegistry.uuidOf(item).map(UUID::toString).orElse("")));
        }
        msg.send(staff, "staff.items-given", Msg.number("amount", count), Msg.p("tag", tagId), Msg.p("player", target.getName()));
        if (staff != target) {
            msg.send(target, "staff.items-received", Msg.number("amount", count), Msg.p("tag", tagId));
        }
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        var commands = core.commands();
        Arg<String> tagId = Args.oneOf("tag", () -> registry.ids(Cosmetics.Kind.TAG));
        Arg<String> titleId = Args.oneOf("title", () -> registry.ids(Cosmetics.Kind.TITLE));
        Arg<String> tagTarget = Args.offlinePlayer("player");
        Arg<String> titleTarget = Args.offlinePlayer("player");
        Arg<String> tagTarget2 = Args.offlinePlayer("player");
        Arg<String> titleTarget2 = Args.offlinePlayer("player");
        Arg<String> tagId2 = Args.oneOf("tag", () -> registry.ids(Cosmetics.Kind.TAG));
        Arg<String> titleId2 = Args.oneOf("title", () -> registry.ids(Cosmetics.Kind.TITLE));
        Arg<String> itemTag = Args.oneOf("tag", () -> registry.ids(Cosmetics.Kind.TAG));
        Arg<Integer> itemAmount = Args.integer("amount", 1, 64, "1", "5", "16");
        Arg<Player> itemPlayer = Args.onlinePlayer("player");

        commands.register(CommandSpec.command("tags")
                .description("Your tags and titles: equip one of each").category("Cosmetics")
                .permission(PERM_MENU).aliases("tag")
                .executes(ctx -> core.menus().open(ctx.player(), new CosmeticsMenu(this, Cosmetics.Kind.TAG)))
                .sub(CommandSpec.sub("give").description("Give a player a tag").permission(PERM_GIVE)
                        .arg(tagTarget).arg(tagId).example("tags give Steve phoenix")
                        .executes(ctx -> giveOrTake(ctx.sender(), ctx.get(tagTarget), Cosmetics.Kind.TAG, ctx.get(tagId), true)))
                .sub(CommandSpec.sub("take").description("Take a tag from a player").permission(PERM_GIVE)
                        .arg(tagTarget2).arg(tagId2).example("tags take Steve phoenix")
                        .executes(ctx -> giveOrTake(ctx.sender(), ctx.get(tagTarget2), Cosmetics.Kind.TAG, ctx.get(tagId2), false)))
                .sub(CommandSpec.sub("item").description("Create tag items (redeemable, tradeable)").permission(PERM_ITEM)
                        .arg(itemTag).optional(itemAmount).optional(itemPlayer).example("tags item phoenix 1")
                        .executes(ctx -> {
                            Player target = ctx.has(itemPlayer) ? ctx.get(itemPlayer)
                                    : ctx.sender() instanceof Player p ? p : null;
                            if (target == null) {
                                throw new CommandFailure(ID, "staff.console-needs-player");
                            }
                            giveTagItems(ctx.sender(), target, ctx.get(itemTag), ctx.getOr(itemAmount, 1));
                        })));

        commands.register(CommandSpec.command("titles")
                .description("Your titles and tags: equip one of each").category("Cosmetics")
                .permission(PERM_MENU).aliases("title")
                .executes(ctx -> core.menus().open(ctx.player(), new CosmeticsMenu(this, Cosmetics.Kind.TITLE)))
                .sub(CommandSpec.sub("give").description("Give a player a title").permission(PERM_GIVE)
                        .arg(titleTarget).arg(titleId).example("titles give Steve newcomer")
                        .executes(ctx -> giveOrTake(ctx.sender(), ctx.get(titleTarget), Cosmetics.Kind.TITLE, ctx.get(titleId), true)))
                .sub(CommandSpec.sub("take").description("Take a title from a player").permission(PERM_GIVE)
                        .arg(titleTarget2).arg(titleId2).example("titles take Steve newcomer")
                        .executes(ctx -> giveOrTake(ctx.sender(), ctx.get(titleTarget2), Cosmetics.Kind.TITLE, ctx.get(titleId2), false))));

        commands.register(CommandSpec.command("display")
                .description("Edit how players look in chat, tab and above heads").category("Staff")
                .permission(PERM_DISPLAY).playerOnly().aliases("displayformat")
                .executes(ctx -> core.menus().open(ctx.player(), new DisplayMenu(this))));
    }

    /** Resolves an online or offline player name to a UUID. */
    private CompletableFuture<Optional<UUID>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        return core.players().offline(name).thenApply(s -> s.map(snapshot -> snapshot.uuid()));
    }

    private void giveOrTake(CommandSender staff, String name, Cosmetics.Kind kind, String id, boolean give) {
        String kindKey = kind == Cosmetics.Kind.TITLE ? "title" : "tag";
        if (service.automatic(kind, id)) {
            // spec 14.11: e.g. the clean-streak title is earned and lost by itself; staff can't give or take it
            msg.send(staff, "staff.automatic-" + kindKey, Msg.p("id", id));
            return;
        }
        resolve(name).thenComposeAsync(uuid -> {
            if (uuid.isEmpty()) {
                msg.send(staff, "staff.unknown-player", Msg.p("player", name));
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Boolean> action = give
                    ? service.grant(uuid.get(), kind, id, "staff:" + staff.getName(), Actor.of(staff))
                    : service.revoke(uuid.get(), kind, id, "staff:" + staff.getName(), Actor.of(staff));
            return action.thenAccept(changed -> msg.send(staff,
                    "staff." + (give ? "given" : "taken") + "-" + kindKey + (changed ? "" : "-noop"),
                    Msg.p("player", name), Msg.p("id", id)));
        }, new MainThread(core.plugin())).exceptionally(error -> {
            msg.send(staff, "staff.error", Msg.p("reason", String.valueOf(error.getMessage())));
            return null;
        });
    }
}
