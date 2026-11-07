package net.skybornmc.skyblock.profiles;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Island profiles (PROF, owner decisions P1–P12 and round 3 PR1–PR5): up to 3 profiles per player, each with its own
 * island, money, crystals, shards, inventory, ender chest, XP, health/food/effects, skills, Depths unlocks, auction
 * listings, notes, settings and playtime. {@code /profiles} is the only way to switch (P5).
 *
 * <p>Publishes {@link ProfileService}. Settings: {@code profiles.yml}; texts: {@code messages/profiles.yml};
 * data: {@code db/profiles}. Other modules store per-profile data under {@link ProfileService#profileKey}.</p>
 */
public final class ProfilesModule implements SkyblockModule {

    static final String ID = "profiles";
    static final String PERM_USE = "skyblock.profiles.use";
    static final String PERM_INFO = "skyblock.profiles.admin.info";
    static final String PERM_RESET = "skyblock.profiles.admin.reset";
    static final String PERM_SNAPSHOTS = "skyblock.profiles.admin.snapshots";
    static final String PERM_RESTORE = "skyblock.profiles.admin.restore";

    final Core core;
    final ModuleMessages msg;
    final ProfilesConfig config = new ProfilesConfig();
    private ProfileServiceImpl service;
    private ProfileGuards guards;

    public ProfilesModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        StateCodec.persistentKeys(config.persistentKeys);
        service = new ProfileServiceImpl(this, new ProfileStore(), new MainThread(core.plugin()));
        service.start();
        core.services().register(ID, ProfileService.class, service);
        core.plugin().getServer().getPluginManager().registerEvents(service, core.plugin());
        // Owner 7 Nov 2026: profile slots 2 and 3 in the credit shop (Extras).
        net.skybornmc.skyblock.common.store.CreditShop.unregisterAll(ID);
        for (int slot = 2; slot <= 3; slot++) {
            int n = slot;
            if (config.price(n) <= 0) {
                continue;
            }
            net.skybornmc.skyblock.common.store.CreditShop.register(ID, net.skybornmc.skyblock.common.store.CreditShop.Offer.of("profile_slot_" + n,
                    net.skybornmc.skyblock.common.store.CreditShop.Tab.EXTRAS, 40 + n,
                    (viewer, receiver) -> core.menus().item(org.bukkit.Material.ARMOR_STAND, viewer, ID,
                            service.slotUnlocked(receiver, n) ? "store.slot-owned" : "store.slot", net.skybornmc.skyblock.common.text.Msg.number("slot", n)),
                    (viewer, receiver) -> service.shopPrice(receiver, n), false, true,
                    (buyer, receiver, purchaseId) -> service.grantSlot(receiver, n)));
        }
        guards = new ProfileGuards(this, service);
        core.plugin().getServer().getPluginManager().registerEvents(guards, core.plugin());
        core.placeholders().registerText(ID, "profile_name", p -> p == null ? "" : service.activeName(p));
        core.placeholders().registerText(ID, "profile_slot",
                p -> p == null ? "" : service.active(p.getUniqueId()).map(pr -> String.valueOf(pr.slot())).orElse(""));
        // Playtime of the active profile (owner P2: playtime is per profile; the account total stays in <ph:playtime>).
        core.placeholders().registerText(ID, "profile_playtime", p -> p == null ? "" : service.active(p.getUniqueId())
                .map(pr -> net.skybornmc.skyblock.common.util.Format.duration(java.time.Duration.ofMillis(service.playtime(p.getUniqueId(), pr))))
                .orElse(""));
        core.commands().register(CommandSpec.command("profiles")
                .description("Your island profiles: switch, create, rename, delete")
                .category("Island")
                .permission(PERM_USE)
                .playerOnly()
                .aliases("profile")
                .executes(ctx -> open(ctx.player()))
                .sub(CommandSpec.sub("info").description("Staff: a player's profiles (slot, name, active, playtime, island)")
                        .permission(PERM_INFO).arg(net.skybornmc.skyblock.common.command.Args.offlinePlayer("player"))
                        .example("profiles info Steve")
                        .executes(ctx -> service.staffInfo(ctx.sender(), ctx.get(net.skybornmc.skyblock.common.command.Args.offlinePlayer("player")))))
                .sub(resetSpec())
                .sub(snapshotsSpec())
                .sub(restoreSpec()));
    }

    /** {@code /profiles snapshots <player>} (staff): the wiped profiles of a player that can still be restored. */
    private CommandSpec snapshotsSpec() {
        var player = net.skybornmc.skyblock.common.command.Args.offlinePlayer("player");
        return CommandSpec.sub("snapshots").description("Staff: a player's wiped profiles (snapshots kept for wipe.snapshot-days)")
                .permission(PERM_SNAPSHOTS).arg(player)
                .example("profiles snapshots Steve")
                .executes(ctx -> service.staffSnapshots(ctx.sender(), ctx.get(player)));
    }

    /**
     * {@code /profiles restore <player> <slot> [snapshot]} (staff, confirm): puts a wiped profile back into its slot (default
     * the newest open snapshot of that slot). The snapshot id suggestions are that player's open snapshots of that slot.
     */
    private CommandSpec restoreSpec() {
        var player = net.skybornmc.skyblock.common.command.Args.offlinePlayer("player");
        var slot = net.skybornmc.skyblock.common.command.Args.integer("slot", 1, 3, "1", "2", "3");
        var snapshot = net.skybornmc.skyblock.common.command.Arg.<Integer>word("snapshot", (sender, raw) -> {
            try {
                int id = Integer.parseInt(raw.strip());
                if (id >= 1) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // falls through to the error
            }
            throw net.skybornmc.skyblock.common.command.CommandFailure.of("invalid-number", net.skybornmc.skyblock.common.text.Msg.p("input", raw),
                    net.skybornmc.skyblock.common.text.Msg.p("min", 1), net.skybornmc.skyblock.common.text.Msg.p("max", Integer.MAX_VALUE));
        }, this::suggestSnapshots);
        return CommandSpec.sub("restore").description("Staff: put a wiped profile back into its slot (confirm; the current one is kept as a snapshot)")
                .permission(PERM_RESTORE).arg(player).arg(slot).optional(snapshot).confirm(10)
                .example("profiles restore Steve 1")
                .executes(ctx -> service.staffRestore(ctx.sender(), ctx.get(player), ctx.get(slot), ctx.getOr(snapshot, null)));
    }

    /** Tab completion of the snapshot id: the open snapshots of the typed player (and slot), from the database. */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSnapshots(
            com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String name = argument(ctx, "player");
        org.bukkit.OfflinePlayer target = name == null ? null : org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        if (target == null || service == null) {
            builder.suggest("<snapshot>");
            return builder.buildFuture();
        }
        Integer slot = null;
        try {
            String raw = argument(ctx, "slot");
            slot = raw == null ? null : Integer.parseInt(raw.strip());
        } catch (NumberFormatException ignored) {
            // any slot
        }
        String typed = builder.getRemaining();
        return service.openSnapshotIds(target.getUniqueId(), slot).handle((ids, error) -> {
            if (ids != null) {
                for (String id : ids) {
                    if (id.startsWith(typed)) {
                        builder.suggest(id);
                    }
                }
            }
            return builder.build();
        });
    }

    private static String argument(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
                                   String name) {
        try {
            return ctx.getArgument(name, String.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code /profiles reset <player> [slot]} (staff, confirm): a fresh profile in that slot (default: the active one). */
    private CommandSpec resetSpec() {
        var player = net.skybornmc.skyblock.common.command.Args.offlinePlayer("player");
        var slot = net.skybornmc.skyblock.common.command.Args.integer("slot", 1, 3, "1", "2", "3");
        return CommandSpec.sub("reset").description("Staff: wipe a player's profile into a snapshot and give them a fresh one (confirm)")
                .permission(PERM_RESET).arg(player).optional(slot).confirm(10)
                .example("profiles reset Steve 1")
                .executes(ctx -> service.staffReset(ctx.sender(), ctx.get(player), ctx.getOr(slot, null)));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        StateCodec.persistentKeys(config.persistentKeys);
        core.messages().load(ID);
    }

    @Override
    public void disable() {
        net.skybornmc.skyblock.common.store.CreditShop.unregisterAll(ID);
        if (service != null) {
            HandlerList.unregisterAll(service);
            service.stop();
        }
        if (guards != null) {
            HandlerList.unregisterAll(guards);
        }
    }

    ProfileServiceImpl service() {
        return service;
    }

    void open(Player player) {
        core.menus().open(player, new ProfilesMenu(this));
    }

    void fail(Player player, String key, TagResolver... placeholders) {
        if (player.isOnline()) {
            msg.send(player, key, placeholders);
            core.sounds().play(player, "error");
        }
    }

    /** The icon of a profile: its own pick, else the slot's default. */
    Material icon(Profile profile) {
        Material own = profile.icon() == null ? null : Material.matchMaterial(profile.icon());
        if (own != null && own.isItem() && !own.isAir()) {
            return own;
        }
        return defaultIcon(profile.slot());
    }

    Material defaultIcon(int slot) {
        List<String> defaults = config.defaultIcons;
        String key = slot >= 1 && slot <= defaults.size() ? defaults.get(slot - 1) : "grass_block";
        Material m = key == null ? null : Material.matchMaterial(key.trim());
        return m == null || !m.isItem() || m.isAir() ? Material.GRASS_BLOCK : m;
    }

    List<Material> iconChoices() {
        List<Material> list = new ArrayList<>();
        for (String key : config.iconChoices) {
            Material m = key == null ? null : Material.matchMaterial(key.trim());
            if (m != null && m.isItem() && !m.isAir() && !list.contains(m)) {
                list.add(m);
            }
        }
        return list;
    }
}
